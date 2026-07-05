package com.uzairansar.hermex.ui.workspace

import android.graphics.BitmapFactory
import androidx.compose.foundation.Image
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.uzairansar.hermex.core.model.WorkspaceEntry
import com.uzairansar.hermex.data.repository.WorkspaceRepository

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

    Column(Modifier.fillMaxSize().padding(16.dp)) {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            Button(onClick = onBack) { Text("Back") }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Button(onClick = viewModel::goUp, enabled = state.currentPath != null) { Text("Up") }
                Button(onClick = viewModel::refresh) { Text("Refresh") }
            }
        }
        Spacer(Modifier.height(12.dp))
        Text("Workspace", style = MaterialTheme.typography.headlineMedium)
        Text(state.currentPath ?: "Session workspace", style = MaterialTheme.typography.bodySmall, maxLines = 1, overflow = TextOverflow.Ellipsis)
        if (state.roots.isNotEmpty()) {
            Spacer(Modifier.height(8.dp))
            Row(
                modifier = Modifier.horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                state.roots.forEach { root ->
                    AssistChip(onClick = {}, label = { Text(root.name ?: root.path ?: "Root") })
                }
            }
        }
        state.error?.let {
            Spacer(Modifier.height(8.dp))
            Text(it, color = MaterialTheme.colorScheme.error)
        }
        Spacer(Modifier.height(12.dp))
        when {
            state.isLoading -> CircularProgressIndicator()
            state.preview != null || state.isPreviewLoading -> FilePreview(
                isLoading = state.isPreviewLoading,
                title = state.preview?.path,
                content = state.preview?.content,
                binaryPreview = null,
                onClose = viewModel::closePreview,
            )
            state.binaryPreview != null -> FilePreview(
                isLoading = false,
                title = state.binaryPreview?.path,
                content = null,
                binaryPreview = state.binaryPreview,
                onClose = viewModel::closePreview,
            )
            state.entries.isEmpty() -> Text("No files found.")
            else -> Column(
                verticalArrangement = Arrangement.spacedBy(8.dp),
                modifier = Modifier.verticalScroll(rememberScrollState()),
            ) {
                state.entries.forEach { entry ->
                    WorkspaceEntryRow(entry = entry, onClick = { viewModel.open(entry) })
                }
            }
        }
    }
}

@Composable
private fun WorkspaceEntryRow(entry: WorkspaceEntry, onClick: () -> Unit) {
    val isDirectory = entry.type == "directory" || entry.type == "dir" || entry.type == "folder"
    Card(Modifier.fillMaxWidth().clickable(onClick = onClick)) {
        Column(Modifier.padding(12.dp)) {
            Text(
                text = "${if (isDirectory) "[dir]" else "[file]"} ${entry.name ?: entry.path ?: "Untitled"}",
                fontWeight = FontWeight.SemiBold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                text = listOfNotNull(entry.type, entry.size?.let { "$it bytes" }).joinToString(" - "),
                style = MaterialTheme.typography.bodySmall,
            )
        }
    }
}

@Composable
private fun FilePreview(
    isLoading: Boolean,
    title: String?,
    content: String?,
    binaryPreview: BinaryPreview?,
    onClose: () -> Unit,
) {
    Column(Modifier.fillMaxSize()) {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            Text(title ?: "Preview", style = MaterialTheme.typography.titleMedium, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Button(onClick = onClose) { Text("Close") }
        }
        Spacer(Modifier.height(8.dp))
        if (isLoading) {
            CircularProgressIndicator()
        } else if (binaryPreview?.isImage == true) {
            val bitmap = remember(binaryPreview.bytes) {
                BitmapFactory.decodeByteArray(binaryPreview.bytes, 0, binaryPreview.bytes.size)
            }
            if (bitmap != null) {
                Image(
                    bitmap = bitmap.asImageBitmap(),
                    contentDescription = title ?: "Image preview",
                    modifier = Modifier.fillMaxWidth().verticalScroll(rememberScrollState()),
                )
            } else {
                Text("Image preview could not be decoded.")
            }
        } else if (binaryPreview != null) {
            Card(Modifier.fillMaxWidth()) {
                Column(Modifier.padding(12.dp)) {
                    Text("Binary preview is not available yet.")
                    Text("${binaryPreview.bytes.size} bytes", style = MaterialTheme.typography.bodySmall)
                }
            }
        } else {
            Card(Modifier.fillMaxSize()) {
                Text(
                    text = content ?: "Binary preview is not available yet.",
                    modifier = Modifier.padding(12.dp).verticalScroll(rememberScrollState()),
                    fontFamily = FontFamily.Monospace,
                    style = MaterialTheme.typography.bodySmall,
                )
            }
        }
    }
}
