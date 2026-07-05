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

    val isRecording: Boolean
        get() = recorder != null

    fun start(): File {
        stop(delete = true)
        val file = File(context.cacheDir, "voice-note-${System.currentTimeMillis()}.m4a")
        val nextRecorder = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            MediaRecorder(context)
        } else {
            @Suppress("DEPRECATION")
            MediaRecorder()
        }
        nextRecorder.setAudioSource(MediaRecorder.AudioSource.MIC)
        nextRecorder.setOutputFormat(MediaRecorder.OutputFormat.MPEG_4)
        nextRecorder.setAudioEncoder(MediaRecorder.AudioEncoder.AAC)
        nextRecorder.setAudioEncodingBitRate(96_000)
        nextRecorder.setAudioSamplingRate(44_100)
        nextRecorder.setOutputFile(file.absolutePath)
        nextRecorder.prepare()
        nextRecorder.start()
        recorder = nextRecorder
        outputFile = file
        return file
    }

    fun stop(delete: Boolean = false): File? {
        val file = outputFile
        val current = recorder
        recorder = null
        outputFile = null
        if (current != null) {
            runCatching { current.stop() }
            current.reset()
            current.release()
        }
        if (delete) {
            file?.delete()
            return null
        }
        return file?.takeIf { it.exists() && it.length() > 0L }
    }
}
