package com.example.findme.ui.nav

import android.content.Context
import android.media.AudioAttributes
import android.media.AudioFocusRequest
import android.media.AudioManager
import android.media.ToneGenerator
import android.speech.tts.TextToSpeech
import android.speech.tts.UtteranceProgressListener
import com.example.findme.data.SettingsStore
import java.util.Locale

/**
 * Spoken guidance through Android's TextToSpeech. Music gets quieter while it speaks.
 * The engine starts on the first phrase, so the app pays nothing until guidance talks.
 */
class Voice(context: Context, private val settings: SettingsStore) {
    private val app = context.applicationContext

    /** The sound button in guidance and the Voice setting: the same switch. */
    val muted: Boolean get() = !settings.voice

    private var tts: TextToSpeech? = null
    private var ready = false
    /** Said before the engine was ready: spoken as soon as it is. */
    private var pending: String? = null
    private var nextId = 0

    private val audio = app.getSystemService(AudioManager::class.java)
    private val attributes = AudioAttributes.Builder()
        .setUsage(AudioAttributes.USAGE_ASSISTANCE_NAVIGATION_GUIDANCE)
        .setContentType(AudioAttributes.CONTENT_TYPE_SPEECH)
        .build()
    private val focus = AudioFocusRequest.Builder(AudioManager.AUDIOFOCUS_GAIN_TRANSIENT_MAY_DUCK)
        .setAudioAttributes(attributes)
        .build()

    fun toggleMuted() = setMuted(!muted)

    fun setMuted(value: Boolean) {
        settings.voice = !value
        if (value) {
            pending = null
            tts?.stop()
            audio.abandonAudioFocusRequest(focus)
        }
    }

    fun speak(text: String) {
        if (muted) return
        val engine = tts ?: start()
        if (!ready) {
            pending = text
            return
        }
        audio.requestAudioFocus(focus)
        engine.speak(text, TextToSpeech.QUEUE_ADD, null, "nav-${nextId++}")
    }

    private var tones: ToneGenerator? = null

    /** A short double beep: speeding. Silent when the voice is off. */
    fun beep() {
        if (muted) return
        val gen = tones ?: runCatching { ToneGenerator(AudioManager.STREAM_MUSIC, 90) }.getOrNull()?.also { tones = it }
        gen?.startTone(ToneGenerator.TONE_PROP_BEEP2, 400)
    }

    private fun start(): TextToSpeech {
        val engine = TextToSpeech(app) { status ->
            if (status != TextToSpeech.SUCCESS) return@TextToSpeech
            val engine = tts ?: return@TextToSpeech
            // The app speaks English; any English voice will do if there is no US one.
            if (engine.setLanguage(Locale.US) < TextToSpeech.LANG_AVAILABLE) engine.setLanguage(Locale.ENGLISH)
            ready = true
            pending?.let {
                pending = null
                speak(it)
            }
        }
        engine.setAudioAttributes(attributes)
        engine.setOnUtteranceProgressListener(object : UtteranceProgressListener() {
            override fun onStart(utteranceId: String?) = Unit
            override fun onDone(utteranceId: String?) = release()
            @Deprecated("Deprecated in Java")
            override fun onError(utteranceId: String?) = release()

            // Music back to full volume once nothing is left to say.
            private fun release() {
                if (tts?.isSpeaking != true) audio.abandonAudioFocusRequest(focus)
            }
        })
        tts = engine
        return engine
    }
}
