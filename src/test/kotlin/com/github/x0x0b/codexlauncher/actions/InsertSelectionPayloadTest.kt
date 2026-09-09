package com.github.x0x0b.codexlauncher.actions

import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.command.WriteCommandAction
import com.intellij.testFramework.LightProjectDescriptor
import com.intellij.testFramework.fixtures.CodeInsightTestFixture
import com.intellij.testFramework.fixtures.IdeaTestFixtureFactory
import com.intellij.testFramework.fixtures.impl.LightTempDirTestFixtureImpl
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Test

class InsertSelectionPayloadTest {
    private lateinit var fixture: CodeInsightTestFixture

    @Before
    fun setUp() {
        val factory = IdeaTestFixtureFactory.getFixtureFactory()
        val builder = factory.createLightFixtureBuilder(
            LightProjectDescriptor.EMPTY_PROJECT_DESCRIPTOR,
            "InsertSelectionPayloadTest",
        )
        fixture = factory.createCodeInsightFixture(builder.fixture, LightTempDirTestFixtureImpl(true))
        fixture.setUp()
    }

    @After
    fun tearDown() = onEdt {
        if (::fixture.isInitialized) fixture.tearDown()
    }

    @Test
    fun resolve_partialSingleLine_includesOnlySelectedText() = onEdt {
        fixture.configureByText("Foo.cs", "before <selection>value</selection> after")
        assertSelection("value", LineRange(1, null), ": 1 value\n")
    }

    @Test
    fun resolve_multilineSelection_preservesIndentationAndSpecialCharacters() = onEdt {
        fixture.configureByText("Foo.cs", "header\n  <selection>var 名称 = \"<>&\";\n\t  next(); </selection>tail")
        val selected = "var 名称 = \"<>&\";\n\t  next(); "
        assertSelection(selected, LineRange(2, 3), ": 2-3 $selected\n")
    }

    @Test
    fun resolve_whitespaceSelection_preservesWhitespace() = onEdt {
        fixture.configureByText("Foo.cs", "x<selection> \t </selection>y")
        assertSelection(" \t ", LineRange(1, null), ": 1  \t \n")
    }

    @Test
    fun resolve_selectionEndingAtNextLineStart_excludesNextLine() = onEdt {
        fixture.configureByText("Foo.cs", "<selection>first\n</selection>second")
        assertSelection("first\n", LineRange(1, null), ": 1 first\n\n")
    }

    @Test
    fun resolve_selectionEndingAtFileTrailingNewline_excludesEmptyLastLine() = onEdt {
        fixture.configureByText("Foo.cs", "<selection>first\nsecond\n</selection>")
        assertSelection("first\nsecond\n", LineRange(1, 2), ": 1-2 first\nsecond\n\n")
    }

    @Test
    fun resolve_selectionEndingAtFileEnd_includesLastCharacter() = onEdt {
        fixture.configureByText("Foo.cs", "first\n<selection>last</selection>")
        assertSelection("last", LineRange(2, null), ": 2 last\n")
    }

    @Test
    fun resolve_unsavedSelection_usesCurrentDocumentText() = onEdt {
        fixture.configureByText("Foo.cs", "original")
        WriteCommandAction.runWriteCommandAction(fixture.project) {
            fixture.editor.document.setText("unsaved\n  edited")
        }
        fixture.editor.selectionModel.setSelection(0, fixture.editor.document.textLength)
        assertSelection("unsaved\n  edited", LineRange(1, 2), ": 1-2 unsaved\n  edited\n")
    }

    @Test
    fun resolve_noSelection_insertsOnlyPathAndSpace() = onEdt {
        fixture.configureByText("Foo.cs", "text")
        val payload = resolve()
        assertNull(payload.selectedText)
        assertNull(payload.lineRange)
        assertEquals(payload.relativePath + " ", InsertPayloadResolver.formatInsertText(payload))
    }

    private fun assertSelection(text: String, range: LineRange, formattedSuffix: String) {
        val payload = resolve()
        assertEquals(text, payload.selectedText)
        assertEquals(range, payload.lineRange)
        assertEquals(payload.relativePath + formattedSuffix, InsertPayloadResolver.formatInsertText(payload))
    }

    private fun resolve(): InsertPayload = requireNotNull(
        InsertPayloadResolver.resolve(fixture.project, fixture.editor, fixture.file.virtualFile),
    )

    private fun onEdt(block: () -> Unit) {
        val application = ApplicationManager.getApplication()
        if (application.isDispatchThread) block() else application.invokeAndWait(block)
    }
}
