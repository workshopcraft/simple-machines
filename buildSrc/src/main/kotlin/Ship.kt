import groovy.json.JsonOutput
import groovy.json.JsonSlurper
import org.gradle.api.tasks.SourceSet
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
    val VERSIONS = setOf("26.1.2")
    fun enabled(mc: String) = mc in VERSIONS
}

/** Simple Machines 2.3 portal core (M2): src/portal, same versions and rules as Ship. Stonecutter constant `portal`. */
object Portal {
    val VERSIONS = setOf("26.1.2")
    fun enabled(mc: String) = mc in VERSIONS
}

/** The extra source dirs a version builds, in merge order. */
fun extraDirs(mc: String): List<String> = buildList {
    if (Ship.enabled(mc)) add("src/ship")
    if (Portal.enabled(mc)) add("src/portal")
}

/** Adds src/<extra>/java and src/<extra>/resources to the source set. */
fun SourceSet.applyShip(root: File, dirs: List<String> = listOf("src/ship")) {
    for (d in dirs) {
        java.srcDir(File(root, "$d/java"))
        resources.srcDir(File(root, "$d/resources"))
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
