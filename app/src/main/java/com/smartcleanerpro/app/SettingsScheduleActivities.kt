package com.smartcleanerpro.app

import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.app.AppCompatDelegate
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.core.os.LocaleListCompat
import androidx.work.*
import java.util.concurrent.TimeUnit

@OptIn(ExperimentalMaterial3Api::class)
class SettingsActivity : AppCompatActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val prefs = getSharedPreferences("smart_cleaner_prefs", MODE_PRIVATE)
        setContent {
            var dark by remember { mutableStateOf(prefs.getBoolean("dark_theme", false)) }
            var language by remember { mutableStateOf(prefs.getString("language", "system") ?: "system") }
            MaterialTheme(colorScheme = if (dark) darkColorScheme() else lightColorScheme()) {
                Scaffold(topBar = { TopAppBar(title = { Text("Settings") }) }) { padding ->
                    Column(Modifier.fillMaxSize().padding(padding).padding(20.dp), verticalArrangement = Arrangement.spacedBy(18.dp)) {
                        Text("Appearance", style = MaterialTheme.typography.titleLarge)
                        Text("Theme")
                        listOf("System default", "Light", "Dark").forEachIndexed { index, label ->
                            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                                Text(label)
                                RadioButton(selected = when (index) { 0 -> !dark && prefs.getString("theme_mode", "system") == "system"; 1 -> !dark && prefs.getString("theme_mode") == "light"; else -> dark }, onClick = {
                                    val mode = when (index) { 0 -> "system"; 1 -> "light"; else -> "dark" }
                                    prefs.edit().putString("theme_mode", mode).putBoolean("dark_theme", mode == "dark").apply()
                                    dark = mode == "dark"
                                    AppCompatDelegate.setDefaultNightMode(when (mode) { "dark" -> AppCompatDelegate.MODE_NIGHT_YES; "light" -> AppCompatDelegate.MODE_NIGHT_NO; else -> AppCompatDelegate.MODE_NIGHT_FOLLOW_SYSTEM })
                                })
                            }
                        }
                        Divider()
                        Text("Language", style = MaterialTheme.typography.titleLarge)
                        listOf("system" to "System default", "en" to "English", "fa" to "فارسی").forEach { (code, label) ->
                            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                                Text(label)
                                RadioButton(selected = language == code, onClick = {
                                    language = code
                                    prefs.edit().putString("language", code).apply()
                                    AppCompatDelegate.setApplicationLocales(if (code == "system") LocaleListCompat.getEmptyLocaleList() else LocaleListCompat.forLanguageTags(code))
                                })
                            }
                        }
                        Text("Language changes may recreate the activity. Your existing scans and files are not affected.", style = MaterialTheme.typography.bodySmall)
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
class ScheduleActivity : AppCompatActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val prefs = getSharedPreferences("smart_cleaner_schedule", MODE_PRIVATE)
        setContent {
            var enabled by remember { mutableStateOf(prefs.getBoolean("enabled", false)) }
            var hours by remember { mutableIntStateOf(prefs.getInt("interval_hours", 24)) }
            MaterialTheme {
                Scaffold(topBar = { TopAppBar(title = { Text("Schedule Clean Up") }) }) { padding ->
                    Column(Modifier.fillMaxSize().padding(padding).padding(20.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
                        Text("Automatic schedule", style = MaterialTheme.typography.titleLarge)
                        Text("Choose how often Smart Cleaner Pro should remind you to review storage and run a scan.")
                        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                            Text("Enable schedule")
                            Switch(checked = enabled, onCheckedChange = {
                                enabled = it
                                prefs.edit().putBoolean("enabled", it).apply()
                                updateSchedule(it, hours)
                            })
                        }
                        Text("Reminder interval")
                        listOf(12, 24, 48).forEach { value ->
                            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                                Text("Every $value hours")
                                RadioButton(selected = hours == value, onClick = {
                                    hours = value
                                    prefs.edit().putInt("interval_hours", value).apply()
                                    if (enabled) updateSchedule(true, value)
                                })
                            }
                        }
                        if (enabled) {
                            Text("Schedule enabled. Android may defer background work to preserve battery.", color = MaterialTheme.colorScheme.primary)
                        } else {
                            Text("Schedule is disabled. No recurring reminders will be sent.")
                        }
                        Text("For your safety, this feature schedules a reminder only. It does not delete files or clear other apps' caches automatically.", style = MaterialTheme.typography.bodySmall)
                    }
                }
            }
        }
    }

    private fun updateSchedule(enable: Boolean, hours: Int) {
        val work = WorkManager.getInstance(this)
        if (!enable) {
            work.cancelUniqueWork("scheduled_cleanup_review")
            return
        }
        val request = PeriodicWorkRequestBuilder<ScheduledCleanupWorker>(hours.toLong(), TimeUnit.HOURS)
            .setConstraints(Constraints.Builder().setRequiredNetworkType(NetworkType.NOT_REQUIRED).build())
            .build()
        work.enqueueUniquePeriodicWork("scheduled_cleanup_review", ExistingPeriodicWorkPolicy.UPDATE, request)
    }
}

class ScheduledCleanupWorker(context: android.content.Context, params: WorkerParameters) : Worker(context, params) {
    override fun doWork(): Result {
        val manager = applicationContext.getSystemService(android.content.Context.NOTIFICATION_SERVICE) as android.app.NotificationManager
        val channel = "scheduled_cleanup"
        if (android.os.Build.VERSION.SDK_INT >= 26) {
            manager.createNotificationChannel(android.app.NotificationChannel(channel, "Scheduled cleanup reminders", android.app.NotificationManager.IMPORTANCE_DEFAULT))
        }
        val note = androidx.core.app.NotificationCompat.Builder(applicationContext, channel)
            .setSmallIcon(android.R.drawable.ic_menu_manage)
            .setContentTitle("Time for a storage review")
            .setContentText("Open Smart Cleaner Pro to review storage and scan accessible folders.")
            .setAutoCancel(true)
            .build()
        runCatching { manager.notify(510, note) }
        return Result.success()
    }
}
