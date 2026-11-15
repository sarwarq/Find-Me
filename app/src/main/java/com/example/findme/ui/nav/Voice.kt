package com.example.findme.ui.nav

import android.content.Context
import android.media.AudioAttributes
import android.media.AudioFocusRequest
import android.media.AudioManager
import android.media.ToneGenerator
import android.speech.tts.TextToSpeech
import android.speech.tts.UtteranceProgressListener
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import com.example.findme.data.SettingsStore
import com.example.findme.data.VoiceLanguage
import java.util.Locale

/**
 * Spoken guidance through Android's TextToSpeech. Music gets quieter while it speaks.
 * The engine starts on the first phrase, so the app pays nothing until guidance talks.
 */
class Voice(context: Context, private val settings: SettingsStore) {
    private val app = context.applicationContext

    /** The sound button in guidance and the Voice setting: the same switch. */
    val muted: Boolean get() = !settings.voice

    /** A language chosen in the settings that this phone has no voice for (shown there). */
    var missing by mutableStateOf<VoiceLanguage?>(null)
        private set

    /** The language phrases are made in: the chosen one, or English when the phone can't speak it. */
    val language: VoiceLanguage
        get() = settings.voiceLanguage.takeIf { it != missing } ?: VoiceLanguage.English

    private var tts: TextToSpeech? = null
    private var ready = false
    /** The engine speaks this language now. */
    private var applied: VoiceLanguage? = null
    /** Said before the engine was ready, and its language: spoken as soon as it is. */
    private var pending: Pair<String, VoiceLanguage>? = null
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

    /** Guidance is starting: get the engine ready before the first prompt. */
    fun warmUp() {
        if (!muted && tts == null) start()
    }

    /** [text] is in [language] as it was when the text was made. */
    fun speak(text: String) {
        if (muted) return
        val lang = language
        val engine = tts ?: start()
        if (!ready) {
            pending = text to lang
            return
        }
        // Russian words read by an English voice are noise: such a phrase is dropped.
        if (applyLanguage(engine) != lang) return
        audio.requestAudioFocus(focus)
        engine.speak(text, TextToSpeech.QUEUE_ADD, null, "nav-${nextId++}")
    }

    /** Switches the engine to [language]; returns the one it speaks now. */
    private fun applyLanguage(engine: TextToSpeech): VoiceLanguage {
        val want = language
        if (want == applied) return want
        if (engine.setLanguage(want.locale) >= TextToSpeech.LANG_AVAILABLE) {
            applied = want
            return want
        }
        if (want != VoiceLanguage.English) missing = want
        // Any English voice will do if there is no US one.
        if (engine.setLanguage(Locale.US) < TextToSpeech.LANG_AVAILABLE) engine.setLanguage(Locale.ENGLISH)
        applied = VoiceLanguage.English
        return VoiceLanguage.English
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
            applyLanguage(engine)
            ready = true
            pending?.let { (text, lang) ->
                pending = null
                if (lang == applied) speak(text)
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
