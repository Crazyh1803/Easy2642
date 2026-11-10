package com.easy2642.app.ui

import android.content.Intent
import android.net.Uri
import android.widget.Toast
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.Checkbox
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.Role
import androidx.compose.foundation.selection.toggleable
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.compose.viewModel
import com.easy2642.app.Easy2642App
import com.easy2642.app.data.Repository
import com.easy2642.core.ClaimFilter
import com.easy2642.core.ClaimRecord
import com.easy2642.core.ClaimState
import com.easy2642.core.SPONSOR_PATIENT_ID
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class PatientChip(val id: String, val name: String)

data class DashboardState(
    val loading: Boolean = true,
    val filter: ClaimFilter = ClaimFilter(),
    val patients: List<PatientChip> = emptyList(),
    val claims: List<ClaimRecord> = emptyList(),
    val total: Int = 0,
)

class DashboardViewModel(private val repo: Repository) : ViewModel() {
    private val filter = MutableStateFlow(ClaimFilter())

    val state = combine(repo.claims, repo.sponsor, repo.dependents, filter) { claims, sponsor, deps, f ->
        val known = buildList {
            sponsor?.let { add(PatientChip(SPONSOR_PATIENT_ID, it.name.firstName.ifBlank { "Sponsor" })) }
            deps.forEach { add(PatientChip(it.id, it.name.firstName.ifBlank { it.name.lastName })) }
        }
        // Claims may reference a patient whose profile was since removed.
        val orphans = claims.filter { c -> known.none { it.id == c.patientId } }
            .distinctBy { it.patientId }
            .map { PatientChip(it.patientId, it.patientName.substringBefore(' ').ifBlank { "Other" }) }
        DashboardState(false, f, known + orphans, claims.filter(f::matches), claims.size)
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), DashboardState())

    fun setPatient(id: String?) = filter.update { it.copy(patientId = id) }
    fun setState(s: ClaimState?) = filter.update { it.copy(state = s) }
    fun setQuery(q: String) = filter.update { it.copy(query = q) }

    fun setFiled(c: ClaimRecord, filed: Boolean) = viewModelScope.launch { repo.saveClaim(c.withFiled(filed, today())) }
    fun setReimbursed(c: ClaimRecord, done: Boolean) = viewModelScope.launch { repo.saveClaim(c.withReimbursed(done, today())) }
    fun setNotes(c: ClaimRecord, notes: String) = viewModelScope.launch { repo.saveClaim(c.copy(notes = notes)) }
    fun delete(c: ClaimRecord) = viewModelScope.launch { repo.deleteClaim(c.id) }
}

