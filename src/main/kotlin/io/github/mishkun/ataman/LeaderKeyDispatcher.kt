package io.github.mishkun.ataman

import com.intellij.openapi.components.Service
import java.awt.KeyEventDispatcher
import java.awt.KeyboardFocusManager
import java.awt.event.KeyEvent
import javax.swing.KeyStroke

/** Captures input early; the active popup owns navigation and action execution. */
@Service(Service.Level.APP)
class LeaderKeyDispatcher {
    class Session internal constructor(internal val focusManager: KeyboardFocusManager) {
        internal lateinit var callback: KeyEventDispatcher
        internal var root: LeaderPopupUI? = null
        internal var active: LeaderPopupUI? = null
        internal val pending = ArrayDeque<KeyEvent>()
        internal var ready = false
        internal var draining = false
        internal var closing = false
    }

    private var current: Session? = null

    fun start(): Session {
        val previous = current
        val session = Session(KeyboardFocusManager.getCurrentKeyboardFocusManager())
        session.callback = KeyEventDispatcher { event -> capture(session, event) }
        current = session
        try {
            session.focusManager.addKeyEventDispatcher(session.callback)
            // Register first: closing the previous popup can pump events or invoke
            // another LeaderAction. The newest token must win that reentrancy.
            stop(previous)
        } catch (error: Throwable) {
            try { stop(session) } catch (cleanup: Throwable) { error.addSuppressed(cleanup) }
            try { stop(previous) } catch (cleanup: Throwable) { error.addSuppressed(cleanup) }
            throw error
        }
        return session
    }

    internal fun owns(session: Session) = current === session

    /** Shared by early Java capture and the IDE's popup-first dispatch route. */
    internal fun capture(session: Session, event: KeyEvent): Boolean {
        if (!owns(session)) return false
        if (event.id !in listOf(KeyEvent.KEY_PRESSED, KeyEvent.KEY_RELEASED, KeyEvent.KEY_TYPED)) return false
        if (event.keyCode in listOf(KeyEvent.VK_SHIFT, KeyEvent.VK_CONTROL, KeyEvent.VK_ALT, KeyEvent.VK_META)) return false
        session.pending.addLast(KeyEvent(event.component, event.id, event.`when`, event.modifiersEx,
            event.keyCode, event.keyChar, event.keyLocation))
        event.consume()
        drain(session)
        return true
    }

    internal fun attach(session: Session, popup: LeaderPopupUI) {
        if (!owns(session)) return
        if (popup.parent == null) session.root = popup
        session.active = popup
    }

    internal fun ready(session: Session) {
        if (!owns(session) || session.root?.isDisposed != false) return
        session.ready = true
        drain(session)
    }

    /** Mouse-driven child construction can pump input just like keyboard navigation. */
    internal fun selecting(session: Session?, selection: () -> Unit) {
        if (session == null) return selection()
        if (!owns(session)) return
        if (session.draining) return selection()
        session.draining = true
        try {
            selection()
        } catch (error: Throwable) {
            try { stop(session) } catch (cleanup: Throwable) { error.addSuppressed(cleanup) }
            throw error
        } finally {
            session.draining = false
            drain(session)
        }
    }

    /** Detach before finalRunnable can execute or a closing chain can restore a parent. */
    internal fun finish(session: Session) {
        if (owns(session)) current = null
        session.closing = true
        session.ready = false
        session.pending.clear()
        session.active = null
        session.focusManager.removeKeyEventDispatcher(session.callback)
    }

    fun stop(session: Session? = current) {
        if (session == null) return
        finish(session)
        try {
            session.root?.takeUnless { it.isDisposed }?.cancel()
        } finally {
            session.root = null
        }
    }

    internal fun closed(session: Session, popup: LeaderPopupUI) {
        if (popup === session.root) {
            finish(session)
            session.root = null
        } else if (owns(session) && session.active === popup && !session.closing) {
            session.active = (popup.parent as? LeaderPopupUI)?.takeUnless { it.isDisposed || it.closing }
        }
    }

    private fun drain(session: Session) {
        if (!session.ready || session.draining) return
        session.draining = true
        try {
            while (owns(session) && session.ready && session.pending.isNotEmpty()) {
                handle(session, session.pending.removeFirst())
            }
        } catch (error: Throwable) {
            try { stop(session) } catch (cleanup: Throwable) { error.addSuppressed(cleanup) }
            throw error
        } finally {
            session.draining = false
        }
    }

    private fun handle(session: Session, event: KeyEvent) {
        val popup = session.active?.takeUnless { it.isDisposed } ?: return
        if (event.id == KeyEvent.KEY_TYPED) return
        val stroke = event.toKeyStroke() ?: return
        val bindings = (popup.step as LeaderListStep).values
        // Retain character-first layout handling, but honor phase and modifiers.
        // Exact strokes cover function keys and layouts without a configured character.
        val binding = bindings.firstOrNull {
            it.char.length == 1 && it.char[0] == event.keyChar &&
                it.key.isOnKeyRelease == stroke.isOnKeyRelease && it.key.modifiers == stroke.modifiers
        } ?: bindings.firstOrNull { it.key == stroke }
        if (binding != null) {
            popup.list.setSelectedValue(binding, true)
            popup.handleSelect(true)
        } else {
            // Configured bindings override native commands, as in the original popup.
            popup.replayCommand(event)
        }
    }
}

/**
 * Utility to convert a KeyEvent to a KeyStroke for matching against bindings.
 */
fun KeyEvent.toKeyStroke(): KeyStroke? {
    return when (id) {
        KeyEvent.KEY_TYPED -> {
            if (keyChar != KeyEvent.CHAR_UNDEFINED) {
                KeyStroke.getKeyStroke(keyChar)
            } else null
        }
        KeyEvent.KEY_PRESSED -> {
            KeyStroke.getKeyStroke(keyCode, modifiersEx, false)
        }
        KeyEvent.KEY_RELEASED -> {
            KeyStroke.getKeyStroke(keyCode, modifiersEx, true)
        }
        else -> null
    }
}
