package com.example.hardware

import android.content.Context
import android.media.AudioManager
import android.media.ToneGenerator
import android.os.Build
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import kotlinx.coroutines.*

class AlarmSoundPlayer(private val context: Context) {
    private var alarmJob: Job? = null
    private var toneGenerator: ToneGenerator? = null

    fun start15SecondAlarm(
        scope: CoroutineScope,
        onTickRemainingSeconds: (Int) -> Unit,
        onCompleted: () -> Unit
    ) {
        stop()
        alarmJob = scope.launch(Dispatchers.Default) {
            val tg = runCatching { ToneGenerator(AudioManager.STREAM_ALARM, 90) }.getOrNull()
            toneGenerator = tg
            val vibrator = getVibrator()

            for (sec in 15 downTo 1) {
                if (!isActive) break
                withContext(Dispatchers.Main) { onTickRemainingSeconds(sec) }
                runCatching {
                    tg?.startTone(ToneGenerator.TONE_CDMA_EMERGENCY_RINGBACK, 450)
                }
                runCatching {
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                        vibrator?.vibrate(VibrationEffect.createOneShot(320, VibrationEffect.DEFAULT_AMPLITUDE))
                    } else {
                        @Suppress("DEPRECATION")
                        vibrator?.vibrate(320)
                    }
                }
                delay(1000L)
            }
            runCatching { tg?.release() }
            toneGenerator = null
            withContext(Dispatchers.Main) { onCompleted() }
        }
    }

    fun stop() {
        alarmJob?.cancel()
        alarmJob = null
        runCatching {
            toneGenerator?.stopTone()
            toneGenerator?.release()
        }
        toneGenerator = null
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
}
