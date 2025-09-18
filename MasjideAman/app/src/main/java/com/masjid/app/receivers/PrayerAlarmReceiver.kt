package com.masjid.app.receivers

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import androidx.core.content.ContextCompat
import com.masjid.app.services.AzanPlaybackService

class PrayerAlarmReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        val serviceIntent = Intent(context, AzanPlaybackService::class.java).apply {
            putExtra("PRAYER_NAME", intent.getStringExtra("PRAYER_NAME"))
            putExtra("TUNE_RESOURCE_ID", intent.getIntExtra("TUNE_RESOURCE_ID", 0))
            Intent.setAction = AzanPlaybackService.Companion.ACTION_PLAY
        }
        ContextCompat.startForegroundService(context, serviceIntent)
    }
}