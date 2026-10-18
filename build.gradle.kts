import java.util.zip.ZipFile
import javax.imageio.ImageIO

// NeoForge 26.3 build of Power Grid for Zoompack 5, against ZP5's NeoForge port of Create Fly.
// See PORTING.md. Mirrors ZP5's mods/build-logic zp5.neoforge-mod convention (ModDevGradle 2.0.148).
plugins {
    `java-library`
    idea
    id("net.neoforged.moddev") version "2.0.148"
}

group = property("maven_group") as String
version = "${property("mod_version")}+${property("port_suffix")}"

base {
    archivesName.set(property("archives_base_name") as String)
}

repositories {
    mavenCentral()
    exclusiveContent {
        forRepository { maven("https://api.modrinth.com/maven") }
        filter { includeGroup("maven.modrinth") }
    }
}

java.toolchain.languageVersion.set(JavaLanguageVersion.of(25))

sourceSets {
    main {
        resources.srcDir("src/generated/resources")
        java {
            // Fabric entrypoints, replaced by org/patryk3211/powergrid/neoforge/** (see PORTING.md)
            exclude("org/patryk3211/powergrid/fabric/**")
            // REI / Create REI Viewer plugins: REI has no NeoForge 26.3 build in the pack (JEI is a TODO)
            exclude("org/patryk3211/powergrid/compat/rei/**")
            exclude("org/patryk3211/powergrid/compat/viewer/**")
            exclude("org/patryk3211/powergrid/compat/fabric/**")
            // Farmer's Delight has no NeoForge 26.3 build in the pack
            exclude("org/patryk3211/powergrid/compat/farmersdelight/**")
            exclude("org/patryk3211/powergrid/mixin/compat/farmersdelight/**")
        }
        resources {
            exclude("fabric.mod.json")
            exclude("powergrid.accesswidener")
            exclude("powergrid.farmersdelight.mixins.json")
        }
    }
}

val localRuntime: Configuration by configurations.creating
configurations.runtimeClasspath.get().extendsFrom(localRuntime)

val createJar = files(property("create_jar") as String)

neoForge {
    version = property("neo_version") as String

    accessTransformers {
        from(file("src/main/resources/META-INF/accesstransformer.cfg"))
        // Create's own widenings, which it applies at runtime too
        from(file("libs/create-accesstransformer.cfg"))
    }
    // Create's compile-time view of the interfaces its mixins add to Container / WorldlyContainer
    interfaceInjectionData {
        from(file("libs/interfaces.json"))
    }

    mods {
        create("powergrid") {
            sourceSet(sourceSets.main.get())
        }
    }

    // JUnit tests (electric solver, recipe codec) compile and run against Minecraft on a plain classpath, as on
    // Fabric Loom. MDG's FML unit-test launcher (junit-fml 12) fails to start on this setup.
    addModdingDependenciesTo(sourceSets.test.get())

    runs {
        create("client") {
            client()
            systemProperty("neoforge.enabledGameTestNamespaces", "powergrid")
        }
        create("server") {
            server()
            programArgument("--nogui")
            systemProperty("neoforge.enabledGameTestNamespaces", "powergrid")
        }
        create("gameTestServer") {
            type = "gameTestServer"
            systemProperty("neoforge.enabledGameTestNamespaces", "powergrid")
        }
        configureEach {
            systemProperty("forge.logging.markers", "REGISTRIES")
            logLevel = org.slf4j.event.Level.INFO
            if (project.hasProperty("mixinDebug")) {
                systemProperty("mixin.debug.verbose", "true")
                systemProperty("mixin.debug.export", "true")
            }
        }
    }
}

