package com.uzairansar.hermex.ui.chat

import android.Manifest
import android.content.ClipData
import android.content.ClipboardManager
import android.content.ContentValues
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.BitmapFactory
import android.media.MediaPlayer
import android.net.Uri
import android.os.Build
import android.provider.MediaStore
import android.speech.tts.TextToSpeech
import android.view.HapticFeedbackConstants
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.ime
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Slider
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.viewmodel.compose.viewModel
import com.uzairansar.hermex.core.model.ApprovalChoice
import com.uzairansar.hermex.core.model.ChatMessage
import com.uzairansar.hermex.core.model.CompressionReferenceCard
import com.uzairansar.hermex.core.model.ContextWindowSnapshot
import com.uzairansar.hermex.core.model.FileResponse
import com.uzairansar.hermex.core.model.GitDiffResponse
import com.uzairansar.hermex.core.model.GitFileChange
import com.uzairansar.hermex.core.model.MessageAttachment
import com.uzairansar.hermex.core.model.MessageActionContext
import com.uzairansar.hermex.core.model.MessageActionContextResolver
import com.uzairansar.hermex.core.model.MessageActionRole
import com.uzairansar.hermex.core.model.ModelSummary
import com.uzairansar.hermex.core.model.PendingApproval
import com.uzairansar.hermex.core.model.PendingClarification
import com.uzairansar.hermex.core.model.ProfileSummary
import com.uzairansar.hermex.core.model.ToolCall
import com.uzairansar.hermex.core.model.ToolCallGroup
import com.uzairansar.hermex.core.model.TranscriptMediaParser
import com.uzairansar.hermex.core.model.TranscriptMediaReference
import com.uzairansar.hermex.core.model.TranscriptMediaSegment
import com.uzairansar.hermex.core.model.TurnFileChange
import com.uzairansar.hermex.core.model.TurnFileChangeAggregator
import com.uzairansar.hermex.core.model.TurnFileChangeSummary
import com.uzairansar.hermex.core.model.UploadResponse
import com.uzairansar.hermex.core.model.WorkspaceRoot
import com.uzairansar.hermex.core.model.shouldRenderTranscriptItem
import com.uzairansar.hermex.data.preferences.ChatDisplaySettings
import com.uzairansar.hermex.data.preferences.LocalSettingsRepository
import com.uzairansar.hermex.data.preferences.ModelFavoriteKey
import com.uzairansar.hermex.data.preferences.StreamingSendBehavior
import com.uzairansar.hermex.data.preferences.displayModelTitle
import com.uzairansar.hermex.data.preferences.favoriteKeyOrNull
import com.uzairansar.hermex.data.preferences.matchesSelection
import com.uzairansar.hermex.data.preferences.modelIdentifier
import com.uzairansar.hermex.data.preferences.normalizedProvider
import com.uzairansar.hermex.data.preferences.visibleFavoriteModels
import com.uzairansar.hermex.data.preferences.visibleRecentModels
import com.uzairansar.hermex.data.repository.ChatRepository
import com.uzairansar.hermex.data.repository.GitRepository
import com.uzairansar.hermex.data.share.SharedDraftStore
import com.uzairansar.hermex.ui.theme.HermexCardShape
import com.uzairansar.hermex.ui.theme.HermexGlassShape
import com.uzairansar.hermex.ui.theme.HermexIconButton
import com.uzairansar.hermex.ui.theme.HermexPillButton
import com.uzairansar.hermex.ui.theme.HermexSelectorPill
import com.uzairansar.hermex.ui.theme.LocalHermexHapticsEnabled
import com.uzairansar.hermex.ui.git.HermexGitDiffContent
import com.uzairansar.hermex.ui.theme.hermexColorFromHex
import com.uzairansar.hermex.ui.theme.hermexGlass
import com.uzairansar.hermex.ui.theme.hermexPrimaryActionContainerColor
import com.uzairansar.hermex.ui.theme.hermexPrimaryActionContentColor
import com.uzairansar.hermex.ui.theme.primaryActionTintApplies
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import okhttp3.HttpUrl
import java.io.File
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle
import java.util.Locale

private data class TurnDiffPresentation(
    val files: List<GitFileChange>,
    val initialPath: String? = null,
)

@Composable
fun ChatRoute(
    sessionId: String,
    repository: ChatRepository,
    gitRepository: GitRepository? = null,
    localSettingsRepository: LocalSettingsRepository? = null,
    activeHeaderColorHex: String? = null,
    sharedDraftStore: SharedDraftStore? = null,
    consumeSharedDraft: Boolean = false,
    autoStartVoice: Boolean = false,
    onOpenChat: (String) -> Unit = {},
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
    val gitViewModel: ChatGitViewModel? = gitRepository?.let { repo ->
        viewModel(
            key = "chat-git-$sessionId",
            factory = object : ViewModelProvider.Factory {
                override fun <T : ViewModel> create(modelClass: Class<T>): T {
                    @Suppress("UNCHECKED_CAST")
                    return ChatGitViewModel(sessionId, repo) as T
                }
            },
        )
    }
    val gitState by remember(gitViewModel) {
        gitViewModel?.state ?: flowOf(ChatGitUiState())
    }.collectAsStateWithLifecycle(initialValue = ChatGitUiState())
    val turnChangesSummary = remember(
        state.messages,
        state.completedToolCallGroups,
        gitState.files,
        gitState.hasRepository,
        state.isStreaming,
    ) {
        if (!gitState.hasRepository || state.isStreaming) {
            TurnFileChangeSummary()
        } else {
            TurnFileChangeAggregator.summarize(
                toolCalls = TurnFileChangeAggregator.latestTurnToolCalls(
                    messages = state.messages,
                    completedGroups = state.completedToolCallGroups,
                ),
                statusFiles = gitState.files,
            )
        }
    }
    val turnChangesAnchorIndex = remember(state.messages, turnChangesSummary) {
        TurnFileChangeAggregator.latestAssistantIndex(state.messages)
            ?.takeIf { turnChangesSummary.hasChanges }
    }
    val chatDisplaySettings by remember(localSettingsRepository) {
        localSettingsRepository?.chatDisplaySettings ?: flowOf(ChatDisplaySettings())
    }.collectAsStateWithLifecycle(initialValue = ChatDisplaySettings())
    val streamingSendBehavior by remember(localSettingsRepository) {
        localSettingsRepository?.streamingSendBehavior ?: flowOf(StreamingSendBehavior.Steer)
    }.collectAsStateWithLifecycle(initialValue = StreamingSendBehavior.Steer)
    val tintPrimaryActionsWithThemeColor by remember(localSettingsRepository) {
        localSettingsRepository?.tintPrimaryActionsWithThemeColor ?: flowOf(false)
    }.collectAsStateWithLifecycle(initialValue = false)
    val responseCompletionNotificationsEnabled by remember(localSettingsRepository) {
        localSettingsRepository?.responseCompletionNotificationsEnabled ?: flowOf(false)
    }.collectAsStateWithLifecycle(initialValue = false)
    val favoriteModelKeys by remember(localSettingsRepository) {
        localSettingsRepository?.favoriteModelKeys ?: flowOf(emptyList<ModelFavoriteKey>())
    }.collectAsStateWithLifecycle(initialValue = emptyList())
    val recentModelKeys by remember(localSettingsRepository) {
        localSettingsRepository?.recentModelKeys ?: flowOf(emptyList<ModelFavoriteKey>())
    }.collectAsStateWithLifecycle(initialValue = emptyList())
    val chatLayoutDirection = if (chatDisplaySettings.rtlChatLayoutEnabled) {
        LayoutDirection.Rtl
    } else {
        LayoutDirection.Ltr
    }
    val sendButtonEnabled = if (state.isStreaming) true else state.draft.isNotBlank() && !state.isViewingCachedData
    val primaryActionTintColor = remember(tintPrimaryActionsWithThemeColor, activeHeaderColorHex, sendButtonEnabled) {
        if (primaryActionTintApplies(tintPrimaryActionsWithThemeColor, sendButtonEnabled)) {
            hermexColorFromHex(activeHeaderColorHex)
        } else {
            null
        }
    }
    val context = LocalContext.current
    val hapticView = LocalView.current
    val hapticsEnabled = LocalHermexHapticsEnabled.current
    val density = LocalDensity.current
    val lifecycleOwner = LocalLifecycleOwner.current
    var appIsActive by remember(lifecycleOwner) {
        mutableStateOf(lifecycleOwner.lifecycle.currentState.isAtLeast(Lifecycle.State.RESUMED))
    }
    val latestResponseCompletionNotificationsEnabled by rememberUpdatedState(responseCompletionNotificationsEnabled)
    val latestAppIsActive by rememberUpdatedState(appIsActive)
    val recorder = remember(context) { VoiceNoteRecorder(context) }
    val streamNotifier = remember(context) { StreamStatusNotifier(context.applicationContext) }
    val ttsState = remember { mutableStateOf<TextToSpeech?>(null) }
    val serverSpeechPlayer = remember { mutableStateOf<MediaPlayer?>(null) }
    val serverSpeechFile = remember { mutableStateOf<File?>(null) }
    val speechScope = rememberCoroutineScope()
    val modelPickerScope = rememberCoroutineScope()
    val releaseServerSpeech: () -> Unit = {
        serverSpeechPlayer.value?.release()
        serverSpeechPlayer.value = null
        serverSpeechFile.value?.delete()
        serverSpeechFile.value = null
    }

    LaunchedEffect(state.openSessionId) {
        val openSessionId = state.openSessionId ?: return@LaunchedEffect
        onOpenChat(openSessionId)
        viewModel.consumeOpenSession()
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
    val photoPicker = rememberLauncherForActivityResult(ActivityResultContracts.PickMultipleVisualMedia()) { uris ->
        uris.forEach { uri -> viewModel.attach(context, uri) }
    }
    val microphonePermission = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        if (granted) viewModel.startVoiceNote(recorder)
    }
    var showsModelPicker by remember { mutableStateOf(false) }
    var showsProfilePicker by remember { mutableStateOf(false) }
    var showsReasoningPicker by remember { mutableStateOf(false) }
    var showsWorkspacePicker by remember { mutableStateOf(false) }
    var showsAttachmentOptions by remember { mutableStateOf(false) }
    var selectedTextContext by remember { mutableStateOf<MessageActionContext?>(null) }
    var editingMessageContext by remember { mutableStateOf<MessageActionContext?>(null) }
    var editDiscardContext by remember { mutableStateOf<MessageActionContext?>(null) }
    var regenerateDiscardContext by remember { mutableStateOf<MessageActionContext?>(null) }
    var editMessageDraft by remember { mutableStateOf("") }
    var showsClearConversationConfirmation by remember { mutableStateOf(false) }
    var turnDiffPresentation by remember { mutableStateOf<TurnDiffPresentation?>(null) }
    var autoVoiceConsumed by remember(sessionId, autoStartVoice) { mutableStateOf(false) }
    var composerHeight by remember { mutableStateOf(0.dp) }
    val imeBottomInset = with(density) { WindowInsets.ime.getBottom(this).toDp() }
    val navigationBottomInset = with(density) { WindowInsets.navigationBars.getBottom(this).toDp() }
    val transcriptBottomInset = composerHeight + maxOf(imeBottomInset, navigationBottomInset) + 28.dp

    LaunchedEffect(state.showsReasoningControl) {
        if (!state.showsReasoningControl) {
            showsReasoningPicker = false
        }
    }
    LaunchedEffect(state.showsProfileControl) {
        if (!state.showsProfileControl) {
            showsProfilePicker = false
        }
    }

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
    val transcriptMessagesAfter: (MessageActionContext) -> Int = remember(
        state.messages,
        chatDisplaySettings.showThinkingAndToolCards,
    ) {
        { context ->
            state.messages.indices.count { index ->
                index > context.visibleIndex &&
                    state.messages[index].shouldRenderTranscriptItem(chatDisplaySettings.showThinkingAndToolCards)
            }
        }
    }

    LaunchedEffect(consumeSharedDraft, sharedDraftStore) {
        if (consumeSharedDraft) {
            sharedDraftStore?.loadPendingDraft()?.let { draft ->
                viewModel.consumeSharedDraft(context, draft)
            }
        }
    }

    LaunchedEffect(autoStartVoice, autoVoiceConsumed) {
        if (autoStartVoice && !autoVoiceConsumed) {
            autoVoiceConsumed = true
            if (ContextCompat.checkSelfPermission(context, Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED) {
                viewModel.startVoiceNote(recorder)
            } else {
                microphonePermission.launch(Manifest.permission.RECORD_AUDIO)
            }
        }
    }

    LaunchedEffect(
        state.isStreaming,
        state.isRecoveringStream,
        state.activeStreamId,
        state.liveToolActivity,
        assistantPreview,
        chatDisplaySettings.showsStatusNotificationResponseExcerpts,
    ) {
        if (state.isStreaming) {
            streamNotifier.show(
                sessionId = sessionId,
                streamId = state.activeStreamId,
                recoveryLabel = state.activeStreamRecoveryLabel,
                toolActivity = state.liveToolActivity,
                preview = assistantPreview.takeIf { chatDisplaySettings.showsStatusNotificationResponseExcerpts },
            )
        } else {
            streamNotifier.clear(sessionId)
        }
    }

    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, _ ->
            appIsActive = lifecycleOwner.lifecycle.currentState.isAtLeast(Lifecycle.State.RESUMED)
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    LaunchedEffect(viewModel, streamNotifier, sessionId, context) {
        var lastHandledTrigger = viewModel.state.value.responseCompletionTrigger
        viewModel.state
            .map { it.responseCompletionTrigger }
            .distinctUntilChanged()
            .collect { trigger ->
                if (trigger <= lastHandledTrigger) return@collect
                lastHandledTrigger = trigger
                viewModel.refreshCompletedTranscriptIfNeeded()
                val canPostNotifications = Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU ||
                    ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED
                if (
                    ResponseCompletionNotificationPolicy.shouldSchedule(
                        preferenceEnabled = latestResponseCompletionNotificationsEnabled,
                        canPostNotifications = canPostNotifications,
                        completedNormally = true,
                        appIsActive = latestAppIsActive,
                    )
                ) {
                    streamNotifier.showResponseComplete(sessionId)
                }
            }
    }

    LaunchedEffect(viewModel, sessionId, hapticsEnabled) {
        var previousIsStreaming = viewModel.state.value.isStreaming
        var previousCompletionTrigger = viewModel.state.value.responseCompletionTrigger
        viewModel.state
            .map { Triple(it.isStreaming, it.responseCompletionTrigger, it.error != null) }
            .distinctUntilChanged()
            .collect { (isStreaming, completionTrigger, hasError) ->
                if (hapticsEnabled) {
                    when (
                        ChatHapticPolicy.eventForTransition(
                            previousIsStreaming = previousIsStreaming,
                            currentIsStreaming = isStreaming,
                            previousCompletionTrigger = previousCompletionTrigger,
                            currentCompletionTrigger = completionTrigger,
                            hasError = hasError,
                        )
                    ) {
                        ChatHapticEvent.MessageSent -> hapticView.performHapticFeedback(HapticFeedbackConstants.CLOCK_TICK)
                        ChatHapticEvent.ResponseCompleted -> hapticView.performHapticFeedback(HapticFeedbackConstants.CONFIRM)
                        ChatHapticEvent.StreamCancelled -> hapticView.performHapticFeedback(HapticFeedbackConstants.LONG_PRESS)
                        ChatHapticEvent.None -> Unit
                    }
                }
                previousIsStreaming = isStreaming
                previousCompletionTrigger = completionTrigger
            }
    }

    DisposableEffect(streamNotifier, sessionId) {
        onDispose { streamNotifier.clear(sessionId) }
    }

    LaunchedEffect(gitViewModel, state.responseCompletionTrigger, state.isStreaming) {
        if (!state.isStreaming) {
            gitViewModel?.refresh()
        }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background),
    ) {
        Column(
            Modifier
                .fillMaxSize()
                .statusBarsPadding(),
        ) {
            ChatTopBar(
                title = state.headerTitle,
                subtitle = state.headerSubtitle,
                isLoading = state.isLoading,
                onBack = onBack,
                onOpenWorkspace = onOpenWorkspace,
                onOpenGit = onOpenGit,
                onRefresh = viewModel::load,
                onClearConversation = { showsClearConversationConfirmation = true },
                canClearConversation = !state.isLoading && !state.isViewingCachedData && !state.isRunningSessionAction,
            )
            ChatGitActionStrip(
                state = gitState,
                writesDisabled = state.isStreaming || state.isViewingCachedData || state.isRunningSessionAction,
                fetchDisabled = state.isViewingCachedData,
                onChanges = onOpenGit,
                onCommit = { gitViewModel?.quickCommit(push = false) },
                onCommitAndPush = { gitViewModel?.quickCommit(push = true) },
                onFetch = { gitViewModel?.fetch() },
                onPull = { gitViewModel?.pull() },
                onPush = { gitViewModel?.push() },
                onClear = { gitViewModel?.clearMessages() },
            )
            CompositionLocalProvider(LocalLayoutDirection provides chatLayoutDirection) {
                ChatStatusStack(
                state = state,
                onApprovalChoice = viewModel::respondApproval,
                onSkipApprovals = viewModel::skipApprovalsForCurrentSession,
                onClarificationDraftChange = viewModel::updateClarificationDraft,
                onClarificationSubmit = viewModel::respondClarification,
                onClarificationChoice = { choice -> viewModel.respondClarification(choice) },
            )
            if (state.isLoading) {
                ChatTranscriptLoadingSkeleton()
            } else if (state.showsTranscriptErrorState) {
                ChatTranscriptErrorState(
                    errorMessage = state.error.orEmpty(),
                    onRetry = viewModel::load,
                )
            } else if (state.messages.isEmpty() && state.compressionReferenceCard == null && state.pendingClarification == null && state.error == null) {
                ChatTranscriptEmptyState()
            } else {
                val renderedMessages = remember(
                    state.messages,
                    state.completedToolCallGroups,
                    state.compressionReferenceCard,
                    chatDisplaySettings.showThinkingAndToolCards,
                ) {
                    state.messages.mapIndexedNotNull { index, message ->
                        val hasCompletedToolGroup = chatDisplaySettings.showThinkingAndToolCards &&
                            state.completedToolCallGroups.any { it.afterMessageIndex == index }
                        val hasCompressionReference = state.compressionReferenceCard?.afterMessageIndex == index
                        val shouldRender = message.shouldRenderTranscriptItem(chatDisplaySettings.showThinkingAndToolCards) ||
                            hasCompletedToolGroup ||
                            hasCompressionReference
                        if (shouldRender) IndexedValue(index, message) else null
                    }
                }
                LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(
                        start = 14.dp,
                        end = 14.dp,
                        top = 8.dp,
                        bottom = transcriptBottomInset,
                    ),
                    verticalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    if (state.hasOlderMessages) {
                        item("load-older-messages") {
                            LoadOlderMessagesButton(
                                isLoading = state.isLoadingOlderMessages,
                                onClick = viewModel::loadOlderMessages,
                            )
                        }
                    }
                    state.compressionReferenceCard
                        ?.takeIf { it.afterMessageIndex == null }
                        ?.let { card ->
                            item("compression-reference-top") {
                                CompressionReferenceMarkerCard(card)
                            }
                    }
                    items(
                        renderedMessages,
                        key = { item ->
                            val message = item.value
                            "message-${state.messagesOffset + item.index}-${message.messageId ?: message.id ?: message.role.orEmpty()}"
                        },
                    ) { item ->
                        val index = item.index
                        val message = item.value
                        val visibleText = message.visibleDisplayText(chatDisplaySettings.hidesAttachmentPaths)
                        val completedToolGroups = state.completedToolCallGroups.filter { it.afterMessageIndex == index }
                        val actionContext = MessageActionContextResolver.contextFor(
                            message = message,
                            visibleIndex = index,
                            messagesOffset = state.messagesOffset,
                        )
                        Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                            if (chatDisplaySettings.showThinkingAndToolCards) {
                                completedToolGroups.forEach { group ->
                                    CompletedToolActivityCard(
                                        group = group,
                                        startsExpanded = chatDisplaySettings.toolCardsStartExpanded,
                                    )
                                }
                            }
                            if (message.shouldRenderTranscriptItem(chatDisplaySettings.showThinkingAndToolCards)) {
                                MessageRow(
                                    message = message,
                                    isStreamingMessage = state.isStreaming && message.id == "streaming",
                                    showThinkingAndToolCards = chatDisplaySettings.showThinkingAndToolCards,
                                    thinkingCardsStartExpanded = chatDisplaySettings.thinkingCardsStartExpanded,
                                    toolCardsStartExpanded = chatDisplaySettings.toolCardsStartExpanded,
                                    hidesAttachmentPaths = chatDisplaySettings.hidesAttachmentPaths,
                                    showsAssistantTurnTimestamps = chatDisplaySettings.showsAssistantTurnTimestamps,
                                    wrapsCodeBlockLines = chatDisplaySettings.wrapsCodeBlockLines,
                                    streamedTextAnimationEnabled = chatDisplaySettings.streamedTextAnimationEnabled,
                                    loadTranscriptMediaImage = viewModel::transcriptMediaThumbnailData,
                                    loadAttachmentFile = viewModel::attachmentTextFile,
                                    actionContext = actionContext,
                                    messageActionEnabled = !state.isViewingCachedData && !state.isStreaming && !state.isRunningSessionAction,
                                    isRegeneratingMessage = state.isRegeneratingMessage,
                                    isEditingMessage = state.isEditingMessage,
                                    isForkingMessage = state.isForkingMessage,
                                    onCopy = { copyText(actionContext?.copyText ?: visibleText) },
                                    onListen = { listenText(actionContext?.listenText ?: visibleText) },
                                    onSelectText = { selectedTextContext = it },
                                    onEdit = {
                                        editMessageDraft = it.copyText
                                        if (transcriptMessagesAfter(it) > 0) {
                                            editDiscardContext = it
                                        } else {
                                            editingMessageContext = it
                                        }
                                    },
                                    onRegenerate = {
                                        if (transcriptMessagesAfter(it) > 0) {
                                            regenerateDiscardContext = it
                                        } else {
                                            viewModel.regenerateAssistantResponse(it)
                                        }
                                    },
                                    onFork = viewModel::forkFromMessage,
                                )
                            }
                            state.compressionReferenceCard
                                ?.takeIf { it.afterMessageIndex == index }
                                ?.let { card ->
                                    CompressionReferenceMarkerCard(card)
                                }
                            if (turnChangesAnchorIndex == index) {
                                GitTurnChangesCard(
                                    summary = turnChangesSummary,
                                    onOpenAll = {
                                        val files = turnChangesSummary.diffFiles
                                        if (files.isNotEmpty()) {
                                            turnDiffPresentation = TurnDiffPresentation(files = files)
                                        } else {
                                            onOpenGit()
                                        }
                                    },
                                    onOpenFile = { file ->
                                        turnDiffPresentation = TurnDiffPresentation(files = listOf(file), initialPath = file.gitPath())
                                    },
                                )
                            }
                        }
                    }
                    if (chatDisplaySettings.showThinkingAndToolCards && state.liveReasoning.isNotBlank()) {
                        item("live-reasoning") {
                            ReasoningAccessoryCard(
                                text = state.liveReasoning,
                                startsExpanded = chatDisplaySettings.thinkingCardsStartExpanded,
                            )
                        }
                    }
                    state.liveToolActivity
                        ?.takeIf { chatDisplaySettings.showThinkingAndToolCards && it.isNotBlank() }
                        ?.let { toolActivity ->
                            item("live-tool-activity") {
                                LiveToolActivityCard(
                                    activity = toolActivity,
                                    startsExpanded = chatDisplaySettings.toolCardsStartExpanded,
                                )
                            }
                        }
                    if (state.showsAssistantTypingIndicator(chatDisplaySettings.showThinkingAndToolCards)) {
                        item("assistant-typing-indicator") {
                            AssistantTypingIndicator()
                        }
                    }
                }
            }
            }
        }

        Box(
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .fillMaxWidth()
                .onSizeChanged { size ->
                    composerHeight = with(density) { size.height.toDp() }
                }
                .imePadding()
                .navigationBarsPadding()
                .background(
                    Brush.verticalGradient(
                        listOf(
                            Color.Transparent,
                            MaterialTheme.colorScheme.background.copy(alpha = 0.92f),
                            MaterialTheme.colorScheme.background,
                        ),
                    ),
                )
                .padding(top = 30.dp),
        ) {
            CompositionLocalProvider(LocalLayoutDirection provides chatLayoutDirection) {
                ComposerSurface(
                    state = state,
                    streamingSendBehavior = streamingSendBehavior,
                    primaryActionTintColor = primaryActionTintColor,
                    onDraftChange = viewModel::updateDraft,
                    onSend = viewModel::send,
                    onStreamingSend = { viewModel.submitStreamingDraft(streamingSendBehavior) },
                    onCancel = viewModel::cancel,
                    onOpenModelPicker = { showsModelPicker = true },
                    onOpenProfilePicker = { showsProfilePicker = true },
                    onOpenReasoningPicker = { showsReasoningPicker = true },
                    onOpenWorkspacePicker = { showsWorkspacePicker = true },
                    onAttach = { showsAttachmentOptions = true },
                    onVoice = {
                        if (state.isRecordingVoiceNote) {
                            viewModel.stopAndTranscribeVoiceNote(recorder)
                        } else if (ContextCompat.checkSelfPermission(context, Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED) {
                            viewModel.startVoiceNote(recorder)
                        } else {
                            microphonePermission.launch(Manifest.permission.RECORD_AUDIO)
                        }
                    },
                    onCancelVoice = { viewModel.cancelVoiceNote(recorder) },
                    onRemoveAttachment = viewModel::removeAttachment,
                    loadAttachmentImage = viewModel::attachmentImageData,
                    loadAttachmentFile = viewModel::attachmentTextFile,
                    onUndo = viewModel::undoLastExchange,
                    onRetry = viewModel::retryLastTurn,
                    onCompress = viewModel::compressContext,
                )
            }
        }
    }

    turnDiffPresentation?.let { presentation ->
        gitRepository?.let { repository ->
            GitTurnDiffSheet(
                sessionId = sessionId,
                repository = repository,
                presentation = presentation,
                onDismiss = { turnDiffPresentation = null },
            )
        }
    }

    selectedTextContext?.let { context ->
        SelectableMessageTextSheet(
            text = context.copyText,
            onDismiss = { selectedTextContext = null },
        )
    }

    editingMessageContext?.let { context ->
        EditMessageSheet(
            draft = editMessageDraft,
            onDraftChange = { editMessageDraft = it },
            onDismiss = {
                editingMessageContext = null
                editMessageDraft = ""
            },
            onSubmit = {
                viewModel.editMessage(context, editMessageDraft)
                editingMessageContext = null
                editMessageDraft = ""
            },
        )
    }

    editDiscardContext?.let { context ->
        DiscardLaterMessagesDialog(
            message = "Editing this message will discard ${transcriptMessagesAfter(context)} later messages.",
            confirmLabel = "Discard & Edit",
            onDismiss = {
                editDiscardContext = null
                editMessageDraft = ""
            },
            onConfirm = {
                editDiscardContext = null
                editingMessageContext = context
            },
        )
    }

    regenerateDiscardContext?.let { context ->
        DiscardLaterMessagesDialog(
            message = "Regenerating this response will discard ${transcriptMessagesAfter(context)} later messages.",
            confirmLabel = "Discard & Regenerate",
            onDismiss = { regenerateDiscardContext = null },
            onConfirm = {
                regenerateDiscardContext = null
                viewModel.regenerateAssistantResponse(context)
            },
        )
    }

    if (showsClearConversationConfirmation) {
        ClearConversationDialog(
            onDismiss = { showsClearConversationConfirmation = false },
            onConfirm = {
                showsClearConversationConfirmation = false
                viewModel.clearConversation()
            },
        )
    }

    if (showsModelPicker) {
        ModelPickerDialog(
            models = state.modelOptions,
            selected = state.selectedModel,
            favoriteKeys = favoriteModelKeys,
            recentKeys = recentModelKeys,
            onDismiss = { showsModelPicker = false },
            onSelect = { model ->
                showsModelPicker = false
                viewModel.selectModel(model)
                modelPickerScope.launch {
                    localSettingsRepository?.recordRecentModel(model)
                }
            },
            onToggleFavorite = { model ->
                modelPickerScope.launch {
                    localSettingsRepository?.toggleFavoriteModel(model)
                }
            },
            onDeleteSavedCustom = { model ->
                modelPickerScope.launch {
                    localSettingsRepository?.removeFavoriteModel(model)
                    localSettingsRepository?.removeRecentModel(model)
                }
            },
        )
    }
    if (showsProfilePicker && state.showsProfileControl) {
        ProfilePickerDialog(
            profiles = state.profileOptions,
            selected = state.selectedProfile,
            onDismiss = { showsProfilePicker = false },
            onSelect = { profile ->
                showsProfilePicker = false
                viewModel.selectProfile(profile)
            },
        )
    }
    if (showsReasoningPicker && state.showsReasoningControl) {
        ReasoningPickerDialog(
            efforts = state.reasoningOptions,
            selected = state.selectedReasoning,
            onDismiss = { showsReasoningPicker = false },
            onSelect = { effort ->
                showsReasoningPicker = false
                viewModel.selectReasoning(effort)
            },
        )
    }
    if (showsWorkspacePicker) {
        WorkspacePickerDialog(
            roots = state.workspaceRoots,
            selected = state.selectedWorkspacePath,
            suggestions = state.workspaceSuggestions,
            onLoadSuggestions = viewModel::loadWorkspaceSuggestions,
            onDismiss = { showsWorkspacePicker = false },
            onSelect = { path ->
                showsWorkspacePicker = false
                viewModel.selectWorkspace(path)
            },
        )
    }
    if (showsAttachmentOptions) {
        AttachmentOptionsSheet(
            onDismiss = { showsAttachmentOptions = false },
            onAttachFile = {
                showsAttachmentOptions = false
                attachmentPicker.launch(arrayOf("*/*"))
            },
            onPhotos = {
                showsAttachmentOptions = false
                photoPicker.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly))
            },
        )
    }
}

