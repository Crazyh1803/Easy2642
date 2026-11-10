package com.easy2642.app.ui

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.documentfile.provider.DocumentFile
import com.easy2642.app.BuildConfig
import com.easy2642.app.Easy2642App
import com.easy2642.app.R
import java.time.LocalDate
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

fun folderLabel(app: Easy2642App, uri: Uri?): String? =
    uri?.let { runCatching { DocumentFile.fromTreeUri(app, it)?.name }.getOrNull() ?: "Selected folder" }

@Composable
fun SettingsScreen(app: Easy2642App, onOpenHarness: (() -> Unit)?) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val snackbar = remember { SnackbarHostState() }
    var folder by remember { mutableStateOf(app.settings.claimsFolder) }
    var confirmBackup by remember { mutableStateOf(false) }
    var confirmImport by remember { mutableStateOf<Uri?>(null) }
    var confirmClear by remember { mutableStateOf(false) }

    val pickFolder = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocumentTree()) { uri ->
        if (uri != null) {
            app.settings.claimsFolder = uri
            folder = uri
        }
    }
    val createBackup = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/json")) { uri ->
        if (uri != null) scope.launch {
            val result = runCatching {
                withContext(Dispatchers.IO) {
                    val text = app.repository.exportBackup()
                    context.contentResolver.openOutputStream(uri, "wt")!!.use { it.write(text.toByteArray()) }
                }
            }
            snackbar.showSnackbar(if (result.isSuccess) "Backup saved" else "Backup failed: could not write to that location")
        }
    }
    val openBackup = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri -> confirmImport = uri }

    Scaffold(
        topBar = { TopAppBar(title = { Text("Settings") }) },
        snackbarHost = { SnackbarHost(snackbar) },
    ) { padding ->
        Column(Modifier.padding(padding).verticalScroll(rememberScrollState()).padding(horizontal = 16.dp)) {
            SectionTitle("Claims folder")
            Text(
                folderLabel(app, folder)?.let { "Packets can be saved straight to \"$it\"." }
                    ?: "Optional. Pick a folder (device, SD card or a cloud drive app) to save packets with one tap.",
                style = MaterialTheme.typography.bodyMedium,
            )
            OutlinedButton(onClick = { pickFolder.launch(folder) }, modifier = Modifier.padding(top = 8.dp)) {
                Text(if (folder == null) "Choose claims folder" else "Change claims folder")
            }
            if (folder != null) {
                TextButton(onClick = { app.settings.claimsFolder = null; folder = null }) { Text("Stop using this folder") }
            }

            SectionTitle("Your data")
            OutlinedButton(onClick = { confirmBackup = true }) { Text("Export Backup") }
            OutlinedButton(onClick = { openBackup.launch(arrayOf("application/json", "text/plain", "application/octet-stream")) }, modifier = Modifier.padding(top = 8.dp)) {
                Text("Restore from backup")
            }
            OutlinedButton(
                onClick = { confirmClear = true },
                colors = ButtonDefaults.outlinedButtonColors(contentColor = MaterialTheme.colorScheme.error),
                modifier = Modifier.padding(top = 8.dp),
            ) { Text("Clear All Local Data") }

            SectionTitle("Privacy")
            Text(stringResource(R.string.privacy_summary), style = MaterialTheme.typography.bodyMedium)
            SectionTitle("Disclaimer")
            Text(stringResource(R.string.disclaimer), style = MaterialTheme.typography.bodyMedium)

            SectionTitle("About")
            Text("2642 Easy ${BuildConfig.VERSION_NAME} · Prepares DD Form 2642 (SEP 2024 edition)", style = MaterialTheme.typography.bodyMedium)
            if (onOpenHarness != null) {
                TextButton(onClick = onOpenHarness) { Text("Form engine test harness (debug builds only)") }
            }
            Spacer(Modifier.height(24.dp))
        }
    }

    if (confirmBackup) {
        AlertDialog(
            onDismissRequest = { confirmBackup = false },
            title = { Text("Export backup") },
            text = { Text("The backup file contains your family profiles and claim list as readable, unencrypted text, including the sponsor's SSN or DBN. Save it only somewhere you trust.") },
            confirmButton = {
                TextButton(onClick = {
                    confirmBackup = false
                    createBackup.launch("2642Easy_Backup_${LocalDate.now().toString().replace("-", "")}.json")
                }) { Text("Choose location") }
            },
            dismissButton = { TextButton(onClick = { confirmBackup = false }) { Text("Cancel") } },
        )
    }
    confirmImport?.let { uri ->
        AlertDialog(
            onDismissRequest = { confirmImport = null },
            title = { Text("Restore from backup?") },
            text = { Text("This replaces all profiles and claims currently in the app with the contents of the backup file.") },
            confirmButton = {
                TextButton(onClick = {
                    confirmImport = null
                    scope.launch {
                        val result = runCatching {
                            withContext(Dispatchers.IO) {
                                val text = context.contentResolver.openInputStream(uri)!!.use { it.readBytes().decodeToString() }
                                app.repository.importBackup(text)
                            }
                        }
                        snackbar.showSnackbar(if (result.isSuccess) "Backup restored" else "That file is not a valid 2642 Easy backup. Nothing was changed.")
                    }
                }) { Text("Replace my data") }
            },
            dismissButton = { TextButton(onClick = { confirmImport = null }) { Text("Cancel") } },
        )
    }
    if (confirmClear) {
        AlertDialog(
            onDismissRequest = { confirmClear = false },
            title = { Text("Clear all local data?") },
            text = { Text("This permanently erases every profile, every claim in the tracker, the encryption key and all settings. PDFs you already saved elsewhere are not touched. This cannot be undone.") },
            confirmButton = { TextButton(onClick = { app.clearAllLocalData() }) { Text("Erase everything", color = MaterialTheme.colorScheme.error) } },
            dismissButton = { TextButton(onClick = { confirmClear = false }) { Text("Cancel") } },
        )
    }
}
