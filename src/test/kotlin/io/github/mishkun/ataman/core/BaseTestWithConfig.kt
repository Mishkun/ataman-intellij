package io.github.mishkun.ataman.core

import com.intellij.testFramework.LightPlatform4TestCase

abstract class BaseTestWithConfig : LightPlatform4TestCase() {
    protected open val mockConfig: MockConfig = MockConfig()

    override fun setUp() {
        mockConfig.setup()
        System.setProperty("ataman.configFolder", mockConfig.configFolder.root.absolutePath)
        super.setUp()
    }

    override fun tearDown() {
        super.tearDown()
        mockConfig.teardown()
        setProject(null)
    }
}
