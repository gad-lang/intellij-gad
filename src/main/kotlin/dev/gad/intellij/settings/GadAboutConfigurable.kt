package dev.gad.intellij.settings

import com.intellij.openapi.options.BoundConfigurable
import com.intellij.openapi.options.Configurable
import com.intellij.openapi.ui.DialogPanel
import com.intellij.ui.dsl.builder.panel

/**
 * Settings ▸ Build, Execution, Deployment ▸ Gad ▸ About — the installed plugin's
 * version and the git commit (id + time) it was built from (see GadBuildInfo).
 */
class GadAboutConfigurable : BoundConfigurable("About"), Configurable {
    override fun createPanel(): DialogPanel = panel {
        row("Plugin:") { label("Gad Language ${GadBuildInfo.version}") }
        if (GadBuildInfo.commitShort.isNotBlank()) {
            row("Commit:") {
                browserLink(
                    GadBuildInfo.commitShort,
                    "https://github.com/gad-lang/intellij-gad/commit/${GadBuildInfo.commit}",
                )
            }
        }
        if (GadBuildInfo.commitTime.isNotBlank()) {
            row("Commit time:") { label(GadBuildInfo.commitTime) }
        }
        if (GadBuildInfo.buildTime.isNotBlank()) {
            row("Built:") { label(GadBuildInfo.buildTime) }
        }
        separator()
        row { browserLink("Website", "https://gad-lang.github.io/intellij-gad/") }
        row { browserLink("Repository", "https://github.com/gad-lang/intellij-gad") }
    }

    // Read-only page: nothing to persist.
    override fun isModified(): Boolean = false
    override fun apply() {}
}
