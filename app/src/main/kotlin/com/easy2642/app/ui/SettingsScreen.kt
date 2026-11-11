package com.easy2642.app.ui

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.widget.Toast
import com.easy2642.core.OutputMode
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.selection.selectable
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.BugReport
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.filled.DeleteForever
import androidx.compose.material.icons.filled.Description
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Upload
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.RadioButton
import androidx.compose.ui.Alignment
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.Role
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

object Links {
    const val AUTHOR = "https://appsbydan.com/"
    const val TIP = "https://buymeacoffee.com/AppsbyDan"
    const val PRIVACY = "https://github.com/Crazyh1803/Easy2642/blob/main/PRIVACY.md"
}

/** Hands a link to the user's browser. The app itself has no network access. */
fun openUrl(context: Context, url: String) {
    runCatching { context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url))) }.onFailure {
        Toast.makeText(context, "No browser available to open $url", Toast.LENGTH_LONG).show()
    }
}

fun folderLabel(app: Easy2642App, uri: Uri?): String? =
    uri?.let { runCatching { DocumentFile.fromTreeUri(app, it)?.name }.getOrNull() ?: "Selected folder" }

@Composable
private fun SettingsGroup(title: String) {
    Text(
        title,
        style = MaterialTheme.typography.titleSmall,
        color = MaterialTheme.colorScheme.primary,
        modifier = Modifier.padding(start = 16.dp, end = 16.dp, top = 20.dp, bottom = 4.dp),
    )
}

/** One tappable settings line: icon, title, a short line of detail, optional trailing control. */
@Composable
private fun SettingsRow(
    icon: ImageVector,
    title: String,
    detail: String,
    onClick: () -> Unit,
    danger: Boolean = false,
    trailing: (@Composable () -> Unit)? = null,
) {
    val tint = if (danger) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurfaceVariant
    ListItem(
        headlineContent = { Text(title, color = if (danger) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurface) },
        supportingContent = { Text(detail) },
        leadingContent = { Icon(icon, contentDescription = null, tint = tint) },
        trailingContent = trailing,
        modifier = Modifier.clickable(role = Role.Button, onClick = onClick),
    )
}

@Composable
fun SettingsScreen(app: Easy2642App, onOpenHarness: (() -> Unit)?) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val snackbar = remember { SnackbarHostState() }
    var folder by remember { mutableStateOf(app.settings.claimsFolder) }
    var outputMode by remember { mutableStateOf(app.settings.outputMode) }
    var confirmBackup by remember { mutableStateOf(false) }
    var confirmImport by remember { mutableStateOf<Uri?>(null) }
    var confirmClear by remember { mutableStateOf(false) }
    var pickOutput by remember { mutableStateOf(false) }
    var info by remember { mutableStateOf<Pair<String, String>?>(null) }
    val privacyText = stringResource(R.string.privacy_summary)
    val disclaimerText = stringResource(R.string.disclaimer)

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
        Column(Modifier.padding(padding).verticalScroll(rememberScrollState())) {
            SettingsGroup("Saving claims")
            SettingsRow(
                Icons.Filled.Description, "Default output",
                if (outputMode == OutputMode.COMBINED) "One combined PDF" else "Separate form, bill and receipt files",
                onClick = { pickOutput = true },
            )
            SettingsRow(
                Icons.Filled.Folder, "Claims folder",
                folderLabel(app, folder) ?: "Not set. Choose one to save with a single tap",
                onClick = { pickFolder.launch(folder) },
                trailing = if (folder == null) null else ({
                    IconButton(onClick = { app.settings.claimsFolder = null; folder = null }) {
                        Icon(Icons.Filled.Clear, contentDescription = "Stop using this folder")
                    }
                }),
            )

            SettingsGroup("Your data")
            SettingsRow(Icons.Filled.Upload, "Export backup", "Save profiles and claims to a file", onClick = { confirmBackup = true })
            SettingsRow(
                Icons.Filled.Download, "Restore from backup", "Replace everything with a backup file",
                onClick = { openBackup.launch(arrayOf("application/json", "text/plain", "application/octet-stream")) },
            )
            SettingsRow(
                Icons.Filled.DeleteForever, "Clear all local data", "Erase profiles, claims, signatures and settings",
                onClick = { confirmClear = true }, danger = true,
            )

            SettingsGroup("About")
            SettingsRow(Icons.Filled.Lock, "Privacy", "Nothing leaves this device unless you send it", onClick = { info = "Privacy" to privacyText })
            SettingsRow(Icons.Filled.Info, "Disclaimer", "Independent tool, not affiliated with TRICARE, DHA or DoD", onClick = { info = "Disclaimer" to disclaimerText })
            SettingsRow(Icons.Filled.Person, "About the author", "appsbydan.com", onClick = { openUrl(context, Links.AUTHOR) })
            SettingsRow(Icons.Filled.Favorite, "Tip the developer", "Optional. Opens in your browser", onClick = { openUrl(context, Links.TIP) })
            if (onOpenHarness != null) {
                SettingsRow(Icons.Filled.BugReport, "Form engine test harness", "Debug builds only", onClick = onOpenHarness)
            }
            Text(
                "2642 Easy ${BuildConfig.VERSION_NAME} · DD Form 2642, SEP 2024 edition",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(16.dp),
            )
        }
    }

    if (pickOutput) {
        AlertDialog(
            onDismissRequest = { pickOutput = false },
            title = { Text("Default output") },
            text = {
                Column {
                    OutputMode.entries.forEach { m ->
                        Row(
                            Modifier.fillMaxWidth().selectable(outputMode == m, role = Role.RadioButton) {
                                app.settings.outputMode = m
                                outputMode = m
                                pickOutput = false
                            }.padding(vertical = 8.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            RadioButton(selected = outputMode == m, onClick = null)
                            Column(Modifier.padding(start = 12.dp)) {
                                Text(m.label)
                                Text(
                                    if (m == OutputMode.COMBINED) "Form, bill and receipts in one file"
                                    else "DD 2642, bill and receipt / proof of payment as three files",
                                    style = MaterialTheme.typography.bodySmall,
                                )
                            }
                        }
                    }
                    Text("You can still switch for an individual claim on its review step.", style = MaterialTheme.typography.bodySmall, modifier = Modifier.padding(top = 8.dp))
                }
            },
            confirmButton = { TextButton(onClick = { pickOutput = false }) { Text("Close") } },
        )
    }
    info?.let { (title, body) ->
        AlertDialog(
            onDismissRequest = { info = null },
            title = { Text(title) },
            text = { Text(body, Modifier.verticalScroll(rememberScrollState())) },
            confirmButton = { TextButton(onClick = { info = null }) { Text("Close") } },
            dismissButton = if (title != "Privacy") null else ({
                TextButton(onClick = { openUrl(context, Links.PRIVACY) }) { Text("Read the full policy") }
            }),
        )
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
