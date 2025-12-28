package com.example.nfc_reader_01.utils

import android.content.Context
import android.media.MediaPlayer
import com.example.nfc_reader_01.R

/**
 * Manages playback of sounds.
 *
 * @param context The application context.
 */
class SoundManager(private val context: Context) {

    private var mediaPlayer: MediaPlayer? = null

    /**
     * Plays a beep sound.
     *
     * This function initializes a [MediaPlayer] instance and plays the beep sound from the raw resources.
     * It also sets an on-completion listener to release the [MediaPlayer] resources after the sound has finished playing.
     */
    fun playBeep() {
        if (mediaPlayer == null) {
            mediaPlayer = MediaPlayer.create(context, R.raw.beep)
            mediaPlayer?.setOnCompletionListener {
                it.release()
                mediaPlayer = null
            }
        }
        mediaPlayer?.start()
    }
}
