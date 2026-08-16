package com.uzairansar.hermex.ui.chat

import android.Manifest
import android.content.ClipData
import android.content.ClipboardManager
import android.content.ContentValues
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.BitmapFactory
import android.graphics.Bitmap
import android.media.MediaPlayer
import android.net.Uri
import android.os.Build
import android.provider.MediaStore
import android.view.HapticFeedbackConstants
import android.widget.MediaController
import android.widget.Toast
import android.widget.VideoView
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.interaction.collectIsDraggedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.isImeVisible
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Slider
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.IconButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.produceState
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.snapshotFlow
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.ui.graphics.SolidColor
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
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import androidx.core.content.FileProvider
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.uzairansar.hermex.core.runSuspendCatching
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
import com.uzairansar.hermex.core.model.TranscriptMediaDataClassifier
import com.uzairansar.hermex.core.model.TranscriptMediaKind
import com.uzairansar.hermex.core.model.TranscriptMediaReference
import com.uzairansar.hermex.core.model.TranscriptMediaSource
import com.uzairansar.hermex.core.model.TranscriptMediaSegment
import com.uzairansar.hermex.core.model.TurnFileChange
import com.uzairansar.hermex.core.model.TurnFileChangeAggregator
import com.uzairansar.hermex.core.model.TurnFileChangeSummary
import com.uzairansar.hermex.core.model.UploadResponse
import com.uzairansar.hermex.core.model.WorkspaceRoot
import com.uzairansar.hermex.core.model.shouldRenderTranscriptItem
import com.uzairansar.hermex.data.preferences.ChatDisplaySettings
import com.uzairansar.hermex.data.preferences.DictationProviderPreference
import com.uzairansar.hermex.data.preferences.LocalSettingsRepository
import com.uzairansar.hermex.data.preferences.ModelFavoriteKey
import com.uzairansar.hermex.data.preferences.StreamingSendBehavior
import com.uzairansar.hermex.data.repository.WorkspaceRepository
import com.uzairansar.hermex.data.preferences.displayModelTitle
import com.uzairansar.hermex.data.preferences.favoriteKeyOrNull
import com.uzairansar.hermex.data.preferences.matchesSelection
import com.uzairansar.hermex.data.preferences.modelIdentifier
import com.uzairansar.hermex.data.preferences.normalizedProvider
import com.uzairansar.hermex.data.preferences.visibleFavoriteModels
import com.uzairansar.hermex.data.preferences.visibleRecentModels
import com.uzairansar.hermex.data.repository.ChatRepository
import com.uzairansar.hermex.data.repository.GitRepository
import com.uzairansar.hermex.data.share.SharedDraftPolicy
import com.uzairansar.hermex.data.share.SharedDraftStore
import com.uzairansar.hermex.ui.theme.HermexCardShape
import com.uzairansar.hermex.ui.theme.HermexGlassShape
import com.uzairansar.hermex.ui.theme.HermexIconButton
import com.uzairansar.hermex.ui.theme.HermexPillButton
import com.uzairansar.hermex.ui.theme.HermexSelectorPill
import com.uzairansar.hermex.ui.theme.HermexSurfaceLevel
import com.uzairansar.hermex.ui.theme.LocalHermexHapticsEnabled
import com.uzairansar.hermex.ui.git.HermexGitDiffContent
import com.uzairansar.hermex.ui.theme.hermexColorFromHex
import com.uzairansar.hermex.ui.theme.hermexGlass
import com.uzairansar.hermex.ui.theme.hermexHazeSource
import com.uzairansar.hermex.ui.theme.hermexPrimaryActionContainerColor
import com.uzairansar.hermex.ui.theme.hermexPrimaryActionContentColor
import com.uzairansar.hermex.ui.theme.primaryActionTintApplies
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import kotlinx.coroutines.isActive
import kotlinx.coroutines.withContext
import okhttp3.HttpUrl
import java.io.File
import java.text.NumberFormat
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle
import java.util.Locale
import com.uzairansar.hermex.ui.localization.localizedString
import com.uzairansar.hermex.ui.workspace.WorkspaceManagerDialog
import com.uzairansar.hermex.ui.localization.localizedStringFormat

private data class TurnDiffPresentation(
    val files: List<GitFileChange>,
    val initialPath: String? = null,
)

private enum class VoicePermissionAction {
    Dictation,
    VoiceNote,
}

