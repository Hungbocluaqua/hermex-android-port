package com.uzairansar.hermex.ui.chat

import android.content.Context
import android.media.AudioAttributes
import android.media.AudioFocusRequest
import android.media.AudioManager
import android.media.MediaMetadata
import android.media.MediaPlayer
import android.media.PlaybackParams
import android.media.session.MediaSession
import android.media.session.PlaybackState
import android.os.Handler
import android.os.Looper
import android.speech.tts.TextToSpeech
import android.speech.tts.UtteranceProgressListener
import java.io.File
import java.util.Locale
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.withContext

internal enum class ListenPlaybackPhase {
    Idle,
    Loading,
    Playing,
    Paused,
}

internal enum class ListenPlaybackSpeed(
    val rate: Float,
    val title: String,
) {
    Half(0.5f, "0.5x"),
    Normal(1f, "1x"),
    OneAndHalf(1.5f, "1.5x"),
    Double(2f, "2x"),
    ;

    companion object {
        fun fromStoredRate(rate: Float): ListenPlaybackSpeed =
            entries.firstOrNull { kotlin.math.abs(it.rate - rate) < 0.001f } ?: Normal
    }
}

internal data class ListenPlaybackUiState(
    val phase: ListenPlaybackPhase = ListenPlaybackPhase.Idle,
    val activeMessageId: String? = null,
    val title: String = "Hermex response",
    val elapsedMillis: Long = 0,
    val durationMillis: Long = 0,
    val speed: ListenPlaybackSpeed = ListenPlaybackSpeed.Normal,
    val hasSeekableAudio: Boolean = false,
) {
    val showsPlaybackBar: Boolean
        get() = phase == ListenPlaybackPhase.Loading || hasSeekableAudio

    val isPlaying: Boolean
        get() = phase == ListenPlaybackPhase.Playing

    val isReady: Boolean
        get() = hasSeekableAudio && (phase == ListenPlaybackPhase.Playing || phase == ListenPlaybackPhase.Paused)
}

internal object ServerTtsPolicy {
    const val MaximumCharacters = 5_000

    fun shouldUseServer(text: String): Boolean = text.isNotBlank() && text.length <= MaximumCharacters
}

/**
 * Owns one assistant-response playback session. Server TTS is seekable and is surfaced through
 * Android's system media session; on-device speech remains the silent offline fallback.
 */
internal class ListenPlaybackController(context: Context) {
    private val appContext = context.applicationContext
    private val mainHandler = Handler(Looper.getMainLooper())
    private val preferences = appContext.getSharedPreferences(PreferencesName, Context.MODE_PRIVATE)
    private val audioManager = appContext.getSystemService(Context.AUDIO_SERVICE) as AudioManager
    private val audioAttributes = AudioAttributes.Builder()
        .setUsage(AudioAttributes.USAGE_MEDIA)
        .setContentType(AudioAttributes.CONTENT_TYPE_SPEECH)
        .build()
    private val audioFocusRequest = AudioFocusRequest.Builder(AudioManager.AUDIOFOCUS_GAIN)
        .setAudioAttributes(audioAttributes)
        .setAcceptsDelayedFocusGain(false)
        .setOnAudioFocusChangeListener { change ->
            mainHandler.post {
                when (change) {
                    AudioManager.AUDIOFOCUS_LOSS -> stop()
                    AudioManager.AUDIOFOCUS_LOSS_TRANSIENT,
                    AudioManager.AUDIOFOCUS_LOSS_TRANSIENT_CAN_DUCK,
                    -> pause()
                }
            }
        }
        .build()

    private val _state = MutableStateFlow(
        ListenPlaybackUiState(
            speed = ListenPlaybackSpeed.fromStoredRate(
                preferences.getFloat(PlaybackSpeedKey, ListenPlaybackSpeed.Normal.rate),
            ),
        ),
    )
    val state: StateFlow<ListenPlaybackUiState> = _state.asStateFlow()

