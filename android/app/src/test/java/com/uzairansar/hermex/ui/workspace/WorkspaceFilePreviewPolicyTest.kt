package com.uzairansar.hermex.ui.workspace

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class WorkspaceFilePreviewPolicyTest {
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
}
