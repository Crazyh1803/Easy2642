package com.easy2642.app.pdf

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Matrix
import android.media.ExifInterface
import com.tom_roush.pdfbox.pdmodel.PDDocument
import com.tom_roush.pdfbox.pdmodel.PDPage
import com.tom_roush.pdfbox.pdmodel.PDPageContentStream
import com.tom_roush.pdfbox.pdmodel.common.PDRectangle
import com.tom_roush.pdfbox.pdmodel.graphics.image.JPEGFactory
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.File
import kotlin.math.max
import kotlin.math.roundToInt

enum class AttachmentKind { IMAGE, PDF }

/** Which of the separate files an attachment belongs to. */
enum class AttachmentCategory(val label: String, val filePart: String) {
    BILL("Bill", "Bill"),
    RECEIPT("Receipt / proof of payment", "Receipt"),
}

/** A supporting document already copied into the app's private cache. */
data class Attachment(
    val file: File,
    val kind: AttachmentKind,
    val label: String,
    val category: AttachmentCategory = AttachmentCategory.BILL,
)

data class PacketResult(
    /** What this file holds, e.g. "Claim packet", "DD Form 2642", "Bill". */
    val label: String,
    val file: File,
    val pageCount: Int,
    val sizeBytes: Long,
    val withinLimit: Boolean,
    /** JPEG quality finally used for image pages, or null if there were none. */
    val jpegQuality: Int?,
)

/**
 * Builds claim PDFs. Combined: the filled DD Form 2642 first, then every
 * attachment in order. Separate: the form, the bill pages and the receipt
 * pages as three files. Image pages are recompressed with progressively
 * stronger settings until each file fits the size limit.
 */
class PacketBuilder {

    private data class Level(val maxEdgePx: Int, val quality: Int)

    /**
     * Writes [formPdf] (if any) followed by [attachments] to [out].
     * @param label stored on the result for display.
     */
    fun build(
        formPdf: File?,
        attachments: List<Attachment>,
        out: File,
        maxBytes: Long = MAX_BYTES,
        label: String = "Claim packet",
    ): PacketResult {
        require(formPdf != null || attachments.isNotEmpty()) { "Nothing to write" }
        val hasImages = attachments.any { it.kind == AttachmentKind.IMAGE }
        var last: PacketResult? = null
        for (level in if (hasImages) LEVELS else LEVELS.take(1)) {
            val pages = assemble(formPdf, attachments, out, level)
            val size = out.length()
            last = PacketResult(label, out, pages, size, size <= maxBytes, level.quality.takeIf { hasImages })
            if (size <= maxBytes) break
        }
        return last!!
    }

    /**
     * Writes the form, the bill and the receipt/proof of payment as separate PDFs in [dir].
     * A category with no attachments produces no file.
     * @param nameFor file name for a part ("Form", "Bill", "Receipt").
     */
    fun buildSeparate(
        formPdf: File,
        attachments: List<Attachment>,
        dir: File,
        maxBytes: Long = MAX_BYTES,
        nameFor: (String) -> String,
    ): List<PacketResult> = buildList {
        add(build(formPdf, emptyList(), File(dir, nameFor("Form")), maxBytes, "DD Form 2642"))
        for (category in AttachmentCategory.entries) {
            val items = attachments.filter { it.category == category }
            if (items.isNotEmpty()) add(build(null, items, File(dir, nameFor(category.filePart)), maxBytes, category.label))
        }
    }

    private fun assemble(formPdf: File?, attachments: List<Attachment>, out: File, level: Level): Int {
        val sources = mutableListOf<PDDocument>()
        try {
            (formPdf?.let { PDDocument.load(it) } ?: PDDocument()).use { doc ->
                for (a in attachments) {
                    when (a.kind) {
                        AttachmentKind.IMAGE -> addImagePage(doc, a.file, level)
                        AttachmentKind.PDF -> {
                            val src = PDDocument.load(a.file).also { sources += it }
                            for (page in src.pages) doc.importPage(page)
                        }
                    }
                }
                out.parentFile?.mkdirs()
                doc.save(out)
                return doc.numberOfPages
            }
        } finally {
            sources.forEach { runCatching { it.close() } }
        }
    }

