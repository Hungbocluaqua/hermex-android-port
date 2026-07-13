package com.uzairansar.hermex.ui

import android.content.Context
import java.io.File
import java.util.UUID

internal fun Context.createExportDirectory(prefix: String): File {
    val root = File(cacheDir, "exports")
    check(root.mkdirs() || root.isDirectory) { "Could not prepare the export directory." }
    val cutoff = System.currentTimeMillis() - EXPORT_MAX_AGE_MILLIS
    root.listFiles()
        .orEmpty()
        .filter(File::isDirectory)
        .sortedByDescending(File::lastModified)
        .forEachIndexed { index, directory ->
            if (directory.lastModified() < cutoff || index >= EXPORT_DIRECTORY_LIMIT) {
                runCatching { directory.deleteRecursively() }
            }
        }
    return File(root, "$prefix-${UUID.randomUUID()}").also { directory ->
        check(directory.mkdir()) { "Could not prepare the export directory." }
    }
}

private const val EXPORT_DIRECTORY_LIMIT = 8
private const val EXPORT_MAX_AGE_MILLIS = 24L * 60L * 60L * 1_000L
