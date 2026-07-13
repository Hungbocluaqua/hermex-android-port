package com.uzairansar.hermex.ui.chat

import android.content.Context
import android.media.MediaRecorder
import android.os.Build
import java.io.File

class VoiceNoteRecorder(
    private val context: Context,
) {
    private var recorder: MediaRecorder? = null
    private var outputFile: File? = null
    @Volatile
    private var recordingStoppedByLimit = false
    var lastErrorMessage: String? = null
        private set

    val isRecording: Boolean
        get() = recorder != null

    fun start(onLimitReached: () -> Unit = {}): File {
        stop(delete = true)
        lastErrorMessage = null
        val file = File(context.cacheDir, "voice-note-${System.currentTimeMillis()}.m4a")
        val nextRecorder = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            MediaRecorder(context)
        } else {
            @Suppress("DEPRECATION")
            MediaRecorder()
        }
        return try {
            nextRecorder.setAudioSource(MediaRecorder.AudioSource.MIC)
            nextRecorder.setOutputFormat(MediaRecorder.OutputFormat.MPEG_4)
            nextRecorder.setAudioEncoder(MediaRecorder.AudioEncoder.AAC)
            nextRecorder.setAudioEncodingBitRate(96_000)
            nextRecorder.setAudioSamplingRate(44_100)
            nextRecorder.setMaxDuration(MAXIMUM_DURATION_MILLIS)
            nextRecorder.setMaxFileSize(MAXIMUM_FILE_BYTES)
            nextRecorder.setOnInfoListener { _, what, _ ->
                if (
                    what == MediaRecorder.MEDIA_RECORDER_INFO_MAX_DURATION_REACHED ||
                    what == MediaRecorder.MEDIA_RECORDER_INFO_MAX_FILESIZE_REACHED
                ) {
                    recordingStoppedByLimit = true
                    onLimitReached()
                }
            }
            nextRecorder.setOutputFile(file.absolutePath)
            nextRecorder.prepare()
            nextRecorder.start()
            recorder = nextRecorder
            outputFile = file
            recordingStoppedByLimit = false
            file
        } catch (error: Throwable) {
            runCatching { nextRecorder.reset() }
            runCatching { nextRecorder.release() }
            runCatching { file.delete() }
            throw error
        }
    }

    fun stop(delete: Boolean = false): File? {
        val file = outputFile
        val current = recorder
        val stoppedByLimit = recordingStoppedByLimit
        recorder = null
        outputFile = null
        recordingStoppedByLimit = false
        var stoppedCleanly = current == null || stoppedByLimit
        if (current != null) {
            if (!stoppedByLimit) {
                stoppedCleanly = runCatching { current.stop() }.isSuccess
            }
            runCatching { current.reset() }
            runCatching { current.release() }
        }
        if (delete || !stoppedCleanly) {
            if (!delete && !stoppedCleanly) {
                lastErrorMessage = "Could not finalize the voice note. Try recording again."
            }
            file?.delete()
            return null
        }
        return file?.takeIf { it.exists() && it.length() in 1..MAXIMUM_FILE_BYTES }
            ?: file?.let { invalid ->
                lastErrorMessage = if (invalid.length() > MAXIMUM_FILE_BYTES) {
                    "Voice notes must be 20 MB or smaller."
                } else {
                    "Voice note was empty."
                }
                runCatching { invalid.delete() }
                null
            }
    }

    private companion object {
        const val MAXIMUM_DURATION_MILLIS = 5 * 60 * 1_000
        const val MAXIMUM_FILE_BYTES = 20L * 1_024L * 1_024L
    }
}