private fun ChatUiState.showsAssistantTypingIndicator(showThinkingAndToolCards: Boolean): Boolean {
        if (!isStreaming || activeStreamId.isNullOrBlank()) return false
        if (pendingApproval != null || pendingClarification != null) return false
        if (showThinkingAndToolCards && (liveReasoning.isNotBlank() || !liveToolActivity.isNullOrBlank())) return false
        return messages.none { message ->
            message.role == "assistant" &&
                message.id == "streaming" &&
                message.displayText.isNotBlank()
            }
}

private val ChatUiState.showsTranscriptErrorState: Boolean
    get() = !isLoading &&
        messages.isEmpty() &&
        pendingClarification == null &&
        !error.isNullOrBlank()

@Composable
private fun ComposerAttachmentStrip(
    attachments: List<UploadResponse>,
    onRemove: (UploadResponse) -> Unit,
    onPreview: (UploadResponse) -> Unit,
    loadAttachmentImage: suspend (String) -> ByteArray?,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .height(108.dp)
            .horizontalScroll(rememberScrollState())
            .padding(horizontal = 2.dp, vertical = 2.dp),
        horizontalArrangement = Arrangement.spacedBy(10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        attachments.forEach { attachment ->
            ComposerAttachmentTile(
                attachment = attachment,
                onRemove = { onRemove(attachment) },
                onPreview = { onPreview(attachment) },
                loadAttachmentImage = loadAttachmentImage,
            )
        }
    }
}

@Composable
private fun ComposerAttachmentTile(
    attachment: UploadResponse,
    onRemove: () -> Unit,
    onPreview: () -> Unit,
    loadAttachmentImage: suspend (String) -> ByteArray?,
) {
    Box(
        modifier = Modifier.padding(top = 6.dp, end = 6.dp),
    ) {
        if (attachment.inferredIsImage) {
            RemoteAttachmentImageTile(
                path = attachment.resolvedAttachmentPath,
                size = 96.dp,
                cornerRadius = 14.dp,
                loadAttachmentImage = loadAttachmentImage,
                onPreview = onPreview,
            )
        } else {
            Row(
                modifier = Modifier
                    .width(222.dp)
                    .height(92.dp)
                    .clip(RoundedCornerShape(14.dp))
                    .clickable(onClick = onPreview)
                    .background(MaterialTheme.colorScheme.surfaceVariant)
                    .border(0.5.dp, MaterialTheme.colorScheme.outlineVariant, RoundedCornerShape(14.dp))
                    .padding(horizontal = 12.dp, vertical = 12.dp),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Box(
                    modifier = Modifier
                        .size(width = 58.dp, height = 68.dp)
                        .clip(RoundedCornerShape(10.dp))
                        .background(attachment.badgeColor.copy(alpha = 0.15f)),
                    contentAlignment = Alignment.Center,
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(3.dp)) {
                        Text(
                            attachment.fileKindLabel,
                            style = MaterialTheme.typography.labelSmall,
                            color = attachment.badgeColor,
                            fontWeight = FontWeight.Bold,
                        )
                        Text(
                            attachment.fileExtensionLabel,
                            style = MaterialTheme.typography.labelSmall,
                            color = attachment.badgeColor,
                            fontWeight = FontWeight.Bold,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
                }
                Column(
                    modifier = Modifier.weight(1f),
                    verticalArrangement = Arrangement.spacedBy(5.dp),
                ) {
                    Text(
                        attachment.displayName,
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = FontWeight.Medium,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                    )
                    Text(
                        attachment.fileDetailText,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.secondary,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }
        }
        Text(
            text = "X",
            modifier = Modifier
                .align(Alignment.TopEnd)
                .clip(CircleShape)
                .clickable(onClick = onRemove)
                .background(MaterialTheme.colorScheme.background)
                .border(0.5.dp, MaterialTheme.colorScheme.outlineVariant, CircleShape)
                .padding(horizontal = 8.dp, vertical = 4.dp),
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurface,
            fontWeight = FontWeight.Bold,
        )
    }
}

@Composable
private fun RemoteAttachmentImageTile(
    path: String?,
    size: Dp,
    cornerRadius: Dp,
    loadAttachmentImage: suspend (String) -> ByteArray?,
    onPreview: () -> Unit,
) {
    var bytes by remember(path) { mutableStateOf<ByteArray?>(null) }
    var didAttemptLoad by remember(path) { mutableStateOf(false) }
    LaunchedEffect(path) {
        bytes = null
        didAttemptLoad = false
        val resolvedPath = path?.takeIf { it.isNotBlank() }
        if (resolvedPath != null) {
            bytes = loadAttachmentImage(resolvedPath)
        }
        didAttemptLoad = true
    }
    val bitmap = remember(bytes) {
        bytes?.let { BitmapFactory.decodeByteArray(it, 0, it.size) }
    }
    val shape = RoundedCornerShape(cornerRadius)
    Box(
        modifier = Modifier
            .size(size)
            .clip(shape)
            .clickable(onClick = onPreview)
            .background(MaterialTheme.colorScheme.surfaceVariant)
            .border(0.5.dp, MaterialTheme.colorScheme.outlineVariant, shape),
        contentAlignment = Alignment.Center,
    ) {
        when {
            bitmap != null -> {
                Image(
                    bitmap = bitmap.asImageBitmap(),
                    contentDescription = "Image attachment",
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.fillMaxSize(),
                )
            }
            !didAttemptLoad -> CircularProgressIndicator(strokeWidth = 2.dp, modifier = Modifier.size(22.dp))
            else -> Text(
                "IMG",
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.secondary,
                fontWeight = FontWeight.SemiBold,
            )
        }
    }
}

@Composable
private fun AttachmentPreviewSheet(
    attachment: UploadResponse,
    loadAttachmentData: suspend (String) -> ByteArray?,
    loadAttachmentFile: suspend (String) -> FileResponse?,
    onDismiss: () -> Unit,
) {
    PickerSheet(
        title = attachment.displayName,
        onDismiss = onDismiss,
        heightFraction = 0.48f,
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(18.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Text(
                when {
                    attachment.inferredIsImage -> "Image attachment"
                    attachment.inferredIsAudio -> "Audio attachment"
                    else -> "File attachment"
                },
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.SemiBold,
            )
            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
            if (attachment.inferredIsImage) {
                AttachmentPreviewImage(
                    path = attachment.resolvedAttachmentPath,
                    loadAttachmentData = loadAttachmentData,
                    contentDescription = attachment.displayName,
                )
            } else if (attachment.inferredIsAudio) {
                InlineAudioAttachmentPlayer(
                    title = attachment.displayName,
                    path = attachment.resolvedAttachmentPath,
                    loadAttachmentData = loadAttachmentData,
                )
            } else if (attachment.isKnownUnsupportedBinary) {
                AttachmentPreviewUnavailable(
                    message = "Preview is not available for this file type.",
                    path = attachment.resolvedAttachmentPath ?: attachment.displayName,
                )
            } else {
                AttachmentTextPreview(
                    path = attachment.resolvedAttachmentPath,
                    loadAttachmentFile = loadAttachmentFile,
                )
            }
            AttachmentInfoRow("Name", attachment.displayName)
            AttachmentInfoRow("Path", attachment.path?.takeIf { it.isNotBlank() } ?: "Unavailable")
            AttachmentInfoRow("Type", attachment.mime?.takeIf { it.isNotBlank() } ?: attachment.fileExtensionLabel)
            AttachmentInfoRow("Size", attachment.size.formatBytesOrUnavailable())
        }
    }
}

@Composable
private fun AttachmentInfoRow(
    label: String,
    value: String,
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = label,
            modifier = Modifier.weight(1f),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.secondary,
        )
        Text(
            text = value,
            modifier = Modifier.weight(2f),
            style = MaterialTheme.typography.bodySmall,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

@Composable
private fun ChatTopBar(
    title: String,
    subtitle: String?,
    isLoading: Boolean,
    onBack: () -> Unit,
    onOpenWorkspace: () -> Unit,
    onOpenGit: () -> Unit,
    onRefresh: () -> Unit,
    onClearConversation: () -> Unit,
    canClearConversation: Boolean,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .statusBarsPadding()
            .padding(horizontal = 10.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        HermexIconButton("Back", "\u2039", onBack)
        Column(
            modifier = Modifier
                .weight(1f)
                .padding(horizontal = 12.dp),
        ) {
            Text(
                title,
                style = MaterialTheme.typography.titleMedium,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            if (!subtitle.isNullOrBlank()) {
                Text(
                    subtitle,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.secondary,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
        Row(
            modifier = Modifier.hermexGlass(shape = CircleShape, castsShadow = false).padding(horizontal = 2.dp),
            horizontalArrangement = Arrangement.spacedBy(0.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            HermexIconButton("Files", "\u2302", onOpenWorkspace)
            HermexIconButton("Git", "\u2387", onOpenGit)
            HermexIconButton("Refresh", "\u21bb", onRefresh, enabled = !isLoading)
            HermexIconButton("Clear conversation", "\u232B", onClearConversation, enabled = canClearConversation)
        }
    }
}

@Composable
private fun ChatGitActionStrip(
    state: ChatGitUiState,
    writesDisabled: Boolean,
    fetchDisabled: Boolean,
    onChanges: () -> Unit,
    onCommit: () -> Unit,
    onCommitAndPush: () -> Unit,
    onFetch: () -> Unit,
    onPull: () -> Unit,
    onPush: () -> Unit,
    onClear: () -> Unit,
) {
    if (!state.hasRepository) return

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 14.dp, vertical = 4.dp),
        verticalArrangement = Arrangement.spacedBy(5.dp),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = buildString {
                    append(state.branch?.takeIf { it.isNotBlank() } ?: "Git")
                    append("  ")
                    append("+${state.totalAdditions} -${state.totalDeletions}  ${state.changedCount}")
                },
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.secondary,
                modifier = Modifier
                    .hermexGlass(shape = CircleShape, castsShadow = false)
                    .padding(horizontal = 10.dp, vertical = 7.dp),
            )
            if (state.phase != null) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    modifier = Modifier
                        .hermexGlass(shape = CircleShape, castsShadow = false)
                        .padding(horizontal = 10.dp, vertical = 7.dp),
                ) {
                    CircularProgressIndicator(modifier = Modifier.size(14.dp), strokeWidth = 2.dp)
                    Text(state.phase.title, style = MaterialTheme.typography.labelSmall)
                }
            }
            HermexPillButton("Changes", onChanges, enabled = !state.isLoading)
            HermexPillButton("Commit", onCommit, enabled = !writesDisabled && !state.isMutating && state.hasChanges)
            HermexPillButton("Commit & Push", onCommitAndPush, enabled = !writesDisabled && !state.isMutating && state.hasChanges, filled = true)
            HermexPillButton("Fetch", onFetch, enabled = !fetchDisabled && !state.isMutating)
            HermexPillButton("Pull", onPull, enabled = !writesDisabled && !state.isMutating)
            HermexPillButton("Push", onPush, enabled = !writesDisabled && !state.isMutating)
        }
        state.notice?.let {
            Text(
                it,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.tertiary,
                modifier = Modifier.clickable(onClick = onClear),
            )
        }
        state.error?.let {
            Text(
                it,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.error,
                modifier = Modifier.clickable(onClick = onClear),
            )
        }
    }
}

@Composable
private fun ChatStatusStack(
    state: ChatUiState,
    onApprovalChoice: (ApprovalChoice) -> Unit,
    onSkipApprovals: () -> Unit,
    onClarificationDraftChange: (String) -> Unit,
    onClarificationSubmit: () -> Unit,
    onClarificationChoice: (String) -> Unit,
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 14.dp),
        verticalArrangement = Arrangement.spacedBy(7.dp),
    ) {
        if (state.isViewingCachedData) InlineNotice("Offline cache")
        state.activeStreamRecoveryLabel?.let { recoveryLabel ->
            StreamRecoveryStatusPill(recoveryLabel)
        }
        if (!state.isRecoveringStream) {
            state.notice?.let { InlineNotice(it) }
        }
        if (!state.showsTranscriptErrorState) {
            state.error?.let { InlineNotice(it, isError = true) }
        }
        if (state.isSessionApprovalBypassEnabled) {
            ApprovalBypassStatusPill()
        }
        state.pendingApproval?.let { approval ->
            ApprovalCard(
                approval = approval,
                count = state.pendingApprovalCount,
                isResponding = state.isRespondingToPendingPrompt,
                onChoice = onApprovalChoice,
                onSkipAll = onSkipApprovals,
            )
        }
        state.pendingClarification?.let { clarification ->
            ClarificationCard(
                clarification = clarification,
                count = state.pendingClarificationCount,
                draft = state.clarificationDraft,
                isResponding = state.isRespondingToPendingPrompt,
                onDraftChange = onClarificationDraftChange,
                onSubmit = onClarificationSubmit,
                onChoice = onClarificationChoice,
            )
        }
    }
}

@Composable
private fun ChatTranscriptLoadingSkeleton() {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .semantics { contentDescription = "Loading messages" }
            .padding(horizontal = 16.dp)
            .padding(top = 16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        chatSkeletonRows.forEach { row ->
            ChatTranscriptLoadingSkeletonRow(row)
        }
        Spacer(Modifier.height(1.dp))
    }
}

@Composable
private fun ChatTranscriptLoadingSkeletonRow(configuration: ChatSkeletonRow) {
    when (configuration.role) {
        ChatSkeletonRole.Assistant -> Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 4.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
            horizontalAlignment = Alignment.Start,
        ) {
            configuration.lines.forEach { width ->
                ChatSkeletonLine(width = width)
            }
        }

        ChatSkeletonRole.User -> Box(
            modifier = Modifier
                .fillMaxWidth()
                .padding(start = 48.dp),
            contentAlignment = Alignment.CenterEnd,
        ) {
            Column(
                modifier = Modifier
                    .widthIn(max = configuration.maxLineWidth + 24.dp)
                    .clip(RoundedCornerShape(12.dp))
                    .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.72f))
                    .padding(horizontal = 12.dp, vertical = 10.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
                horizontalAlignment = Alignment.End,
            ) {
                configuration.lines.forEach { width ->
                    ChatSkeletonLine(width = width)
                }
            }
        }
    }
}

