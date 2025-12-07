package io.github.mishkun.ataman

import com.intellij.ide.DataManager
import com.intellij.openapi.actionSystem.AnAction
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.actionSystem.ex.AnActionListener
import com.intellij.testFramework.PlatformTestUtil
import com.intellij.util.application
import io.github.mishkun.ataman.core.BaseTestWithConfig
import org.hamcrest.MatcherAssert.assertThat
import org.hamcrest.Matchers
import org.junit.Test

class RepeatLatestAtamanCommandActionTest : BaseTestWithConfig() {

    private val recentActions = mutableListOf<String>()

    private val myActionListener = object : AnActionListener {
        override fun beforeActionPerformed(action: AnAction, event: AnActionEvent) {
            recentActions.add(action.javaClass.simpleName)
        }
    }

    @Test
    fun `executes action`() {
        application.messageBus.connect().subscribe(AnActionListener.TOPIC, myActionListener)
        val bindings = listOf(
            LeaderBinding.SingleBinding(
                getKeyStroke('c'),
                "c",
                "CommentAction",
                "CommentByLineComment"
            )
        )
        val popup = LeaderPopupUI(
            project,
            LeaderListStep(
                "Ataman",
                DataManager.getInstance().dataContext,
                bindings,
            )
        )
        popup.selectAndExecuteValue('c')
        popup.handleSelect(true)
        PlatformTestUtil.invokeNamedAction("RepeatLatestAtamanCommandAction")
        PlatformTestUtil.dispatchAllEventsInIdeEventQueue()
        assertThat(
            recentActions,
            Matchers.containsInRelativeOrder(
                "CommentByLineCommentAction",
                "RepeatLatestAtamanCommandAction",
                "CommentByLineCommentAction"
            )
        )
    }
}
