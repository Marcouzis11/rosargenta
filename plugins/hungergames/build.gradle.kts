plugins {
    java
    id("com.gradleup.shadow") version "8.3.5"
}

group = "me.aymanisam"
version = "1.9.1-beta"
val spigotAPIVersion = "1.21.4"

repositories {
    mavenCentral()
    maven("https://repo.papermc.io/repository/maven-public/")
    mavenLocal()
    maven("https://hub.spigotmc.org/nexus/content/repositories/snapshots/")
    maven("https://oss.sonatype.org/content/groups/public/")
    maven("https://jitpack.io")
    maven("https://repo.codemc.io/repository/maven-releases/")
    maven("https://repo.codemc.io/repository/maven-snapshots/")
    maven("https://repo.extendedclip.com/releases/")
}

dependencies {
    compileOnly("org.spigotmc:spigot-api:$spigotAPIVersion-R0.1-SNAPSHOT")
    implementation("commons-io:commons-io:2.14.0")
    implementation("org.bstats:bstats-bukkit:2.2.1")
    compileOnly("com.github.retrooper:packetevents-spigot:2.7.0")
    implementation("com.googlecode.json-simple:json-simple:1.1.1")
    implementation("fr.mrmicky:fastboard:2.2.2")
    implementation("com.mysql:mysql-connector-j:8.4.0")
    compileOnly("me.clip:placeholderapi:2.11.6")
    testImplementation("org.mockbukkit.mockbukkit:mockbukkit-v1.21:4.33.2")
    testImplementation("org.junit.jupiter:junit-jupiter:5.11.4")
    testImplementation("org.mockito:mockito-core:5.15.2")
}

tasks.test {
    useJUnitPlatform()
    systemProperty("junit.jupiter.extensions.autodetection.enabled", "true")
}

val targetJavaVersion = 21
java {
    val javaVersion = JavaVersion.toVersion(targetJavaVersion)
    sourceCompatibility = javaVersion
    targetCompatibility = javaVersion
    toolchain.languageVersion.set(JavaLanguageVersion.of(targetJavaVersion))
}

tasks.withType<JavaCompile> {
    options.encoding = "UTF-8"
    if (targetJavaVersion >= 10 || JavaVersion.current().isJava10Compatible) {
        options.release.set(targetJavaVersion)
    }
}

tasks.shadowJar {
    archiveFileName.set("Hungergames-$version.jar")
    relocate("org.bstats", "${project.group}.bstats")
    relocate("fr.mrmicky.fastboard", "${project.group}.fastboard")
    archiveClassifier.set("")
    providers.environmentVariable("OUTPUT_DIR").orNull?.takeIf { it.isNotBlank() }?.let {
        destinationDirectory.set(file(it))
    }
    mergeServiceFiles()
}

tasks.processResources {
    val props = mapOf("version" to version)
    inputs.properties(props)
    filteringCharset = "UTF-8"
    filesMatching("plugin.yml") {
        expand(props)
    }
}
