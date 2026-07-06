package com.uzairansar.hermex.ui.chat

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.text.method.LinkMovementMethod
import android.widget.TextView
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.text
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
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
    wrapsCodeBlockLines: Boolean = true,
    isStreaming: Boolean = false,
    streamedTextAnimationEnabled: Boolean = false,
) {
    val fadeAlpha = remember { Animatable(1f) }
    LaunchedEffect(markdown, isStreaming, streamedTextAnimationEnabled) {
        if (isStreaming && streamedTextAnimationEnabled) {
            fadeAlpha.snapTo(0.72f)
            fadeAlpha.animateTo(1f, animationSpec = tween(durationMillis = 220))
        } else {
            fadeAlpha.snapTo(1f)
        }
    }
    val segments = remember(markdown) { markdown.parseMarkdownSegments() }
    val animatedModifier = modifier.graphicsLayer(alpha = fadeAlpha.value)
    if (segments.size == 1 && segments.single() is MarkdownSegment.Markdown) {
        MarkdownAndroidView(
            markdown = (segments.single() as MarkdownSegment.Markdown).text,
            modifier = animatedModifier,
        )
        return
    }
    Column(
        modifier = animatedModifier,
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        segments.forEach { segment ->
            when (segment) {
                is MarkdownSegment.Markdown -> {
                    if (segment.text.isNotBlank()) {
                        MarkdownAndroidView(markdown = segment.text)
                    }
                }
                is MarkdownSegment.CodeBlock -> ChatCodeBlock(
                    language = segment.language,
                    content = segment.content,
                    startsWrapped = wrapsCodeBlockLines,
                )
            }
        }
    }
}

@Composable
private fun MarkdownAndroidView(
    markdown: String,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
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
        modifier = modifier.clearAndSetSemantics {
            text = AnnotatedString(markdown)
        },
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
            textView.setHorizontallyScrolling(false)
            textView.isHorizontalScrollBarEnabled = false
            markwon.setMarkdown(textView, markdown)
        },
    )
}

@Composable
private fun ChatCodeBlock(
    language: String?,
    content: String,
    startsWrapped: Boolean,
) {
    val context = LocalContext.current
    var wraps by remember(content) { mutableStateOf(startsWrapped) }
    var copied by remember(content) { mutableStateOf(false) }
    val shape = RoundedCornerShape(10.dp)
    CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Ltr) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .clip(shape)
                .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.72f))
                .border(0.5.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.72f), shape),
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(start = 14.dp, end = 8.dp, top = 8.dp, bottom = 2.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    language?.takeIf { it.isNotBlank() } ?: "code",
                    modifier = Modifier.weight(1f),
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.secondary,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                TextButton(onClick = { wraps = !wraps }) {
                    Text(if (wraps) "Scroll" else "Wrap")
                }
                TextButton(
                    onClick = {
                        val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                        clipboard.setPrimaryClip(ClipData.newPlainText("Hermex code block", content))
                        copied = true
                    },
                ) {
                    Text(if (copied) "Copied" else "Copy")
                }
            }
            val codeModifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 14.dp, vertical = 12.dp)
            val scrollState = rememberScrollState()
            SelectionContainer {
                if (wraps) {
                    CodeText(content = content, modifier = codeModifier)
                } else {
                    Row(Modifier.horizontalScroll(scrollState)) {
                        CodeText(content = content, modifier = codeModifier)
                    }
                }
            }
        }
    }
}

@Composable
private fun CodeText(
    content: String,
    modifier: Modifier = Modifier,
) {
    Text(
        text = content.ifEmpty { " " },
        modifier = modifier,
        style = MaterialTheme.typography.bodySmall.copy(
            fontFamily = FontFamily.Monospace,
            lineHeight = 18.sp,
        ),
        color = MaterialTheme.colorScheme.onSurface,
    )
}

private sealed interface MarkdownSegment {
    data class Markdown(val text: String) : MarkdownSegment
    data class CodeBlock(val language: String?, val content: String) : MarkdownSegment
}

private fun String.parseMarkdownSegments(): List<MarkdownSegment> {
    val normalized = replace("\r\n", "\n").replace('\r', '\n')
    val lines = normalized.split('\n')
    val segments = mutableListOf<MarkdownSegment>()
    val markdown = StringBuilder()
    var inFence = false
    var fenceMarker = ""
    var fenceOpening = ""
    var language: String? = null
    val code = StringBuilder()

    fun flushMarkdown() {
        val text = markdown.toString().trim('\n')
        if (text.isNotBlank()) segments += MarkdownSegment.Markdown(text)
        markdown.clear()
    }

    fun appendLine(builder: StringBuilder, line: String) {
        if (builder.isNotEmpty()) builder.append('\n')
        builder.append(line)
    }

    lines.forEach { line ->
        val trimmedStart = line.trimStart()
        if (!inFence) {
            val marker = when {
                trimmedStart.startsWith("```") -> "```"
                trimmedStart.startsWith("~~~") -> "~~~"
                else -> null
            }
            if (marker == null) {
                appendLine(markdown, line)
            } else {
                flushMarkdown()
                inFence = true
                fenceMarker = marker
                fenceOpening = line
                language = trimmedStart.removePrefix(marker).trim().substringBefore(' ').ifBlank { null }
                code.clear()
            }
        } else if (trimmedStart.startsWith(fenceMarker)) {
            segments += MarkdownSegment.CodeBlock(language = language, content = code.toString())
            inFence = false
            fenceMarker = ""
            fenceOpening = ""
            language = null
            code.clear()
        } else {
            appendLine(code, line)
        }
    }
    if (inFence) {
        appendLine(markdown, fenceOpening)
        if (code.isNotEmpty()) appendLine(markdown, code.toString())
    }
    flushMarkdown()
    return segments.ifEmpty { listOf(MarkdownSegment.Markdown(normalized)) }
}
