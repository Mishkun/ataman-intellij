package io.github.mishkun.ataman

import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.components.service
import com.intellij.openapi.project.DumbAwareAction

class RepeatLatestAtamanCommandAction : DumbAwareAction() {
    override fun actionPerformed(e: AnActionEvent) {
        val configService = service<ConfigService>()
        configService.latestCommand?.let { command ->
            executeAction(command, e.dataContext)
        }
    }
}
