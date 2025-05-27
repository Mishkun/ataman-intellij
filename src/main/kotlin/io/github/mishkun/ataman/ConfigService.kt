package io.github.mishkun.ataman

import com.intellij.openapi.components.Service
import com.intellij.openapi.components.service
import java.io.File

@Service
class ConfigService {
    var parsedBindings: List<LeaderBinding> = emptyList()
        set(value) {
            field = value
            // When bindings are updated, prepare actions ahead of time
            service<KeyActionManager>().prepareActionsForBindings(value)
        }
    var latestCommand: String? = null
}

class Config {
    val configDir: File = File(System.getProperty("ataman.configFolder") ?: System.getProperty("user.home"))
}
