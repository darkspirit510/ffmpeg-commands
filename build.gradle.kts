import org.jetbrains.kotlin.gradle.dsl.JvmTarget
import org.jetbrains.kotlin.gradle.tasks.KotlinCompile

plugins {
    kotlin("jvm") version "2.4.20"
    application
}

group = "de.darkspirit510"
version = "1.0-SNAPSHOT"

repositories {
    mavenCentral()
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