    private var mediaPlayer: MediaPlayer? = null
    private var mediaFile: File? = null
    private var textToSpeech: TextToSpeech? = null
    private var isTextToSpeechReady = false
    private var localUtteranceId: String? = null
    private var generation = 0L

    private val mediaSession = MediaSession(appContext, "Hermex Listen").apply {
        @Suppress("DEPRECATION")
        setFlags(MediaSession.FLAG_HANDLES_MEDIA_BUTTONS or MediaSession.FLAG_HANDLES_TRANSPORT_CONTROLS)
        setCallback(
            object : MediaSession.Callback() {
                override fun onPlay() {
                    mainHandler.post { resume() }
                }

                override fun onPause() {
                    mainHandler.post { pause() }
                }

                override fun onStop() {
                    mainHandler.post { stop() }
                }

                override fun onSeekTo(pos: Long) {
                    mainHandler.post { seekTo(pos) }
                }
            },
            mainHandler,
        )
    }

    init {
        var engine: TextToSpeech? = null
        engine = TextToSpeech(appContext) { status ->
            if (textToSpeech !== engine) return@TextToSpeech
            isTextToSpeechReady = status == TextToSpeech.SUCCESS
            if (isTextToSpeechReady) {
                engine?.language = Locale.getDefault()
                engine?.setAudioAttributes(audioAttributes)
            }
        }
        engine.setOnUtteranceProgressListener(
            object : UtteranceProgressListener() {
                override fun onStart(utteranceId: String?) = Unit

                override fun onDone(utteranceId: String?) {
                    finishLocalUtterance(utteranceId)
                }

                @Deprecated("Deprecated by Android")
                override fun onError(utteranceId: String?) {
                    finishLocalUtterance(utteranceId)
                }

                override fun onError(utteranceId: String?, errorCode: Int) {
                    finishLocalUtterance(utteranceId)
                }
            },
        )
        textToSpeech = engine
    }

    fun beginLoading(messageId: String, title: String = "Hermex response"): Long {
        stopInternal(clearActiveMessage = true)
        generation += 1
        _state.value = _state.value.copy(
            phase = ListenPlaybackPhase.Loading,
            activeMessageId = messageId,
            title = title,
            elapsedMillis = 0,
            durationMillis = 0,
            hasSeekableAudio = false,
        )
        return generation
    }

    fun isCurrent(requestGeneration: Long, messageId: String): Boolean =
        generation == requestGeneration && _state.value.activeMessageId == messageId

    suspend fun startServerAudio(
        requestGeneration: Long,
        messageId: String,
        title: String,
        audio: ByteArray,
    ): Boolean {
        if (audio.isEmpty() || !isCurrent(requestGeneration, messageId)) return false
        var preparedPlayer: MediaPlayer? = null
        var preparedFile: File? = null
        try {
            withContext(Dispatchers.IO) {
                val file = File.createTempFile("hermex-tts-", ".mp3", appContext.cacheDir)
                var player: MediaPlayer? = null
                try {
                    file.writeBytes(audio)
                    player = MediaPlayer().apply {
                        setAudioAttributes(audioAttributes)
                        setDataSource(file.absolutePath)
                        prepare()
                    }
                    preparedPlayer = player
                    preparedFile = file
                } catch (error: Throwable) {
                    player?.release()
                    file.delete()
                    throw error
                }
            }
        } catch (error: CancellationException) {
            runCatching { preparedPlayer?.release() }
            preparedFile?.delete()
            throw error
        } catch (_: Throwable) {
            runCatching { preparedPlayer?.release() }
            preparedFile?.delete()
            return false
        }

        val player = preparedPlayer ?: return false
        val file = preparedFile ?: run {
            player.release()
            return false
        }
        if (!isCurrent(requestGeneration, messageId)) {
            player.release()
            file.delete()
            return false
        }
        if (audioManager.requestAudioFocus(audioFocusRequest) != AudioManager.AUDIOFOCUS_REQUEST_GRANTED) {
            player.release()
            file.delete()
            return false
        }

        releaseServerAudio()
        mediaPlayer = player
        mediaFile = file
        player.setOnCompletionListener { completed ->
            mainHandler.post {
                if (mediaPlayer === completed) stop()
            }
        }
        player.setOnErrorListener { failed, _, _ ->
            mainHandler.post {
                if (mediaPlayer === failed) stop()
            }
            true
        }
        applyPlaybackSpeed(player, _state.value.speed)
        return runCatching {
            player.start()
            val duration = player.duration.toLong().coerceAtLeast(0)
            _state.value = _state.value.copy(
                phase = ListenPlaybackPhase.Playing,
                title = title,
                elapsedMillis = 0,
                durationMillis = duration,
                hasSeekableAudio = true,
            )
            updateSystemPlayback()
            true
        }.getOrElse {
            releaseServerAudio()
            audioManager.abandonAudioFocusRequest(audioFocusRequest)
            false
        }
    }

