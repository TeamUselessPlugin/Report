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
                from(archiveFile)
                val newPluginFileLocation = File(rootDir, ".dev/plugins")
                if (File(newPluginFileLocation, archiveFileName.get()).exists()) into(
                    File(
                        newPluginFileLocation,
                        "update"
                    )
                ) else into(newPluginFileLocation)
            }
        }
    }
}