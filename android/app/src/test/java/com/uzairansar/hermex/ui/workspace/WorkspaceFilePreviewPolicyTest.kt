package com.uzairansar.hermex.ui.workspace

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class WorkspaceFilePreviewPolicyTest {
    @Test
    fun recognizesIosMarkdownPreviewExtensions() {
        assertTrue(WorkspaceFilePreviewPolicy.isMarkdown("README.md"))
        assertTrue(WorkspaceFilePreviewPolicy.isMarkdown("guide.MARKDOWN"))
        assertTrue(WorkspaceFilePreviewPolicy.isMarkdown("notes.mdown"))
        assertTrue(WorkspaceFilePreviewPolicy.isMarkdown("draft.mkd"))
        assertFalse(WorkspaceFilePreviewPolicy.isMarkdown("notes.txt"))
    }

    @Test
    fun classifiesRasterImagesLikeIosPreview() {
        assertTrue(WorkspaceFilePreviewPolicy.isRasterImage("/repo/image.PNG"))
        assertTrue(WorkspaceFilePreviewPolicy.isRasterImage("icon.ico"))
        assertFalse(WorkspaceFilePreviewPolicy.isRasterImage("diagram.svg"))
    }

    @Test
    fun classifiesKnownUnsupportedBinariesLikeIosPreview() {
        assertTrue(WorkspaceFilePreviewPolicy.isKnownUnsupportedBinary("/repo/report.pdf"))
        assertTrue(WorkspaceFilePreviewPolicy.isKnownUnsupportedBinary("archive.zip"))
        assertTrue(WorkspaceFilePreviewPolicy.isKnownUnsupportedBinary("diagram.svg"))
        assertFalse(WorkspaceFilePreviewPolicy.isKnownUnsupportedBinary("README.md"))
    }

    @Test
    fun onlyLoadsRawBytesForRenderableBinaryPreviews() {
        assertTrue(WorkspaceFilePreviewPolicy.shouldLoadRawPreview("image.png"))
        assertFalse(WorkspaceFilePreviewPolicy.shouldLoadRawPreview("archive.zip"))
        assertFalse(WorkspaceFilePreviewPolicy.shouldLoadRawPreview("report.pdf"))
    }

    @Test
    fun resolvesMimeTypesForExport() {
        assertEquals("text/plain", WorkspaceFilePreviewPolicy.mimeType("README.md", isText = true))
        assertEquals("image/png", WorkspaceFilePreviewPolicy.mimeType("image.png"))
        assertEquals("application/pdf", WorkspaceFilePreviewPolicy.mimeType("report.pdf"))
        assertEquals("application/octet-stream", WorkspaceFilePreviewPolicy.mimeType("unknown.custom"))
    }

    @Test
    fun resolvesDisplayNameAndLineCount() {
        assertEquals("file.txt", WorkspaceFilePreviewPolicy.displayName("/tmp/workspace/file.txt"))
        assertEquals("Hermex File", WorkspaceFilePreviewPolicy.displayName("  "))
        assertEquals(0, WorkspaceFilePreviewPolicy.lineCount(""))
        assertEquals(2, WorkspaceFilePreviewPolicy.lineCount("one\ntwo"))
        assertEquals(null, WorkspaceFilePreviewPolicy.lineCount(null))
    }

    @Test
    fun treatsEmptyTextContentAsAValidPreview() {
        assertTrue(shouldRenderWorkspaceTextPreview(""))
        assertTrue(shouldRenderWorkspaceTextPreview("content"))
        assertFalse(shouldRenderWorkspaceTextPreview(null))
        assertFalse(shouldRenderWorkspaceTextPreview("header\u0000binary"))
        assertFalse(shouldRenderWorkspaceTextPreview("\uFFFD".repeat(20)))
    }

    @Test
    fun fileBadgesAndKindLabelsReflectTheActualFileType() {
        assertEquals("DIR", WorkspaceFilePreviewPolicy.badgeLabel("/repo/src", isDirectory = true))
        assertEquals("JSON", WorkspaceFilePreviewPolicy.badgeLabel("settings.json"))
        assertEquals("IMG", WorkspaceFilePreviewPolicy.badgeLabel("photo.webp"))
        assertEquals("CODE", WorkspaceFilePreviewPolicy.badgeLabel("MainActivity.kt"))
        assertEquals("Audio", WorkspaceFilePreviewPolicy.kindLabel("voice.m4a"))
        assertEquals("Source code", WorkspaceFilePreviewPolicy.kindLabel("main.swift"))
    }

    @Test
    fun fileSizesUseReadableUnits() {
        assertEquals("999 bytes", fileSizeText(999))
        assertEquals("1.5 KB", fileSizeText(1_500))
        assertEquals("2.5 MB", fileSizeText(2_500_000))
    }
}
