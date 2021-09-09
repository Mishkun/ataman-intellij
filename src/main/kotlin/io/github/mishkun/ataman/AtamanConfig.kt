package io.github.mishkun.ataman

import com.intellij.ide.actions.OpenFileAction
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.project.DumbAwareAction
import com.intellij.openapi.project.Project
import com.intellij.openapi.wm.WindowManager
import com.typesafe.config.ConfigFactory
import java.awt.event.KeyEvent
import java.io.File
import java.io.IOException
import javax.swing.KeyStroke

object AtamanConfig {
    private const val ATAMAN_RC_FILENAME = ".atamanrc.config"

    fun getKeyStroke(project: Project?, char: Char) = KeyStroke.getKeyStrokeForEvent(
        // Ugly hack to get KEY_REALEASED keystroke
        KeyEvent(
            WindowManager.getInstance().getFrame(project),
            KeyEvent.KEY_RELEASED,
            0,
            if (char.isUpperCase()) KeyEvent.SHIFT_DOWN_MASK else 0,
            KeyEvent.getExtendedKeyCodeForChar(char.toInt()),
            char,
        )
    )

    private val RC_TEMPLATE = """
        # This file is written in HOCON (Human-Optimized Config Object Notation) format. 
        # For more information about HOCON see https://github.com/lightbend/config/blob/master/HOCON.md
        
        bindings {
            q { 
                description: Session...
                bindings {
                     f { actionId: OpenAtamanConfigAction, description: Open ~/.atamanrc.config }
                }
            },
        }
    """.trimIndent()

    private const val BINDINGS_KEYWORD = "bindings"
    private const val DESCRIPTION_KEYWORD = "description"
    private const val ACTION_ID_KEYWORD = "actionId"

    fun execFile(file: File): List<Pair<out String, Any>> =
        (ConfigFactory.parseFile(file).root().unwrapped()[BINDINGS_KEYWORD] as Map<String, Any>).toList()

    fun buildBindingsTree(project: Project?, bindingConfig: List<Pair<String, Any>>): List<LeaderBinding> {
        return bindingConfig.mapNotNull { (keyword, bodyObject) ->
            val key = keyword.first()
            val body = bodyObject as Map<String, Any>
            val description = bodyObject[DESCRIPTION_KEYWORD] as String
            when {
                body.containsKey(ACTION_ID_KEYWORD) -> {
                    val actionId = body[ACTION_ID_KEYWORD] as String
                    LeaderBinding.SingleBinding(getKeyStroke(project, key), key, description, actionId)
                }
                body.containsKey(BINDINGS_KEYWORD) -> {
                    val childBindingsObject = body[BINDINGS_KEYWORD] as Map<String, Any>
                    val childBindings = buildBindingsTree(project, childBindingsObject.toList())
                    LeaderBinding.GroupBinding(getKeyStroke(project, key), key, description, childBindings)
                }
                else -> null
            }
        }.sortedByDescending { it.char }.sortedBy { it.char.toLowerCase() }
    }

    fun findOrCreateRcFile(): File? {
        val homeDirName = System.getProperty("user.home")
        // Check whether file exists in home dir
        if (homeDirName != null) {
            val file = File(homeDirName, ATAMAN_RC_FILENAME)
            if (file.exists()) {
                return file
            } else {
                try {
                    file.createNewFile()
                    file.writeText(RC_TEMPLATE)
                    return file
                } catch (ignored: IOException) {
                    // Try to create one of two files
                }
            }
        }
        return null
    }
}


class OpenAtamanConfigAction : DumbAwareAction() {

    override fun actionPerformed(e: AnActionEvent) {
        val eventProject = e.project
        if (eventProject != null) {
            val atamanRc = AtamanConfig.findOrCreateRcFile()
            if (atamanRc != null) {
                OpenFileAction.openFile(atamanRc.path, eventProject)
            }
        }
    }
}
