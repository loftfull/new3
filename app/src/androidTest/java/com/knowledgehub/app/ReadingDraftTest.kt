package com.knowledgehub.app

import androidx.compose.ui.test.assertTextContains
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.junit4.createEmptyComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollToNode
import androidx.compose.ui.test.performTextInput
import androidx.test.core.app.ActivityScenario
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.knowledgehub.core.CaptureInput
import com.knowledgehub.core.CaptureRepository
import kotlinx.coroutines.runBlocking
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class ReadingDraftTest {
    @get:Rule val compose = createEmptyComposeRule()

    @Test fun keepsDraftAfterReturningToSameMaterial() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val title = "Draft QA ${System.currentTimeMillis()}"
        val draft = "Unfinished note"
        runBlocking {
            CaptureRepository.shared(context).save(CaptureInput.Text("Saved QA material", title))
        }
        ActivityScenario.launch(MainActivity::class.java).use {
            compose.waitUntil(10_000) { compose.onAllNodesWithText(title).fetchSemanticsNodes().isNotEmpty() }
            compose.onNodeWithText(title).performClick()
            compose.onNodeWithTag("material-detail").performScrollToNode(hasTestTag("note-editor"))
            compose.onNodeWithTag("note-editor").performTextInput(draft)
            compose.onNodeWithContentDescription("Назад").performClick()
            compose.onNodeWithText(title).performClick()
            compose.onNodeWithTag("material-detail").performScrollToNode(hasTestTag("note-editor"))
            compose.onNodeWithTag("note-editor").assertTextContains(draft)
        }
    }
}
