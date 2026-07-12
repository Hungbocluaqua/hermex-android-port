package com.uzairansar.hermex.ui.workspace

import android.Manifest
import android.content.ContentValues
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.BitmapFactory
import android.os.Build
import android.provider.MediaStore
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.core.content.FileProvider
import androidx.core.content.ContextCompat
import com.uzairansar.hermex.core.model.WorkspaceEntry
import com.uzairansar.hermex.core.model.WorkspaceRoot
import com.uzairansar.hermex.data.repository.WorkspaceRepository
import com.uzairansar.hermex.ui.theme.HermexCardShape
import com.uzairansar.hermex.ui.theme.HermexIconButton
import com.uzairansar.hermex.ui.theme.HermexPillButton
import com.uzairansar.hermex.ui.theme.HermexSurfaceLevel
import com.uzairansar.hermex.ui.theme.hermexGlass
import com.uzairansar.hermex.ui.theme.hermexHairline
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File

@Composable
fun WorkspaceRoute(
    sessionId: String,
    repository: WorkspaceRepository,
    onBack: () -> Unit,
) {
    val viewModel: WorkspaceViewModel = viewModel(factory = object : ViewModelProvider.Factory {
        override fun <T : ViewModel> create(modelClass: Class<T>): T {
            @Suppress("UNCHECKED_CAST")
            return WorkspaceViewModel(sessionId, repository) as T
        }
    })
    val state by viewModel.state.collectAsStateWithLifecycle()
    val context = LocalContext.current

    Box(
        modifier = Modifier.fillMaxSize(),
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .navigationBarsPadding()
                .padding(horizontal = 16.dp, vertical = 14.dp),
        ) {
            WorkspaceHeader(
                currentPath = state.currentPath,
                onBack = onBack,
                onUp = viewModel::goUp,
                onRefresh = viewModel::refresh,
                canGoUp = state.currentPath != null,
            )
            WorkspaceLocationHeader(
                currentPath = state.currentPath,
                roots = state.roots,
                onRoot = viewModel::goRoot,
                onUp = viewModel::goUp,
                onOpenPath = viewModel::loadPath,
                onOpenRoot = viewModel::openRoot,
                canGoUp = state.currentPath != null,
            )
            WorkspaceSearchBar(
                searchText = state.searchText,
                onSearchTextChange = viewModel::updateSearchText,
            )
            state.error?.let {
                Text(it, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall, modifier = Modifier.padding(bottom = 8.dp))
            }
            val visibleEntries = remember(state.entries, state.searchText) {
                state.entries.filter { entry -> entry.matchesSearch(state.searchText) }
            }
            when {
                state.isLoading -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator(strokeWidth = 2.dp)
                }
                state.preview != null || state.isPreviewLoading -> FilePreview(
                    isLoading = state.isPreviewLoading,
                    title = state.preview?.path,
                    content = state.preview?.content,
                    textSizeBytes = state.preview?.size,
                    textLineCount = WorkspaceFilePreviewPolicy.lineCount(state.preview?.content),
                    binaryPreview = null,
                    onClose = viewModel::closePreview,
                    onShare = {
                        shareWorkspacePreview(
                            context = context,
                            title = state.preview?.path,
                            content = state.preview?.content,
                            binaryPreview = null,
                        )
                    },
                )
                state.binaryPreview != null -> FilePreview(
                    isLoading = false,
                    title = state.binaryPreview?.path,
                    content = null,
                    textSizeBytes = null,
                    textLineCount = null,
                    binaryPreview = state.binaryPreview,
                    onClose = viewModel::closePreview,
                    onShare = {
                        shareWorkspacePreview(
                            context = context,
                            title = state.binaryPreview?.path,
                            content = null,
                            binaryPreview = state.binaryPreview,
                        )
                    },
                )
                visibleEntries.isEmpty() -> EmptyWorkspace(query = state.searchText, currentPath = state.currentPath)
                else -> LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    verticalArrangement = Arrangement.spacedBy(4.dp),
                ) {
                    items(visibleEntries.size) { index ->
                        val entry = visibleEntries[index]
                        WorkspaceEntryRow(entry = entry, onClick = { viewModel.open(entry) })
                    }
                }
            }
        }
    }
}

