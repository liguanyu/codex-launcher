package com.github.x0x0b.codexlauncher.terminal

import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.components.Service
import com.intellij.openapi.diagnostic.logger
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.Key
import com.intellij.openapi.wm.ToolWindowManager
import com.intellij.terminal.frontend.toolwindow.TerminalToolWindowTabsManager
import com.intellij.terminal.frontend.view.TerminalView
import com.intellij.terminal.frontend.view.TerminalViewSessionState
import com.intellij.terminal.ui.TerminalWidget
import com.intellij.ui.content.Content
import org.jetbrains.plugins.terminal.TerminalToolWindowManager
import org.jetbrains.plugins.terminal.TerminalToolWindowFactory

/**
 * Manages Codex launch sessions and inserts context into the selected visible terminal.
 * Encapsulates lookup, reuse, focus, and command execution logic so actions stay thin.
 */
@Service(Service.Level.PROJECT)
@Suppress("UnstableApiUsage")
class CodexTerminalManager(private val project: Project) {

    companion object {
        private val CODEX_TERMINAL_KEY = Key.create<Boolean>("codex.launcher.codexTerminal")
        private val CODEX_TERMINAL_RUNNING_KEY = Key.create<Boolean>("codex.launcher.codexTerminal.running")
        private val CODEX_TERMINAL_CALLBACK_KEY = Key.create<Boolean>("codex.launcher.codexTerminal.callbackRegistered")
    }

    private val logger = logger<CodexTerminalManager>()
    private val scriptFactory = CommandScriptFactory(project)

    private data class CodexTerminal(val widget: TerminalWidget, val content: Content)

    private sealed interface InputTarget {
        data class Unavailable(val state: TerminalInputState) : InputTarget
        data class Reworked(val view: TerminalView) : InputTarget
        data class Classic(val widget: TerminalWidget) : InputTarget
    }

    /**
     * Launches or reuses the Codex terminal for the given command.
     * @throws Throwable when terminal creation or command execution fails.
     */
    fun launch(baseDir: String, command: String) {
        val terminalManager = TerminalToolWindowManager.getInstance(project)
        var existingTerminal = locateCodexTerminal(terminalManager)

        existingTerminal?.let { terminal ->
            ensureTerminationCallback(terminal.widget, terminal.content)
            if (isCodexRunning(terminal)) {
                logger.info("Focusing active Codex terminal")
                focusCodexTerminal(terminalManager, terminal)
                return
            }

            if (reuseCodexTerminal(terminal, command)) {
                logger.info("Reused existing Codex terminal for new Codex run")
                focusCodexTerminal(terminalManager, terminal)
                return
            } else {
                clearCodexMetadata(terminalManager, terminal.widget)
                existingTerminal = null
            }
        }

        var widget: TerminalWidget? = null
        try {
            widget = terminalManager.createShellWidget(baseDir, "Codex", true, true)
            val content = markCodexTerminal(terminalManager, widget)
            if (!sendCommandToTerminal(widget, content, command)) {
                throw IllegalStateException("Failed to execute Codex command")
            }
            if (content != null) {
                focusCodexTerminal(terminalManager, CodexTerminal(widget, content))
            }
        } catch (sendError: Throwable) {
            widget?.let { clearCodexMetadata(terminalManager, it) }
            throw sendError
        }
    }

    /**
     * Inspects the selected terminal without treating lookup failures as a hidden terminal.
     * Keyboard focus may be in the editor or toolbar; no Codex binding is required.
     */
    fun getTerminalInputState(): TerminalInputState {
        return try {
            inputState(findDisplayedTerminal())
        } catch (t: Throwable) {
            logger.warn("Failed to inspect terminal active state", t)
            TerminalInputState.UNSUPPORTED
        }
    }

    /** Sends text to one selected target, without executing it or falling back to another tab. */
    fun typeIntoActiveTerminal(text: String, asPaste: Boolean = false): TerminalInsertResult {
        return try {
            val target = findDisplayedTerminal()
            val state = inputState(target)
            if (state != TerminalInputState.READY) {
                return TerminalInsertResult.Failed(requireNotNull(state.message))
            }
            if (asPaste && text.contains("\u001b[201~")) {
                return TerminalInsertResult.Failed("The selection contains a terminal paste terminator; no text was sent")
            }

            when (target) {
                is InputTarget.Reworked -> {
                    // The frontend owns encoding and bracketed-paste handling. Do not use
                    // a raw connector or shouldExecute() for editor context.
                    target.view.createSendTextBuilder().useBracketedPasteMode().send(text)
                    TerminalInsertResult.Accepted
                }
                is InputTarget.Classic -> {
                    val sent = if (asPaste) pasteText(target.widget, text) else typeText(target.widget, text)
                    if (sent) TerminalInsertResult.Accepted else TerminalInsertResult.Failed(
                        "Failed to write to the current Classic terminal; no other terminal was selected",
                    )
                }
                is InputTarget.Unavailable -> TerminalInsertResult.Failed(requireNotNull(target.state.message))
            }
        } catch (t: Throwable) {
            logger.warn("Failed to type into the current terminal", t)
            TerminalInsertResult.Failed("Failed to send text to the current terminal; see the IDE log for details")
        }
    }

