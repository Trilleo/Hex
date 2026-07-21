import org.jetbrains.kotlin.gradle.dsl.JvmTarget

plugins {
	id("net.fabricmc.fabric-loom")
	`maven-publish`
	id("org.jetbrains.kotlin.jvm") version "2.4.10"
}

version = providers.gradleProperty("mod_version").get()
group = providers.gradleProperty("maven_group").get()

repositories {
	// Add repositories to retrieve artifacts from in here.
	// You should only use this when depending on other mods because
	// Loom adds the essential maven repositories to download Minecraft and libraries from automatically.
	// See https://docs.gradle.org/current/userguide/declaring_repositories.html
	// for more information about repositories.
	maven("https://maven.shedaniel.me/") { name = "Shedaniel" }
	maven("https://maven.terraformersmc.com/releases/") { name = "TerraformersMC" }
}

dependencies {
	// To change the versions see the gradle.properties file
	minecraft("com.mojang:minecraft:${providers.gradleProperty("minecraft_version").get()}")
	implementation("net.fabricmc:fabric-loader:${providers.gradleProperty("loader_version").get()}")

	// Fabric API. This is technically optional, but you probably want it anyway.
	implementation("net.fabricmc.fabric-api:fabric-api:${providers.gradleProperty("fabric_api_version").get()}")
    implementation("net.fabricmc:fabric-language-kotlin:${providers.gradleProperty("fabric_kotlin_version").get()}")

	// Cloth Config backs the settings menu and is a hard dependency — users install it themselves, it is not
	// bundled. Both it and Mod Menu declare a Fabric API dependency of their own; without the exclusions Gradle
	// resolves to whichever version is newer and silently replaces the fabric_api_version pinned above.
	//
	// The errorprone exclusion is not cosmetic: Cloth pulls in toml4j, which requests gson, which brings gson's
	// full Maven metadata into the runtime classpath and with it error_prone_annotations. Those are build-time
	// annotations that nothing needs at runtime, and leaving them in makes runClient fail outright on any
	// network that cannot reach Maven Central.
	implementation("me.shedaniel.cloth:cloth-config-fabric:${providers.gradleProperty("cloth_config_version").get()}") {
		exclude(group = "net.fabricmc.fabric-api")
		exclude(group = "com.google.errorprone")
	}

	// Mod Menu is optional at runtime: it is the only thing that reads the "modmenu" entrypoint, so with it
	// absent the integration class is never loaded. compileOnly keeps it off the runtime classpath and out of
	// the published POM; localRuntime puts it in the dev client so the mod-list button can be tested.
	compileOnly("com.terraformersmc:modmenu:${providers.gradleProperty("modmenu_version").get()}") {
		exclude(group = "net.fabricmc.fabric-api")
	}
	localRuntime("com.terraformersmc:modmenu:${providers.gradleProperty("modmenu_version").get()}") {
		exclude(group = "net.fabricmc.fabric-api")
	}
}

tasks.processResources {
	val props = mapOf(
		"version" to version,
		"minecraft_version" to providers.gradleProperty("minecraft_version").get(),
		"loader_version" to providers.gradleProperty("loader_version").get(),
		"cloth_config_min_version" to providers.gradleProperty("cloth_config_min_version").get(),
	)
	props.forEach { (k, v) -> inputs.property(k, v) }

	filesMatching("fabric.mod.json") {
		expand(props)
	}
}

tasks.withType<JavaCompile>().configureEach {
	options.release = 25
}

kotlin {
	compilerOptions {
		jvmTarget = JvmTarget.JVM_25
	}
}

java {
	// Loom will automatically attach sourcesJar to a RemapSourcesJar task and to the "build" task
	// if it is present.
	// If you remove this line, sources will not be generated.
	withSourcesJar()

	sourceCompatibility = JavaVersion.VERSION_25
	targetCompatibility = JavaVersion.VERSION_25
}

tasks.jar {
	val projectName = project.name
	inputs.property("projectName", projectName)

	from("LICENSE") {
		rename { "${it}_$projectName" }
	}
}

// configure the maven publication
publishing {
	publications {
		register<MavenPublication>("mavenJava") {
			from(components["java"])
		}
	}

	// See https://docs.gradle.org/current/userguide/publishing_maven.html for information on how to set up publishing.
	repositories {
		// Add repositories to publish to here.
		// Notice: This block does NOT have the same function as the block in the top level.
		// The repositories here will be used for publishing your artifact, not for
		// retrieving dependencies.
	}
}
