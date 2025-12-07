package io.github.mishkun.ataman

import com.intellij.icons.AllIcons
import com.intellij.openapi.actionSystem.DataContext
import com.intellij.openapi.components.service
import com.intellij.openapi.project.Project
import com.intellij.openapi.ui.popup.PopupStep
import com.intellij.openapi.ui.popup.util.BaseListPopupStep
import com.intellij.ui.popup.WizardPopup
import com.intellij.ui.popup.list.ListPopupImpl
import com.intellij.util.ui.EmptyIcon
import com.intellij.util.ui.GridBag
import com.intellij.util.ui.JBUI
import com.intellij.util.ui.UIUtil
import java.awt.Font
import java.awt.GridBagLayout
import java.awt.event.KeyEvent
import javax.swing.JComponent
import javax.swing.JLabel
import javax.swing.JList
import javax.swing.JPanel
import javax.swing.ListCellRenderer

/**
 * Display-only popup for showing leader bindings.
 * All key handling is done by LeaderKeyDispatcher - this popup only renders the UI.
 */
class LeaderPopupUI(
    project: Project? = null,
    step: LeaderListStep,
    parent: WizardPopup? = null,
    parentObject: Any? = null
) : ListPopupImpl(project, parent, step, parentObject), LeaderStateListener {

    private val dispatcher = service<LeaderKeyDispatcher>()

    init {
        dispatcher.addListener(this)
    }

    override fun onBindingSelected(binding: LeaderBinding.SingleBinding) {
        // Visual feedback - highlight the selected binding
        list.setSelectedValue(binding, true)
    }

    override fun onGroupEntered(group: LeaderBinding.GroupBinding) {
        // Navigate to sub-group using ListPopupImpl's built-in mechanism
        list.setSelectedValue(group, true)
        handleSelect(true)
    }

    override fun onDismiss() {
        cancel()
    }

    override fun dispose() {
        dispatcher.removeListener(this)
        super.dispose()
    }

    // Consume all key events - dispatcher handles them
    override fun process(aEvent: KeyEvent) {
        aEvent.consume()
    }

    override fun getListElementRenderer(): ListCellRenderer<*> = ActionItemRenderer()

    override fun createPopup(parent: WizardPopup?, step: PopupStep<*>?, parentValue: Any?): LeaderPopupUI {
        return LeaderPopupUI(
            parent?.project,
            step as LeaderListStep,
            parent,
            parentValue
        )
    }
}

/**
 * Step for the leader popup list.
 */
class LeaderListStep(
    title: String? = null,
    val dataContext: DataContext,
    values: List<LeaderBinding>
) : BaseListPopupStep<LeaderBinding>(title, values) {

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
            is LeaderBinding.GroupBinding -> {
                LeaderListStep(null, dataContext, selectedValue.bindings)
            }
            null -> null
        }

    override fun getBackgroundFor(value: LeaderBinding?) = UIUtil.getPanelBackground()
}

/**
 * Renderer for leader binding items in the popup list.
 */
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
