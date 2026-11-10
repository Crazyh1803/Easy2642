package com.easy2642.app.ui

import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.IntentSenderRequest
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.foundation.selection.toggleable
import androidx.compose.ui.semantics.Role
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.easy2642.app.Easy2642App
import com.easy2642.app.pdf.AttachmentCategory
import com.easy2642.app.pdf.AttachmentKind
import com.easy2642.app.pdf.PacketBuilder
import com.easy2642.app.scan.DocumentScan
import com.easy2642.core.CareType
import com.easy2642.core.Currency
import com.easy2642.core.InsuranceEntry
import com.easy2642.core.OhiType
import com.easy2642.core.OutputMode
import com.easy2642.core.Signature
import com.easy2642.core.SignaturePoint
import com.easy2642.core.OverseasOhiType
import com.easy2642.core.PaymentCurrency
import com.easy2642.core.Relationship
import com.easy2642.core.SPONSOR_PATIENT_ID
import com.easy2642.core.Sex

@Composable
fun NewClaimScreen(app: Easy2642App, onClose: () -> Unit, onOpenFamily: () -> Unit) {
    val vm: NewClaimViewModel = viewModel { NewClaimViewModel(app) }
    val state by vm.state.collectAsStateWithLifecycle()
    val snackbar = remember { SnackbarHostState() }
    var confirmLeave by remember { mutableStateOf(false) }

    val back = {
        when {
            state.step > 0 -> vm.goTo(state.step - 1)
            state.provider.isNotBlank() || state.attachments.isNotEmpty() -> confirmLeave = true
            else -> onClose()
        }
    }
    BackHandler(onBack = back)

    LaunchedEffect(state.message) {
        state.message?.let {
            snackbar.showSnackbar(it)
            vm.clearMessage()
        }
    }
    LaunchedEffect(state.step) {
        if (state.step == Steps.REVIEW && state.outputs.isEmpty()) vm.buildPacket()
    }

    Scaffold(
        topBar = {
            Column {
                TopAppBar(
                    title = { Text("${state.step + 1} of 5 · ${Steps.TITLES[state.step]}") },
                    navigationIcon = { IconButton(onClick = back) { Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back") } },
                )
                LinearProgressIndicator(progress = { (state.step + 1) / 5f }, modifier = Modifier.fillMaxWidth())
            }
        },
        snackbarHost = { SnackbarHost(snackbar) },
        bottomBar = {
            Surface(tonalElevation = 2.dp) {
                Row(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp), horizontalArrangement = Arrangement.End) {
                    when (state.step) {
                        Steps.VISIT -> Button(enabled = state.visitErrors.isEmpty(), onClick = { vm.goTo(Steps.FORM) }) { Text("Next") }
                        Steps.FORM -> Button(onClick = { vm.goTo(Steps.DOCUMENTS) }) { Text("Next") }
                        Steps.DOCUMENTS -> Button(enabled = !state.busy, onClick = { vm.goTo(Steps.SIGN) }) { Text("Next: sign") }
                        // The packet cannot be created until the form is signed.
                        Steps.SIGN -> Button(enabled = state.signErrors.isEmpty(), onClick = { vm.goTo(Steps.REVIEW) }) { Text("Build packet") }
                        else -> Button(onClick = onClose) { Text("Done") }
                    }
                }
            }
        },
    ) { padding ->
        Column(
            Modifier.padding(padding).fillMaxSize().imePadding().verticalScroll(rememberScrollState()).padding(horizontal = 16.dp),
        ) {
            when (state.step) {
                Steps.VISIT -> VisitStep(state, vm, onOpenFamily)
                Steps.FORM -> FormStep(state, vm)
                Steps.DOCUMENTS -> DocumentsStep(state, vm)
                Steps.SIGN -> SignStep(state, vm)
                else -> ReviewStep(app, state, vm)
            }
            Spacer(Modifier.height(24.dp))
        }
    }

    if (confirmLeave) {
        AlertDialog(
            onDismissRequest = { confirmLeave = false },
            title = { Text("Discard this claim?") },
            text = { Text("What you entered and any attached documents will be discarded.") },
            confirmButton = { TextButton(onClick = { confirmLeave = false; onClose() }) { Text("Discard") } },
            dismissButton = { TextButton(onClick = { confirmLeave = false }) { Text("Keep editing") } },
        )
    }
}

@Composable
private fun VisitStep(state: NewClaimState, vm: NewClaimViewModel, onOpenFamily: () -> Unit) {
    SectionTitle("Who is the patient?")
    val sponsor = state.sponsor
    if (sponsor == null && state.dependents.isEmpty()) {
        Text("No family profiles saved yet. Set them up once to pre-fill every claim, or type the details in below.", style = MaterialTheme.typography.bodyMedium)
        TextButton(onClick = onOpenFamily) { Text("Set up family profiles") }
    } else {
        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            if (sponsor != null) {
                androidx.compose.material3.FilterChip(
                    selected = state.patientId == SPONSOR_PATIENT_ID,
                    onClick = { vm.selectPatient(SPONSOR_PATIENT_ID) },
                    label = { Text(sponsor.name.firstName.ifBlank { "Sponsor" }) },
                )
            }
            state.dependents.forEach { d ->
                androidx.compose.material3.FilterChip(
                    selected = state.patientId == d.id,
                    onClick = { vm.selectPatient(d.id) },
                    label = { Text(d.name.firstName.ifBlank { d.name.lastName }) },
                )
            }
        }
        if (sponsor == null) Text("Add the sponsor profile under Family to pre-fill blocks 9 and 10.", style = MaterialTheme.typography.bodySmall)
    }
    NameFields(state.form.patientName) { n -> vm.updateForm { it.copy(patientName = n) } }

    SectionTitle("The visit")
    Field("Provider (doctor, clinic or pharmacy)", state.provider, { v -> vm.update { it.copy(provider = v) } })
    DateField("Date of service", state.dateOfService, { v -> vm.update { it.copy(dateOfService = v) } })
    Field(
        "Amount charged", state.amountText, { v -> vm.update { it.copy(amountText = v) } },
        keyboardType = KeyboardType.Decimal,
        isError = state.amountText.isNotBlank() && "Amount" in state.visitErrors,
        supporting = "For your tracker; the form itself has no amount box",
    )
    ChoiceChips("Currency", Currency.entries, state.currency, { it.name }) { c -> if (c != null) vm.update { it.copy(currency = c) } }
    Field("Notes", state.notes, { v -> vm.update { it.copy(notes = v) } }, singleLine = false, minLines = 2, capitalization = KeyboardCapitalization.Sentences)
    if (state.visitErrors.isNotEmpty()) {
        Text("Still needed: ${state.visitErrors.joinToString(", ")}", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

@Composable
private fun FormStep(state: NewClaimState, vm: NewClaimViewModel) {
    val f = state.form
    Text("These are the blocks on page 2 of DD Form 2642. Pre-filled values can be changed for this claim only.", style = MaterialTheme.typography.bodySmall)

    SectionTitle("2 · Patient's telephone")
    Row {
        Field("Area/country", f.primaryPhone.areaCode, { v -> vm.updateForm { it.copy(primaryPhone = it.primaryPhone.copy(areaCode = v)) } }, Modifier.weight(1f).padding(end = 8.dp), keyboardType = KeyboardType.Phone)
        Field("Primary number", f.primaryPhone.number, { v -> vm.updateForm { it.copy(primaryPhone = it.primaryPhone.copy(number = v)) } }, Modifier.weight(2f), keyboardType = KeyboardType.Phone)
    }
    Row {
        Field("Area/country", f.secondaryPhone.areaCode, { v -> vm.updateForm { it.copy(secondaryPhone = it.secondaryPhone.copy(areaCode = v)) } }, Modifier.weight(1f).padding(end = 8.dp), keyboardType = KeyboardType.Phone)
        Field("Secondary number", f.secondaryPhone.number, { v -> vm.updateForm { it.copy(secondaryPhone = it.secondaryPhone.copy(number = v)) } }, Modifier.weight(2f), keyboardType = KeyboardType.Phone)
    }

    SectionTitle("3 · Address")
    Field("3a Patient's address", f.patientAddress, { v -> vm.updateForm { it.copy(patientAddress = v) } }, singleLine = false, supporting = "Street, apt, city, state/country and ZIP. No PO boxes except rural routes.")
    Field("3b Overseas only: state/country where care was given", f.overseasLocation, { v -> vm.updateForm { it.copy(overseasLocation = v) } }, supporting = "Leave blank if the same as 3a")

    SectionTitle("4–6 · Patient")
    ChoiceChips("4 Relationship to sponsor", Relationship.entries, f.relationship, { it.label }) { v -> vm.updateForm { it.copy(relationship = v) } }
    if (f.relationship == Relationship.OTHER) {
        Field("Specify relationship", f.relationshipOther, { v -> vm.updateForm { it.copy(relationshipOther = v) } })
    }
    DateField("5 Date of birth", f.patientDob, { v -> vm.updateForm { it.copy(patientDob = v) } })
    ChoiceChips("6 Sex", Sex.entries, f.sex, { it.label }) { v -> vm.updateForm { it.copy(sex = v) } }

    SectionTitle("7 · Is the condition…")
    YesNo("Accident related?", f.accidentRelated) { v -> vm.updateForm { it.copy(accidentRelated = v) } }
    YesNo("Work related?", f.workRelated) { v -> vm.updateForm { it.copy(workRelated = v) } }
    if (f.accidentRelated == true || f.workRelated == true) {
        Text("If yes, the claims processor also requires DD Form 2527 (Statement of Personal Injury).", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.error)
    }

    SectionTitle("8 · Illness and care")
    Field(
        "8a Illness, injury or symptoms treated", f.description, { v -> vm.updateForm { it.copy(description = v) } },
        singleLine = false, minLines = 3, capitalization = KeyboardCapitalization.Sentences,
        supporting = "If an injury, say how it happened. ${f.description.length} characters (about 600 fit comfortably).",
    )
    ChoiceChips("8b Was the care", CareType.entries, f.careType, { it.label }) { v -> vm.updateForm { it.copy(careType = v) } }
    Text("8c Overseas claims only", style = MaterialTheme.typography.bodyMedium, modifier = Modifier.padding(top = 4.dp))
    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        androidx.compose.material3.FilterChip(f.telemedicine, { vm.updateForm { it.copy(telemedicine = !it.telemedicine) } }, { Text("Telemedicine") })
        androidx.compose.material3.FilterChip(f.urgentCare, { vm.updateForm { it.copy(urgentCare = !it.urgentCare) } }, { Text("Urgent care") })
        androidx.compose.material3.FilterChip(f.telemedicineAudioOnly, { vm.updateForm { it.copy(telemedicineAudioOnly = !it.telemedicineAudioOnly) } }, { Text("Telemedicine, audio only") })
    }
    if (f.telemedicineAudioOnly) {
        Field("Reason for audio only", f.audioOnlyReason, { v -> vm.updateForm { it.copy(audioOnlyReason = v) } }, capitalization = KeyboardCapitalization.Sentences)
    }

    SectionTitle("9–10 · Sponsor")
    if (f.relationship == Relationship.SELF) {
        Text("Block 9 will read \"Same\" because the patient is the sponsor.", style = MaterialTheme.typography.bodySmall)
    } else {
        NameFields(f.sponsorName) { n -> vm.updateForm { it.copy(sponsorName = n) } }
    }
    Field("10 Sponsor's SSN or DoD Benefits Number", f.sponsorId, { v -> vm.updateForm { it.copy(sponsorId = v) } }, keyboardType = KeyboardType.Number)

    SectionTitle("11 · Other health insurance")
    YesNo("Is the patient covered by any other health insurance (including travel or national health insurance overseas)?", f.hasOtherInsurance) { v -> vm.updateForm { it.copy(hasOtherInsurance = v) } }
    if (f.hasOtherInsurance == true) {
        MultiChips("11b Type of coverage", OhiType.entries, f.ohiTypes, { it.label }) { v -> vm.updateForm { it.copy(ohiTypes = v) } }
        if (OhiType.OTHER in f.ohiTypes) Field("Specify other coverage", f.ohiOtherSpecify, { v -> vm.updateForm { it.copy(ohiOtherSpecify = v) } })
        MultiChips("11c Overseas claims only", OverseasOhiType.entries, f.overseasOhiTypes, { it.label }) { v -> vm.updateForm { it.copy(overseasOhiTypes = v) } }
        InsuranceFields("Insurance 1", f.insurance1) { v -> vm.updateForm { it.copy(insurance1 = v) } }
        InsuranceFields("Insurance 2 (if any)", f.insurance2) { v -> vm.updateForm { it.copy(insurance2 = v) } }
        Text("Remember to attach the other insurer's Explanation of Benefits.", style = MaterialTheme.typography.bodySmall)
    }

    SectionTitle("13 · Overseas claims only")
    ChoiceChips("Payment in", PaymentCurrency.entries, f.paymentCurrency, { it.label }) { v -> vm.updateForm { it.copy(paymentCurrency = v) } }
    YesNo("Did you make payment to the provider?", f.paidProvider) { v -> vm.updateForm { it.copy(paidProvider = v) } }
}

@Composable
private fun InsuranceFields(title: String, e: InsuranceEntry, onChange: (InsuranceEntry) -> Unit) {
    Text(title, style = MaterialTheme.typography.labelLarge, modifier = Modifier.padding(top = 8.dp))
    Field("11d Name and address", e.nameAndAddress, { onChange(e.copy(nameAndAddress = it)) }, singleLine = false)
    Field("11e Identification number", e.idNumber, { onChange(e.copy(idNumber = it)) }, capitalization = KeyboardCapitalization.Characters)
    DateField("11f Effective date", e.effectiveDate, { onChange(e.copy(effectiveDate = it)) })
    YesNo("11g Drug coverage?", e.drugCoverage) { onChange(e.copy(drugCoverage = it)) }
}

@Composable
private fun DocumentsStep(state: NewClaimState, vm: NewClaimViewModel) {
    val context = LocalContext.current
    var scanError by remember { mutableStateOf<String?>(null) }

    val scanLauncher = rememberLauncherForActivityResult(ActivityResultContracts.StartIntentSenderForResult()) { result ->
        vm.addAttachments(DocumentScan.pages(result), AttachmentKind.IMAGE, "Scan")
    }
    val photoLauncher = rememberLauncherForActivityResult(ActivityResultContracts.PickMultipleVisualMedia(20)) { uris ->
        vm.addAttachments(uris, AttachmentKind.IMAGE, "Image")
    }
    val pdfLauncher = rememberLauncherForActivityResult(ActivityResultContracts.OpenMultipleDocuments()) { uris ->
        vm.addAttachments(uris, AttachmentKind.PDF, "PDF")
    }

    Text(
        "Add the itemized bill, pharmacy receipts and proof of payment, and mark each one as a bill or a receipt. In a combined packet they follow the form in the order shown here; as separate files, bills and receipts each get their own PDF.",
        style = MaterialTheme.typography.bodyMedium,
    )
    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.padding(vertical = 8.dp)) {
        Button(onClick = {
            DocumentScan.start(
                context.findActivity(),
                onReady = { scanLauncher.launch(IntentSenderRequest.Builder(it).build()) },
                onError = { scanError = "The document scanner is not available on this device right now. It is provided by Google Play services, which may need an update. You can add photos or PDFs instead." },
            )
        }) { Text("Scan pages") }
        OutlinedButton(onClick = {
            photoLauncher.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly))
        }) { Text("Add images") }
        OutlinedButton(onClick = { pdfLauncher.launch(arrayOf("application/pdf")) }) { Text("Add PDFs") }
    }
    if (state.busy) CircularProgressIndicator(Modifier.padding(8.dp))
    if (state.attachments.isEmpty() && !state.busy) {
        Text("Nothing attached yet. A claim without an itemized bill will be returned.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
    state.attachments.forEachIndexed { i, a ->
        Card(Modifier.fillMaxWidth().padding(vertical = 4.dp)) {
            Row(Modifier.padding(start = 16.dp), verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text(a.label, style = MaterialTheme.typography.titleSmall)
                    Text("${if (a.kind == AttachmentKind.PDF) "PDF" else "Image"} · ${PacketBuilder.formatSize(a.file.length())}", style = MaterialTheme.typography.bodySmall)
                }
                IconButton(enabled = i > 0, onClick = { vm.moveAttachment(i, -1) }) { Icon(Icons.Filled.KeyboardArrowUp, contentDescription = "Move ${a.label} up") }
                IconButton(enabled = i < state.attachments.lastIndex, onClick = { vm.moveAttachment(i, 1) }) { Icon(Icons.Filled.KeyboardArrowDown, contentDescription = "Move ${a.label} down") }
                IconButton(onClick = { vm.removeAttachment(a) }) { Icon(Icons.Filled.Delete, contentDescription = "Remove ${a.label}") }
            }
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.padding(horizontal = 16.dp)) {
                AttachmentCategory.entries.forEach { c ->
                    androidx.compose.material3.FilterChip(a.category == c, { vm.setCategory(a, c) }, { Text(c.label) })
                }
            }
        }
    }

    scanError?.let {
        AlertDialog(
            onDismissRequest = { scanError = null },
            title = { Text("Scanner unavailable") },
            text = { Text(it) },
            confirmButton = { TextButton(onClick = { scanError = null }) { Text("OK") } },
        )
    }
}

