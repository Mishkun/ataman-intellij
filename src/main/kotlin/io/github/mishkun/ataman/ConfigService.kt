package io.github.mishkun.ataman

import com.intellij.openapi.components.Service
import java.io.File
import javax.swing.KeyStroke

@Service
class ConfigService {
    var parsedBindings: List<LeaderBinding> = emptyList()
        set(value) {
            field = value
            // Pre-compute keystroke lookup map when bindings are set
            _bindingsMap = buildBindingsMap(value)
        }

    var latestCommand: String? = null

    private var _bindingsMap: Map<KeyStroke, LeaderBinding> = emptyMap()

    /**
     * Pre-computed map from KeyStroke to LeaderBinding for fast lookup.
     * This is built when parsedBindings is set to avoid doing map construction
     * during the time-critical popup initialization phase.
     */
    val bindingsMap: Map<KeyStroke, LeaderBinding>
        get() = _bindingsMap

    /**
     * Build a lookup map from KeyStroke to LeaderBinding for a list of bindings.
     */
    fun buildBindingsMap(bindings: List<LeaderBinding>): Map<KeyStroke, LeaderBinding> {
        return bindings.associateBy { it.key }
    }
}

class Config {
    val configDir: File = File(System.getProperty("ataman.configFolder") ?: System.getProperty("user.home"))
}
