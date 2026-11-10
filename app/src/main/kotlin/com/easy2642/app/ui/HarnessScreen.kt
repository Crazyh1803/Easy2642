package com.easy2642.app.ui

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.unit.dp
import com.easy2642.app.Easy2642App
import com.easy2642.app.SampleData
import com.easy2642.app.WorkFiles
import com.easy2642.app.pdf.FieldInfo
import com.easy2642.core.CareType
import com.easy2642.core.Relationship
import com.easy2642.core.Sex
import java.io.File
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/** Developer harness for Gate 1: edit a payload, fill the template, inspect the result and the field audit. */
@Composable
fun HarnessScreen(app: Easy2642App, onClose: () -> Unit) {
    val scope = rememberCoroutineScope()
    var form by remember { mutableStateOf(SampleData.form) }
    var includeInstructions by remember { mutableStateOf(false) }
    var output by remember { mutableStateOf<File?>(null) }
    var version by remember { mutableIntStateOf(0) }
    var status by remember { mutableStateOf("Synthetic sample data loaded. Nothing here is saved.") }
    var audit by remember { mutableStateOf<List<FieldInfo>?>(null) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Form engine harness") },
                navigationIcon = { IconButton(onClick = onClose) { Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back") } },
            )
        },
    ) { padding ->
        Column(Modifier.padding(padding).fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = 16.dp)) {
            NameFields(form.patientName) { form = form.copy(patientName = it) }
            ChoiceChips("4 Relationship", Relationship.entries, form.relationship, { it.label }) { form = form.copy(relationship = it) }
            ChoiceChips("6 Sex", Sex.entries, form.sex, { it.label }) { form = form.copy(sex = it) }
            ChoiceChips("8b Care", CareType.entries, form.careType, { it.label }) { form = form.copy(careType = it) }
            YesNo("7 Accident related", form.accidentRelated) { form = form.copy(accidentRelated = it) }
            YesNo("11 Other insurance", form.hasOtherInsurance) { form = form.copy(hasOtherInsurance = it) }
            YesNo("13 Paid provider", form.paidProvider) { form = form.copy(paidProvider = it) }
            Field("8a Description", form.description, { form = form.copy(description = it) }, singleLine = false, minLines = 3, capitalization = KeyboardCapitalization.Sentences, supporting = "${form.description.length} characters")
            Row(verticalAlignment = Alignment.CenterVertically) {
                TextButton(onClick = { form = form.copy(description = "Wrap test sentence number one. ".repeat(30)) }) { Text("Long text") }
                TextButton(onClick = { form = SampleData.form }) { Text("Reset") }
                TextButton(onClick = { form = com.easy2642.core.ClaimForm() }) { Text("Blank") }
            }
            Row(verticalAlignment = Alignment.CenterVertically) {
                Switch(includeInstructions, { includeInstructions = it })
                Text("Keep instruction page", Modifier.padding(start = 12.dp))
            }
            Row {
                Button(onClick = {
                    scope.launch {
                        val file = File(WorkFiles.packets(app), "harness.pdf")
                        val started = System.nanoTime()
                        val result = runCatching { withContext(Dispatchers.IO) { app.formService.fill(form, file, includeInstructions) } }
                        val ms = (System.nanoTime() - started) / 1_000_000
                        result.onSuccess {
                            output = file
                            version++
                            status = "Filled ${it.pageCount} page(s) in $ms ms, ${file.length() / 1024} KB. " +
                                if (it.warnings.isEmpty()) "No warnings." else it.warnings.joinToString(" ")
                        }.onFailure { status = "FAILED: $it" }
                    }
                }) { Text("Fill & preview") }
                TextButton(onClick = {
                    scope.launch {
                        val (fields, drift) = withContext(Dispatchers.IO) { app.formService.audit() to app.formService.auditDrift() }
                        audit = fields
                        status = "Audit: ${fields.size} fields. Unmapped in template: ${drift.first.size}. Missing from template: ${drift.second.size}."
                    }
                }) { Text("Audit field keys") }
            }
            Text(status, style = MaterialTheme.typography.bodySmall, modifier = Modifier.padding(vertical = 8.dp))
            output?.let { PdfPreview(it, version) }
            audit?.forEach { f ->
                Text(
                    "${f.type}${if (f.multiline) "*" else ""} ${f.onValue?.let { "[$it] " } ?: ""}${f.name.removePrefix("form1[0].Page2[0].")}  ${f.width.toInt()}x${f.height.toInt()}",
                    style = MaterialTheme.typography.labelSmall,
                    fontFamily = FontFamily.Monospace,
                )
            }
        }
    }
}