private fun WorkspaceEntry.matchesSearch(query: String): Boolean {
    val trimmed = query.trim()
    if (trimmed.isBlank()) return true
    return listOfNotNull(name, path, type)
        .any { it.contains(trimmed, ignoreCase = true) }
}

@Composable
private fun WorkspaceHeader(
    currentPath: String?,
    onBack: () -> Unit,
    onUp: () -> Unit,
    onRefresh: () -> Unit,
    canGoUp: Boolean,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .statusBarsPadding()
            .padding(bottom = 14.dp)
            .hermexGlass(
                shape = HermexCardShape,
                surfaceLevel = HermexSurfaceLevel.Floating,
            )
            .padding(horizontal = 4.dp, vertical = 3.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        HermexIconButton("Back", "‹", onBack)
        Column(Modifier.weight(1f).padding(horizontal = 12.dp)) {
            Text("Files", style = MaterialTheme.typography.headlineMedium)
            Text(
                currentPath ?: "Session workspace",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.secondary,
                maxLines = 1,
                overflow = TextOverflow.MiddleEllipsis,
            )
        }
        HermexIconButton("Up", "↑", onUp, enabled = canGoUp)
        HermexIconButton("Refresh", "↻", onRefresh)
    }
}

@Composable
private fun WorkspaceLocationHeader(
    currentPath: String?,
    roots: List<WorkspaceRoot>,
    onRoot: () -> Unit,
    onUp: () -> Unit,
    onOpenPath: (String?) -> Unit,
    onOpenRoot: (WorkspaceRoot) -> Unit,
    canGoUp: Boolean,
) {
    val breadcrumbs = remember(currentPath) { currentPath.workspaceBreadcrumbs() }
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(bottom = 10.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text("Location", style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.SemiBold, color = MaterialTheme.colorScheme.secondary)
            Text(
                currentPath ?: ".",
                modifier = Modifier.weight(1f),
                style = MaterialTheme.typography.labelSmall,
                fontFamily = FontFamily.Monospace,
                maxLines = 1,
                overflow = TextOverflow.MiddleEllipsis,
            )
        }
        Row(
            modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            HermexPillButton("Root", onRoot, enabled = currentPath != null)
            HermexPillButton("Up", onUp, enabled = canGoUp)
            roots.forEach { root ->
                HermexPillButton(root.name ?: root.path ?: "Root", onClick = { onOpenRoot(root) })
            }
        }
        if (breadcrumbs.size > 1) {
            Row(
                modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(6.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                breadcrumbs.forEachIndexed { index, crumb ->
                    if (index > 0) {
                        Text(">", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.secondary)
                    }
                    HermexPillButton(
                        label = crumb.title,
                        onClick = { onOpenPath(crumb.path) },
                        enabled = crumb.path != currentPath,
                    )
                }
            }
        }
    }
}

internal data class WorkspaceBreadcrumb(
    val title: String,
    val path: String?,
)

internal fun String?.workspaceBreadcrumbs(): List<WorkspaceBreadcrumb> {
    val source = this.orEmpty()
    val raw = source.trim().trim('/', '\\')
    if (raw.isBlank()) return listOf(WorkspaceBreadcrumb("Root", null))
    val isUnixAbsolute = source.trimStart().startsWith('/')
    val separator = if (source.contains('\\') && !source.contains('/')) "\\" else "/"
    val parts = raw.split('/', '\\').filter { it.isNotBlank() }
    if (parts.isEmpty()) return listOf(WorkspaceBreadcrumb("Root", null))
    val crumbs = mutableListOf(WorkspaceBreadcrumb("Root", null))
    parts.forEachIndexed { index, part ->
        crumbs += WorkspaceBreadcrumb(
            title = part,
            path = parts
                .take(index + 1)
                .joinToString(separator)
                .let { joined -> if (isUnixAbsolute) "/$joined" else joined },
        )
    }
    return crumbs
}

