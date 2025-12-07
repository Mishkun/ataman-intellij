package io.github.mishkun.ataman

import com.intellij.openapi.actionSystem.ActionManager
import com.intellij.openapi.actionSystem.ActionPlaces
import com.intellij.openapi.actionSystem.ActionUiKind
import com.intellij.openapi.actionSystem.ActionUpdateThread
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.actionSystem.DataContext
import com.intellij.openapi.actionSystem.ex.ActionUtil
import com.intellij.openapi.components.service
import com.intellij.openapi.project.DumbAwareAction
import com.intellij.openapi.ui.popup.JBPopupListener
import com.intellij.openapi.ui.popup.LightweightWindowEvent
import com.intellij.ui.speedSearch.SpeedSearchSupply
import java.awt.KeyboardFocusManager
import javax.swing.JComponent
import javax.swing.text.JTextComponent

class TransparentLeaderAction : DumbAwareAction() {

    private val delegateAction = LeaderAction()

    override fun getActionUpdateThread(): ActionUpdateThread = ActionUpdateThread.EDT

    override fun update(e: AnActionEvent) {
        super.update(e)
        val focusOwner = getCurrentFocus() ?: kotlin.run {
            e.presentation.isEnabled = false
            return
        }
        val isSupplyActive = isSpeedSearchActive(focusOwner)
        val isTextField = isTextField(focusOwner)
        e.presentation.isEnabled = !(isSupplyActive || isTextField)
    }

    override fun actionPerformed(e: AnActionEvent) {
        delegateAction.actionPerformed(e)
    }

    private fun getCurrentFocus(): JComponent? {
        val focusManager = KeyboardFocusManager.getCurrentKeyboardFocusManager()
        return focusManager.focusOwner as? JComponent
    }

    private fun isTextField(focusOwner: JComponent) = focusOwner is JTextComponent

    private fun isSpeedSearchActive(focusOwner: JComponent): Boolean {
        fun JComponent.getSupply(): SpeedSearchSupply? =
            SpeedSearchSupply.getSupply(this) ?: (parent as? JComponent)?.getSupply()

        val supply = focusOwner.getSupply()
        return supply?.isPopupActive == true
    }
}

class LeaderAction : DumbAwareAction() {

    override fun actionPerformed(event: AnActionEvent) {
        val dispatcher = service<LeaderKeyDispatcher>()
        val bindings = service<ConfigService>().parsedBindings

        // Start key capture FIRST, before any UI work
        dispatcher.start(bindings, event.dataContext)

        // Create display-only popup
        val popup = LeaderPopupUI(
            event.project,
            LeaderListStep(
                "Ataman",
                event.dataContext,
                values = bindings
            )
        )

        // Wire up lifecycle: popup close -> stop dispatcher
        popup.addListener(object : JBPopupListener {
            override fun onClosed(event: LightweightWindowEvent) {
                dispatcher.stop()
            }
        })

        // Show popup
        val project = event.project
        if (project != null) {
            popup.showCenteredInCurrentWindow(project)
        } else {
            popup.showInFocusCenter()
        }
    }
}

fun executeAction(actionId: String, context: DataContext) {
    val action = ActionManager.getInstance().getAction(actionId)
    val event = AnActionEvent.createEvent(
        action, context, null, ActionPlaces.KEYBOARD_SHORTCUT, ActionUiKind.NONE, null,
    )
    ActionUtil.performDumbAwareUpdate(action, event, true)
    ActionUtil.invokeAction(action, event, null)
    service<ConfigService>().latestCommand = actionId
}
