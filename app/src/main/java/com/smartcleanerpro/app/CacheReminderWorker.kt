package com.smartcleanerpro.app

import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import android.os.Environment
import android.os.StatFs
import androidx.core.app.NotificationCompat
import androidx.work.*
import java.util.concurrent.TimeUnit

class CacheReminderWorker(context: Context, params: WorkerParameters) : Worker(context, params) {
    override fun doWork(): Result {
        val freeBytes = runCatching { StatFs(Environment.getDataDirectory().path).availableBytes }.getOrDefault(Long.MAX_VALUE)
        if (freeBytes <= 200L * 1024L * 1024L) {
            val manager = applicationContext.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            val channelId = "storage_reminders"
            if (android.os.Build.VERSION.SDK_INT >= 26) {
                manager.createNotificationChannel(NotificationChannel(channelId, "Storage reminders", NotificationManager.IMPORTANCE_DEFAULT))
            }
            val notification = NotificationCompat.Builder(applicationContext, channelId)
                .setSmallIcon(android.R.drawable.ic_dialog_info)
                .setContentTitle("Storage is almost full")
                .setContentText("200 MB or less is available. Review Android storage cleanup options.")
                .setAutoCancel(true)
                .build()
            runCatching { manager.notify(200, notification) }
        }
        return Result.success()
    }

    companion object {
        fun schedule(context: Context) {
            val request = PeriodicWorkRequestBuilder<CacheReminderWorker>(24, TimeUnit.HOURS).build()
            WorkManager.getInstance(context).enqueueUniquePeriodicWork("daily_storage_reminder", ExistingPeriodicWorkPolicy.UPDATE, request)
        }
    }
}
