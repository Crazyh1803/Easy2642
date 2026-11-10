package com.easy2642.app.ui

import android.content.Intent
import android.net.Uri
import androidx.core.content.FileProvider
import androidx.documentfile.provider.DocumentFile
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.easy2642.app.Easy2642App
import com.easy2642.app.WorkFiles
import com.easy2642.app.pdf.Attachment
import com.easy2642.app.pdf.AttachmentKind
import com.easy2642.app.pdf.PacketBuilder
import com.easy2642.app.pdf.PacketResult
import com.easy2642.core.ClaimFileName
import com.easy2642.core.ClaimForm
import com.easy2642.core.ClaimPrefill
import com.easy2642.core.ClaimRecord
import com.easy2642.core.Currency
import com.easy2642.core.Dependent
import com.easy2642.core.SPONSOR_PATIENT_ID
import com.easy2642.core.Sponsor
import com.easy2642.core.parseAmountMinor
import java.io.File
import java.util.UUID
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

const val MANUAL_PATIENT_ID = "manual"

data class NewClaimState(
    val step: Int = 0,
    val sponsor: Sponsor? = null,
    val dependents: List<Dependent> = emptyList(),
    val patientId: String? = null,
    val form: ClaimForm = ClaimForm(signedDate = today()),
    val provider: String = "",
    val dateOfService: String = "",
    val amountText: String = "",
    val currency: Currency = Currency.USD,
    val notes: String = "",
    val attachments: List<Attachment> = emptyList(),
    val busy: Boolean = false,
    val packet: PacketResult? = null,
    val packetVersion: Int = 0,
    val warnings: List<String> = emptyList(),
    val message: String? = null,
    val savedTo: String? = null,
) {
    val fileName: String get() = ClaimFileName.build(form.patientName.lastName, provider, dateOfService)

    /** Problems that block moving past the first step. */
    val visitErrors: List<String>
        get() = buildList {
            if (form.patientName.lastName.isBlank()) add("Patient last name")
            if (provider.isBlank()) add("Provider")
            if (!isIsoDate(dateOfService)) add("Date of service")
            if (amountText.isNotBlank() && parseAmountMinor(amountText) == null) add("Amount")
        }

    /** Non-blocking reminders about blocks the claims processor expects. */
    val formGaps: List<String>
        get() = buildList {
            if (form.patientAddress.isBlank()) add("3a Patient's address")
            if (form.relationship == null) add("4 Relationship to sponsor")
            if (!isIsoDate(form.patientDob)) add("5 Date of birth")
            if (form.sex == null) add("6 Sex")
            if (form.accidentRelated == null || form.workRelated == null) add("7 Accident / work related")
            if (form.description.isBlank()) add("8a Description of illness or injury")
            if (form.careType == null) add("8b Type of care")
            if (form.sponsorId.isBlank()) add("10 Sponsor's SSN or DBN")
            if (form.hasOtherInsurance == null) add("11 Other health insurance")
        }
}

class NewClaimViewModel(private val app: Easy2642App) : ViewModel() {
    private val claimId = UUID.randomUUID().toString()
    private val _state = MutableStateFlow(NewClaimState())
    val state = _state.asStateFlow()

    init {
        viewModelScope.launch {
            val sponsor = app.repository.sponsor.first()
            val deps = app.repository.dependents.first()
            _state.update { it.copy(sponsor = sponsor, dependents = deps) }
        }
    }

    fun update(f: (NewClaimState) -> NewClaimState) = _state.update { f(it).copy(packet = null, savedTo = null) }
    fun updateForm(f: (ClaimForm) -> ClaimForm) = update { it.copy(form = f(it.form)) }
    fun clearMessage() = _state.update { it.copy(message = null) }
    fun goTo(step: Int) = _state.update { it.copy(step = step.coerceIn(0, 3)) }

    /** Pre-fills blocks 1-6, 9 and 10 from the chosen profile, keeping everything else entered so far. */
    fun selectPatient(id: String) = update { s ->
        val sponsor = s.sponsor
        val dep = s.dependents.firstOrNull { it.id == id }
        val form = when {
            sponsor != null && id == SPONSOR_PATIENT_ID -> ClaimPrefill.forSponsor(sponsor, s.form)
            sponsor != null && dep != null -> ClaimPrefill.forDependent(sponsor, dep, s.form)
            dep != null -> ClaimPrefill.forDependent(Sponsor(), dep, s.form)
            else -> s.form
        }
        s.copy(patientId = id, form = form)
    }

