package com.uzairansar.hermex.ui.chat

import android.text.method.LinkMovementMethod
import android.widget.TextView
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import io.noties.markwon.AbstractMarkwonPlugin
import io.noties.markwon.Markwon
import io.noties.markwon.core.CorePlugin
import io.noties.markwon.core.MarkwonTheme
import io.noties.markwon.ext.latex.JLatexMathPlugin
import io.noties.markwon.ext.strikethrough.StrikethroughPlugin
import io.noties.markwon.ext.tables.TablePlugin
import io.noties.markwon.ext.tasklist.TaskListPlugin
import io.noties.markwon.html.HtmlPlugin
import io.noties.markwon.inlineparser.MarkwonInlineParserPlugin
import io.noties.markwon.linkify.LinkifyPlugin
import io.noties.markwon.syntax.Prism4jThemeDarkula
import io.noties.markwon.syntax.Prism4jThemeDefault
import io.noties.markwon.syntax.SyntaxHighlightPlugin
import io.noties.prism4j.Prism4j

@Composable
fun MarkdownText(
    markdown: String,
    modifier: Modifier = Modifier,
) {
    val context = androidx.compose.ui.platform.LocalContext.current
    val colorScheme = MaterialTheme.colorScheme
    val textColor = colorScheme.onSurface.toArgb()
    val linkColor = colorScheme.primary.toArgb()
    val codeBackground = colorScheme.surfaceVariant.toArgb()
    val dividerColor = colorScheme.outlineVariant.toArgb()
    val isDarkTheme = colorScheme.background.luminance() < 0.5f
    val latexTextSizePx = with(LocalDensity.current) { 15.sp.toPx() }
    val markwon = remember(context, textColor, linkColor, codeBackground, dividerColor, isDarkTheme, latexTextSizePx) {
        val prism4j = Prism4j(HermexGrammarLocator())
        val prismTheme = if (isDarkTheme) {
            Prism4jThemeDarkula.create()
        } else {
            Prism4jThemeDefault.create()
        }
        Markwon.builder(context)
            .usePlugin(CorePlugin.create())
            .usePlugin(StrikethroughPlugin.create())
            .usePlugin(TablePlugin.create(context))
            .usePlugin(TaskListPlugin.create(context))
            .usePlugin(HtmlPlugin.create())
            .usePlugin(LinkifyPlugin.create())
            .usePlugin(SyntaxHighlightPlugin.create(prism4j, prismTheme))
            .usePlugin(JLatexMathPlugin.create(latexTextSizePx) { builder ->
                builder.inlinesEnabled(true)
            })
            .usePlugin(MarkwonInlineParserPlugin.create())
            .usePlugin(
                object : AbstractMarkwonPlugin() {
                    override fun configureTheme(builder: MarkwonTheme.Builder) {
                        builder
                            .codeTextColor(textColor)
                            .codeBackgroundColor(codeBackground)
                            .codeBlockTextColor(textColor)
                            .codeBlockBackgroundColor(codeBackground)
                            .blockMargin(16)
                            .headingBreakHeight(0)
                            .thematicBreakColor(dividerColor)
                    }
                },
            )
            .build()
    }
    AndroidView(
        modifier = modifier,
        factory = {
            TextView(it).apply {
                textSize = 15f
                setTextColor(textColor)
                setLinkTextColor(linkColor)
                movementMethod = LinkMovementMethod.getInstance()
            }
        },
        update = { textView ->
            textView.setTextColor(textColor)
            textView.setLinkTextColor(linkColor)
            markwon.setMarkdown(textView, markdown)
        },
    )
}
