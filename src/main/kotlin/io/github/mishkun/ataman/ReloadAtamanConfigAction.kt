package io.github.mishkun.ataman

import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.application.ApplicationInfo
import com.intellij.openapi.project.DumbAwareAction

class ReloadAtamanConfigAction : DumbAwareAction() {
    override fun actionPerformed(e: AnActionEvent) {
        val project = checkNotNull(e.project)
        val build = ApplicationInfo.getInstance().build.productCode
        updateConfig(project, Config().configDir, build)
    }
}