    fun startOnDevice(requestGeneration: Long, messageId: String, text: String): Boolean {
        if (!isCurrent(requestGeneration, messageId) || !isTextToSpeechReady) {
            if (isCurrent(requestGeneration, messageId)) stop()
            return false
        }
        releaseServerAudio()
        clearSystemPlayback()
        if (audioManager.requestAudioFocus(audioFocusRequest) != AudioManager.AUDIOFOCUS_REQUEST_GRANTED) {
            stop()
            return false
        }
        val utteranceId = "hermex-listen-$requestGeneration"
        localUtteranceId = utteranceId
        _state.value = _state.value.copy(
            phase = ListenPlaybackPhase.Playing,
            hasSeekableAudio = false,
            elapsedMillis = 0,
            durationMillis = 0,
        )
        val result = textToSpeech?.speak(text, TextToSpeech.QUEUE_FLUSH, null, utteranceId)
        if (result != TextToSpeech.SUCCESS) {
            stop()
            return false
        }
        return true
    }

    fun togglePlayPause() {
        when (_state.value.phase) {
            ListenPlaybackPhase.Playing -> pause()
            ListenPlaybackPhase.Paused -> resume()
            ListenPlaybackPhase.Idle,
            ListenPlaybackPhase.Loading,
            -> Unit
        }
    }

    fun pause() {
        val player = mediaPlayer ?: return
        if (_state.value.phase != ListenPlaybackPhase.Playing) return
        runCatching { player.pause() }
        refreshProgress()
        _state.value = _state.value.copy(phase = ListenPlaybackPhase.Paused)
        updateSystemPlayback()
    }

    fun resume() {
        val player = mediaPlayer ?: return
        if (_state.value.phase != ListenPlaybackPhase.Paused) return
        if (audioManager.requestAudioFocus(audioFocusRequest) != AudioManager.AUDIOFOCUS_REQUEST_GRANTED) return
        runCatching {
            applyPlaybackSpeed(player, _state.value.speed)
            player.start()
            _state.value = _state.value.copy(phase = ListenPlaybackPhase.Playing)
            updateSystemPlayback()
        }
    }

    fun seekTo(positionMillis: Long) {
        val player = mediaPlayer ?: return
        if (!_state.value.isReady) return
        val bounded = positionMillis.coerceIn(0, _state.value.durationMillis.coerceAtLeast(0))
        runCatching { player.seekTo(bounded.toInt()) }
        _state.value = _state.value.copy(elapsedMillis = bounded)
        updateSystemPlayback()
    }

    fun setSpeed(speed: ListenPlaybackSpeed) {
        _state.value = _state.value.copy(speed = speed)
        preferences.edit().putFloat(PlaybackSpeedKey, speed.rate).apply()
        mediaPlayer?.let { applyPlaybackSpeed(it, speed) }
        updateSystemPlayback()
    }

