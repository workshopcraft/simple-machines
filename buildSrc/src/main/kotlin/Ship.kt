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

/** Adds src/ship/java and src/ship/resources to the source set. */
fun SourceSet.applyShip(root: File) {
    java.srcDir(File(root, "src/ship/java"))
    resources.srcDir(File(root, "src/ship/resources"))
}

/** A json file that exists in both src/main/resources and src/ship/resources is merged: objects key by key, arrays joined. */
fun ProcessResources.mergeShipJson(root: File) {
    val main = File(root, "src/main/resources")
    val ship = File(root, "src/ship/resources")
    if (!ship.isDirectory) return
    ship.walk().filter { it.isFile && it.extension == "json" }.forEach { f ->
        val rel = f.relativeTo(ship).invariantSeparatorsPath
        val m = File(main, rel)
        if (m.isFile) filesMatching(rel) { filter(mapOf("main" to m.absolutePath, "ship" to f.absolutePath), JsonMerge::class.java) }
    }
}

/** Ignores its input and writes main merged with ship, so it gives the same result whichever copy Gradle keeps. */
class JsonMerge(input: Reader) : FilterReader(input) {
    var main: String = ""
    var ship: String = ""
    private var merged: Reader? = null

    private fun src(): Reader {
        merged?.let { return it }
        val slurper = JsonSlurper()
        val out = merge(slurper.parse(File(main)), slurper.parse(File(ship)))
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
