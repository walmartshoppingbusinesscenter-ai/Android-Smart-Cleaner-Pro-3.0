package com.smartcleanerpro.app

import android.Manifest
import android.app.Activity
import android.content.ContentUris
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.Environment
import android.os.StatFs
import android.os.storage.StorageManager
import android.provider.MediaStore
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.result.IntentSenderRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.app.AppCompatDelegate
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.core.os.LocaleListCompat
import androidx.documentfile.provider.DocumentFile
import java.util.Locale

class MainActivity : AppCompatActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val prefs = getSharedPreferences("smart_cleaner_prefs", MODE_PRIVATE)
        val dark = prefs.getBoolean("dark_theme", false)
        val language = prefs.getString("language", "system") ?: "system"
        AppCompatDelegate.setDefaultNightMode(if (dark) AppCompatDelegate.MODE_NIGHT_YES else AppCompatDelegate.MODE_NIGHT_NO)
        if (language != "system") AppCompatDelegate.setApplicationLocales(LocaleListCompat.forLanguageTags(language))
        setContent {
            var isDark by remember { mutableStateOf(dark) }
            var selectedLanguage by remember { mutableStateOf(language) }
            MaterialTheme(colorScheme = if (isDark) darkColorScheme() else lightColorScheme()) {
                Dashboard(this, isDark, { value ->
                    isDark = value
                    prefs.edit().putBoolean("dark_theme", value).apply()
                    AppCompatDelegate.setDefaultNightMode(if (value) AppCompatDelegate.MODE_NIGHT_YES else AppCompatDelegate.MODE_NIGHT_NO)
                }, selectedLanguage, { value ->
                    selectedLanguage = value
                    prefs.edit().putString("language", value).apply()
                    AppCompatDelegate.setApplicationLocales(if (value == "system") LocaleListCompat.getEmptyLocaleList() else LocaleListCompat.forLanguageTags(value))
                })
            }
        }
    }
}

private data class StorageInfo(val total: Long, val free: Long) {
    val used get() = (total - free).coerceAtLeast(0)
    val fraction get() = if (total > 0) used.toFloat() / total.toFloat() else 0f
}
private data class MediaCandidate(val uri: Uri, val name: String, val size: Long, val video: Boolean)
private data class JunkCandidate(val file: DocumentFile, val reason: String, val size: Long, val directory: Boolean) {
    val key: String get() = file.uri.toString()
}
private fun storageInfo(): StorageInfo = try {
    val s = StatFs(Environment.getDataDirectory().path)
    StorageInfo(s.totalBytes, s.availableBytes)
} catch (_: Exception) { StorageInfo(0, 0) }
private fun gb(value: Long) = String.format(Locale.getDefault(), "%.1f", value / 1073741824.0)
private fun mediaPermissions() = when {
    Build.VERSION.SDK_INT >= 34 -> arrayOf(Manifest.permission.READ_MEDIA_IMAGES, Manifest.permission.READ_MEDIA_VIDEO, Manifest.permission.READ_MEDIA_VISUAL_USER_SELECTED)
    Build.VERSION.SDK_INT >= 33 -> arrayOf(Manifest.permission.READ_MEDIA_IMAGES, Manifest.permission.READ_MEDIA_VIDEO)
    else -> arrayOf(Manifest.permission.READ_EXTERNAL_STORAGE)
}

private fun scanLargeMedia(activity: Activity): List<MediaCandidate> {
    val found = mutableListOf<MediaCandidate>()
    val sources = listOf(MediaStore.Images.Media.EXTERNAL_CONTENT_URI to false, MediaStore.Video.Media.EXTERNAL_CONTENT_URI to true)
    for ((collection, video) in sources) {
        val projection = arrayOf(MediaStore.MediaColumns._ID, MediaStore.MediaColumns.DISPLAY_NAME, MediaStore.MediaColumns.SIZE)
        runCatching {
            activity.contentResolver.query(collection, projection, null, null, "${MediaStore.MediaColumns.SIZE} DESC")?.use { c ->
                val idCol = c.getColumnIndexOrThrow(MediaStore.MediaColumns._ID)
                val nameCol = c.getColumnIndexOrThrow(MediaStore.MediaColumns.DISPLAY_NAME)
                val sizeCol = c.getColumnIndexOrThrow(MediaStore.MediaColumns.SIZE)
                while (c.moveToNext()) {
                    val size = c.getLong(sizeCol)
                    if (size >= 20L * 1024 * 1024) {
                        found += MediaCandidate(ContentUris.withAppendedId(collection, c.getLong(idCol)), c.getString(nameCol) ?: "Unknown", size, video)
                    }
                }
            }
        }
    }
    return found.sortedByDescending { it.size }
}

