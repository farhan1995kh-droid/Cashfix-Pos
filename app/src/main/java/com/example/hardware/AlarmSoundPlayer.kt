package com.example.hardware

import android.content.Context
import android.media.AudioAttributes
import android.media.AudioFormat
import android.media.AudioTrack
import android.os.Build
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import kotlinx.coroutines.*
import kotlin.math.PI
import kotlin.math.min
import kotlin.math.sin

/**
 * Plays ONLY CashFix POS's own built-in synthesized chime sound on the app media output
 * (AudioAttributes.USAGE_MEDIA / CONTENT_TYPE_SONIFICATION) for incoming QR/online orders.
 * Never invokes Android's system alarm clock, alarm ringtone, or STREAM_ALARM.
 */
class AlarmSoundPlayer(private val context: Context) {
    private var alarmJob: Job? = null
    @Volatile
    private var activeTrack: AudioTrack? = null

    fun start15SecondAlarm(
        scope: CoroutineScope,
        onTickRemainingSeconds: (Int) -> Unit,
        onCompleted: () -> Unit
    ) {
        stop()
        alarmJob = scope.launch(Dispatchers.Default) {
            val chimePcm = synthesizeCashFixOrderChimePcm(SAMPLE_RATE)
            val vibrator = getVibrator()

            for (sec in 15 downTo 1) {
                if (!isActive) break
                withContext(Dispatchers.Main) { onTickRemainingSeconds(sec) }
                playAppChimeOnce(chimePcm)
                runCatching {
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                        vibrator?.vibrate(VibrationEffect.createOneShot(260, VibrationEffect.DEFAULT_AMPLITUDE))
                    } else {
                        @Suppress("DEPRECATION")
                        vibrator?.vibrate(260)
                    }
                }
                delay(1000L)
            }
            releaseActiveTrack()
            withContext(Dispatchers.Main) { onCompleted() }
        }
    }

    fun stop() {
        alarmJob?.cancel()
        alarmJob = null
        releaseActiveTrack()
    }

    private fun playAppChimeOnce(pcm: ShortArray) {
        releaseActiveTrack()
        runCatching {
            val bufferBytes = pcm.size * 2
            val attrs = AudioAttributes.Builder()
                .setUsage(AudioAttributes.USAGE_MEDIA)
                .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                .build()
            val format = AudioFormat.Builder()
                .setSampleRate(SAMPLE_RATE)
                .setEncoding(AudioFormat.ENCODING_PCM_16BIT)
                .setChannelMask(AudioFormat.CHANNEL_OUT_MONO)
                .build()

            val track = AudioTrack.Builder()
                .setAudioAttributes(attrs)
                .setAudioFormat(format)
                .setBufferSizeInBytes(bufferBytes)
                .setTransferMode(AudioTrack.MODE_STATIC)
                .build()

            activeTrack = track
            track.write(pcm, 0, pcm.size)
            track.play()
        }
    }

    private fun releaseActiveTrack() {
        val track = activeTrack
        activeTrack = null
        runCatching {
            track?.pause()
            track?.flush()
            track?.stop()
            track?.release()
        }
    }

    private fun getVibrator(): Vibrator? {
        return runCatching {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                val vm = context.getSystemService(Context.VIBRATOR_MANAGER_SERVICE) as VibratorManager
                vm.defaultVibrator
            } else {
                @Suppress("DEPRECATION")
                context.getSystemService(Context.VIBRATOR_SERVICE) as Vibrator
            }
        }.getOrNull()
    }

    companion object {
        const val SAMPLE_RATE = 22050

        /**
         * Generates CashFix POS's own custom 3-note rising POS order chime (C6 -> E6 -> G6)
         * as 16-bit mono PCM samples with warm harmonics and smooth fade envelopes.
         */
        fun synthesizeCashFixOrderChimePcm(sampleRate: Int = SAMPLE_RATE): ShortArray {
            val notesHz = doubleArrayOf(1046.50, 1318.51, 1567.98) // C6, E6, G6
            val noteDurationMs = 135
            val gapDurationMs = 35
            val noteSamples = (sampleRate * noteDurationMs) / 1000
            val gapSamples = (sampleRate * gapDurationMs) / 1000
            val totalSamples = notesHz.size * noteSamples + (notesHz.size - 1) * gapSamples
            val pcm = ShortArray(totalSamples)

            var cursor = 0
            notesHz.forEachIndexed { noteIdx, freq ->
                val attack = min(noteSamples / 8, 220)
                val release = noteSamples / 2
                for (i in 0 until noteSamples) {
                    val t = i.toDouble() / sampleRate.toDouble()
                    val env = when {
                        i < attack -> i.toDouble() / attack.toDouble()
                        i > noteSamples - release -> (noteSamples - i).toDouble() / release.toDouble()
                        else -> 1.0
                    }.coerceIn(0.0, 1.0)
                    // Fundamental + soft 2nd harmonic for a crisp, unmistakable POS bell chime
                    val wave = (0.78 * sin(2.0 * PI * freq * t)) + (0.22 * sin(2.0 * PI * freq * 2.0 * t))
                    val sample = (wave * env * 24000.0).toInt().coerceIn(Short.MIN_VALUE.toInt(), Short.MAX_VALUE.toInt())
                    pcm[cursor + i] = sample.toShort()
                }
                cursor += noteSamples
                if (noteIdx < notesHz.lastIndex) {
                    cursor += gapSamples
                }
            }
            return pcm
        }
    }
}
