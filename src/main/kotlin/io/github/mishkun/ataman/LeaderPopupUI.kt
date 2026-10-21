package io.github.mishkun.ataman

import com.intellij.icons.AllIcons
import com.intellij.openapi.actionSystem.DataContext
import com.intellij.openapi.application.WriteIntentReadAction
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
import java.awt.event.ActionEvent
import java.awt.event.InputEvent
import java.awt.event.KeyEvent
import javax.swing.AbstractAction
import javax.swing.Action
import javax.swing.JComponent
import javax.swing.JLabel
import javax.swing.JList
import javax.swing.JPanel
import javax.swing.ListCellRenderer
import javax.swing.KeyStroke

/** The active popup owns both keyboard and mouse navigation. */
class LeaderPopupUI(
    project: Project? = null,
    step: LeaderListStep,
    parent: WizardPopup? = null,
    parentObject: Any? = null,
    private val session: LeaderKeyDispatcher.Session? = (parent as? LeaderPopupUI)?.session
) : ListPopupImpl(project, parent, step, parentObject) {

    private val dispatcher = service<LeaderKeyDispatcher>()
    private val keyboardCommands = mutableMapOf<KeyStroke, Action>()
    internal var closing = false
        private set

    init {
        session?.let { dispatcher.attach(it, this) }
        // Use the owned chain rather than the platform's global active root. This also
        // works when showing is intercepted in tests or another popup becomes active.
        registerKeyboardCommand("atamanDismissAll", KeyEvent.VK_ESCAPE, InputEvent.SHIFT_DOWN_MASK, object : AbstractAction() {
            override fun actionPerformed(e: ActionEvent) {
                if (speedSearch.isHoldingFilter) speedSearch.reset()
                else generateSequence(this@LeaderPopupUI) { it.parent as? LeaderPopupUI }.last().cancel()
            }
        })
        if (session != null) {
            // WizardPopup.dispatch/registerAction are final. Override its three
            // standard registered commands using APIs also present in IDE 2024.3.
            registerKeyboardCommand("atamanBack", KeyEvent.VK_ESCAPE, 0, object : AbstractAction() {
                override fun actionPerformed(e: ActionEvent) { goBack() }
            })
            registerKeyboardCommand("atamanSelect", KeyEvent.VK_ENTER, 0, object : AbstractAction() {
                override fun actionPerformed(e: ActionEvent) {
                    handleSelect(true, createKeyEvent(e, KeyEvent.VK_ENTER))
                }
            })
        }
    }

    private fun registerKeyboardCommand(name: String, code: Int, modifiers: Int, action: Action) {
        val token = session
        val stroke = KeyStroke.getKeyStroke(code, modifiers)
        if (token == null) {
            registerAction(name, stroke, action)
            return
        }
        keyboardCommands[stroke] = action
        registerAction(name, stroke, object : AbstractAction() {
            override fun isEnabled() = action.isEnabled
            override fun actionPerformed(e: ActionEvent) {
                dispatcher.capture(token, KeyEvent(list, stroke.keyEventType, e.`when`, e.modifiers,
                    stroke.keyCode, stroke.keyChar))
            }
        })
    }

    internal fun replayCommand(event: KeyEvent): Boolean {
        val action = keyboardCommands[event.toKeyStroke()] ?: return false
        if (!action.isEnabled) return false
        // Match WizardPopup's native action invocation, without recursive dispatch.
        WriteIntentReadAction.run(Runnable {
            action.actionPerformed(ActionEvent(content, event.id, "", event.`when`, event.modifiersEx))
        })
        return true
    }

    override fun goBack() {
        if (parent == null && !speedSearch.isHoldingFilter) cancel() else super.goBack()
    }

    override fun handleSelect(handleFinalChoices: Boolean) {
        dispatcher.selecting(session) { super.handleSelect(handleFinalChoices) }
    }

    override fun handleSelect(handleFinalChoices: Boolean, event: InputEvent?) {
        dispatcher.selecting(session) { super.handleSelect(handleFinalChoices, event) }
    }

    override fun cancel(event: InputEvent?) {
        closing = true
        if (parent == null) session?.let { dispatcher.finish(it) }
        super.cancel(event)
    }

    override fun dispose() {
        closing = true
        session?.let { dispatcher.closed(it, this) }
        super.dispose()
    }

    override fun processKeyEvent(event: KeyEvent) {
        if (session == null) {
            super.processKeyEvent(event)
        } else {
            // Unregistered input shares the queue used by registered command wrappers.
            dispatcher.capture(session, event)
            event.consume()
        }
    }

    // Do not type unmatched leader input into the list/speed-search.
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
    values: List<LeaderBinding>,
    private val session: LeaderKeyDispatcher.Session? = null
) : BaseListPopupStep<LeaderBinding>(title, values) {

    override fun hasSubstep(selectedValue: LeaderBinding?): Boolean {
        return selectedValue is LeaderBinding.GroupBinding
    }

    override fun onChosen(selectedValue: LeaderBinding?, finalChoice: Boolean): PopupStep<*>? =
        when (selectedValue) {
            is LeaderBinding.SingleBinding -> {
                session?.let { service<LeaderKeyDispatcher>().finish(it) }
                doFinalStep {
                    selectedValue.action.forEach { action ->
                        executeAction(action, dataContext)
                    }
                }
            }
            is LeaderBinding.GroupBinding -> {
                LeaderListStep(null, dataContext, selectedValue.bindings, session)
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
