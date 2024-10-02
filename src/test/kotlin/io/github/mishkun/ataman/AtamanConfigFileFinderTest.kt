package io.github.mishkun.ataman

import io.github.mishkun.ataman.core.setupEmptyConfigDir
import io.github.mishkun.ataman.core.setupStubConfigDir
import org.hamcrest.MatcherAssert.assertThat
import org.hamcrest.Matchers.allOf
import org.hamcrest.Matchers.`is`
import org.hamcrest.Matchers.notNullValue
import org.hamcrest.io.FileMatchers.aFileNamed
import org.hamcrest.io.FileMatchers.anExistingFile
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

class AtamanConfigFileFinderTest {

    @get:Rule
    val tmpFolder = TemporaryFolder()

    @Test
    fun `creates rc file if it does not exist`() {
        val foundRcFile = findOrCreateRcFile(tmpFolder.setupEmptyConfigDir())
        assertThat(foundRcFile, notNullValue())
        assertThat(
            foundRcFile, allOf(
                anExistingFile(),
                aFileNamed(`is`(ATAMAN_RC_FILENAME)),
            )
        )
        assertThat(foundRcFile.readText(), `is`(RC_TEMPLATE))
    }

    @Test
    fun `finds rc file if it exists`() {
        val foundRcFile = findOrCreateRcFile(tmpFolder.setupStubConfigDir())
        assertThat(foundRcFile, notNullValue())
    }
}
