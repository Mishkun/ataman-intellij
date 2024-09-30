package io.github.mishkun.ataman

import com.intellij.openapi.project.Project
import com.intellij.openapi.startup.ProjectActivity

class PluginStartup : ProjectActivity {
    override suspend fun execute(project: Project) {
        updateConfig(project)
    }
}