dependencies {
    implementation(createJar)

    val ejml = property("ejml_version")
    for (module in listOf("ejml-core", "ejml-simple", "ejml-ddense", "ejml-dsparse")) {
        implementation("org.ejml:$module:$ejml")
        jarJar("org.ejml:$module:$ejml")
    }

    // Iris shader compat (IrisPipelineCompat), only used when Iris is installed
    compileOnly("maven.modrinth:iris:${property("iris_version")}")
    compileOnly("com.google.code.findbugs:jsr305:3.0.2")

    testImplementation("org.junit.jupiter:junit-jupiter:${property("junit_version")}")
    testRuntimeOnly("org.junit.platform:junit-platform-launcher")
}

tasks.withType<JavaCompile>().configureEach {
    options.encoding = "UTF-8"
    options.release.set(25)
    options.compilerArgs.addAll(listOf("-Xmaxerrs", "10000", "-Xmaxwarns", "1000"))
}

java {
    withSourcesJar()
}

tasks.withType<AbstractCopyTask>().configureEach {
    duplicatesStrategy = DuplicatesStrategy.EXCLUDE
}

// Expands ${...} placeholders in src/main/templates (neoforge.mods.toml) from gradle.properties.
val generateModMetadata = tasks.register<ProcessResources>("generateModMetadata") {
    val replaceProperties = mapOf(
        "minecraft_version_range" to project.property("minecraft_version_range") as String,
        "neo_version_range" to project.property("neo_version_range") as String,
        "create_version_range" to project.property("create_version_range") as String,
        "mod_version" to project.version.toString(),
    )
    inputs.properties(replaceProperties)
    expand(replaceProperties)
    from("src/main/templates")
    into(layout.buildDirectory.dir("generated/sources/modMetadata"))
}
sourceSets.main.get().resources.srcDir(generateModMetadata)
neoForge.ideSyncTask(generateModMetadata)

val generatedConnectedTextureResources = layout.buildDirectory.dir("generated/connected-texture-resources")

val omniTileIndexes = listOf(
    1, 2, 3, 8, 9, 10, 11, 12, 13, 16, 17, 18, 19, 20, 21, 24, 25, 26, 27, 28, 29, 30,
    32, 33, 34, 35, 36, 37, 38, 40, 41, 42, 43, 44, 45, 46, 48, 49, 50, 51, 52, 53, 54, 56, 57, 58,
)
val rectangleTileIndexes = (0..11).toList() + (13..15).toList()

val connectedTextureSheets = mapOf(
    "assets/powergrid/textures/block/conductive_casing_connected.png" to (8 to omniTileIndexes),
    "assets/powergrid/textures/block/copper_plating_connected.png" to (8 to omniTileIndexes),
    "assets/powergrid/textures/block/solar_panel/solar_panel_connected.png" to (8 to omniTileIndexes),
    "assets/powergrid/textures/block/solar_panel/copper_plating_connected.png" to (8 to omniTileIndexes),
    "assets/powergrid/textures/block/battery/battery_side_connected.png" to (4 to rectangleTileIndexes),
    "assets/powergrid/textures/block/battery/battery_top_connected.png" to (4 to rectangleTileIndexes),
)

val sheetsSampledByModels = setOf(
    "assets/powergrid/textures/block/solar_panel/solar_panel_connected.png",
)

