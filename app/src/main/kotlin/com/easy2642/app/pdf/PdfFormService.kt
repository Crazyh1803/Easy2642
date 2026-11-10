package com.easy2642.app.pdf

import com.easy2642.core.ClaimForm
import com.easy2642.core.Dd2642FieldMap
import com.easy2642.core.Signature
import com.tom_roush.pdfbox.pdmodel.PDPage
import com.tom_roush.pdfbox.pdmodel.PDPageContentStream
import com.tom_roush.pdfbox.pdmodel.common.PDRectangle
import com.tom_roush.pdfbox.cos.COSName
import com.tom_roush.pdfbox.pdmodel.PDDocument
import com.tom_roush.pdfbox.pdmodel.font.PDType1Font
import com.tom_roush.pdfbox.pdmodel.interactive.form.PDAcroForm
import com.tom_roush.pdfbox.pdmodel.interactive.form.PDCheckBox
import com.tom_roush.pdfbox.pdmodel.interactive.form.PDField
import com.tom_roush.pdfbox.pdmodel.interactive.form.PDTerminalField
import com.tom_roush.pdfbox.pdmodel.interactive.form.PDTextField
import java.io.File
import java.io.InputStream

data class FieldInfo(
    val name: String,
    /** "Tx", "Btn" or "Sig". */
    val type: String,
    val onValue: String?,
    val multiline: Boolean,
    val width: Float,
    val height: Float,
)

data class FillResult(val pageCount: Int, val warnings: List<String>)

/**
 * Fills the bundled DD Form 2642 AcroForm template entirely on-device.
 *
 * [openTemplate] must return a fresh stream of `assets/forms/dd2642_template.pdf`.
 */
class PdfFormService(private val openTemplate: () -> InputStream) {

    /** Catalogue of every terminal field in the template (the "AcroForm key audit"). */
    fun audit(): List<FieldInfo> = openTemplate().use { PDDocument.load(it) }.use { doc ->
        doc.requireForm().fieldTree.filterIsInstance<PDTerminalField>().map { f ->
            val rect = f.widgets.firstOrNull()?.rectangle
            FieldInfo(
                name = f.fullyQualifiedName,
                type = f.fieldType ?: "?",
                onValue = (f as? PDCheckBox)?.onValue,
                multiline = (f as? PDTextField)?.isMultiline == true,
                width = rect?.width ?: 0f,
                height = rect?.height ?: 0f,
            )
        }
    }

    /** Field names the template has that the app does not map, and vice versa. Both empty when in sync. */
    fun auditDrift(): Pair<Set<String>, Set<String>> {
        val inTemplate = audit().filter { it.type != "Sig" }.map { it.name }.toSet()
        return (inTemplate - Dd2642FieldMap.ALL_FIELDS) to (Dd2642FieldMap.ALL_FIELDS - inTemplate)
    }

    /**
     * Writes a filled, flattened form to [out].
     *
     * With a [signature], its strokes are drawn into block 12a and the signature field is
     * removed, leaving no interactive fields. Without one, block 12a stays an open signature
     * field (used only by the developer harness; the claim flow always signs).
     *
     * @param includeInstructions keep the official instruction page in front of the form page.
     */
    fun fill(form: ClaimForm, out: File, signature: Signature? = null, includeInstructions: Boolean = false): FillResult {
        val warnings = mutableListOf<String>()
        openTemplate().use { PDDocument.load(it) }.use { doc ->
            val acro = doc.requireForm()
            acro.setNeedAppearances(false)
            acro.defaultResources.put(HELV, PDType1Font.HELVETICA)

            val values = Dd2642FieldMap.toFieldValues(form)
            val filled = mutableListOf<PDField>()
            for ((name, value) in values) {
                when (val field = acro.getField(name)) {
                    is PDCheckBox -> if (value == Dd2642FieldMap.ON) field.check() else field.unCheck()
                    is PDTextField -> setText(field, value, warnings)
                    else -> error("Template is missing field $name")
                }
                filled += acro.getField(name)
            }
            val signed = signature != null && !signature.isBlank
            if (signed) {
                val sigField = acro.getField(Dd2642FieldMap.SIGNATURE) ?: error("Template is missing the signature field")
                val widget = sigField.widgets.first()
                val page = widget.page ?: doc.getPage(doc.numberOfPages - 1)
                val rect = widget.rectangle
                acro.flatten(filled + sigField, false)
                drawSignature(doc, page, rect, signature!!)
            } else {
                acro.flatten(filled, false)
            }
            if (!includeInstructions && doc.numberOfPages > 1) doc.removePage(0)
            out.parentFile?.mkdirs()
            doc.save(out)
            return FillResult(doc.numberOfPages, warnings)
        }
    }

