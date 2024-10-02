package io.github.mishkun.ataman

import com.intellij.openapi.components.Service
import java.io.File

@Service
class ConfigService {
    val configDir: File = File(System.getProperty("user.home"))
    var parsedBindings: List<LeaderBinding> = emptyList()
}