@Composable
fun DashboardScreen(app: Easy2642App, onNewClaim: () -> Unit) {
    val vm: DashboardViewModel = viewModel { DashboardViewModel(app.repository) }
    val state by vm.state.collectAsStateWithLifecycle()

    Scaffold(
        topBar = { TopAppBar(title = { Text("Claims") }) },
        floatingActionButton = {
            ExtendedFloatingActionButton(
                onClick = onNewClaim,
                icon = { Icon(Icons.Filled.Add, contentDescription = null) },
                text = { Text("New claim") },
            )
        },
    ) { padding ->
        Column(Modifier.padding(padding).fillMaxSize()) {
            OutlinedTextField(
                value = state.filter.query,
                onValueChange = vm::setQuery,
                placeholder = { Text("Search provider or notes") },
                leadingIcon = { Icon(Icons.Filled.Search, contentDescription = null) },
                trailingIcon = {
                    if (state.filter.query.isNotEmpty()) {
                        IconButton(onClick = { vm.setQuery("") }) { Icon(Icons.Filled.Clear, contentDescription = "Clear search") }
                    }
                },
                singleLine = true,
                modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp),
            )
            ChipRow {
                FilterChip(state.filter.patientId == null, { vm.setPatient(null) }, { Text("All") })
                state.patients.forEach { p ->
                    FilterChip(state.filter.patientId == p.id, { vm.setPatient(p.id) }, { Text(p.name) })
                }
            }
            ChipRow {
                FilterChip(state.filter.state == null, { vm.setState(null) }, { Text("All") })
                ClaimState.entries.forEach { s ->
                    FilterChip(state.filter.state == s, { vm.setState(s) }, { Text(s.label) })
                }
            }
            when {
                state.loading -> Unit
                state.claims.isEmpty() -> Box(Modifier.fillMaxSize().padding(32.dp), contentAlignment = Alignment.Center) {
                    Text(
                        if (state.total == 0) "No claims yet.\nTap \"New claim\" to prepare your first DD Form 2642 packet."
                        else "No claims match these filters.",
                        textAlign = TextAlign.Center,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                else -> LazyColumn(
                    contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 8.dp, bottom = 96.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    items(state.claims, key = { it.id }) { claim -> ClaimCard(claim, vm) }
                }
            }
        }
    }
}

@Composable
private fun ChipRow(content: @Composable () -> Unit) {
    Row(
        Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()).padding(horizontal = 16.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) { content() }
}

@Composable
private fun ClaimCard(claim: ClaimRecord, vm: DashboardViewModel) {
    val context = LocalContext.current
    var menu by remember { mutableStateOf(false) }
    var editNotes by remember { mutableStateOf(false) }
    var confirmDelete by remember { mutableStateOf(false) }

    Card(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(start = 16.dp, end = 4.dp, top = 8.dp, bottom = 8.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text(claim.provider.ifBlank { "Unnamed provider" }, style = MaterialTheme.typography.titleMedium)
                    Text(
                        listOf(claim.patientName, claim.dateOfService, claim.formattedAmount()).filter { it.isNotBlank() }.joinToString(" · "),
                        style = MaterialTheme.typography.bodyMedium,
                    )
                    Text(claim.state.label, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.primary)
                }
                Box {
                    IconButton(onClick = { menu = true }) { Icon(Icons.Filled.MoreVert, contentDescription = "Claim options") }
                    DropdownMenu(menu, onDismissRequest = { menu = false }) {
                        if (claim.dossierUri.isNotBlank()) {
                            DropdownMenuItem(text = { Text("Open packet") }, onClick = {
                                menu = false
                                val intent = Intent(Intent.ACTION_VIEW)
                                    .setDataAndType(Uri.parse(claim.dossierUri), "application/pdf")
                                    .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                                runCatching { context.startActivity(intent) }.onFailure {
                                    Toast.makeText(context, "The saved packet is no longer available at that location.", Toast.LENGTH_LONG).show()
                                }
                            })
                        }
                        DropdownMenuItem(text = { Text("Edit notes") }, onClick = { menu = false; editNotes = true })
                        DropdownMenuItem(text = { Text("Delete claim") }, onClick = { menu = false; confirmDelete = true })
                    }
                }
            }
            CheckRow("Filed Online", claim.isFiled, claim.filedDate) { vm.setFiled(claim, it) }
            CheckRow("Reimbursed", claim.isReimbursed, claim.reimbursedDate) { vm.setReimbursed(claim, it) }
            if (claim.notes.isNotBlank()) {
                Text(claim.notes, style = MaterialTheme.typography.bodySmall, modifier = Modifier.padding(end = 12.dp, top = 4.dp))
            }
        }
    }

    if (editNotes) {
        var text by remember { mutableStateOf(claim.notes) }
        AlertDialog(
            onDismissRequest = { editNotes = false },
            title = { Text("Notes") },
            text = { OutlinedTextField(text, { text = it }, minLines = 3, modifier = Modifier.fillMaxWidth()) },
            confirmButton = { TextButton(onClick = { vm.setNotes(claim, text.trim()); editNotes = false }) { Text("Save") } },
            dismissButton = { TextButton(onClick = { editNotes = false }) { Text("Cancel") } },
        )
    }
    if (confirmDelete) {
        AlertDialog(
            onDismissRequest = { confirmDelete = false },
            title = { Text("Delete this claim?") },
            text = { Text("The tracker entry is removed. The exported PDF, if any, stays where you saved it.") },
            confirmButton = { TextButton(onClick = { vm.delete(claim); confirmDelete = false }) { Text("Delete") } },
            dismissButton = { TextButton(onClick = { confirmDelete = false }) { Text("Cancel") } },
        )
    }
}

@Composable
private fun CheckRow(label: String, checked: Boolean, date: String?, onChange: (Boolean) -> Unit) {
    Row(
        Modifier.fillMaxWidth().toggleable(checked, role = Role.Checkbox, onValueChange = onChange),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Checkbox(checked = checked, onCheckedChange = null, modifier = Modifier.padding(vertical = 8.dp))
        Text(
            if (checked && date != null) "$label · $date" else label,
            style = MaterialTheme.typography.bodyMedium,
            modifier = Modifier.padding(start = 12.dp),
        )
    }
}