@Composable
private fun ChatSkeletonLine(width: Dp) {
    Box(
        modifier = Modifier
            .height(19.dp)
            .width(width)
            .clip(RoundedCornerShape(5.dp))
            .background(MaterialTheme.colorScheme.onSurface.copy(alpha = 0.12f)),
    )
}

private enum class ChatSkeletonRole {
    Assistant,
    User,
}

private data class ChatSkeletonRow(
    val role: ChatSkeletonRole,
    val lines: List<Dp>,
) {
    val maxLineWidth: Dp = lines.maxOrNull() ?: 0.dp
}

private val chatSkeletonRows = listOf(
    ChatSkeletonRow(ChatSkeletonRole.Assistant, listOf(320.dp, 260.dp)),
    ChatSkeletonRow(ChatSkeletonRole.User, listOf(280.dp)),
    ChatSkeletonRow(ChatSkeletonRole.Assistant, listOf(330.dp, 300.dp, 240.dp)),
    ChatSkeletonRow(ChatSkeletonRole.User, listOf(260.dp)),
    ChatSkeletonRow(ChatSkeletonRole.Assistant, listOf(340.dp, 245.dp)),
)

@Composable
private fun LoadOlderMessagesButton(
    isLoading: Boolean,
    onClick: () -> Unit,
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.Center,
    ) {
        TextButton(
            onClick = onClick,
            enabled = !isLoading,
            modifier = Modifier
                .clip(RoundedCornerShape(999.dp))
                .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.48f)),
            contentPadding = PaddingValues(horizontal = 14.dp, vertical = 8.dp),
        ) {
            if (isLoading) {
                CircularProgressIndicator(
                    modifier = Modifier.size(14.dp),
                    strokeWidth = 2.dp,
                )
                Spacer(Modifier.width(8.dp))
            }
            Text(
                if (isLoading) "Loading older messages" else "Load older messages",
                style = MaterialTheme.typography.labelLarge,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}

@Composable
private fun ChatTranscriptErrorState(
    errorMessage: String,
    onRetry: () -> Unit,
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 32.dp)
            .semantics { contentDescription = "Could Not Load Messages" },
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Image(
            painter = painterResource(com.uzairansar.hermex.R.drawable.ic_hermex_exclamation_triangle),
            contentDescription = null,
            modifier = Modifier.size(34.dp),
            colorFilter = ColorFilter.tint(MaterialTheme.colorScheme.error.copy(alpha = 0.82f)),
        )
        Spacer(Modifier.height(12.dp))
        Text(
            "Could Not Load Messages",
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.SemiBold,
            color = MaterialTheme.colorScheme.onSurface,
        )
        Spacer(Modifier.height(6.dp))
        Text(
            errorMessage.ifBlank { "Something went wrong." },
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.secondary,
        )
        Spacer(Modifier.height(14.dp))
        HermexPillButton(
            label = "Try Again",
            onClick = onRetry,
        )
    }
}

@Composable
private fun ChatTranscriptEmptyState() {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 32.dp)
            .semantics { contentDescription = "Send a message to start the conversation." },
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Image(
            painter = painterResource(com.uzairansar.hermex.R.drawable.ic_hermex_chat_bubbles),
            contentDescription = null,
            modifier = Modifier.size(34.dp),
            colorFilter = ColorFilter.tint(MaterialTheme.colorScheme.secondary.copy(alpha = 0.74f)),
        )
        Spacer(Modifier.height(12.dp))
        Text(
            "Send a message to start the conversation.",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.secondary,
        )
    }
}

@Composable
private fun StreamRecoveryStatusPill(label: String) {
    Row(
        modifier = Modifier
            .hermexGlass(shape = CircleShape, castsShadow = false)
            .semantics { contentDescription = label }
            .padding(horizontal = 11.dp, vertical = 7.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        CircularProgressIndicator(
            modifier = Modifier.size(13.dp),
            strokeWidth = 1.7.dp,
            color = MaterialTheme.colorScheme.secondary,
            trackColor = MaterialTheme.colorScheme.secondary.copy(alpha = 0.14f),
            strokeCap = StrokeCap.Round,
        )
        Text(
            text = label,
            style = MaterialTheme.typography.labelMedium,
            fontWeight = FontWeight.SemiBold,
            color = MaterialTheme.colorScheme.secondary,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

@Composable
private fun InlineNotice(
    text: String,
    isError: Boolean = false,
) {
    Text(
        text = text,
        color = if (isError) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.secondary,
        style = MaterialTheme.typography.bodySmall,
        modifier = Modifier
            .fillMaxWidth()
            .hermexGlass(shape = HermexCardShape, castsShadow = false)
            .padding(horizontal = 10.dp, vertical = 8.dp),
    )
}

@Composable
private fun ComposerSurface(
    state: ChatUiState,
    streamingSendBehavior: StreamingSendBehavior,
    primaryActionTintColor: Color?,
    onDraftChange: (String) -> Unit,
    onSend: () -> Unit,
    onStreamingSend: () -> Unit,
    onCancel: () -> Unit,
    onOpenModelPicker: () -> Unit,
    onOpenProfilePicker: () -> Unit,
    onOpenReasoningPicker: () -> Unit,
    onOpenWorkspacePicker: () -> Unit,
    onAttach: () -> Unit,
    onVoice: () -> Unit,
    onCancelVoice: () -> Unit,
    onRemoveAttachment: (UploadResponse) -> Unit,
    loadAttachmentImage: suspend (String) -> ByteArray?,
    loadAttachmentFile: suspend (String) -> FileResponse?,
    onUndo: () -> Unit,
    onRetry: () -> Unit,
    onCompress: () -> Unit,
) {
    var previewAttachment by remember { mutableStateOf<UploadResponse?>(null) }
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .navigationBarsPadding()
            .imePadding()
            .padding(horizontal = 14.dp, vertical = 8.dp),
        verticalArrangement = Arrangement.spacedBy(7.dp),
    ) {
        when {
            state.isRecordingVoiceNote -> ComposerVoiceRecordingStatus(
                startedAtMillis = state.voiceNoteStartedAtMillis,
                onStop = onVoice,
                onCancel = onCancelVoice,
            )
            state.isTranscribingVoiceNote -> ComposerVoiceTranscribingStatus()
        }
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .hermexGlass(shape = HermexGlassShape),
        ) {
            if (state.pendingAttachments.isNotEmpty()) {
                ComposerAttachmentStrip(
                    attachments = state.pendingAttachments,
                    onRemove = onRemoveAttachment,
                    onPreview = { previewAttachment = it },
                    loadAttachmentImage = loadAttachmentImage,
                )
            }
            OutlinedTextField(
                value = state.draft,
                onValueChange = onDraftChange,
                placeholder = { Text(if (state.isViewingCachedData) "Reconnect to send messages." else "Message Hermex") },
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 10.dp, vertical = 2.dp)
                    .semantics { contentDescription = "Message" },
                minLines = 1,
                maxLines = 5,
                enabled = !state.isViewingCachedData,
                shape = HermexCardShape,
                colors = OutlinedTextFieldDefaults.colors(
                    focusedBorderColor = Color.Transparent,
                    unfocusedBorderColor = Color.Transparent,
                    disabledBorderColor = Color.Transparent,
                    focusedContainerColor = Color.Transparent,
                    unfocusedContainerColor = Color.Transparent,
                    disabledContainerColor = Color.Transparent,
                ),
            )
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .horizontalScroll(rememberScrollState())
                    .padding(horizontal = 16.dp)
                    .padding(top = 2.dp, bottom = 8.dp),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                HermexIconButton(
                    label = "Attach",
                    symbol = "+",
                    onClick = onAttach,
                    enabled = !state.isUploadingAttachment && !state.isStreaming && !state.isViewingCachedData,
                )
                HermexSelectorPill(
                    label = state.selectedModel?.label ?: state.selectedModel?.name ?: state.selectedModel?.id ?: "Model",
                    onClick = onOpenModelPicker,
                    enabled = state.modelOptions.isNotEmpty() && !state.isStreaming && !state.isViewingCachedData && !state.isRunningSessionAction,
                    maxWidth = 132.dp,
                    glassed = false,
                    contentPadding = PaddingValues(horizontal = 0.dp, vertical = 14.dp),
                )
                if (state.showsReasoningControl) {
                    HermexSelectorPill(
                        label = ReasoningEffortOption.titleFor(state.selectedReasoning),
                        onClick = onOpenReasoningPicker,
                        enabled = state.reasoningOptions.isNotEmpty() && !state.isStreaming && !state.isViewingCachedData && !state.isRunningSessionAction,
                        leadingIcon = com.uzairansar.hermex.R.drawable.ic_lucide_brain,
                        minWidth = 104.dp,
                        maxWidth = 104.dp,
                        glassed = false,
                        contentPadding = PaddingValues(horizontal = 0.dp, vertical = 14.dp),
                    )
                }
                Spacer(Modifier.width(4.dp))
                HermexIconButton(
                    label = "Voice",
                    symbol = when {
                        state.isTranscribingVoiceNote -> "…"
                        state.isRecordingVoiceNote -> "■"
                        else -> "♪"
                    },
                    onClick = onVoice,
                    enabled = !state.isStreaming && !state.isViewingCachedData && !state.isTranscribingVoiceNote && !state.isRunningSessionAction,
                )
                if (state.isRecordingVoiceNote) {
                    HermexPillButton("Cancel voice", onCancelVoice)
                }
                HermexIconButton(
                    label = if (state.isStreaming) "Stop" else "Send",
                    symbol = if (state.isStreaming) "■" else "↑",
                    onClick = if (state.isStreaming) onCancel else onSend,
                    enabled = if (state.isStreaming) true else state.draft.isNotBlank() && !state.isViewingCachedData && !state.isRunningSessionAction,
                    filled = true,
                    filledContainerColor = hermexPrimaryActionContainerColor(
                        if (state.isStreaming) true else state.draft.isNotBlank() && !state.isViewingCachedData && !state.isRunningSessionAction,
                        primaryActionTintColor,
                    ),
                    filledContentColor = hermexPrimaryActionContentColor(
                        if (state.isStreaming) true else state.draft.isNotBlank() && !state.isViewingCachedData && !state.isRunningSessionAction,
                        primaryActionTintColor,
                    ),
                )
                if (state.isStreaming && state.draft.trimStart().startsWith("/queue", ignoreCase = true)) {
                    HermexPillButton("Queue", onSend, enabled = !state.isRunningSessionAction, filled = true)
                }
                if (state.isStreaming && state.draft.isNotBlank()) {
                    HermexPillButton(
                        streamingSendBehavior.actionLabel,
                        onStreamingSend,
                        enabled = !state.isRunningSessionAction,
                        filled = true,
                    )
                }
            }
        }
        ComposerSecondaryBar(
            state = state,
            onOpenWorkspacePicker = onOpenWorkspacePicker,
            onOpenProfilePicker = onOpenProfilePicker,
        )
        Row(
            modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            val sessionActionEnabled = !state.isStreaming && !state.isViewingCachedData && !state.isRunningSessionAction
            HermexPillButton("Undo", onClick = onUndo, enabled = sessionActionEnabled)
            HermexPillButton("Retry", onClick = onRetry, enabled = sessionActionEnabled)
            HermexPillButton("Compress", onClick = onCompress, enabled = sessionActionEnabled)
        }
    }
    previewAttachment?.let { attachment ->
        AttachmentPreviewSheet(
            attachment = attachment,
            loadAttachmentData = loadAttachmentImage,
            loadAttachmentFile = loadAttachmentFile,
            onDismiss = { previewAttachment = null },
        )
    }
}

@Composable
private fun ComposerVoiceRecordingStatus(
    startedAtMillis: Long?,
    onStop: () -> Unit,
    onCancel: () -> Unit,
) {
    var nowMillis by remember(startedAtMillis) { mutableStateOf(System.currentTimeMillis()) }
    LaunchedEffect(startedAtMillis) {
        while (startedAtMillis != null) {
            nowMillis = System.currentTimeMillis()
            delay(500)
        }
    }
    val elapsedSeconds = (((nowMillis - (startedAtMillis ?: nowMillis)).coerceAtLeast(0L)) / 1000L).toInt()
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 2.dp)
            .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.72f), RoundedCornerShape(12.dp))
            .border(0.5.dp, MaterialTheme.colorScheme.outlineVariant, RoundedCornerShape(12.dp))
            .padding(horizontal = 14.dp, vertical = 10.dp),
        horizontalArrangement = Arrangement.spacedBy(10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            modifier = Modifier
                .size(10.dp)
                .clip(CircleShape)
                .background(MaterialTheme.colorScheme.error),
        )
        Text(
            formatVoiceElapsed(elapsedSeconds),
            style = MaterialTheme.typography.bodyMedium,
            fontFamily = FontFamily.Monospace,
            fontWeight = FontWeight.SemiBold,
        )
        Text(
            "Recording voice note",
            modifier = Modifier.weight(1f),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.secondary,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
        HermexPillButton("Cancel", onCancel, contentPadding = PaddingValues(horizontal = 10.dp, vertical = 6.dp))
        HermexPillButton("Use", onStop, filled = true, contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp))
    }
}

@Composable
private fun ComposerVoiceTranscribingStatus() {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 2.dp)
            .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.72f), RoundedCornerShape(12.dp))
            .border(0.5.dp, MaterialTheme.colorScheme.outlineVariant, RoundedCornerShape(12.dp))
            .padding(horizontal = 14.dp, vertical = 10.dp),
        horizontalArrangement = Arrangement.spacedBy(10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        CircularProgressIndicator(modifier = Modifier.size(16.dp), strokeWidth = 2.dp)
        Text(
            "Transcribing voice note...",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.secondary,
        )
    }
}

private fun formatVoiceElapsed(totalSeconds: Int): String =
    "${totalSeconds / 60}:${(totalSeconds % 60).toString().padStart(2, '0')}"

@Composable
private fun ComposerSecondaryBar(
    state: ChatUiState,
    onOpenWorkspacePicker: () -> Unit,
    onOpenProfilePicker: () -> Unit,
) {
    val showsWorkspace = state.hasWorkspaceChoices
    val showsProfile = state.showsProfileControl
    val contextSnapshot = state.contextWindowSnapshot
    if (!showsWorkspace && !showsProfile && contextSnapshot?.percentage == null) return

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .horizontalScroll(rememberScrollState())
            .padding(bottom = 7.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (showsWorkspace) {
            HermexSelectorPill(
                label = state.workspaceTitle,
                onClick = onOpenWorkspacePicker,
                enabled = !state.isStreaming && !state.isViewingCachedData && !state.isRunningSessionAction,
                leadingIcon = com.uzairansar.hermex.R.drawable.ic_lucide_folder,
                contentPadding = PaddingValues(horizontal = 14.dp, vertical = 8.dp),
            )
        }
        if (showsProfile) {
            HermexSelectorPill(
                label = state.profileTitle,
                onClick = onOpenProfilePicker,
                enabled = !state.isStreaming && !state.isViewingCachedData && !state.isRunningSessionAction,
                leadingIcon = com.uzairansar.hermex.R.drawable.ic_lucide_user_round_cog,
                contentPadding = PaddingValues(horizontal = 14.dp, vertical = 8.dp),
            )
        }
        contextSnapshot?.let {
            ContextWindowIndicator(snapshot = it)
        }
    }
}

