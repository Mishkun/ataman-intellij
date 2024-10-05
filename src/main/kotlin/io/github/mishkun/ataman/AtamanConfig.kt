package io.github.mishkun.ataman

import com.intellij.notification.NotificationGroupManager
import com.intellij.notification.NotificationType
import com.intellij.openapi.components.service
import com.intellij.openapi.project.Project
import com.intellij.openapi.ui.playback.commands.KeyStrokeMap
import com.typesafe.config.ConfigFactory
import java.awt.event.KeyEvent
import java.io.File
import java.io.IOException
import javax.swing.KeyStroke

const val ATAMAN_RC_FILENAME = ".atamanrc.config"
private const val COMMON_BINDINGS_KEY = "bindings"

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

fun updateConfig(project: Project, configDir: File, ideProductKey: String) {
    parseConfig(configDir, ideProductKey).fold(
        onSuccess = { values ->
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

fun mergeBindings(bindingConfig: List<LeaderBinding>, overrideConfig: List<LeaderBinding>): List<LeaderBinding> {
    val commonBindingsMap = bindingConfig.associateBy { it.char }
    val productBindingsMap = overrideConfig.associateBy { it.char }
    val commonKeys = commonBindingsMap.keys.toSet().intersect(productBindingsMap.keys)
    val mergedBindings = commonKeys.associateWith { key ->
        val commonBinding = commonBindingsMap.getValue(key)
        val productBinding = productBindingsMap.getValue(key)
        when {
            commonBinding is LeaderBinding.GroupBinding && productBinding is LeaderBinding.GroupBinding -> {
                productBinding.copy(
                    bindings = mergeBindings(commonBinding.bindings, productBinding.bindings)
                )
            }

            else -> productBinding
        }
    }
    return (commonBindingsMap + productBindingsMap + mergedBindings).values.toList()
}

fun parseConfig(configDir: File, ideProductKey: String): Result<List<LeaderBinding>> {
    return runCatching {
        val rcFile = findOrCreateRcFile(configDir)
        val config = execFile(rcFile)
        val (commonBindingsParsed, commonThrowables) = buildBindingsTree(config, COMMON_BINDINGS_KEY)
        val (productBindingsParsed, productThrowables) = buildBindingsTree(config, ideProductKey)
        val bindings = mergeBindings(commonBindingsParsed, productBindingsParsed)
        val throwables = commonThrowables + productThrowables
        if (throwables.isNotEmpty()) {
            throw IllegalStateException(throwables.joinToString("\n"))
        } else {
            bindings
        }
    }
}
private val keyStrokeMap = KeyStrokeMap()

fun getKeyStroke(key: String): KeyStroke {
    val trimmedKey = key.trim('"')
    return when (trimmedKey.length) {
        1 -> keyStrokeMap.get(trimmedKey[0])
        else -> getFKeyStroke(key)
    }
}

fun getFKeyStroke(key: String): KeyStroke = KeyStroke.getKeyStroke(
    key.substringAfter("F").toInt() + 111,
    0
)

fun getKeyStroke(char: Char): KeyStroke = KeyStroke.getKeyStroke(
    KeyEvent.getExtendedKeyCodeForChar(char.code),
    if (char.isUpperCase()) KeyEvent.SHIFT_DOWN_MASK else 0,
    true
)

private const val BINDINGS_KEYWORD = COMMON_BINDINGS_KEY
private const val DESCRIPTION_KEYWORD = "description"
private const val ACTION_ID_KEYWORD = "actionId"

@Suppress("UNCHECKED_CAST")
private fun execFile(file: File): Map<String, List<Pair<String, Any>>> =
    (ConfigFactory.parseFile(file).root().unwrapped() as Map<String, Map<String, Any>>).mapValues { it.value.toList() }

private fun buildBindingsTree(
    rootBindingConfig: Map<String, List<Pair<String, Any>>>,
    key: String
): Pair<List<LeaderBinding>, List<String>> {
    val commonBindings = rootBindingConfig[key] ?: emptyList()
    return buildBindingsTree(commonBindings)
}

@Suppress("UNCHECKED_CAST")
private fun buildBindingsTree(bindingConfig: List<Pair<String, Any>>): Pair<List<LeaderBinding>, List<String>> {
    val errors = mutableListOf<String>()
    val bindings = bindingConfig.mapNotNull { (keyword, bodyObject) ->
        val body = bodyObject as Map<String, Any>
        val description = bodyObject[DESCRIPTION_KEYWORD] as String
        when {
            body.containsKey(ACTION_ID_KEYWORD) -> {
                when (val actionId = body[ACTION_ID_KEYWORD]) {
                    is String -> LeaderBinding.SingleBinding(getKeyStroke(keyword), keyword, description, actionId)
                    is List<*> -> LeaderBinding.SingleBinding(
                        getKeyStroke(keyword),
                        keyword,
                        description,
                        actionId as List<String>
                    )

                    else -> {
                        errors.add("Expected either String or List<String> for $ACTION_ID_KEYWORD but got $actionId")
                        null
                    }
                }
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
