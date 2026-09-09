package com.github.x0x0b.codexlauncher.actions

import com.intellij.openapi.diagnostic.logger
import com.intellij.openapi.editor.Document
import com.intellij.openapi.editor.Editor
import com.intellij.openapi.fileEditor.FileEditorManager
import com.intellij.openapi.project.Project
import com.intellij.openapi.vfs.VirtualFile
import java.nio.file.Path

object InsertPayloadResolver {

    private val logger = logger<InsertPayloadResolver>()

    fun resolve(
        project: Project,
        editor: Editor? = null,
        file: VirtualFile? = null
    ): InsertPayload? {
        val editorManager = FileEditorManager.getInstance(project)
        val targetFile = file ?: editorManager.selectedFiles.firstOrNull() ?: return null
        val relativePath = resolveRelativePath(project, targetFile) ?: return null

        val targetEditor = editor ?: editorManager.selectedTextEditor
        val selection = targetEditor?.selectionModel
        val selectedText = selection?.takeIf { it.hasSelection() }?.selectedText?.takeIf { it.isNotEmpty() }
        val lineRange = if (targetEditor != null && selection != null && selectedText != null) {
            toLineRange(targetEditor.document, selection.selectionStart, selection.selectionEnd)
                ?: return null
        } else {
            null
        }

        return InsertPayload(relativePath, lineRange, selectedText)
    }

    fun formatInsertText(payload: InsertPayload): String {
        return buildString {
            append(payload.relativePath)
            payload.lineRange?.let { range ->
                append(": ")
                append(range.start)
                range.end?.takeIf { it != range.start }?.let { end ->
                    append('-')
                    append(end)
                }
            }
            append(' ')
            payload.selectedText?.let {
                append(it)
                append('\n')
            }
        }
    }

    private fun resolveRelativePath(project: Project, file: VirtualFile): String? {
        val rawPath = file.canonicalPath ?: file.presentableUrl ?: file.path
        val basePath = project.basePath ?: return rawPath
        return runCatching {
            val base = Path.of(basePath).normalize()
            val target = Path.of(rawPath).normalize()
            if (target.startsWith(base)) base.relativize(target).toString() else rawPath
        }.getOrElse {
            logger.warn("Failed to compute relative path for $rawPath", it)
            rawPath
        }
    }

    private fun toLineRange(document: Document, startOffset: Int, endOffset: Int): LineRange? {
        if (startOffset < 0 || endOffset < startOffset) {
            return null
        }

        val startLine = runCatching { document.getLineNumber(startOffset) }.getOrElse {
            logger.warn("Failed to resolve start line", it)
            return null
        }

        val adjustedEnd = when {
            endOffset <= startOffset -> startOffset
            else -> endOffset - 1
        }

        val endLine = runCatching { document.getLineNumber(adjustedEnd.coerceAtLeast(startOffset)) }.getOrElse {
            logger.warn("Failed to resolve end line", it)
            startLine
        }

        val start = startLine + 1
        val end = (endLine + 1).takeIf { it > start }
        return LineRange(start, end)
    }
}

/** File context to insert, including the exact editor text when a selection exists. */
data class InsertPayload(
    val relativePath: String,
    val lineRange: LineRange?,
    val selectedText: String? = null,
)

data class LineRange(val start: Int, val end: Int?)
