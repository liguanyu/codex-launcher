package com.github.x0x0b.codexlauncher.terminal

/** Distinguishes absent terminals from input failures when routing a toolbar left-click. */
enum class TerminalInputState(val message: String?) {
    READY(null),
    NO_VISIBLE_TERMINAL("Open and select a terminal first to send file context"),
    NOT_READY("The current terminal is not ready yet; wait for its shell to start and try again"),
    TERMINATED("The current terminal session has ended; open a new session to send file context"),
    UNSUPPORTED("Unable to identify the current terminal; no text was sent and no Codex terminal was opened"),
}

/** Accepted means the terminal input API accepted the text, not that the shell processed it. */
sealed interface TerminalInsertResult {
    data object Accepted : TerminalInsertResult
    data class Failed(val message: String) : TerminalInsertResult
}
