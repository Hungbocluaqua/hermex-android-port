package com.uzairansar.hermex.ui.chat

import android.animation.ValueAnimator
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.text.Spannable
import android.text.SpannableStringBuilder
import android.text.Spanned
import android.text.method.LinkMovementMethod
import android.text.style.ForegroundColorSpan
import android.view.View
import android.widget.TextView
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
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.text
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import com.uzairansar.hermex.ui.localization.localizedString
import io.noties.markwon.AbstractMarkwonPlugin
import io.noties.markwon.LinkResolver
import io.noties.markwon.Markwon
import io.noties.markwon.MarkwonConfiguration
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
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import java.util.LinkedHashMap

@Composable
fun MarkdownText(
    markdown: String,
    modifier: Modifier = Modifier,
    wrapsCodeBlockLines: Boolean = true,
    isStreaming: Boolean = false,
    streamedTextAnimationEnabled: Boolean = false,
) {
    var usesStreamingRenderer by remember { mutableStateOf(isStreaming) }
    LaunchedEffect(isStreaming) {
        if (isStreaming) {
            usesStreamingRenderer = true
        } else if (usesStreamingRenderer) {
            delay(STREAMING_SUFFIX_FADE_DURATION_MILLIS)
            usesStreamingRenderer = false
        }
    }
    if (usesStreamingRenderer) {
        StreamingStructuredMarkdown(
            markdown = markdown,
            modifier = modifier,
            wrapsCodeBlockLines = wrapsCodeBlockLines,
            streamedTextAnimationEnabled = streamedTextAnimationEnabled,
        )
        return
    }
    StructuredMarkdownText(
        markdown = markdown,
        modifier = modifier,
        wrapsCodeBlockLines = wrapsCodeBlockLines,
        isStreaming = false,
        streamedTextAnimationEnabled = streamedTextAnimationEnabled,
    )
}

