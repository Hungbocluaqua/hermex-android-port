package com.uzairansar.hermex.data.repository

import com.uzairansar.hermex.core.model.DirectoryListResponse
import com.uzairansar.hermex.core.model.FileResponse
import com.uzairansar.hermex.core.model.WorkspaceRoot
import com.uzairansar.hermex.core.network.HermesApiClient

class WorkspaceRepository(private val client: HermesApiClient) {
    suspend fun workspaces(): List<WorkspaceRoot> {
        val response = client.workspaces()
        return response.workspaces ?: response.roots.orEmpty()
    }

    suspend fun suggestions(prefix: String): List<String> = client.workspaceSuggestions(prefix).suggestions.orEmpty()
    suspend fun list(sessionId: String, path: String?): DirectoryListResponse = client.directoryList(sessionId, path)
    suspend fun file(sessionId: String, path: String): FileResponse = client.file(sessionId, path)
    suspend fun rawFile(sessionId: String, path: String): ByteArray = client.rawFile(sessionId, path)
}
