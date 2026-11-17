import groovy.json.JsonOutput
import groovy.json.JsonSlurper
import org.gradle.api.DefaultTask
import org.gradle.api.file.ConfigurableFileCollection
import org.gradle.api.file.DirectoryProperty
import org.gradle.api.provider.Property
import org.gradle.api.tasks.Input
import org.gradle.api.tasks.InputFiles
import org.gradle.api.tasks.OutputDirectory
import org.gradle.api.tasks.PathSensitive
import org.gradle.api.tasks.PathSensitivity
import org.gradle.api.tasks.TaskAction
import java.io.File

/** Generates 1.20.1 data (recipes, loot tables, advancements, tags) from the 1.21 data, which stays the source of truth. */
abstract class Data120 : DefaultTask() {
    @get:InputFiles @get:PathSensitive(PathSensitivity.RELATIVE)
    abstract val sources: ConfigurableFileCollection

    /** Forge reads "conditions" but has no loot table conditions: conditional tables ship empty and the mod fills them in code. */
    @get:Input
    abstract val forge: Property<Boolean>

    @get:OutputDirectory
    abstract val outputDir: DirectoryProperty

    private val dirs = listOf("recipe/" to "recipes/", "loot_table/" to "loot_tables/", "advancement/" to "advancements/", "tags/item/" to "tags/items/", "tags/block/" to "tags/blocks/")
    private val vanillaShaped = setOf("simplemachines:crafting_shaped_quarry", "simplemachines:crafting_shapeless_keep_tool")
    private val cooking = setOf("minecraft:smelting", "minecraft:blasting", "minecraft:smoking", "minecraft:campfire_cooking")

    private val vanilla = mapOf(
        "ingots/iron" to listOf("minecraft:iron_ingot"), "ingots/copper" to listOf("minecraft:copper_ingot"), "ingots/gold" to listOf("minecraft:gold_ingot"),
        "ingots/netherite" to listOf("minecraft:netherite_ingot"), "nuggets/iron" to listOf("minecraft:iron_nugget"), "nuggets/gold" to listOf("minecraft:gold_nugget"),
        "gems/diamond" to listOf("minecraft:diamond"), "gems/emerald" to listOf("minecraft:emerald"), "gems/lapis" to listOf("minecraft:lapis_lazuli"),
        "gems/quartz" to listOf("minecraft:quartz"), "dusts/redstone" to listOf("minecraft:redstone"), "ender_pearls" to listOf("minecraft:ender_pearl"),
        "glass_blocks" to listOf("minecraft:glass", "minecraft:tinted_glass", "minecraft:white_stained_glass", "minecraft:orange_stained_glass", "minecraft:magenta_stained_glass", "minecraft:light_blue_stained_glass", "minecraft:yellow_stained_glass", "minecraft:lime_stained_glass", "minecraft:pink_stained_glass", "minecraft:gray_stained_glass", "minecraft:light_gray_stained_glass", "minecraft:cyan_stained_glass", "minecraft:purple_stained_glass", "minecraft:blue_stained_glass", "minecraft:brown_stained_glass", "minecraft:green_stained_glass", "minecraft:red_stained_glass", "minecraft:black_stained_glass"), "raw_materials/iron" to listOf("minecraft:raw_iron"), "raw_materials/copper" to listOf("minecraft:raw_copper"),
        "raw_materials/gold" to listOf("minecraft:raw_gold"), "ores/iron" to listOf("#minecraft:iron_ores"), "ores/copper" to listOf("#minecraft:copper_ores"),
        "ores/gold" to listOf("#minecraft:gold_ores"), "storage_blocks/iron" to listOf("minecraft:iron_block"), "storage_blocks/gold" to listOf("minecraft:gold_block"),
        "storage_blocks/redstone" to listOf("minecraft:redstone_block"), "sands" to listOf("#minecraft:sand"), "leathers" to listOf("minecraft:leather"),
        "slime_balls" to listOf("minecraft:slime_ball"), "stones" to listOf("minecraft:stone"), "cobblestones" to listOf("minecraft:cobblestone"),
        "chests" to listOf("minecraft:chest"), "gunpowders" to listOf("minecraft:gunpowder"), "strings" to listOf("minecraft:string"),
        "seeds" to listOf("minecraft:wheat_seeds", "minecraft:beetroot_seeds", "minecraft:melon_seeds", "minecraft:pumpkin_seeds", "minecraft:torchflower_seeds"),
        "crops" to listOf("minecraft:wheat", "minecraft:carrot", "minecraft:potato", "minecraft:beetroot", "minecraft:nether_wart"),
    )

