package com.uzairansar.hermex.data.repository

import com.uzairansar.hermex.core.model.*
import com.uzairansar.hermex.core.network.HermesApiClient

class GitRepository(private val client: HermesApiClient) {
    suspend fun info(sessionId: String): GitInfoResponse = client.gitInfo(sessionId)
    suspend fun status(sessionId: String): GitStatusResponse = client.gitStatus(sessionId)
    suspend fun branches(sessionId: String): GitBranchesResponse = client.gitBranches(sessionId)
    suspend fun diff(sessionId: String, path: String, kind: String): GitDiffResponse = client.gitDiff(sessionId, path, kind)
    suspend fun fetch(sessionId: String): GitRemoteActionResponse = client.gitFetch(sessionId)
    suspend fun pull(sessionId: String): GitRemoteActionResponse = client.gitPull(sessionId)
    suspend fun push(sessionId: String): GitRemoteActionResponse = client.gitPush(sessionId)
    suspend fun checkout(
        sessionId: String,
        ref: String,
        mode: String,
        newBranch: String? = null,
        track: Boolean? = null,
    ): GitCheckoutResponse = client.gitCheckout(sessionId, ref, mode, newBranch, track)
    suspend fun stashCheckout(
        sessionId: String,
        ref: String,
        mode: String,
        newBranch: String? = null,
        track: Boolean? = null,
    ): GitCheckoutResponse = client.gitStashCheckout(sessionId, ref, mode, newBranch, track)
    suspend fun stage(sessionId: String, paths: List<String>): GitMutationResponse = client.gitStage(sessionId, paths)
    suspend fun unstage(sessionId: String, paths: List<String>): GitMutationResponse = client.gitUnstage(sessionId, paths)
    suspend fun discard(sessionId: String, paths: List<String>, deleteUntracked: Boolean): GitMutationResponse =
        client.gitDiscard(sessionId, paths, deleteUntracked)
    suspend fun commit(sessionId: String, message: String): GitCommitResponse = client.gitCommit(sessionId, message)
    suspend fun commitSelected(sessionId: String, message: String, paths: List<String>): GitCommitResponse =
        client.gitCommitSelected(sessionId, message, paths)
    suspend fun commitMessage(sessionId: String): GitCommitMessageResponse = client.gitCommitMessage(sessionId)
    suspend fun commitMessageSelected(sessionId: String, paths: List<String>): GitCommitMessageResponse =
        client.gitCommitMessageSelected(sessionId, paths)
}
