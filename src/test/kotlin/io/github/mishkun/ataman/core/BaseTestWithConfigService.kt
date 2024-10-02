package io.github.mishkun.ataman.core

import com.intellij.testFramework.LightPlatform4TestCase

abstract class BaseTestWithConfigService : LightPlatform4TestCase() {
    protected abstract val mockConfigService: MockConfigService

    override fun setUp() {
        mockConfigService.setup()
        super.setUp()
    }

    override fun tearDown() {
        super.tearDown()
        mockConfigService.teardown()
        setProject(null)
    }
}