@Composable
fun ChatRoute(
    sessionId: String,
    serverId: String = "",
    viewModelKey: String = "chat:$sessionId",
    repository: ChatRepository,
    gitRepository: GitRepository? = null,
    workspaceRepository: WorkspaceRepository? = null,
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
    val context = LocalContext.current
    val viewModel: ChatViewModel = viewModel(key = viewModelKey, factory = object : ViewModelProvider.Factory {
        override fun <T : ViewModel> create(modelClass: Class<T>): T {
            @Suppress("UNCHECKED_CAST")
            return ChatViewModel(
                sessionId,
                repository,
                ChatPendingStateStore(context.applicationContext, "$serverId\u0000$sessionId"),
            ) as T
        }
    })
    val state by viewModel.state.collectAsStateWithLifecycle()
    val gitViewModel: ChatGitViewModel? = gitRepository?.let { repo ->
        viewModel(
            key = "$viewModelKey:git",
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
    val dictationProviderPreference by remember(localSettingsRepository) {
        localSettingsRepository?.dictationProviderPreference ?: flowOf(DictationProviderPreference.ServerFirst)
    }.collectAsStateWithLifecycle(initialValue = DictationProviderPreference.ServerFirst)
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
    val systemLayoutDirection = LocalLayoutDirection.current
    val chatLayoutDirection = if (chatDisplaySettings.rtlChatLayoutEnabled) {
        LayoutDirection.Rtl
    } else {
        systemLayoutDirection
    }
    val sendButtonEnabled = if (state.isStreaming) true else state.draft.isNotBlank() && !state.isViewingCachedData
    val primaryActionTintColor = remember(tintPrimaryActionsWithThemeColor, activeHeaderColorHex, sendButtonEnabled) {
        if (primaryActionTintApplies(tintPrimaryActionsWithThemeColor, sendButtonEnabled)) {
            hermexColorFromHex(activeHeaderColorHex)
        } else {
            null
        }
    }
    val hapticView = LocalView.current
    val hapticsEnabled = LocalHermexHapticsEnabled.current
    val latestResponseCompletionNotificationsEnabled by rememberUpdatedState(responseCompletionNotificationsEnabled)
    val latestShowResponseExcerpts by rememberUpdatedState(chatDisplaySettings.showsStatusNotificationResponseExcerpts)
    val recorder = remember(context) { VoiceNoteRecorder(context) }
    val dictationRecorder = remember(context) { VoiceNoteRecorder(context) }
    val dictationController = remember(context) { VoiceDictationController(context) }
    val streamNotifier = remember(context) { StreamStatusNotifier(context.applicationContext) }
    val listenPlaybackController = remember(context) { ListenPlaybackController(context) }
    val listenPlaybackState by listenPlaybackController.state.collectAsStateWithLifecycle()
    var speechJob by remember { mutableStateOf<Job?>(null) }
    var dictationJob by remember { mutableStateOf<Job?>(null) }
    val speechScope = rememberCoroutineScope()
    val modelPickerScope = rememberCoroutineScope()

    LaunchedEffect(state.openSessionId) {
        val openSessionId = state.openSessionId ?: return@LaunchedEffect
        onOpenChat(openSessionId)
        viewModel.consumeOpenSession()
    }

    DisposableEffect(context) {
        onDispose {
            speechJob?.cancel()
            speechJob = null
            dictationJob?.cancel()
            dictationJob = null
            if (recorder.isRecording) viewModel.cancelVoiceNote(recorder)
            dictationRecorder.stop(delete = true)
            dictationController.cancel()
            listenPlaybackController.close()
        }
    }

    val attachmentPicker = rememberLauncherForActivityResult(ActivityResultContracts.OpenMultipleDocuments()) { uris ->
        val availableSlots = viewModel.remainingAttachmentSlots()
        if (uris.size > availableSlots) {
            Toast.makeText(context, "Attach up to ${SharedDraftPolicy.MAXIMUM_SHARED_ATTACHMENT_COUNT} files per message.", Toast.LENGTH_LONG).show()
        }
        uris.take(availableSlots).forEach { uri -> viewModel.attach(context, uri) }
    }
    val photoPicker = rememberLauncherForActivityResult(ActivityResultContracts.PickMultipleVisualMedia()) { uris ->
        val availableSlots = viewModel.remainingAttachmentSlots()
        if (uris.size > availableSlots) {
            Toast.makeText(context, "Attach up to ${SharedDraftPolicy.MAXIMUM_SHARED_ATTACHMENT_COUNT} photos per message.", Toast.LENGTH_LONG).show()
        }
        uris.take(availableSlots).forEach { uri -> viewModel.attach(context, uri) }
    }
    var pendingCameraPath by rememberSaveable { mutableStateOf<String?>(null) }
    val cameraPicker = rememberLauncherForActivityResult(ActivityResultContracts.TakePicture()) { captured ->
        val file = pendingCameraPath?.let(::File)
        pendingCameraPath = null
        if (captured && file?.isFile == true && file.length() > 0) {
            viewModel.attachCapturedPhoto(file)
        } else {
            file?.delete()
        }
    }
    val capturePhoto: () -> Unit = {
        if (viewModel.remainingAttachmentSlots() <= 0) {
            Toast.makeText(
                context,
                "Attach up to ${SharedDraftPolicy.MAXIMUM_SHARED_ATTACHMENT_COUNT} files per message.",
                Toast.LENGTH_LONG,
            ).show()
        } else {
            runCatching {
                val directory = File(context.cacheDir, "camera").apply { mkdirs() }
                val file = File.createTempFile("hermex-camera-", ".jpg", directory)
                val uri = FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", file)
                pendingCameraPath = file.absolutePath
                cameraPicker.launch(uri)
            }.onFailure { error ->
                pendingCameraPath?.let(::File)?.delete()
                pendingCameraPath = null
                Toast.makeText(context, error.message ?: "Could not open the camera.", Toast.LENGTH_LONG).show()
            }
        }
    }
    var pendingVoicePermissionAction by rememberSaveable { mutableStateOf<VoicePermissionAction?>(null) }
    var isVoiceDictating by remember { mutableStateOf(false) }
    var isVoiceDictationTranscribing by remember { mutableStateOf(false) }
    var voiceDictationError by rememberSaveable { mutableStateOf<String?>(null) }
    var voiceDictationBaseDraft by rememberSaveable { mutableStateOf("") }
    lateinit var finishServerDictation: () -> Unit
    finishServerDictation = {
        if (!dictationRecorder.isRecording || isVoiceDictationTranscribing) {
            Unit
        } else {
            val file = dictationRecorder.stop()
            if (file == null) {
                isVoiceDictating = false
                voiceDictationError = dictationRecorder.lastErrorMessage ?: "Voice dictation was empty."
            } else {
                isVoiceDictationTranscribing = true
                isVoiceDictating = true
                dictationJob = speechScope.launch {
                    try {
                        val response = repository.transcribe(file)
                        val transcript = response.transcript?.trim().orEmpty()
                        require(response.error.isNullOrBlank() && transcript.isNotEmpty()) {
                            response.error ?: "The server did not return a transcript."
                        }
                        viewModel.updateDraft(voiceDictationDraft(voiceDictationBaseDraft, transcript))
                        voiceDictationError = null
                    } catch (error: CancellationException) {
                        throw error
                    } catch (error: Throwable) {
                        voiceDictationError = error.message ?: "Could not transcribe voice input."
                    } finally {
                        runCatching { file.delete() }
                        isVoiceDictationTranscribing = false
                        isVoiceDictating = false
                        dictationJob = null
                    }
                }
            }
        }
    }
    val beginVoiceDictation: () -> Unit = {
        when {
            isVoiceDictationTranscribing -> Unit
            isVoiceDictating && dictationRecorder.isRecording -> finishServerDictation()
            isVoiceDictating -> dictationController.stop()
            else -> {
                voiceDictationBaseDraft = state.draft
                voiceDictationError = null
                when (
                    DictationProviderPolicy.primaryProvider(
                        preference = dictationProviderPreference,
                        serverConfigured = true,
                        onDeviceSupported = dictationController.isOnDeviceRecognitionAvailable,
                    )
                ) {
                    DictationProvider.Server -> runCatching {
                        dictationRecorder.start { speechScope.launch { finishServerDictation() } }
                    }.onSuccess {
                        isVoiceDictating = true
                    }.onFailure { error ->
                        voiceDictationError = error.message ?: "Could not start server dictation."
                    }
                    DictationProvider.OnDevice -> dictationController.start(
                        onDeviceOnly = true,
                        onText = { transcript, _ ->
                            viewModel.updateDraft(voiceDictationDraft(voiceDictationBaseDraft, transcript))
                        },
                        onListeningChanged = { isVoiceDictating = it },
                        onError = { voiceDictationError = it },
                    )
                    null -> voiceDictationError = "On-device dictation is not available on this device."
                }
            }
        }
    }
    val beginVoiceNote: () -> Unit = {
        if (isVoiceDictating) dictationController.cancel { isVoiceDictating = it }
        if (dictationRecorder.isRecording) dictationRecorder.stop(delete = true)
        dictationJob?.cancel()
        dictationJob = null
        isVoiceDictating = false
        isVoiceDictationTranscribing = false
        voiceDictationError = null
        viewModel.startVoiceNote(recorder)
    }
    val microphonePermission = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        val action = pendingVoicePermissionAction
        pendingVoicePermissionAction = null
        if (granted) {
            when (action) {
                VoicePermissionAction.Dictation -> beginVoiceDictation()
                VoicePermissionAction.VoiceNote -> beginVoiceNote()
                null -> Unit
            }
        } else {
            voiceDictationError = "Microphone permission is required for voice input."
        }
    }
    val requestVoiceDictation: () -> Unit = {
        if (ContextCompat.checkSelfPermission(context, Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED) {
            beginVoiceDictation()
        } else {
            pendingVoicePermissionAction = VoicePermissionAction.Dictation
            microphonePermission.launch(Manifest.permission.RECORD_AUDIO)
        }
    }
    val requestVoiceNote: () -> Unit = {
        if (ContextCompat.checkSelfPermission(context, Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED) {
            beginVoiceNote()
        } else {
            pendingVoicePermissionAction = VoicePermissionAction.VoiceNote
            microphonePermission.launch(Manifest.permission.RECORD_AUDIO)
        }
    }
    var showsModelPicker by rememberSaveable { mutableStateOf(false) }
    var showsProfilePicker by rememberSaveable { mutableStateOf(false) }
    var showsReasoningPicker by rememberSaveable { mutableStateOf(false) }
    var showsWorkspacePicker by rememberSaveable { mutableStateOf(false) }
    var showsWorkspaceManager by rememberSaveable { mutableStateOf(false) }
    var showsAttachmentOptions by rememberSaveable { mutableStateOf(false) }
    var selectedTextContext by remember { mutableStateOf<MessageActionContext?>(null) }
    var editingMessageContext by remember { mutableStateOf<MessageActionContext?>(null) }
    var editDiscardContext by remember { mutableStateOf<MessageActionContext?>(null) }
    var regenerateDiscardContext by remember { mutableStateOf<MessageActionContext?>(null) }
    var editMessageDraft by remember { mutableStateOf("") }
    var showsClearConversationConfirmation by rememberSaveable { mutableStateOf(false) }
    var turnDiffPresentation by remember { mutableStateOf<TurnDiffPresentation?>(null) }
    var autoVoiceConsumed by rememberSaveable(sessionId, autoStartVoice) { mutableStateOf(false) }
    var topBarHeightPx by remember(sessionId) { mutableIntStateOf(0) }
    var composerHeightPx by remember(sessionId) { mutableIntStateOf(0) }
    var statusStackHeightPx by remember(sessionId) { mutableIntStateOf(0) }
    val density = LocalDensity.current
    val topBarHeight = with(density) { topBarHeightPx.toDp() }.takeIf { it > 0.dp } ?: 82.dp
    val statusBarHeight = with(density) { WindowInsets.statusBars.getTop(this).toDp() }
    val statusStackHeight = with(density) { statusStackHeightPx.toDp() }
    val transcriptTopPadding =
        (topBarHeight - statusBarHeight).coerceAtLeast(0.dp) + statusStackHeight + 8.dp
    val composerHeight = with(density) { composerHeightPx.toDp() }.takeIf { it > 0.dp } ?: 160.dp
    val transcriptListState = rememberLazyListState()
    val isTranscriptDragged by transcriptListState.interactionSource.collectIsDraggedAsState()
    var followsTranscriptBottom by remember(sessionId) { mutableStateOf(true) }
    var transcriptScrollCooldownActive by remember(sessionId) { mutableStateOf(false) }
    var isReadingOlderTranscript by remember(sessionId) { mutableStateOf(false) }
    val nearBottomTolerancePx = with(density) {
        (if (state.isStreaming) 160.dp else 80.dp).roundToPx()
    }
    val readingOlderHysteresisPx = with(density) { 64.dp.roundToPx() }
    val isTranscriptAtBottom by remember(sessionId, transcriptListState) {
        derivedStateOf {
            val layout = transcriptListState.layoutInfo
            val lastVisible = layout.visibleItemsInfo.lastOrNull()
            isTranscriptBottomVisible(
                totalItemsCount = layout.totalItemsCount,
                lastVisibleIndex = lastVisible?.index ?: -1,
                lastVisibleOffset = lastVisible?.offset ?: 0,
                lastVisibleSize = lastVisible?.size ?: 0,
                viewportEndOffset = layout.viewportEndOffset,
            )
        }
    }
    val isTranscriptNearBottomNow by remember(sessionId, transcriptListState, nearBottomTolerancePx) {
        derivedStateOf {
            val layout = transcriptListState.layoutInfo
            val lastVisible = layout.visibleItemsInfo.lastOrNull()
            isTranscriptNearBottom(
                totalItemsCount = layout.totalItemsCount,
                lastVisibleIndex = lastVisible?.index ?: -1,
                lastVisibleOffset = lastVisible?.offset ?: 0,
                lastVisibleSize = lastVisible?.size ?: 0,
                viewportEndOffset = layout.viewportEndOffset,
                tolerancePixels = nearBottomTolerancePx,
            )
        }
    }

    LaunchedEffect(isTranscriptDragged) {
        if (isTranscriptDragged) {
            transcriptScrollCooldownActive = true
        } else {
            delay(TRANSCRIPT_USER_SCROLL_COOLDOWN_MILLIS)
            transcriptScrollCooldownActive = false
        }
    }

    LaunchedEffect(transcriptListState, isTranscriptDragged, nearBottomTolerancePx, readingOlderHysteresisPx) {
        snapshotFlow {
            val layout = transcriptListState.layoutInfo
            val lastVisible = layout.visibleItemsInfo.lastOrNull()
            val isNearBottom = isTranscriptNearBottom(
                totalItemsCount = layout.totalItemsCount,
                lastVisibleIndex = lastVisible?.index ?: -1,
                lastVisibleOffset = lastVisible?.offset ?: 0,
                lastVisibleSize = lastVisible?.size ?: 0,
                viewportEndOffset = layout.viewportEndOffset,
                tolerancePixels = nearBottomTolerancePx,
            )
            val distanceFromBottom = when {
                layout.totalItemsCount == 0 -> 0
                lastVisible?.index != layout.totalItemsCount - 1 -> Int.MAX_VALUE
                else -> (lastVisible.offset + lastVisible.size - layout.viewportEndOffset).coerceAtLeast(0)
            }
            TranscriptScrollMetrics(
                observation = TranscriptScrollObservation(
                    isUserDragging = isTranscriptDragged,
                    lastScrolledBackward = transcriptListState.lastScrolledBackward,
                    isNearBottom = isNearBottom,
                ),
                distanceFromBottomPixels = distanceFromBottom,
            )
        }.collect { metrics ->
            followsTranscriptBottom = transcriptFollowState(
                currentlyFollowing = followsTranscriptBottom,
                observation = metrics.observation,
            )
            isReadingOlderTranscript = transcriptReadingOlderState(
                currentlyReadingOlder = isReadingOlderTranscript,
                isNearBottom = metrics.observation.isNearBottom,
                distanceFromBottomPixels = metrics.distanceFromBottomPixels,
                nearBottomTolerancePixels = nearBottomTolerancePx,
                hysteresisPixels = readingOlderHysteresisPx,
            )
        }
    }

    LaunchedEffect(
        state.messages.size,
        state.messages.lastOrNull()?.displayText?.length,
        state.liveReasoning,
        state.liveToolActivity,
        state.responseCompletionTrigger,
        state.isLoading,
        composerHeightPx,
        statusStackHeightPx,
        transcriptScrollCooldownActive,
    ) {
        if (!shouldAutoScrollTranscript(
                followsBottom = followsTranscriptBottom,
                isScrollInProgress = transcriptListState.isScrollInProgress,
                isUserScrollCooldownActive = transcriptScrollCooldownActive,
            )
        ) {
            return@LaunchedEffect
        }
        delay(32)
        if (!shouldAutoScrollTranscript(
                followsBottom = followsTranscriptBottom,
                isScrollInProgress = transcriptListState.isScrollInProgress,
                isUserScrollCooldownActive = transcriptScrollCooldownActive,
            )
        ) {
            return@LaunchedEffect
        }
        val lastItem = transcriptListState.layoutInfo.totalItemsCount - 1
        if (lastItem >= 0) transcriptListState.animateScrollToItem(lastItem)
    }

    LaunchedEffect(isTranscriptAtBottom, followsTranscriptBottom, transcriptScrollCooldownActive) {
        if (
            isTranscriptAtBottom ||
            !followsTranscriptBottom ||
            transcriptListState.isScrollInProgress ||
            transcriptScrollCooldownActive
        ) {
            return@LaunchedEffect
        }
        // AndroidView-backed Markdown can grow after the message-count effect fires.
        // Re-anchor once the next frame has committed the measured height.
        withFrameNanos { }
        if (
            followsTranscriptBottom &&
            !transcriptListState.isScrollInProgress &&
            !transcriptScrollCooldownActive
        ) {
            val lastItem = transcriptListState.layoutInfo.totalItemsCount - 1
            if (lastItem >= 0) transcriptListState.scrollToItem(lastItem, Int.MAX_VALUE)
        }
    }

    LaunchedEffect(transcriptListState, followsTranscriptBottom, transcriptScrollCooldownActive) {
        snapshotFlow { transcriptListState.isScrollInProgress }.collect { isScrollInProgress ->
            if (
                isScrollInProgress ||
                !followsTranscriptBottom ||
                transcriptScrollCooldownActive ||
                isTranscriptAtBottom
            ) {
                return@collect
            }
            // Content can grow while an earlier follow animation is still running.
            // Catch up after it settles so the transcript cannot remain one layout behind.
            withFrameNanos { }
            if (
                followsTranscriptBottom &&
                !transcriptListState.isScrollInProgress &&
                !transcriptScrollCooldownActive
            ) {
                val lastItem = transcriptListState.layoutInfo.totalItemsCount - 1
                if (lastItem >= 0) transcriptListState.scrollToItem(lastItem, Int.MAX_VALUE)
            }
        }
    }

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
    val listenText: (MessageActionContext?, String) -> Unit = { actionContext, fallbackText ->
        val text = actionContext?.listenText ?: fallbackText
        val messageId = actionContext?.messageId ?: "assistant-${text.hashCode()}"
        speechJob?.cancel()
        if (listenPlaybackState.activeMessageId == messageId) {
            listenPlaybackController.stop()
        } else {
            val requestGeneration = listenPlaybackController.beginLoading(messageId)
            speechJob = speechScope.launch {
                val audio = if (ServerTtsPolicy.shouldUseServer(text)) {
                    viewModel.synthesizeSpeech(text)
                } else {
                    null
                }
                if (!isActive || !listenPlaybackController.isCurrent(requestGeneration, messageId)) {
                    return@launch
                }
                val played = audio?.takeIf { it.isNotEmpty() }?.let { bytes ->
                    listenPlaybackController.startServerAudio(
                        requestGeneration = requestGeneration,
                        messageId = messageId,
                        title = "Hermex response",
                        audio = bytes,
                    )
                } == true
                if (!played && isActive) {
                    listenPlaybackController.startOnDevice(requestGeneration, messageId, text)
                }
            }
        }
    }

    LaunchedEffect(listenPlaybackState.phase) {
        while (listenPlaybackState.phase == ListenPlaybackPhase.Playing && listenPlaybackState.hasSeekableAudio) {
            delay(200)
            listenPlaybackController.refreshProgress()
        }
    }
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
            sharedDraftStore?.loadPendingDraft(removeAfterLoad = false)?.let { draft ->
                viewModel.consumeSharedDraft(context, draft) { remainingAttachments ->
                    sharedDraftStore.commitImportedDraft(draft.createdAtEpochMillis, remainingAttachments)
                }
            }
        }
    }

    LaunchedEffect(autoStartVoice, autoVoiceConsumed) {
        if (autoStartVoice && !autoVoiceConsumed) {
            autoVoiceConsumed = true
            requestVoiceDictation()
        }
    }

    LaunchedEffect(state.isStreaming) {
        if (state.isStreaming) {
            streamNotifier.monitor(
                serverId = serverId,
                sessionId = sessionId,
                state = viewModel.state,
                completionNotificationsEnabled = { latestResponseCompletionNotificationsEnabled },
                showResponseExcerpts = { latestShowResponseExcerpts },
            )
        }
    }

    DisposableEffect(streamNotifier, serverId, sessionId) {
        onDispose { streamNotifier.release(serverId, sessionId) }
    }

    LaunchedEffect(state.responseCompletionTrigger) {
        if (state.responseCompletionTrigger > 0) viewModel.refreshCompletedTranscriptIfNeeded()
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
                        ChatHapticEvent.ResponseCompleted -> hapticView.performHapticFeedback(
                            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                                HapticFeedbackConstants.CONFIRM
                            } else {
                                HapticFeedbackConstants.LONG_PRESS
                            },
                        )
                        ChatHapticEvent.StreamCancelled -> hapticView.performHapticFeedback(HapticFeedbackConstants.LONG_PRESS)
                        ChatHapticEvent.None -> Unit
                    }
                }
                previousIsStreaming = isStreaming
                previousCompletionTrigger = completionTrigger
            }
    }

    LaunchedEffect(gitViewModel, state.responseCompletionTrigger, state.isStreaming) {
        if (!state.isStreaming) {
            gitViewModel?.refresh()
        }
    }

    Box(
        modifier = Modifier.fillMaxSize(),
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .imePadding(),
        ) {
            CompositionLocalProvider(LocalLayoutDirection provides chatLayoutDirection) {
                Column(Modifier.fillMaxSize()) {
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
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(top = statusBarHeight)
                        .testTag("chat_transcript")
                        .hermexHazeSource(key = "chat-transcript"),
                    state = transcriptListState,
                    contentPadding = PaddingValues(
                        start = 14.dp,
                        end = 14.dp,
                        top = transcriptTopPadding,
                        bottom = composerHeight + 40.dp,
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
                                    showsResponseSpeed = chatDisplaySettings.showsResponseSpeed,
                                    wrapsCodeBlockLines = chatDisplaySettings.wrapsCodeBlockLines,
                                    streamedTextAnimationEnabled = chatDisplaySettings.streamedTextAnimationEnabled,
                                    loadTranscriptMediaImage = viewModel::transcriptMediaThumbnailData,
                                    loadAttachmentFile = viewModel::attachmentTextFile,
                                    actionContext = actionContext,
                                    isListening = listenPlaybackState.activeMessageId == actionContext?.messageId,
                                    messageActionEnabled = !state.isViewingCachedData && !state.isStreaming && !state.isRunningSessionAction,
                                    isRegeneratingMessage = state.isRegeneratingMessage,
                                    isEditingMessage = state.isEditingMessage,
                                    isForkingMessage = state.isForkingMessage,
                                    onCopy = { copyText(actionContext?.copyText ?: visibleText) },
                                    onListen = { listenText(actionContext, visibleText) },
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
                            if (chatDisplaySettings.showsChatGitControls && turnChangesAnchorIndex == index) {
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
                    item("transcript-bottom-anchor") {
                        Spacer(Modifier.height(1.dp))
                    }
                }
            }
            }
        }
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(
                        top = topBarHeight + 8.dp,
                        bottom = composerHeight + 8.dp,
                    ),
                contentAlignment = Alignment.TopCenter,
            ) {
                CompositionLocalProvider(LocalLayoutDirection provides chatLayoutDirection) {
                    ChatStatusStack(
                        state = state,
                        onApprovalChoice = viewModel::respondApproval,
                        onSkipApprovals = viewModel::skipApprovalsForCurrentSession,
                        onClarificationDraftChange = viewModel::updateClarificationDraft,
                        onClarificationSubmit = viewModel::respondClarification,
                        onClarificationChoice = { choice -> viewModel.respondClarification(choice) },
                        onRetryUploads = viewModel::retryPendingLocalUploads,
                        modifier = Modifier
                            .fillMaxWidth()
                            .verticalScroll(rememberScrollState())
                            .onSizeChanged { statusStackHeightPx = it.height }
                            .testTag("chat_status_stack"),
                    )
                }
            }
            Box(
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .fillMaxWidth()
                    .height(composerHeight + 64.dp)
                    .background(
                        Brush.verticalGradient(
                            0f to Color.Transparent,
                            0.46f to MaterialTheme.colorScheme.background.copy(alpha = 0.68f),
                            1f to MaterialTheme.colorScheme.background,
                        ),
                    ),
            )
            Box(
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .fillMaxWidth()
                    .navigationBarsPadding()
                    .onSizeChanged { composerHeightPx = it.height },
            ) {
                CompositionLocalProvider(LocalLayoutDirection provides chatLayoutDirection) {
                    Column {
                        if (listenPlaybackState.showsPlaybackBar) {
                            ListenPlaybackBar(
                                state = listenPlaybackState,
                                onTogglePlayPause = listenPlaybackController::togglePlayPause,
                                onSeek = listenPlaybackController::seekTo,
                                onSpeedChange = listenPlaybackController::setSpeed,
                                onStop = {
                                    speechJob?.cancel()
                                    speechJob = null
                                    listenPlaybackController.stop()
                                },
                            )
                        }
                        ComposerSurface(
                            state = state,
                            isVoiceDictating = isVoiceDictating,
                            isVoiceDictationTranscribing = isVoiceDictationTranscribing,
                            voiceDictationError = voiceDictationError,
                            streamingSendBehavior = streamingSendBehavior,
                            primaryActionTintColor = primaryActionTintColor,
                            showSecondaryBar = !isReadingOlderTranscript,
                            onDraftChange = viewModel::updateDraft,
                            onSend = {
                                followsTranscriptBottom = true
                                transcriptScrollCooldownActive = false
                                isReadingOlderTranscript = false
                                viewModel.send()
                            },
                            onStreamingSend = {
                                followsTranscriptBottom = true
                                transcriptScrollCooldownActive = false
                                isReadingOlderTranscript = false
                                viewModel.submitStreamingDraft(streamingSendBehavior)
                            },
                            onCancel = viewModel::cancel,
                            onOpenModelPicker = { showsModelPicker = true },
                            onOpenProfilePicker = { showsProfilePicker = true },
                            onOpenReasoningPicker = { showsReasoningPicker = true },
                            onOpenWorkspacePicker = { showsWorkspacePicker = true },
                            onLoadWorkspaceSuggestions = viewModel::loadWorkspaceSuggestions,
                            onAttach = { showsAttachmentOptions = true },
                            onVoiceDictation = requestVoiceDictation,
                            onVoiceNote = requestVoiceNote,
                            onStopVoiceNote = {
                                followsTranscriptBottom = true
                                transcriptScrollCooldownActive = false
                                isReadingOlderTranscript = false
                                viewModel.stopAndSendVoiceNote(recorder)
                            },
                            onCancelVoice = { viewModel.cancelVoiceNote(recorder) },
                            onRemoveAttachment = viewModel::removeAttachment,
                            loadAttachmentImage = viewModel::attachmentImageData,
                            loadAttachmentFile = viewModel::attachmentTextFile,
                        )
                    }
                }
            }
            if (!isTranscriptNearBottomNow && (!state.isStreaming || !followsTranscriptBottom)) {
                HermexIconButton(
                    label = localizedString("Go to latest message"),
                    symbol = "↓",
                    onClick = {
                        followsTranscriptBottom = true
                        transcriptScrollCooldownActive = false
                        isReadingOlderTranscript = false
                        val lastItem = transcriptListState.layoutInfo.totalItemsCount - 1
                        if (lastItem >= 0) {
                            speechScope.launch {
                                transcriptListState.scrollToItem(lastItem, Int.MAX_VALUE)
                            }
                        }
                    },
                    modifier = Modifier
                        .align(Alignment.BottomCenter)
                        .padding(bottom = composerHeight + 20.dp)
                        .size(48.dp)
                        .testTag("chat_scroll_to_bottom"),
                )
            }
        }
        ChatTopBar(
            title = state.headerTitle,
            subtitle = state.headerSubtitle,
            hasRepository = gitState.hasRepository,
            showsFilesButton = chatDisplaySettings.showsChatFilesButton,
            showsGitControls = chatDisplaySettings.showsChatGitControls,
            onBack = onBack,
            onOpenWorkspace = onOpenWorkspace,
            onOpenGit = onOpenGit,
            canClearConversation = state.messages.isNotEmpty() &&
                !state.isStreaming &&
                !state.isRunningSessionAction &&
                !state.isViewingCachedData,
            onClearConversation = { showsClearConversationConfirmation = true },
            modifier = Modifier.onSizeChanged { topBarHeightPx = it.height },
        )
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
    state.pendingProfileSwitch?.let { pending ->
        StartNewProfileSessionDialog(
            profileTitle = pending.profile.displayTitle,
            onDismiss = viewModel::dismissPendingProfileSwitch,
            onConfirm = viewModel::confirmProfileSwitchStartingNewSession,
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
            onManage = workspaceRepository?.let {
                {
                    showsWorkspacePicker = false
                    showsWorkspaceManager = true
                }
            },
            onSelect = { path ->
                showsWorkspacePicker = false
                viewModel.selectWorkspace(path)
            },
        )
    }
    if (showsWorkspaceManager && workspaceRepository != null) {
        WorkspaceManagerDialog(
            viewModelKey = "workspace-manager:${repository.serverUrl}",
            repository = workspaceRepository,
            onDismiss = { showsWorkspaceManager = false },
            onRegistryChanged = viewModel::loadComposerConfig,
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
            onCamera = {
                showsAttachmentOptions = false
                capturePhoto()
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
    val removeDescription = localizedStringFormat("Remove attachment %@", attachment.displayName)
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
        IconButton(
            onClick = onRemove,
            modifier = Modifier
                .align(Alignment.TopEnd)
                .size(48.dp)
                .clip(CircleShape)
                .background(MaterialTheme.colorScheme.background)
                .border(0.5.dp, MaterialTheme.colorScheme.outlineVariant, CircleShape)
                .semantics { contentDescription = removeDescription },
        ) {
            Text(
                text = "X",
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurface,
                fontWeight = FontWeight.Bold,
            )
        }
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
    var remoteLoadApproved by remember(path) { mutableStateOf(false) }
    val remoteUrl = remember(path) {
        path?.let { (TranscriptMediaReference(it).source as? TranscriptMediaSource.RemoteUrl)?.url }
    }
    val isRemote = remoteUrl != null
    val remoteLoadBlocked = remoteUrl?.scheme != null && remoteUrl.scheme != "https"
    LaunchedEffect(path, remoteLoadApproved) {
        bytes = null
        didAttemptLoad = false
        if (isRemote && (!remoteLoadApproved || remoteLoadBlocked)) return@LaunchedEffect
        val resolvedPath = path?.takeIf { it.isNotBlank() }
        if (resolvedPath != null) {
            bytes = loadAttachmentImage(resolvedPath)
        }
        didAttemptLoad = true
    }
    val bitmap = rememberDecodedBitmap(bytes, maxDimension = 1_024, maxPixels = 1_500_000L)
    val shape = RoundedCornerShape(cornerRadius)
    Box(
        modifier = Modifier
            .size(size)
            .clip(shape)
            .clickable(enabled = !remoteLoadBlocked) {
                if (isRemote && !remoteLoadApproved && !remoteLoadBlocked) remoteLoadApproved = true else if (!remoteLoadBlocked) onPreview()
            }
            .background(MaterialTheme.colorScheme.surfaceVariant)
            .border(0.5.dp, MaterialTheme.colorScheme.outlineVariant, shape),
        contentAlignment = Alignment.Center,
    ) {
        when {
            remoteLoadBlocked -> Text(
                "${localizedString("Remote")}\nHTTP",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.error,
                textAlign = androidx.compose.ui.text.style.TextAlign.Center,
            )
            isRemote && !remoteLoadApproved -> Text(
                localizedString("Load remote image"),
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = androidx.compose.ui.text.style.TextAlign.Center,
            )
            bitmap != null -> {
                Image(
                    bitmap = bitmap.asImageBitmap(),
                    contentDescription = localizedString("Image"),
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.fillMaxSize(),
                )
            }
            !didAttemptLoad -> CircularProgressIndicator(strokeWidth = 2.dp, modifier = Modifier.size(22.dp))
            else -> Text(
                localizedString("Image"),
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
                    message = localizedString("Preview is not available for this file type."),
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
    hasRepository: Boolean,
    showsFilesButton: Boolean,
    showsGitControls: Boolean,
    onBack: () -> Unit,
    onOpenWorkspace: () -> Unit,
    onOpenGit: () -> Unit,
    canClearConversation: Boolean,
    onClearConversation: () -> Unit,
    modifier: Modifier = Modifier,
) {
    var actionsExpanded by rememberSaveable { mutableStateOf(false) }
    Box(
        modifier = modifier
            .fillMaxWidth()
            .statusBarsPadding()
            .hermexGlass(
                shape = RectangleShape,
                castsShadow = false,
                surfaceLevel = HermexSurfaceLevel.Base,
                tintEnabled = false,
                drawsBorder = false,
                noiseFactor = 0f,
                blurRadius = 10.dp,
            )
            .background(MaterialTheme.colorScheme.background.copy(alpha = 0.68f))
            .padding(horizontal = 14.dp, vertical = 4.dp)
            .testTag("chat_top_bar"),
    ) {
        HermexIconButton(
            label = localizedString("Back"),
            symbol = "\u2039",
            onClick = onBack,
            modifier = Modifier.align(Alignment.CenterStart),
        )
        Column(
            modifier = Modifier
                .align(Alignment.Center)
                .fillMaxWidth()
                .padding(
                    horizontal = when {
                        showsFilesButton && showsGitControls && hasRepository -> 152.dp
                        showsFilesButton || (showsGitControls && hasRepository) -> 108.dp
                        else -> 64.dp
                    },
                ),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Text(
                title,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            if (!subtitle.isNullOrBlank()) {
                Text(
                    subtitle,
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.secondary,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                )
            }
        }
        Row(
            modifier = Modifier
                .align(Alignment.CenterEnd)
                .hermexGlass(shape = CircleShape, castsShadow = false)
                .padding(1.dp),
        ) {
            if (showsFilesButton) {
                HermexIconButton(
                    label = localizedString("Files"),
                    symbol = "\u2302",
                    onClick = onOpenWorkspace,
                    tonalContainerColor = Color.Transparent,
                    modifier = Modifier.size(44.dp),
                )
            }
            if (showsGitControls && hasRepository) {
                HermexIconButton(
                    label = localizedString("Git"),
                    symbol = "Git",
                    onClick = onOpenGit,
                    tonalContainerColor = Color.Transparent,
                    modifier = Modifier.size(44.dp),
                )
            }
            Box {
                HermexIconButton(
                    label = localizedString("Session actions"),
                    symbol = "\u22ef",
                    onClick = { actionsExpanded = true },
                    tonalContainerColor = Color.Transparent,
                    modifier = Modifier.size(44.dp),
                )
                DropdownMenu(
                    expanded = actionsExpanded,
                    onDismissRequest = { actionsExpanded = false },
                ) {
                    DropdownMenuItem(
                        text = { Text(localizedString("Clear conversation")) },
                        enabled = canClearConversation,
                        onClick = {
                            actionsExpanded = false
                            onClearConversation()
                        },
                    )
                }
            }
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
    onRetryUploads: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier
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
        if (state.pendingLocalUploadCount > 0 && !state.isUploadingAttachment) {
            HermexPillButton(
                label = localizedString("Retry"),
                onClick = onRetryUploads,
            )
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
    val loadingDescription = localizedString("Loading messages")
    Box(
        modifier = Modifier
            .fillMaxSize()
            .padding(bottom = 132.dp)
            .semantics { contentDescription = loadingDescription },
        contentAlignment = Alignment.Center,
    ) {
        CircularProgressIndicator(
            modifier = Modifier.size(22.dp),
            strokeWidth = 2.dp,
            color = MaterialTheme.colorScheme.secondary.copy(alpha = 0.72f),
            trackColor = Color.Transparent,
        )
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
                localizedString(if (isLoading) "Loading older messages" else "Load older messages"),
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
    val errorDescription = localizedString("Could Not Load Messages")
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 32.dp)
            .semantics { contentDescription = errorDescription },
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
            localizedString("Could Not Load Messages"),
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.SemiBold,
            color = MaterialTheme.colorScheme.onSurface,
        )
        Spacer(Modifier.height(6.dp))
        Text(
            localizedString(errorMessage.ifBlank { "Something went wrong." }),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.secondary,
        )
        Spacer(Modifier.height(14.dp))
        HermexPillButton(
            label = localizedString("Try Again"),
            onClick = onRetry,
        )
    }
}

@Composable
private fun ChatTranscriptEmptyState() {
    val emptyDescription = localizedString("Send a message to start the conversation.")
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 32.dp)
            .semantics { contentDescription = emptyDescription },
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Image(
            painter = painterResource(com.uzairansar.hermex.R.drawable.ic_hermex_chat_bubbles),
            contentDescription = null,
            modifier = Modifier.size(52.dp),
            colorFilter = ColorFilter.tint(MaterialTheme.colorScheme.secondary.copy(alpha = 0.86f)),
        )
        Spacer(Modifier.height(18.dp))
        Text(
            localizedString("Send a message to start the conversation."),
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.SemiBold,
            color = MaterialTheme.colorScheme.secondary,
            textAlign = androidx.compose.ui.text.style.TextAlign.Center,
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
private fun SlashAutocompleteSurface(
    result: SlashAutocompleteResult,
    onSelect: (SlashAutocompleteSuggestion) -> Unit,
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(max = 280.dp)
            .hermexGlass(
                shape = RoundedCornerShape(16.dp),
                surfaceLevel = HermexSurfaceLevel.Floating,
            ),
    ) {
        if (result.suggestions.isEmpty()) {
            Text(
                text = result.emptyMessage ?: "No matching commands",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 20.dp),
            )
        } else {
            LazyColumn(
                modifier = Modifier.heightIn(max = 280.dp),
            ) {
                itemsIndexed(
                    items = result.suggestions,
                    key = { _, suggestion -> suggestion.key },
                ) { index, suggestion ->
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .heightIn(min = 48.dp)
                            .clickable { onSelect(suggestion) }
                            .padding(horizontal = 16.dp, vertical = 10.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(10.dp),
                    ) {
                        Row(
                            modifier = Modifier.weight(1f),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                        ) {
                            Text(
                                text = suggestion.label,
                                style = MaterialTheme.typography.bodyMedium,
                                fontWeight = FontWeight.SemiBold,
                                fontFamily = if (suggestion.kind == SlashAutocompleteSuggestionKind.Argument) {
                                    FontFamily.Default
                                } else {
                                    FontFamily.Monospace
                                },
                                color = if (suggestion.isSelected) {
                                    MaterialTheme.colorScheme.primary
                                } else {
                                    MaterialTheme.colorScheme.onSurface
                                },
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                                modifier = Modifier.weight(1f, fill = false),
                            )
                            suggestion.argumentHint?.let { hint ->
                                Text(
                                    text = hint,
                                    style = MaterialTheme.typography.labelMedium,
                                    fontFamily = FontFamily.Monospace,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                )
                            }
                        }
                        Text(
                            text = if (suggestion.isSelected) localizedString("Current") else suggestion.detail,
                            style = MaterialTheme.typography.labelMedium,
                            color = if (suggestion.isSelected) {
                                MaterialTheme.colorScheme.primary
                            } else {
                                MaterialTheme.colorScheme.onSurfaceVariant
                            },
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.widthIn(max = 144.dp),
                        )
                    }
                    if (index < result.suggestions.lastIndex) {
                        HorizontalDivider(
                            modifier = Modifier.padding(horizontal = 16.dp),
                            color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.42f),
                        )
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun ComposerSurface(
    state: ChatUiState,
    isVoiceDictating: Boolean,
    isVoiceDictationTranscribing: Boolean,
    voiceDictationError: String?,
    streamingSendBehavior: StreamingSendBehavior,
    primaryActionTintColor: Color?,
    showSecondaryBar: Boolean,
    onDraftChange: (String) -> Unit,
    onSend: () -> Unit,
    onStreamingSend: () -> Unit,
    onCancel: () -> Unit,
    onOpenModelPicker: () -> Unit,
    onOpenProfilePicker: () -> Unit,
    onOpenReasoningPicker: () -> Unit,
    onOpenWorkspacePicker: () -> Unit,
    onLoadWorkspaceSuggestions: (String) -> Unit,
    onAttach: () -> Unit,
    onVoiceDictation: () -> Unit,
    onVoiceNote: () -> Unit,
    onStopVoiceNote: () -> Unit,
    onCancelVoice: () -> Unit,
    onRemoveAttachment: (UploadResponse) -> Unit,
    loadAttachmentImage: suspend (String) -> ByteArray?,
    loadAttachmentFile: suspend (String) -> FileResponse?,
) {
    var previewAttachment by remember { mutableStateOf<UploadResponse?>(null) }
    val messageDescription = localizedString("message").replaceFirstChar { character ->
        if (character.isLowerCase()) character.titlecase() else character.toString()
    }
    val isImeVisible = WindowInsets.isImeVisible
    val slashAutocompleteContext = remember(
        state.modelOptions,
        state.profileOptions,
        state.reasoningOptions,
        state.workspaceRoots,
        state.workspaceSuggestions,
        state.skillSuggestions,
        state.agentCommands,
        state.selectedModel,
        state.selectedProfile,
        state.selectedReasoning,
        state.selectedWorkspacePath,
    ) {
        state.slashAutocompleteContext()
    }
    val slashAutocompleteResult = remember(state.draft, slashAutocompleteContext) {
        SlashAutocompletePolicy.evaluate(state.draft, slashAutocompleteContext)
    }
    val slashWorkspaceQuery = remember(state.draft) {
        SlashAutocompletePolicy.workspaceArgumentQuery(state.draft)
    }
    LaunchedEffect(slashWorkspaceQuery) {
        val query = slashWorkspaceQuery ?: return@LaunchedEffect
        delay(160)
        onLoadWorkspaceSuggestions(query)
    }
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 14.dp, vertical = 8.dp)
            .testTag("chat_composer"),
        verticalArrangement = Arrangement.spacedBy(7.dp),
    ) {
        when {
            state.isRecordingVoiceNote -> ComposerVoiceRecordingStatus(
                startedAtMillis = state.voiceNoteStartedAtMillis,
                onStop = onStopVoiceNote,
                onCancel = onCancelVoice,
            )
            state.isTranscribingVoiceNote -> ComposerVoiceTranscribingStatus()
            isVoiceDictationTranscribing -> ComposerVoiceDictationStatus("Transcribing...", isError = false)
            isVoiceDictating -> ComposerVoiceDictationStatus("Listening...", isError = false)
            voiceDictationError != null -> ComposerVoiceDictationStatus(voiceDictationError, isError = true)
        }
        if (slashAutocompleteResult.isVisible) {
            SlashAutocompleteSurface(
                result = slashAutocompleteResult,
                onSelect = { suggestion -> onDraftChange(suggestion.replacement) },
            )
        }
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .hermexGlass(
                    shape = HermexGlassShape,
                    surfaceLevel = HermexSurfaceLevel.Floating,
                ),
        ) {
            if (state.pendingAttachments.isNotEmpty()) {
                ComposerAttachmentStrip(
                    attachments = state.pendingAttachments,
                    onRemove = onRemoveAttachment,
                    onPreview = { previewAttachment = it },
                    loadAttachmentImage = loadAttachmentImage,
                )
            }
            BasicTextField(
                value = state.draft,
                onValueChange = onDraftChange,
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(min = 52.dp, max = 132.dp)
                    .padding(horizontal = 18.dp, vertical = 14.dp)
                    .semantics { contentDescription = messageDescription },
                enabled = !state.isViewingCachedData,
                textStyle = MaterialTheme.typography.bodyLarge.copy(
                    color = if (state.isViewingCachedData) {
                        MaterialTheme.colorScheme.secondary
                    } else {
                        MaterialTheme.colorScheme.onSurface
                    },
                ),
                cursorBrush = SolidColor(MaterialTheme.colorScheme.primary),
                decorationBox = { innerTextField ->
                    Box(
                        modifier = Modifier.fillMaxWidth(),
                        contentAlignment = Alignment.TopStart,
                    ) {
                        if (state.draft.isEmpty()) {
                            Text(
                                localizedString(if (state.isViewingCachedData) "Reconnect to send messages." else "Ask anything... /commands"),
                                style = MaterialTheme.typography.bodyLarge,
                                color = MaterialTheme.colorScheme.secondary,
                            )
                        }
                        innerTextField()
                    }
                },
            )
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 10.dp)
                    .padding(top = 2.dp, bottom = 8.dp),
                horizontalArrangement = Arrangement.spacedBy(4.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                ComposerInlineIconButton(
                    label = localizedString("Attach"),
                    iconRes = com.uzairansar.hermex.R.drawable.ic_hermex_plus,
                    onClick = onAttach,
                    enabled = !state.isUploadingAttachment && !state.isStreaming && !state.isViewingCachedData,
                )
                HermexSelectorPill(
                    label = state.selectedModel?.label ?: state.selectedModel?.name ?: state.selectedModel?.id ?: "Model",
                    onClick = onOpenModelPicker,
                    enabled = (state.selectedModel != null || state.modelOptions.isNotEmpty()) &&
                        !state.isStreaming && !state.isViewingCachedData && !state.isRunningSessionAction,
                    modifier = Modifier.weight(1f),
                    glassed = false,
                    contentPadding = PaddingValues(horizontal = 4.dp, vertical = 10.dp),
                )
                if (state.showsReasoningControl) {
                    HermexSelectorPill(
                        label = ReasoningEffortOption.titleFor(state.selectedReasoning),
                        onClick = onOpenReasoningPicker,
                        enabled = state.reasoningOptions.isNotEmpty() && !state.isStreaming && !state.isViewingCachedData && !state.isRunningSessionAction,
                        leadingIcon = com.uzairansar.hermex.R.drawable.ic_lucide_brain,
                        minWidth = 84.dp,
                        maxWidth = 100.dp,
                        glassed = false,
                        contentPadding = PaddingValues(horizontal = 2.dp, vertical = 10.dp),
                    )
                }
                ComposerInlineIconButton(
                    label = localizedString("Voice dictation. Long press to record a voice note."),
                    iconRes = com.uzairansar.hermex.R.drawable.ic_hermex_mic,
                    onClick = onVoiceDictation,
                    onLongClick = onVoiceNote,
                    enabled = !state.isStreaming && !state.isViewingCachedData && !state.isRecordingVoiceNote && !state.isTranscribingVoiceNote && !state.isRunningSessionAction,
                )
                HermexIconButton(
                    label = localizedString(if (state.isStreaming) "Stop" else "Send"),
                    symbol = if (state.isStreaming) "■" else "↑",
                    onClick = if (state.isStreaming) onCancel else onSend,
                    enabled = if (state.isStreaming) {
                        true
                    } else {
                        state.draft.isNotBlank() &&
                            !state.isViewingCachedData &&
                            !state.isRunningSessionAction &&
                            !state.isUploadingAttachment &&
                            !state.isRecordingVoiceNote &&
                            !state.isTranscribingVoiceNote
                    },
                    filled = true,
                    filledContainerColor = hermexPrimaryActionContainerColor(
                        if (state.isStreaming) true else state.draft.isNotBlank() && !state.isViewingCachedData && !state.isRunningSessionAction && !state.isUploadingAttachment && !state.isRecordingVoiceNote && !state.isTranscribingVoiceNote,
                        primaryActionTintColor,
                    ),
                    filledContentColor = hermexPrimaryActionContentColor(
                        if (state.isStreaming) true else state.draft.isNotBlank() && !state.isViewingCachedData && !state.isRunningSessionAction && !state.isUploadingAttachment && !state.isRecordingVoiceNote && !state.isTranscribingVoiceNote,
                        primaryActionTintColor,
                    ),
                    modifier = Modifier.size(48.dp),
                )
            }
            if (state.isStreaming && state.draft.isNotBlank()) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(start = 12.dp, end = 12.dp, bottom = 10.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.End),
                ) {
                    if (state.draft.trimStart().startsWith("/queue", ignoreCase = true)) {
                        HermexPillButton(localizedString("Queue"), onSend, enabled = !state.isRunningSessionAction, filled = true)
                    }
                    HermexPillButton(
                        streamingSendBehavior.actionLabel,
                        onStreamingSend,
                        enabled = !state.isRunningSessionAction,
                        filled = true,
                    )
                }
            }
        }
        if (!isImeVisible && showSecondaryBar) {
            ComposerSecondaryBar(
                state = state,
                onOpenWorkspacePicker = onOpenWorkspacePicker,
                onOpenProfilePicker = onOpenProfilePicker,
            )
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

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun ComposerInlineIconButton(
    label: String,
    iconRes: Int,
    onClick: () -> Unit,
    onLongClick: (() -> Unit)? = null,
    enabled: Boolean,
) {
    Image(
        painter = painterResource(iconRes),
        contentDescription = label,
        modifier = Modifier
            .size(48.dp)
            .clip(CircleShape)
            .then(
                if (onLongClick == null) {
                    Modifier.clickable(enabled = enabled, onClick = onClick)
                } else {
                    Modifier.combinedClickable(
                        enabled = enabled,
                        onClick = onClick,
                        onLongClick = onLongClick,
                    )
                },
            )
            .padding(9.dp),
        colorFilter = ColorFilter.tint(
            MaterialTheme.colorScheme.onSurface.copy(alpha = if (enabled) 0.82f else 0.34f),
        ),
    )
}

@Composable
private fun ComposerVoiceRecordingStatus(
    startedAtMillis: Long?,
    onStop: () -> Unit,
    onCancel: () -> Unit,
) {
    var nowMillis by remember(startedAtMillis) { mutableLongStateOf(System.currentTimeMillis()) }
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
            .hermexGlass(
                shape = RoundedCornerShape(12.dp),
                castsShadow = false,
                surfaceLevel = HermexSurfaceLevel.Raised,
            )
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
            localizedString("Recording voice note"),
            modifier = Modifier.weight(1f),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.secondary,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
        HermexPillButton(localizedString("Cancel"), onCancel, contentPadding = PaddingValues(horizontal = 10.dp, vertical = 6.dp))
        HermexPillButton(localizedString("Use"), onStop, filled = true, contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp))
    }
}

@Composable
private fun ComposerVoiceTranscribingStatus() {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 2.dp)
            .hermexGlass(
                shape = RoundedCornerShape(12.dp),
                castsShadow = false,
                surfaceLevel = HermexSurfaceLevel.Raised,
            )
            .padding(horizontal = 14.dp, vertical = 10.dp),
        horizontalArrangement = Arrangement.spacedBy(10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        CircularProgressIndicator(modifier = Modifier.size(16.dp), strokeWidth = 2.dp)
        Text(
            localizedString("Sending voice note..."),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.secondary,
        )
    }
}

@Composable
private fun ComposerVoiceDictationStatus(text: String, isError: Boolean) {
    Text(
        text,
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 12.dp, vertical = 6.dp),
        style = MaterialTheme.typography.bodySmall,
        color = if (isError) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.secondary,
    )
}

private fun formatVoiceElapsed(totalSeconds: Int): String =
    "${totalSeconds / 60}:${(totalSeconds % 60).toString().padStart(2, '0')}"

internal fun voiceDictationDraft(baseDraft: String, transcript: String): String {
    val spoken = transcript.trim()
    if (spoken.isEmpty()) return baseDraft
    if (baseDraft.isBlank()) return spoken
    val separator = if (baseDraft.last().isWhitespace()) "" else " "
    return "$baseDraft$separator$spoken"
}

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
                modifier = Modifier.testTag("chat_workspace_picker"),
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
    val contextWindowDescription = "${localizedString("Context Window")} ${(clamped * 100).toInt()}%"
    val trackColor = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.13f)
    val progressColor = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.82f)
    Box(
        modifier = Modifier
            .size(48.dp)
            .clip(CircleShape)
            .clickable { showsDetails = true }
            .semantics(mergeDescendants = true) {
                contentDescription = contextWindowDescription
            }
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
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        sheetGesturesEnabled = false,
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
                    text = localizedString("Context Window"),
                    modifier = Modifier.weight(1f),
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold,
                )
                TextButton(onClick = onDismiss) {
                    Text(localizedString("Done"))
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
private fun ListenPlaybackBar(
    state: ListenPlaybackUiState,
    onTogglePlayPause: () -> Unit,
    onSeek: (Long) -> Unit,
    onSpeedChange: (ListenPlaybackSpeed) -> Unit,
    onStop: () -> Unit,
) {
    var showsSpeedMenu by remember { mutableStateOf(false) }
    val playbackPositionDescription = localizedString("Listen")
    val maximumPosition = state.durationMillis.coerceAtLeast(1).toFloat()
    val currentPosition = state.elapsedMillis.coerceIn(0, state.durationMillis.coerceAtLeast(0)).toFloat()
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .background(MaterialTheme.colorScheme.surface.copy(alpha = 0.96f))
            .testTag("listen_playback_bar"),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 12.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            if (state.phase == ListenPlaybackPhase.Loading) {
                Box(Modifier.size(36.dp), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(22.dp),
                        strokeWidth = 2.dp,
                    )
                }
            } else {
                HermexIconButton(
                    label = localizedString(if (state.isPlaying) "Pause" else "Listen"),
                    symbol = if (state.isPlaying) "Ⅱ" else "▶",
                    onClick = onTogglePlayPause,
                    enabled = state.isReady,
                    modifier = Modifier.size(36.dp),
                )
            }
            Column(Modifier.weight(1f)) {
                Slider(
                    value = currentPosition.coerceIn(0f, maximumPosition),
                    onValueChange = { onSeek(it.toLong()) },
                    valueRange = 0f..maximumPosition,
                    enabled = state.isReady && state.durationMillis > 0,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(28.dp)
                        .semantics { contentDescription = playbackPositionDescription },
                )
                Text(
                    text = "${formatPlaybackDuration(state.elapsedMillis)} / ${formatPlaybackDuration(state.durationMillis)}",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.secondary,
                )
            }
            Box {
                TextButton(
                    onClick = { showsSpeedMenu = true },
                    enabled = state.isReady,
                    modifier = Modifier.semantics { contentDescription = state.speed.title },
                ) {
                    Text(state.speed.title, fontWeight = FontWeight.SemiBold)
                }
                DropdownMenu(
                    expanded = showsSpeedMenu,
                    onDismissRequest = { showsSpeedMenu = false },
                ) {
                    ListenPlaybackSpeed.entries.forEach { speed ->
                        DropdownMenuItem(
                            text = { Text(speed.title) },
                            trailingIcon = {
                                if (speed == state.speed) Text("✓")
                            },
                            onClick = {
                                showsSpeedMenu = false
                                onSpeedChange(speed)
                            },
                        )
                    }
                }
            }
            HermexIconButton(
                label = localizedString("Stop Listening"),
                symbol = "×",
                onClick = onStop,
                modifier = Modifier.size(36.dp),
            )
        }
        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
    }
}