@Composable
private fun ContextWindowIndicator(snapshot: ContextWindowSnapshot) {
    val percentage = snapshot.percentage ?: return
    var showsDetails by remember { mutableStateOf(false) }
    val clamped = percentage.coerceIn(0.0, 1.0)
    val trackColor = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.13f)
    val progressColor = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.82f)
    Box(
        modifier = Modifier
            .size(44.dp)
            .clip(CircleShape)
            .clickable { showsDetails = true }
            .hermexGlass(shape = CircleShape, castsShadow = false),
        contentAlignment = Alignment.Center,
    ) {
        Canvas(Modifier.size(30.dp)) {
            drawCircle(
                color = trackColor,
                style = Stroke(width = 3.dp.toPx()),
            )
            drawArc(
                color = progressColor,
                startAngle = -90f,
                sweepAngle = (360f * clamped).toFloat(),
                useCenter = false,
                style = Stroke(width = 3.dp.toPx(), cap = StrokeCap.Round),
            )
        }
        Text(
            text = "${(clamped * 100).toInt()}",
            modifier = Modifier.clickable { showsDetails = true },
            style = MaterialTheme.typography.labelSmall,
            fontWeight = FontWeight.SemiBold,
        )
    }
    if (showsDetails) {
        ContextWindowDetailsSheet(
            snapshot = snapshot,
            onDismiss = { showsDetails = false },
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ContextWindowDetailsSheet(
    snapshot: ContextWindowSnapshot,
    onDismiss: () -> Unit,
) {
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        dragHandle = null,
        containerColor = MaterialTheme.colorScheme.background,
        contentColor = MaterialTheme.colorScheme.onBackground,
        shape = RoundedCornerShape(topStart = 20.dp, topEnd = 20.dp),
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .navigationBarsPadding()
                .padding(start = 18.dp, top = 14.dp, end = 18.dp, bottom = 22.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = "Context Window",
                    modifier = Modifier.weight(1f),
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold,
                )
                TextButton(onClick = onDismiss) {
                    Text("Done")
                }
            }
            Text(
                text = snapshot.tokensLabel(),
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.SemiBold,
            )
            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
            ContextWindowInfoRow("Input", snapshot.inputTokens.formatTokensOrUnavailable())
            ContextWindowInfoRow("Output", snapshot.outputTokens.formatTokensOrUnavailable())
            ContextWindowInfoRow("Threshold", snapshot.thresholdTokens.formatTokensOrUnavailable())
            ContextWindowInfoRow("Cost", snapshot.estimatedCost.formatCostOrUnavailable())
        }
    }
}

@Composable
private fun ContextWindowInfoRow(
    label: String,
    value: String,
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = label,
            modifier = Modifier.weight(1f),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.secondary,
        )
        Text(
            text = value,
            style = MaterialTheme.typography.bodySmall,
            fontWeight = FontWeight.Medium,
        )
    }
}

@Composable
private fun AttachmentOptionsSheet(
    onDismiss: () -> Unit,
    onAttachFile: () -> Unit,
    onPhotos: () -> Unit,
) {
    PickerSheet(
        title = "Attach",
        onDismiss = onDismiss,
        heightFraction = 0.36f,
    ) {
        Column(Modifier.fillMaxSize()) {
            PickerSectionHeader("Attach")
            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
            SelectorRow(
                title = "Attach File",
                subtitle = "Choose from documents",
                selected = false,
                onClick = onAttachFile,
            )
            HorizontalDivider(
                color = MaterialTheme.colorScheme.outlineVariant,
                modifier = Modifier.padding(start = 52.dp),
            )
            SelectorRow(
                title = "Photos",
                subtitle = "Choose images from your library",
                selected = false,
                onClick = onPhotos,
            )
            HorizontalDivider(
                color = MaterialTheme.colorScheme.outlineVariant,
                modifier = Modifier.padding(start = 52.dp),
            )
        }
    }
}

