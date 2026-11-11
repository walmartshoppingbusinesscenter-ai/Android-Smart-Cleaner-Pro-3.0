package com.smartcleanerpro.app

import android.app.ActivityManager
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.os.Environment
import android.os.StatFs
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Android
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.documentfile.provider.DocumentFile
import java.util.Locale

private fun openSystemSettings(activity: AppCompatActivity, action: String, uri: Uri? = null) {
    runCatching { activity.startActivity(Intent(action).apply { if (uri != null) data = uri }) }
}

@OptIn(ExperimentalMaterial3Api::class)
class AppsActivity : AppCompatActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val launcher = Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER)
        val apps = packageManager.queryIntentActivities(launcher, 0).mapNotNull { result ->
            val info = result.activityInfo?.applicationInfo ?: return@mapNotNull null
            if (info.packageName == packageName) return@mapNotNull null
            val label = runCatching { packageManager.getApplicationLabel(info).toString() }.getOrDefault(info.packageName)
            val version = runCatching {
                @Suppress("DEPRECATION")
                packageManager.getPackageInfo(info.packageName, 0).versionName ?: "—"
            }.getOrDefault("—")
            Triple(info.packageName, label, version)
        }.distinctBy { it.first }.sortedBy { it.second.lowercase(Locale.getDefault()) }

        setContent {
            MaterialTheme {
                var query by remember { mutableStateOf("") }
                val visible = apps.filter { it.second.contains(query, true) || it.first.contains(query, true) }
                Scaffold(topBar = { TopAppBar(title = { Text("Installed apps") }) }) { padding ->
                    Column(Modifier.fillMaxSize().padding(padding).padding(16.dp)) {
                        OutlinedTextField(query, { query = it }, Modifier.fillMaxWidth(), label = { Text("Search apps") })
                        Spacer(Modifier.height(10.dp))
                        Text("${visible.size} apps")
                        LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            items(visible, key = { it.first }) { app ->
                                Card {
                                    Row(Modifier.fillMaxWidth().padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
                                        Icon(Icons.Default.Android, null, tint = Color(0xFF008F87))
                                        Spacer(Modifier.width(10.dp))
                                        Column(Modifier.weight(1f)) {
                                            Text(app.second, style = MaterialTheme.typography.titleMedium)
                                            Text(app.first, style = MaterialTheme.typography.bodySmall)
                                            Text("Version ${app.third}", style = MaterialTheme.typography.labelSmall)
                                        }
                                        IconButton(onClick = {
                                            openSystemSettings(this@AppsActivity, Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.parse("package:${app.first}"))
                                        }) { Icon(Icons.Default.Settings, "App settings") }
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
class SpeedBoostActivity : AppCompatActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val manager = getSystemService(Context.ACTIVITY_SERVICE) as ActivityManager
        val memory = ActivityManager.MemoryInfo().also { manager.getMemoryInfo(it) }
        val freeMb = memory.availMem / (1024 * 1024)
        val totalMb = memory.totalMem / (1024 * 1024)
        setContent {
            MaterialTheme {
                Scaffold(topBar = { TopAppBar(title = { Text("Speed Boost") }) }) { padding ->
                    Column(Modifier.fillMaxSize().padding(padding).padding(18.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
                        Card(colors = CardDefaults.cardColors(containerColor = Color(0xFF5356D8).copy(alpha = .12f))) {
                            Column(Modifier.fillMaxWidth().padding(18.dp)) {
                                Text("Memory overview", style = MaterialTheme.typography.titleLarge, color = Color(0xFF5356D8))
                                Text("Available RAM: $freeMb MB")
                                Text("Total RAM: $totalMb MB")
                            }
                        }
                        Text("Android manages background processes automatically. Ordinary apps cannot force-stop other apps. Use the safe system controls below.")
                        Button(onClick = { openSystemSettings(this@SpeedBoostActivity, Settings.ACTION_APPLICATION_SETTINGS) }, Modifier.fillMaxWidth()) { Text("Manage apps in Android settings") }
                        OutlinedButton(onClick = { openSystemSettings(this@SpeedBoostActivity, Settings.ACTION_MANAGE_APPLICATIONS_SETTINGS) }, Modifier.fillMaxWidth()) { Text("Review background apps") }
                        OutlinedButton(onClick = { openSystemSettings(this@SpeedBoostActivity, android.os.storage.StorageManager.ACTION_MANAGE_STORAGE) }, Modifier.fillMaxWidth()) { Text("Open storage manager") }
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
class ResidualsActivity : AppCompatActivity() {
    private data class Candidate(val file: DocumentFile, val reason: String)
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            var candidates by remember { mutableStateOf(emptyList<Candidate>()) }
            var status by remember { mutableStateOf("Choose a folder you own, such as Downloads. Protected app folders are not accessible.") }
            val picker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocumentTree()) { uri ->
                if (uri != null) runCatching {
                    contentResolver.takePersistableUriPermission(uri, Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION)
                    val root = DocumentFile.fromTreeUri(this, uri)
                    val found = mutableListOf<Candidate>()
                    fun walk(dir: DocumentFile, depth: Int) {
                        if (depth > 6) return
                        dir.listFiles().forEach { child ->
                            if (child.isDirectory) {
                                if (child.listFiles().isEmpty()) found += Candidate(child, "Empty folder") else walk(child, depth + 1)
                            } else {
                                val name = child.name.orEmpty().lowercase(Locale.ROOT)
                                if (listOf(".tmp", ".temp", ".bak", ".log", ".partial").any { name.endsWith(it) }) found += Candidate(child, "Temporary-looking file")
                            }
                        }
                    }
                    if (root != null) walk(root, 0)
                    candidates = found
                    status = "Found ${found.size} possible items. Review carefully before deleting."
                }.onFailure { status = "Folder scan failed. Choose another accessible folder." }
            }
            MaterialTheme {
                Scaffold(topBar = { TopAppBar(title = { Text("Residual files") }) }) { padding ->
                    Column(Modifier.fillMaxSize().padding(padding).padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                        Text(status)
                        Button(onClick = { picker.launch(null) }, Modifier.fillMaxWidth()) { Text("Choose folder and scan") }
                        LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            items(candidates, key = { it.file.uri.toString() }) { item ->
                                Card {
                                    Column(Modifier.fillMaxWidth().padding(12.dp)) {
                                        Text(item.file.name ?: "Unnamed", style = MaterialTheme.typography.titleMedium)
                                        Text(item.reason)
                                        TextButton(onClick = {
                                            val deleted = runCatching { item.file.delete() }.getOrDefault(false)
                                            status = if (deleted) "Item deleted." else "Android did not allow deletion."
                                            candidates = candidates.filterNot { it.file.uri == item.file.uri }
                                        }) { Text("Delete item") }
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
class CacheMonitorActivity : AppCompatActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        CacheReminderWorker.schedule(this)
        val freeMb = StatFs(Environment.getDataDirectory().path).availableBytes / (1024 * 1024)
        setContent {
            MaterialTheme {
                Scaffold(topBar = { TopAppBar(title = { Text("Storage & cache monitor") }) }) { padding ->
                    Column(Modifier.fillMaxSize().padding(padding).padding(18.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
                        Card(colors = CardDefaults.cardColors(containerColor = Color(0xFFE97935).copy(alpha = .12f))) {
                            Column(Modifier.fillMaxWidth().padding(18.dp)) {
                                Text("Available device storage", style = MaterialTheme.typography.titleLarge, color = Color(0xFFE97935))
                                Text("$freeMb MB available", style = MaterialTheme.typography.headlineSmall)
                                Text("A daily reminder checks whether available storage is 200 MB or less.")
                            }
                        }
                        Text("Android does not expose the combined cache size of all other apps. This reminder monitors free storage and opens system cleanup options; it does not silently delete other apps' caches.")
                        Button(onClick = { openSystemSettings(this@CacheMonitorActivity, android.os.storage.StorageManager.ACTION_MANAGE_STORAGE) }, Modifier.fillMaxWidth()) { Text("Open Android storage manager") }
                        OutlinedButton(onClick = { CacheReminderWorker.schedule(this@CacheMonitorActivity); finish() }, Modifier.fillMaxWidth()) { Text("Enable daily reminder and return") }
                    }
                }
            }
        }
    }
}