@Composable
private fun AttachmentOptionsSheet(
    onDismiss: () -> Unit,
    onAttachFile: () -> Unit,
    onPhotos: () -> Unit,
    onCamera: () -> Unit,
) {
    PickerSheet(
        title = "Attach",
        onDismiss = onDismiss,
        heightFraction = 0.44f,
    ) {
        Column(Modifier.fillMaxSize()) {
            PickerSectionHeader("Attach")
            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
            SelectorRow(
                title = localizedString("Attach File"),
                subtitle = "Choose from documents",
                selected = false,
                onClick = onAttachFile,
            )
            HorizontalDivider(
                color = MaterialTheme.colorScheme.outlineVariant,
                modifier = Modifier.padding(start = 52.dp),
            )
            SelectorRow(
                title = localizedString("Photos"),
                subtitle = "Choose images from your library",
                selected = false,
                onClick = onPhotos,
            )
            HorizontalDivider(
                color = MaterialTheme.colorScheme.outlineVariant,
                modifier = Modifier.padding(start = 52.dp),
            )
            SelectorRow(
                title = localizedString("Camera"),
                subtitle = "Take a new photo",
                selected = false,
                onClick = onCamera,
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
    var searchText by rememberSaveable { mutableStateOf("") }
    var customModelId by rememberSaveable { mutableStateOf("") }
    var customProviderId by rememberSaveable { mutableStateOf("") }
    var expandedGroupIds by rememberSaveable { mutableStateOf(emptySet<String>()) }
    var collapsedSearchGroupIds by rememberSaveable { mutableStateOf(emptySet<String>()) }
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
                placeholder = { Text(localizedString("Search models")) },
                singleLine = true,
                shape = HermexCardShape,
            )
            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
            LazyColumn(
                modifier = Modifier
                    .fillMaxSize()
                    .testTag("model_picker_list"),
            ) {
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
            localizedString("Custom Model"),
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.secondary,
            fontWeight = FontWeight.SemiBold,
        )
        OutlinedTextField(
            value = modelId,
            onValueChange = onModelIdChange,
            modifier = Modifier.fillMaxWidth(),
            placeholder = { Text(localizedString("Exact model ID")) },
            singleLine = true,
            shape = HermexCardShape,
        )
        OutlinedTextField(
            value = providerId,
            onValueChange = onProviderIdChange,
            modifier = Modifier.fillMaxWidth(),
            placeholder = { Text(localizedString("Provider ID")) },
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
                label = localizedString("Use Custom"),
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
                    localizedString("Delete"),
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
                            title = localizedString(ReasoningEffortOption.titleFor(effort)),
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
    onManage: (() -> Unit)? = null,
    onSelect: (String) -> Unit,
) {
    var prefix by rememberSaveable { mutableStateOf("") }
    var acceptedWorkspacePath by rememberSaveable { mutableStateOf<String?>(null) }
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
            modifier = Modifier.fillMaxSize().testTag("workspace_picker_list"),
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
                        placeholder = { Text(localizedString("Workspace path")) },
                        singleLine = true,
                        shape = HermexCardShape,
                    )
                    Text(
                        localizedString("Suggestions are limited to trusted workspace roots from the server."),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.secondary,
                    )
                }
                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
            }
            if (onManage != null) {
                item("workspace-manage") {
                    TextButton(
                        onClick = onManage,
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 8.dp),
                    ) {
                        Text(localizedString("Manage Workspaces"))
                    }
                    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                }
            }
            if (!effectiveSelected.isNullOrBlank()) {
                item("current-header") {
                    PickerSectionHeader("Current")
                    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                }
                item("current-workspace") {
                    SelectorRow(
                        title = localizedString("Current Workspace"),
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
    val sheetShape = RoundedCornerShape(topStart = 24.dp, topEnd = 24.dp)
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        sheetGesturesEnabled = false,
        dragHandle = null,
        containerColor = Color.Transparent,
        contentColor = MaterialTheme.colorScheme.onBackground,
        scrimColor = Color.Black.copy(alpha = 0.52f),
        shape = sheetShape,
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .fillMaxHeight(heightFraction)
                .navigationBarsPadding()
                .hermexGlass(
                    shape = sheetShape,
                    surfaceLevel = HermexSurfaceLevel.Floating,
                )
                .testTag("picker_sheet"),
        ) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(start = 16.dp, top = 10.dp, end = 8.dp, bottom = 8.dp),
            ) {
                Text(
                    localizedString(title),
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
                    Text(localizedString("Done"))
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
        localizedString(title),
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
        localizedString(text),
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
            .padding(12.dp)
            .testTag("approval_card"),
    ) {
        Text(localizedString("Approval required"), style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
        if (count > 1) Text(localizedStringFormat("Pending approvals: %lld", count), style = MaterialTheme.typography.bodySmall)
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
            HermexPillButton(localizedString("Allow once"), { onChoice(ApprovalChoice.Once) }, enabled = !isResponding, filled = true)
            HermexPillButton(localizedString("Session"), { onChoice(ApprovalChoice.Session) }, enabled = !isResponding)
            HermexPillButton(localizedString("Always"), { onChoice(ApprovalChoice.Always) }, enabled = !isResponding)
            HermexPillButton(localizedString("Deny"), { onChoice(ApprovalChoice.Deny) }, enabled = !isResponding)
        }
        HermexPillButton(
            localizedString("Skip all this session"),
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
        Text(localizedString("Approval bypass active"), style = MaterialTheme.typography.bodySmall, fontWeight = FontWeight.SemiBold)
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
            .padding(12.dp)
            .testTag("clarification_card"),
    ) {
        Text(localizedString("Clarification needed"), style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
        if (count > 1) Text(localizedStringFormat("Pending prompts: %lld", count), style = MaterialTheme.typography.bodySmall)
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
            placeholder = { Text(localizedString("Response")) },
            minLines = 1,
            maxLines = 4,
            enabled = !isResponding,
            shape = HermexCardShape,
        )
        HermexPillButton(
            label = localizedString("Submit"),
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
    showsResponseSpeed: Boolean,
    wrapsCodeBlockLines: Boolean,
    streamedTextAnimationEnabled: Boolean,
    loadTranscriptMediaImage: suspend (TranscriptMediaReference) -> ByteArray?,
    loadAttachmentFile: suspend (String) -> FileResponse?,
    actionContext: MessageActionContext?,
    isListening: Boolean,
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
            tokensPerSecond = message.turnTokensPerSecond,
            isStreamingMessage = isStreamingMessage,
            showThinkingAndToolCards = showThinkingAndToolCards,
            thinkingCardsStartExpanded = thinkingCardsStartExpanded,
            toolCardsStartExpanded = toolCardsStartExpanded,
            showsAssistantTurnTimestamp = showsAssistantTurnTimestamps,
            showsResponseSpeed = showsResponseSpeed,
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
            isListening = isListening,
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
        title = { Text(localizedString("Discard Later Messages?")) },
        text = { Text(message) },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(localizedString("Cancel"))
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
        title = { Text(localizedString("Clear conversation")) },
        text = { Text(localizedString("Clear all messages? This cannot be undone.")) },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(localizedString("Cancel"))
            }
        },
        confirmButton = {
            TextButton(onClick = onConfirm) {
                Text(localizedString("Clear"), color = MaterialTheme.colorScheme.error)
            }
        },
    )
}

@Composable
private fun StartNewProfileSessionDialog(
    profileTitle: String,
    onDismiss: () -> Unit,
    onConfirm: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(localizedString("Start New Session?")) },
        text = {
            Text(
                localizedStringFormat(
                    "Switch to %@ and start a new session. This keeps the current transcript on its original profile.",
                    profileTitle,
                ),
            )
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(localizedString("Cancel"))
            }
        },
        confirmButton = {
            TextButton(onClick = onConfirm) {
                Text(localizedString("Start New Session"))
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
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        sheetGesturesEnabled = false,
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
                    Text(localizedString("Cancel"))
                }
                Text(
                    localizedString("Edit Message"),
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
                    Text(localizedString("Send"))
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
    tokensPerSecond: Double?,
    isStreamingMessage: Boolean,
    showThinkingAndToolCards: Boolean,
    thinkingCardsStartExpanded: Boolean,
    toolCardsStartExpanded: Boolean,
    showsAssistantTurnTimestamp: Boolean,
    showsResponseSpeed: Boolean,
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
            if (showsAssistantTurnTimestamp || (showsResponseSpeed && responseSpeedText(tokensPerSecond) != null)) {
                AssistantTurnHeader(
                    timestamp = timestamp.takeIf { showsAssistantTurnTimestamp },
                    tokensPerSecond = tokensPerSecond.takeIf { showsResponseSpeed },
                )
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
    when (reference.mediaKind) {
        TranscriptMediaKind.Audio -> {
            TranscriptMediaAudioView(
                reference = reference,
                loadMediaData = loadMediaImage,
            )
            return
        }
        TranscriptMediaKind.Video -> {
            TranscriptMediaVideoTile(
                reference = reference,
                onPreviewMedia = onPreviewMedia,
            )
            return
        }
        TranscriptMediaKind.Unsupported -> {
            TranscriptMediaFileDownloadView(
                reference = reference,
                loadMediaData = loadMediaImage,
            )
            return
        }
        TranscriptMediaKind.Image -> Unit
    }

    var bytes by remember(reference.id) { mutableStateOf<ByteArray?>(null) }
    var didAttemptLoad by remember(reference.id) { mutableStateOf(false) }
    var remoteLoadApproved by remember(reference.id) { mutableStateOf(false) }
    val isRemote = reference.source is TranscriptMediaSource.RemoteUrl
    val remoteUrl = (reference.source as? TranscriptMediaSource.RemoteUrl)?.url
    val remoteHost = remoteUrl?.host
    val remoteLoadBlocked = remoteUrl?.scheme != null && remoteUrl.scheme != "https"
    LaunchedEffect(reference.id, remoteLoadApproved) {
        didAttemptLoad = false
        bytes = null
        if (isRemote && (!remoteLoadApproved || remoteLoadBlocked)) return@LaunchedEffect
        bytes = loadMediaImage(reference)
        didAttemptLoad = true
    }
    val bitmap = rememberDecodedBitmap(bytes, maxDimension = 840, maxPixels = 1_000_000L)
    val resolvedKind = remember(reference.id, bytes) {
        bytes?.let { TranscriptMediaDataClassifier.resolvedKind(reference, it) }
    }

    val shape = RoundedCornerShape(10.dp)
    when {
        remoteLoadBlocked -> {
            TranscriptMediaUnavailableChip(reference = reference, detail = "Insecure remote media blocked")
        }
        isRemote && !remoteLoadApproved -> {
            Column(
                modifier = Modifier
                    .widthIn(max = 240.dp)
                    .clip(shape)
                    .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.72f))
                    .border(0.5.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.55f), shape)
                    .clickable { remoteLoadApproved = true }
                    .padding(horizontal = 12.dp, vertical = 10.dp),
                verticalArrangement = Arrangement.spacedBy(3.dp),
            ) {
                Text(localizedString("Load remote image"), style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.SemiBold)
                Text(
                    remoteHost ?: localizedString("Remote"),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
        bitmap != null -> {
            Image(
                bitmap = bitmap.asImageBitmap(),
                contentDescription = localizedStringFormat("Open media image %@", reference.displayName),
                contentScale = ContentScale.Crop,
                modifier = Modifier
                    .size(width = 210.dp, height = 132.dp)
                    .clip(shape)
                    .clickable { onPreviewMedia(reference) }
                    .border(0.5.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.55f), shape),
            )
        }
        resolvedKind == TranscriptMediaKind.Audio && bytes != null -> {
            TranscriptMediaAudioView(
                reference = reference,
                initialData = bytes,
                remoteAlreadyApproved = true,
                loadMediaData = loadMediaImage,
            )
        }
        resolvedKind == TranscriptMediaKind.Video -> {
            TranscriptMediaVideoTile(
                reference = reference,
                onPreviewMedia = onPreviewMedia,
            )
        }
        resolvedKind == TranscriptMediaKind.Unsupported && bytes != null -> {
            TranscriptMediaFileDownloadView(
                reference = reference,
                initialData = bytes,
                loadMediaData = loadMediaImage,
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
private fun TranscriptMediaAudioView(
    reference: TranscriptMediaReference,
    loadMediaData: suspend (TranscriptMediaReference) -> ByteArray?,
    initialData: ByteArray? = null,
    remoteAlreadyApproved: Boolean = false,
) {
    Column(
        modifier = Modifier.widthIn(max = 300.dp),
        verticalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        InlineAudioAttachmentPlayer(
            title = reference.displayName,
            path = reference.rawReference,
            loadAttachmentData = { initialData ?: loadMediaData(reference) },
            requiresRemoteApproval = !remoteAlreadyApproved,
        )
        TranscriptMediaDownloadButton(
            reference = reference,
            initialData = initialData,
            loadMediaData = loadMediaData,
            modifier = Modifier.align(Alignment.End),
        )
    }
}

@Composable
private fun TranscriptMediaVideoTile(
    reference: TranscriptMediaReference,
    onPreviewMedia: (TranscriptMediaReference) -> Unit,
) {
    val shape = RoundedCornerShape(10.dp)
    val accessibilityLabel = localizedStringFormat("Open media video %@", reference.displayName)
    val playSymbol = "▶"
    Column(
        modifier = Modifier
            .size(width = 210.dp, height = 132.dp)
            .clip(shape)
            .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.78f))
            .border(0.5.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.55f), shape)
            .clickable { onPreviewMedia(reference) }
            .semantics { contentDescription = accessibilityLabel }
            .padding(horizontal = 14.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Text(
            playSymbol,
            style = MaterialTheme.typography.headlineMedium,
            color = MaterialTheme.colorScheme.primary,
            fontWeight = FontWeight.Bold,
        )
        Spacer(Modifier.height(7.dp))
        Text(
            reference.displayName,
            style = MaterialTheme.typography.labelMedium,
            fontWeight = FontWeight.SemiBold,
            maxLines = 1,
            overflow = TextOverflow.MiddleEllipsis,
        )
        Text(
            localizedString("Video"),
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
private fun TranscriptMediaFileDownloadView(
    reference: TranscriptMediaReference,
    loadMediaData: suspend (TranscriptMediaReference) -> ByteArray?,
    initialData: ByteArray? = null,
) {
    val shape = RoundedCornerShape(8.dp)
    Row(
        modifier = Modifier
            .widthIn(max = 300.dp)
            .clip(shape)
            .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.72f))
            .border(0.5.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.55f), shape)
            .padding(horizontal = 10.dp, vertical = 8.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            reference.fileExtension?.uppercase(Locale.ROOT)?.take(5) ?: "FILE",
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.secondary,
            fontWeight = FontWeight.SemiBold,
        )
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text(
                reference.displayName,
                style = MaterialTheme.typography.labelMedium,
                fontWeight = FontWeight.SemiBold,
                maxLines = 1,
                overflow = TextOverflow.MiddleEllipsis,
            )
            Text(
                localizedString("Tap to download"),
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        TranscriptMediaDownloadButton(
            reference = reference,
            initialData = initialData,
            loadMediaData = loadMediaData,
        )
    }
}

@Composable
private fun TranscriptMediaDownloadButton(
    reference: TranscriptMediaReference,
    loadMediaData: suspend (TranscriptMediaReference) -> ByteArray?,
    initialData: ByteArray? = null,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var cachedData by remember(reference.id, initialData) { mutableStateOf(initialData) }
    var pendingData by remember(reference.id) { mutableStateOf<ByteArray?>(null) }
    var isLoading by remember(reference.id) { mutableStateOf(false) }
    val resolvedKind = cachedData?.let { TranscriptMediaDataClassifier.resolvedKind(reference, it) }
        ?: reference.mediaKind
    val mimeType = when (resolvedKind) {
        TranscriptMediaKind.Image -> "image/*"
        TranscriptMediaKind.Audio -> "audio/*"
        TranscriptMediaKind.Video -> "video/*"
        TranscriptMediaKind.Unsupported -> "application/octet-stream"
    }
    val createDocument = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument(mimeType)) { uri ->
        val data = pendingData
        pendingData = null
        if (uri != null && data != null) {
            scope.launch {
                val saved = withContext(Dispatchers.IO) {
                    runCatching {
                        context.contentResolver.openOutputStream(uri, "w").use { output ->
                            requireNotNull(output) { "Could not open the selected file." }
                            output.write(data)
                        }
                    }.isSuccess
                }
                Toast.makeText(
                    context,
                    context.localizedString(if (saved) "Download complete" else "Download Failed"),
                    Toast.LENGTH_SHORT,
                ).show()
            }
        }
    }
    val downloadLabel = localizedString("Download")
    HermexIconButton(
        label = downloadLabel,
        symbol = if (isLoading) "…" else "↓",
        enabled = !isLoading,
        onClick = {
            scope.launch {
                isLoading = true
                val data = cachedData ?: loadMediaData(reference)
                isLoading = false
                if (data == null || data.isEmpty()) {
                    Toast.makeText(context, context.localizedString("Could not load media."), Toast.LENGTH_SHORT).show()
                    return@launch
                }
                cachedData = data
                pendingData = data
                val extension = TranscriptMediaDataClassifier.suggestedExtension(reference, data)
                createDocument.launch(reference.exportFilename(extension))
            }
        },
        modifier = modifier.size(38.dp),
    )
}

@Composable
private fun TranscriptMediaUnavailableChip(
    reference: TranscriptMediaReference,
    detail: String = "Media unavailable",
) {
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
                localizedString(detail),
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
    var videoFile by remember(reference.id) { mutableStateOf<File?>(null) }
    LaunchedEffect(reference.id) {
        didAttemptLoad = false
        bytes = loadMediaImage(reference)
        didAttemptLoad = true
    }
    val bitmap = rememberDecodedBitmap(bytes, maxDimension = 4_096, maxPixels = 8_000_000L)
    val resolvedKind = remember(reference.id, bytes, bitmap) {
        bytes?.let { data ->
            if (bitmap != null) TranscriptMediaKind.Image else TranscriptMediaDataClassifier.resolvedKind(reference, data)
        } ?: reference.mediaKind
    }
    LaunchedEffect(reference.id, bytes, resolvedKind) {
        videoFile?.delete()
        videoFile = null
        val videoBytes = bytes?.takeIf { resolvedKind == TranscriptMediaKind.Video } ?: return@LaunchedEffect
        videoFile = withContext(Dispatchers.IO) {
            val extension = TranscriptMediaDataClassifier.suggestedExtension(reference, videoBytes)
            File.createTempFile("hermex-transcript-video-", ".$extension", context.cacheDir).also { it.writeBytes(videoBytes) }
        }
    }
    val latestVideoFile by rememberUpdatedState(videoFile)
    DisposableEffect(reference.id) {
        onDispose { latestVideoFile?.delete() }
    }
    val saveMedia: () -> Unit = {
        val mediaBytes = bytes
        if (mediaBytes == null) {
            saveMessage = "Could not save media."
        } else {
            scope.launch {
                isSaving = true
                try {
                    saveMessage = withContext(Dispatchers.IO) {
                        when (resolvedKind) {
                            TranscriptMediaKind.Image -> saveTranscriptMediaImageToGallery(context, reference, mediaBytes)
                            TranscriptMediaKind.Video -> saveTranscriptMediaVideoToGallery(context, reference, mediaBytes)
                            else -> "This media type cannot be saved to Photos."
                        }
                    }
                } finally {
                    isSaving = false
                }
            }
        }
    }
    val legacyStoragePermission = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        if (granted) saveMedia() else saveMessage = "Photos permission is required to save this media."
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
                }
                resolvedKind == TranscriptMediaKind.Audio && bytes != null -> TranscriptMediaAudioView(
                    reference = reference,
                    initialData = bytes,
                    remoteAlreadyApproved = true,
                    loadMediaData = loadMediaImage,
                )
                resolvedKind == TranscriptMediaKind.Video && videoFile != null -> AndroidView(
                    factory = { viewContext ->
                        VideoView(viewContext).apply {
                            val controller = MediaController(viewContext)
                            controller.setAnchorView(this)
                            setMediaController(controller)
                        }
                    },
                    update = { videoView ->
                        val path = videoFile?.absolutePath ?: return@AndroidView
                        if (videoView.tag != path) {
                            videoView.tag = path
                            videoView.setVideoPath(path)
                            videoView.setOnPreparedListener { player ->
                                player.isLooping = false
                                videoView.seekTo(1)
                            }
                        }
                    },
                    onRelease = { videoView ->
                        videoView.stopPlayback()
                        videoView.setMediaController(null)
                    },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(360.dp)
                        .clip(RoundedCornerShape(12.dp))
                        .background(Color.Black),
                )
                resolvedKind == TranscriptMediaKind.Unsupported && bytes != null -> TranscriptMediaFileDownloadView(
                    reference = reference,
                    initialData = bytes,
                    loadMediaData = loadMediaImage,
                )
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
            if (bytes != null && (resolvedKind == TranscriptMediaKind.Image || resolvedKind == TranscriptMediaKind.Video)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.End),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    TranscriptMediaDownloadButton(
                        reference = reference,
                        initialData = bytes,
                        loadMediaData = loadMediaImage,
                    )
                    HermexPillButton(
                        label = localizedString(if (isSaving) "Saving" else "Save"),
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
                                saveMedia()
                            }
                        },
                        enabled = !isSaving,
                        filled = true,
                    )
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
): String = runCatching {
    val resolver = context.contentResolver
    val fileName = reference.exportFilename(TranscriptMediaDataClassifier.suggestedExtension(reference, bytes))
    val values = ContentValues().apply {
        put(MediaStore.MediaColumns.DISPLAY_NAME, fileName)
        put(MediaStore.MediaColumns.MIME_TYPE, fileName.galleryMimeType())
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            put(MediaStore.MediaColumns.RELATIVE_PATH, "Pictures/Hermex")
            put(MediaStore.MediaColumns.IS_PENDING, 1)
        }
    }
    val uri = resolver.insert(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, values)
        ?: error("Gallery did not create an image entry.")
    try {
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
        throw error
    }
}.getOrElse { error ->
    "Could not save image: ${error.localizedMessage ?: "Unknown error."}"
}

