package com.verified.incar

import android.content.Context
import android.media.AudioAttributes
import android.media.AudioFocusRequest
import android.media.AudioManager
import android.speech.tts.TextToSpeech
import android.speech.tts.UtteranceProgressListener
import java.util.Locale

/** 음성 안내. 순정 내비·통화·경고가 오디오 포커스를 쥐고 있으면 말하지 않고 양보한다. */
class Speaker(ctx: Context) {
    private val audio = ctx.getSystemService(AudioManager::class.java)
    private val attrs = AudioAttributes.Builder()
        .setUsage(AudioAttributes.USAGE_ASSISTANCE_NAVIGATION_GUIDANCE)
        .setContentType(AudioAttributes.CONTENT_TYPE_SPEECH)
        .build()
    private var ready = false
    private var focus: AudioFocusRequest? = null
    private val tts = TextToSpeech(ctx) { status ->
        ready = status == TextToSpeech.SUCCESS
    }.also {
        it.setAudioAttributes(attrs)
        // 한 문장이 끝나면 포커스를 바로 돌려줘 줄여 둔 음악·내비 소리를 되살린다
        it.setOnUtteranceProgressListener(object : UtteranceProgressListener() {
            override fun onStart(id: String?) {}
            override fun onDone(id: String?) = finished()
            @Deprecated("Deprecated in Java")
            override fun onError(id: String?) = finished()
        })
    }

    /** 말하는 중인 문장 수. 0이 되어야 포커스를 돌려준다(앞 문장이 끝났다고 뒤 문장 도중에 음악이 커지지 않게). */
    private val pending = java.util.concurrent.atomic.AtomicInteger(0)

    private fun finished() {
        if (pending.decrementAndGet() <= 0) {
            pending.set(0)
            release()
        }
    }

    private fun release() {
        focus?.let { audio.abandonAudioFocusRequest(it) }
        focus = null
    }

    /** 말했으면 null, 못 했으면 안내줄에 붙일 이유(화면 안내로만 남는다). 통화·벨 울림 중에도 말하지 않는다. */
    fun speak(text: String): String? {
        if (!ready) return "음성 준비 안 됨"
        // 자동차 OS는 통화 중에도 내비 음성 포커스를 기본으로 함께 준다. 통화·벨 울림 중에는 말하지 않고 화면 안내로 남긴다
        if (audio.mode != AudioManager.MODE_NORMAL) return "통화 중"
        // 한국어 음성 데이터가 없는 차에서는 말하지 않고 화면 안내로 남긴다
        val lang = tts.setLanguage(Locale.KOREAN)
        if (lang == TextToSpeech.LANG_MISSING_DATA || lang == TextToSpeech.LANG_NOT_SUPPORTED) return "한국어 음성 없음"
        val req = AudioFocusRequest.Builder(AudioManager.AUDIOFOCUS_GAIN_TRANSIENT_MAY_DUCK).setAudioAttributes(attrs).build()
        if (audio.requestAudioFocus(req) != AudioManager.AUDIOFOCUS_REQUEST_GRANTED) return "순정 오디오 우선"
        focus = req
        pending.incrementAndGet()
        if (tts.speak(text, TextToSpeech.QUEUE_ADD, null, text.hashCode().toString()) == TextToSpeech.ERROR) {
            finished()
            return "음성 준비 안 됨"
        }
        return null
    }

    /** 시동 OFF 때 말하던 것을 멈추고 포커스를 돌려준다. */
    fun stop() {
        tts.stop()
        pending.set(0)
        release()
    }

    fun shutdown() {
        tts.shutdown()
        pending.set(0)
        release()
    }
}
