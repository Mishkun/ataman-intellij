package io.github.mishkun.ataman

import com.intellij.notification.NotificationGroupManager
import com.intellij.notification.NotificationType
import com.intellij.openapi.components.service
import com.intellij.openapi.project.Project
import com.typesafe.config.ConfigFactory
import java.awt.event.KeyEvent
import java.io.File
import java.io.IOException
import javax.swing.KeyStroke

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

fun updateConfig(project: Project, configDir: File) {
    parseConfig(configDir).fold(onSuccess = { values ->
        service<ConfigService>().parsedBindings = values
    },
        onFailure = { error ->
            when (error) {
                is IllegalStateException -> project.showNotification(
                    "Bindings schema is invalid. Aborting...\n${error.message}",
                    NotificationType.ERROR
                )

                is IOException -> project.showNotification(
                    "Config file is not found and I could not create it. Aborting...\n${error.message}",
                    NotificationType.ERROR
                )

                else -> project.showNotification(
                    "Config is malformed. Aborting...\n${error.message}",
                    NotificationType.ERROR
                )
            }
        })
}

private fun Project.showNotification(
    message: String,
    notificationType: NotificationType = NotificationType.INFORMATION
) {
    NotificationGroupManager.getInstance()
        .getNotificationGroup("io.github.mishkun.ataman")
        .createNotification(
            "Ataman",
            message,
            notificationType
        )
        .notify(this)
}

fun parseConfig(configDir: File): Result<List<LeaderBinding>> {
    return runCatching {
        val rcFile = findOrCreateRcFile(configDir)
        val (bindings, throwables) = buildBindingsTree(execFile(rcFile))
        if (throwables.isNotEmpty()) {
            throw IllegalStateException(throwables.joinToString("\n"))
        } else {
            bindings
        }
    }
}

fun getKeyStroke(key: String): KeyStroke = when (key.length) {
    1 -> getKeyStroke(key[0])
    else -> getFKeyStroke(key)
}

fun getFKeyStroke(key: String): KeyStroke = KeyStroke.getKeyStroke(
    key.substringAfter("F").toInt() + 111,
    0,
    true
)

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
private fun buildBindingsTree(bindingConfig: List<Pair<String, Any>>): Pair<List<LeaderBinding>, List<String>> {
    val errors = mutableListOf<String>()
    val bindings = bindingConfig.mapNotNull { (keyword, bodyObject) ->
        val body = bodyObject as Map<String, Any>
        val description = bodyObject[DESCRIPTION_KEYWORD] as String
        when {
            body.containsKey(ACTION_ID_KEYWORD) -> {
                val actionId = body[ACTION_ID_KEYWORD] as String
                LeaderBinding.SingleBinding(getKeyStroke(keyword), keyword, description, actionId)
            }

            body.containsKey(BINDINGS_KEYWORD) -> {
                val childBindingsObject = body[BINDINGS_KEYWORD] as Map<String, Any>
                val childBindings = buildBindingsTree(childBindingsObject.toList()).first
                LeaderBinding.GroupBinding(getKeyStroke(keyword), keyword, description, childBindings)
            }

            else -> {
                errors.add("Expected either $ACTION_ID_KEYWORD or $BINDINGS_KEYWORD for $keyword but got $bodyObject")
                null
            }
        }
    }.sortedByDescending { it.char }.sortedBy { it.char.lowercase() }
    return bindings to errors
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
