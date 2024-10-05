package io.github.mishkun.ataman

import com.intellij.openapi.application.ApplicationInfo
import com.intellij.openapi.project.Project
import com.intellij.openapi.startup.ProjectActivity

class PluginStartup : ProjectActivity {
    override suspend fun execute(project: Project) {
        val configDir = Config().configDir
        val build = ApplicationInfo.getInstance().build.productCode
        updateConfig(project, configDir, build)
    }
}
