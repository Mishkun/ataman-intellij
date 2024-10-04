package io.github.mishkun.ataman.core

import io.github.mishkun.ataman.ATAMAN_RC_FILENAME
import io.github.mishkun.ataman.RC_TEMPLATE
import org.junit.rules.TemporaryFolder
import java.io.File

class MockConfig(private val config: String = RC_TEMPLATE) {

    val configFolder: TemporaryFolder
        get() = Companion.configFolder

    val configFile: File
        get() = configFolder.root.resolve(ATAMAN_RC_FILENAME)

    fun setup() {
        configFolder.create()
        configFolder.setupStubConfigDir(config)
    }

    fun stubConfig(text: String) {
        configFolder.setupStubConfigDir(text)
    }

    fun teardown() {
        configFolder.delete()
    }

    companion object {
        @JvmStatic
        private val configFolder = TemporaryFolder()
    }
}

fun TemporaryFolder.setupEmptyConfigDir(): File = this.root

fun TemporaryFolder.setupStubConfigDir(text: String = RC_TEMPLATE): File = this.root.also {
    File(it, ATAMAN_RC_FILENAME).writeText(text)
}
