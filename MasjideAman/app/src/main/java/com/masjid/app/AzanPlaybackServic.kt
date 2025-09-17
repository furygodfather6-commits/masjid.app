package com.masjid.app

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.media.MediaPlayer
import android.net.Uri
import android.os.Build
import android.os.IBinder
import androidx.core.app.NotificationCompat

class AzanPlaybackService : Service() {

    private var mediaPlayer: MediaPlayer? = null
    private var isPlaying = false
    private var prayerName = ""
    private var tuneResourceId = 0

    companion object {
        const val ACTION_PLAY = "com.masjid.app.ACTION_PLAY"
        const val ACTION_PAUSE_RESUME = "com.masjid.app.ACTION_PAUSE_RESUME"
        const val ACTION_STOP = "com.masjid.app.ACTION_STOP"
        const val NOTIFICATION_ID = 1
        const val CHANNEL_ID = "AZAN_PLAYBACK_CHANNEL"
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_PLAY -> {
                prayerName = intent.getStringExtra("PRAYER_NAME") ?: "Namaz ka waqt"
                tuneResourceId = intent.getIntExtra("TUNE_RESOURCE_ID", 0)
                startPlayback()
            }
            ACTION_PAUSE_RESUME -> togglePlayback()
            ACTION_STOP -> stopPlayback()
        }
        return START_NOT_STICKY
    }

    private fun startPlayback() {
        if (isPlaying || tuneResourceId == 0) return

        mediaPlayer?.release()
        val soundUri = Uri.parse("android.resource://${packageName}/$tuneResourceId")
        mediaPlayer = MediaPlayer.create(this, soundUri).apply {
            setOnCompletionListener { stopPlayback() }
            start()
        }
        isPlaying = true

        val notification = createNotification()
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            startForeground(NOTIFICATION_ID, notification, ServiceInfo.FOREGROUND_SERVICE_TYPE_MEDIA_PLAYBACK)
        } else {
            startForeground(NOTIFICATION_ID, notification)
        }
    }

    private fun togglePlayback() {
        if (isPlaying) {
            mediaPlayer?.pause()
            isPlaying = false
        } else {
            mediaPlayer?.start()
            isPlaying = true
        }
        updateNotification()
    }

    private fun stopPlayback() {
        mediaPlayer?.stop()
        mediaPlayer?.release()
        mediaPlayer = null
        isPlaying = false
        stopForeground(true)
        stopSelf()
    }

    private fun createNotification(): Notification {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(CHANNEL_ID, "Azan Playback", NotificationManager.IMPORTANCE_HIGH)
            (getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager).createNotificationChannel(channel)
        }

        val pauseResumeIntent = PendingIntent.getService(this, 0, Intent(this, AzanPlaybackService::class.java).apply { action = ACTION_PAUSE_RESUME }, PendingIntent.FLAG_IMMUTABLE)
        val stopIntent = PendingIntent.getService(this, 1, Intent(this, AzanPlaybackService::class.java).apply { action = ACTION_STOP }, PendingIntent.FLAG_IMMUTABLE)

        val pauseActionText = if (isPlaying) "Pause" else "Resume"
        val pauseActionIcon = if (isPlaying) android.R.drawable.ic_media_pause else android.R.drawable.ic_media_play

        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle("Azan: $prayerName")
            .setContentText(if (isPlaying) "Azan ho rahi hai..." else "Azan ruki hui hai.")
            .setSmallIcon(R.drawable.ic_alert_bell)
            .addAction(pauseActionIcon, pauseActionText, pauseResumeIntent)
            .addAction(android.R.drawable.ic_menu_close_clear_cancel, "Stop", stopIntent)
            .setOngoing(true)
            .build()
    }

    private fun updateNotification() {
        val notificationManager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        notificationManager.notify(NOTIFICATION_ID, createNotification())
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onDestroy() {
        super.onDestroy()
        stopPlayback()
    }
}