private fun saveTranscriptMediaVideoToGallery(
    context: Context,
    reference: TranscriptMediaReference,
    bytes: ByteArray,
): String = runCatching {
    val resolver = context.contentResolver
    val extension = TranscriptMediaDataClassifier.suggestedExtension(reference, bytes)
    val fileName = reference.exportFilename(extension)
    val values = ContentValues().apply {
        put(MediaStore.MediaColumns.DISPLAY_NAME, fileName)
        put(MediaStore.MediaColumns.MIME_TYPE, when (extension) {
            "mov" -> "video/quicktime"
            "m4v" -> "video/x-m4v"
            else -> "video/mp4"
        })
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            put(MediaStore.MediaColumns.RELATIVE_PATH, "Movies/Hermex")
            put(MediaStore.MediaColumns.IS_PENDING, 1)
        }
    }
    val uri = resolver.insert(MediaStore.Video.Media.EXTERNAL_CONTENT_URI, values)
        ?: error("Gallery did not create a video entry.")
    try {
        resolver.openOutputStream(uri)?.use { output -> output.write(bytes) }
            ?: error("Could not open gallery item.")
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            values.clear()
            values.put(MediaStore.MediaColumns.IS_PENDING, 0)
            resolver.update(uri, values, null, null)
        }
        "Video saved to gallery."
    } catch (error: Throwable) {
        runCatching { resolver.delete(uri, null, null) }
        throw error
    }
}.getOrElse { error ->
    "Could not save video: ${error.localizedMessage ?: "Unknown error."}"
}

