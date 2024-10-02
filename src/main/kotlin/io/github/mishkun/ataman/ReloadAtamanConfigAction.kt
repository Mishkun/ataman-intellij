package io.github.mishkun.ataman

import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.components.service
import com.intellij.openapi.project.DumbAwareAction

class ReloadAtamanConfigAction : DumbAwareAction() {
    override fun actionPerformed(e: AnActionEvent) {
        val project = checkNotNull(e.project)
        updateConfig(project, service<ConfigService>().configDir)
    }
}