@Composable
private fun ModelPickerDialog(
    models: List<ModelSummary>,
    selected: ModelSummary?,
    favoriteKeys: List<ModelFavoriteKey>,
    recentKeys: List<ModelFavoriteKey>,
    onDismiss: () -> Unit,
    onSelect: (ModelSummary) -> Unit,
    onToggleFavorite: (ModelSummary) -> Unit,
    onDeleteSavedCustom: (ModelSummary) -> Unit,
) {
    var searchText by remember { mutableStateOf("") }
    var customModelId by remember { mutableStateOf("") }
    var customProviderId by remember { mutableStateOf("") }
    var expandedGroupIds by remember { mutableStateOf(emptySet<String>()) }
    var collapsedSearchGroupIds by remember { mutableStateOf(emptySet<String>()) }
    val query = searchText.trim()
    val providerChoices = remember(models, selected) { modelProviderChoices(models, selected) }
    val customOption = remember(customModelId, customProviderId) {
        val modelId = customModelId.trim()
        val providerId = customProviderId.trim().lowercase(Locale.US)
        if (modelId.isNotBlank() && providerId.isNotBlank()) {
            ModelSummary(id = modelId, name = modelId, label = modelId, provider = providerId)
        } else {
            null
        }
    }
    val modelGroups = remember(models, selected, favoriteKeys, recentKeys, query) {
        val catalogGroups = modelCatalogGroups(models)
            .mapNotNull { group ->
                val filteredModels = group.models.filter { model -> model.matchesModelQuery(query) }
                if (filteredModels.isEmpty()) {
                    null
                } else {
                    group.copy(models = filteredModels)
                }
            }
        customModelGroups(
            catalogModels = models,
            selected = selected,
            favoriteKeys = favoriteKeys,
            recentKeys = recentKeys,
            query = query,
        ) + catalogGroups
    }

    LaunchedEffect(query) {
        collapsedSearchGroupIds = emptySet()
    }
    LaunchedEffect(providerChoices, selected) {
        if (customProviderId.isBlank()) {
            customProviderId = selected?.normalizedProvider ?: providerChoices.firstOrNull()?.id.orEmpty()
        }
    }

    PickerSheet(
        title = "Choose Model",
        onDismiss = onDismiss,
    ) {
        Column(Modifier.fillMaxSize()) {
            OutlinedTextField(
                value = searchText,
                onValueChange = { searchText = it },
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 10.dp),
                placeholder = { Text("Search models") },
                singleLine = true,
                shape = HermexCardShape,
            )
            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
            LazyColumn(modifier = Modifier.fillMaxSize()) {
                item("custom-model-entry") {
                    CustomModelEntry(
                        modelId = customModelId,
                        providerId = customProviderId,
                        providerChoices = providerChoices,
                        customOption = customOption,
                        isFavorite = customOption?.favoriteKeyOrNull()?.let { it in favoriteKeys } == true,
                        onModelIdChange = { customModelId = it },
                        onProviderIdChange = { customProviderId = it },
                        onUseCustom = { option -> onSelect(option) },
                        onToggleFavorite = { option -> onToggleFavorite(option) },
                    )
                    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                }
                when {
                    models.isEmpty() && modelGroups.isEmpty() -> {
                        item("empty-models") {
                            EmptyPickerMessage("No models available.")
                        }
                    }
                    modelGroups.isEmpty() -> {
                        item("empty-search") {
                            EmptyPickerMessage("No models match the search.")
                        }
                    }
                    else -> {
                        modelGroups.forEach { group ->
                            val expanded = if (query.isEmpty()) {
                                group.id in expandedGroupIds
                            } else {
                                group.id !in collapsedSearchGroupIds
                            }
                            item("header-${group.id}") {
                                ModelGroupHeader(
                                    group = group,
                                    expanded = expanded,
                                    onToggle = {
                                        if (query.isEmpty()) {
                                            expandedGroupIds = if (expanded) {
                                                expandedGroupIds - group.id
                                            } else {
                                                expandedGroupIds + group.id
                                            }
                                        } else {
                                            collapsedSearchGroupIds = if (expanded) {
                                                collapsedSearchGroupIds + group.id
                                            } else {
                                                collapsedSearchGroupIds - group.id
                                            }
                                        }
                                    },
                                )
                                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                            }
                            if (expanded) {
                                items(
                                    group.models,
                                    key = { model -> "model-${group.id}-${model.favoriteKeyOrNull() ?: model.hashCode()}" },
                                ) { model ->
                                    ModelOptionRow(
                                        model = model,
                                        selected = model.matchesSelection(selected),
                                        isFavorite = model.favoriteKeyOrNull()?.let { it in favoriteKeys } == true,
                                        allowsDelete = group.allowsDelete,
                                        onSelect = { onSelect(model) },
                                        onToggleFavorite = { onToggleFavorite(model) },
                                        onDelete = { onDeleteSavedCustom(model) },
                                    )
                                    HorizontalDivider(
                                        color = MaterialTheme.colorScheme.outlineVariant,
                                        modifier = Modifier.padding(start = 52.dp),
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun CustomModelEntry(
    modelId: String,
    providerId: String,
    providerChoices: List<ModelProviderChoice>,
    customOption: ModelSummary?,
    isFavorite: Boolean,
    onModelIdChange: (String) -> Unit,
    onProviderIdChange: (String) -> Unit,
    onUseCustom: (ModelSummary) -> Unit,
    onToggleFavorite: (ModelSummary) -> Unit,
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Text(
            "Custom Model",
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.secondary,
            fontWeight = FontWeight.SemiBold,
        )
        OutlinedTextField(
            value = modelId,
            onValueChange = onModelIdChange,
            modifier = Modifier.fillMaxWidth(),
            placeholder = { Text("Exact model ID") },
            singleLine = true,
            shape = HermexCardShape,
        )
        OutlinedTextField(
            value = providerId,
            onValueChange = onProviderIdChange,
            modifier = Modifier.fillMaxWidth(),
            placeholder = { Text("Provider ID") },
            singleLine = true,
            shape = HermexCardShape,
        )
        if (providerChoices.isNotEmpty()) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                providerChoices.forEach { provider ->
                    HermexPillButton(
                        label = provider.name,
                        onClick = { onProviderIdChange(provider.id) },
                        filled = provider.id.equals(providerId.trim(), ignoreCase = true),
                        contentPadding = PaddingValues(horizontal = 10.dp, vertical = 6.dp),
                    )
                }
            }
        }
        Row(
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            HermexPillButton(
                label = "Use Custom",
                onClick = { customOption?.let(onUseCustom) },
                enabled = customOption != null,
                filled = true,
                contentPadding = PaddingValues(horizontal = 12.dp, vertical = 7.dp),
                leading = {
                    Text("+", style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.SemiBold)
                    Spacer(Modifier.width(4.dp))
                },
            )
            TextButton(
                onClick = { customOption?.let(onToggleFavorite) },
                enabled = customOption != null,
            ) {
                Text(
                    if (isFavorite) "\u2605" else "\u2606",
                    color = if (isFavorite) Color(0xFFFFB300) else MaterialTheme.colorScheme.secondary,
                    style = MaterialTheme.typography.titleMedium,
                )
            }
        }
    }
}

@Composable
private fun ModelGroupHeader(
    group: ModelPickerGroup,
    expanded: Boolean,
    onToggle: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onToggle)
            .padding(horizontal = 16.dp, vertical = 10.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            if (expanded) "\u2304" else "\u203a",
            modifier = Modifier.width(18.dp),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.secondary,
        )
        Text(
            group.name,
            modifier = Modifier.weight(1f),
            style = MaterialTheme.typography.bodyMedium,
            fontWeight = FontWeight.SemiBold,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
        Text(
            group.models.size.toString(),
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.secondary,
        )
    }
}

@Composable
private fun ModelOptionRow(
    model: ModelSummary,
    selected: Boolean,
    isFavorite: Boolean,
    allowsDelete: Boolean,
    onSelect: () -> Unit,
    onToggleFavorite: () -> Unit,
    onDelete: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(start = 16.dp, end = 8.dp, top = 3.dp, bottom = 3.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Row(
            modifier = Modifier
                .weight(1f)
                .clip(HermexCardShape)
                .clickable(onClick = onSelect)
                .padding(vertical = 8.dp),
            horizontalArrangement = Arrangement.spacedBy(9.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                if (selected) "\u2713" else "",
                modifier = Modifier.width(18.dp),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.primary,
                fontWeight = FontWeight.SemiBold,
            )
            Column(Modifier.weight(1f)) {
                Text(
                    model.displayModelTitle,
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Normal,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                val modelId = model.modelIdentifier
                if (!modelId.isNullOrBlank() && modelId != model.displayModelTitle) {
                    Text(
                        modelId,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.secondary,
                        maxLines = 1,
                        overflow = TextOverflow.MiddleEllipsis,
                    )
                }
                model.normalizedProvider?.let { provider ->
                    Text(
                        provider,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.tertiary,
                        maxLines = 1,
                        overflow = TextOverflow.MiddleEllipsis,
                    )
                }
            }
        }
        TextButton(
            onClick = onToggleFavorite,
            contentPadding = PaddingValues(horizontal = 6.dp, vertical = 4.dp),
        ) {
            Text(
                if (isFavorite) "\u2605" else "\u2606",
                color = if (isFavorite) Color(0xFFFFB300) else MaterialTheme.colorScheme.secondary,
                style = MaterialTheme.typography.titleMedium,
            )
        }
        if (allowsDelete) {
            TextButton(
                onClick = onDelete,
                contentPadding = PaddingValues(horizontal = 6.dp, vertical = 4.dp),
            ) {
                Text(
                    "Delete",
                    color = MaterialTheme.colorScheme.error,
                    style = MaterialTheme.typography.labelMedium,
                )
            }
        }
    }
}

private data class ModelPickerGroup(
    val id: String,
    val name: String,
    val providerId: String?,
    val models: List<ModelSummary>,
    val allowsDelete: Boolean = false,
)

private data class ModelProviderChoice(
    val id: String,
    val name: String,
)

private fun modelCatalogGroups(models: List<ModelSummary>): List<ModelPickerGroup> {
    val grouped = linkedMapOf<String, MutableList<ModelSummary>>()
    val names = linkedMapOf<String, String>()
    val providerIds = linkedMapOf<String, String?>()

    models.forEach { model ->
        val providerId = model.normalizedProvider
        val groupId = providerId?.lowercase(Locale.US) ?: "default-models"
        grouped.getOrPut(groupId) { mutableListOf() } += model
        names.putIfAbsent(groupId, providerId ?: "Model")
        providerIds.putIfAbsent(groupId, providerId)
    }

    return grouped.map { (groupId, groupModels) ->
        ModelPickerGroup(
            id = "catalog-$groupId",
            name = names[groupId].orEmpty().ifBlank { "Model" },
            providerId = providerIds[groupId],
            models = groupModels,
        )
    }
}

private fun customModelGroups(
    catalogModels: List<ModelSummary>,
    selected: ModelSummary?,
    favoriteKeys: List<ModelFavoriteKey>,
    recentKeys: List<ModelFavoriteKey>,
    query: String,
): List<ModelPickerGroup> {
    val catalogKeys = catalogModels.mapNotNull { it.favoriteKeyOrNull() }.toSet()
    val storedCustomModels = (catalogModels.visibleFavoriteModels(favoriteKeys) +
        catalogModels.visibleRecentModels(recentKeys, favoriteKeys))
        .distinctBy { it.favoriteKeyOrNull() }
        .filter { model ->
            val key = model.favoriteKeyOrNull()
            key != null && key !in catalogKeys && model.matchesModelQuery(query)
        }
    val selectedCustom = selected
        ?.takeIf { selectedModel -> catalogModels.none { it.matchesSelection(selectedModel) } }
        ?.takeIf { it.matchesModelQuery(query) }
        ?.takeIf { selectedModel ->
            val selectedKey = selectedModel.favoriteKeyOrNull()
            selectedKey != null && storedCustomModels.none { it.favoriteKeyOrNull() == selectedKey }
        }

    return buildList {
        if (selectedCustom != null) {
            add(
                ModelPickerGroup(
                    id = "current-custom-model",
                    name = "Current Custom",
                    providerId = null,
                    models = listOf(selectedCustom),
                ),
            )
        }
        if (storedCustomModels.isNotEmpty()) {
            add(
                ModelPickerGroup(
                    id = "saved-custom-models",
                    name = "Saved Custom",
                    providerId = null,
                    models = storedCustomModels,
                    allowsDelete = true,
                ),
            )
        }
    }
}

private fun modelProviderChoices(models: List<ModelSummary>, selected: ModelSummary?): List<ModelProviderChoice> {
    val seen = linkedSetOf<String>()
    val choices = mutableListOf<ModelProviderChoice>()

    selected?.normalizedProvider?.let { providerId ->
        if (seen.add(providerId.lowercase(Locale.US))) {
            choices += ModelProviderChoice(id = providerId, name = providerId)
        }
    }
    modelCatalogGroups(models).forEach { group ->
        val providerId = group.providerId ?: return@forEach
        if (seen.add(providerId.lowercase(Locale.US))) {
            choices += ModelProviderChoice(id = providerId, name = group.name)
        }
    }

    return choices
}

private fun ModelSummary.matchesModelQuery(query: String): Boolean {
    if (query.isBlank()) return true
    return listOfNotNull(displayModelTitle, modelIdentifier, normalizedProvider)
        .any { value -> value.contains(query, ignoreCase = true) }
}

@Composable
private fun ProfilePickerDialog(
    profiles: List<ProfileSummary>,
    selected: ProfileSummary?,
    onDismiss: () -> Unit,
    onSelect: (ProfileSummary) -> Unit,
) {
    PickerSheet(
        title = "Choose Profile",
        onDismiss = onDismiss,
    ) {
        Column(Modifier.fillMaxSize()) {
            if (profiles.isEmpty()) {
                EmptyPickerMessage("No profiles available.")
            } else {
                PickerSectionHeader("Profile")
                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                LazyColumn(modifier = Modifier.fillMaxSize()) {
                    items(profiles, key = { it.name ?: it.displayName ?: it.hashCode().toString() }) { profile ->
                        SelectorRow(
                            title = profile.displayTitle,
                            subtitle = profile.modelProviderText,
                            selected = profile == selected,
                            onClick = { onSelect(profile) },
                        )
                        HorizontalDivider(
                            color = MaterialTheme.colorScheme.outlineVariant,
                            modifier = Modifier.padding(start = 52.dp),
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun ReasoningPickerDialog(
    efforts: List<String>,
    selected: String?,
    onDismiss: () -> Unit,
    onSelect: (String) -> Unit,
) {
    PickerSheet(
        title = "Reasoning",
        onDismiss = onDismiss,
    ) {
        Column(Modifier.fillMaxSize()) {
            if (efforts.isEmpty()) {
                EmptyPickerMessage("No reasoning options available.")
            } else {
                PickerSectionHeader("Reasoning")
                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                LazyColumn(modifier = Modifier.fillMaxSize()) {
                    items(efforts, key = { it }) { effort ->
                        SelectorRow(
                            title = ReasoningEffortOption.titleFor(effort),
                            subtitle = null,
                            selected = effort == selected,
                            onClick = { onSelect(effort) },
                        )
                        HorizontalDivider(
                            color = MaterialTheme.colorScheme.outlineVariant,
                            modifier = Modifier.padding(start = 52.dp),
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun WorkspacePickerDialog(
    roots: List<WorkspaceRoot>,
    selected: String?,
    suggestions: List<String>,
    onLoadSuggestions: (String) -> Unit,
    onDismiss: () -> Unit,
    onSelect: (String) -> Unit,
) {
    var prefix by remember { mutableStateOf("") }
    var acceptedWorkspacePath by remember { mutableStateOf<String?>(null) }
    val effectiveSelected = acceptedWorkspacePath ?: selected
    LaunchedEffect(prefix) {
        if (prefix.isNotBlank()) {
            delay(250)
        }
        onLoadSuggestions(prefix)
    }
    val savedRows = remember(roots) {
        roots.mapNotNull { root ->
            val path = root.path?.trim()?.takeIf { it.isNotBlank() } ?: return@mapNotNull null
            WorkspacePickerRow(path = path, name = root.name)
        }.distinctBy { it.path }
    }
    val savedPaths = remember(savedRows) { savedRows.map { it.path }.toSet() }
    val suggestionRows = remember(suggestions, savedPaths, effectiveSelected) {
        suggestions
            .map { it.trim() }
            .filter { it.isNotBlank() && it !in savedPaths && it != effectiveSelected }
            .distinct()
    }
    val selectWorkspace: (String) -> Unit = { path ->
        if (acceptedWorkspacePath == null) {
            acceptedWorkspacePath = path
            onSelect(path)
        }
    }

    PickerSheet(
        title = "Choose Workspace",
        onDismiss = onDismiss,
    ) {
        LazyColumn(
            modifier = Modifier.fillMaxSize(),
        ) {
            item("workspace-input") {
                Column(
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 10.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    OutlinedTextField(
                        value = prefix,
                        onValueChange = { prefix = it },
                        modifier = Modifier.fillMaxWidth(),
                        placeholder = { Text("Workspace path") },
                        singleLine = true,
                        shape = HermexCardShape,
                    )
                    Text(
                        "Suggestions are limited to trusted workspace roots from the server.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.secondary,
                    )
                }
                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
            }
            if (!effectiveSelected.isNullOrBlank()) {
                item("current-header") {
                    PickerSectionHeader("Current")
                    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                }
                item("current-workspace") {
                    SelectorRow(
                        title = "Current Workspace",
                        subtitle = effectiveSelected,
                        selected = true,
                        onClick = { selectWorkspace(effectiveSelected) },
                    )
                    HorizontalDivider(
                        color = MaterialTheme.colorScheme.outlineVariant,
                        modifier = Modifier.padding(start = 52.dp),
                    )
                }
            }
            if (savedRows.isNotEmpty()) {
                item("saved-header") {
                    PickerSectionHeader("Saved Workspaces")
                    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                }
                items(savedRows, key = { "saved-${it.path}" }) { row ->
                    SelectorRow(
                        title = row.displayTitle,
                        subtitle = row.path,
                        selected = row.path == effectiveSelected,
                        onClick = { selectWorkspace(row.path) },
                    )
                    HorizontalDivider(
                        color = MaterialTheme.colorScheme.outlineVariant,
                        modifier = Modifier.padding(start = 52.dp),
                    )
                }
            }
            if (suggestionRows.isNotEmpty()) {
                item("suggestions-header") {
                    PickerSectionHeader("Suggestions")
                    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                }
                items(suggestionRows, key = { "suggestion-$it" }) { path ->
                    SelectorRow(
                        title = path.lastPathComponentFallback(),
                        subtitle = path,
                        selected = path == effectiveSelected,
                        onClick = { selectWorkspace(path) },
                    )
                    HorizontalDivider(
                        color = MaterialTheme.colorScheme.outlineVariant,
                        modifier = Modifier.padding(start = 52.dp),
                    )
                }
            }
            if (savedRows.isEmpty() && suggestionRows.isEmpty()) {
                item("empty-workspaces") {
                    EmptyPickerMessage("Try typing a path under your home folder or an existing workspace root.")
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun PickerSheet(
    title: String,
    onDismiss: () -> Unit,
    heightFraction: Float = 0.86f,
    content: @Composable () -> Unit,
) {
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        dragHandle = null,
        containerColor = MaterialTheme.colorScheme.background,
        contentColor = MaterialTheme.colorScheme.onBackground,
        shape = RoundedCornerShape(topStart = 20.dp, topEnd = 20.dp),
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .fillMaxHeight(heightFraction)
                .navigationBarsPadding(),
        ) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(start = 16.dp, top = 10.dp, end = 8.dp, bottom = 8.dp),
            ) {
                Text(
                    title,
                    modifier = Modifier.align(Alignment.Center),
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                TextButton(
                    onClick = onDismiss,
                    modifier = Modifier.align(Alignment.CenterEnd),
                ) {
                    Text("Done")
                }
            }
            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
            content()
        }
    }
}

@Composable
private fun PickerSectionHeader(title: String) {
    Text(
        title,
        modifier = Modifier
            .fillMaxWidth()
            .padding(start = 16.dp, top = 16.dp, end = 16.dp, bottom = 6.dp),
        style = MaterialTheme.typography.labelSmall,
        color = MaterialTheme.colorScheme.secondary,
        fontWeight = FontWeight.SemiBold,
    )
}

@Composable
private fun EmptyPickerMessage(text: String) {
    Text(
        text,
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 24.dp),
        style = MaterialTheme.typography.bodyMedium,
        color = MaterialTheme.colorScheme.secondary,
    )
}

private data class WorkspacePickerRow(
    val path: String,
    val name: String?,
)

@Composable
private fun SelectorRow(
    title: String,
    subtitle: String?,
    selected: Boolean,
    onClick: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(HermexCardShape)
            .clickable(onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 11.dp),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            if (selected) "\u2713" else "",
            modifier = Modifier.width(24.dp),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.primary,
            fontWeight = FontWeight.SemiBold,
        )
        Column(Modifier.weight(1f)) {
            Text(
                title,
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Normal,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            if (!subtitle.isNullOrBlank()) {
                Text(
                    subtitle,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.secondary,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
    }
}

@Composable
private fun ApprovalCard(
    approval: PendingApproval,
    count: Int,
    isResponding: Boolean,
    onChoice: (ApprovalChoice) -> Unit,
    onSkipAll: () -> Unit,
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .hermexGlass(shape = HermexCardShape, castsShadow = false)
            .padding(12.dp),
    ) {
        Text("Approval required", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
        if (count > 1) Text("Pending approvals: $count", style = MaterialTheme.typography.bodySmall)
        Text(
            approval.command ?: approval.description ?: "The agent wants to run an action.",
            style = MaterialTheme.typography.bodyMedium,
            modifier = Modifier.padding(top = 6.dp),
        )
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .horizontalScroll(rememberScrollState())
                .padding(top = 8.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            HermexPillButton("Allow once", { onChoice(ApprovalChoice.Once) }, enabled = !isResponding, filled = true)
            HermexPillButton("Session", { onChoice(ApprovalChoice.Session) }, enabled = !isResponding)
            HermexPillButton("Always", { onChoice(ApprovalChoice.Always) }, enabled = !isResponding)
            HermexPillButton("Deny", { onChoice(ApprovalChoice.Deny) }, enabled = !isResponding)
        }
        HermexPillButton(
            "Skip all this session",
            onSkipAll,
            enabled = !isResponding,
            modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
        )
    }
}

@Composable
private fun ApprovalBypassStatusPill() {
    Row(
        modifier = Modifier
            .hermexGlass(shape = CircleShape, castsShadow = false)
            .padding(horizontal = 12.dp, vertical = 8.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text("Approval bypass active", style = MaterialTheme.typography.bodySmall, fontWeight = FontWeight.SemiBold)
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
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .hermexGlass(shape = HermexCardShape, castsShadow = false)
            .padding(12.dp),
    ) {
        Text("Clarification needed", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
        if (count > 1) Text("Pending prompts: $count", style = MaterialTheme.typography.bodySmall)
        Text(clarification.displayQuestion, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.padding(top = 6.dp))
        if (clarification.displayChoices.isNotEmpty()) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .horizontalScroll(rememberScrollState())
                    .padding(top = 8.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                clarification.displayChoices.forEach { choice ->
                    HermexPillButton(choice, { onChoice(choice) }, enabled = !isResponding)
                }
            }
        }
        OutlinedTextField(
            value = draft,
            onValueChange = onDraftChange,
            modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
            placeholder = { Text("Response") },
            minLines = 1,
            maxLines = 4,
            enabled = !isResponding,
            shape = HermexCardShape,
        )
        HermexPillButton(
            label = "Submit",
            onClick = onSubmit,
            enabled = draft.isNotBlank() && !isResponding,
            filled = true,
            modifier = Modifier.padding(top = 8.dp),
        )
    }
}

@Composable
private fun MessageRow(
    message: ChatMessage,
    isStreamingMessage: Boolean,
    showThinkingAndToolCards: Boolean,
    thinkingCardsStartExpanded: Boolean,
    toolCardsStartExpanded: Boolean,
    hidesAttachmentPaths: Boolean,
    showsAssistantTurnTimestamps: Boolean,
    wrapsCodeBlockLines: Boolean,
    streamedTextAnimationEnabled: Boolean,
    loadTranscriptMediaImage: suspend (TranscriptMediaReference) -> ByteArray?,
    loadAttachmentFile: suspend (String) -> FileResponse?,
    actionContext: MessageActionContext?,
    messageActionEnabled: Boolean,
    isRegeneratingMessage: Boolean,
    isEditingMessage: Boolean,
    isForkingMessage: Boolean,
    onCopy: () -> Unit,
    onListen: () -> Unit,
    onSelectText: (MessageActionContext) -> Unit,
    onEdit: (MessageActionContext) -> Unit,
    onRegenerate: (MessageActionContext) -> Unit,
    onFork: (MessageActionContext) -> Unit,
) {
    val visibleText = message.visibleDisplayText(hidesAttachmentPaths)
    val attachments = message.displayAttachments
    val linkPreviewUrl = remember(message.content, message.role, isStreamingMessage) {
        TranscriptLinkPreviewEligibility.previewUrlFor(message, isStreamingMessage)
    }
    var previewAttachment by remember { mutableStateOf<MessageAttachment?>(null) }
    var previewTranscriptMedia by remember { mutableStateOf<TranscriptMediaReference?>(null) }
    var showsMessageActions by remember { mutableStateOf(false) }
    message.markerKind?.let { markerKind ->
        MarkerMessageCard(
            kind = markerKind,
            content = message.visibleDisplayText(hidesAttachmentPaths),
        )
        return
    }
    when (message.role) {
        "user" -> {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 2.dp),
                horizontalAlignment = Alignment.End,
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                if (attachments.isNotEmpty()) {
                    MessageAttachmentGrid(
                        attachments = attachments,
                        onPreview = { previewAttachment = it },
                        loadAttachmentImage = { path -> loadTranscriptMediaImage(TranscriptMediaReference(path)) },
                    )
                }
                if (visibleText.isNotBlank() || attachments.isEmpty()) {
                    UserMessageBubble(
                        text = visibleText.ifBlank { "(empty)" },
                        onShowActions = { showsMessageActions = true },
                    )
                }
                linkPreviewUrl?.let { url ->
                    TranscriptLinkPreviewCard(url = url)
                }
            }
        }

        "local_notice" -> LocalStatusMessageRow(
            text = visibleText.ifBlank { " " },
            symbol = "\u2713",
            accentColor = Color(0xFF34C759),
        )

        "local_assistant" -> LocalStatusMessageRow(
            text = visibleText.ifBlank { " " },
            symbol = "\u2318",
            accentColor = MaterialTheme.colorScheme.primary,
        )

        else -> AssistantMessageRow(
            visibleText = visibleText,
            attachments = attachments,
            reasoningTexts = message.reasoningTexts,
            tools = message.toolCalls.orEmpty(),
            timestamp = message.timestamp,
            isStreamingMessage = isStreamingMessage,
            showThinkingAndToolCards = showThinkingAndToolCards,
            thinkingCardsStartExpanded = thinkingCardsStartExpanded,
            toolCardsStartExpanded = toolCardsStartExpanded,
            showsAssistantTurnTimestamp = showsAssistantTurnTimestamps,
            wrapsCodeBlockLines = wrapsCodeBlockLines,
            streamedTextAnimationEnabled = streamedTextAnimationEnabled,
            linkPreviewUrl = linkPreviewUrl,
            loadTranscriptMediaImage = loadTranscriptMediaImage,
            onPreviewAttachment = { previewAttachment = it },
            onPreviewTranscriptMedia = { previewTranscriptMedia = it },
            onShowActions = { showsMessageActions = true },
        )
    }
    previewAttachment?.let { attachment ->
        MessageAttachmentPreviewSheet(
            attachment = attachment,
            loadAttachmentData = { path -> loadTranscriptMediaImage(TranscriptMediaReference(path)) },
            loadAttachmentFile = loadAttachmentFile,
            onDismiss = { previewAttachment = null },
        )
    }
    previewTranscriptMedia?.let { reference ->
        TranscriptMediaPreviewSheet(
            reference = reference,
            loadMediaImage = loadTranscriptMediaImage,
            onDismiss = { previewTranscriptMedia = null },
        )
    }
    if (showsMessageActions && actionContext != null) {
        MessageActionSheet(
            context = actionContext,
            messageActionEnabled = messageActionEnabled,
            isRegeneratingMessage = isRegeneratingMessage,
            isEditingMessage = isEditingMessage,
            isForkingMessage = isForkingMessage,
            onCopy = {
                showsMessageActions = false
                onCopy()
            },
            onListen = {
                showsMessageActions = false
                onListen()
            },
            onSelectText = {
                showsMessageActions = false
                onSelectText(actionContext)
            },
            onEdit = {
                showsMessageActions = false
                onEdit(actionContext)
            },
            onRegenerate = {
                showsMessageActions = false
                onRegenerate(actionContext)
            },
            onFork = {
                showsMessageActions = false
                onFork(actionContext)
            },
            onDismiss = { showsMessageActions = false },
        )
    }
}

@Composable
private fun DiscardLaterMessagesDialog(
    message: String,
    confirmLabel: String,
    onDismiss: () -> Unit,
    onConfirm: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Discard Later Messages?") },
        text = { Text(message) },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancel")
            }
        },
        confirmButton = {
            TextButton(onClick = onConfirm) {
                Text(confirmLabel, color = MaterialTheme.colorScheme.error)
            }
        },
    )
}

@Composable
private fun ClearConversationDialog(
    onDismiss: () -> Unit,
    onConfirm: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Clear conversation") },
        text = { Text("Clear all messages? This cannot be undone.") },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancel")
            }
        },
        confirmButton = {
            TextButton(onClick = onConfirm) {
                Text("Clear", color = MaterialTheme.colorScheme.error)
            }
        },
    )
}

@Composable
private fun SelectableMessageTextSheet(
    text: String,
    onDismiss: () -> Unit,
) {
    PickerSheet(
        title = "Select Text",
        onDismiss = onDismiss,
        heightFraction = 0.86f,
    ) {
        val scrollState = rememberScrollState()
        SelectionContainer {
            Text(
                text = text.ifBlank { " " },
                modifier = Modifier
                    .fillMaxSize()
                    .verticalScroll(scrollState)
                    .padding(18.dp),
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onBackground,
            )
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun EditMessageSheet(
    draft: String,
    onDraftChange: (String) -> Unit,
    onDismiss: () -> Unit,
    onSubmit: () -> Unit,
) {
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        dragHandle = null,
        containerColor = MaterialTheme.colorScheme.background,
        contentColor = MaterialTheme.colorScheme.onBackground,
        shape = RoundedCornerShape(topStart = 20.dp, topEnd = 20.dp),
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .fillMaxHeight(0.64f)
                .navigationBarsPadding(),
        ) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(start = 8.dp, top = 10.dp, end = 8.dp, bottom = 8.dp),
            ) {
                TextButton(
                    onClick = onDismiss,
                    modifier = Modifier.align(Alignment.CenterStart),
                ) {
                    Text("Cancel")
                }
                Text(
                    "Edit Message",
                    modifier = Modifier.align(Alignment.Center),
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                TextButton(
                    onClick = onSubmit,
                    enabled = draft.trim().isNotEmpty(),
                    modifier = Modifier.align(Alignment.CenterEnd),
                ) {
                    Text("Send")
                }
            }
            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
            OutlinedTextField(
                value = draft,
                onValueChange = onDraftChange,
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f)
                    .padding(16.dp),
                textStyle = MaterialTheme.typography.bodyLarge,
                minLines = 8,
                colors = OutlinedTextFieldDefaults.colors(
                    focusedContainerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f),
                    unfocusedContainerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f),
                    focusedBorderColor = MaterialTheme.colorScheme.primary,
                    unfocusedBorderColor = MaterialTheme.colorScheme.outlineVariant,
                ),
            )
        }
    }
}

@Composable
private fun AssistantMessageRow(
    visibleText: String,
    attachments: List<MessageAttachment>,
    reasoningTexts: List<String>,
    tools: List<ToolCall>,
    timestamp: Double?,
    isStreamingMessage: Boolean,
    showThinkingAndToolCards: Boolean,
    thinkingCardsStartExpanded: Boolean,
    toolCardsStartExpanded: Boolean,
    showsAssistantTurnTimestamp: Boolean,
    wrapsCodeBlockLines: Boolean,
    streamedTextAnimationEnabled: Boolean,
    linkPreviewUrl: HttpUrl?,
    loadTranscriptMediaImage: suspend (TranscriptMediaReference) -> ByteArray?,
    onPreviewAttachment: (MessageAttachment) -> Unit,
    onPreviewTranscriptMedia: (TranscriptMediaReference) -> Unit,
    onShowActions: () -> Unit,
) {
    val hasHiddenCards = !showThinkingAndToolCards && (reasoningTexts.isNotEmpty() || tools.isNotEmpty())
    if (visibleText.isBlank() && attachments.isEmpty() && linkPreviewUrl == null && hasHiddenCards) return
    val transcriptMediaSegments = remember(visibleText) { TranscriptMediaParser.segments(visibleText) }
    val containsTranscriptMedia = transcriptMediaSegments.any { it is TranscriptMediaSegment.Media }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .messageActionsGesture(
                enabled = visibleText.isNotBlank(),
                onLongPress = onShowActions,
            )
            .padding(horizontal = 2.dp, vertical = 4.dp),
        verticalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        if (showThinkingAndToolCards) {
            reasoningTexts.forEach { reasoningText ->
                ReasoningAccessoryCard(
                    text = reasoningText,
                    startsExpanded = thinkingCardsStartExpanded,
                )
            }
            if (tools.isNotEmpty()) {
                ToolActivityCard(
                    tools = tools,
                    startsExpanded = toolCardsStartExpanded,
                )
            }
        }
        if (visibleText.isNotBlank()) {
            if (showsAssistantTurnTimestamp) {
                AssistantTurnHeader(timestamp = timestamp)
            }
            if (containsTranscriptMedia) {
                TranscriptMediaContentView(
                    segments = transcriptMediaSegments,
                    loadMediaImage = loadTranscriptMediaImage,
                    onPreviewMedia = onPreviewTranscriptMedia,
                    wrapsCodeBlockLines = wrapsCodeBlockLines,
                    isStreaming = isStreamingMessage,
                    streamedTextAnimationEnabled = streamedTextAnimationEnabled,
                )
            } else {
                MarkdownText(
                    markdown = visibleText,
                    wrapsCodeBlockLines = wrapsCodeBlockLines,
                    isStreaming = isStreamingMessage,
                    streamedTextAnimationEnabled = streamedTextAnimationEnabled,
                )
            }
        } else if (attachments.isEmpty() && reasoningTexts.isEmpty() && tools.isEmpty()) {
            MarkdownText("(empty)")
        }
        linkPreviewUrl?.let { url ->
            TranscriptLinkPreviewCard(url = url)
        }
        if (attachments.isNotEmpty()) {
            MessageAttachmentGrid(
                attachments = attachments,
                onPreview = onPreviewAttachment,
                loadAttachmentImage = { path -> loadTranscriptMediaImage(TranscriptMediaReference(path)) },
                alignEnd = false,
            )
        }
    }
}

@Composable
private fun TranscriptMediaContentView(
    segments: List<TranscriptMediaSegment>,
    loadMediaImage: suspend (TranscriptMediaReference) -> ByteArray?,
    onPreviewMedia: (TranscriptMediaReference) -> Unit,
    wrapsCodeBlockLines: Boolean,
    isStreaming: Boolean,
    streamedTextAnimationEnabled: Boolean,
) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        segments.forEach { segment ->
            when (segment) {
                is TranscriptMediaSegment.Text -> {
                    if (segment.text.isNotBlank()) {
                        MarkdownText(
                            markdown = segment.text,
                            wrapsCodeBlockLines = wrapsCodeBlockLines,
                            isStreaming = isStreaming,
                            streamedTextAnimationEnabled = streamedTextAnimationEnabled,
                        )
                    }
                }
                is TranscriptMediaSegment.Media -> {
                    CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Ltr) {
                        TranscriptMediaThumbnailView(
                            reference = segment.reference,
                            loadMediaImage = loadMediaImage,
                            onPreviewMedia = onPreviewMedia,
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun TranscriptMediaThumbnailView(
    reference: TranscriptMediaReference,
    loadMediaImage: suspend (TranscriptMediaReference) -> ByteArray?,
    onPreviewMedia: (TranscriptMediaReference) -> Unit,
) {
    if (!reference.isRasterImageCandidate) {
        TranscriptMediaUnavailableChip(reference = reference)
        return
    }

    var bytes by remember(reference.id) { mutableStateOf<ByteArray?>(null) }
    var didAttemptLoad by remember(reference.id) { mutableStateOf(false) }
    LaunchedEffect(reference.id) {
        didAttemptLoad = false
        bytes = loadMediaImage(reference)
        didAttemptLoad = true
    }
    val bitmap = remember(bytes) {
        bytes?.let { BitmapFactory.decodeByteArray(it, 0, it.size) }
    }

    val shape = RoundedCornerShape(10.dp)
    when {
        bitmap != null -> {
            Image(
                bitmap = bitmap.asImageBitmap(),
                contentDescription = "Open media image ${reference.displayName}",
                contentScale = ContentScale.Crop,
                modifier = Modifier
                    .size(width = 210.dp, height = 132.dp)
                    .clip(shape)
                    .clickable { onPreviewMedia(reference) }
                    .border(0.5.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.55f), shape),
            )
        }
        didAttemptLoad -> TranscriptMediaUnavailableChip(reference = reference)
        else -> {
            Box(
                modifier = Modifier
                    .size(width = 210.dp, height = 132.dp)
                    .clip(shape)
                    .background(MaterialTheme.colorScheme.surfaceVariant)
                    .border(0.5.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.55f), shape),
                contentAlignment = Alignment.Center,
            ) {
                CircularProgressIndicator(strokeWidth = 2.dp, modifier = Modifier.size(22.dp))
            }
        }
    }
}

@Composable
private fun TranscriptMediaUnavailableChip(reference: TranscriptMediaReference) {
    val shape = RoundedCornerShape(8.dp)
    Row(
        modifier = Modifier
            .widthIn(max = 240.dp)
            .clip(shape)
            .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.72f))
            .border(0.5.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.55f), shape)
            .padding(horizontal = 10.dp, vertical = 8.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            if (reference.isRasterImageCandidate) "IMG" else "FILE",
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.secondary,
            fontWeight = FontWeight.SemiBold,
        )
        Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text(
                reference.displayName,
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurface,
                fontWeight = FontWeight.SemiBold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                "Media unavailable",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
            )
        }
    }
}

@Composable
private fun TranscriptMediaPreviewSheet(
    reference: TranscriptMediaReference,
    loadMediaImage: suspend (TranscriptMediaReference) -> ByteArray?,
    onDismiss: () -> Unit,
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var bytes by remember(reference.id) { mutableStateOf<ByteArray?>(null) }
    var didAttemptLoad by remember(reference.id) { mutableStateOf(false) }
    var isSaving by remember(reference.id) { mutableStateOf(false) }
    var saveMessage by remember(reference.id) { mutableStateOf<String?>(null) }
    LaunchedEffect(reference.id) {
        didAttemptLoad = false
        bytes = loadMediaImage(reference)
        didAttemptLoad = true
    }
    val bitmap = remember(bytes) {
        bytes?.let { BitmapFactory.decodeByteArray(it, 0, it.size) }
    }

    PickerSheet(
        title = reference.displayName,
        onDismiss = onDismiss,
        heightFraction = 0.72f,
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(18.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            when {
                bitmap != null -> {
                    Image(
                        bitmap = bitmap.asImageBitmap(),
                        contentDescription = reference.displayName,
                        contentScale = ContentScale.Fit,
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(420.dp)
                            .clip(RoundedCornerShape(12.dp))
                            .background(MaterialTheme.colorScheme.surfaceVariant),
                    )
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.End,
                    ) {
                        HermexPillButton(
                            label = if (isSaving) "Saving" else "Save",
                            onClick = {
                                val imageBytes = bytes ?: return@HermexPillButton
                                scope.launch {
                                    isSaving = true
                                    saveMessage = withContext(Dispatchers.IO) {
                                        saveTranscriptMediaImageToGallery(context, reference, imageBytes)
                                    }
                                    isSaving = false
                                }
                            },
                            enabled = !isSaving,
                            filled = true,
                        )
                    }
                }
                didAttemptLoad -> TranscriptMediaUnavailableChip(reference = reference)
                else -> {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(220.dp),
                        contentAlignment = Alignment.Center,
                    ) {
                        CircularProgressIndicator(strokeWidth = 2.dp)
                    }
                }
            }
            saveMessage?.let { message ->
                Text(
                    message,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.secondary,
                )
            }
            AttachmentInfoRow("Reference", reference.rawReference)
        }
    }
}

private fun saveTranscriptMediaImageToGallery(
    context: Context,
    reference: TranscriptMediaReference,
    bytes: ByteArray,
): String {
    val resolver = context.contentResolver
    val fileName = reference.galleryFilename()
    val values = ContentValues().apply {
        put(MediaStore.MediaColumns.DISPLAY_NAME, fileName)
        put(MediaStore.MediaColumns.MIME_TYPE, fileName.galleryMimeType())
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            put(MediaStore.MediaColumns.RELATIVE_PATH, "Pictures/Hermex")
            put(MediaStore.MediaColumns.IS_PENDING, 1)
        }
    }
    val uri = resolver.insert(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, values)
        ?: return "Could not save image."

    return try {
        resolver.openOutputStream(uri)?.use { output ->
            output.write(bytes)
        } ?: error("Could not open gallery item.")
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

private fun TranscriptMediaReference.galleryFilename(): String {
    val rawName = displayName.trim().takeIf { it.isNotBlank() } ?: "hermex-media"
    val extension = rawName.substringAfterLast('.', missingDelimiterValue = "")
        .lowercase(Locale.US)
        .takeIf { it in galleryImageExtensions }
        ?: "png"
    val stem = rawName
        .substringBeforeLast('.', missingDelimiterValue = rawName)
        .replace(Regex("[^A-Za-z0-9._-]+"), "_")
        .trim('.', '_', '-')
        .take(80)
        .ifBlank { "hermex-media" }
    return "$stem.$extension"
}

private fun String.galleryMimeType(): String =
    when (substringAfterLast('.', missingDelimiterValue = "").lowercase(Locale.US)) {
        "bmp" -> "image/bmp"
        "gif" -> "image/gif"
        "heic" -> "image/heic"
        "heif" -> "image/heif"
        "jpg", "jpeg" -> "image/jpeg"
        "png" -> "image/png"
        "tif", "tiff" -> "image/tiff"
        "webp" -> "image/webp"
        else -> "image/png"
    }

private val galleryImageExtensions = setOf("bmp", "gif", "heic", "heif", "jpg", "jpeg", "png", "tif", "tiff", "webp")

@Composable
private fun TranscriptLinkPreviewCard(
    url: HttpUrl,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    val shape = RoundedCornerShape(12.dp)
    Row(
        modifier = modifier
            .widthIn(max = 300.dp)
            .clip(shape)
            .clickable {
                runCatching {
                    context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url.toString())))
                }
            }
            .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.7f))
            .border(0.5.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.6f), shape)
            .padding(10.dp),
        horizontalArrangement = Arrangement.spacedBy(10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            modifier = Modifier
                .width(3.dp)
                .height(42.dp)
                .clip(RoundedCornerShape(100.dp))
                .background(MaterialTheme.colorScheme.primary),
        )
        Column(
            modifier = Modifier.weight(1f),
            verticalArrangement = Arrangement.spacedBy(3.dp),
        ) {
            Text(
                url.host,
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurface,
                fontWeight = FontWeight.SemiBold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                url.transcriptPreviewDisplayText(),
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
        }
        Text(
            ">",
            style = MaterialTheme.typography.titleSmall,
            color = MaterialTheme.colorScheme.primary,
            fontWeight = FontWeight.Bold,
        )
    }
}

@Composable
private fun AssistantTurnHeader(timestamp: Double?) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .semantics { contentDescription = "Assistant response timestamp" },
        horizontalArrangement = Arrangement.spacedBy(5.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            "\u2726",
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.primary,
            fontWeight = FontWeight.SemiBold,
        )
        timestamp.shortTimeText()?.let { time ->
            Text(
                time,
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.secondary,
                fontWeight = FontWeight.Medium,
            )
        }
    }
}

@Composable
private fun LocalStatusMessageRow(
    text: String,
    symbol: String,
    accentColor: Color,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp)
            .hermexGlass(shape = RoundedCornerShape(16.dp), castsShadow = false)
            .padding(horizontal = 12.dp, vertical = 12.dp),
        horizontalArrangement = Arrangement.spacedBy(10.dp),
        verticalAlignment = Alignment.Top,
    ) {
        Box(
            modifier = Modifier
                .size(28.dp)
                .clip(CircleShape)
                .background(accentColor.copy(alpha = 0.14f)),
            contentAlignment = Alignment.Center,
        ) {
            Text(
                symbol,
                style = MaterialTheme.typography.labelMedium,
                color = accentColor,
                fontWeight = FontWeight.SemiBold,
            )
        }
        Box(Modifier.weight(1f)) {
            MarkdownText(text)
        }
    }
}

