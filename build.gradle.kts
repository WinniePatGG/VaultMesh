plugins {
    id("java-library")
    id("com.gradleup.shadow") version "8.3.5"
    id("xyz.jpenilla.run-paper") version "2.3.1"
}

repositories {
    mavenCentral()
    maven("https://repo.papermc.io/repository/maven-public/")
    maven("https://repo.codemc.io/repository/maven-public/")
}

// Multiversion support (1.16.3 - 26.3 single jar):
//
// * The plugin is compiled against the OLDEST supported API (1.16-era Paper, which
//   still lives under the com.destroystokyo.paper coordinates) with Java 8
//   bytecode. Java 8 class files load on every runtime a Paper server can use,
//   from Java 8 (1.16.x) over 17/21 up to the Java 25 runtime required by 26.x.
// * Source code must therefore stay Java 8 compatible: no switch-expression
//   arrows, no instanceof pattern binding, no List.of/Map.copyOf/var/Stream#toList().
// * Only APIs present with identical binary signatures in every Paper build from
//   1.16.5 through 26.3 may be used (verified: Adventure ItemMeta/Component,
//   PersistentDataContainer on TileState, createInventory(Holder,int,Component),
//   getRecipe/removeRecipe/getOfflinePlayerIfCached). Never touch InventoryView
//   methods directly: it changed from abstract class (<=1.20.x) to interface
//   (1.21+) and would throw IncompatibleClassChangeError on modern servers.
//
// Compile API: com.destroystokyo.paper:paper-api 1.16.5-R0.1-SNAPSHOT
// Lowest tested bytecode: Java 8 (major 52), bStats 3.2.1 is class version 52 too.

val javaRelease: Int = providers.gradleProperty("javaRelease").orElse("8").get().toInt()

// Version used for the local runPaper dev server. Override with -PdevMinecraftVersion=, e.g.
//   gradlew runServer -PdevMinecraftVersion=1.16.5
val devMinecraftVersion: String = providers.gradleProperty("devMinecraftVersion").orElse("1.21.11").get()

dependencies {
    compileOnly("com.destroystokyo.paper:paper-api:1.16.5-R0.1-SNAPSHOT")
    implementation("org.bstats:bstats-bukkit:3.2.1")
}

java {
    toolchain.languageVersion = JavaLanguageVersion.of(21)
}

tasks {
    compileJava {
        options.encoding = "UTF-8"
        options.release = javaRelease
        options.compilerArgs.add("-Xlint:-options")
    }

    jar {
        enabled = false
    }

    shadowJar {
        archiveClassifier.set("")
        relocate("org.bstats", "de.winniepat.vaultMesh.libs.bstats")
    }

    runServer {
        // Configure the Minecraft version for our task.
        // This is the only required configuration besides applying the plugin.
        // Your plugin's jar (or shadowJar if present) will be used automatically.
        minecraftVersion(devMinecraftVersion)
        jvmArgs("-Xms2G", "-Xmx2G", "-Dcom.mojang.eula.agree=true")
    }

    processResources {
        val props = mapOf("version" to version, "description" to project.description)
        filesMatching("plugin.yml") {
            expand(props)
        }
    }
}