private fun TranscriptMediaReference.exportFilename(extension: String): String {
    val rawName = displayName.trim().takeIf { it.isNotBlank() } ?: "hermex-media"
    if (rawName.substringAfterLast('.', missingDelimiterValue = "").isNotBlank()) return rawName
    val stem = rawName
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

@Composable
private fun TranscriptLinkPreviewCard(
    url: HttpUrl,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    val shape = RoundedCornerShape(12.dp)
    val canLoadPreview = url.isHttps
    var previewRequested by remember(url) { mutableStateOf(false) }
    val metadata by produceState(LinkPreviewMetadata(), url, previewRequested) {
        if (previewRequested && canLoadPreview) value = LinkPreviewMetadataProvider.metadata(url)
    }
    val previewBitmap = rememberDecodedBitmap(
        bytes = metadata.imageBytes,
        maxDimension = 1_024,
        maxPixels = 1_500_000L,
    )
    Row(
        modifier = modifier
            .widthIn(max = 300.dp)
            .clip(shape)
            .clickable {
                if (canLoadPreview && !previewRequested) {
                    previewRequested = true
                } else {
                    runCatching {
                        context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url.toString())))
                    }
                }
            }
            .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.7f))
            .border(0.5.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.6f), shape)
            .padding(10.dp),
        horizontalArrangement = Arrangement.spacedBy(10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        previewBitmap?.let { bitmap ->
            Image(
                bitmap = bitmap.asImageBitmap(),
                contentDescription = null,
                modifier = Modifier
                    .size(58.dp)
                    .clip(RoundedCornerShape(9.dp))
                    .background(MaterialTheme.colorScheme.surfaceVariant),
                contentScale = ContentScale.Crop,
            )
        }
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
                metadata.title ?: url.host,
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurface,
                fontWeight = FontWeight.SemiBold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                metadata.description
                    ?: if (canLoadPreview && !previewRequested) localizedString("Details") else url.transcriptPreviewDisplayText(),
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
private fun AssistantTurnHeader(timestamp: Double?, tokensPerSecond: Double?) {
    val speed = responseSpeedText(tokensPerSecond)
    val details = listOfNotNull(timestamp.shortTimeText(), speed)
    val headerDescription = localizedString(
        if (timestamp != null) "Response Timestamps" else "Response Speed",
    )
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .semantics { contentDescription = headerDescription },
        horizontalArrangement = Arrangement.spacedBy(5.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            "\u2726",
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.primary,
            fontWeight = FontWeight.SemiBold,
        )
        details.forEach { detail ->
            Text(
                detail,
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.secondary,
                fontWeight = FontWeight.Medium,
            )
        }
    }
}

