package com.uzairansar.hermex.core.model

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class TranscriptMediaParserTest {
    @Test
    fun liftsMediaReferencesOutsideFencedCode() {
        val segments = TranscriptMediaParser.segments(
            """
            Before MEDIA:/tmp/chart.png.
            ```text
            MEDIA:/tmp/inside-code.png
            ```
            After MEDIA:https://example.com/plot.webp!
            """.trimIndent(),
        )

        val media = segments.filterIsInstance<TranscriptMediaSegment.Media>()
        assertEquals("/tmp/chart.png", media[0].reference.rawReference)
        assertEquals("https://example.com/plot.webp", media[1].reference.rawReference)
        assertEquals(2, media.size)
        assertTrue(segments.filterIsInstance<TranscriptMediaSegment.Text>().any { it.text.contains("MEDIA:/tmp/inside-code.png") })
    }

    @Test
    fun classifiesRasterCandidatesLikeIos() {
        assertTrue(TranscriptMediaReference("/tmp/image.jpeg").isRasterImageCandidate)
        assertTrue(TranscriptMediaReference("https://example.com/render").isRasterImageCandidate)
        assertFalse(TranscriptMediaReference("/tmp/archive.zip").isRasterImageCandidate)
        assertEquals("image.jpeg", TranscriptMediaReference("/tmp/image.jpeg").displayName)
    }

    @Test
    fun classifiesAudioVideoAndDownloadCandidatesLikeIos() {
        assertEquals(TranscriptMediaKind.Audio, TranscriptMediaReference("/tmp/voice.m4a").mediaKind)
        assertEquals(TranscriptMediaKind.Video, TranscriptMediaReference("/tmp/demo.mp4").mediaKind)
        assertEquals(TranscriptMediaKind.Unsupported, TranscriptMediaReference("/tmp/archive.zip").mediaKind)
        assertTrue(TranscriptMediaReference("https://example.com/render").isExtensionlessRemoteMediaCandidate)
    }

    @Test
    fun sniffsExtensionlessRemoteMediaAndExportExtensions() {
        val reference = TranscriptMediaReference("https://example.com/render")
        val wave = "RIFF0000WAVEfmt ".encodeToByteArray()
        val mp4 = byteArrayOf(0, 0, 0, 20) + "ftypisom".encodeToByteArray()
        val png = byteArrayOf(0x89.toByte(), 0x50, 0x4E, 0x47, 0x0D, 0x0A)

        assertEquals(TranscriptMediaKind.Audio, TranscriptMediaDataClassifier.resolvedKind(reference, wave))
        assertEquals("wav", TranscriptMediaDataClassifier.suggestedExtension(reference, wave))
        assertEquals(TranscriptMediaKind.Video, TranscriptMediaDataClassifier.resolvedKind(reference, mp4))
        assertEquals("mp4", TranscriptMediaDataClassifier.suggestedExtension(reference, mp4))
        assertEquals(TranscriptMediaKind.Image, TranscriptMediaDataClassifier.resolvedKind(reference, png))
        assertEquals("png", TranscriptMediaDataClassifier.suggestedExtension(reference, png))
    }

    @Test
    fun shorterOrInfoBearingFenceDoesNotCloseACommonMarkCodeBlock() {
        val segments = TranscriptMediaParser.segments(
            """
            ````text
            ```
            MEDIA:/tmp/still-code.png
            ```` not-a-close
            MEDIA:/tmp/also-code.png
            ````
            MEDIA:/tmp/outside.png
            """.trimIndent(),
        )

        val media = segments.filterIsInstance<TranscriptMediaSegment.Media>()
        assertEquals(listOf("/tmp/outside.png"), media.map { it.reference.rawReference })
    }

    @Test
    fun stripsClosingMarkdownEmphasisFromMediaReferences() {
        val segments = TranscriptMediaParser.segments(
            "**MEDIA:/tmp/bold.png** and _MEDIA:/tmp/italic.mp4_",
        )

        assertEquals(
            listOf("/tmp/bold.png", "/tmp/italic.mp4"),
            segments.filterIsInstance<TranscriptMediaSegment.Media>().map { it.reference.rawReference },
        )
    }

    @Test
    fun parsesBareFileUrlsAtLineStartOrAfterWhitespace() {
        val segments = TranscriptMediaParser.segments(
            "file:///tmp/report.csv ready\nImage file:///tmp/chart.png.",
        )

        assertEquals(
            listOf(
                TranscriptMediaSegment.Media(TranscriptMediaReference("/tmp/report.csv")),
                TranscriptMediaSegment.Text(" ready\nImage "),
                TranscriptMediaSegment.Media(TranscriptMediaReference("/tmp/chart.png")),
                TranscriptMediaSegment.Text("."),
            ),
            segments,
        )
    }

    @Test
    fun bareFileUrlDecodesPercentEscapesAndKeepsExportName() {
        val references = TranscriptMediaParser.segments(
            "Created file:///Users/hermes/workspace/Q3%20report%20%28final%29.csv",
        ).filterIsInstance<TranscriptMediaSegment.Media>().map { it.reference }

        assertEquals(listOf("/Users/hermes/workspace/Q3 report (final).csv"), references.map { it.rawReference })
        assertEquals("Q3 report (final).csv", references.single().displayName)
    }

    @Test
    fun bareFileUrlKeepsProsePunctuationAndExistingKindClassification() {
        val segments = TranscriptMediaParser.segments(
            "Created file:///tmp/report.csv, then file:///tmp/image.png file:///tmp/audio.m4a file:///tmp/video.mp4 file:///tmp/data.zip!",
        )
        val references = segments.filterIsInstance<TranscriptMediaSegment.Media>().map { it.reference }

        assertEquals(
            listOf(
                TranscriptMediaKind.Unsupported,
                TranscriptMediaKind.Image,
                TranscriptMediaKind.Audio,
                TranscriptMediaKind.Video,
                TranscriptMediaKind.Unsupported,
            ),
            references.map { it.mediaKind },
        )
        assertTrue(segments.filterIsInstance<TranscriptMediaSegment.Text>().joinToString("") { it.text }.endsWith("!"))
    }

    @Test
    fun bareFileUrlRequiresWhitespaceAndStaysLiteralInsideCode() {
        val markdown = """
            prefixfile:///tmp/hidden.txt and [report](file:///tmp/report.csv)
            Before `open file:///tmp/inline.csv` after file:///tmp/outside.csv
            ```text
            file:///tmp/fenced.png
            ```
        """.trimIndent()
        val segments = TranscriptMediaParser.segments(markdown)

        assertEquals(
            listOf("/tmp/outside.csv"),
            segments.filterIsInstance<TranscriptMediaSegment.Media>().map { it.reference.rawReference },
        )
        val text = segments.filterIsInstance<TranscriptMediaSegment.Text>().joinToString("") { it.text }
        assertTrue(text.contains("prefixfile:///tmp/hidden.txt"))
        assertTrue(text.contains("[report](file:///tmp/report.csv)"))
        assertTrue(text.contains("file:///tmp/inline.csv"))
        assertTrue(text.contains("file:///tmp/fenced.png"))
    }
}
