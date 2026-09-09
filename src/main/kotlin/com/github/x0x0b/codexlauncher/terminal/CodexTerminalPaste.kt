package com.github.x0x0b.codexlauncher.terminal

/** Sends one paste event, with all payload newlines inside the terminal paste boundaries. */
internal object CodexTerminalPaste {
    fun write(text: String, writeToTerminal: (String) -> Unit) {
        // An embedded terminator would turn the remaining text into ordinary terminal input.
        require(!text.contains("\u001b[201~")) { "Selection contains a terminal paste terminator" }
        writeToTerminal("\u001b[200~$text\u001b[201~")
    }
}
