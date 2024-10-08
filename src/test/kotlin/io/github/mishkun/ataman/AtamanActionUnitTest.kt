package io.github.mishkun.ataman

import com.intellij.ide.DataManager
import com.intellij.openapi.actionSystem.AnAction
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.actionSystem.ex.AnActionListener
import com.intellij.testFramework.LightPlatform4TestCase
import com.intellij.util.application
import org.hamcrest.MatcherAssert.assertThat
import org.hamcrest.Matchers
import org.junit.Test

class AtamanActionUnitTest : LightPlatform4TestCase() {

    private val recentActions = mutableListOf<String>()

    private val myActionListener = object : AnActionListener {
        override fun beforeActionPerformed(action: AnAction, event: AnActionEvent) {
            recentActions.add(action.javaClass.simpleName)
        }
    }

    @Test
    fun `executes multiple actions`() {
        application.messageBus.connect().subscribe(AnActionListener.TOPIC, myActionListener)
        val bindings = listOf(
            LeaderBinding.SingleBinding(
                getKeyStroke('w'),
                "w",
                "split and unsplit",
                listOf("SplitVertically", "Unsplit")
            )
        )
        val popup = LeaderPopup(
            project,
            LeaderListStep(
                "Ataman",
                dataContext(),
                bindings,
            )
        )
        popup.selectAndExecuteValue('w')
        popup.handleSelect(true)
        assertThat(
            recentActions,
            Matchers.containsInRelativeOrder(
                "SplitVerticallyAction",
                "Unsplit"
            )
        )
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
        val popup = LeaderPopup(
            project,
            LeaderListStep(
                "Ataman",
                dataContext(),
                bindings,
            )
        )
        popup.selectAndExecuteValue('c')
        popup.handleSelect(true)
        assertThat(recentActions, Matchers.contains("CommentByLineCommentAction"))
    }

    @Suppress("DEPRECATION")
    private fun dataContext() = DataManager.getInstance().dataContext
}