@Composable
private fun SignStep(state: NewClaimState, vm: NewClaimViewModel) {
    var strokes by remember { mutableStateOf<List<List<SignaturePoint>>>(emptyList()) }
    var signerName by remember { mutableStateOf("") }
    var save by remember { mutableStateOf(true) }
    var drawing by remember { mutableStateOf(false) }

    Text(
        "Block 12 certifies that the claim is correct. The form must be signed here before the packet can be created.",
        style = MaterialTheme.typography.bodyMedium,
    )
    Text(
        "The patient signs; a parent or guardian signs for a child under 18.",
        style = MaterialTheme.typography.bodySmall,
        modifier = Modifier.padding(top = 4.dp),
    )

    SectionTitle("12a · Signature")
    val applied = state.signature
    if (applied != null && !drawing) {
        Card(Modifier.fillMaxWidth()) {
            Column(Modifier.padding(12.dp)) {
                Text(if (state.signerName.isBlank()) "Signature applied" else "Signed by ${state.signerName}", style = MaterialTheme.typography.labelLarge)
                SignaturePreview(applied)
            }
        }
        TextButton(onClick = { strokes = emptyList(); signerName = state.signerName; drawing = true }) { Text("Sign again") }
    } else {
        if (state.savedSignatures.isNotEmpty() && !drawing) {
            Text("Use a saved signature", style = MaterialTheme.typography.bodyMedium)
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                state.savedSignatures.forEach { saved ->
                    androidx.compose.material3.FilterChip(false, { vm.useSavedSignature(saved) }, { Text(saved.signerName) })
                }
            }
            Text("Or sign below", style = MaterialTheme.typography.bodyMedium, modifier = Modifier.padding(top = 8.dp))
        }
        SignaturePad(strokes, { strokes = it }, Modifier.padding(vertical = 8.dp))
        Field("Signer's full name", signerName, { signerName = it }, supporting = "Used to label the saved signature")
        Row(
            Modifier.fillMaxWidth().toggleable(save, role = Role.Checkbox, onValueChange = { save = it }),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Checkbox(checked = save, onCheckedChange = null, modifier = Modifier.padding(vertical = 8.dp))
            Text("Save this signature (encrypted on this device) for future claims", Modifier.padding(start = 12.dp), style = MaterialTheme.typography.bodyMedium)
        }
        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Button(
                enabled = !Signature(strokes).isBlank && (!save || signerName.isNotBlank()),
                onClick = {
                    vm.applyDrawnSignature(Signature(strokes), signerName, save)
                    drawing = false
                },
            ) { Text("Use this signature") }
            OutlinedButton(enabled = strokes.isNotEmpty(), onClick = { strokes = emptyList() }) { Text("Clear") }
            if (drawing && applied != null) TextButton(onClick = { drawing = false }) { Text("Cancel") }
        }
    }

    SectionTitle("12b–c")
    DateField("12b Date signed", state.form.signedDate, { v -> vm.updateForm { it.copy(signedDate = v) } })
    Field("12c Signer's relationship to patient", state.form.signerRelationship, { v -> vm.updateForm { it.copy(signerRelationship = v) } }, supporting = "e.g. Self, Parent, Spouse")
    if (state.signErrors.isNotEmpty()) {
        Text("Still needed: ${state.signErrors.joinToString(", ")}", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.error)
    }
}