    // Forge 1.20.1 names that differ from the 1.21 c: path
    private val forgeNames = mapOf("glass_blocks" to "glass", "leathers" to "leather", "slime_balls" to "slimeballs", "stones" to "stone",
        "cobblestones" to "cobblestone", "gunpowders" to "gunpowder", "strings" to "string", "sands" to "sand")

    // Fabric 1.20.1 conventional names that do not follow <material>_<kind>
    private val fabricNames = mapOf("gems/diamond" to "diamonds", "gems/emerald" to "emeralds", "gems/lapis" to "lapis", "gems/quartz" to "quartz",
        "dusts/redstone" to "redstone_dusts", "sands" to "sand", "glass_blocks" to "glass_blocks")

    private fun fabricName(path: String): String {
        fabricNames[path]?.let { return it }
        if ('/' !in path) return path
        val (kind, mat) = path.split('/', limit = 2)
        return when (kind) {
            "raw_materials" -> "raw_${mat}_ores"
            "storage_blocks" -> "${mat}_blocks"
            else -> "${mat}_$kind"
        }
    }

    @Suppress("UNCHECKED_CAST")
    private fun obj(o: Any?) = o as MutableMap<String, Any?>

    private fun cond(c: Any?): Any? = when (c) {
        is List<*> -> c.map { cond(it) }
        is Map<*, *> -> LinkedHashMap<String, Any?>().also { m ->
            for ((k, v) in c) m[k as String] = if (k == "type" && v is String && v.startsWith("neoforge:")) "forge:" + v.removePrefix("neoforge:") else cond(v)
        }
        else -> c
    }

    private fun recipe(j: MutableMap<String, Any?>) {
        val r = j["result"] as? Map<*, *> ?: return
        if ("id" !in r) return
        // mod process recipes read the 1.21 shape through ItemStackTemplate
        if ((j["type"] as? String)?.startsWith("simplemachines:") == true && j["type"] !in vanillaShaped) return
        if (j["type"] in cooking) { j["result"] = r["id"]; return }
        if (j["type"] == "minecraft:stonecutting") { j["result"] = r["id"]; j["count"] = r["count"] ?: 1; return }
        val n = LinkedHashMap<String, Any?>()
        n["item"] = r["id"]
        val mod = (r["components"] as? Map<*, *>).orEmpty().filterKeys { (it as String).startsWith("simplemachines:") }
        for ((k, v) in r) if (k != "id" && !(k == "components" && mod.isNotEmpty())) n[k as String] = v
        if (mod.isNotEmpty()) {
            // components become the SimpleMachines nbt compound; the keep tool serializer is the one that reads result nbt on both loaders
            n["nbt"] = linkedMapOf("SimpleMachines" to mod.entries.associate { (k, v) -> (k as String).substringAfter(':') to v })
            if (j["type"] == "minecraft:crafting_shapeless") j["type"] = "simplemachines:crafting_shapeless_keep_tool"
        }
        j["result"] = n
    }

    private fun loot(o: Any?): Any? = when (o) {
        is List<*> -> o.map { loot(it) }
        is Map<*, *> -> when (o["function"]) {
            "minecraft:copy_components" -> linkedMapOf("function" to "minecraft:copy_nbt", "source" to "block_entity",
                "ops" to (o["include"] as List<*>).map { val n = "SimpleMachines." + (it as String).substringAfter(':'); linkedMapOf("source" to n, "target" to n, "op" to "replace") })
            "minecraft:set_components" -> {
                val comps = (o["components"] as Map<*, *>).filterKeys { it == "simplebooks:book" }
                linkedMapOf("function" to "minecraft:set_nbt", "tag" to JsonOutput.toJson(comps))
            }
            else -> if (o["condition"] == "minecraft:match_tool") matchTool(o)
                else LinkedHashMap<String, Any?>().also { m -> for ((k, v) in o) m[k as String] = loot(v) }
        }
        else -> o
    }