    fun addAttachments(uris: List<Uri>, kind: AttachmentKind, labelPrefix: String) {
        if (uris.isEmpty()) return
        viewModelScope.launch {
            _state.update { it.copy(busy = true) }
            val added = mutableListOf<Attachment>()
            var failed = 0
            withContext(Dispatchers.IO) {
                val dir = WorkFiles.attachments(app)
                uris.forEach { uri ->
                    val ext = if (kind == AttachmentKind.PDF) "pdf" else "img"
                    val dest = File(dir, "${UUID.randomUUID()}.$ext")
                    val ok = runCatching {
                        app.contentResolver.openInputStream(uri)!!.use { input -> dest.outputStream().use { input.copyTo(it) } }
                        if (kind == AttachmentKind.PDF) PacketBuilder.pageCount(dest)
                    }.isSuccess
                    if (ok) added += Attachment(dest, kind, labelPrefix) else { dest.delete(); failed++ }
                }
            }
            update { s ->
                val all = s.attachments + added
                s.copy(
                    attachments = all.mapIndexed { i, a -> a.copy(label = "${a.label.substringBefore(" #")} #${i + 1}") },
                    busy = false,
                    message = if (failed > 0) "$failed file(s) could not be read and were skipped (password-protected or damaged)." else null,
                )
            }
        }
    }

    fun removeAttachment(a: Attachment) {
        a.file.delete()
        update { it.copy(attachments = it.attachments - a) }
    }

    fun moveAttachment(index: Int, delta: Int) = update { s ->
        val target = index + delta
        if (target !in s.attachments.indices) s else s.copy(attachments = s.attachments.toMutableList().apply { add(target, removeAt(index)) })
    }

    /** Fills the form, merges the attachments and records the claim in the tracker. */
    fun buildPacket() {
        val s = _state.value
        if (s.busy) return
        viewModelScope.launch {
            _state.update { it.copy(busy = true, packet = null, savedTo = null) }
            val result = runCatching {
                withContext(Dispatchers.IO) {
                    val dir = WorkFiles.packets(app)
                    dir.listFiles()?.forEach { it.delete() }
                    val formPdf = File(dir, "form.pdf")
                    val fill = app.formService.fill(s.form, formPdf)
                    val packet = app.packetBuilder.build(formPdf, s.attachments, File(dir, s.fileName))
                    formPdf.delete()
                    saveRecord(s, dossierUri = null)
                    fill.warnings to packet
                }
            }
            _state.update {
                result.fold(
                    onSuccess = { (warnings, packet) ->
                        it.copy(busy = false, packet = packet, packetVersion = it.packetVersion + 1, warnings = warnings)
                    },
                    onFailure = { e -> it.copy(busy = false, message = "The packet could not be built (${e.javaClass.simpleName}). Check the attached files and try again.") },
                )
            }
        }
    }

    private suspend fun saveRecord(s: NewClaimState, dossierUri: String?) {
        val existing = app.repository.claim(claimId)
        val base = existing ?: ClaimRecord(claimId, "", "", "", "")
        app.repository.saveClaim(
            base.copy(
                patientId = s.patientId ?: MANUAL_PATIENT_ID,
                patientName = s.form.patientName.display(),
                provider = s.provider.trim(),
                dateOfService = s.dateOfService,
                amountMinor = parseAmountMinor(s.amountText) ?: 0,
                currency = s.currency,
                dossierUri = dossierUri ?: base.dossierUri,
                isPacketGenerated = true,
                notes = s.notes.trim(),
            ),
        )
    }

    /** Copies the packet to a document the user created with the system picker. */
    fun exportTo(uri: Uri, destinationLabel: String) {
        val s = _state.value
        val packet = s.packet ?: return
        viewModelScope.launch {
            _state.update { it.copy(busy = true) }
            val result = runCatching {
                withContext(Dispatchers.IO) {
                    app.contentResolver.openOutputStream(uri, "w")!!.use { out -> packet.file.inputStream().use { it.copyTo(out) } }
                    // Lets "Open packet" on the dashboard keep working after a reboot, where the provider allows it.
                    runCatching { app.contentResolver.takePersistableUriPermission(uri, Intent.FLAG_GRANT_READ_URI_PERMISSION) }
                    saveRecord(s, uri.toString())
                }
            }
            _state.update {
                if (result.isSuccess) it.copy(busy = false, savedTo = destinationLabel)
                else it.copy(busy = false, message = "Saving failed. The destination may be read-only or unavailable; try another location.")
            }
        }
    }

    /** Creates the file inside the designated claims folder and writes the packet there. */
    fun exportToClaimsFolder() {
        val s = _state.value
        val tree = app.settings.claimsFolder ?: return
        viewModelScope.launch {
            val target = withContext(Dispatchers.IO) {
                runCatching { DocumentFile.fromTreeUri(app, tree)?.createFile("application/pdf", s.fileName)?.uri }.getOrNull()
            }
            if (target == null) {
                _state.update { it.copy(message = "The claims folder is no longer accessible. Choose it again in Settings, or pick a location manually.") }
            } else {
                exportTo(target, folderLabel(app, tree) ?: "claims folder")
            }
        }
    }

    fun shareIntent(): Intent? {
        val packet = _state.value.packet ?: return null
        val uri = FileProvider.getUriForFile(app, "${app.packageName}.share", packet.file)
        val send = Intent(Intent.ACTION_SEND)
            .setType("application/pdf")
            .putExtra(Intent.EXTRA_STREAM, uri)
            .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        return Intent.createChooser(send, "Share claim packet")
    }

    override fun onCleared() {
        WorkFiles.purge(app)
    }
}