@Composable
private fun MarkerMessageCard(
    kind: ChatMarkerKind,
    content: String,
) {
    val cardBody = markerCardBody(kind, content)
    val summary = markerSummary(kind, cardBody)
    var expanded by remember { mutableStateOf(false) }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(10.dp))
            .background(MaterialTheme.colorScheme.surface.copy(alpha = 0.68f))
            .border(0.5.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.62f), RoundedCornerShape(10.dp))
            .padding(horizontal = 10.dp, vertical = 9.dp),
        verticalArrangement = Arrangement.spacedBy(if (expanded) 8.dp else 0.dp),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(8.dp))
                .clickable { expanded = !expanded },
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                kind.symbol,
                modifier = Modifier.width(18.dp),
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.secondary,
                fontWeight = FontWeight.SemiBold,
            )
            Text(
                kind.title,
                style = MaterialTheme.typography.labelSmall,
                fontWeight = FontWeight.SemiBold,
                maxLines = 1,
            )
            Text(
                summary,
                modifier = Modifier.weight(1f),
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.secondary,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                if (expanded) "\u2303" else "\u2304",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.secondary,
                fontWeight = FontWeight.SemiBold,
            )
        }
        if (expanded) {
            SelectionContainer {
                Text(
                    cardBody.ifBlank { kind.title },
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurface,
                )
            }
        }
    }
}

@Composable
private fun CompressionReferenceMarkerCard(card: CompressionReferenceCard) {
    MarkerMessageCard(
        kind = ChatMarkerKind.CompressionReference,
        content = card.referenceText,
    )
}

@Composable
private fun ReasoningAccessoryCard(
    text: String,
    startsExpanded: Boolean = false,
) {
    val trimmed = text.trim()
    if (trimmed.isEmpty()) return
    var userToggledExpansion by remember { mutableStateOf<Boolean?>(null) }
    val expanded = userToggledExpansion ?: startsExpanded
    val summary = trimmed
        .replace('\n', ' ')
        .trim()
        .let { if (it.length <= 80) it else "${it.take(80)}..." }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(10.dp))
            .background(MaterialTheme.colorScheme.surface.copy(alpha = 0.68f))
            .border(0.5.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.62f), RoundedCornerShape(10.dp))
            .padding(horizontal = 10.dp, vertical = 9.dp),
        verticalArrangement = Arrangement.spacedBy(if (expanded) 8.dp else 0.dp),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(8.dp))
                .clickable { userToggledExpansion = !expanded },
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Image(
                painter = painterResource(com.uzairansar.hermex.R.drawable.ic_lucide_brain),
                contentDescription = null,
                modifier = Modifier.size(18.dp),
                colorFilter = ColorFilter.tint(MaterialTheme.colorScheme.secondary),
            )
            Text(
                "Thinking",
                style = MaterialTheme.typography.labelSmall,
                fontWeight = FontWeight.SemiBold,
                maxLines = 1,
            )
            Text(
                summary,
                modifier = Modifier.weight(1f),
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.secondary,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                if (expanded) "\u2303" else "\u2304",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.secondary,
                fontWeight = FontWeight.SemiBold,
            )
        }
        if (expanded) {
            SelectionContainer {
                Text(
                    trimmed,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurface,
                )
            }
        }
    }
}

@Composable
private fun LiveToolActivityCard(
    activity: String,
    startsExpanded: Boolean = false,
) {
    val trimmed = activity.trim()
    if (trimmed.isEmpty()) return
    var userToggledExpansion by remember { mutableStateOf<Boolean?>(null) }
    val expanded = userToggledExpansion ?: startsExpanded
    val accentColor = MaterialTheme.colorScheme.secondary

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(10.dp))
            .background(MaterialTheme.colorScheme.surface.copy(alpha = 0.68f))
            .border(0.5.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.62f), RoundedCornerShape(10.dp))
            .padding(horizontal = 10.dp, vertical = 9.dp),
        verticalArrangement = Arrangement.spacedBy(if (expanded) 8.dp else 0.dp),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(8.dp))
                .clickable { userToggledExpansion = !expanded },
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Image(
                painter = painterResource(com.uzairansar.hermex.R.drawable.ic_lucide_hammer),
                contentDescription = null,
                modifier = Modifier.size(18.dp),
                colorFilter = ColorFilter.tint(accentColor),
            )
            Text(
                "Tool",
                style = MaterialTheme.typography.labelSmall,
                fontWeight = FontWeight.SemiBold,
                maxLines = 1,
            )
            Text(
                trimmed,
                modifier = Modifier.weight(1f),
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.secondary,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            TranscriptStatusPill(text = "Running", color = accentColor)
            Text(
                if (expanded) "\u2303" else "\u2304",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.secondary,
                fontWeight = FontWeight.SemiBold,
            )
        }
        if (expanded) {
            ToolDetailSection(title = "Activity", value = trimmed)
        }
    }
}

@Composable
private fun AssistantTypingIndicator() {
    val transition = rememberInfiniteTransition(label = "assistant-typing")
    val scale by transition.animateFloat(
        initialValue = 0.86f,
        targetValue = 1.16f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 850),
            repeatMode = RepeatMode.Reverse,
        ),
        label = "assistant-typing-scale",
    )
    val opacity by transition.animateFloat(
        initialValue = 0.55f,
        targetValue = 0.95f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 850),
            repeatMode = RepeatMode.Reverse,
        ),
        label = "assistant-typing-opacity",
    )
    val dotColor = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.78f)
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .semantics { contentDescription = "Hermex is preparing a response" },
        contentAlignment = Alignment.CenterStart,
    ) {
        Box(
            modifier = Modifier
                .padding(start = 4.dp, top = 8.dp, bottom = 8.dp)
                .size(16.dp)
                .graphicsLayer {
                    scaleX = scale
                    scaleY = scale
                    alpha = opacity
                }
                .clip(CircleShape)
                .background(dotColor),
        )
    }
}

@Composable
private fun CompletedToolActivityCard(
    group: ToolCallGroup,
    startsExpanded: Boolean = false,
) {
    ToolActivityCard(
        tools = group.tools,
        startsExpanded = startsExpanded,
    )
}

@Composable
private fun GitTurnChangesCard(
    summary: TurnFileChangeSummary,
    onOpenAll: () -> Unit,
    onOpenFile: (GitFileChange) -> Unit,
) {
    var expanded by remember(summary) { mutableStateOf(true) }
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(10.dp))
            .background(MaterialTheme.colorScheme.surface.copy(alpha = 0.68f))
            .border(0.5.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.62f), RoundedCornerShape(10.dp))
            .padding(horizontal = 10.dp, vertical = 9.dp),
        verticalArrangement = Arrangement.spacedBy(if (expanded) 8.dp else 0.dp),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(8.dp))
                .clickable { expanded = !expanded },
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                "\u270e",
                modifier = Modifier.width(18.dp),
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.secondary,
                fontWeight = FontWeight.SemiBold,
            )
            Text(
                "File changes",
                style = MaterialTheme.typography.labelSmall,
                fontWeight = FontWeight.SemiBold,
                maxLines = 1,
            )
            Text(
                "+${summary.totalAdditions} -${summary.totalDeletions}  ${summary.fileCount}",
                modifier = Modifier.weight(1f),
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.secondary,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                "Open diff",
                modifier = Modifier
                    .clip(RoundedCornerShape(999.dp))
                    .clickable(onClick = onOpenAll)
                    .background(MaterialTheme.colorScheme.secondary.copy(alpha = 0.12f))
                    .padding(horizontal = 8.dp, vertical = 4.dp),
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.secondary,
                fontWeight = FontWeight.SemiBold,
            )
            Text(
                if (expanded) "\u2303" else "\u2304",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.secondary,
                fontWeight = FontWeight.SemiBold,
            )
        }
        if (expanded) {
            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                summary.changes.forEach { change ->
                    GitTurnChangeRow(
                        change = change,
                        onClick = { change.gitFile?.let(onOpenFile) },
                    )
                }
            }
        }
    }
}

@Composable
private fun GitTurnChangeRow(
    change: TurnFileChange,
    onClick: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(8.dp))
            .clickable(onClick = onClick)
            .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.35f))
            .padding(horizontal = 9.dp, vertical = 8.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            change.path,
            modifier = Modifier.weight(1f),
            style = MaterialTheme.typography.labelSmall,
            maxLines = 1,
            overflow = TextOverflow.MiddleEllipsis,
        )
        if (change.additions > 0 || change.deletions > 0) {
            Text(
                "+${change.additions} -${change.deletions}",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.secondary,
                fontWeight = FontWeight.SemiBold,
            )
        }
        TranscriptStatusPill(
            text = change.displayStatus,
            color = gitStatusColor(change.displayStatus),
        )
    }
}

