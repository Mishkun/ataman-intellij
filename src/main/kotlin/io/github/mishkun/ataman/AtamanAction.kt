package io.github.mishkun.ataman

import com.intellij.icons.AllIcons
import com.intellij.openapi.actionSystem.ActionManager
import com.intellij.openapi.actionSystem.ActionPlaces
import com.intellij.openapi.actionSystem.ActionUiKind
import com.intellij.openapi.actionSystem.ActionUpdateThread
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.actionSystem.DataContext
import com.intellij.openapi.actionSystem.ex.ActionUtil
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.components.service
import com.intellij.openapi.project.DumbAwareAction
import com.intellij.openapi.project.Project
import com.intellij.openapi.ui.popup.PopupStep
import com.intellij.openapi.ui.popup.util.BaseListPopupStep
import com.intellij.ui.popup.WizardPopup
import com.intellij.ui.popup.list.ListPopupImpl
import com.intellij.ui.speedSearch.SpeedSearchSupply
import com.intellij.util.ui.EmptyIcon
import com.intellij.util.ui.GridBag
import com.intellij.util.ui.JBUI
import com.intellij.util.ui.UIUtil
import java.awt.Font
import java.awt.GridBagLayout
import java.awt.KeyboardFocusManager
import java.awt.event.ActionEvent
import java.util.Timer
import java.util.TimerTask
import javax.swing.AbstractAction
import javax.swing.JComponent
import javax.swing.JLabel
import javax.swing.JList
import javax.swing.JPanel
import javax.swing.ListCellRenderer
import javax.swing.SwingUtilities
import javax.swing.text.JTextComponent

/**
 * A specialized version of LeaderAction that checks if the current focus owner
 * is appropriate for triggering the action. For example, it will not trigger
 * when a text field is focused or when speed search is active.
 *
 * This action is useful for IdeaVim users who want to use a leader key without
 * modifiers, like Space, but don't want it to interfere with normal text entry.
 */
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
        // Simply delegate to the LeaderAction which now handles key event buffering
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
        // Start capturing key events immediately with the aggressive strategy
        val keyEventBuffer = service<KeyEventBuffer>()
        keyEventBuffer.clearCapturedEvents()
        keyEventBuffer.startCapturing()

        // Use a timer to ensure key event blocking has time to fully initialize
        // before showing the popup (gives time for our aggressive event blocking to take effect)
        val timer = Timer()

        try {
            timer.schedule(object : TimerTask() {
                override fun run() {
                    SwingUtilities.invokeLater {
                        try {
                            // Create and show the popup with our key event buffer
                            LeaderPopup(
                                event.project,
                                LeaderListStep(
                                    "Ataman",
                                    event.dataContext,
                                    values = service<ConfigService>().parsedBindings
                                ),
                                keyEventBuffer = keyEventBuffer
                            ).run {
                                val project = event.project
                                if (project != null) {
                                    showCenteredInCurrentWindow(project)
                                } else {
                                    showInFocusCenter()
                                }

                                // Process any captured events after the popup is fully initialized
                                ApplicationManager.getApplication().invokeLater {
                                    processBufferedEvents()
                                }
                            }
                        } catch (e: Exception) {
                            keyEventBuffer.stopCapturing()
                        }
                    }
                }
            }, 50) // Short delay to ensure event blocking is fully initialized

        } catch (e: Exception) {
            // Make sure to stop capturing events if something goes wrong
            keyEventBuffer.stopCapturing()
            timer.cancel()
            throw e
        }
    }
}

