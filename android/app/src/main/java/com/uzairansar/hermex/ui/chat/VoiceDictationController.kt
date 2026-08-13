package com.uzairansar.hermex.ui.chat

import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.Bundle
import android.speech.RecognitionListener
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer
import java.util.Locale

class VoiceDictationController(
    private val context: Context,
) {
    private var recognizer: SpeechRecognizer? = null

    val isListening: Boolean
        get() = recognizer != null

    val isOnDeviceRecognitionAvailable: Boolean
        get() = Build.VERSION.SDK_INT >= Build.VERSION_CODES.S &&
            SpeechRecognizer.isOnDeviceRecognitionAvailable(context)

    fun start(
        onDeviceOnly: Boolean = false,
        onText: (String, Boolean) -> Unit,
        onListeningChanged: (Boolean) -> Unit,
        onError: (String) -> Unit,
    ) {
        cancel()
        if (onDeviceOnly && !isOnDeviceRecognitionAvailable) {
            onError("On-device dictation is not available on this device.")
            return
        }
        if (!onDeviceOnly && !SpeechRecognizer.isRecognitionAvailable(context)) {
            onError("Voice dictation is not available on this device.")
            return
        }

        val next = runCatching {
            if (
                isOnDeviceRecognitionAvailable
            ) {
                SpeechRecognizer.createOnDeviceSpeechRecognizer(context)
            } else {
                SpeechRecognizer.createSpeechRecognizer(context)
            }
        }.getOrElse { error ->
            onError(error.message ?: "Could not start voice dictation.")
            return
        }
        recognizer = next
        next.setRecognitionListener(object : RecognitionListener {
            override fun onReadyForSpeech(params: Bundle?) = Unit
            override fun onBeginningOfSpeech() = Unit
            override fun onRmsChanged(rmsdB: Float) = Unit
            override fun onBufferReceived(buffer: ByteArray?) = Unit
            override fun onEndOfSpeech() = Unit
            override fun onEvent(eventType: Int, params: Bundle?) = Unit

            override fun onPartialResults(partialResults: Bundle?) {
                if (recognizer !== next) return
                partialResults?.bestSpeechResult()?.let { onText(it, false) }
            }

            override fun onResults(results: Bundle?) {
                if (recognizer !== next) return
                results?.bestSpeechResult()?.let { onText(it, true) }
                finish(next, onListeningChanged)
            }

            override fun onError(error: Int) {
                if (recognizer !== next) return
                finish(next, onListeningChanged)
                if (error != SpeechRecognizer.ERROR_NO_MATCH && error != SpeechRecognizer.ERROR_SPEECH_TIMEOUT) {
                    onError(voiceRecognitionErrorMessage(error))
                }
            }
        })
        onListeningChanged(true)
        val intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
            putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
            putExtra(RecognizerIntent.EXTRA_LANGUAGE, Locale.getDefault().toLanguageTag())
            putExtra(RecognizerIntent.EXTRA_PARTIAL_RESULTS, true)
            putExtra(RecognizerIntent.EXTRA_MAX_RESULTS, 3)
        }
        runCatching { next.startListening(intent) }
            .onFailure { error ->
                finish(next, onListeningChanged)
                onError(error.message ?: "Could not start voice dictation.")
            }
    }

    fun stop() {
        recognizer?.stopListening()
    }

    fun cancel(onListeningChanged: ((Boolean) -> Unit)? = null) {
        val current = recognizer
        recognizer = null
        current?.cancel()
        current?.destroy()
        onListeningChanged?.invoke(false)
    }

    private fun finish(expected: SpeechRecognizer, onListeningChanged: (Boolean) -> Unit) {
        if (recognizer !== expected) {
            expected.destroy()
            return
        }
        recognizer = null
        expected.destroy()
        onListeningChanged(false)
    }
}

private fun Bundle.bestSpeechResult(): String? =
    getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
        ?.firstOrNull()
        ?.trim()
        ?.takeIf { it.isNotEmpty() }

internal fun voiceRecognitionErrorMessage(error: Int): String = when (error) {
    SpeechRecognizer.ERROR_AUDIO -> "The microphone could not capture audio."
    SpeechRecognizer.ERROR_INSUFFICIENT_PERMISSIONS -> "Microphone permission is required for voice dictation."
    SpeechRecognizer.ERROR_NETWORK, SpeechRecognizer.ERROR_NETWORK_TIMEOUT -> "Voice dictation could not reach the speech service."
    SpeechRecognizer.ERROR_RECOGNIZER_BUSY -> "Voice dictation is already in use."
    SpeechRecognizer.ERROR_SERVER, SpeechRecognizer.ERROR_SERVER_DISCONNECTED -> "The speech service stopped unexpectedly."
    else -> "Voice dictation stopped unexpectedly."
}