@Composable
private fun gitStatusColor(status: String): Color {
    val normalized = status.lowercase()
    return when {
        "delete" in normalized || normalized == "d" -> MaterialTheme.colorScheme.error
        "add" in normalized || "new" in normalized || "untracked" in normalized || normalized == "a" -> MaterialTheme.colorScheme.tertiary
        "rename" in normalized -> MaterialTheme.colorScheme.primary
        else -> MaterialTheme.colorScheme.secondary
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun GitTurnDiffSheet(
    sessionId: String,
    repository: GitRepository,
    presentation: TurnDiffPresentation,
    onDismiss: () -> Unit,
) {
    val files = presentation.files.distinctBy { it.gitPath() }
    var selectedFile by remember(presentation) {
        mutableStateOf(
            files.firstOrNull { it.gitPath() == presentation.initialPath }
                ?: files.firstOrNull(),
        )
    }
    var retryNonce by remember(presentation) { mutableStateOf(0) }
    var diff by remember(presentation) { mutableStateOf<GitDiffResponse?>(null) }
    var error by remember(presentation) { mutableStateOf<String?>(null) }
    var isLoading by remember(presentation) { mutableStateOf(false) }

    LaunchedEffect(selectedFile, retryNonce) {
        val file = selectedFile
        val path = file?.gitPath()
        if (file == null || path.isNullOrBlank()) {
            diff = null
            error = "No diffable files are available for this turn."
            isLoading = false
            return@LaunchedEffect
        }
        isLoading = true
        error = null
        diff = null
        runCatching { repository.diff(sessionId, path, file.gitDiffKind()) }
            .onSuccess { response ->
                diff = response
                error = response.error
            }
            .onFailure { throwable ->
                error = throwable.message ?: "Could not load diff."
            }
        isLoading = false
    }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        dragHandle = null,
        containerColor = MaterialTheme.colorScheme.background,
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 14.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Column(Modifier.weight(1f)) {
                    Text(
                        if (files.size == 1) "1 file changed" else "${files.size} files changed",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.SemiBold,
                    )
                    selectedFile?.gitPath()?.let { path ->
                        Text(
                            path,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.secondary,
                            maxLines = 1,
                            overflow = TextOverflow.MiddleEllipsis,
                        )
                    }
                }
                TextButton(onClick = onDismiss) { Text("Done") }
            }

            if (files.size > 1) {
                Row(
                    modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    files.forEach { file ->
                        val path = file.gitPath().orEmpty()
                        HermexPillButton(
                            label = path.substringAfterLast('/').substringAfterLast('\\').ifBlank { "File" },
                            onClick = { selectedFile = file },
                            filled = file.gitPath() == selectedFile?.gitPath(),
                            contentPadding = PaddingValues(horizontal = 12.dp, vertical = 7.dp),
                        )
                    }
                }
            }

            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(520.dp)
                    .verticalScroll(rememberScrollState()),
            ) {
                when {
                    isLoading -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        CircularProgressIndicator(strokeWidth = 2.dp)
                    }
                    error != null && diff == null -> Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        Text(error.orEmpty(), color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall)
                        HermexPillButton("Try Again", onClick = { retryNonce++ })
                    }
                    diff != null -> HermexGitDiffContent(requireNotNull(diff))
                    else -> Text("No diff selected.", style = MaterialTheme.typography.bodySmall)
                }
            }
        }
    }
}

private fun GitFileChange.gitPath(): String? =
    (path ?: workspacePath)
        ?.trim()
        ?.takeIf { it.isNotEmpty() }

private fun GitFileChange.gitDiffKind(): String =
    if (staged == true) "staged" else "unstaged"

@Composable
private fun ToolActivityCard(
    tools: List<ToolCall>,
    startsExpanded: Boolean = false,
) {
    var userToggledExpansion by remember { mutableStateOf<Boolean?>(null) }
    val expanded = userToggledExpansion ?: startsExpanded
    val hasFailure = tools.any { it.isError == true }
    val accentColor = if (hasFailure) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.secondary
    val summary = tools.joinToString(", ") { it.displayName }.ifBlank { "Tool activity" }
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(10.dp))
            .background(MaterialTheme.colorScheme.surface.copy(alpha = 0.68f))
            .border(0.5.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.62f), RoundedCornerShape(10.dp))
            .padding(horizontal = 10.dp, vertical = 9.dp),
        verticalArrangement = Arrangement.spacedBy(if (expanded) 8.dp else 0.dp),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(8.dp))
                .clickable { userToggledExpansion = !expanded },
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                if (hasFailure) "!" else "\u2692",
                modifier = Modifier.width(18.dp),
                style = MaterialTheme.typography.labelMedium,
                color = accentColor,
                fontWeight = FontWeight.SemiBold,
            )
            Text(
                text = if (tools.size == 1) "Tool" else "${tools.size} tools",
                style = MaterialTheme.typography.labelSmall,
                fontWeight = FontWeight.SemiBold,
                maxLines = 1,
            )
            Text(
                text = summary,
                modifier = Modifier.weight(1f),
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.secondary,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            if (hasFailure) {
                TranscriptStatusPill(text = "Failed", color = accentColor)
            }
            Text(
                if (expanded) "\u2303" else "\u2304",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.secondary,
                fontWeight = FontWeight.SemiBold,
            )
        }
        if (expanded) {
            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                tools.forEach { tool ->
                    ToolCallCard(tool, startsExpanded = startsExpanded)
                }
            }
        }
    }
}

@Composable
private fun ToolCallCard(
    tool: ToolCall,
    startsExpanded: Boolean = false,
) {
    var userToggledExpansion by remember { mutableStateOf<Boolean?>(null) }
    val expanded = userToggledExpansion ?: startsExpanded
    val accentColor = if (tool.isError == true) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.secondary
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(9.dp))
            .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.46f))
            .border(0.5.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.52f), RoundedCornerShape(9.dp))
            .padding(horizontal = 9.dp, vertical = if (expanded) 8.dp else 7.dp),
        verticalArrangement = Arrangement.spacedBy(if (expanded) 8.dp else 0.dp),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(7.dp))
                .clickable { userToggledExpansion = !expanded },
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                if (tool.isError == true) "!" else "\u2692",
                modifier = Modifier.width(18.dp),
                style = MaterialTheme.typography.labelMedium,
                color = accentColor,
                fontWeight = FontWeight.SemiBold,
            )
            Text(
                text = tool.displayName,
                modifier = Modifier.weight(1f),
                style = MaterialTheme.typography.labelSmall,
                fontWeight = FontWeight.SemiBold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            tool.collapsedStatusText?.let { status ->
                TranscriptStatusPill(text = status, color = accentColor)
            }
            Text(
                if (expanded) "\u2303" else "\u2304",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.secondary,
                fontWeight = FontWeight.SemiBold,
            )
        }
        if (expanded) {
            ToolCallDetails(tool)
        }
    }
}

@Composable
private fun TranscriptStatusPill(
    text: String,
    color: Color,
) {
    Text(
        text,
        modifier = Modifier
            .clip(RoundedCornerShape(999.dp))
            .background(color.copy(alpha = 0.12f))
            .padding(horizontal = 7.dp, vertical = 3.dp),
        style = MaterialTheme.typography.labelSmall,
        color = color,
        fontWeight = FontWeight.SemiBold,
        maxLines = 1,
    )
}

@Composable
private fun ToolCallDetails(tool: ToolCall) {
    Column(verticalArrangement = Arrangement.spacedBy(7.dp)) {
        if (!tool.preview.isNullOrBlank()) {
            ToolDetailSection(title = "Preview", value = tool.preview)
        }
        val argsText = tool.args?.takeIf { it.isNotEmpty() }?.entries
            ?.joinToString("\n") { (key, value) -> "$key: ${value.toString().trim()}" }
        if (!argsText.isNullOrBlank()) {
            ToolDetailSection(title = "Arguments", value = argsText)
        }
        tool.result?.let { result ->
            ToolDetailSection(title = "Result", value = result.toString().trim())
        }
    }
}

@Composable
private fun ToolDetailSection(
    title: String,
    value: String,
) {
    Column(verticalArrangement = Arrangement.spacedBy(3.dp)) {
        Text(
            title,
            style = MaterialTheme.typography.labelSmall,
            fontWeight = FontWeight.SemiBold,
            color = MaterialTheme.colorScheme.secondary,
        )
        SelectionContainer {
            Text(
                value,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurface,
            )
        }
    }
}

private val ToolCall.displayName: String
    get() = name?.takeIf { it.isNotBlank() }
        ?: preview?.lineSequence()?.firstOrNull { it.isNotBlank() }?.take(48)
        ?: id?.takeIf { it.isNotBlank() }
        ?: "Tool"

private val ToolCall.collapsedStatusText: String?
    get() = when {
        isError == true -> "Failed"
        result != null -> "Done"
        else -> null
    }

private enum class ChatMarkerKind(
    val title: String,
    val symbol: String,
) {
    ContextCompaction("Context compaction", "\u2198"),
    PreservedTaskList("Preserved task list", "\u2611"),
    CompressionReference("Context compaction", "\u2605"),
}

private val ChatMessage.markerKind: ChatMarkerKind?
    get() {
        val roleName = role ?: return null
        if (roleName == "tool") return null
        val text = displayText.withoutAttachedFilesMarker().trim()
        if (
            roleName == "user" &&
            text.startsWith(preservedTaskListPrefix, ignoreCase = true)
        ) {
            return ChatMarkerKind.PreservedTaskList
        }
        if (isContextCompactionText(text)) {
            return ChatMarkerKind.ContextCompaction
        }
        return null
    }

private const val preservedTaskListPrefix = "[your active task list was preserved across context compression]"

private fun isContextCompactionText(text: String?): Boolean {
    val trimmed = text.orEmpty().trim()
    return trimmed.startsWith("[context compaction", ignoreCase = true) ||
        trimmed.startsWith("context compaction", ignoreCase = true)
}

private fun markerCardBody(kind: ChatMarkerKind, content: String?): String {
    val text = content.orEmpty().trim()
    if (kind != ChatMarkerKind.PreservedTaskList) return text
    return if (text.startsWith(preservedTaskListPrefix, ignoreCase = true)) {
        text.drop(preservedTaskListPrefix.length).trim()
    } else {
        text
    }
}

private fun markerSummary(kind: ChatMarkerKind, body: String): String {
    val oneLine = body.replace('\n', ' ').trim()
    if (kind == ChatMarkerKind.CompressionReference) {
        return if (oneLine.isBlank()) {
            "Reference only"
        } else {
            "Reference only \u00b7 ${if (oneLine.length <= 80) oneLine else "${oneLine.take(80)}..."}"
        }
    }
    val value = if (oneLine.isBlank()) kind.title else oneLine
    return if (value.length <= 80) value else "${value.take(80)}..."
}

private fun Modifier.messageActionsGesture(
    enabled: Boolean,
    onLongPress: () -> Unit,
): Modifier {
    if (!enabled) return this
    return pointerInput(onLongPress) {
        detectTapGestures(onLongPress = { onLongPress() })
    }
}

@Composable
private fun UserMessageBubble(
    text: String,
    onShowActions: () -> Unit,
) {
    val bubbleShape = RoundedCornerShape(20.dp)
    Column(
        modifier = Modifier
            .fillMaxWidth(0.86f)
            .messageActionsGesture(
                enabled = text.isNotBlank(),
                onLongPress = onShowActions,
            )
            .clip(bubbleShape)
            .background(MaterialTheme.colorScheme.surfaceVariant)
            .border(0.5.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.55f), bubbleShape)
            .padding(horizontal = 14.dp, vertical = 8.dp),
    ) {
        SelectionContainer {
            Text(
                text,
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurface,
            )
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun MessageActionSheet(
    context: MessageActionContext,
    messageActionEnabled: Boolean,
    isRegeneratingMessage: Boolean,
    isEditingMessage: Boolean,
    isForkingMessage: Boolean,
    onCopy: () -> Unit,
    onListen: () -> Unit,
    onSelectText: () -> Unit,
    onEdit: () -> Unit,
    onRegenerate: () -> Unit,
    onFork: () -> Unit,
    onDismiss: () -> Unit,
) {
    ModalBottomSheet(onDismissRequest = onDismiss) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 18.dp)
                .padding(bottom = 22.dp),
            verticalArrangement = Arrangement.spacedBy(2.dp),
        ) {
            if (context.role == MessageActionRole.Assistant) {
                MessageActionSheetRow(
                    title = "Listen",
                    symbol = "\u266a",
                    enabled = context.listenText?.isNotBlank() == true,
                    onClick = onListen,
                )
                MessageActionSheetRow(
                    title = "Select Text",
                    symbol = "T",
                    onClick = onSelectText,
                )
                MessageActionSheetRow(
                    title = "Regenerate Response",
                    symbol = "\u21bb",
                    enabled = messageActionEnabled && !isRegeneratingMessage,
                    onClick = onRegenerate,
                )
            }
            if (context.role == MessageActionRole.User) {
                MessageActionSheetRow(
                    title = "Edit Message",
                    symbol = "\u270e",
                    enabled = messageActionEnabled && !isEditingMessage,
                    onClick = onEdit,
                )
            }
            MessageActionSheetRow(
                title = "Fork From Here",
                symbol = "\u21b1",
                enabled = messageActionEnabled && !isForkingMessage,
                onClick = onFork,
            )
            MessageActionSheetRow(
                title = "Copy",
                symbol = "\u2398",
                onClick = onCopy,
            )
        }
    }
}

@Composable
private fun MessageActionSheetRow(
    title: String,
    symbol: String,
    enabled: Boolean = true,
    onClick: () -> Unit,
) {
    TextButton(
        onClick = onClick,
        enabled = enabled,
        modifier = Modifier.fillMaxWidth(),
        contentPadding = PaddingValues(horizontal = 12.dp, vertical = 12.dp),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                symbol,
                modifier = Modifier.width(22.dp),
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.secondary,
                fontWeight = FontWeight.SemiBold,
            )
            Text(
                title,
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurface,
                fontWeight = FontWeight.Medium,
            )
        }
    }
}

@Composable
private fun MessageAttachmentGrid(
    attachments: List<MessageAttachment>,
    onPreview: (MessageAttachment) -> Unit,
    loadAttachmentImage: suspend (String) -> ByteArray?,
    alignEnd: Boolean = true,
) {
    val audioAttachments = attachments.filter { it.inferredIsAudio }
    val gridAttachments = attachments.filterNot { it.inferredIsAudio }
    val rows = gridAttachments.chunked(2)
    Column(
        modifier = Modifier
            .widthIn(max = 244.dp)
            .fillMaxWidth(),
        horizontalAlignment = if (alignEnd) Alignment.End else Alignment.Start,
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        audioAttachments.forEach { attachment ->
            InlineAudioAttachmentPlayer(
                title = attachment.displayName,
                path = attachment.resolvedAttachmentPath,
                loadAttachmentData = loadAttachmentImage,
            )
        }
        rows.forEach { row ->
            Row(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                row.forEach { attachment ->
                    MessageAttachmentTile(
                        attachment = attachment,
                        onPreview = { onPreview(attachment) },
                        loadAttachmentImage = loadAttachmentImage,
                    )
                }
            }
        }
    }
}

