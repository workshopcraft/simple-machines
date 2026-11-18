plugins {
    id("net.neoforged.moddev.legacyforge")
    id("neoforge-mutex")
}

val modVersion = providers.gradleProperty("version").orNull ?: sc.properties.get<String>("mod.version")
val mc = sc.current.version
val extras = extraDirs(mc)
version = modVersion
group = property("mod.group") as String
base.archivesName = "${property("mod.id")}-forge-$mc"

repositories {
    exclusiveContent {
        forRepository { maven("https://maven.blamejared.com") { name = "BlameJared" } }
        filter { includeGroup("mezz.jei") }
    }
}

sourceSets.main {
    java.exclude("com/workshopcraft/simplemachines/fabric/**", "com/workshopcraft/simplemachines/neoforge/**", "com/workshopcraft/simplemachines/mixin/**")
    resources.setSrcDirs(listOf(rootProject.file("src/legacy/1.20.1"), rootProject.file("src/legacy/1.21.1")) +
        extraLegacyDirs(rootProject.file("."), extras, listOf("1.20.1", "1.21.1")) + rootProject.file("src/main/resources"))
    resources.srcDir(tasks.register<Data120>("data120") {
        sources.from(listOf(rootProject.file("src/legacy/1.21.1")) + extraLegacyDirs(rootProject.file("."), extras, listOf("1.20.1", "1.21.1")) +
            rootProject.file("src/main/resources") + extras.map { rootProject.file("$it/resources") })
        forge = true
        outputDir = layout.buildDirectory.dir("generated/data120")
    }.map { it.outputDir })
    applyOverlays(rootProject.file("."), overlayChain(sc.current.version, "forge"), layout.buildDirectory.get().asFile)
    skipDisabledExtras(mc)
}
if (extras.isNotEmpty()) applyShip(rootProject.file("."), extras)
// the portal chunk generator extends NoiseBasedChunkGenerator, which is final on 1.20.1
val portalAt = rootProject.file("src/portal/legacy/1.20.1/META-INF/accesstransformer.cfg")

legacyForge {
    version = property("deps.forge") as String
    if (Portal.enabled(mc)) accessTransformers.files.setFrom(portalAt) else accessTransformers.files.setFrom()
    mods {
        register(property("mod.id") as String) {
            sourceSet(sourceSets.main.get())
        }
    }
    runs {
        register("client") {
            gameDirectory = rootProject.file("runs/${sc.current.project}")
            client()
            programArguments.addAll(providers.gradleProperty("quickPlay").map { listOf("--quickPlayMultiplayer", it) }.orElse(listOf()))
        }
        register("server") {
            gameDirectory = rootProject.file("runs/${sc.current.project}-server")
            server()
        }
    }
}

mixin {
    add(sourceSets.main.get(), "simplemachines.refmap.json")
    config("simplemachines.forge.mixins.json")
}

dependencies {
    annotationProcessor("org.spongepowered:mixin:0.8.5:processor")
    compileOnly("mezz.jei:jei-$mc-common-api:${property("deps.jei")}")
    compileOnly("org.jspecify:jspecify:1.0.0")
}

java {
    withSourcesJar()
    targetCompatibility = JavaVersion.VERSION_17
    sourceCompatibility = JavaVersion.VERSION_17
    toolchain.languageVersion = JavaLanguageVersion.of(17)
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
        )
        inputs.properties(props)
        filesMatching(listOf("META-INF/mods.toml", "pack.mcmeta")) { expand(props) }
        val mainAt = rootProject.file("src/main/resources/META-INF/accesstransformer.cfg")
        exclude { it.file == mainAt }
        exclude("fabric.mod.json", "META-INF/neoforge.mods.toml", "simplemachines.accesswidener",
            "assets/simplemachines/items/**", "data/simplemachines/recipe/**", "data/simplemachines/loot_table/**",
            "data/*/advancement/**", "data/*/tags/block/**", "data/*/tags/item/**", "data/neoforge/**", "data/simplemachines/context_int_provider/**", "simplemachines.mixins.json", "simplemachines.neoforge.mixins.json", "simplemachines.accesswidener")
        duplicatesStrategy = DuplicatesStrategy.EXCLUDE
    }

    named("createMinecraftArtifacts") {
        dependsOn("stonecutterGenerate")
    }

    jar {
        manifest.attributes(
            "Specification-Title" to sc.properties.get<String>("mod.name"),
            "Specification-Vendor" to sc.properties.get<String>("mod.author"),
            "Specification-Version" to modVersion,
            "Implementation-Title" to "forge",
            "Implementation-Version" to modVersion,
            "Implementation-Vendor" to sc.properties.get<String>("mod.author"),
            "Built-On-Minecraft" to mc,
            "MixinConfigs" to "simplemachines.forge.mixins.json",
        )
    }

    withType<Jar> {
        duplicatesStrategy = DuplicatesStrategy.EXCLUDE
        val name = project.property("mod.name")
        inputs.property("mod_name", name)
        from(rootProject.file("LICENSE")) { rename { "${it}_$name" } }
    }
}

addSimpleBooksApi()
