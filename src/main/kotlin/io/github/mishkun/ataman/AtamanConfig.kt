package io.github.mishkun.ataman

import com.intellij.notification.NotificationGroupManager
import com.intellij.notification.NotificationType
import com.intellij.openapi.project.Project
import com.typesafe.config.ConfigException
import com.typesafe.config.ConfigFactory
import java.awt.event.KeyEvent
import java.io.File
import javax.swing.KeyStroke

var parsedBindings: List<LeaderBinding> = emptyList()

const val ATAMAN_RC_FILENAME = ".atamanrc.config"

val RC_TEMPLATE = """
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

fun updateConfig(project: Project? = null, homeDir: File = getHomeDir()) {
    val rcFile = findOrCreateRcFile(homeDir)
    val values = try {
        buildBindingsTree(execFile(rcFile))
    } catch (exception: ConfigException) {
        project?.let {
            NotificationGroupManager.getInstance()
                .getNotificationGroup("io.github.mishkun.ataman")
                .createNotification(
                    "Ataman",
                    "Config is malformed. Aborting...\n${exception.message}",
                    NotificationType.ERROR
                )
                .notify(project)
        }
        return
    }
    parsedBindings = values
}

fun getKeyStroke(char: Char): KeyStroke = KeyStroke.getKeyStroke(
    KeyEvent.getExtendedKeyCodeForChar(char.code),
        if (char.isUpperCase()) KeyEvent.SHIFT_DOWN_MASK else 0,
    true
)


private const val BINDINGS_KEYWORD = "bindings"
private const val DESCRIPTION_KEYWORD = "description"
private const val ACTION_ID_KEYWORD = "actionId"

@Suppress("UNCHECKED_CAST")
private fun execFile(file: File): List<Pair<String, Any>> =
    (ConfigFactory.parseFile(file).root().unwrapped()[BINDINGS_KEYWORD] as Map<String, Any>).toList()

@Suppress("UNCHECKED_CAST")
private fun buildBindingsTree(bindingConfig: List<Pair<String, Any>>): List<LeaderBinding> {
    return bindingConfig.mapNotNull { (keyword, bodyObject) ->
        val key = keyword.first()
        val body = bodyObject as Map<String, Any>
        val description = bodyObject[DESCRIPTION_KEYWORD] as String
        when {
            body.containsKey(ACTION_ID_KEYWORD) -> {
                val actionId = body[ACTION_ID_KEYWORD] as String
                LeaderBinding.SingleBinding(getKeyStroke(key), key, description, actionId)
            }

            body.containsKey(BINDINGS_KEYWORD) -> {
                val childBindingsObject = body[BINDINGS_KEYWORD] as Map<String, Any>
                val childBindings = buildBindingsTree(childBindingsObject.toList())
                LeaderBinding.GroupBinding(getKeyStroke(key), key, description, childBindings)
            }

            else -> null
        }
    }.sortedByDescending { it.char }.sortedBy { it.char.lowercaseChar() }
}

fun getHomeDir(): File {
    val homeDirName = System.getProperty("user.home")
    return File(homeDirName)
}

fun findOrCreateRcFile(homeDir: File): File {
    val file = File(homeDir, ATAMAN_RC_FILENAME)
    if (file.exists()) {
        return file
    } else {
        file.createNewFile()
        file.writeText(RC_TEMPLATE)
        return file
    }
}