internal fun responseSpeedText(tokensPerSecond: Double?, locale: Locale = Locale.getDefault()): String? {
    val value = tokensPerSecond?.takeIf { it.isFinite() && it > 0.0 } ?: return null
    val formatter = NumberFormat.getNumberInstance(locale).apply {
        minimumFractionDigits = 1
        maximumFractionDigits = 1
    }
    return "${formatter.format(value)} t/s"
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
            .hermexGlass(
                shape = RoundedCornerShape(10.dp),
                castsShadow = false,
                surfaceLevel = HermexSurfaceLevel.Raised,
            )
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
            .hermexGlass(
                shape = RoundedCornerShape(10.dp),
                castsShadow = false,
                surfaceLevel = HermexSurfaceLevel.Raised,
            )
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
                localizedString("Thinking"),
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
            .hermexGlass(
                shape = RoundedCornerShape(10.dp),
                castsShadow = false,
                surfaceLevel = HermexSurfaceLevel.Raised,
            )
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
                localizedString("Tool"),
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
            TranscriptStatusPill(text = localizedString("Running"), color = accentColor)
            Text(
                if (expanded) "\u2303" else "\u2304",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.secondary,
                fontWeight = FontWeight.SemiBold,
            )
        }
        if (expanded) {
            ToolDetailSection(title = localizedString("Activity"), value = trimmed)
        }
    }
}

