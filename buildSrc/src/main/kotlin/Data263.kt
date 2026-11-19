import groovy.json.JsonOutput
import groovy.json.JsonSlurper
import java.io.FilterReader
import java.io.Reader
import java.io.StringReader

/** 26.3 dimension types: the bed rule "explodes" flag became destroy_on_use, and straw beds got their own rule. */
class DimensionType263(input: Reader) : FilterReader(StringReader(convert(input.readText()))) {
    companion object {
        private const val BED = "minecraft:gameplay/bed_rule"
        private const val STRAW = "minecraft:gameplay/straw_bed_rule"

        @Suppress("UNCHECKED_CAST")
        fun convert(text: String): String {
            val j = JsonSlurper().parseText(text) as MutableMap<String, Any?>
            val attrs = j["attributes"] as? MutableMap<String, Any?> ?: return text
            val bed = attrs[BED] as? MutableMap<String, Any?> ?: return text
            val explodes = bed.remove("explodes") == true
            if (explodes) bed["destroy_on_use"] = true
            // same straw rules as vanilla: like the bed where beds blow up, otherwise usable but gone once you leave
            if (STRAW !in attrs) attrs[STRAW] = if (explodes) LinkedHashMap(bed)
                else linkedMapOf("can_set_spawn" to "never", "can_sleep" to "when_dark", "destroy_on_leave" to true,
                    "error_message" to mapOf("translate" to "block.minecraft.bed.no_sleep"))
            return JsonOutput.prettyPrint(JsonOutput.toJson(j))
        }
    }
}

/** 26.3 features: worldgen/configured_feature is now worldgen/feature, the config object is inlined and block states are plain ids. */
class Feature263(input: Reader) : FilterReader(StringReader(convert(input.readText()))) {
    companion object {
        @Suppress("UNCHECKED_CAST")
        fun convert(text: String): String {
            val j = JsonSlurper().parseText(text) as Map<String, Any?>
            val out = LinkedHashMap<String, Any?>()
            out["type"] = j["type"]
            (j["config"] as? Map<String, Any?>)?.forEach { (k, v) -> out[k] = v }
            for ((k, v) in j) if (k != "type" && k != "config") out[k] = v
            (out["targets"] as? List<*>)?.forEach { t ->
                val m = t as MutableMap<String, Any?>
                val s = m["state"] as? Map<*, *>
                if (s != null && s.keys == setOf("Name")) m["state"] = s["Name"]
            }
            return JsonOutput.prettyPrint(JsonOutput.toJson(out))
        }
    }
}

/** 26.3 biomes: mob lists moved to the gameplay/natural_mob_spawns attribute. Empty spawners are dropped. */
class Biome263(input: Reader) : FilterReader(StringReader(convert(input.readText()))) {
    companion object {
        private const val SPAWNS = "minecraft:gameplay/natural_mob_spawns"

        @Suppress("UNCHECKED_CAST")
        fun convert(text: String): String {
            val j = JsonSlurper().parseText(text) as MutableMap<String, Any?>
            j.remove("spawn_costs")
            val spawners = j.remove("spawners") as? Map<String, Any?>
            val hasMobs = spawners?.values?.any { it is List<*> && it.isNotEmpty() } == true
            if (hasMobs) {
                val attrs = (j["attributes"] as? MutableMap<String, Any?>) ?: LinkedHashMap<String, Any?>().also { j["attributes"] = it }
                if (SPAWNS !in attrs) {
                    attrs[SPAWNS] = linkedMapOf(
                        "modifier" to "overlay",
                        "argument" to linkedMapOf("spawn_costs" to emptyMap<String, Any?>(), "spawns_by_category" to spawners),
                    )
                }
            }
            return JsonOutput.prettyPrint(JsonOutput.toJson(j))
        }
    }
}