val generateConnectedTextureSprites = tasks.register("generateConnectedTextureSprites") {
    val resourceRoot = file("src/main/resources")
    inputs.files(fileTree(resourceRoot) { include(connectedTextureSheets.keys) })
        .withPropertyName("connectedTextureSheets")
        .withPathSensitivity(PathSensitivity.RELATIVE)
    inputs.property("connectedTextureLayout", "create-fly-26.2-v1")
    outputs.dir(generatedConnectedTextureResources)
    doLast {
        val outputRoot = generatedConnectedTextureResources.get().asFile
        delete(outputRoot)
        var sheetCount = 0
        var spriteCount = 0
        connectedTextureSheets.forEach { (pattern, layout) ->
            val (gridSize, tileIndexes) = layout
            fileTree(resourceRoot) { include(pattern) }.files.sortedBy { it.invariantSeparatorsPath }.forEach { sheetFile ->
                val sheet = ImageIO.read(sheetFile) ?: throw GradleException("Could not decode $sheetFile")
                if (sheet.width != sheet.height || sheet.width % gridSize != 0) {
                    throw GradleException("$sheetFile must be a $gridSize x $gridSize grid of square tiles, but is ${sheet.width} x ${sheet.height}")
                }
                val tileSize = sheet.width / gridSize
                val relativeSheet = resourceRoot.toPath().relativize(sheetFile.toPath()).toString()
                val spriteDirectory = outputRoot.resolve(relativeSheet.removeSuffix(".png"))
                spriteDirectory.mkdirs()
                tileIndexes.forEachIndexed { index, sourceTileIndex ->
                    val tile = sheet.getSubimage(sourceTileIndex % gridSize * tileSize, sourceTileIndex / gridSize * tileSize, tileSize, tileSize)
                    if (!ImageIO.write(tile, "png", spriteDirectory.resolve("${index + 1}.png"))) {
                        throw GradleException("No PNG writer is available for $spriteDirectory")
                    }
                    spriteCount++
                }
                sheetCount++
            }
        }
        logger.lifecycle("Generated $spriteCount connected-texture sprites from $sheetCount sheets")
    }
}

tasks.processResources {
    dependsOn(generateConnectedTextureSprites)
    from(generatedConnectedTextureResources)
    exclude(connectedTextureSheets.keys - sheetsSampledByModels)
    duplicatesStrategy = DuplicatesStrategy.EXCLUDE
}

tasks.test {
    useJUnitPlatform()
    // Needs a running FML loader (SharedConstants -> FMLEnvironment); the content_registered GameTest checks that the
    // shipped light_cord_patterning recipe loads instead
    exclude("org/patryk3211/recipe/**")
}

tasks.jar {
    from("LICENSE")
    from("NOTICE")
    from("licenses") { into("licenses") }
}

tasks.named<Jar>("sourcesJar") {
    from("LICENSE")
    from("NOTICE")
    from("licenses") { into("licenses") }
}

val jarContainmentPrefix = "org/patryk3211/powergrid/"

val checkJarContainment = tasks.register("checkJarContainment") {
    description = "Fails if the mod jar or the sources jar carries a class or source file outside Power Grid's own namespace."
    group = "verification"
    val archives = files(
        tasks.named<Jar>("jar").flatMap { it.archiveFile },
        tasks.named<Jar>("sourcesJar").flatMap { it.archiveFile }
    )
    dependsOn(tasks.named("jar"), tasks.named("sourcesJar"))
    inputs.files(archives)
    val prefix = jarContainmentPrefix
    doLast {
        val offenders = mutableListOf<String>()
        archives.forEach { archive ->
            ZipFile(archive).use { zip ->
                val names = zip.entries().toList().map { it.name }
                for (name in names) {
                    if (!name.endsWith(".class") && !name.endsWith(".java")) continue
                    if (name.startsWith(prefix)) continue
                    offenders += archive.name + " -> " + name
                }
            }
        }
        if (offenders.isNotEmpty()) {
            throw GradleException(
                "Power Grid must ship nothing outside " + prefix + "; found " + offenders.size +
                    " foreign entries:\n" + offenders.joinToString("\n") { "  " + it }
            )
        }
    }
}

tasks.named("check") {
    dependsOn(checkJarContainment)
}

tasks.register("printCompileClasspath") {
    val cp = sourceSets.main.get().compileClasspath
    val out = layout.projectDirectory.file(".classpath.txt")
    doLast {
        out.asFile.writeText(cp.files.joinToString("\n") { it.absolutePath } + "\n")
    }
}

tasks.register("printTestClasspath") {
    val cp = sourceSets.test.get().compileClasspath + sourceSets.test.get().runtimeClasspath
    val out = layout.projectDirectory.file(".testclasspath.txt")
    doLast {
        out.asFile.writeText(cp.files.joinToString("\n") { it.absolutePath } + "\n")
    }
}
