pluginManagement {
    val downloadPluginVersion: String by settings
    val kotlinVersion: String by settings
    val ktlintPluginVersion: String by settings
    val osdetectorPluginVersion: String by settings
    val protoPluginVersion: String by settings
    val versionsPluginVersion: String by settings

    plugins {
        kotlin("jvm") version kotlinVersion
        id("com.github.ben-manes.versions") version versionsPluginVersion
        id("com.google.osdetector") version osdetectorPluginVersion
        id("com.google.protobuf") version protoPluginVersion
        id("de.undercouch.download") version downloadPluginVersion
        id("org.jlleitschuh.gradle.ktlint") version ktlintPluginVersion
    }
}

rootProject.name = "envoy-control-plane-kotlin"
