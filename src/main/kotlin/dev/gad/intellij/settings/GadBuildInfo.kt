package dev.gad.intellij.settings

import java.util.Properties

/**
 * Build metadata baked into the plugin at build time (see the `generateBuildInfo`
 * Gradle task): the plugin version and the git commit (id + time) it was built
 * from. Read from the packaged `/gad-build.properties`; blank when run from a raw
 * source tree.
 */
object GadBuildInfo {
    private val props = Properties().apply {
        GadBuildInfo::class.java.getResourceAsStream("/gad-build.properties")?.use { load(it) }
    }

    val version: String = props.getProperty("version").orEmpty().ifBlank { "dev" }
    val commit: String = props.getProperty("commit").orEmpty()
    val commitShort: String = props.getProperty("commitShort").orEmpty()
    val commitTime: String = props.getProperty("commitTime").orEmpty()
    val buildTime: String = props.getProperty("buildTime").orEmpty()
}