@Composable
private fun WorkspaceSearchBar(
    searchText: String,
    onSearchTextChange: (String) -> Unit,
) {
    OutlinedTextField(
        value = searchText,
        onValueChange = onSearchTextChange,
        label = { Text("Search files") },
        singleLine = true,
        modifier = Modifier.fillMaxWidth().padding(bottom = 10.dp),
    )
}

@Composable
private fun WorkspaceEntryRow(entry: WorkspaceEntry, onClick: () -> Unit) {
    val isDirectory = entry.type == "directory" || entry.type == "dir" || entry.type == "folder"
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .hermexHairline(HermexCardShape)
            .padding(horizontal = 12.dp, vertical = 10.dp),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            modifier = Modifier
                .size(32.dp)
                .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.75f), HermexCardShape),
            contentAlignment = Alignment.Center,
        ) {
            Text(if (isDirectory) "DIR" else "TXT", style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.SemiBold)
        }
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text(
                text = entry.name ?: entry.path ?: "Untitled",
                fontWeight = FontWeight.SemiBold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                text = listOfNotNull(entry.path, entry.type, entry.size?.let { "$it bytes" }).joinToString(" - ").ifBlank { "No metadata" },
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.secondary,
                maxLines = 1,
                overflow = TextOverflow.MiddleEllipsis,
            )
        }
        if (isDirectory) {
            Text(">", color = MaterialTheme.colorScheme.secondary, fontWeight = FontWeight.SemiBold)
        }
    }
}

@Composable
private fun EmptyWorkspace(query: String, currentPath: String?) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .hermexGlass(shape = HermexCardShape, castsShadow = false)
            .padding(16.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(if (query.isBlank()) "No Files" else "No Matches", style = MaterialTheme.typography.titleMedium)
        Text(
            if (query.isBlank()) currentPath ?: "This session has no workspace entries yet." else "Try a different file name or path.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.secondary,
        )
    }
}

