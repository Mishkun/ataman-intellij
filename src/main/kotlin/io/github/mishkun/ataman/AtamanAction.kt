package io.github.mishkun.ataman

import com.intellij.icons.AllIcons
import com.intellij.openapi.actionSystem.ActionManager
import com.intellij.openapi.actionSystem.ActionPlaces
import com.intellij.openapi.actionSystem.ActionUiKind
import com.intellij.openapi.actionSystem.ActionUpdateThread
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.actionSystem.DataContext
import com.intellij.openapi.actionSystem.ex.ActionUtil
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
import java.awt.event.KeyEvent
import javax.swing.AbstractAction
import javax.swing.JComponent
import javax.swing.JLabel
import javax.swing.JList
import javax.swing.JPanel
import javax.swing.ListCellRenderer
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
        LeaderPopup(
            event.project, LeaderListStep(
                "Ataman",
                event.dataContext,
                values = service<ConfigService>().parsedBindings
            )
        ).run {
            val project = event.project
            if (project != null) {
                showCenteredInCurrentWindow(project)
            } else {
                showInFocusCenter()
            }
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