    private fun locateCodexTerminal(manager: TerminalToolWindowManager): CodexTerminal? = try {
        manager.terminalWidgets.asSequence().mapNotNull { widget ->
            val content = manager.getContainer(widget)?.content ?: return@mapNotNull null
            val isCodex = content.getUserData(CODEX_TERMINAL_KEY) == true || content.displayName == "Codex"
            if (!isCodex) {
                return@mapNotNull null
            }
            CodexTerminal(widget, content)
        }.firstOrNull()
    } catch (t: Throwable) {
        logger.warn("Failed to inspect existing terminal widgets", t)
        null
    }

    private fun findDisplayedTerminal(): InputTarget {
        // Resolve the tool window directly: the old manager only owns Classic sessions.
        val toolWindow = ToolWindowManager.getInstance(project)
            .getToolWindow(TerminalToolWindowFactory.TOOL_WINDOW_ID)
            ?: return InputTarget.Unavailable(TerminalInputState.NO_VISIBLE_TERMINAL)
        if (!toolWindow.isVisible) {
            return InputTarget.Unavailable(TerminalInputState.NO_VISIBLE_TERMINAL)
        }
        val contentManager = toolWindow.contentManager
        val selectedContent = contentManager.selectedContent
            ?: return InputTarget.Unavailable(
                if (contentManager.isEmpty) TerminalInputState.NO_VISIBLE_TERMINAL else TerminalInputState.NOT_READY,
            )

        val tab = TerminalToolWindowTabsManager.getInstance(project).tabs
            .firstOrNull { it.content === selectedContent }
        if (tab != null) {
            return InputTarget.Reworked(tab.view)
        }
        val widget = TerminalToolWindowManager.findWidgetByContent(selectedContent)
        return if (widget != null) InputTarget.Classic(widget)
        else InputTarget.Unavailable(TerminalInputState.UNSUPPORTED)
    }

    private fun inputState(target: InputTarget): TerminalInputState = when (target) {
        is InputTarget.Unavailable -> target.state
        is InputTarget.Reworked -> when (target.view.sessionState.value) {
            TerminalViewSessionState.Running -> TerminalInputState.READY
            TerminalViewSessionState.Terminated -> TerminalInputState.TERMINATED
            else -> TerminalInputState.NOT_READY
        }
        is InputTarget.Classic -> {
            val connector = target.widget.ttyConnector
            when {
                connector == null -> TerminalInputState.NOT_READY
                !connector.isConnected -> TerminalInputState.TERMINATED
                else -> TerminalInputState.READY
            }
        }
    }

    private fun focusCodexTerminal(
        manager: TerminalToolWindowManager,
        terminal: CodexTerminal
    ) {
        ApplicationManager.getApplication().invokeLater {
            if (project.isDisposed) {
                return@invokeLater
            }

            try {
                val toolWindow = resolveTerminalToolWindow(manager)
                if (toolWindow == null) {
                    logger.warn("Terminal tool window is not available for focusing Codex")
                    return@invokeLater
                }

                val contentManager = toolWindow.contentManager
                if (contentManager.selectedContent != terminal.content) {
                    contentManager.setSelectedContent(terminal.content, true)
                }

                toolWindow.activate({
                    try {
                        terminal.widget.requestFocus()
                    } catch (focusError: Throwable) {
                        logger.warn("Failed to request focus for Codex terminal", focusError)
                    }
                }, true)
            } catch (focusError: Throwable) {
                logger.warn("Failed to focus existing Codex terminal", focusError)
            }
        }
    }

    private fun resolveTerminalToolWindow(
        manager: TerminalToolWindowManager
    ) = manager.getToolWindow()
        ?: ToolWindowManager.getInstance(project)
            .getToolWindow(TerminalToolWindowFactory.TOOL_WINDOW_ID)

    private fun markCodexTerminal(manager: TerminalToolWindowManager, widget: TerminalWidget): Content? {
        return try {
            manager.getContainer(widget)?.content?.also { content ->
                content.putUserData(CODEX_TERMINAL_KEY, true)
                setCodexRunning(content, false)
                ensureTerminationCallback(widget, content)
                content.displayName = "Codex"
            }
        } catch (t: Throwable) {
            logger.warn("Failed to tag Codex terminal metadata", t)
            null
        }
    }

