import groovy.json.JsonOutput
import groovy.json.JsonSlurper
import org.gradle.api.Project
import org.gradle.api.tasks.SourceSet
import org.gradle.api.tasks.SourceSetContainer
import org.gradle.language.jvm.tasks.ProcessResources
import java.io.File
import java.io.FilterReader
import java.io.Reader
import java.io.StringReader

/**
 * Simple Machines 2.3 ship and factory blocks. They live in src/ship and are only built for the versions below
 * until they are backfilled. Main code that calls into them sits behind the stonecutter constant `ship`.
 */
object Ship {
    val VERSIONS = setOf("1.20.1", "1.21.1", "26.1.2", "26.2", "26.3")
    fun enabled(mc: String) = mc in VERSIONS
}

/** Simple Machines 2.3 portal core (M2): src/portal, same versions and rules as Ship. Stonecutter constant `portal`. */
object Portal {
    val VERSIONS = setOf("1.20.1", "1.21.1", "26.1.2", "26.2", "26.3")
    fun enabled(mc: String) = mc in VERSIONS
}

/** Old-format data for an extra dir (src/<extra>/legacy/<mc>). It goes right after src/legacy so it hides the modern copy. */
fun extraLegacyDirs(root: File, dirs: List<String>, versions: List<String>): List<File> =
    versions.flatMap { v -> dirs.map { File(root, "$it/legacy/$v") } }.filter { it.isDirectory }

/** Whole-file overlays for ship or portal classes are left out on versions that do not build that dir. */
fun SourceSet.skipDisabledExtras(mc: String) {
    if (!Ship.enabled(mc)) java.exclude("com/workshopcraft/simplemachines/ship/**", "com/workshopcraft/simplemachines/*/ship/**")
    if (!Portal.enabled(mc)) java.exclude("com/workshopcraft/simplemachines/portal/**", "com/workshopcraft/simplemachines/*/portal/**")
}

/** The extra source dirs a version builds, in merge order. */
fun extraDirs(mc: String): List<String> = buildList {
    if (Ship.enabled(mc)) add("src/ship")
    if (Portal.enabled(mc)) add("src/portal")
}

/**
 * Adds src/<extra>/resources to main and builds the Java from there through Stonecutter. Stonecutter only processes
 * src/<sourceSet>, so each extra dir gets a dedicated source set and its processed output is compiled as part of main.
 * That keeps the processed copies (and their `//?` guards) working while main and the extra dirs can still reference
 * each other.
 */
fun Project.applyShip(root: File, dirs: List<String> = listOf("src/ship")) {
    val sourceSets = extensions.getByType(SourceSetContainer::class.java)
    val main = sourceSets.getByName(SourceSet.MAIN_SOURCE_SET_NAME)
    for (d in dirs) {
        val name = File(d).name
        val extra = sourceSets.create(name)
        main.resources.srcDir(File(root, "$d/resources"))
        // Only the processed copy is needed; main compiles it so both directions can reference each other
        extra.java.setSrcDirs(emptyList<File>())
        extra.resources.setSrcDirs(emptyList<File>())
        val suffix = name.replaceFirstChar { it.uppercaseChar() }
        main.java.srcDir(files(layout.buildDirectory.dir("generated/stonecutter/$name/java"))
            .builtBy("stonecutterGenerate$suffix"))
    }
}

/** A json file that exists in more than one of src/main/resources and the extra resource dirs is merged: objects key by key, arrays joined. */
fun ProcessResources.mergeShipJson(root: File, dirs: List<String> = listOf("src/ship")) {
    val roots = listOf(File(root, "src/main/resources")) + dirs.map { File(root, "$it/resources") }.filter { it.isDirectory }
    val byRel = LinkedHashMap<String, MutableList<File>>()
    for (r in roots) r.walk().filter { it.isFile && it.extension == "json" }.forEach { f ->
        byRel.getOrPut(f.relativeTo(r).invariantSeparatorsPath) { mutableListOf() }.add(f)
    }
    for ((rel, files) in byRel) if (files.size > 1) {
        filesMatching(rel) { filter(mapOf("files" to files.joinToString(File.pathSeparator) { it.absolutePath }), JsonMerge::class.java) }
    }
}

/** Ignores its input and writes the listed files merged in order, so it gives the same result whichever copy Gradle keeps. */
class JsonMerge(input: Reader) : FilterReader(input) {
    var files: String = ""
    private var merged: Reader? = null

    private fun src(): Reader {
        merged?.let { return it }
        val slurper = JsonSlurper()
        var out: Any? = null
        for (f in files.split(File.pathSeparator)) {
            val v = slurper.parse(File(f))
            out = if (out == null) v else merge(out, v)
        }
        return StringReader(JsonOutput.prettyPrint(JsonOutput.toJson(out))).also { merged = it }
    }

    override fun read(): Int = src().read()
    override fun read(cbuf: CharArray, off: Int, len: Int): Int = src().read(cbuf, off, len)
    override fun skip(n: Long): Long = src().skip(n)
    override fun ready(): Boolean = src().ready()
    override fun markSupported(): Boolean = false

    companion object {
        fun merge(a: Any?, b: Any?): Any? = when {
            a is Map<*, *> && b is Map<*, *> -> {
                val m = LinkedHashMap<String, Any?>()
                for ((k, v) in a) m[k as String] = v
                for ((k, v) in b) m[k as String] = if (m.containsKey(k)) merge(m[k], v) else v
                m
            }
            a is List<*> && b is List<*> -> (a + b.filter { it !in a })
            else -> b
        }
    }
}
