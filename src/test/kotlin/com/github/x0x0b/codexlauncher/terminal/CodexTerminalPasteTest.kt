package com.github.x0x0b.codexlauncher.terminal

import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test

class CodexTerminalPasteTest {
    @Test
    fun write_multilinePayload_sendsOnePasteWithoutSubmitOutsideBoundaries() {
        val writes = mutableListOf<String>()
        val payload = "src/Foo.cs: 2-3 var 名称 = \"<>&\";\n\t next();\n"

        CodexTerminalPaste.write(payload) { writes.add(it) }

        assertEquals(listOf("\u001b[200~${payload}\u001b[201~"), writes)
    }

    @Test
    fun write_embeddedPasteTerminator_rejectsBeforeWriting() {
        val writes = mutableListOf<String>()

        assertThrows(IllegalArgumentException::class.java) {
            CodexTerminalPaste.write("text\u001b[201~\n") { writes.add(it) }
        }

        assertTrue(writes.isEmpty())
    }

    @Test
    fun write_connectorFailure_doesNotRetryOrSubmit() {
        var attempts = 0

        assertThrows(IllegalStateException::class.java) {
            CodexTerminalPaste.write("file.cs: 1 selected\n") {
                attempts++
                throw IllegalStateException("Disconnected")
            }
        }

        assertEquals(1, attempts)
    }
}
