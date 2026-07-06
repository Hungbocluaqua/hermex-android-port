package com.uzairansar.hermex.core.model

enum class SessionExportFormat(
    val wireValue: String,
    val fileExtension: String,
    val mimeType: String,
) {
    Html("html", "html", "text/html"),
    Json("json", "json", "application/json"),
}

data class SessionExportFile(
    val data: ByteArray,
    val filename: String,
    val mimeType: String,
) {
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is SessionExportFile) return false
        return filename == other.filename &&
            mimeType == other.mimeType &&
            data.contentEquals(other.data)
    }

    override fun hashCode(): Int {
        var result = data.contentHashCode()
        result = 31 * result + filename.hashCode()
        result = 31 * result + mimeType.hashCode()
        return result
    }
}
