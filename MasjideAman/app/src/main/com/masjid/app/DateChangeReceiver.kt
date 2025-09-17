package com.masjid.app

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent

class DateChangeReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context?, intent: Intent?) {
        // We only care about the date changing
        if (intent?.action == Intent.ACTION_DATE_CHANGED) {
            // Instead of sending another broadcast, we store a timestamp in SharedPreferences.
            // The HomeFragment will check this value when it resumes.
            val sharedPreferences = context?.getSharedPreferences("app_prefs", Context.MODE_PRIVATE)
            with(sharedPreferences?.edit()) {
                this?.putLong("last_date_change", System.currentTimeMillis())
                this?.apply()
            }
        }
    }
}