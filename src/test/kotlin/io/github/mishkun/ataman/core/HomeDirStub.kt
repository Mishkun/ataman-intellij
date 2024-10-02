package io.github.mishkun.ataman.core

import io.github.mishkun.ataman.ATAMAN_RC_FILENAME
import io.github.mishkun.ataman.ConfigService
import io.github.mishkun.ataman.RC_TEMPLATE
import io.mockk.every
import io.mockk.mockkConstructor
import org.junit.rules.TemporaryFolder
import java.io.File

class MockConfigService(private val config: String = RC_TEMPLATE) {

    fun setup() {
        configFolder.create()
        configFolder.setupStubConfigDir(config)
        mockConfigDir()
    }

    fun teardown() {
        configFolder.delete()
    }

    private fun mockConfigDir() {
        mockkConstructor(ConfigService::class)
        every { anyConstructed<ConfigService>().configDir } answers { configFolder.root }
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
