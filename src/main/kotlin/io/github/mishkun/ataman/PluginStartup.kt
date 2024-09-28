package io.github.mishkun.ataman

import com.intellij.openapi.project.Project
import com.intellij.openapi.startup.StartupActivity

class PluginStartup : StartupActivity.DumbAware/*, LightEditCompatible*/ {
    override fun runActivity(project: Project) {
        updateConfig(project)
    }
}
