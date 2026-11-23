package com.knowledgehub.app

import android.content.Intent
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createEmptyComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.test.core.app.ActivityScenario
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class CaptureFlowTest {
    @get:Rule val compose = createEmptyComposeRule()

    @Test
    fun sharedLinkSurvivesActivityRestart() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val title = "Проверка ссылки ${System.currentTimeMillis()}"
        val share = Intent(context, MainActivity::class.java).apply {
            action = Intent.ACTION_SEND
            type = "text/plain"
            putExtra(Intent.EXTRA_TEXT, "https://example.org/android")
            putExtra(Intent.EXTRA_SUBJECT, title)
        }
        ActivityScenario.launch<MainActivity>(share).use {
            compose.onNodeWithText("Проверьте перед сохранением").assertIsDisplayed()
            compose.onNodeWithText("Сохранить").performClick()
            waitForTitle(title)
        }
        ActivityScenario.launch(MainActivity::class.java).use {
            waitForTitle(title)
            compose.onNodeWithText(title).performClick()
            compose.onNodeWithText("https://example.org/android").assertIsDisplayed()
        }
    }

    private fun waitForTitle(title: String) {
        compose.waitUntil(10_000) {
            compose.onAllNodesWithText(title).fetchSemanticsNodes().isNotEmpty()
        }
        compose.onNodeWithText(title).assertIsDisplayed()
    }
}
