import com.github.jengelman.gradle.plugins.shadow.tasks.ShadowJar

val name: String by project
val version: String by project
val targetMC: String by project

plugins {
    kotlin("jvm") version "1.9.21"
    id("com.github.johnrengelman.shadow") version "8.1.1"
}

kotlin {
    jvmToolchain(21)
}

repositories {
    mavenCentral()
    maven("https://repo.purpurmc.org/snapshots")
}

dependencies {
    compileOnly("org.purpurmc.purpur", "purpur-api", "${targetMC}-R0.1-SNAPSHOT") // PurpurMC API
    compileOnly("dev.jorel", "commandapi-bukkit-core", "9.3.0") // CommandAPI Dev Only
//    compileOnly(fileTree(mapOf("dir" to "libs", "include" to listOf("*.jar")))) // Load all jars in libs folder (Local Libraries)

    implementation("dev.jorel", "commandapi-bukkit-shade", "9.3.0") // CommandAPI Shade
    implementation("io.github.monun:heartbeat-coroutines:0.0.5") // Heartbeat Coroutines
}

tasks {
    processResources {
        filesMatching("**/*.yml") {
            expand(project.properties)
        }
    }

    project.delete(
        file("build/resources")
    )

    register<ShadowJar>("purpurJar") {
        archiveBaseName.set(project.name)
        archiveVersion.set(version)
        archiveClassifier.set("")
        configurations = listOf(project.configurations.runtimeClasspath.get())
        from(sourceSets["main"].output, "LICENSE", "README.MD")

        doLast {
            copy {
                val archiveFileName = "${project.name}-dev.jar"

                from(archiveFile)
                rename { archiveFileName }

                val newPluginFileLocation = File("\\\\192.168.123.107\\Users\\User\\Desktop\\DEV\\plugins") // DevServer

                if (File(newPluginFileLocation, archiveFileName).exists()) {
                    into(File(newPluginFileLocation, "update"))
                    File(newPluginFileLocation, "update/RELOAD").delete()
                } else {
                    into(newPluginFileLocation)
                }
            }
        }
    }
}