@Composable
private fun FilePreview(
    isLoading: Boolean,
    title: String?,
    content: String?,
    textSizeBytes: Long?,
    textLineCount: Int?,
    binaryPreview: BinaryPreview?,
    onClose: () -> Unit,
    onShare: () -> Unit,
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var isSavingImage by remember(binaryPreview?.path) { mutableStateOf(false) }
    var saveImageMessage by remember(binaryPreview?.path) { mutableStateOf<String?>(null) }
    val displayPath = title ?: binaryPreview?.path ?: "Preview"
    val metadata = remember(content, textSizeBytes, textLineCount, binaryPreview) {
        previewMetadata(
            content = content,
            textSizeBytes = textSizeBytes,
            textLineCount = textLineCount,
            binaryPreview = binaryPreview,
        )
    }
    val saveImage: () -> Unit = {
        val image = binaryPreview
        if (image?.isImage != true) {
            saveImageMessage = "This file is not an image."
        } else {
            scope.launch {
                isSavingImage = true
                saveImageMessage = withContext(Dispatchers.IO) {
                    saveWorkspaceImageToGallery(context, image)
                }
                isSavingImage = false
            }
        }
    }
    val legacyStoragePermission = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        if (granted) saveImage() else saveImageMessage = "Photos permission is required to save this image."
    }
    Column(Modifier.fillMaxSize()) {
        Row(
            Modifier.fillMaxWidth().padding(bottom = 8.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(title ?: "Preview", style = MaterialTheme.typography.titleMedium, maxLines = 1, overflow = TextOverflow.MiddleEllipsis, modifier = Modifier.weight(1f))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                if (binaryPreview?.isImage == true) {
                    HermexPillButton(
                        if (isSavingImage) "Saving" else "Save",
                        onClick = {
                            if (
                                Build.VERSION.SDK_INT <= Build.VERSION_CODES.P &&
                                ContextCompat.checkSelfPermission(
                                    context,
                                    Manifest.permission.WRITE_EXTERNAL_STORAGE,
                                ) != PackageManager.PERMISSION_GRANTED
                            ) {
                                legacyStoragePermission.launch(Manifest.permission.WRITE_EXTERNAL_STORAGE)
                            } else {
                                saveImage()
                            }
                        },
                        enabled = !isLoading && !isSavingImage,
                        filled = true,
                    )
                }
                HermexPillButton("Share", onShare, enabled = !isLoading && (content != null || binaryPreview != null))
                HermexPillButton("Close", onClose)
            }
        }
        saveImageMessage?.let { message ->
            Text(
                message,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.secondary,
                modifier = Modifier.padding(bottom = 8.dp),
            )
        }
        if (isLoading) {
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                CircularProgressIndicator(strokeWidth = 2.dp)
            }
        } else if (binaryPreview?.isImage == true) {
            val bitmap = remember(binaryPreview.bytes) {
                BitmapFactory.decodeByteArray(binaryPreview.bytes, 0, binaryPreview.bytes.size)
            }
            if (bitmap != null) {
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .verticalScroll(rememberScrollState()),
                    verticalArrangement = Arrangement.spacedBy(14.dp),
                ) {
                    FilePreviewHeader(displayPath, metadata)
                    Image(
                        bitmap = bitmap.asImageBitmap(),
                        contentDescription = title ?: "Image preview",
                        modifier = Modifier.fillMaxWidth(),
                        contentScale = ContentScale.Fit,
                    )
                }
            } else {
                FileUnavailablePreview(
                    title = "Could Not Preview Image",
                    message = "Could not decode this image.",
                    path = displayPath,
                    metadata = metadata,
                )
            }
        } else if (binaryPreview != null) {
            FileUnavailablePreview(
                title = "No Preview",
                message = "Preview is not available for this file type.",
                path = displayPath,
                metadata = metadata,
            )
        } else {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .hermexGlass(shape = HermexCardShape, castsShadow = false)
                    .padding(12.dp)
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                FilePreviewHeader(displayPath, metadata)
                SelectionContainer {
                    Text(
                        text = content ?: "Preview is not available for this file.",
                        modifier = Modifier.horizontalScroll(rememberScrollState()),
                        fontFamily = FontFamily.Monospace,
                        style = MaterialTheme.typography.bodySmall,
                    )
                }
            }
        }
    }
}

@Composable
private fun FilePreviewHeader(
    path: String,
    metadata: String?,
) {
    SelectionContainer {
        Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text(
                text = path,
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.secondary,
                fontFamily = FontFamily.Monospace,
            )
            metadata?.let {
                Text(
                    text = it,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.secondary,
                )
            }
        }
    }
}

@Composable
private fun FileUnavailablePreview(
    title: String,
    message: String,
    path: String,
    metadata: String?,
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .hermexGlass(shape = HermexCardShape, castsShadow = false)
            .padding(14.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
        Text(message, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.secondary)
        FilePreviewHeader(path = path, metadata = metadata)
    }
}

private fun previewMetadata(
    content: String?,
    textSizeBytes: Long?,
    textLineCount: Int?,
    binaryPreview: BinaryPreview?,
): String? {
    val parts = mutableListOf<String>()
    val byteCount = textSizeBytes ?: binaryPreview?.bytes?.size?.toLong() ?: content?.toByteArray(Charsets.UTF_8)?.size?.toLong()
    byteCount?.let { parts += fileSizeText(it) }
    textLineCount?.let { parts += "$it lines" }
    binaryPreview?.mimeType?.takeIf { it.isNotBlank() }?.let { parts += it }
    return parts.joinToString(" - ").takeIf { it.isNotBlank() }
}

