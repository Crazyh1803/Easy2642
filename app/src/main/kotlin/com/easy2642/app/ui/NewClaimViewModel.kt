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
import com.easy2642.app.pdf.AttachmentCategory
import com.easy2642.app.pdf.AttachmentKind
import com.easy2642.app.pdf.PacketBuilder
import com.easy2642.app.pdf.PacketResult
import com.easy2642.core.ClaimFileName
import com.easy2642.core.ClaimForm
import com.easy2642.core.ClaimPrefill
import com.easy2642.core.ClaimRecord
import com.easy2642.core.Currency
import com.easy2642.core.Dependent
import com.easy2642.core.OutputMode
import com.easy2642.core.SPONSOR_PATIENT_ID
import com.easy2642.core.SavedSignature
import com.easy2642.core.Signature
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

object Steps {
    const val VISIT = 0
    const val FORM = 1
    const val DOCUMENTS = 2
    const val SIGN = 3
    const val REVIEW = 4
    val TITLES = listOf("Patient & visit", "Form details", "Supporting documents", "Sign", "Review & save")
}

data class NewClaimState(
    val step: Int = Steps.VISIT,
    val sponsor: Sponsor? = null,
    val dependents: List<Dependent> = emptyList(),
    val savedSignatures: List<SavedSignature> = emptyList(),
    val patientId: String? = null,
    val form: ClaimForm = ClaimForm(signedDate = today()),
    val provider: String = "",
    val dateOfService: String = "",
    val amountText: String = "",
    val currency: Currency = Currency.USD,
    val notes: String = "",
    val attachments: List<Attachment> = emptyList(),
    /** The signature that will be drawn into block 12a. A packet cannot be built without it. */
    val signature: Signature? = null,
    val signerName: String = "",
    val outputMode: OutputMode = OutputMode.COMBINED,
    val busy: Boolean = false,
    /** One file in combined mode; form, bill and receipt files in separate mode. */
    val outputs: List<PacketResult> = emptyList(),
    val outputsVersion: Int = 0,
    val warnings: List<String> = emptyList(),
    val message: String? = null,
    val savedTo: String? = null,
) {
    val isSigned: Boolean get() = signature?.isBlank == false

    fun fileName(part: String? = null): String =
        ClaimFileName.build(form.patientName.lastName, provider, dateOfService, part)

    /** Problems that block moving past the first step. */
    val visitErrors: List<String>
        get() = buildList {
            if (form.patientName.lastName.isBlank()) add("Patient last name")
            if (provider.isBlank()) add("Provider")
            if (!isIsoDate(dateOfService)) add("Date of service")
            if (amountText.isNotBlank() && parseAmountMinor(amountText) == null) add("Amount")
        }

    /** Problems that block leaving the signing step. */
    val signErrors: List<String>
        get() = buildList {
            if (!isSigned) add("Signature")
            if (!isIsoDate(form.signedDate)) add("Date signed")
            if (form.signerRelationship.isBlank()) add("Relationship to patient")
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
    private val _state = MutableStateFlow(NewClaimState(outputMode = app.settings.outputMode))
    val state = _state.asStateFlow()

    init {
        viewModelScope.launch {
            val sponsor = app.repository.sponsor.first()
            val deps = app.repository.dependents.first()
            val sigs = app.repository.signatures.first()
            _state.update { it.copy(sponsor = sponsor, dependents = deps, savedSignatures = sigs) }
        }
    }

    /** Any change to the claim invalidates files that were already built. */
    fun update(f: (NewClaimState) -> NewClaimState) = _state.update { f(it).copy(outputs = emptyList(), savedTo = null) }
    fun updateForm(f: (ClaimForm) -> ClaimForm) = update { it.copy(form = f(it.form)) }
    fun clearMessage() = _state.update { it.copy(message = null) }

    /** Moves between steps. The review step cannot be reached unsigned. */
    fun goTo(step: Int) = _state.update {
        val target = step.coerceIn(Steps.VISIT, Steps.REVIEW)
        if (target == Steps.REVIEW && it.signErrors.isNotEmpty()) it.copy(step = Steps.SIGN) else it.copy(step = target)
    }

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

    fun setCategory(a: Attachment, category: AttachmentCategory) = update { s ->
        s.copy(attachments = s.attachments.map { if (it === a) it.copy(category = category) else it })
    }

    fun setOutputMode(mode: OutputMode) {
        update { it.copy(outputMode = mode) }
        if (_state.value.step == Steps.REVIEW) buildPacket()
    }

    /** Applies a signature the signer saved earlier. */
    fun useSavedSignature(saved: SavedSignature) = update { it.copy(signature = saved.signature, signerName = saved.signerName) }

    /** Applies a freshly drawn signature and, if asked, saves it under the signer's name. */
    fun applyDrawnSignature(signature: Signature, signerName: String, save: Boolean) {
        if (signature.isBlank) return
        update { it.copy(signature = signature, signerName = signerName.trim()) }
        if (save && signerName.isNotBlank()) {
            viewModelScope.launch {
                app.repository.saveSignature(signerName, signature)
                val sigs = app.repository.signatures.first()
                _state.update { it.copy(savedSignatures = sigs) }
            }
        }
    }

    fun clearSignature() = update { it.copy(signature = null) }

    /** Fills and signs the form, writes the output file(s) and records the claim in the tracker. */
    fun buildPacket() {
        val s = _state.value
        if (s.busy) return
        val signature = s.signature
        if (signature == null || s.signErrors.isNotEmpty()) {
            // Hard rule: nothing is generated without a signature.
            _state.update { it.copy(step = Steps.SIGN, message = "Sign the form before the packet can be created.") }
            return
        }
        viewModelScope.launch {
            _state.update { it.copy(busy = true, outputs = emptyList(), savedTo = null) }
            val result = runCatching {
                withContext(Dispatchers.IO) {
                    val dir = WorkFiles.packets(app)
                    dir.listFiles()?.forEach { it.delete() }
                    val formPdf = File(dir, "form.pdf")
                    val fill = app.formService.fill(s.form, formPdf, signature)
                    val outputs = when (s.outputMode) {
                        OutputMode.COMBINED -> listOf(app.packetBuilder.build(formPdf, s.attachments, File(dir, s.fileName())))
                        OutputMode.SEPARATE -> app.packetBuilder.buildSeparate(formPdf, s.attachments, dir) { s.fileName(it) }
                    }
                    formPdf.delete()
                    saveRecord(s, dossierUri = null)
                    fill.warnings to outputs
                }
            }
            _state.update {
                result.fold(
                    onSuccess = { (warnings, outputs) ->
                        it.copy(busy = false, outputs = outputs, outputsVersion = it.outputsVersion + 1, warnings = warnings)
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

    private fun copyTo(uri: Uri, file: File) {
        app.contentResolver.openOutputStream(uri, "w")!!.use { out -> file.inputStream().use { it.copyTo(out) } }
    }

    /** Combined mode: copies the single packet to a document the user created with the system picker. */
    fun exportTo(uri: Uri, destinationLabel: String) {
        val s = _state.value
        val packet = s.outputs.singleOrNull() ?: return
        viewModelScope.launch {
            _state.update { it.copy(busy = true) }
            val result = runCatching {
                withContext(Dispatchers.IO) {
                    copyTo(uri, packet.file)
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

    /** Writes every output file into a folder: the designated claims folder, or one just picked. */
    fun exportToFolder(tree: Uri? = app.settings.claimsFolder) {
        val s = _state.value
        if (tree == null || s.outputs.isEmpty()) return
        viewModelScope.launch {
            _state.update { it.copy(busy = true) }
            val result = runCatching {
                withContext(Dispatchers.IO) {
                    val folder = DocumentFile.fromTreeUri(app, tree) ?: error("folder unavailable")
                    val written = s.outputs.map { out ->
                        val doc = folder.createFile("application/pdf", out.file.name) ?: error("cannot create file")
                        copyTo(doc.uri, out.file)
                        doc.uri
                    }
                    // The first file is the packet (combined) or the form (separate).
                    saveRecord(s, written.first().toString())
                }
            }
            _state.update {
                if (result.isSuccess) it.copy(busy = false, savedTo = "\"${folderLabel(app, tree) ?: "the folder"}\"")
                else it.copy(busy = false, message = "That folder is not accessible or not writable. Choose another folder, or pick it again in Settings.")
            }
        }
    }

    fun shareIntent(): Intent? {
        val outputs = _state.value.outputs
        if (outputs.isEmpty()) return null
        val uris = outputs.map { FileProvider.getUriForFile(app, "${app.packageName}.share", it.file) }
        val send = if (uris.size == 1) {
            Intent(Intent.ACTION_SEND).putExtra(Intent.EXTRA_STREAM, uris.single())
        } else {
            Intent(Intent.ACTION_SEND_MULTIPLE).putParcelableArrayListExtra(Intent.EXTRA_STREAM, ArrayList(uris))
        }
        send.setType("application/pdf").addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        return Intent.createChooser(send, "Share claim files")
    }

    override fun onCleared() {
        WorkFiles.purge(app)
    }
}
