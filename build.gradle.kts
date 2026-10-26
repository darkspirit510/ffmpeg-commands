import org.jetbrains.kotlin.gradle.dsl.JvmTarget
import org.jetbrains.kotlin.gradle.tasks.KotlinCompile

plugins {
    kotlin("jvm") version "2.4.21"
    application
}

group = "de.darkspirit510"
// The single source of the tool version. The video stream of every encode is tagged with it, see AGENTS.md.
version = "1.0"

repositories {
    mavenCentral()
}

val generateToolVersion by tasks.registering {
    val toolVersion = project.version.toString()
    val outputDir = layout.buildDirectory.dir("generated/sources/toolVersion/kotlin")

    inputs.property("toolVersion", toolVersion)
    outputs.dir(outputDir)

    doLast {
        outputDir.get().file("ToolVersion.kt").asFile.writeText(
            "// Generated from the version in build.gradle.kts, do not edit.\n" +
                "const val TOOL_VERSION = \"$toolVersion\"\n"
        )
    }
}

kotlin {
    sourceSets.main {
        kotlin.srcDir(generateToolVersion)
    }
}

dependencies {
    testImplementation(kotlin("test"))
    testImplementation(sourceSets.main.get().output)
}

tasks.test {
    useJUnitPlatform()

    // Some tests run the packaged jar, so build it first and tell the tests where it is.
    dependsOn(tasks.jar)
    systemProperty("jar.path", tasks.jar.get().archiveFile.get().asFile.absolutePath)
}

tasks.withType<KotlinCompile> {
    compilerOptions {
        jvmTarget.set(JvmTarget.JVM_1_8)
    }
}

tasks.withType<JavaCompile> {
    options.release.set(8)
}

application {
    mainClass.set("CommandCreatorKt")
}

tasks.jar {
    manifest {
        attributes("Main-Class" to "CommandCreatorKt")
    }

    // To avoid the duplicate handling strategy error
    duplicatesStrategy = DuplicatesStrategy.EXCLUDE

    // To add all the dependencies
    from(sourceSets.main.get().output)

    dependsOn(configurations.runtimeClasspath)
    from({
        configurations
            .runtimeClasspath
            .get()
            .filter { it.name.endsWith("jar") }
            .map { zipTree(it) }
    })
}