private fun fileSizeText(bytes: Long): String =
    when {
        bytes < 1_000 -> "$bytes bytes"
        bytes < 1_000_000 -> String.format(java.util.Locale.US, "%.1f KB", bytes / 1_000.0)
        bytes < 1_000_000_000 -> String.format(java.util.Locale.US, "%.1f MB", bytes / 1_000_000.0)
        else -> String.format(java.util.Locale.US, "%.1f GB", bytes / 1_000_000_000.0)
    }

private fun saveWorkspaceImageToGallery(
    context: Context,
    preview: BinaryPreview,
): String {
    val resolver = context.contentResolver
    val fileName = preview.galleryFileName()
    val values = ContentValues().apply {
        put(MediaStore.MediaColumns.DISPLAY_NAME, fileName)
        put(MediaStore.MediaColumns.MIME_TYPE, preview.mimeType)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            put(MediaStore.MediaColumns.RELATIVE_PATH, "Pictures/Hermex")
            put(MediaStore.MediaColumns.IS_PENDING, 1)
        }
    }
    val uri = runCatching {
        resolver.insert(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, values)
    }.getOrNull() ?: return "Could not save image."

    return try {
        resolver.openOutputStream(uri)?.use { output -> output.write(preview.bytes) }
            ?: error("Could not open gallery item.")
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            values.clear()
            values.put(MediaStore.MediaColumns.IS_PENDING, 0)
            resolver.update(uri, values, null, null)
        }
        "Image saved to gallery."
    } catch (error: Throwable) {
        runCatching { resolver.delete(uri, null, null) }
        "Could not save image: ${error.localizedMessage ?: "Unknown error."}"
    }
}

private fun BinaryPreview.galleryFileName(): String {
    val rawName = path
        .trimEnd('/', '\\')
        .substringAfterLast('/')
        .substringAfterLast('\\')
        .ifBlank { "hermex-image" }
    val safeName = rawName
        .replace(Regex("[^A-Za-z0-9._-]+"), "_")
        .trim('.', '_', '-')
        .take(96)
        .ifBlank { "hermex-image" }
    if (safeName.substringAfterLast('.', missingDelimiterValue = "").isNotBlank()) return safeName
    val extension = mimeType.substringAfter('/', missingDelimiterValue = "png")
        .substringBefore(';')
        .lowercase()
        .takeIf { it.matches(Regex("[a-z0-9]{2,5}")) }
        ?: "png"
    return "$safeName.$extension"
}

private fun shareWorkspacePreview(
    context: Context,
    title: String?,
    content: String?,
    binaryPreview: BinaryPreview?,
) {
    val bytes = content?.toByteArray(Charsets.UTF_8) ?: binaryPreview?.bytes ?: return
    val sourcePath = title ?: binaryPreview?.path ?: "workspace-file"
    val fileName = WorkspaceFilePreviewPolicy.displayName(sourcePath)
        .ifBlank { "workspace-file" }
        .replace(Regex("[^A-Za-z0-9._-]"), "_")
        .take(96)
    val exportDir = File(context.cacheDir, "exports").also { it.mkdirs() }
    val file = File(exportDir, fileName)
    file.writeBytes(bytes)
    val mimeType = when {
        content != null -> WorkspaceFilePreviewPolicy.mimeType(sourcePath, isText = true)
        binaryPreview != null -> binaryPreview.mimeType
        else -> WorkspaceFilePreviewPolicy.mimeType(sourcePath)
    }
    val uri = FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", file)
    val intent = Intent(Intent.ACTION_SEND)
        .setType(mimeType)
        .putExtra(Intent.EXTRA_STREAM, uri)
        .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
    context.startActivity(Intent.createChooser(intent, "Share File"))
}
