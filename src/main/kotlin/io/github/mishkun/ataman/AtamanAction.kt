package io.github.mishkun.ataman

import com.intellij.icons.AllIcons
import com.intellij.notification.Notification
import com.intellij.notification.NotificationListener
import com.intellij.notification.NotificationType
import com.intellij.notification.Notifications
import com.intellij.openapi.actionSystem.*
import com.intellij.openapi.actionSystem.ex.ActionManagerEx
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.application.ModalityState
import com.intellij.openapi.project.DumbAwareAction
import com.intellij.openapi.project.Project
import com.intellij.openapi.ui.popup.JBPopupFactory
import com.intellij.openapi.ui.popup.PopupStep
import com.intellij.openapi.ui.popup.util.BaseListPopupStep
import com.intellij.openapi.wm.WindowManager
import com.intellij.ui.awt.RelativePoint
import com.intellij.ui.popup.WizardPopup
import com.intellij.ui.popup.list.ListPopupImpl
import com.intellij.ui.speedSearch.SpeedSearchSupply
import com.intellij.util.ui.EmptyIcon
import com.intellij.util.ui.GridBag
import com.intellij.util.ui.JBUI
import com.intellij.util.ui.UIUtil
import java.awt.GridBagLayout
import java.awt.KeyboardFocusManager
import java.awt.event.ActionEvent
import java.awt.event.KeyEvent
import javax.swing.*
import javax.swing.text.JTextComponent


fun invokeLaterOnEDT(block: () -> Unit) =
    ApplicationManager.getApplication().invokeAndWait(block, ModalityState.NON_MODAL)

fun show(
    message: String,
    title: String = "",
    notificationType: NotificationType = NotificationType.INFORMATION,
    groupDisplayId: String = "",
    notificationListener: NotificationListener? = null
) {
    invokeLaterOnEDT {
        val notification = Notification(
            groupDisplayId,
            title,
            // this is because Notification doesn't accept empty messages
            message.takeUnless { it.isBlank() } ?: "[ empty ]",
            notificationType,
            notificationListener)
        ApplicationManager.getApplication().messageBus.syncPublisher(Notifications.TOPIC).notify(notification)
    }
}

class TransparentLeaderAction : DumbAwareAction() {

    private val delegateAction = LeaderAction()

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
        val frame = WindowManager.getInstance().getFrame(event.project)!!
        val point = RelativePoint.getCenterOf(frame.rootPane)
        LeaderPopup(
            event.project, LeaderListStep(
                "Ataman",
                event.dataContext,
                values = AtamanConfig.parsedBindings
            )
        ).show(point)
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
                    val action = ActionManager.getInstance().getAction(selectedValue.action)
                    executeAction(action, dataContext)
                }
            }
            is LeaderBinding.GroupBinding -> LeaderListStep(null, dataContext, selectedValue.bindings)
            null -> null
        }

    override fun getBackgroundFor(value: LeaderBinding?) = UIUtil.getPanelBackground()

    private fun executeAction(action: AnAction, context: DataContext): Boolean {
        val event = AnActionEvent(
            null, context, ActionPlaces.KEYBOARD_SHORTCUT, action.templatePresentation,
            ActionManager.getInstance(), 0
        )
        if (action is ActionGroup && !action.canBePerformed(context)) {
            // Some ActionGroups should not be performed, but shown as a popup
            val popup = JBPopupFactory.getInstance()
                .createActionGroupPopup(event.presentation.text, action, context, false, null, -1)
            val component = context.getData(PlatformDataKeys.CONTEXT_COMPONENT)
            if (component != null) {
                val window = SwingUtilities.getWindowAncestor(component)
                if (window != null) {
                    popup.showInCenterOf(window)
                }
                return true
            }
            popup.showInFocusCenter()
            return true
        } else {
            // beforeActionPerformedUpdate should be called to update the action. It fixes some rider-specific problems.
            //   because rider use async update method. See VIM-1819.
            action.beforeActionPerformedUpdate(event)
            if (event.presentation.isEnabled) {
                // Executing listeners for action. I can't be sure that this code is absolutely correct,
                //   action execution process in IJ seems to be more complicated.
                val actionManager = ActionManagerEx.getInstanceEx()
                // [VERSION UPDATE] 212+
                actionManager.fireBeforeActionPerformed(action, event.dataContext, event)
                action.actionPerformed(event)

                // [VERSION UPDATE] 212+
                actionManager.fireAfterActionPerformed(action, event.dataContext, event)
                return true
            }
        }
        return false
    }
}

class LeaderPopup(
    project: Project? = null,
    step: LeaderListStep,
    parent: WizardPopup? = null,
    parentObject: Any? = null
) : ListPopupImpl(project, parent, step, parentObject) {

    init {
        step.values.forEach { binding ->
            registerAction("handle${binding.key}", binding.key, object : AbstractAction() {
                override fun actionPerformed(e: ActionEvent?) {
                    list.setSelectedValue(binding, true)
                    handleSelect(true)
                }
            })
        }
    }

    override fun process(aEvent: KeyEvent) {
        aEvent.consume()
    }

    override fun getListElementRenderer(): ListCellRenderer<*> = ActionItemRenderer()

    override fun createPopup(parent: WizardPopup?, step: PopupStep<*>?, parentValue: Any?) = LeaderPopup(
        parent?.project,
        step as LeaderListStep,
        parent,
        parentValue
    )
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
        bindingKeyLabel.text = value.char.toString()
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

