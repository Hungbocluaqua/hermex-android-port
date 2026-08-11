package com.uzairansar.hermex

import android.text.Spanned
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.uzairansar.hermex.ui.chat.MarkdownText
import com.uzairansar.hermex.ui.theme.HermexTheme
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicReference

@RunWith(AndroidJUnit4::class)
class StreamingMarkdownInstrumentedTest {
    @get:Rule
    val composeRule = createComposeRule()

    @Test
    fun streamingMarkdownUsesStructuredSpansBeforeTheStreamCompletes() {
        val rootView = AtomicReference<View>()
        composeRule.setContent {
            rootView.set(LocalView.current)
            HermexTheme {
                MarkdownText(
                    markdown = "## Streaming heading\n\n- first item\n- second item",
                    isStreaming = true,
                )
            }
        }

        val renderedStructuredText = AtomicBoolean(false)
        val renderedSelectableText = AtomicBoolean(false)
        composeRule.waitUntil(timeoutMillis = 5_000) {
            InstrumentationRegistry.getInstrumentation().runOnMainSync {
                val textView = rootView.get()?.descendantTextViews()?.firstOrNull { view ->
                    view.text.toString().contains("Streaming heading")
                }
                val text = textView?.text as? Spanned
                renderedSelectableText.set(textView?.isTextSelectable == true)
                renderedStructuredText.set(
                    text != null && text.getSpans(0, text.length, Any::class.java).isNotEmpty(),
                )
            }
            renderedStructuredText.get()
        }

        assertTrue(renderedStructuredText.get())
        assertTrue(renderedSelectableText.get())
    }

    private fun View.descendantTextViews(): List<TextView> = buildList {
        if (this@descendantTextViews is TextView) add(this@descendantTextViews)
        if (this@descendantTextViews is ViewGroup) {
            repeat(childCount) { index -> addAll(getChildAt(index).descendantTextViews()) }
        }
    }
}