    fun refreshProgress() {
        val player = mediaPlayer ?: return
        if (!_state.value.hasSeekableAudio) return
        val elapsed = runCatching { player.currentPosition.toLong() }.getOrDefault(_state.value.elapsedMillis)
        val duration = runCatching { player.duration.toLong() }.getOrDefault(_state.value.durationMillis)
        _state.value = _state.value.copy(
            elapsedMillis = elapsed.coerceIn(0, duration.coerceAtLeast(0)),
            durationMillis = duration.coerceAtLeast(0),
        )
        updateSystemPlayback()
    }

    fun stop() {
        generation += 1
        stopInternal(clearActiveMessage = true)
    }

    fun close() {
        stop()
        textToSpeech?.shutdown()
        textToSpeech = null
        isTextToSpeechReady = false
        mediaSession.release()
    }

    private fun stopInternal(clearActiveMessage: Boolean) {
        textToSpeech?.stop()
        localUtteranceId = null
        releaseServerAudio()
        audioManager.abandonAudioFocusRequest(audioFocusRequest)
        clearSystemPlayback()
        _state.value = _state.value.copy(
            phase = ListenPlaybackPhase.Idle,
            activeMessageId = if (clearActiveMessage) null else _state.value.activeMessageId,
            elapsedMillis = 0,
            durationMillis = 0,
            hasSeekableAudio = false,
        )
    }

    private fun releaseServerAudio() {
        mediaPlayer?.let { player ->
            player.setOnCompletionListener(null)
            player.setOnErrorListener(null)
            runCatching { player.stop() }
            player.release()
        }
        mediaPlayer = null
        mediaFile?.delete()
        mediaFile = null
    }

    private fun finishLocalUtterance(utteranceId: String?) {
        if (utteranceId == null || utteranceId != localUtteranceId) return
        mainHandler.post {
            if (utteranceId == localUtteranceId) stop()
        }
    }

    private fun applyPlaybackSpeed(player: MediaPlayer, speed: ListenPlaybackSpeed) {
        runCatching {
            val params = runCatching { player.playbackParams }.getOrElse { PlaybackParams() }
            player.playbackParams = params.setSpeed(speed.rate).setPitch(1f)
        }
    }

    private fun updateSystemPlayback() {
        val snapshot = _state.value
        if (!snapshot.hasSeekableAudio) return
        mediaSession.setMetadata(
            MediaMetadata.Builder()
                .putString(MediaMetadata.METADATA_KEY_TITLE, snapshot.title)
                .putString(MediaMetadata.METADATA_KEY_ARTIST, "Hermex")
                .putLong(MediaMetadata.METADATA_KEY_DURATION, snapshot.durationMillis)
                .build(),
        )
        mediaSession.setPlaybackState(
            PlaybackState.Builder()
                .setActions(SystemPlaybackActions)
                .setState(
                    if (snapshot.isPlaying) PlaybackState.STATE_PLAYING else PlaybackState.STATE_PAUSED,
                    snapshot.elapsedMillis,
                    if (snapshot.isPlaying) snapshot.speed.rate else 0f,
                )
                .build(),
        )
        mediaSession.isActive = true
    }

    private fun clearSystemPlayback() {
        mediaSession.setPlaybackState(
            PlaybackState.Builder()
                .setActions(SystemPlaybackActions)
                .setState(PlaybackState.STATE_STOPPED, 0, 0f)
                .build(),
        )
        mediaSession.setMetadata(null)
        mediaSession.isActive = false
    }

    private companion object {
        const val PreferencesName = "hermex_listen_playback"
        const val PlaybackSpeedKey = "speed"
        const val SystemPlaybackActions =
            PlaybackState.ACTION_PLAY or
                PlaybackState.ACTION_PAUSE or
                PlaybackState.ACTION_PLAY_PAUSE or
                PlaybackState.ACTION_STOP or
                PlaybackState.ACTION_SEEK_TO
    }
}

internal fun formatPlaybackDuration(durationMillis: Long): String {
    val totalSeconds = (durationMillis.coerceAtLeast(0) / 1_000)
    val minutes = totalSeconds / 60
    val seconds = totalSeconds % 60
    return "%d:%02d".format(Locale.ROOT, minutes, seconds)
}