@Composable
private fun ReviewStep(app: Easy2642App, state: NewClaimState, vm: NewClaimViewModel) {
    val context = LocalContext.current
    var review by remember { mutableStateOf(false) }
    val claimsFolder = remember { app.settings.claimsFolder }
    val folderName = remember(claimsFolder) { folderLabel(app, claimsFolder) }
    val createDocument = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/pdf")) { uri ->
        if (uri != null) vm.exportTo(uri, "the location you chose")
    }
    val pickFolder = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocumentTree()) { uri ->
        if (uri != null) vm.exportToFolder(uri)
    }

    ChoiceChips("Save as", OutputMode.entries, state.outputMode, { it.label }) { m -> if (m != null && m != state.outputMode) vm.setOutputMode(m) }

    val outputs = state.outputs
    if (outputs.isEmpty()) {
        if (state.busy) {
            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(vertical = 24.dp)) {
                CircularProgressIndicator()
                Text("Building your packet…", modifier = Modifier.padding(start = 16.dp))
            }
        } else {
            Text("The packet has not been built.", modifier = Modifier.padding(vertical = 16.dp))
            Button(onClick = vm::buildPacket) { Text("Try again") }
        }
        return
    }
    val separate = outputs.size > 1

    state.savedTo?.let {
        Card(Modifier.fillMaxWidth().padding(vertical = 8.dp)) {
            Text("Saved to $it. The claim is in your tracker under \"Needs Filing\".", modifier = Modifier.padding(16.dp))
        }
    }
    outputs.forEach { out ->
        Column(Modifier.padding(vertical = 4.dp)) {
            Text(out.file.name, style = MaterialTheme.typography.titleSmall)
            Text("${out.label} · ${out.pageCount} page(s) · ${PacketBuilder.formatSize(out.sizeBytes)}", style = MaterialTheme.typography.bodyMedium)
            if (!out.withinLimit) {
                Text("Larger than the usual 10 MB portal limit even after compression. Remove some pages or split the claim.", color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall)
            }
        }
    }
    state.warnings.forEach { Text(it, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall) }
    if (state.formGaps.isNotEmpty()) {
        Text("Left blank on the form: ${state.formGaps.joinToString("; ")}. Go back to fill them in if they apply.", style = MaterialTheme.typography.bodySmall, modifier = Modifier.padding(top = 4.dp))
    }
    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.padding(vertical = 8.dp)) {
        Button(enabled = !state.busy, onClick = { review = true }) { Text(if (separate) "Save files" else "Save packet") }
        OutlinedButton(enabled = !state.busy, onClick = { vm.shareIntent()?.let(context::startActivity) }) { Text("Share") }
    }
    outputs.forEach { out ->
        if (separate) Text(out.label, style = MaterialTheme.typography.labelLarge, modifier = Modifier.padding(top = 8.dp))
        PdfPreview(out.file, state.outputsVersion, Modifier.padding(top = 8.dp))
    }

    if (review) {
        AlertDialog(
            onDismissRequest = { review = false },
            title = { Text("Review before saving") },
            text = {
                Column {
                    ReviewLine(
                        "Destination",
                        when {
                            folderName != null && separate -> "Claims folder \"$folderName\", or another folder you choose"
                            folderName != null -> "Claims folder \"$folderName\", or a location you choose"
                            separate -> "A folder you choose in the next step"
                            else -> "You choose in the next step"
                        },
                    )
                    outputs.forEach { out ->
                        ReviewLine(if (separate) out.label else "File name", "${out.file.name}\n${out.pageCount} page(s) · ${PacketBuilder.formatSize(out.sizeBytes)}")
                    }
                    if (separate) ReviewLine("Total", "${outputs.size} files · ${PacketBuilder.formatSize(outputs.sumOf { it.sizeBytes })}")
                }
            },
            confirmButton = {
                Column(horizontalAlignment = Alignment.End) {
                    if (folderName != null) {
                        TextButton(onClick = { review = false; vm.exportToFolder() }) { Text("Save to \"$folderName\"") }
                    }
                    if (separate) {
                        TextButton(onClick = { review = false; pickFolder.launch(null) }) { Text("Choose folder…") }
                    } else {
                        TextButton(onClick = { review = false; createDocument.launch(outputs.single().file.name) }) { Text("Choose location…") }
                    }
                    TextButton(onClick = { review = false }) { Text("Cancel") }
                }
            },
        )
    }
}

@Composable
private fun ReviewLine(label: String, value: String) {
    Column(Modifier.padding(vertical = 4.dp)) {
        Text(label, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Text(value, style = MaterialTheme.typography.bodyMedium)
    }
}
