plugins {
    id("dev.kikugie.loom-back-compat")
}

val modVersion = providers.gradleProperty("version").orNull ?: sc.properties.get<String>("mod.version")
val mc = sc.current.version
val modern = sc.current.parsed >= "26.1"
val ship = Ship.enabled(mc)
val extras = extraDirs(mc)
version = modVersion
group = property("mod.group") as String
base.archivesName = "${property("mod.id")}-fabric-$mc"

val requiredJava = when {
    modern -> JavaVersion.VERSION_25
    sc.current.parsed >= "1.20.5" -> JavaVersion.VERSION_21
    else -> JavaVersion.VERSION_17
}

repositories {
    exclusiveContent {
        forRepository { maven("https://maven.blamejared.com") { name = "BlameJared" } }
        filter { includeGroup("mezz.jei") }
    }
}

dependencies {
    minecraft("com.mojang:minecraft:$mc")
    loomx.applyMojangMappings()
    modImplementation("net.fabricmc:fabric-loader:${property("deps.fabric_loader")}")
    modImplementation("net.fabricmc.fabric-api:fabric-api:${property("deps.fabric_api")}")
    modImplementation("teamreborn:energy:${property("deps.energy")}")
    include("teamreborn:energy:${property("deps.energy")}")
    compileOnly("mezz.jei:jei-$mc-common-api:${property("deps.jei")}")
    compileOnly("org.jspecify:jspecify:1.0.0")
}

loom {
    fabricModJsonPath = rootProject.file(if (modern) "src/main/resources/fabric.mod.json" else if (sc.current.parsed < "1.21") "src/legacy/1.20.1/fabric.mod.json" else "src/legacy/1.21.1/fabric.mod.json")
    accessWidenerPath = rootProject.file(if (modern) "src/main/resources/simplemachines.accesswidener" else if (sc.current.parsed < "1.21") "src/legacy/1.20.1/simplemachines.accesswidener" else "src/legacy/1.21.1/simplemachines.accesswidener")
    runConfigs.all {
        runDirectory = rootProject.file("runs/${sc.current.project}")
    }
    providers.gradleProperty("quickPlay").orNull?.let { host -> runs.named("client") { programArgs("--quickPlayMultiplayer", host, "--username", "Dev") } }
}

sourceSets.main {
    if (extras.isNotEmpty()) applyShip(rootProject.file("."), extras)
    java.exclude("com/workshopcraft/simplemachines/neoforge/**", "com/workshopcraft/simplemachines/forge/**")
    if (!modern) resources.setSrcDirs(listOf("1.20.1", "1.21.1").filter { sc.current.parsed < "1.21" || it == "1.21.1" }.map { rootProject.file("src/legacy/$it") } + rootProject.file("src/main/resources"))
    if (sc.current.parsed < "1.21") resources.srcDir(tasks.register<Data120>("data120") {
        sources.from(rootProject.file("src/legacy/1.21.1"), rootProject.file("src/main/resources"))
        forge = false
        outputDir = layout.buildDirectory.dir("generated/data120")
    }.map { it.outputDir })
    // Whole-file replacements for old targets; the main copy of each file is left out
    if (!modern) applyOverlays(rootProject.file("."), overlayChain(sc.current.version, "fabric"), layout.buildDirectory.get().asFile)
}

java {
    withSourcesJar()
    targetCompatibility = requiredJava
    sourceCompatibility = requiredJava
    toolchain.languageVersion = JavaLanguageVersion.of(requiredJava.majorVersion)
}

tasks {
    processResources {
        if (extras.isNotEmpty()) mergeShipJson(rootProject.file("."), extras)
        val props = mapOf(
            "id" to sc.properties.get<String>("mod.id"),
            "name" to sc.properties.get<String>("mod.name"),
            "version" to modVersion,
            "author" to sc.properties.get<String>("mod.author"),
            "description" to sc.properties.get<String>("mod.description"),
            "minecraft" to sc.properties.get<String>("mod.mc_compat"),
            "loader" to if (mc == "1.20.1") "0.16.0" else Regex("\\d+\\.\\d+\\.\\d+").find(sc.properties.get<String>("deps.fabric_loader"))!!.value,
            "java" to requiredJava.majorVersion,
            "energy" to sc.properties.get<String>("deps.energy"),
        )
        inputs.properties(props)
        filesMatching(listOf("fabric.mod.json", "pack.mcmeta")) { expand(props) }
        if (sc.current.parsed < "26.3") exclude("data/simplemachines/context_int_provider/**")
        if (sc.current.parsed >= "26.3") filesMatching("data/simplemachines/advancement/**") { filter { it.replace("\"recipe\":", "\"recipes\":") } }
        if (sc.current.parsed >= "26.3") filesMatching("data/simplemachines/loot_table/**") { filter(LootTable263::class.java) }
        exclude("META-INF/neoforge.mods.toml", "META-INF/mods.toml")
        if (!modern) exclude("pack.mcmeta", "META-INF/accesstransformer.cfg", "assets/simplemachines/items/**")
        if (sc.current.parsed < "1.21") exclude("data/simplemachines/recipe/**", "data/simplemachines/loot_table/**", "data/*/advancement/**", "data/*/tags/block/**", "data/*/tags/item/**", "data/neoforge/**", "simplemachines.neoforge.mixins.json", "simplemachines.forge.mixins.json")
        duplicatesStrategy = DuplicatesStrategy.EXCLUDE
    }

    jar {
        manifest.attributes(
            "Specification-Title" to sc.properties.get<String>("mod.name"),
            "Specification-Vendor" to sc.properties.get<String>("mod.author"),
            "Specification-Version" to modVersion,
            "Implementation-Title" to "fabric",
            "Implementation-Version" to modVersion,
            "Implementation-Vendor" to sc.properties.get<String>("mod.author"),
            "Built-On-Minecraft" to mc,
        )
    }

    withType<Jar> {
        duplicatesStrategy = DuplicatesStrategy.EXCLUDE
        val name = project.property("mod.name")
        inputs.property("mod_name", name)
        from(rootProject.file("LICENSE")) { rename { "${it}_$name" } }
    }
}