    private fun addImagePage(doc: PDDocument, file: File, level: Level) {
        val bitmap = decode(file, level.maxEdgePx)
        try {
            val jpeg = ByteArrayOutputStream().also { bitmap.compress(Bitmap.CompressFormat.JPEG, level.quality, it) }
            val image = JPEGFactory.createFromStream(doc, ByteArrayInputStream(jpeg.toByteArray()))
            val landscape = bitmap.width > bitmap.height
            val box = if (landscape) PDRectangle(PDRectangle.LETTER.height, PDRectangle.LETTER.width) else PDRectangle.LETTER
            val page = PDPage(box)
            doc.addPage(page)
            val placement = fitInside(bitmap.width, bitmap.height, box.width, box.height, MARGIN)
            PDPageContentStream(doc, page).use { cs ->
                cs.drawImage(image, placement.x, placement.y, placement.width, placement.height)
            }
        } finally {
            bitmap.recycle()
        }
    }

    /** Decodes at reduced resolution, applies EXIF rotation and flattens transparency onto white. */
    private fun decode(file: File, maxEdge: Int): Bitmap {
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeFile(file.path, bounds)
        var sample = 1
        while (max(bounds.outWidth, bounds.outHeight) / (sample * 2) >= maxEdge) sample *= 2
        val raw = BitmapFactory.decodeFile(file.path, BitmapFactory.Options().apply { inSampleSize = sample })
            ?: throw IllegalArgumentException("${file.name} is not a readable image")

        val scale = (maxEdge.toFloat() / max(raw.width, raw.height)).coerceAtMost(1f)
        val matrix = Matrix().apply {
            postScale(scale, scale)
            postRotate(exifRotation(file))
        }
        val transformed = Bitmap.createBitmap(raw, 0, 0, raw.width, raw.height, matrix, true)
        val flat = Bitmap.createBitmap(transformed.width, transformed.height, Bitmap.Config.ARGB_8888)
        Canvas(flat).apply {
            drawColor(Color.WHITE)
            drawBitmap(transformed, 0f, 0f, null)
        }
        if (transformed !== raw) transformed.recycle()
        raw.recycle()
        return flat
    }

    private fun exifRotation(file: File): Float = runCatching {
        when (ExifInterface(file.path).getAttributeInt(ExifInterface.TAG_ORIENTATION, ExifInterface.ORIENTATION_NORMAL)) {
            ExifInterface.ORIENTATION_ROTATE_90 -> 90f
            ExifInterface.ORIENTATION_ROTATE_180 -> 180f
            ExifInterface.ORIENTATION_ROTATE_270 -> 270f
            else -> 0f
        }
    }.getOrDefault(0f)

    data class Placement(val x: Float, val y: Float, val width: Float, val height: Float)

    companion object {
        /** Common claims-portal upload limit. */
        const val MAX_BYTES = 10L * 1024 * 1024
        private const val MARGIN = 18f

        private val LEVELS = listOf(
            Level(2200, 80), Level(2000, 70), Level(1700, 60), Level(1400, 50), Level(1200, 40), Level(1000, 30),
        )

        /** Largest centred rectangle with the image's aspect ratio inside the page margins. */
        fun fitInside(imgW: Int, imgH: Int, pageW: Float, pageH: Float, margin: Float): Placement {
            val availW = pageW - 2 * margin
            val availH = pageH - 2 * margin
            val scale = minOf(availW / imgW, availH / imgH)
            val w = imgW * scale
            val h = imgH * scale
            return Placement((pageW - w) / 2, (pageH - h) / 2, w, h)
        }

        fun pageCount(pdf: File): Int = PDDocument.load(pdf).use { it.numberOfPages }

        fun formatSize(bytes: Long): String =
            if (bytes >= 1024 * 1024) "%.1f MB".format(bytes / 1048576.0) else "${(bytes / 1024.0).roundToInt()} KB"
    }
}
