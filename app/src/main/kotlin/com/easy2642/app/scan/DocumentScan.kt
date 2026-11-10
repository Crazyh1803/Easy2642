package com.easy2642.app.scan

import android.app.Activity
import android.content.IntentSender
import android.net.Uri
import androidx.activity.result.ActivityResult
import com.google.mlkit.vision.documentscanner.GmsDocumentScannerOptions
import com.google.mlkit.vision.documentscanner.GmsDocumentScanning
import com.google.mlkit.vision.documentscanner.GmsDocumentScanningResult

/**
 * On-device document scanning through the ML Kit Document Scanner. The scanner UI,
 * camera access, edge detection, deskew and clean-up all run inside Google Play
 * services, so this app needs neither the CAMERA nor any network permission.
 */
object DocumentScan {
    private const val PAGE_LIMIT = 20

    private val options = GmsDocumentScannerOptions.Builder()
        .setScannerMode(GmsDocumentScannerOptions.SCANNER_MODE_FULL)
        .setResultFormats(GmsDocumentScannerOptions.RESULT_FORMAT_JPEG)
        .setGalleryImportAllowed(false)
        .setPageLimit(PAGE_LIMIT)
        .build()

    fun start(activity: Activity, onReady: (IntentSender) -> Unit, onError: (Exception) -> Unit) {
        GmsDocumentScanning.getClient(options)
            .getStartScanIntent(activity)
            .addOnSuccessListener(onReady)
            .addOnFailureListener(onError)
    }

    /** Page image URIs from a finished scan; empty if the user cancelled. */
    fun pages(result: ActivityResult): List<Uri> {
        if (result.resultCode != Activity.RESULT_OK) return emptyList()
        return GmsDocumentScanningResult.fromActivityResultIntent(result.data)?.pages?.map { it.imageUri }.orEmpty()
    }
}
