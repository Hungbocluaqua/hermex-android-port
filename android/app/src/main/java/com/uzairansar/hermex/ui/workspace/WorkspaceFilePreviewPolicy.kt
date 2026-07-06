package com.uzairansar.hermex.ui.workspace

object WorkspaceFilePreviewPolicy {
    private val rasterImageExtensions = setOf("png", "jpg", "jpeg", "gif", "webp", "ico", "bmp")
    private val unsupportedBinaryExtensions = setOf(
        "7z",
        "a",
        "aiff",
        "avi",
        "bin",
        "bz2",
        "class",
        "db",
        "dmg",
        "doc",
        "docx",
        "dylib",
        "exe",
        "flac",
        "gz",
        "jar",
        "m4a",
        "mov",
        "mp3",
        "mp4",
        "o",
        "pdf",
        "pkg",
        "ppt",
        "pptx",
        "pyc",
        "rar",
        "sqlite",
        "svg",
        "tar",
        "tgz",
        "wav",
        "xls",
        "xlsx",
        "xz",
        "zip",
    )

    fun extension(path: String?): String =
        path.orEmpty()
            .substringAfterLast('/')
            .substringAfterLast('\\')
            .substringAfterLast('.', missingDelimiterValue = "")
            .lowercase()

    fun displayName(path: String?): String =
        path.orEmpty()
            .trim()
            .trimEnd('/', '\\')
            .substringAfterLast('/')
            .substringAfterLast('\\')
            .ifBlank { "Hermex File" }

    fun isRasterImage(path: String?): Boolean = extension(path) in rasterImageExtensions

    fun isKnownUnsupportedBinary(path: String?): Boolean = extension(path) in unsupportedBinaryExtensions

    fun mimeType(path: String?, isText: Boolean = false): String =
        if (isText) {
            "text/plain"
        } else {
            when (extension(path)) {
                "png" -> "image/png"
                "jpg", "jpeg" -> "image/jpeg"
                "gif" -> "image/gif"
                "webp" -> "image/webp"
                "bmp" -> "image/bmp"
                "ico" -> "image/x-icon"
                "pdf" -> "application/pdf"
                "svg" -> "image/svg+xml"
                "zip" -> "application/zip"
                "json" -> "application/json"
                "md" -> "text/markdown"
                "html", "htm" -> "text/html"
                "css" -> "text/css"
                "js", "mjs", "cjs" -> "text/javascript"
                else -> "application/octet-stream"
            }
        }

    fun lineCount(content: String?): Int? {
        val text = content ?: return null
        if (text.isEmpty()) return 0
        return text.lineSequence().count()
    }
}
