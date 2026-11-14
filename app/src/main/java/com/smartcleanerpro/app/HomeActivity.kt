package com.smartcleanerpro.app

import android.content.Intent
import android.content.res.Configuration
import android.os.Bundle
import androidx.activity.compose.setContent
import androidx.appcompat.app.AppCompatActivity
import androidx.compose.runtime.Composable
import androidx.compose.runtime.*
import androidx.appcompat.app.AppCompatDelegate
import androidx.core.os.LocaleListCompat
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp

@OptIn(ExperimentalMaterial3Api::class)
class HomeActivity : AppCompatActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        CacheReminderWorker.schedule(this)
        val prefs = getSharedPreferences("smart_cleaner_prefs", MODE_PRIVATE)
        val themeMode = prefs.getString("theme_mode", if (prefs.getBoolean("dark_theme", false)) "dark" else "system") ?: "system"
        val systemDark = (resources.configuration.uiMode and Configuration.UI_MODE_NIGHT_MASK) == Configuration.UI_MODE_NIGHT_YES
        val dark = themeMode == "dark" || (themeMode == "system" && systemDark)
        val language = prefs.getString("language", "system") ?: "system"
        AppCompatDelegate.setDefaultNightMode(when (themeMode) { "dark" -> AppCompatDelegate.MODE_NIGHT_YES; "light" -> AppCompatDelegate.MODE_NIGHT_NO; else -> AppCompatDelegate.MODE_NIGHT_FOLLOW_SYSTEM })
        if (language != "system") AppCompatDelegate.setApplicationLocales(LocaleListCompat.forLanguageTags(language))
        setContent {
            MaterialTheme(colorScheme = if (dark) darkColorScheme() else lightColorScheme(
                primary = Color(0xFF4857C9), secondary = Color(0xFF008F87), tertiary = Color(0xFFE97935)
            )) {
                Scaffold(topBar = {
                    TopAppBar(title = {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Default.Shield, null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(34.dp))
                            Spacer(Modifier.width(10.dp))
                            Column {
                                Text("Smart Cleaner Pro", style = MaterialTheme.typography.titleLarge)
                                Text("Version 5.0 • Device care", style = MaterialTheme.typography.labelSmall)
                            }
                        }
                    }, actions = {
                        IconButton(onClick = { open(SettingsActivity::class.java) }) {
                            Icon(Icons.Default.Settings, contentDescription = "Settings")
                        }
                    })
                }) { padding ->
                    LazyColumn(
                        modifier = Modifier.fillMaxSize().padding(padding),
                        contentPadding = PaddingValues(16.dp),
                        verticalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        item {
                            Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer)) {
                                Row(Modifier.fillMaxWidth().padding(18.dp), verticalAlignment = Alignment.CenterVertically) {
                                    Icon(Icons.Default.AutoAwesome, null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(46.dp))
                                    Spacer(Modifier.width(14.dp))
                                    Column {
                                        Text("Your phone, easier to manage", style = MaterialTheme.typography.titleMedium)
                                        Text("Review storage, apps and device performance safely.")
                                    }
                                }
                            }
                        }
                        item { FeatureCard("Storage & cache monitor", "Daily reminder when free storage reaches 200 MB or less.", Color(0xFFE97935), Icons.Default.Storage) { open(CacheMonitorActivity::class.java) } }
                        item { FeatureCard("Residual file scanner", "Scan accessible shared storage locations you select for possible leftovers.", Color(0xFF008F87), Icons.Default.FolderOpen) { open(ResidualsActivity::class.java) } }
                        item { FeatureCard("Schedule Clean Up", "Set recurring reminders to review storage and scan for possible junk.", Color(0xFF008F87), Icons.Default.CalendarMonth) { open(ScheduleActivity::class.java) } }
                        item { FeatureCard("Speed Boost", "Review RAM and open Android's safe app-management controls.", Color(0xFF4857C9), Icons.Default.Bolt) { open(SpeedBoostActivity::class.java) } }
                        item { FeatureCard("Installed apps", "Browse installed apps available to this app and open system settings.", Color(0xFFD94F79), Icons.Default.Apps) { open(AppsActivity::class.java) } }
                        item { FeatureCard("Large media scanner", "Open the original scanner to review large photos and videos.", Color(0xFF6D55B5), Icons.Default.PhotoLibrary) { open(MainActivity::class.java) } }
                        item {
                            OutlinedButton(onClick = { finishAffinity() }, modifier = Modifier.fillMaxWidth()) {
                                Icon(Icons.Default.ExitToApp, null)
                                Spacer(Modifier.width(8.dp))
                                Text("Exit app")
                            }
                        }
                        item {
                            Text("Android restricts access to other apps' private data. Scheduled cleanup sends a reminder; files are never deleted automatically.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    }
                }
            }
        }
    }

    private fun open(target: Class<*>) {
        startActivity(Intent(this, target))
    }

    override fun onStop() {
        super.onStop()
        // Clear only this cleaner's own cache, never another app's cache or user files.
        runCatching {
            cacheDir.listFiles()?.forEach { file ->
                if (file.isDirectory) file.deleteRecursively() else file.delete()
            }
        }
    }
}

@Composable
private fun FeatureCard(title: String, description: String, tint: Color, icon: androidx.compose.ui.graphics.vector.ImageVector, onClick: () -> Unit) {
    Card(colors = CardDefaults.cardColors(containerColor = tint.copy(alpha = 0.11f))) {
        Row(Modifier.fillMaxWidth().padding(14.dp), verticalAlignment = Alignment.CenterVertically) {
            Icon(icon, null, tint = tint, modifier = Modifier.size(34.dp))
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Text(title, style = MaterialTheme.typography.titleMedium, color = tint)
                Text(description, style = MaterialTheme.typography.bodySmall)
            }
            IconButton(onClick = onClick) { Icon(Icons.Default.ChevronRight, contentDescription = "Open") }
        }
    }
}
