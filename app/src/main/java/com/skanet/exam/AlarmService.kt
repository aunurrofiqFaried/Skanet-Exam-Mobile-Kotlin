package com.skanet.exam

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.media.AudioAttributes
import android.media.AudioManager
import android.media.MediaPlayer
import android.media.RingtoneManager
import android.os.Build
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import androidx.core.app.NotificationCompat

class AlarmService : Service() {

    companion object {
        const val EXTRA_DURATION_MS = "duration_ms"
        private const val CHANNEL_ID = "alarm_ujian"
        private const val NOTIF_ID = 1001
    }

    private var player: MediaPlayer? = null
    private val handler = Handler(Looper.getMainLooper())
    private var originalVolume = -1

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val notification = buildNotification()
        if (Build.VERSION.SDK_INT >= 29) {
            startForeground(
                NOTIF_ID, notification,
                ServiceInfo.FOREGROUND_SERVICE_TYPE_MEDIA_PLAYBACK
            )
        } else {
            startForeground(NOTIF_ID, notification)
        }

        startAlarm()

        // Jika ada durasi (> 0), alarm berhenti otomatis setelah durasi itu
        val duration = intent?.getLongExtra(EXTRA_DURATION_MS, 0L) ?: 0L
        handler.removeCallbacksAndMessages(null)
        if (duration > 0) {
            handler.postDelayed({ stopSelf() }, duration)
        }
        return START_NOT_STICKY
    }

    private fun startAlarm() {
        if (player?.isPlaying == true) return

        val am = getSystemService(Context.AUDIO_SERVICE) as AudioManager
        originalVolume = am.getStreamVolume(AudioManager.STREAM_ALARM)
        am.setStreamVolume(
            AudioManager.STREAM_ALARM,
            am.getStreamMaxVolume(AudioManager.STREAM_ALARM),
            0
        )

        player = MediaPlayer().apply {
            setAudioAttributes(
                AudioAttributes.Builder()
                    .setUsage(AudioAttributes.USAGE_ALARM)
                    .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                    .build()
            )
            this@AlarmService.resources.openRawResourceFd(R.raw.ringtone).use { afd ->
                setDataSource(afd.fileDescriptor, afd.startOffset, afd.length)
            }
            isLooping = true
            prepare()
            start()
        }
    }

    private fun buildNotification(): Notification {
        val nm = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        if (Build.VERSION.SDK_INT >= 26) {
            nm.createNotificationChannel(
                NotificationChannel(
                    CHANNEL_ID, "Alarm Ujian",
                    NotificationManager.IMPORTANCE_HIGH
                )
            )
        }
        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setSmallIcon(android.R.drawable.ic_lock_idle_alarm)
            .setContentTitle("Pelanggaran ujian terdeteksi")
            .setContentText("Kembali ke aplikasi ujian sekarang")
            .setOngoing(true)
            .build()
    }

    override fun onDestroy() {
        handler.removeCallbacksAndMessages(null)
        player?.run {
            if (isPlaying) stop()
            release()
        }
        player = null

        if (originalVolume >= 0) {
            val am = getSystemService(Context.AUDIO_SERVICE) as AudioManager
            am.setStreamVolume(AudioManager.STREAM_ALARM, originalVolume, 0)
        }
        super.onDestroy()
    }
}