package com.buzzkill.service

import android.content.Context
import android.speech.tts.TextToSpeech
import java.util.Locale
import java.util.concurrent.atomic.AtomicBoolean

/**
 * 用于朗读操作的惰性初始化文字转语音封装。它会将待朗读的内容排队，
 * 使多条通知按顺序朗读，而不是被丢弃。
 */
class TtsManager(context: Context) : TextToSpeech.OnInitListener {

    private val ready = AtomicBoolean(false)
    private val pending = mutableListOf<String>()
    private val tts = TextToSpeech(context.applicationContext, this)

    override fun onInit(status: Int) {
        if (status == TextToSpeech.SUCCESS) {
            tts.language = Locale.getDefault()
            ready.set(true)
            synchronized(pending) {
                pending.forEach { speakNow(it) }
                pending.clear()
            }
        }
    }

    fun speak(text: String) {
        if (text.isBlank()) return
        if (ready.get()) {
            speakNow(text)
        } else {
            synchronized(pending) { pending.add(text) }
        }
    }

    private fun speakNow(text: String) {
        tts.speak(text, TextToSpeech.QUEUE_ADD, null, "buzzkill-${text.hashCode()}")
    }

    fun shutdown() {
        runCatching { tts.shutdown() }
    }
}