    // 1.20.5 item predicates: "items" may be a tag string, enchantments sit under "predicates"
    private fun matchTool(o: Map<*, *>): Map<String, Any?> {
        val pred = LinkedHashMap<String, Any?>()
        for ((k, v) in o["predicate"] as Map<*, *>) when (k) {
            "items" -> if (v is String && v.startsWith("#")) pred["tag"] = v.substring(1) else pred["items"] = if (v is String) listOf(v) else v
            "predicates" -> (v as Map<*, *>)["minecraft:enchantments"]?.let { e ->
                pred["enchantments"] = (e as List<*>).map { x -> LinkedHashMap<String, Any?>().also { m ->
                    for ((ek, ev) in x as Map<*, *>) m[if (ek == "enchantments") "enchantment" else ek as String] = ev
                } }
            }
            else -> pred[k as String] = v
        }
        return linkedMapOf("condition" to "minecraft:match_tool", "predicate" to pred)
    }

    private fun advancement(j: MutableMap<String, Any?>) {
        val crit = j["criteria"] as? Map<*, *> ?: return
        for (c in crit.values) {
            val items = ((c as Map<*, *>)["conditions"] as? Map<*, *>)?.get("items") as? List<*> ?: continue
            for (p in items) {
                val m = obj(p)
                val v = m["items"] as? String ?: continue
                if (v.startsWith("#")) { m.remove("items"); m["tag"] = v.substring(1) } else m["items"] = listOf(v)
            }
        }
    }

    private fun mergeTag(target: File, add: Map<String, Any?>) {
        val j = obj(JsonSlurper().parse(target))
        val vals = ArrayList(j["values"] as List<*>)
        for (v in add["values"] as List<*>) if (v !in vals) vals.add(v)
        j["values"] = vals
        write(target, j)
    }

    private fun write(f: File, j: Any?) {
        f.parentFile.mkdirs()
        f.writeText(JsonOutput.prettyPrint(JsonOutput.toJson(j)) + "\n")
    }

    @TaskAction
    fun run() {
        val out = outputDir.get().asFile
        out.deleteRecursively()
        val data = File(out, "data")
        val done = HashSet<String>()
        val usedC = sortedSetOf<String>()
        val ref = Regex("\"#?c:([a-z0-9_/]+)\"")
        for (base in sources.files.map { File(it, "data") }.filter { it.isDirectory }) {
            base.walkTopDown().filter { it.isFile && it.name.endsWith(".json") }.sortedBy { it.path }.forEach { f ->
                val rel = f.relativeTo(base).invariantSeparatorsPath
                val ns = rel.substringBefore('/'); val rest = rel.substringAfter('/')
                val (a, b) = dirs.firstOrNull { rest.startsWith(it.first) } ?: return@forEach
                val new = "$ns/$b${rest.removePrefix(a)}"
                if (!done.add(new)) {
                    // tags from the ship and portal dirs join the main copy, like mergeShipJson does on newer versions
                    val extra = f.invariantSeparatorsPath.let { "/src/ship/" in it || "/src/portal/" in it }
                    if (extra && b.startsWith("tags/") && File(data, new).exists()) mergeTag(File(data, new), obj(JsonSlurper().parseText(f.readText())))
                    return@forEach
                }
                // 1.20.5+ vanilla items that 1.20.1 lacks
                val text = f.readText().replace("\"minecraft:crafter\"", "\"minecraft:crafting_table\"")
                if (text.contains("\"neoforge:false\"")) return@forEach
                ref.findAll(text).forEach { usedC.add(it.groupValues[1]) }
                var j = obj(JsonSlurper().parseText(text))
                val conds = j.remove("neoforge:conditions")
                if (forge.get()) j.remove("fabric:load_conditions")
                if (conds != null && forge.get()) j["conditions"] = cond(conds)
                when (b) {
                    "recipes/" -> recipe(j)
                    "loot_tables/" -> if (conds != null && forge.get()) { j.remove("conditions"); j["pools"] = listOf<Any>() } else j = obj(loot(j))
                    "advancements/" -> advancement(j)
                }
                write(File(data, new), j)
            }
        }
        for (path in usedC) {
            if (path.startsWith("tools/") || path == "worktops") continue
            val target = File(data, "c/tags/items/$path.json")
            val vals = ArrayList<Any?>()
            if (target.exists()) vals.addAll((JsonSlurper().parse(target) as Map<*, *>)["values"] as List<*>)
            for (v in vanilla[path].orEmpty()) if (v !in vals) vals.add(v)
            for (t in listOf("#forge:" + (forgeNames[path] ?: path), "#c:" + fabricName(path))) if (t != "#c:$path") vals.add(linkedMapOf("id" to t, "required" to false))
            write(target, linkedMapOf("replace" to false, "values" to vals))
        }
        logger.info("Data120: ${done.size} files, ${usedC.size} c tags")
    }
}