    private fun drawSignature(doc: PDDocument, page: PDPage, rect: PDRectangle, signature: Signature) {
        val boxW = rect.width - 2 * SIG_PAD_X
        val boxH = rect.height - 2 * SIG_PAD_Y
        val left = rect.lowerLeftX + SIG_PAD_X
        val top = rect.upperRightY - SIG_PAD_Y
        PDPageContentStream(doc, page, PDPageContentStream.AppendMode.APPEND, true, true).use { cs ->
            cs.setStrokingColor(0f, 0f, 0f)
            cs.setLineWidth(0.7f)
            cs.setLineCapStyle(1)
            cs.setLineJoinStyle(1)
            for (stroke in signature.fitInto(boxW, boxH)) {
                val first = stroke.first()
                cs.moveTo(left + first.x, top - first.y)
                // A single point still leaves a dot thanks to the round line cap.
                if (stroke.size == 1) cs.lineTo(left + first.x + 0.01f, top - first.y)
                for (pt in stroke.drop(1)) cs.lineTo(left + pt.x, top - pt.y)
                cs.stroke()
            }
        }
    }

    private fun setText(field: PDTextField, raw: String, warnings: MutableList<String>) {
        val rect = field.widgets.first().rectangle
        val text = sanitize(raw, keepNewlines = field.isMultiline)
        val fit = TextFit.fit(text, rect.width, rect.height, field.isMultiline) { s ->
            PDType1Font.HELVETICA.getStringWidth(s) / 1000f
        }
        if (!fit.fits) warnings += "\"${field.partialName.substringBefore('[')}\" is too long for its box and may be clipped."
        field.defaultAppearance = "/Helv ${fit.fontSize} Tf 0 g"
        field.value = text
    }

    private fun PDDocument.requireForm(): PDAcroForm =
        documentCatalog.acroForm ?: error("Template has no AcroForm")

    companion object {
        private val HELV: COSName = COSName.getPDFName("Helv")
        private const val SIG_PAD_X = 4f
        private const val SIG_PAD_Y = 1f

        private val replacements = mapOf(
            '‘' to "'", '’' to "'", '“' to "\"", '”' to "\"",
            '–' to "-", '—' to "-", '…' to "...", ' ' to " ", '\t' to " ",
        )

        /** Maps text onto what Helvetica/WinAnsi can draw; anything else becomes '?'. */
        fun sanitize(raw: String, keepNewlines: Boolean): String {
            val sb = StringBuilder(raw.length)
            for (ch in raw.replace("\r\n", "\n").replace('\r', '\n')) {
                when {
                    ch == '\n' -> sb.append(if (keepNewlines) '\n' else ' ')
                    ch in replacements -> sb.append(replacements.getValue(ch))
                    encodable(ch) -> sb.append(ch)
                    else -> sb.append('?')
                }
            }
            return sb.toString()
        }

        private fun encodable(ch: Char): Boolean = try {
            !ch.isISOControl() && PDType1Font.HELVETICA.encode(ch.toString()).isNotEmpty()
        } catch (_: Exception) {
            false
        }
    }
}
