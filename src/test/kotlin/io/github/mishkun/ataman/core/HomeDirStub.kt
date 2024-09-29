package io.github.mishkun.ataman.core

import io.github.mishkun.ataman.ATAMAN_RC_FILENAME
import io.github.mishkun.ataman.RC_TEMPLATE
import org.junit.rules.TemporaryFolder
import java.io.File

fun TemporaryFolder.setupEmptyHomeDir(): File = this.root

fun TemporaryFolder.setupStubHomeDir(text: String = RC_TEMPLATE): File = this.root.also {
    File(it, ATAMAN_RC_FILENAME).writeText(text)
}
