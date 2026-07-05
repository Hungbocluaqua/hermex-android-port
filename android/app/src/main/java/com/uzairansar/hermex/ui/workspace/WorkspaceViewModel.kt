package com.uzairansar.hermex.ui.workspace

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.uzairansar.hermex.core.model.FileResponse
import com.uzairansar.hermex.core.model.WorkspaceEntry
import com.uzairansar.hermex.core.model.WorkspaceRoot
import com.uzairansar.hermex.data.repository.WorkspaceRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class WorkspaceUiState(
    val roots: List<WorkspaceRoot> = emptyList(),
    val currentPath: String? = null,
    val entries: List<WorkspaceEntry> = emptyList(),
    val preview: FileResponse? = null,
    val binaryPreview: BinaryPreview? = null,
    val isLoading: Boolean = true,
    val isPreviewLoading: Boolean = false,
    val error: String? = null,
)

data class BinaryPreview(
    val path: String,
    val bytes: ByteArray,
    val isImage: Boolean,
) {
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is BinaryPreview) return false
        return path == other.path && isImage == other.isImage && bytes.contentEquals(other.bytes)
    }

    override fun hashCode(): Int {
        var result = path.hashCode()
        result = 31 * result + bytes.contentHashCode()
        result = 31 * result + isImage.hashCode()
        return result
    }
}

class WorkspaceViewModel(
    private val sessionId: String,
    private val repository: WorkspaceRepository,
) : ViewModel() {
    private val _state = MutableStateFlow(WorkspaceUiState())
    val state: StateFlow<WorkspaceUiState> = _state

    init {
        refresh()
    }

    fun refresh() {
        val path = _state.value.currentPath
        viewModelScope.launch {
            _state.update { it.copy(isLoading = true, error = null, preview = null, binaryPreview = null) }
            runCatching {
                val roots = repository.workspaces()
                val list = repository.list(sessionId, path)
                roots to list
            }.onSuccess { (roots, list) ->
                _state.update {
                    it.copy(
                        roots = roots,
                        currentPath = list.path ?: path,
                        entries = list.entries.orEmpty().sortedWith(compareBy<WorkspaceEntry> { entry ->
                            if (entry.type == "directory" || entry.type == "dir") 0 else 1
                        }.thenBy { entry -> entry.name ?: entry.path.orEmpty() }),
                        isLoading = false,
                    )
                }
            }.onFailure { error ->
                _state.update { it.copy(isLoading = false, error = error.message ?: "Could not load workspace.") }
            }
        }
    }

    fun open(entry: WorkspaceEntry) {
        val path = entry.path ?: entry.name ?: return
        val isDirectory = entry.type == "directory" || entry.type == "dir" || entry.type == "folder"
        if (isDirectory) {
            _state.update { it.copy(currentPath = path, preview = null, binaryPreview = null) }
            refresh()
        } else {
            preview(path)
        }
    }

    fun goUp() {
        val current = _state.value.currentPath?.trimEnd('/', '\\') ?: return
        val parent = current.substringBeforeLast('/', missingDelimiterValue = "")
            .ifBlank { current.substringBeforeLast('\\', missingDelimiterValue = "") }
            .ifBlank { null }
        _state.update { it.copy(currentPath = parent, preview = null) }
        refresh()
    }

    fun closePreview() {
        _state.update { it.copy(preview = null, binaryPreview = null) }
    }

    private fun preview(path: String) {
        viewModelScope.launch {
            _state.update { it.copy(isPreviewLoading = true, error = null, preview = null, binaryPreview = null) }
            runCatching { repository.file(sessionId, path) }
                .onSuccess { preview ->
                    if (!preview.content.isNullOrEmpty()) {
                        _state.update { it.copy(preview = preview, isPreviewLoading = false) }
                    } else {
                        loadBinaryPreview(path)
                    }
                }
                .onFailure { error ->
                    if (path.looksLikeImage()) {
                        loadBinaryPreview(path)
                    } else {
                        _state.update { it.copy(isPreviewLoading = false, error = error.message ?: "Could not open file.") }
                    }
                }
        }
    }

    private suspend fun loadBinaryPreview(path: String) {
        runCatching { repository.rawFile(sessionId, path) }
            .onSuccess { bytes ->
                _state.update {
                    it.copy(
                        binaryPreview = BinaryPreview(path = path, bytes = bytes, isImage = path.looksLikeImage()),
                        isPreviewLoading = false,
                    )
                }
            }
            .onFailure { error ->
                _state.update { it.copy(isPreviewLoading = false, error = error.message ?: "Could not open file.") }
            }
    }

    private fun String.looksLikeImage(): Boolean {
        val lower = lowercase()
        return lower.endsWith(".png") ||
            lower.endsWith(".jpg") ||
            lower.endsWith(".jpeg") ||
            lower.endsWith(".gif") ||
            lower.endsWith(".webp") ||
            lower.endsWith(".bmp")
    }
}
