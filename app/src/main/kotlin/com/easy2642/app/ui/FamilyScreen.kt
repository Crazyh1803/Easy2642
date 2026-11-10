package com.easy2642.app.ui

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.easy2642.app.Easy2642App
import com.easy2642.core.Dependent
import com.easy2642.core.PersonName
import com.easy2642.core.Relationship
import com.easy2642.core.Sex
import com.easy2642.core.Sponsor
import java.util.UUID
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

@Composable
fun FamilyScreen(app: Easy2642App) {
    val repo = app.repository
    val scope = rememberCoroutineScope()
    val snackbar = remember { SnackbarHostState() }
    val dependents by repo.dependents.collectAsStateWithLifecycle(emptyList())
    val signatures by repo.signatures.collectAsStateWithLifecycle(emptyList())
    var sponsor by remember { mutableStateOf(Sponsor()) }
    var loaded by rememberSaveable { mutableStateOf(false) }
    var editing by remember { mutableStateOf<Dependent?>(null) }
    var deleting by remember { mutableStateOf<Dependent?>(null) }

    LaunchedEffect(Unit) {
        repo.sponsor.first()?.let { sponsor = it }
        loaded = true
    }

    Scaffold(
        topBar = { TopAppBar(title = { Text("Family profiles") }) },
        snackbarHost = { SnackbarHost(snackbar) },
    ) { padding ->
        Column(Modifier.padding(padding).verticalScroll(rememberScrollState()).padding(horizontal = 16.dp)) {
            Text(
                "Saved once, encrypted on this device, and reused to pre-fill blocks 1–6, 9 and 10 of every new claim.",
                style = MaterialTheme.typography.bodySmall,
            )
            SectionTitle("Sponsor")
            NameFields(sponsor.name) { sponsor = sponsor.copy(name = it) }
            Field("DoD Benefits Number (DBN) or SSN", sponsor.dodIdOrSsn, { sponsor = sponsor.copy(dodIdOrSsn = it) }, keyboardType = KeyboardType.Number)
            DateField("Date of birth", sponsor.dob, { sponsor = sponsor.copy(dob = it) })
            ChoiceChips("Sex", Sex.entries, sponsor.sex, { it.label }, { sponsor = sponsor.copy(sex = it) })
            Field("Branch of service", sponsor.branchOfService, { sponsor = sponsor.copy(branchOfService = it) }, supporting = "For your records; not printed on the form")
            Field("Duty status", sponsor.dutyStatus, { sponsor = sponsor.copy(dutyStatus = it) }, supporting = "For your records; not printed on the form")
            Field("Street / APO / FPO address", sponsor.streetAddress, { sponsor = sponsor.copy(streetAddress = it) })
            Field("City (or APO / FPO)", sponsor.city, { sponsor = sponsor.copy(city = it) })
            Row {
                Field("State / country", sponsor.state, { sponsor = sponsor.copy(state = it) }, Modifier.weight(1f).padding(end = 8.dp), capitalization = KeyboardCapitalization.Characters)
                Field("ZIP", sponsor.zipCode, { sponsor = sponsor.copy(zipCode = it) }, Modifier.weight(1f), keyboardType = KeyboardType.Number)
            }
            Field("Phone (with area/country code)", sponsor.phoneNumber, { sponsor = sponsor.copy(phoneNumber = it) }, keyboardType = KeyboardType.Phone, supporting = "e.g. (555) 010-0100 or +49 631 5550100")
            Button(
                enabled = loaded,
                onClick = { scope.launch { repo.saveSponsor(sponsor); snackbar.showSnackbar("Sponsor saved") } },
                modifier = Modifier.padding(vertical = 8.dp),
            ) { Text("Save sponsor") }

            SectionTitle("Dependents")
            if (dependents.isEmpty()) Text("No dependents added yet.", style = MaterialTheme.typography.bodyMedium)
            dependents.forEach { d ->
                Card(Modifier.fillMaxWidth().padding(vertical = 4.dp)) {
                    Row(Modifier.padding(start = 16.dp), verticalAlignment = Alignment.CenterVertically) {
                        Column(Modifier.weight(1f).padding(vertical = 8.dp)) {
                            Text(d.name.display().ifBlank { "Unnamed" }, style = MaterialTheme.typography.titleMedium)
                            Text(listOf(d.relationship.label, d.dob).filter { it.isNotBlank() }.joinToString(" · "), style = MaterialTheme.typography.bodySmall)
                        }
                        IconButton(onClick = { editing = d }) { Icon(Icons.Filled.Edit, contentDescription = "Edit ${d.name.display()}") }
                        IconButton(onClick = { deleting = d }) { Icon(Icons.Filled.Delete, contentDescription = "Delete ${d.name.display()}") }
                    }
                }
            }
            OutlinedButton(
                onClick = {
                    // Most families use the sponsor's number for every member, so start from it.
                    editing = Dependent(
                        UUID.randomUUID().toString(),
                        name = PersonName(lastName = sponsor.name.lastName),
                        dodId = sponsor.dodIdOrSsn,
                    )
                },
                modifier = Modifier.padding(vertical = 8.dp),
            ) { Text("Add dependent") }

            SectionTitle("Saved signatures")
            if (signatures.isEmpty()) {
                Text("None yet. You can save one when you sign your first claim.", style = MaterialTheme.typography.bodyMedium)
            }
            signatures.forEach { s ->
                Card(Modifier.fillMaxWidth().padding(vertical = 4.dp)) {
                    Row(Modifier.padding(start = 16.dp), verticalAlignment = Alignment.CenterVertically) {
                        Column(Modifier.weight(1f).padding(vertical = 8.dp)) {
                            Text(s.signerName, style = MaterialTheme.typography.titleMedium)
                            SignaturePreview(s.signature)
                        }
                        IconButton(onClick = { scope.launch { repo.deleteSignature(s.id) } }) {
                            Icon(Icons.Filled.Delete, contentDescription = "Delete ${s.signerName}'s saved signature")
                        }
                    }
                }
            }
            Spacer(Modifier.height(24.dp))
        }
    }

    editing?.let { original ->
        var d by remember(original.id) { mutableStateOf(original) }
        AlertDialog(
            onDismissRequest = { editing = null },
            title = { Text("Dependent") },
            text = {
                Column(Modifier.verticalScroll(rememberScrollState())) {
                    NameFields(d.name) { d = d.copy(name = it) }
                    ChoiceChips("Relationship to sponsor", Relationship.entries - Relationship.SELF, d.relationship, { it.label }) {
                        if (it != null) d = d.copy(relationship = it)
                    }
                    DateField("Date of birth", d.dob, { d = d.copy(dob = it) })
                    ChoiceChips("Sex", Sex.entries, d.sex, { it.label }, { d = d.copy(sex = it) })
                    Field("DoD Benefits Number (DBN)", d.dodId, { d = d.copy(dodId = it) }, keyboardType = KeyboardType.Number, supporting = "Starts as the sponsor's number; change it if this person has their own")
                    if (d.dodId.isBlank() && sponsor.dodIdOrSsn.isNotBlank()) {
                        TextButton(onClick = { d = d.copy(dodId = sponsor.dodIdOrSsn) }) { Text("Use sponsor's number") }
                    }
                }
            },
            confirmButton = {
                TextButton(
                    enabled = d.name.display().isNotBlank(),
                    onClick = { scope.launch { repo.saveDependent(d) }; editing = null },
                ) { Text("Save") }
            },
            dismissButton = { TextButton(onClick = { editing = null }) { Text("Cancel") } },
        )
    }
    deleting?.let { d ->
        AlertDialog(
            onDismissRequest = { deleting = null },
            title = { Text("Remove ${d.name.display()}?") },
            text = { Text("Existing claims for this person stay in the tracker.") },
            confirmButton = { TextButton(onClick = { scope.launch { repo.deleteDependent(d.id) }; deleting = null }) { Text("Remove") } },
            dismissButton = { TextButton(onClick = { deleting = null }) { Text("Cancel") } },
        )
    }
}

@Composable
fun NameFields(name: PersonName, onChange: (PersonName) -> Unit) {
    Field("Last name", name.lastName, { onChange(name.copy(lastName = it)) })
    Row {
        Field("First name", name.firstName, { onChange(name.copy(firstName = it)) }, Modifier.weight(2f).padding(end = 8.dp))
        Field("MI", name.middleInitial, { onChange(name.copy(middleInitial = it.take(1).uppercase())) }, Modifier.weight(1f), capitalization = KeyboardCapitalization.Characters)
    }
}
