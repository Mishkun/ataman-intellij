package io.github.mishkun.ataman

import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.project.DumbAwareAction

class ReloadAtamanConfigAction : DumbAwareAction() {
    override fun actionPerformed(e: AnActionEvent) {
        updateConfig(e.project)
    }
}
