package io.github.mishkun.ataman

import com.intellij.ide.actions.OpenFileAction
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.project.DumbAwareAction

class OpenAtamanConfigAction : DumbAwareAction() {

    override fun actionPerformed(e: AnActionEvent) {
        val eventProject = e.project
        if (eventProject != null) {
            val atamanRc = findOrCreateRcFile(getHomeDir())
            if (atamanRc != null) {
                OpenFileAction.openFile(atamanRc.path, eventProject)
            }
        }
    }
}
