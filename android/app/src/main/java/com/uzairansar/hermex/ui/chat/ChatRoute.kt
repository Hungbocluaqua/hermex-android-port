package com.uzairansar.hermex.ui.chat

import android.Manifest
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.pm.PackageManager
import android.media.MediaPlayer
import android.os.Build
import android.speech.tts.TextToSpeech
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.uzairansar.hermex.core.model.ApprovalChoice
import com.uzairansar.hermex.core.model.ChatMessage
import com.uzairansar.hermex.core.model.PendingApproval
import com.uzairansar.hermex.core.model.PendingClarification
import com.uzairansar.hermex.data.repository.ChatRepository
import com.uzairansar.hermex.data.share.SharedDraftStore
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File

@Composable
fun ChatRoute(
    sessionId: String,
    repository: ChatRepository,
    sharedDraftStore: SharedDraftStore? = null,
    consumeSharedDraft: Boolean = false,
    onBack: () -> Unit,
    onOpenWorkspace: () -> Unit,
    onOpenGit: () -> Unit,
) {
    val viewModel: ChatViewModel = viewModel(factory = object : ViewModelProvider.Factory {
        override fun <T : ViewModel> create(modelClass: Class<T>): T {
            @Suppress("UNCHECKED_CAST")
            return ChatViewModel(sessionId, repository) as T
        }
    })
    val state by viewModel.state.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val recorder = remember(context) { VoiceNoteRecorder(context) }
    val streamNotifier = remember(context) { StreamStatusNotifier(context.applicationContext) }
    val ttsState = remember { mutableStateOf<TextToSpeech?>(null) }
    val serverSpeechPlayer = remember { mutableStateOf<MediaPlayer?>(null) }
    val serverSpeechFile = remember { mutableStateOf<File?>(null) }
    val speechScope = rememberCoroutineScope()
    val releaseServerSpeech: () -> Unit = {
        serverSpeechPlayer.value?.release()
        serverSpeechPlayer.value = null
        serverSpeechFile.value?.delete()
        serverSpeechFile.value = null
    }
    DisposableEffect(context) {
        val tts = TextToSpeech(context) { }
        ttsState.value = tts
        onDispose {
            releaseServerSpeech()
            tts.stop()
            tts.shutdown()
            ttsState.value = null
        }
    }
    val attachmentPicker = rememberLauncherForActivityResult(ActivityResultContracts.OpenMultipleDocuments()) { uris ->
        uris.forEach { uri -> viewModel.attach(context, uri) }
    }
    val microphonePermission = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        if (granted) viewModel.startVoiceNote(recorder)
    }
    val notificationPermission = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { }
    val copyText: (String) -> Unit = { text ->
        val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
        clipboard.setPrimaryClip(ClipData.newPlainText("Hermex message", text))
    }
    val speakLocally: (String) -> Unit = { text ->
        ttsState.value?.speak(text, TextToSpeech.QUEUE_FLUSH, null, "hermex-message")
    }
    val listenText: (String) -> Unit = { text ->
        speechScope.launch {
            val audio = viewModel.synthesizeSpeech(text)
            if (audio == null || audio.isEmpty()) {
                speakLocally(text)
                return@launch
            }
            val played = runCatching {
                releaseServerSpeech()
                val audioFile = withContext(Dispatchers.IO) {
                    File.createTempFile("hermex-tts-", ".mp3", context.cacheDir).also { it.writeBytes(audio) }
                }
                val player = MediaPlayer()
                serverSpeechPlayer.value = player
                serverSpeechFile.value = audioFile
                player.setDataSource(audioFile.absolutePath)
                player.setOnCompletionListener { releaseServerSpeech() }
                player.setOnErrorListener { _, _, _ ->
                    releaseServerSpeech()
                    true
                }
                player.prepare()
                player.start()
            }.isSuccess
            if (!played) {
                releaseServerSpeech()
                speakLocally(text)
            }
        }
    }
    val assistantPreview = state.messages.lastOrNull { it.role == "assistant" }?.displayText

    LaunchedEffect(consumeSharedDraft, sharedDraftStore) {
        if (consumeSharedDraft) {
            sharedDraftStore?.loadPendingDraft()?.let { draft ->
                viewModel.consumeSharedDraft(context, draft)
            }
        }
    }

    LaunchedEffect(state.isStreaming, state.activeStreamId) {
        if (
            state.isStreaming &&
            Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
            ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED
        ) {
            notificationPermission.launch(Manifest.permission.POST_NOTIFICATIONS)
        }
    }

    LaunchedEffect(state.isStreaming, state.activeStreamId, state.liveToolActivity, assistantPreview) {
        if (state.isStreaming) {
            streamNotifier.show(
                sessionId = sessionId,
                streamId = state.activeStreamId,
                toolActivity = state.liveToolActivity,
                preview = assistantPreview,
            )
        } else {
            streamNotifier.clear(sessionId)
        }
    }

    DisposableEffect(streamNotifier, sessionId) {
        onDispose { streamNotifier.clear(sessionId) }
    }

    Column(Modifier.fillMaxSize().padding(16.dp)) {
        Row(
            Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Button(onClick = onBack) { Text("Back") }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Button(onClick = onOpenWorkspace) { Text("Files") }
                Button(onClick = onOpenGit) { Text("Git") }
                Button(onClick = viewModel::load) { Text("Refresh") }
            }
        }
        Spacer(Modifier.height(8.dp))
        if (state.isViewingCachedData) Text("Offline cache", color = MaterialTheme.colorScheme.tertiary)
        state.notice?.let { Text(it, color = MaterialTheme.colorScheme.tertiary) }
        state.error?.let { Text(it, color = MaterialTheme.colorScheme.error) }
        if (state.liveReasoning.isNotBlank()) {
            Text("Reasoning: ${state.liveReasoning.takeLast(160)}", style = MaterialTheme.typography.bodySmall)
        }
        state.liveToolActivity?.let { Text(it, color = MaterialTheme.colorScheme.secondary) }
        state.pendingApproval?.let { approval ->
            Spacer(Modifier.height(8.dp))
            ApprovalCard(
                approval = approval,
                count = state.pendingApprovalCount,
                isResponding = state.isRespondingToPendingPrompt,
                onChoice = viewModel::respondApproval,
            )
        }
        state.pendingClarification?.let { clarification ->
            Spacer(Modifier.height(8.dp))
            ClarificationCard(
                clarification = clarification,
                count = state.pendingClarificationCount,
                draft = state.clarificationDraft,
                isResponding = state.isRespondingToPendingPrompt,
                onDraftChange = viewModel::updateClarificationDraft,
                onSubmit = { viewModel.respondClarification() },
                onChoice = { viewModel.respondClarification(it) },
            )
        }
        Spacer(Modifier.height(8.dp))
        if (state.isLoading) {
            CircularProgressIndicator()
        } else {
            LazyColumn(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                items(state.messages) { message ->
                    MessageCard(
                        message = message,
                        onCopy = { copyText(message.displayText) },
                        onListen = { listenText(message.displayText) },
                    )
                }
            }
        }
        Spacer(Modifier.height(8.dp))
        OutlinedTextField(
            value = state.draft,
            onValueChange = viewModel::updateDraft,
            label = { Text("Message") },
            modifier = Modifier.fillMaxWidth(),
            minLines = 2,
            maxLines = 6,
            enabled = !state.isViewingCachedData,
        )
        Spacer(Modifier.height(8.dp))
        Row(
            modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            AssistChip(
                onClick = viewModel::cycleModel,
                enabled = state.modelOptions.isNotEmpty() && !state.isStreaming,
                label = { Text("Model: ${state.selectedModel?.label ?: state.selectedModel?.name ?: state.selectedModel?.id ?: "default"}") },
            )
            AssistChip(
                onClick = viewModel::cycleProfile,
                enabled = state.profileOptions.isNotEmpty() && !state.isStreaming,
                label = { Text("Profile: ${state.selectedProfile?.displayName ?: state.selectedProfile?.name ?: "default"}") },
            )
            AssistChip(
                onClick = viewModel::cycleReasoning,
                enabled = state.reasoningOptions.isNotEmpty() && !state.isStreaming,
                label = { Text("Reasoning: ${state.selectedReasoning ?: "default"}") },
            )
            AssistChip(
                onClick = { attachmentPicker.launch(arrayOf("*/*")) },
                enabled = !state.isUploadingAttachment && !state.isStreaming && !state.isViewingCachedData,
                label = { Text(if (state.isUploadingAttachment) "Uploading" else "Attach") },
            )
            AssistChip(
                onClick = {
                    if (state.isRecordingVoiceNote) {
                        viewModel.stopAndTranscribeVoiceNote(recorder)
                    } else if (ContextCompat.checkSelfPermission(context, Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED) {
                        viewModel.startVoiceNote(recorder)
                    } else {
                        microphonePermission.launch(Manifest.permission.RECORD_AUDIO)
                    }
                },
                enabled = !state.isStreaming && !state.isViewingCachedData && !state.isTranscribingVoiceNote,
                label = {
                    Text(
                        when {
                            state.isTranscribingVoiceNote -> "Transcribing"
                            state.isRecordingVoiceNote -> "Stop voice"
                            else -> "Voice"
                        },
                    )
                },
            )
            if (state.isRecordingVoiceNote) {
                AssistChip(onClick = { viewModel.cancelVoiceNote(recorder) }, label = { Text("Cancel voice") })
            }
        }
        if (state.pendingAttachments.isNotEmpty()) {
            Spacer(Modifier.height(8.dp))
            Row(
                modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                state.pendingAttachments.forEach { attachment ->
                    AssistChip(
                        onClick = { viewModel.removeAttachment(attachment) },
                        label = {
                            Text(
                                text = attachment.filename ?: attachment.path ?: "Attachment",
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                            )
                        },
                    )
                }
            }
        }
        Spacer(Modifier.height(8.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Button(
                onClick = viewModel::send,
                enabled = state.draft.isNotBlank() && !state.isStreaming && !state.isViewingCachedData,
            ) {
                Text("Send")
            }
            Button(
                onClick = viewModel::steerDraft,
                enabled = state.draft.isNotBlank() && state.isStreaming && !state.isRunningSessionAction,
            ) {
                Text("Steer")
            }
            Button(onClick = viewModel::cancel, enabled = state.isStreaming) {
                Text("Stop")
            }
        }
        Spacer(Modifier.height(8.dp))
        Row(
            modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            AssistChip(
                onClick = viewModel::undoLastExchange,
                enabled = !state.isStreaming && !state.isViewingCachedData && !state.isRunningSessionAction,
                label = { Text("Undo") },
            )
            AssistChip(
                onClick = viewModel::retryLastTurn,
                enabled = !state.isStreaming && !state.isViewingCachedData && !state.isRunningSessionAction,
                label = { Text("Retry") },
            )
            AssistChip(
                onClick = { viewModel.compressContext() },
                enabled = !state.isStreaming && !state.isViewingCachedData && !state.isRunningSessionAction,
                label = { Text(if (state.isRunningSessionAction) "Working" else "Compress") },
            )
        }
    }
}

