plugins {
    id("com.gradleup.shadow") version "9.0.0-beta4"
    application
}

repositories {
    mavenCentral()
    maven("https://repo.papermc.io/repository/maven-public/")
}

dependencies {
    implementation("com.google.code.gson:gson:2.11.0")
    // paper libs
    implementation("org.jetbrains:annotations:26.0.1")
    implementation("org.jspecify:jspecify:1.0.0")
    implementation("net.kyori:adventure-api:4.18.0")
    implementation(platform("net.kyori:adventure-bom:4.18.0"))
    implementation("net.kyori:adventure-api")
    implementation("net.kyori:adventure-text-minimessage")
    implementation("net.kyori:adventure-text-serializer-gson")
    implementation("net.kyori:adventure-text-serializer-legacy")
    implementation("net.kyori:adventure-text-serializer-plain")
    implementation("net.kyori:adventure-text-logger-slf4j")
    implementation("org.apache.logging.log4j:log4j-api:2.17.1")
    implementation("org.slf4j:slf4j-api:2.0.9")
    implementation("com.google.guava:guava:33.4.0-jre")
    implementation("com.mojang:brigadier:1.3.10")
    implementation("net.md-5:bungeecord-chat:1.20-R0.2-deprecated+build.19") {
        exclude("com.google.guava", "guava")
    }
    implementation("org.apache.maven:maven-resolver-provider:3.9.6")
    implementation("org.apache.maven.resolver:maven-resolver-connector-basic:1.9.18")
    implementation("org.apache.maven.resolver:maven-resolver-transport-http:1.9.18")
    implementation("it.unimi.dsi:fastutil:8.5.15")
    implementation("org.yaml:snakeyaml:2.2")
    implementation("org.joml:joml:1.10.8") {
        isTransitive = false
    }
    implementation("com.googlecode.json-simple:json-simple:1.1.1") {
        isTransitive = false
    }
    implementation("org.ow2.asm:asm:9.7.1")
    implementation("org.ow2.asm:asm-commons:9.7.1")
}

java {
    toolchain.languageVersion.set(JavaLanguageVersion.of(21))
}

val modules = listOf(
    "--add-exports", "jdk.compiler/com.sun.tools.javac.tree=ALL-UNNAMED",
    "--add-exports", "jdk.compiler/com.sun.tools.javac.code=ALL-UNNAMED",
    "--add-exports", "jdk.compiler/com.sun.tools.javac.util=ALL-UNNAMED",
    "--add-exports", "jdk.javadoc/jdk.javadoc.internal.doclets.formats.html=ALL-UNNAMED",
    "--add-exports", "jdk.javadoc/jdk.javadoc.internal.doclets.toolkit=ALL-UNNAMED",
    "--add-exports", "jdk.javadoc/jdk.javadoc.internal.doclets.toolkit.util=ALL-UNNAMED",
    "--add-exports", "jdk.javadoc/jdk.javadoc.internal.doclets.toolkit.taglets=ALL-UNNAMED",
    "--add-exports", "jdk.javadoc/jdk.javadoc.internal.tool=ALL-UNNAMED"
)

tasks.withType<JavaCompile> {
    options.compilerArgs.addAll(modules)
}

application {
    mainClass.set("dev.minidigger.apidiff.Main")
    applicationDefaultJvmArgs = modules
}

