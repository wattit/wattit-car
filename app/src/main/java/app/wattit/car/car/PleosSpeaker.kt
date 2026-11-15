package app.wattit.car.car

import android.content.Context
import ai.pleos.playground.tts.TextToSpeech
import ai.pleos.playground.tts.constant.ErrorType
import ai.pleos.playground.tts.constant.Mode
import ai.pleos.playground.tts.listener.OnInitializeListener

interface Speaker {
    fun speak(text: String)
    fun release()
}

/**
 * Pleos TextToSpeech in ON_DEVICE mode (no app registration). Text queued before the engine is ready
 * is spoken once it is; a failed engine stays silent, because the same sentence is on screen.
 */
class PleosSpeaker(context: Context) : Speaker {
    private val tts = TextToSpeech(context.applicationContext, Mode.ON_DEVICE)
    private var ready = false
    private var pending: String? = null

    init {
        runCatching {
            tts.initialize(object : OnInitializeListener {
                override fun onSuccess() {
                    ready = true
                    pending?.let { say(it) }
                    pending = null
                }

                override fun onFailure(error: ErrorType) {
                    pending = null
                }
            })
        }
    }

    override fun speak(text: String) {
        if (ready) say(text) else pending = text
    }

    private fun say(text: String) {
        runCatching { tts.speak(text, true) }
    }

    override fun release() {
        runCatching { tts.stop() }
        runCatching { tts.release() }
    }
}
