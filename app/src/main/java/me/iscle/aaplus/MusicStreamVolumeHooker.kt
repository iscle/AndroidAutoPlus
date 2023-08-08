package me.iscle.aaplus

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.media.AudioFormat
import android.media.AudioManager
import android.media.AudioRecord
import android.media.MediaRecorder
import android.util.Log
import de.robv.android.xposed.XC_MethodHook
import de.robv.android.xposed.XposedHelpers
import java.nio.ByteBuffer
import java.nio.ByteOrder

private const val TAG = "MusicStreamVolumeHooker"

fun hookMusicStreamVolume(context: Context) {
    val audioManager = context.getSystemService(Context.AUDIO_SERVICE) as AudioManager
    var volume = audioManager.getStreamVolume(AudioManager.STREAM_MUSIC).toFloat() /
            audioManager.getStreamMaxVolume(AudioManager.STREAM_MUSIC).toFloat()
    val volumeReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            if (context == null || intent == null) return
            if (intent.action != "android.media.VOLUME_CHANGED_ACTION") return

            val streamType = intent.getIntExtra("android.media.EXTRA_VOLUME_STREAM_TYPE", -1)
            val streamValue = intent.getIntExtra("android.media.EXTRA_VOLUME_STREAM_VALUE", -1)

            // We only care about music volume
            if (streamType != AudioManager.STREAM_MUSIC) return

            val maxVolume = audioManager.getStreamMaxVolume(streamType)
            volume = streamValue.toFloat() / maxVolume.toFloat()
            Log.d(TAG, "onReceive: VOLUME_CHANGED_ACTION: music volume = $volume")
        }
    }

    XposedHelpers.findAndHookMethod(
        AudioRecord::class.java,
        "read",
        ByteArray::class.java,
        Int::class.java,
        Int::class.java,
        object : XC_MethodHook() {
            override fun afterHookedMethod(param: MethodHookParam?) {
                if (param == null) return
                val audioRecord = param.thisObject as AudioRecord
                val buffer = param.args[0] as ByteArray
                val offset = param.args[1] as Int
                val read = param.result as Int

                if (audioRecord.audioSource != MediaRecorder.AudioSource.REMOTE_SUBMIX || read <= 0) return

                val byteBuffer = ByteBuffer.wrap(buffer, offset, read)
                    .order(ByteOrder.nativeOrder())

                when (val audioFormat = audioRecord.audioFormat) {
                    AudioFormat.ENCODING_PCM_16BIT -> {
                        if (read % 2 != 0) {
                            Log.w(
                                TAG,
                                "read() returned a buffer with a number of bytes not divisible by 2"
                            )
                            return
                        }

                        val shortBuffer = byteBuffer.asShortBuffer()
                        for (i in 0 until read / 2) {
                            val sample = shortBuffer.get(i)
                            val newSample = (sample.toFloat() * volume).toInt().toShort()
                            shortBuffer.put(i, newSample)
                        }
                    }

                    AudioFormat.ENCODING_PCM_8BIT -> {
                        for (i in 0 until read) {
                            val sample = byteBuffer.get(i)
                            val newSample = (sample.toFloat() * volume).toInt().toByte()
                            byteBuffer.put(i, newSample)
                        }
                    }

                    AudioFormat.ENCODING_PCM_FLOAT -> {
                        if (read % 4 != 0) {
                            Log.w(
                                TAG,
                                "read() returned a buffer with a number of bytes not divisible by 4"
                            )
                            return
                        }

                        val floatBuffer = byteBuffer.asFloatBuffer()
                        for (i in 0 until read / 4) {
                            val sample = floatBuffer.get(i)
                            val newSample = sample * volume
                            floatBuffer.put(i, newSample)
                        }
                    }

                    else -> {
                        Log.d(TAG, "raed(): Unsupported audio format: $audioFormat")
                    }
                }
            }
        }
    )

    // Register the receiver only if hooking was successful
    val volumeFilter = IntentFilter("android.media.VOLUME_CHANGED_ACTION")
    context.registerReceiver(volumeReceiver, volumeFilter)
}