@Composable
private fun AssistantTypingIndicator() {
    val typingDescription = localizedString("Hermex is preparing a response")
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
            .semantics { contentDescription = typingDescription },
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
            .hermexGlass(
                shape = RoundedCornerShape(10.dp),
                castsShadow = false,
                surfaceLevel = HermexSurfaceLevel.Raised,
            )
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
                localizedString("File changes"),
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
                localizedString("Open diff"),
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
    var retryNonce by remember(presentation) { mutableIntStateOf(0) }
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
        runSuspendCatching { repository.diff(sessionId, path, file.gitDiffKind()) }
            .onSuccess { response ->
                diff = response
                error = response.error
            }
            .onFailure { throwable ->
                error = throwable.message ?: "Could not load diff."
            }
        isLoading = false
    }

    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        sheetGesturesEnabled = false,
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
                TextButton(onClick = onDismiss) { Text(localizedString("Done")) }
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
                        Text(localizedString(error.orEmpty()), color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall)
                        HermexPillButton(localizedString("Try Again"), onClick = { retryNonce++ })
                    }
                    diff != null -> HermexGitDiffContent(requireNotNull(diff))
                    else -> Text(localizedString("No diff selected."), style = MaterialTheme.typography.bodySmall)
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
            .hermexGlass(
                shape = RoundedCornerShape(10.dp),
                castsShadow = false,
                surfaceLevel = HermexSurfaceLevel.Raised,
            )
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
                TranscriptStatusPill(text = localizedString("Failed"), color = accentColor)
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
            .hermexGlass(
                shape = RoundedCornerShape(9.dp),
                castsShadow = false,
                surfaceLevel = HermexSurfaceLevel.Base,
            )
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
            ToolDetailSection(title = localizedString("Preview"), value = tool.preview)
        }
        val argsText = tool.args?.takeIf { it.isNotEmpty() }?.entries
            ?.joinToString("\n") { (key, value) -> "$key: ${value.toString().trim()}" }
        if (!argsText.isNullOrBlank()) {
            ToolDetailSection(title = localizedString("Arguments"), value = argsText)
        }
        tool.result?.let { result ->
            ToolDetailSection(title = localizedString("Result"), value = result.toString().trim())
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
            localizedString(title),
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
            .widthIn(max = 520.dp)
            .testTag("user_message_bubble")
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
    isListening: Boolean,
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
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        sheetGesturesEnabled = false,
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 18.dp)
                .padding(bottom = 22.dp),
            verticalArrangement = Arrangement.spacedBy(2.dp),
        ) {
            if (context.role == MessageActionRole.Assistant) {
                MessageActionSheetRow(
                    title = localizedString(if (isListening) "Stop Listening" else "Listen"),
                    symbol = "\u266a",
                    enabled = context.listenText?.isNotBlank() == true,
                    onClick = onListen,
                )
                MessageActionSheetRow(
                    title = localizedString("Select Text"),
                    symbol = "T",
                    onClick = onSelectText,
                )
                MessageActionSheetRow(
                    title = localizedString("Regenerate Response"),
                    symbol = "\u21bb",
                    enabled = messageActionEnabled && !isRegeneratingMessage,
                    onClick = onRegenerate,
                )
            }
            if (context.role == MessageActionRole.User) {
                MessageActionSheetRow(
                    title = localizedString("Edit Message"),
                    symbol = "\u270e",
                    enabled = messageActionEnabled && !isEditingMessage,
                    onClick = onEdit,
                )
            }
            MessageActionSheetRow(
                title = localizedString("Fork From Here"),
                symbol = "\u21b1",
                enabled = messageActionEnabled && !isForkingMessage,
                onClick = onFork,
            )
            MessageActionSheetRow(
                title = localizedString("Copy"),
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
                localizedString(title),
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
    requiresRemoteApproval: Boolean = true,
) {
    val context = LocalContext.current
    var phase by remember(path) { mutableStateOf(AudioAttachmentPhase.Loading) }
    var player by remember(path) { mutableStateOf<MediaPlayer?>(null) }
    var tempFile by remember(path) { mutableStateOf<File?>(null) }
    var isPlaying by remember(path) { mutableStateOf(false) }
    var currentMs by remember(path) { mutableIntStateOf(0) }
    var durationMs by remember(path) { mutableIntStateOf(0) }
    var remoteLoadApproved by remember(path) { mutableStateOf(false) }
    val remoteUrl = remember(path) {
        path?.let { (TranscriptMediaReference(it).source as? TranscriptMediaSource.RemoteUrl)?.url }
    }
    val isRemote = requiresRemoteApproval && remoteUrl != null
    val remoteLoadBlocked = remoteUrl?.scheme != null && remoteUrl.scheme != "https"

    LaunchedEffect(path, remoteLoadApproved) {
        phase = AudioAttachmentPhase.Loading
        isPlaying = false
        currentMs = 0
        durationMs = 0
        val resolvedPath = path?.takeIf { it.isNotBlank() }
        if (resolvedPath == null) {
            phase = AudioAttachmentPhase.Failed
            return@LaunchedEffect
        }
        if (isRemote && (!remoteLoadApproved || remoteLoadBlocked)) {
            phase = if (remoteLoadBlocked) AudioAttachmentPhase.BlockedRemote else AudioAttachmentPhase.AwaitingRemoteApproval
            return@LaunchedEffect
        }
        val bytes = loadAttachmentData(resolvedPath)
        if (bytes == null || bytes.isEmpty()) {
            phase = AudioAttachmentPhase.Failed
            return@LaunchedEffect
        }
        var audioFile: File? = null
        var candidatePlayer: MediaPlayer? = null
        val prepared = runCatching {
            audioFile = withContext(Dispatchers.IO) {
                File.createTempFile("hermex-attachment-", ".audio", context.cacheDir).also { it.writeBytes(bytes) }
            }
            val mediaPlayer = MediaPlayer().apply {
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
            }
            candidatePlayer = mediaPlayer
            withContext(Dispatchers.IO) {
                mediaPlayer.setDataSource(requireNotNull(audioFile).absolutePath)
                mediaPlayer.prepare()
            }
            tempFile = audioFile
            mediaPlayer
        }.getOrElse {
            runCatching { candidatePlayer?.release() }
            withContext(Dispatchers.IO) { runCatching { audioFile?.delete() } }
            null
        }
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

    val latestPlayer by rememberUpdatedState(player)
    val latestTempFile by rememberUpdatedState(tempFile)
    DisposableEffect(path) {
        onDispose {
            latestPlayer?.let { mediaPlayer ->
                AudioAttachmentPlaybackCenter.clear(mediaPlayer)
                mediaPlayer.release()
            }
            latestTempFile?.delete()
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
                .size(48.dp)
                .clip(CircleShape)
                .clickable(enabled = phase == AudioAttachmentPhase.Ready || phase == AudioAttachmentPhase.AwaitingRemoteApproval) {
                    if (phase == AudioAttachmentPhase.AwaitingRemoteApproval) {
                        remoteLoadApproved = true
                        return@clickable
                    }
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
                AudioAttachmentPhase.BlockedRemote -> Text("!", color = MaterialTheme.colorScheme.error, fontWeight = FontWeight.Bold)
                AudioAttachmentPhase.AwaitingRemoteApproval -> Text(">", color = MaterialTheme.colorScheme.onPrimary, fontWeight = FontWeight.Bold)
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
            if (phase == AudioAttachmentPhase.Failed || phase == AudioAttachmentPhase.BlockedRemote || phase == AudioAttachmentPhase.AwaitingRemoteApproval) {
                Text(
                    when (phase) {
                        AudioAttachmentPhase.AwaitingRemoteApproval -> "Tap to load remote audio"
                        AudioAttachmentPhase.BlockedRemote -> "Insecure remote audio blocked"
                        else -> "Couldn't play this audio"
                    },
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
    BlockedRemote,
    AwaitingRemoteApproval,
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
    val bitmap = rememberDecodedBitmap(bytes, maxDimension = 2_048, maxPixels = 4_000_000L)

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
                localizedString("Image preview could not be loaded."),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun rememberDecodedBitmap(
    bytes: ByteArray?,
    maxDimension: Int,
    maxPixels: Long,
): Bitmap? {
    val bitmap by produceState<Bitmap?>(initialValue = null, bytes, maxDimension, maxPixels) {
        value = withContext(Dispatchers.IO) {
            bytes?.let { decodeSampledBitmap(it, maxDimension, maxPixels) }
        }
    }
    return bitmap
}

private fun decodeSampledBitmap(
    bytes: ByteArray,
    maxDimension: Int,
    maxPixels: Long,
): Bitmap? {
    val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
    BitmapFactory.decodeByteArray(bytes, 0, bytes.size, bounds)
    if (bounds.outWidth <= 0 || bounds.outHeight <= 0) return null

    var sampleSize = 1
    while (
        bounds.outWidth / sampleSize > maxDimension ||
        bounds.outHeight / sampleSize > maxDimension ||
        (bounds.outWidth.toLong() / sampleSize) * (bounds.outHeight.toLong() / sampleSize) > maxPixels
    ) {
        sampleSize *= 2
    }
    return BitmapFactory.decodeByteArray(
        bytes,
        0,
        bytes.size,
        BitmapFactory.Options().apply { inSampleSize = sampleSize },
    )
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
        else -> AttachmentPreviewUnavailable(localizedString("Preview is not available for this attachment."), path ?: "Unavailable")
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
            localizedString("No Preview"),
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
                    message = localizedString("Preview is not available for this file type."),
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

private fun ChatUiState.slashAutocompleteContext(): SlashAutocompleteContext =
    SlashAutocompleteContext(
        modelIds = modelOptions.mapNotNull { model ->
            model.id?.trim()?.takeIf { it.isNotEmpty() }
                ?: model.name?.trim()?.takeIf { it.isNotEmpty() }
                ?: model.label?.trim()?.takeIf { it.isNotEmpty() }
        },
        profileNames = profileOptions.mapNotNull { profile ->
            profile.name?.trim()?.takeIf { it.isNotEmpty() }
                ?: profile.displayName?.trim()?.takeIf { it.isNotEmpty() }
        },
        reasoningEfforts = (listOf("show", "hide") + reasoningOptions).distinct(),
        workspacePaths = buildList {
            workspaceRoots.mapNotNullTo(this) { root -> root.path?.trim()?.takeIf { it.isNotEmpty() } }
            workspaceSuggestions.mapNotNullTo(this) { path -> path.trim().takeIf { it.isNotEmpty() } }
        },
        skillSuggestions = skillSuggestions,
        serverCommands = agentCommands.mapNotNull { command ->
            command.displayName?.let { name ->
                SlashServerCommand(
                    name = name,
                    description = command.displayDescription,
                    argumentHint = command.displayArgsHint,
                    isMobileVisible = command.isMobileVisible,
                )
            }
        },
        selectedModelId = selectedModel?.id ?: selectedModel?.name ?: selectedModel?.label,
        selectedProfileName = selectedProfile?.name ?: selectedProfile?.displayName,
        selectedReasoningEffort = selectedReasoning,
        selectedWorkspacePath = selectedWorkspacePath,
    )

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