@Composable
private fun StructuredMarkdownText(
    markdown: String,
    modifier: Modifier = Modifier,
    wrapsCodeBlockLines: Boolean = true,
    isStreaming: Boolean,
    streamedTextAnimationEnabled: Boolean,
) {
    val plainTextChunks = remember(markdown) { markdownPlainTextChunksForLargeContent(markdown) }
    if (plainTextChunks != null) {
        SelectionContainer {
            Column(modifier = modifier) {
                plainTextChunks.forEach { chunk ->
                    Text(
                        text = chunk,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurface,
                    )
                }
            }
        }
        return
    }
    var parsedSegments by remember { mutableStateOf<List<MarkdownSegment>?>(null) }
    LaunchedEffect(markdown) {
        parsedSegments = try {
            withContext(Dispatchers.Default) { markdown.parseMarkdownSegments() }
        } catch (error: CancellationException) {
            throw error
        } catch (_: Exception) {
            listOf(MarkdownSegment.Markdown(markdown))
        }
    }
    val segments = parsedSegments ?: listOf(MarkdownSegment.Markdown(markdown))
    if (segments.size == 1 && segments.single() is MarkdownSegment.Markdown) {
        MarkdownAndroidView(
            markdown = (segments.single() as MarkdownSegment.Markdown).text,
            modifier = modifier,
            isStreaming = isStreaming,
            streamedTextAnimationEnabled = streamedTextAnimationEnabled,
        )
        return
    }
    Column(
        modifier = modifier,
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        segments.forEach { segment ->
            when (segment) {
                is MarkdownSegment.Markdown -> {
                    if (segment.text.isNotBlank()) {
                        MarkdownAndroidView(
                            markdown = segment.text,
                            isStreaming = isStreaming,
                            streamedTextAnimationEnabled = streamedTextAnimationEnabled,
                        )
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
private fun StreamingStructuredMarkdown(
    markdown: String,
    modifier: Modifier,
    wrapsCodeBlockLines: Boolean,
    streamedTextAnimationEnabled: Boolean,
) {
    val latestMarkdown by rememberUpdatedState(markdown)
    var renderedMarkdown by remember { mutableStateOf(markdown) }
    LaunchedEffect(Unit) {
        while (true) {
            if (renderedMarkdown != latestMarkdown) renderedMarkdown = latestMarkdown
            delay(STREAM_RENDER_INTERVAL_MILLIS)
        }
    }
    val segments = remember(renderedMarkdown) { StreamingMarkdownBlockSplitter.split(renderedMarkdown) }
    Column(modifier = modifier) {
        segments.stableChunks.forEach { chunk ->
            key(chunk.id) {
                StructuredMarkdownText(
                    markdown = chunk.text,
                    wrapsCodeBlockLines = wrapsCodeBlockLines,
                    isStreaming = false,
                    streamedTextAnimationEnabled = false,
                )
            }
        }
        if (segments.activeMarkdown.isNotEmpty()) {
            StructuredMarkdownText(
                markdown = segments.activeMarkdown,
                wrapsCodeBlockLines = wrapsCodeBlockLines,
                isStreaming = true,
                streamedTextAnimationEnabled = streamedTextAnimationEnabled,
            )
        }
    }
}

private const val STREAM_RENDER_INTERVAL_MILLIS = 100L
private const val STREAMING_SUFFIX_FADE_DURATION_MILLIS = 220L
private const val STREAMING_SUFFIX_START_ALPHA = 72
private const val MAX_STRUCTURED_MARKDOWN_CHARACTERS = 80_000
private const val PLAIN_TEXT_CHUNK_CHARACTERS = 16_000

internal fun markdownPlainTextChunksForLargeContent(markdown: String): List<String>? =
    if (markdown.length > MAX_STRUCTURED_MARKDOWN_CHARACTERS) {
        markdownPlainTextChunks(markdown, PLAIN_TEXT_CHUNK_CHARACTERS)
    } else {
        null
    }

internal fun markdownPlainTextChunks(markdown: String, maximumCharacters: Int): List<String> {
    if (markdown.isEmpty()) return listOf("")
    val chunkSize = maximumCharacters.coerceAtLeast(1)
    val ranges = ArrayList<IntRange>((markdown.length / chunkSize) + 1)
    var start = 0
    while (start < markdown.length) {
        var end = (start + chunkSize).coerceAtMost(markdown.length)
        if (end < markdown.length && markdown[end - 1].isHighSurrogate() && markdown[end].isLowSurrogate()) {
            end -= 1
        }
        if (end == start) end = (start + 2).coerceAtMost(markdown.length)
        ranges += start until end
        start = end
    }
    return object : AbstractList<String>() {
        override val size: Int = ranges.size
        override fun get(index: Int): String {
            val range = ranges[index]
            return markdown.substring(range.first, range.last + 1)
        }
    }
}

@Composable
private fun MarkdownAndroidView(
    markdown: String,
    modifier: Modifier = Modifier,
    isStreaming: Boolean = false,
    streamedTextAnimationEnabled: Boolean = false,
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
        try {
            MarkdownRendererCache.get(
                context = context.applicationContext,
                textColor = textColor,
                codeBackground = codeBackground,
                dividerColor = dividerColor,
                isDarkTheme = isDarkTheme,
                latexTextSizePx = latexTextSizePx,
            )
        } catch (_: Exception) {
            null
        }
    }
    var renderState by remember(markwon) { mutableStateOf<MarkdownRenderState>(MarkdownRenderState.Pending) }
    LaunchedEffect(markwon, markdown) {
        if (markwon == null) {
            renderState = MarkdownRenderState.Failed
            return@LaunchedEffect
        }
        renderState = try {
            MarkdownRenderState.Rendered(withContext(Dispatchers.Default) { markwon.toMarkdown(markdown) })
        } catch (error: CancellationException) {
            throw error
        } catch (_: Exception) {
            MarkdownRenderState.Failed
        }
    }
    val parsedMarkdown = (renderState as? MarkdownRenderState.Rendered)?.markdown
    if (parsedMarkdown == null) {
        SelectionContainer {
            Text(
                text = markdown,
                modifier = modifier,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurface,
            )
        }
        return
    }
    val renderer = markwon ?: return
    AndroidView(
        modifier = modifier.semantics {
            text = AnnotatedString(markdown)
        },
        factory = {
            TextView(it).apply {
                textSize = 15f
                setTextColor(textColor)
                setLinkTextColor(linkColor)
                setTextIsSelectable(true)
                movementMethod = LinkMovementMethod.getInstance()
                tag = StreamingMarkdownViewState()
            }
        },
        update = { textView ->
            textView.setTextColor(textColor)
            textView.setLinkTextColor(linkColor)
            textView.setHorizontallyScrolling(false)
            textView.isHorizontalScrollBarEnabled = false
            parsedMarkdown.let { parsed ->
                val viewState = textView.tag as? StreamingMarkdownViewState
                    ?: StreamingMarkdownViewState().also { textView.tag = it }
                val nextText = parsed.toString()
                if (viewState.renderer === renderer && viewState.renderedText == nextText) return@let
                viewState.animator?.cancel()
                val suffixStart = streamingSuffixStart(viewState.previousText, nextText)
                val spannable = SpannableStringBuilder(parsed)
                try {
                    renderer.setParsedMarkdown(textView, spannable)
                } catch (_: Exception) {
                    textView.text = markdown
                    viewState.previousText = markdown
                    viewState.renderedText = markdown
                    viewState.renderer = null
                    return@let
                }
                val displayedText = textView.text as? Spannable ?: spannable
                if (
                    isStreaming &&
                    streamedTextAnimationEnabled &&
                    ValueAnimator.areAnimatorsEnabled() &&
                    suffixStart < displayedText.length
                ) {
                    animateStreamingSuffix(textView, displayedText, suffixStart, textColor, viewState)
                }
                viewState.previousText = nextText
                viewState.renderedText = nextText
                viewState.renderer = renderer
            }
        },
    )
}

private sealed interface MarkdownRenderState {
    data object Pending : MarkdownRenderState
    data object Failed : MarkdownRenderState
    data class Rendered(val markdown: Spanned) : MarkdownRenderState
}

internal fun streamingSuffixStart(previous: String, current: String): Int {
    val limit = minOf(previous.length, current.length)
    var index = 0
    while (index < limit && previous[index] == current[index]) index += 1
    if (
        index in 1 until current.length &&
        current[index - 1].isHighSurrogate() &&
        current[index].isLowSurrogate()
    ) {
        index -= 1
    }
    return index
}

private fun animateStreamingSuffix(
    textView: TextView,
    text: Spannable,
    start: Int,
    baseColor: Int,
    state: StreamingMarkdownViewState,
) {
    var span: ForegroundColorSpan? = null
    val animator = ValueAnimator.ofInt(STREAMING_SUFFIX_START_ALPHA, 255).apply {
        duration = STREAMING_SUFFIX_FADE_DURATION_MILLIS
        addUpdateListener { animation ->
            span?.let(text::removeSpan)
            val alpha = animation.animatedValue as Int
            span = ForegroundColorSpan((baseColor and 0x00FFFFFF) or (alpha shl 24)).also { colorSpan ->
                text.setSpan(colorSpan, start, text.length, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
            }
            textView.invalidate()
        }
        addListener(object : android.animation.AnimatorListenerAdapter() {
            private var finished = false

            private fun finish(animation: android.animation.Animator) {
                if (finished) return
                finished = true
                span?.let(text::removeSpan)
                textView.invalidate()
                if (state.animator === animation) state.animator = null
            }

            override fun onAnimationEnd(animation: android.animation.Animator) = finish(animation)
            override fun onAnimationCancel(animation: android.animation.Animator) = finish(animation)
        })
    }
    state.animator = animator
    animator.start()
}

private data class StreamingMarkdownViewState(
    var previousText: String = "",
    var renderedText: String = "",
    var renderer: Markwon? = null,
    var animator: ValueAnimator? = null,
)

private object MarkdownRendererCache {
    private const val MAX_RENDERERS = 6
    private val renderers = object : LinkedHashMap<RendererKey, Markwon>(MAX_RENDERERS, 0.75f, true) {
        override fun removeEldestEntry(eldest: MutableMap.MutableEntry<RendererKey, Markwon>?): Boolean =
            size > MAX_RENDERERS
    }

    @Synchronized
    fun get(
        context: Context,
        textColor: Int,
        codeBackground: Int,
        dividerColor: Int,
        isDarkTheme: Boolean,
        latexTextSizePx: Float,
    ): Markwon {
        val key = RendererKey(
            context = context,
            textColor = textColor,
            codeBackground = codeBackground,
            dividerColor = dividerColor,
            isDarkTheme = isDarkTheme,
            latexTextSizeBits = latexTextSizePx.toBits(),
        )
        return renderers.getOrPut(key) {
            val prismTheme = if (isDarkTheme) Prism4jThemeDarkula.create() else Prism4jThemeDefault.create()
            Markwon.builder(context)
                .usePlugin(CorePlugin.create())
                .usePlugin(StrikethroughPlugin.create())
                .usePlugin(TablePlugin.create(context))
                .usePlugin(TaskListPlugin.create(context))
                .usePlugin(HtmlPlugin.create())
                .usePlugin(LinkifyPlugin.create())
                .usePlugin(SyntaxHighlightPlugin.create(Prism4j(HermexGrammarLocator()), prismTheme))
                .usePlugin(JLatexMathPlugin.create(latexTextSizePx) { builder -> builder.inlinesEnabled(true) })
                .usePlugin(MarkwonInlineParserPlugin.create())
                .usePlugin(
                    object : AbstractMarkwonPlugin() {
                        override fun configureConfiguration(builder: MarkwonConfiguration.Builder) {
                            builder.linkResolver(SafeWebLinkResolver)
                        }

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
    }

    private data class RendererKey(
        val context: Context,
        val textColor: Int,
        val codeBackground: Int,
        val dividerColor: Int,
        val isDarkTheme: Boolean,
        val latexTextSizeBits: Int,
    )
}

private object SafeWebLinkResolver : LinkResolver {
    override fun resolve(view: View, link: String) {
        val uri = runCatching { Uri.parse(link) }.getOrNull() ?: return
        if (uri.scheme?.lowercase() !in setOf("http", "https") || uri.host.isNullOrBlank()) return
        runCatching { view.context.startActivity(Intent(Intent.ACTION_VIEW, uri)) }
    }
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
                    Text(localizedString("Wrap"))
                }
                TextButton(
                    onClick = {
                        val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                        clipboard.setPrimaryClip(ClipData.newPlainText("Hermex code block", content))
                        copied = true
                    },
                ) {
                    Text(localizedString(if (copied) "Copied" else "Copy"))
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
