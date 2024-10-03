package io.github.mishkun.ataman

import com.intellij.openapi.components.service
import com.intellij.openapi.editor.EditorFactory
import com.intellij.openapi.vfs.newvfs.impl.VfsRootAccess
import com.intellij.testFramework.PlatformTestUtil
import io.github.mishkun.ataman.core.BaseTestWithConfig
import org.hamcrest.MatcherAssert.assertThat
import org.hamcrest.Matchers
import org.junit.Test

class OpenAtamanConfigActionTest : BaseTestWithConfig() {
    @Test
    fun `test action opens config file in editor`() {
        VfsRootAccess.allowRootAccess(testRootDisposable, mockConfig.configFile.canonicalPath)
        PlatformTestUtil.invokeNamedAction("OpenAtamanConfigAction")
        val editorFactory = service<EditorFactory>()
        val editor = editorFactory.allEditors.first()
        assertThat(editor, Matchers.notNullValue())
        assertThat(editor.virtualFile.canonicalPath, Matchers.equalTo(mockConfig.configFile.canonicalPath))
        editorFactory.releaseEditor(editor!!)
    }
}