@Composable
private fun InlineAudioAttachmentPlayer(
    title: String,
    path: String?,
    loadAttachmentData: suspend (String) -> ByteArray?,
) {
    val context = LocalContext.current
    var phase by remember(path) { mutableStateOf(AudioAttachmentPhase.Loading) }
    var player by remember(path) { mutableStateOf<MediaPlayer?>(null) }
    var tempFile by remember(path) { mutableStateOf<File?>(null) }
    var isPlaying by remember(path) { mutableStateOf(false) }
    var currentMs by remember(path) { mutableStateOf(0) }
    var durationMs by remember(path) { mutableStateOf(0) }

    LaunchedEffect(path) {
        phase = AudioAttachmentPhase.Loading
        isPlaying = false
        currentMs = 0
        durationMs = 0
        val resolvedPath = path?.takeIf { it.isNotBlank() }
        if (resolvedPath == null) {
            phase = AudioAttachmentPhase.Failed
            return@LaunchedEffect
        }
        val bytes = loadAttachmentData(resolvedPath)
        if (bytes == null || bytes.isEmpty()) {
            phase = AudioAttachmentPhase.Failed
            return@LaunchedEffect
        }
        val prepared = runCatching {
            val audioFile = withContext(Dispatchers.IO) {
                File.createTempFile("hermex-attachment-", ".audio", context.cacheDir).also { it.writeBytes(bytes) }
            }
            val mediaPlayer = MediaPlayer().apply {
                setDataSource(audioFile.absolutePath)
                setOnCompletionListener { completedPlayer ->
                    isPlaying = false
                    currentMs = 0
                    completedPlayer.seekTo(0)
                    AudioAttachmentPlaybackCenter.clear(completedPlayer)
                }
                setOnErrorListener { failedPlayer, _, _ ->
                    AudioAttachmentPlaybackCenter.clear(failedPlayer)
                    isPlaying = false
                    phase = AudioAttachmentPhase.Failed
                    true
                }
                prepare()
            }
            tempFile = audioFile
            mediaPlayer
        }.getOrNull()
        if (prepared == null) {
            phase = AudioAttachmentPhase.Failed
        } else {
            player = prepared
            durationMs = prepared.duration.coerceAtLeast(0)
            phase = AudioAttachmentPhase.Ready
        }
    }

    LaunchedEffect(isPlaying, player) {
        while (isPlaying) {
            player?.let { currentMs = it.currentPosition.coerceAtLeast(0) }
            delay(200)
        }
    }

    DisposableEffect(path) {
        onDispose {
            player?.let { mediaPlayer ->
                AudioAttachmentPlaybackCenter.clear(mediaPlayer)
                mediaPlayer.release()
            }
            tempFile?.delete()
        }
    }

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(14.dp))
            .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.78f))
            .border(0.5.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.65f), RoundedCornerShape(14.dp))
            .padding(horizontal = 12.dp, vertical = 10.dp),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            modifier = Modifier
                .size(40.dp)
                .clip(CircleShape)
                .clickable(enabled = phase == AudioAttachmentPhase.Ready) {
                    val mediaPlayer = player ?: return@clickable
                    if (isPlaying) {
                        mediaPlayer.pause()
                        isPlaying = false
                        AudioAttachmentPlaybackCenter.clear(mediaPlayer)
                    } else {
                        AudioAttachmentPlaybackCenter.play(mediaPlayer) {
                            mediaPlayer.pause()
                            isPlaying = false
                        }
                        mediaPlayer.start()
                        isPlaying = true
                    }
                }
                .background(
                    if (phase == AudioAttachmentPhase.Ready) {
                        MaterialTheme.colorScheme.primary
                    } else {
                        MaterialTheme.colorScheme.primary.copy(alpha = 0.15f)
                    },
                ),
            contentAlignment = Alignment.Center,
        ) {
            when (phase) {
                AudioAttachmentPhase.Loading -> CircularProgressIndicator(strokeWidth = 2.dp, modifier = Modifier.size(20.dp))
                AudioAttachmentPhase.Failed -> Text("!", color = MaterialTheme.colorScheme.secondary, fontWeight = FontWeight.Bold)
                AudioAttachmentPhase.Ready -> Text(
                    if (isPlaying) "II" else ">",
                    color = MaterialTheme.colorScheme.onPrimary,
                    fontWeight = FontWeight.Bold,
                    style = MaterialTheme.typography.labelMedium,
                )
            }
        }
        Column(
            modifier = Modifier.weight(1f),
            verticalArrangement = Arrangement.spacedBy(3.dp),
        ) {
            Text(
                title,
                style = MaterialTheme.typography.labelMedium,
                fontWeight = FontWeight.SemiBold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            if (phase == AudioAttachmentPhase.Failed) {
                Text(
                    "Couldn't play this audio",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                )
            } else {
                Slider(
                    value = currentMs.toFloat().coerceIn(0f, durationMs.coerceAtLeast(1).toFloat()),
                    onValueChange = { value ->
                        val seekTo = value.toInt()
                        currentMs = seekTo
                        player?.seekTo(seekTo)
                    },
                    valueRange = 0f..durationMs.coerceAtLeast(1).toFloat(),
                    enabled = phase == AudioAttachmentPhase.Ready,
                )
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                ) {
                    Text(
                        currentMs.formatAudioDuration(),
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Text(
                        durationMs.formatAudioDuration(),
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }
    }
}

private enum class AudioAttachmentPhase {
    Loading,
    Ready,
    Failed,
}

private object AudioAttachmentPlaybackCenter {
    private var activePlayer: MediaPlayer? = null
    private var activePause: (() -> Unit)? = null

    fun play(player: MediaPlayer, pauseCurrent: () -> Unit) {
        if (activePlayer !== player) {
            activePause?.invoke()
        }
        activePlayer = player
        activePause = pauseCurrent
    }

    fun clear(player: MediaPlayer) {
        if (activePlayer === player) {
            activePlayer = null
            activePause = null
        }
    }
}

@Composable
private fun MessageAttachmentTile(
    attachment: MessageAttachment,
    onPreview: () -> Unit,
    loadAttachmentImage: suspend (String) -> ByteArray?,
) {
    if (attachment.inferredIsImage) {
        RemoteAttachmentImageTile(
            path = attachment.resolvedAttachmentPath,
            size = 118.dp,
            cornerRadius = 14.dp,
            loadAttachmentImage = loadAttachmentImage,
            onPreview = onPreview,
        )
    } else {
        Column(
            modifier = Modifier
                .size(118.dp)
                .clip(RoundedCornerShape(12.dp))
                .clickable(onClick = onPreview)
                .background(MaterialTheme.colorScheme.surfaceVariant)
                .border(0.5.dp, MaterialTheme.colorScheme.outlineVariant, RoundedCornerShape(12.dp))
                .padding(horizontal = 9.dp, vertical = 10.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
        ) {
            Text(
                attachment.fileKindLabel,
                style = MaterialTheme.typography.titleMedium,
                color = attachment.badgeColor,
                fontWeight = FontWeight.SemiBold,
            )
            Spacer(Modifier.height(5.dp))
            Text(
                attachment.displayName,
                style = MaterialTheme.typography.labelSmall,
                fontWeight = FontWeight.Medium,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
            Spacer(Modifier.height(5.dp))
            Text(
                attachment.fileExtensionLabel,
                style = MaterialTheme.typography.labelMedium,
                color = attachment.badgeColor,
                fontWeight = FontWeight.Bold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}

@Composable
private fun AttachmentPreviewImage(
    path: String?,
    loadAttachmentData: suspend (String) -> ByteArray?,
    contentDescription: String,
) {
    var bytes by remember(path) { mutableStateOf<ByteArray?>(null) }
    var didAttemptLoad by remember(path) { mutableStateOf(false) }
    LaunchedEffect(path) {
        bytes = null
        didAttemptLoad = false
        val resolvedPath = path?.takeIf { it.isNotBlank() }
        if (resolvedPath != null) {
            bytes = loadAttachmentData(resolvedPath)
        }
        didAttemptLoad = true
    }
    val bitmap = remember(bytes) {
        bytes?.let { BitmapFactory.decodeByteArray(it, 0, it.size) }
    }

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(280.dp)
            .clip(RoundedCornerShape(12.dp))
            .background(MaterialTheme.colorScheme.surfaceVariant),
        contentAlignment = Alignment.Center,
    ) {
        when {
            bitmap != null -> Image(
                bitmap = bitmap.asImageBitmap(),
                contentDescription = contentDescription,
                contentScale = ContentScale.Fit,
                modifier = Modifier.fillMaxSize(),
            )
            !didAttemptLoad -> CircularProgressIndicator(strokeWidth = 2.dp)
            else -> Text(
                "Image preview could not be loaded.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun AttachmentTextPreview(
    path: String?,
    loadAttachmentFile: suspend (String) -> FileResponse?,
) {
    var file by remember(path) { mutableStateOf<FileResponse?>(null) }
    var didAttemptLoad by remember(path) { mutableStateOf(false) }
    LaunchedEffect(path) {
        file = null
        didAttemptLoad = false
        val resolvedPath = path?.takeIf { it.isNotBlank() }
        if (resolvedPath != null) {
            file = loadAttachmentFile(resolvedPath)
        }
        didAttemptLoad = true
    }

    val error = file?.error?.trim()?.takeIf { it.isNotBlank() }
    val content = file?.content
    when {
        !didAttemptLoad -> {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(180.dp),
                contentAlignment = Alignment.Center,
            ) {
                CircularProgressIndicator(strokeWidth = 2.dp)
            }
        }
        error != null -> AttachmentPreviewUnavailable(error, path ?: "Unavailable")
        content != null -> {
            val verticalState = rememberScrollState()
            val horizontalState = rememberScrollState()
            SelectionContainer {
                Text(
                    text = content.ifEmpty { " " },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(240.dp)
                        .clip(RoundedCornerShape(12.dp))
                        .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.58f))
                        .border(0.5.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.6f), RoundedCornerShape(12.dp))
                        .horizontalScroll(horizontalState)
                        .verticalScroll(verticalState)
                        .padding(12.dp),
                    style = MaterialTheme.typography.bodySmall,
                    fontFamily = FontFamily.Monospace,
                    color = MaterialTheme.colorScheme.onSurface,
                )
            }
        }
        else -> AttachmentPreviewUnavailable("Preview is not available for this attachment.", path ?: "Unavailable")
    }
}

@Composable
private fun AttachmentPreviewUnavailable(
    message: String,
    path: String,
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.58f))
            .border(0.5.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.6f), RoundedCornerShape(12.dp))
            .padding(14.dp),
        verticalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        Text(
            "No Preview",
            style = MaterialTheme.typography.titleSmall,
            fontWeight = FontWeight.SemiBold,
        )
        Text(
            message,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        SelectionContainer {
            Text(
                path,
                style = MaterialTheme.typography.labelSmall,
                fontFamily = FontFamily.Monospace,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun MessageAttachmentPreviewSheet(
    attachment: MessageAttachment,
    loadAttachmentData: suspend (String) -> ByteArray?,
    loadAttachmentFile: suspend (String) -> FileResponse?,
    onDismiss: () -> Unit,
) {
    PickerSheet(
        title = attachment.displayName,
        onDismiss = onDismiss,
        heightFraction = 0.48f,
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(18.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Text(
                if (attachment.inferredIsImage) "Image attachment" else "File attachment",
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.SemiBold,
            )
            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
            if (attachment.inferredIsImage) {
                AttachmentPreviewImage(
                    path = attachment.resolvedAttachmentPath,
                    loadAttachmentData = loadAttachmentData,
                    contentDescription = attachment.displayName,
                )
            } else if (attachment.inferredIsAudio) {
                InlineAudioAttachmentPlayer(
                    title = attachment.displayName,
                    path = attachment.resolvedAttachmentPath,
                    loadAttachmentData = loadAttachmentData,
                )
            } else if (attachment.isKnownUnsupportedBinary) {
                AttachmentPreviewUnavailable(
                    message = "Preview is not available for this file type.",
                    path = attachment.resolvedAttachmentPath ?: attachment.displayName,
                )
            } else {
                AttachmentTextPreview(
                    path = attachment.resolvedAttachmentPath,
                    loadAttachmentFile = loadAttachmentFile,
                )
            }
            AttachmentInfoRow("Name", attachment.displayName)
            AttachmentInfoRow("Path", attachment.path?.takeIf { it.isNotBlank() } ?: "Unavailable")
            AttachmentInfoRow("Type", attachment.mime?.takeIf { it.isNotBlank() } ?: attachment.fileExtensionLabel)
            AttachmentInfoRow("Size", attachment.size?.toLong().formatBytesOrUnavailable())
        }
    }
}

private val ModelSummary.displayTitle: String
    get() = label?.takeIf { it.isNotBlank() }
        ?: name?.takeIf { it.isNotBlank() }
        ?: id?.takeIf { it.isNotBlank() }
        ?: "Model"

private val ProfileSummary.displayTitle: String
    get() = displayName?.takeIf { it.isNotBlank() }
        ?: name?.takeIf { it.isNotBlank() }
        ?: "Profile"

private val ProfileSummary.modelProviderText: String?
    get() = listOfNotNull(
        model?.takeIf { it.isNotBlank() },
        provider?.takeIf { it.isNotBlank() },
    ).joinToString(" / ").ifBlank { null }

private val ChatUiState.profileTitle: String
    get() {
        selectedProfile?.displayTitle?.takeIf { it != "Profile" }?.let { return it }
        val profileName = sessionProfile?.trim()?.takeIf { it.isNotBlank() }
            ?: activeProfileName?.trim()?.takeIf { it.isNotBlank() }
            ?: return "Profile"
        return profileOptions.firstOrNull { it.name == profileName }?.displayTitle ?: profileName
    }

private val ChatUiState.headerTitle: String
    get() = sessionTitle?.trim()?.takeIf { it.isNotBlank() } ?: "Untitled Session"

private val ChatUiState.headerSubtitle: String?
    get() {
        val workspace = (sessionWorkspacePath ?: selectedWorkspacePath)?.trim()?.takeIf { it.isNotBlank() }
        if (workspace != null) return workspace.lastPathComponentFallback()
        val profile = profileTitle
        return profile.trim().takeIf { it.isNotBlank() && it != "Profile" }
    }

private val ChatUiState.workspaceTitle: String
    get() {
        val workspace = selectedWorkspacePath?.takeIf { it.isNotBlank() } ?: return "Workspace"
        val root = workspaceRoots.firstOrNull { it.path == workspace }
        return root?.name?.takeIf { it.isNotBlank() } ?: workspace.lastPathComponentFallback()
    }

private fun HttpUrl.transcriptPreviewDisplayText(): String =
    toString()
        .removePrefix("$scheme://")
        .removeSuffix("/")

private val ChatUiState.hasWorkspaceChoices: Boolean
    get() = workspaceRoots.any { !it.path.isNullOrBlank() } || workspaceSuggestions.any { it.isNotBlank() }

private val WorkspacePickerRow.displayTitle: String
    get() = name?.takeIf { it.isNotBlank() } ?: path.lastPathComponentFallback()

private val UploadResponse.displayName: String
    get() = filename?.trim()?.takeIf { it.isNotBlank() }
        ?: path?.trim()?.takeIf { it.isNotBlank() }?.lastPathComponentFallback()
        ?: if (inferredIsImage) "Image" else "File"

private val UploadResponse.resolvedAttachmentPath: String?
    get() = path?.trim()?.takeIf { it.isNotBlank() }
        ?: filename?.trim()?.takeIf { it.isNotBlank() }

private val UploadResponse.inferredIsImage: Boolean
    get() = isImage == true ||
        mime?.lowercase()?.startsWith("image/") == true ||
        fileExtension.lowercase() in setOf("jpg", "jpeg", "png", "gif", "webp", "heic", "heif", "bmp", "tiff", "tif", "ico")

private val UploadResponse.inferredIsAudio: Boolean
    get() {
        if (isImage == true) return false
        if (mime?.lowercase()?.startsWith("audio/") == true) return true
        val filenameExtension = filename?.substringAfterLast('.', missingDelimiterValue = "")?.lowercase()
        val pathExtension = path?.substringAfterLast('.', missingDelimiterValue = "")?.lowercase()
        return filenameExtension in audioAttachmentExtensions || pathExtension in audioAttachmentExtensions
    }

private val UploadResponse.isKnownUnsupportedBinary: Boolean
    get() = attachmentExtension(filename, path, displayName) in unsupportedBinaryAttachmentExtensions

private val UploadResponse.fileExtension: String
    get() = displayName.substringAfterLast('.', missingDelimiterValue = "").take(5)

private val UploadResponse.fileExtensionLabel: String
    get() = fileExtension.uppercase().ifBlank { "FILE" }

private val UploadResponse.fileKindLabel: String
    get() = attachmentKindLabel(fileExtension, mime)

private val UploadResponse.fileDetailText: String
    get() = size.formatBytesOrUnavailable().takeUnless { it == "Unavailable" } ?: fileExtensionLabel

private val UploadResponse.badgeColor: Color
    get() = attachmentBadgeColor(fileExtension, mime)

private fun attachmentKindLabel(extension: String, mime: String?): String {
    val lowerMime = mime?.lowercase().orEmpty()
    return when (extension.lowercase()) {
        "csv", "tsv", "xls", "xlsx" -> "TABLE"
        "pdf" -> "PDF"
        "zip", "tar", "gz", "tgz", "rar", "7z" -> "ZIP"
        "json" -> "JSON"
        "md" -> "MD"
        "txt", "log" -> "TEXT"
        "xml", "yaml", "yml" -> "DOC"
        "mp3", "m4a", "wav", "aac", "flac", "ogg", "opus" -> "AUDIO"
        "mp4", "mov", "m4v", "webm", "mkv", "avi" -> "VIDEO"
        else -> when {
            lowerMime.startsWith("audio/") -> "AUDIO"
            lowerMime.startsWith("video/") -> "VIDEO"
            lowerMime.startsWith("text/") -> "TEXT"
            else -> "FILE"
        }
    }
}

private fun attachmentBadgeColor(extension: String, mime: String?): Color {
    val lowerMime = mime?.lowercase().orEmpty()
    return when (extension.lowercase()) {
        "csv", "tsv", "xls", "xlsx" -> Color(0xFF34A853)
        "pdf" -> Color(0xFFE53935)
        "json", "md", "txt", "log", "xml", "yaml", "yml" -> Color(0xFF007AFF)
        "mp3", "m4a", "wav", "aac", "flac", "ogg", "opus" -> Color(0xFFFF9500)
        "mp4", "mov", "m4v", "webm", "mkv", "avi" -> Color(0xFFFF2D55)
        "zip", "tar", "gz", "tgz", "rar", "7z" -> Color(0xFF8E8E93)
        else -> when {
            lowerMime.startsWith("audio/") -> Color(0xFFFF9500)
            lowerMime.startsWith("video/") -> Color(0xFFFF2D55)
            lowerMime.startsWith("text/") -> Color(0xFF007AFF)
            else -> Color(0xFF5856D6)
        }
    }
}

private val MessageAttachment.fileKindLabel: String
    get() = attachmentKindLabel(fileExtension, mime)

private val MessageAttachment.badgeColor: Color
    get() = attachmentBadgeColor(fileExtension, mime)

private fun ChatMessage.visibleDisplayText(hidesAttachmentPaths: Boolean): String =
    if (hidesAttachmentPaths) displayText.withoutAttachedFilesMarker() else displayText

private val ChatMessage.reasoningTexts: List<String>
    get() = reasoning.orEmpty().mapNotNull { segment ->
        segment.text?.trim()?.takeIf { it.isNotEmpty() }
    }

private val ChatMessage.displayAttachments: List<MessageAttachment>
    get() = attachments?.takeIf { it.isNotEmpty() }
        ?: inferredAttachmentsFromMarker(displayText)

private val MessageAttachment.displayName: String
    get() = name?.trim()?.takeIf { it.isNotBlank() }
        ?: path?.trim()?.takeIf { it.isNotBlank() }?.lastPathComponentFallback()
        ?: if (inferredIsImage) "Image" else "File"

private val MessageAttachment.resolvedAttachmentPath: String?
    get() = path?.trim()?.takeIf { it.isNotBlank() }
        ?: name?.trim()?.takeIf { it.isNotBlank() }

private val MessageAttachment.inferredIsImage: Boolean
    get() = isImage == true ||
        mime?.lowercase()?.startsWith("image/") == true ||
        fileExtension.lowercase() in setOf("jpg", "jpeg", "png", "gif", "webp", "heic", "heif", "bmp", "tiff", "tif", "ico")

private val MessageAttachment.inferredIsAudio: Boolean
    get() {
        if (isImage == true) return false
        if (mime?.lowercase()?.startsWith("audio/") == true) return true
        val nameExtension = name?.substringAfterLast('.', missingDelimiterValue = "")?.lowercase()
        val pathExtension = path?.substringAfterLast('.', missingDelimiterValue = "")?.lowercase()
        return nameExtension in audioAttachmentExtensions || pathExtension in audioAttachmentExtensions
    }

private val MessageAttachment.isKnownUnsupportedBinary: Boolean
    get() = attachmentExtension(name, path, displayName) in unsupportedBinaryAttachmentExtensions

private val MessageAttachment.fileExtension: String
    get() = displayName.substringAfterLast('.', missingDelimiterValue = "").take(5)

private val MessageAttachment.fileExtensionLabel: String
    get() = fileExtension.uppercase().ifBlank { "FILE" }

private val MessageAttachment.fileDetailText: String
    get() = size?.toLong().formatBytesOrUnavailable().takeUnless { it == "Unavailable" } ?: fileExtensionLabel

private fun inferredAttachmentsFromMarker(content: String): List<MessageAttachment> {
    val markerStart = content.lastIndexOf("[Attached files:")
    if (markerStart < 0) return emptyList()
    val close = content.indexOf(']', startIndex = markerStart)
    if (close < 0) return emptyList()
    if (content.substring(close + 1).trim().isNotEmpty()) return emptyList()
    val body = content.substring(markerStart + "[Attached files:".length, close)
    val references = body.split(",").map { it.trim() }.filter { it.isNotBlank() }
    if (references.isEmpty()) return emptyList()
    val fallbackDirectory = references.firstOrNull { it.contains("/") }
        ?.substringBeforeLast('/', missingDelimiterValue = "")
        ?.takeIf { it.isNotBlank() }
    return references.map { reference ->
        val name = reference.lastPathComponentFallback()
        val path = when {
            reference.contains("/") -> reference
            name.substringAfterLast('.', "").lowercase() in setOf("jpg", "jpeg", "png", "gif", "webp", "heic", "heif", "bmp", "tiff", "tif") && fallbackDirectory != null -> "$fallbackDirectory/$name"
            else -> null
        }
        MessageAttachment(
            name = name,
            path = path,
            isImage = name.substringAfterLast('.', "").lowercase() in setOf("jpg", "jpeg", "png", "gif", "webp", "heic", "heif", "bmp", "tiff", "tif"),
        )
    }
}

private fun String.withoutAttachedFilesMarker(): String {
    val markerStart = lastIndexOf("[Attached files:")
    if (markerStart < 0) return this
    val close = indexOf(']', startIndex = markerStart)
    if (close < 0) return this
    if (substring(close + 1).trim().isNotEmpty()) return this
    return substring(0, markerStart).trimEnd()
}

private fun Double?.shortTimeText(): String? {
    val timestamp = this ?: return null
    val millis = (timestamp * 1000).toLong()
    return runCatching {
        DateTimeFormatter
            .ofLocalizedTime(FormatStyle.SHORT)
            .withLocale(Locale.getDefault())
            .withZone(ZoneId.systemDefault())
            .format(Instant.ofEpochMilli(millis))
    }.getOrNull()
}

private fun ContextWindowSnapshot.tokensLabel(): String {
    val used = tokensUsed ?: return "Unavailable"
    val total = contextLength ?: return "Unavailable"
    return "${used.formatTokens()} / ${total.formatTokens()}"
}

private fun Int?.formatTokensOrUnavailable(): String =
    this?.formatTokens() ?: "Unavailable"

private fun Int.formatTokens(): String =
    when {
        this >= 1_000_000 -> String.format(Locale.US, "%.1fM", this / 1_000_000.0)
        this >= 1_000 -> String.format(Locale.US, "%.1fK", this / 1_000.0)
        else -> toString()
    }

private fun Double?.formatCostOrUnavailable(): String =
    this?.let { String.format(Locale.US, "$%.4f", it) } ?: "Unavailable"

private fun Long?.formatBytesOrUnavailable(): String {
    val value = this ?: return "Unavailable"
    val units = listOf("B", "KB", "MB", "GB")
    var amount = value.toDouble()
    var unitIndex = 0
    while (amount >= 1024.0 && unitIndex < units.lastIndex) {
        amount /= 1024.0
        unitIndex += 1
    }
    return if (unitIndex == 0) {
        "${value} ${units[unitIndex]}"
    } else {
        String.format(Locale.US, "%.1f %s", amount, units[unitIndex])
    }
}

private fun Int.formatAudioDuration(): String {
    val totalSeconds = (coerceAtLeast(0) / 1000)
    val hours = totalSeconds / 3600
    val minutes = (totalSeconds % 3600) / 60
    val seconds = totalSeconds % 60
    return if (hours > 0) {
        String.format(Locale.US, "%d:%02d:%02d", hours, minutes, seconds)
    } else {
        String.format(Locale.US, "%d:%02d", minutes, seconds)
    }
}

private val audioAttachmentExtensions = setOf("m4a", "mp3", "wav", "aac", "caf", "ogg", "oga", "opus", "flac")

private val unsupportedBinaryAttachmentExtensions = setOf(
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

private fun attachmentExtension(vararg candidates: String?): String =
    candidates.firstNotNullOfOrNull { candidate ->
        candidate
            ?.trim()
            ?.takeIf { it.isNotBlank() }
            ?.substringAfterLast('.', missingDelimiterValue = "")
            ?.lowercase()
            ?.takeIf { it.isNotBlank() }
    }.orEmpty()

private fun String.lastPathComponentFallback(): String {
    val trimmed = trim().trimEnd('/', '\\')
    return trimmed.substringAfterLast('/').substringAfterLast('\\').ifBlank { this }
}