private fun scanChosenFolder(root: DocumentFile): List<JunkCandidate> {
    val found = mutableListOf<JunkCandidate>()
    val suspiciousExtensions = setOf("tmp", "temp", "log", "bak", "old", "cache", "part", "dmp")
    fun visit(folder: DocumentFile, depth: Int) {
        if (depth > 7) return
        val children = runCatching { folder.listFiles() }.getOrNull() ?: return
        if (children.isEmpty() && folder.uri != root.uri) {
            found += JunkCandidate(folder, "Empty folder", 0L, true)
            return
        }
        children.forEach { child ->
            if (child.isDirectory) {
                visit(child, depth + 1)
            } else {
                val name = child.name.orEmpty()
                val ext = name.substringAfterLast('.', "").lowercase(Locale.ROOT)
                val lower = name.lowercase(Locale.ROOT)
                val reason = when {
                    ext in suspiciousExtensions -> "Temporary-looking file extension"
                    lower.startsWith("~") || lower.endsWith(".partial") -> "Temporary/incomplete file name"
                    else -> null
                }
                if (reason != null) found += JunkCandidate(child, reason, child.length(), false)
            }
        }
    }
    visit(root, 0)
    return found.distinctBy { it.key }.sortedByDescending { it.size }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun Dashboard(
    activity: AppCompatActivity,
    dark: Boolean,
    onDark: (Boolean) -> Unit,
    language: String,
    onLanguage: (String) -> Unit
) {
    val storage = remember { storageInfo() }
    var mediaFiles by remember { mutableStateOf(emptyList<MediaCandidate>()) }
    var selectedMedia by remember { mutableStateOf(emptySet<String>()) }
    var junkFiles by remember { mutableStateOf(emptyList<JunkCandidate>()) }
    var selectedJunk by remember { mutableStateOf(emptySet<String>()) }
    var message by remember { mutableStateOf("") }
    var busy by remember { mutableStateOf(false) }

    val permissionRequest = rememberLauncherForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) { grants ->
        if (grants.values.any { it }) {
            busy = true
            mediaFiles = runCatching { scanLargeMedia(activity) }.getOrElse { emptyList() }
            selectedMedia = emptySet()
            busy = false
            message = activity.getString(R.string.scan_complete, mediaFiles.size)
        } else message = activity.getString(R.string.permission_needed)
    }
    val deleteRequest = rememberLauncherForActivityResult(ActivityResultContracts.StartIntentSenderForResult()) { result ->
        message = if (result.resultCode == Activity.RESULT_OK) activity.getString(R.string.delete_done) else activity.getString(R.string.delete_cancelled)
        if (result.resultCode == Activity.RESULT_OK) {
            mediaFiles = mediaFiles.filterNot { it.uri.toString() in selectedMedia }
            selectedMedia = emptySet()
        }
    }
    val folderPicker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocumentTree()) { uri ->
        if (uri != null) {
            runCatching {
                activity.contentResolver.takePersistableUriPermission(uri, Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION)
                val root = DocumentFile.fromTreeUri(activity, uri)
                junkFiles = if (root != null) scanChosenFolder(root) else emptyList()
                selectedJunk = emptySet()
                message = activity.getString(R.string.junk_scan_complete, junkFiles.size)
            }.onFailure { message = activity.getString(R.string.folder_scan_error) }
        }
    }

    Scaffold(topBar = {
        TopAppBar(title = {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Default.Shield, null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(32.dp))
                Spacer(Modifier.width(10.dp))
                Column {
                    Text(stringResource(R.string.app_title), fontWeight = FontWeight.Bold)
                    Text(stringResource(R.string.subtitle), style = MaterialTheme.typography.labelMedium)
                }
            }
        })
    }) { padding ->
        LazyColumn(Modifier.fillMaxSize().padding(padding), contentPadding = PaddingValues(16.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
            item {
                Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer)) {
                    Column(Modifier.fillMaxWidth().padding(18.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                        Text(stringResource(R.string.storage_title), style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Box(Modifier.size(112.dp), contentAlignment = Alignment.Center) {
                                CircularProgressIndicator(progress = { storage.fraction.coerceIn(0f, 1f) }, modifier = Modifier.fillMaxSize(), strokeWidth = 10.dp)
                                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                    Text(gb(storage.used), style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
                                    Text(stringResource(R.string.used))
                                }
                            }
                            Spacer(Modifier.width(18.dp))
                            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                Text("${stringResource(R.string.free)}: ${gb(storage.free)} GB")
                                Text("${stringResource(R.string.total)}: ${gb(storage.total)} GB")
                            }
                        }
                    }
                }
            }
            item {
                Card {
                    Column(Modifier.fillMaxWidth().padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        Text(stringResource(R.string.media_scan_title), style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
                        Text(stringResource(R.string.scan_explanation), color = MaterialTheme.colorScheme.onSurfaceVariant)
                        Button(onClick = {
                            val missing = mediaPermissions().filter { ContextCompat.checkSelfPermission(activity, it) != PackageManager.PERMISSION_GRANTED }.toTypedArray()
                            if (missing.isEmpty()) {
                                busy = true
                                mediaFiles = runCatching { scanLargeMedia(activity) }.getOrElse { emptyList() }
                                selectedMedia = emptySet()
                                busy = false
                                message = activity.getString(R.string.scan_complete, mediaFiles.size)
                            } else permissionRequest.launch(missing)
                        }, modifier = Modifier.fillMaxWidth(), enabled = !busy) {
                            Icon(Icons.Default.Search, null)
                            Spacer(Modifier.width(8.dp))
                            Text(if (busy) stringResource(R.string.scanning) else stringResource(R.string.scan))
                        }
                    }
                }
            }
            if (mediaFiles.isNotEmpty()) {
                item {
                    Text(stringResource(R.string.large_media_title), style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
                    Text(stringResource(R.string.select_delete_hint), style = MaterialTheme.typography.bodySmall)
                }
                items(mediaFiles, key = { it.uri.toString() }) { file ->
                    Card {
                        Row(Modifier.fillMaxWidth().padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
                            Checkbox(checked = file.uri.toString() in selectedMedia, onCheckedChange = { checked ->
                                selectedMedia = if (checked) selectedMedia + file.uri.toString() else selectedMedia - file.uri.toString()
                            })
                            Icon(if (file.video) Icons.Default.VideoFile else Icons.Default.Image, null, tint = MaterialTheme.colorScheme.primary)
                            Spacer(Modifier.width(10.dp))
                            Column(Modifier.weight(1f)) {
                                Text(file.name, fontWeight = FontWeight.SemiBold)
                                Text("${if (file.video) "Video" else "Image"} • ${file.size / (1024 * 1024)} MB", style = MaterialTheme.typography.bodySmall)
                            }
                        }
                    }
                }
                item {
                    OutlinedButton(onClick = {
                        val chosen = mediaFiles.filter { it.uri.toString() in selectedMedia }
                        if (chosen.isEmpty()) message = activity.getString(R.string.select_at_least_one)
                        else if (Build.VERSION.SDK_INT >= 30) {
                            val pending = MediaStore.createDeleteRequest(activity.contentResolver, chosen.map { it.uri })
                            deleteRequest.launch(IntentSenderRequest.Builder(pending.intentSender).build())
                        } else message = activity.getString(R.string.delete_android_older)
                    }, modifier = Modifier.fillMaxWidth()) {
                        Icon(Icons.Default.DeleteOutline, null)
                        Spacer(Modifier.width(8.dp))
                        Text(stringResource(R.string.delete_selected_media, selectedMedia.size))
                    }
                }
            }
            item {
                Card {
                    Column(Modifier.fillMaxWidth().padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        Text(stringResource(R.string.junk_title), style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
                        Text(stringResource(R.string.junk_explanation), color = MaterialTheme.colorScheme.onSurfaceVariant)
                        Button(onClick = { folderPicker.launch(null) }, modifier = Modifier.fillMaxWidth()) {
                            Icon(Icons.Default.FolderOpen, null)
                            Spacer(Modifier.width(8.dp))
                            Text(stringResource(R.string.choose_folder_scan))
                        }
                    }
                }
            }
            if (junkFiles.isNotEmpty()) {
                item {
                    Text(stringResource(R.string.junk_results), style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
                    Text(stringResource(R.string.junk_select_hint), style = MaterialTheme.typography.bodySmall)
                }
                items(junkFiles, key = { it.key }) { candidate ->
                    Card {
                        Row(Modifier.fillMaxWidth().padding(10.dp), verticalAlignment = Alignment.CenterVertically) {
                            Checkbox(checked = candidate.key in selectedJunk, onCheckedChange = { checked ->
                                selectedJunk = if (checked) selectedJunk + candidate.key else selectedJunk - candidate.key
                            })
                            Icon(if (candidate.directory) Icons.Default.Folder else Icons.Default.InsertDriveFile, null)
                            Spacer(Modifier.width(10.dp))
                            Column(Modifier.weight(1f)) {
                                Text(candidate.file.name ?: "Unknown", fontWeight = FontWeight.SemiBold)
                                Text("${candidate.reason} • ${candidate.size / 1024} KB", style = MaterialTheme.typography.bodySmall)
                            }
                        }
                    }
                }
                item {
                    OutlinedButton(onClick = {
                        val targets = junkFiles.filter { it.key in selectedJunk }
                        if (targets.isEmpty()) message = activity.getString(R.string.select_at_least_one)
                        else {
                            val deleted = targets.count { runCatching { it.file.delete() }.getOrDefault(false) }
                            junkFiles = junkFiles.filterNot { it.key in selectedJunk }
                            selectedJunk = emptySet()
                            message = activity.getString(R.string.junk_deleted, deleted)
                        }
                    }, modifier = Modifier.fillMaxWidth()) {
                        Icon(Icons.Default.DeleteOutline, null)
                        Spacer(Modifier.width(8.dp))
                        Text(stringResource(R.string.delete_selected_junk, selectedJunk.size))
                    }
                }
            }
            item {
                Card {
                    Column(Modifier.fillMaxWidth().padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        Text(stringResource(R.string.cache_title), style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
                        Text(stringResource(R.string.cache_explanation), color = MaterialTheme.colorScheme.onSurfaceVariant)
                        OutlinedButton(onClick = {
                            runCatching { activity.startActivity(Intent(StorageManager.ACTION_MANAGE_STORAGE)) }
                                .onFailure { message = activity.getString(R.string.system_storage_unavailable) }
                        }, modifier = Modifier.fillMaxWidth()) {
                            Icon(Icons.Default.Settings, null)
                            Spacer(Modifier.width(8.dp))
                            Text(stringResource(R.string.open_system_storage))
                        }
                    }
                }
            }
            item {
                Card {
                    Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                        Text(stringResource(R.string.settings), style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Default.DarkMode, null)
                            Spacer(Modifier.width(12.dp))
                            Text(stringResource(R.string.dark_mode), Modifier.weight(1f))
                            Switch(dark, onDark)
                        }
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Default.Language, null)
                            Spacer(Modifier.width(12.dp))
                            Text(stringResource(R.string.language), Modifier.weight(1f))
                            var expanded by remember { mutableStateOf(false) }
                            Box {
                                TextButton(onClick = { expanded = true }) { Text(when (language) { "fa" -> "فارسی"; "en" -> "English"; else -> "System" }) }
                                DropdownMenu(expanded, { expanded = false }) {
                                    DropdownMenuItem(text = { Text("System") }, onClick = { onLanguage("system"); expanded = false })
                                    DropdownMenuItem(text = { Text("English") }, onClick = { onLanguage("en"); expanded = false })
                                    DropdownMenuItem(text = { Text("فارسی") }, onClick = { onLanguage("fa"); expanded = false })
                                }
                            }
                        }
                    }
                }
            }
            if (message.isNotBlank()) item { Text(message, color = MaterialTheme.colorScheme.primary) }
            item { Text(stringResource(R.string.limits_note), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant) }
        }
    }
}
