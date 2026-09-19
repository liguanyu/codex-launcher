package com.github.x0x0b.codexlauncher.actions

import com.github.x0x0b.codexlauncher.http.HttpTriggerService
import com.github.x0x0b.codexlauncher.settings.CodexLauncherSettings
import com.github.x0x0b.codexlauncher.terminal.CodexTerminalManager
import com.github.x0x0b.codexlauncher.terminal.TerminalInputState
import com.github.x0x0b.codexlauncher.terminal.TerminalInsertResult
import com.intellij.notification.NotificationGroupManager
import com.intellij.notification.NotificationType
import com.intellij.openapi.actionSystem.AnAction
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.actionSystem.ActionUpdateThread
import com.intellij.openapi.actionSystem.ActionToolbar
import com.intellij.openapi.actionSystem.Presentation
import com.intellij.openapi.actionSystem.ex.CustomComponentAction
import com.intellij.openapi.actionSystem.impl.ActionButton
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.components.service
import com.intellij.openapi.diagnostic.logger
import com.intellij.openapi.project.DumbAware
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.IconLoader
import java.awt.event.MouseEvent
import javax.swing.Icon
import javax.swing.JComponent
import javax.swing.SwingUtilities

/** Sends context to the visible terminal; toolbar right-click always opens Codex. */
class LaunchCodexAction : AnAction(DEFAULT_TEXT, DEFAULT_DESCRIPTION, null), DumbAware, CustomComponentAction {

    companion object {
        private const val CODEX_COMMAND = "codex"
        private const val NOTIFICATION_TITLE = "Codex Launcher"
        private const val DEFAULT_TEXT = "Launch Codex"
        private const val DEFAULT_DESCRIPTION = "Left-click to open Codex when Terminal is hidden; right-click to always open or focus Codex"
        private const val ACTIVE_TEXT = "Insert File or Selection into Current Terminal"
        private const val ACTIVE_DESCRIPTION = "Left-click to insert the current file or selection into the selected terminal; right-click to open or focus Codex"
        private val DEFAULT_ICON = IconLoader.getIcon("/icons/codex.svg", LaunchCodexAction::class.java)
        private val ACTIVE_ICON = IconLoader.getIcon("/icons/codex_active.svg", LaunchCodexAction::class.java)
    }

    private val logger = logger<LaunchCodexAction>()

    override fun actionPerformed(e: AnActionEvent) {
        val project = e.project
        if (project == null) {
            logger.warn("No project context available for Codex launch")
            return
        }

        val terminalManager = project.service<CodexTerminalManager>()
        val mouseEvent = e.inputEvent as? MouseEvent
        if (mouseEvent != null && SwingUtilities.isRightMouseButton(mouseEvent)) {
            launchCodex(project, terminalManager)
            return
        }
        when (val state = terminalManager.getTerminalInputState()) {
            TerminalInputState.NO_VISIBLE_TERMINAL -> launchCodex(project, terminalManager)
            TerminalInputState.READY -> performInsert(project, terminalManager)
            else -> notify(project, requireNotNull(state.message), NotificationType.WARNING)
        }
    }

    override fun update(e: AnActionEvent) {
        super.update(e)
        val state = determineToolbarState(e.project)
        e.presentation.icon = state.icon
        e.presentation.text = state.text
        e.presentation.description = state.description
    }

    override fun getActionUpdateThread(): ActionUpdateThread = ActionUpdateThread.EDT

    override fun createCustomComponent(presentation: Presentation, place: String): JComponent {
        return object : ActionButton(this@LaunchCodexAction, presentation, place, ActionToolbar.DEFAULT_MINIMUM_BUTTON_SIZE) {
            private var rightButtonPressed = false

            override fun processMouseEvent(event: MouseEvent) {
                if (!SwingUtilities.isRightMouseButton(event)) {
                    super.processMouseEvent(event)
                    return
                }

                // Consume the entire right-click sequence before toolbar popup listeners see it.
                // Only a release following a press on this button executes the action.
                event.consume()
                when (event.id) {
                    MouseEvent.MOUSE_PRESSED -> rightButtonPressed = isEnabled && contains(event.point)
                    MouseEvent.MOUSE_RELEASED -> {
                        val shouldLaunch = rightButtonPressed && isEnabled && contains(event.point)
                        rightButtonPressed = false
                        if (shouldLaunch) {
                            performAction(event)
                        }
                    }
                }
            }
        }
    }

    private fun performInsert(project: Project, terminalManager: CodexTerminalManager) {
        val payload = InsertPayloadResolver.resolve(project)
        if (payload == null) {
            notify(project, "No active file to send to the current terminal", NotificationType.INFORMATION)
            return
        }

        val insertText = InsertPayloadResolver.formatInsertText(payload)

        val result = terminalManager.typeIntoActiveTerminal(insertText, asPaste = payload.selectedText != null)
        if (result is TerminalInsertResult.Failed) {
            notify(project, result.message, NotificationType.WARNING)
            return
        }

        logger.info(
            "Queued file context for the current terminal: path=${payload.relativePath}, " +
                "range=${payload.lineRange}, selectedChars=${payload.selectedText?.length ?: 0}",
        )
    }

    private fun launchCodex(project: Project, terminalManager: CodexTerminalManager) {
        val baseDir = project.basePath ?: System.getProperty("user.home")
        logger.info("Launching Codex in directory: $baseDir")

        try {
            val httpService = ApplicationManager.getApplication().service<HttpTriggerService>()
            val port = httpService.getActualPort()
            if (port == 0) {
                logger.warn("HTTP service port is not available")
                notify(project, "HTTP service is not properly initialized", NotificationType.WARNING)
                return
            }

            val settings = service<CodexLauncherSettings>()
            val command = buildCommand(settings.getArgs(port, baseDir))
            terminalManager.launch(baseDir, command)
            logger.info("Codex command executed successfully: $command")
        } catch (t: Throwable) {
            logger.error("Failed to launch Codex", t)
            notify(project, "Failed to launch Codex: ${t.message}", NotificationType.ERROR)
        }
    }

    private fun buildCommand(args: String): String {
        return buildString {
            append(CODEX_COMMAND)
            if (args.isNotBlank()) {
                append(' ')
                append(args)
            }
        }
    }

    private fun notify(project: Project, content: String, type: NotificationType) {
        runCatching {
            val group = NotificationGroupManager.getInstance().getNotificationGroup("CodexLauncher")
            group.createNotification(NOTIFICATION_TITLE, content, type).notify(project)
        }.onFailure { error ->
            logger.error("Failed to show notification: $content", error)
        }
    }

    private fun determineToolbarState(project: Project?): ToolbarState {
        if (project == null) {
            return ToolbarState(DEFAULT_ICON, DEFAULT_TEXT, DEFAULT_DESCRIPTION)
        }

        val manager = project.service<CodexTerminalManager>()
        return when (val state = manager.getTerminalInputState()) {
            TerminalInputState.NO_VISIBLE_TERMINAL -> ToolbarState(DEFAULT_ICON, DEFAULT_TEXT, DEFAULT_DESCRIPTION)
            TerminalInputState.READY -> ToolbarState(ACTIVE_ICON, ACTIVE_TEXT, ACTIVE_DESCRIPTION)
            else -> ToolbarState(ACTIVE_ICON, ACTIVE_TEXT, "${state.message}; right-click to open or focus Codex")
        }
    }

    private data class ToolbarState(val icon: Icon, val text: String, val description: String)
}