class LeaderListStep(title: String? = null, val dataContext: DataContext, values: List<LeaderBinding>) :
    BaseListPopupStep<LeaderBinding>(title, values) {
    override fun hasSubstep(selectedValue: LeaderBinding?): Boolean {
        return selectedValue is LeaderBinding.GroupBinding
    }


    override fun onChosen(selectedValue: LeaderBinding?, finalChoice: Boolean): PopupStep<*>? =
        when (selectedValue) {
            is LeaderBinding.SingleBinding -> {
                doFinalStep {
                    selectedValue.action.forEach { action ->
                        executeAction(action, dataContext)
                    }
                }
            }

            is LeaderBinding.GroupBinding -> LeaderListStep(null, dataContext, selectedValue.bindings)
            null -> null
        }

    override fun getBackgroundFor(value: LeaderBinding?) = UIUtil.getPanelBackground()

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

class LeaderPopup(
    project: Project? = null,
    step: LeaderListStep,
    parent: WizardPopup? = null,
    parentObject: Any? = null,
    private val keyEventBuffer: KeyEventBuffer? = null
) : ListPopupImpl(project, parent, step, parentObject) {

    private val keyActionManager = service<KeyActionManager>()

    init {
        // Use pre-registered actions from KeyActionManager with popup-specific behavior
        step.values.forEach { binding ->
            // Get the pre-created action template from KeyActionManager
            val actionTemplate = keyActionManager.getActionForBinding(binding.key)

            // If template exists, register it with popup-specific behavior
            if (actionTemplate != null) {
                // Create a popup-specific action that uses the current context
                val popupAction = object : AbstractAction() {
                    override fun actionPerformed(e: ActionEvent?) {
                        list.setSelectedValue(binding, true)
                        handleSelect(true)
                    }
                }

                // Register the action with this popup
                registerAction("handle${binding.key}", binding.key, popupAction)
            } else {
                // Fallback to creating a new action if not found in KeyActionManager
                registerAction("handle${binding.key}", binding.key, object : AbstractAction() {
                    override fun actionPerformed(e: ActionEvent?) {
                        list.setSelectedValue(binding, true)
                        handleSelect(true)
                    }
                })
            }
        }
    }

    /**
     * Process any key events that were captured during popup initialization
     */
    fun processBufferedEvents() {
        if (keyEventBuffer == null) return

        // Get captured events but DON'T stop capturing yet
        // We'll keep blocking all keyboard input until the popup is interacted with
        val capturedEvents = keyEventBuffer.drainCapturedEvents()

        if (capturedEvents.isNotEmpty()) {
            // Process each captured key event
            for (keyStroke in capturedEvents) {
                // Find a binding that matches this key stroke
                val matchingBinding = (step as LeaderListStep).values.find {
                    it.key.keyCode == keyStroke.keyCode &&
                            it.key.modifiers == keyStroke.modifiers
                }

                if (matchingBinding != null) {
                    list.setSelectedValue(matchingBinding, true)
                    handleSelect(true)
                    break // Process only the first valid key
                }
            }
        }

        // Note: We do not stop capturing events here
        // Key event capturing will continue until the popup is disposed
        // This ensures no stray key events can trigger other actions
    }

    override fun getListElementRenderer(): ListCellRenderer<*> = ActionItemRenderer()

    override fun createPopup(parent: WizardPopup?, step: PopupStep<*>?, parentValue: Any?) = LeaderPopup(
        parent?.project,
        step as LeaderListStep,
        parent,
        parentValue,
        keyEventBuffer
    )

    override fun dispose() {
        // Make sure to stop capturing events when the popup is disposed
        keyEventBuffer?.stopCapturing()
        super.dispose()
    }
}


class ActionItemRenderer : JPanel(GridBagLayout()), ListCellRenderer<LeaderBinding> {
    private val leftInset = 12
    private val innerInset = 8
    private val emptyMenuRightArrowIcon = EmptyIcon.create(AllIcons.General.ArrowRight)

    private val listSelectionBackground = UIUtil.getListSelectionBackground(true)

    private val actionTextLabel = JLabel()
    private val bindingKeyLabel = JLabel()

    init {
        val topBottom = 3
        val insets = JBUI.insets(topBottom, leftInset, topBottom, 0)

        var gbc = GridBag().setDefaultAnchor(GridBag.WEST)
        gbc = gbc.nextLine().next().insets(insets)
        add(bindingKeyLabel, gbc)
        gbc = gbc.next().insetLeft(innerInset)
        add(actionTextLabel, gbc)
        gbc = gbc.next().fillCellHorizontally().weightx(1.0).anchor(GridBag.EAST)
            .insets(JBUI.insets(topBottom, leftInset, topBottom, innerInset))
        add(JLabel(emptyMenuRightArrowIcon), gbc)
    }

    override fun getListCellRendererComponent(
        list: JList<out LeaderBinding>?,
        value: LeaderBinding,
        index: Int,
        selected: Boolean,
        focused: Boolean
    ): JComponent {
        bindingKeyLabel.text = value.char
        bindingKeyLabel.font = UIUtil.getFontWithFallback("monospaced", Font.PLAIN, 12)
        bindingKeyLabel.foreground = UIUtil.getListForeground(selected, false)
        actionTextLabel.text = value.description
        actionTextLabel.foreground = UIUtil.getListForeground(selected, value is LeaderBinding.GroupBinding)
        UIUtil.setBackgroundRecursively(
            this,
            if (selected) listSelectionBackground else UIUtil.getPanelBackground()
        )
        return this
    }
}
