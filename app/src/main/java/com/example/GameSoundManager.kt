package com.example

import android.media.AudioAttributes
import android.media.AudioFormat
import android.media.AudioTrack
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlin.math.sin

class GameSoundManager {
    private val scope = CoroutineScope(Dispatchers.IO)
    private val sampleRate = 44100

    fun playShoot() {
        scope.launch {
            // Descending frequency sweep
            generateSweep(800.0, 300.0, 150)
        }
    }

    fun playHit() {
        scope.launch {
            // Short burst of noise
            generateNoise(50)
        }
    }

    fun playLevelClear() {
        scope.launch {
            // Arpeggio
            generateTone(440.0, 100) // A4
            Thread.sleep(100)
            generateTone(554.0, 100) // C#5
            Thread.sleep(100)
            generateTone(659.0, 200) // E5
            Thread.sleep(200)
            generateTone(880.0, 300) // A5
        }
    }

    private fun createAudioTrack(bufferSize: Int): AudioTrack {
        return AudioTrack.Builder()
            .setAudioAttributes(
                AudioAttributes.Builder()
                    .setUsage(AudioAttributes.USAGE_GAME)
                    .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                    .build()
            )
            .setAudioFormat(
                AudioFormat.Builder()
                    .setEncoding(AudioFormat.ENCODING_PCM_16BIT)
                    .setSampleRate(sampleRate)
                    .setChannelMask(AudioFormat.CHANNEL_OUT_MONO)
                    .build()
            )
            .setBufferSizeInBytes(bufferSize)
            .setTransferMode(AudioTrack.MODE_STATIC)
            .build()
    }

    private fun generateTone(freqOfTone: Double, durationMs: Int) {
        val numSamples = (durationMs * sampleRate / 1000.0).toInt()
        val generatedSnd = ByteArray(2 * numSamples)
        for (i in 0 until numSamples) {
            val dVal = sin(2 * Math.PI * i / (sampleRate / freqOfTone))
            val valShort = (dVal * 32767).toInt().toShort()
            generatedSnd[i * 2] = (valShort.toInt() and 0x00ff).toByte()
            generatedSnd[i * 2 + 1] = (valShort.toInt() and 0xff00 ushr 8).toByte()
        }
        val audioTrack = createAudioTrack(generatedSnd.size)
        audioTrack.write(generatedSnd, 0, generatedSnd.size)
        audioTrack.play()
        Thread.sleep(durationMs.toLong() + 50)
        audioTrack.release()
    }

    private fun generateSweep(startFreq: Double, endFreq: Double, durationMs: Int) {
        val numSamples = (durationMs * sampleRate / 1000.0).toInt()
        val generatedSnd = ByteArray(2 * numSamples)
        var phase = 0.0
        for (i in 0 until numSamples) {
            val progress = i.toDouble() / numSamples
            val currentFreq = startFreq + (endFreq - startFreq) * progress
            phase += 2 * Math.PI * currentFreq / sampleRate
            val dVal = sin(phase)
            val valShort = (dVal * 32767).toInt().toShort()
            generatedSnd[i * 2] = (valShort.toInt() and 0x00ff).toByte()
            generatedSnd[i * 2 + 1] = (valShort.toInt() and 0xff00 ushr 8).toByte()
        }
        val audioTrack = createAudioTrack(generatedSnd.size)
        audioTrack.write(generatedSnd, 0, generatedSnd.size)
        audioTrack.play()
        Thread.sleep(durationMs.toLong() + 50)
        audioTrack.release()
    }

    private fun generateNoise(durationMs: Int) {
        val numSamples = (durationMs * sampleRate / 1000.0).toInt()
        val generatedSnd = ByteArray(2 * numSamples)
        for (i in 0 until numSamples) {
            val dVal = (Math.random() * 2 - 1) * 0.3 // 30% volume to prevent clipping
            val valShort = (dVal * 32767).toInt().toShort()
            generatedSnd[i * 2] = (valShort.toInt() and 0x00ff).toByte()
            generatedSnd[i * 2 + 1] = (valShort.toInt() and 0xff00 ushr 8).toByte()
        }
        val audioTrack = createAudioTrack(generatedSnd.size)
        audioTrack.write(generatedSnd, 0, generatedSnd.size)
        audioTrack.play()
        Thread.sleep(durationMs.toLong() + 50)
        audioTrack.release()
    }
}
