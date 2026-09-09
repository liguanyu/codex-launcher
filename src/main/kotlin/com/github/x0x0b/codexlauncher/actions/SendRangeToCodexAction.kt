package com.github.x0x0b.codexlauncher.actions

import com.github.x0x0b.codexlauncher.terminal.CodexTerminalManager
import com.intellij.notification.NotificationGroupManager
import com.intellij.notification.NotificationType
import com.intellij.openapi.actionSystem.AnAction
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.actionSystem.ActionUpdateThread
import com.intellij.openapi.actionSystem.CommonDataKeys
import com.intellij.openapi.components.service
import com.intellij.openapi.diagnostic.logger
import com.intellij.openapi.project.DumbAware
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.IconLoader

class SendRangeToCodexAction : AnAction(
    "Send File or Selection to Codex",
    "Insert the current file path, or its line range and selected text, into Codex",
    IconLoader.getIcon("/icons/codex_active.svg", SendRangeToCodexAction::class.java)
), DumbAware {

    companion object {
        private const val NOTIFICATION_TITLE = "Codex Launcher"
    }

    private val logger = logger<SendRangeToCodexAction>()

    override fun actionPerformed(e: AnActionEvent) {
        val project = e.project
        val editor = e.getData(CommonDataKeys.EDITOR)
        val virtualFile = e.getData(CommonDataKeys.VIRTUAL_FILE)
        if (project == null || editor == null) {
            return
        }

        val payload = InsertPayloadResolver.resolve(
            project = project,
            editor = editor,
            file = virtualFile
        )

        if (payload == null) {
            notify(project, "Unable to determine the current file or selection", NotificationType.INFORMATION)
            return
        }

        val insertText = InsertPayloadResolver.formatInsertText(payload)
        val terminalManager = project.service<CodexTerminalManager>()
        if (!terminalManager.isCodexTerminalActive()) {
            notify(project, "Launch Codex first to send ranges", NotificationType.INFORMATION)
            return
        }

        if (!terminalManager.typeIntoActiveCodexTerminal(insertText, asPaste = payload.selectedText != null)) {
            notify(project, "Failed to insert file context into Codex; selection paste requires a compatible terminal", NotificationType.WARNING)
            return
        }

        logger.info(
            "Sent file context to Codex: path=${payload.relativePath}, " +
                "range=${payload.lineRange}, selectedChars=${payload.selectedText?.length ?: 0}",
        )
    }

    override fun update(e: AnActionEvent) {
        val project = e.project
        val editor = e.getData(CommonDataKeys.EDITOR)

        if (project == null || editor == null) {
            e.presentation.isEnabledAndVisible = false
            return
        }

        val terminalManager = project.service<CodexTerminalManager>()
        if (!terminalManager.isCodexTerminalActive()) {
            e.presentation.isEnabledAndVisible = false
            return
        }

        val hasSelection = editor.selectionModel.hasSelection()
        val inContextBar = e.place == "EditorContextBar"

        val visible = !inContextBar || hasSelection
        e.presentation.isVisible = visible
        e.presentation.isEnabled = visible && (hasSelection || !inContextBar)
    }

    override fun getActionUpdateThread(): ActionUpdateThread = ActionUpdateThread.EDT

    private fun notify(project: Project, content: String, type: NotificationType) {
        runCatching {
            val group = NotificationGroupManager.getInstance().getNotificationGroup("CodexLauncher")
            group.createNotification(NOTIFICATION_TITLE, content, type).notify(project)
        }.onFailure { error ->
            logger.warn("Failed to display notification: $content", error)
        }
    }
}