@Composable
private fun ApprovalCard(
    approval: PendingApproval,
    count: Int,
    isResponding: Boolean,
    onChoice: (ApprovalChoice) -> Unit,
) {
    Card(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(12.dp)) {
            Text("Approval required", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
            if (count > 1) Text("Pending approvals: $count", style = MaterialTheme.typography.bodySmall)
            Spacer(Modifier.height(6.dp))
            Text(approval.command ?: approval.description ?: "The agent wants to run an action.")
            approval.displayPatternKeys.takeIf { it.isNotEmpty() }?.let { keys ->
                Text("Patterns: ${keys.joinToString(", ")}", style = MaterialTheme.typography.bodySmall)
            }
            Spacer(Modifier.height(8.dp))
            Row(
                modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Button(onClick = { onChoice(ApprovalChoice.Once) }, enabled = !isResponding) { Text("Allow once") }
                Button(onClick = { onChoice(ApprovalChoice.Session) }, enabled = !isResponding) { Text("Session") }
                Button(onClick = { onChoice(ApprovalChoice.Always) }, enabled = !isResponding) { Text("Always") }
                Button(onClick = { onChoice(ApprovalChoice.Deny) }, enabled = !isResponding) { Text("Deny") }
            }
        }
    }
}

@Composable
private fun ClarificationCard(
    clarification: PendingClarification,
    count: Int,
    draft: String,
    isResponding: Boolean,
    onDraftChange: (String) -> Unit,
    onSubmit: () -> Unit,
    onChoice: (String) -> Unit,
) {
    Card(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(12.dp)) {
            Text("Clarification needed", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
            if (count > 1) Text("Pending prompts: $count", style = MaterialTheme.typography.bodySmall)
            Spacer(Modifier.height(6.dp))
            Text(clarification.displayQuestion)
            if (clarification.displayChoices.isNotEmpty()) {
                Spacer(Modifier.height(8.dp))
                Row(
                    modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    clarification.displayChoices.forEach { choice ->
                        AssistChip(onClick = { onChoice(choice) }, enabled = !isResponding, label = { Text(choice) })
                    }
                }
            }
            Spacer(Modifier.height(8.dp))
            OutlinedTextField(
                value = draft,
                onValueChange = onDraftChange,
                modifier = Modifier.fillMaxWidth(),
                label = { Text("Response") },
                minLines = 1,
                maxLines = 4,
                enabled = !isResponding,
            )
            Spacer(Modifier.height(8.dp))
            Button(onClick = onSubmit, enabled = draft.isNotBlank() && !isResponding) {
                Text("Submit")
            }
        }
    }
}

@Composable
private fun MessageCard(
    message: ChatMessage,
    onCopy: () -> Unit,
    onListen: () -> Unit,
) {
    Card(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(12.dp)) {
            Text(
                text = message.role ?: "message",
                style = MaterialTheme.typography.labelMedium,
                fontWeight = FontWeight.Bold,
                color = if (message.role == "user") MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.secondary,
            )
            Spacer(Modifier.height(4.dp))
            MarkdownText(message.displayText.ifBlank { "(empty)" })
            val tools = message.toolCalls.orEmpty()
            if (tools.isNotEmpty()) {
                Spacer(Modifier.height(8.dp))
                Text("${tools.size} tool call(s)", style = MaterialTheme.typography.bodySmall)
            }
            if (message.displayText.isNotBlank()) {
                Spacer(Modifier.height(8.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    AssistChip(onClick = onCopy, label = { Text("Copy") })
                    if (message.role == "assistant") {
                        AssistChip(onClick = onListen, label = { Text("Listen") })
                    }
                }
            }
        }
    }
}