    private fun clearCodexMetadata(manager: TerminalToolWindowManager, widget: TerminalWidget) {
        try {
            manager.getContainer(widget)?.content?.let { content ->
                clearCodexMetadata(content)
            }
        } catch (t: Throwable) {
            logger.warn("Failed to clear Codex terminal metadata", t)
        }
    }

    private fun clearCodexMetadata(content: Content) {
        content.putUserData(CODEX_TERMINAL_KEY, null)
        content.putUserData(CODEX_TERMINAL_RUNNING_KEY, null)
        content.putUserData(CODEX_TERMINAL_CALLBACK_KEY, null)
    }

    private fun reuseCodexTerminal(
        terminal: CodexTerminal,
        command: String
    ): Boolean {
        ensureTerminationCallback(terminal.widget, terminal.content)
        return sendCommandToTerminal(terminal.widget, terminal.content, command)
    }

    private fun sendCommandToTerminal(
        widget: TerminalWidget,
        content: Content?,
        command: String
    ): Boolean {
        val plan = scriptFactory.buildPlan(command) ?: return false

        return try {
            widget.sendCommandToExecute(plan.command)
            setCodexRunning(content, true)
            true
        } catch (throwable: Throwable) {
            logger.warn("Failed to execute Codex command", throwable)
            setCodexRunning(content, false)
            runCatching { plan.cleanupOnFailure() }
            false
        }
    }

    private fun isCodexRunning(terminal: CodexTerminal): Boolean {
        val liveState = invokeIsCommandRunning(terminal.widget)
        if (liveState != null) {
            setCodexRunning(terminal.content, liveState)
            return liveState
        }
        return terminal.content.getUserData(CODEX_TERMINAL_RUNNING_KEY) ?: false
    }

    private fun setCodexRunning(content: Content?, running: Boolean) {
        content?.putUserData(CODEX_TERMINAL_RUNNING_KEY, running)
    }

    private fun ensureTerminationCallback(widget: TerminalWidget, content: Content?) {
        if (content == null) return
        if (content.getUserData(CODEX_TERMINAL_CALLBACK_KEY) == true) return
        try {
            widget.addTerminationCallback({ setCodexRunning(content, false) }, content)
            content.putUserData(CODEX_TERMINAL_CALLBACK_KEY, true)
        } catch (t: Throwable) {
            logger.warn("Failed to register termination callback", t)
        }
    }

    private fun invokeIsCommandRunning(widget: TerminalWidget): Boolean? {
        return runCatching {
            val method = widget.javaClass.methods.firstOrNull { it.name == "isCommandRunning" && it.parameterCount == 0 }
            method?.apply { isAccessible = true }?.invoke(widget) as? Boolean
        }.getOrNull()
    }

    private fun pasteText(widget: TerminalWidget, text: String): Boolean {
        // Generic typeText/pasteText reflection does not guarantee bracketed-paste semantics.
        // Only use the raw connector so the final newline stays inside the paste boundaries.
        val connector = runCatching { widget.ttyConnector }.getOrNull()
        if (connector == null) {
            logger.warn("Cannot paste selection: current terminal has no raw connector")
            return false
        }
        return runCatching {
            CodexTerminalPaste.write(text) { connector.write(it) }
            true
        }.getOrElse {
            logger.warn("Failed to paste selection into the current terminal", it)
            false
        }
    }

    private fun typeText(widget: TerminalWidget, text: String): Boolean {
        val connector = runCatching { widget.ttyConnector }.getOrNull()
        if (connector != null) {
            return runCatching {
                connector.write(text)
                true
            }.getOrElse {
                logger.warn("Failed to write to terminal connector", it)
                false
            }
        }

        val methods = widget.javaClass.methods
        val typeMethod = methods.firstOrNull { it.name == "typeText" && it.parameterCount == 1 && it.parameterTypes[0] == String::class.java }
        if (typeMethod != null) {
            return runCatching {
                typeMethod.isAccessible = true
                typeMethod.invoke(widget, text)
                true
            }.getOrElse {
                logger.warn("Failed to invoke typeText on terminal", it)
                false
            }
        }

        val pasteMethod = methods.firstOrNull { it.name == "pasteText" && it.parameterCount == 1 && it.parameterTypes[0] == String::class.java }
        if (pasteMethod != null) {
            return runCatching {
                pasteMethod.isAccessible = true
                pasteMethod.invoke(widget, text)
                true
            }.getOrElse {
                logger.warn("Failed to invoke pasteText on terminal", it)
                false
            }
        }

        return false
    }
}
