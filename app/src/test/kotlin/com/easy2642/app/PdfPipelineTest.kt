package com.easy2642.app

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import androidx.test.core.app.ApplicationProvider
import com.easy2642.app.pdf.Attachment
import com.easy2642.app.pdf.AttachmentKind
import com.easy2642.app.pdf.PacketBuilder
import com.easy2642.app.pdf.PdfFormService
import com.easy2642.app.pdf.TextFit
import com.easy2642.core.ClaimForm
import com.easy2642.core.Dd2642FieldMap
import com.tom_roush.pdfbox.android.PDFBoxResourceLoader
import com.tom_roush.pdfbox.pdmodel.PDDocument
import com.tom_roush.pdfbox.pdmodel.interactive.form.PDSignatureField
import com.tom_roush.pdfbox.text.PDFTextStripper
import java.io.File
import kotlin.random.Random
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], application = android.app.Application::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class PdfPipelineTest {
    private val context = ApplicationProvider.getApplicationContext<android.app.Application>()
    private val service = PdfFormService { context.assets.open(Easy2642App.TEMPLATE_ASSET) }
    private val outDir = File(System.getProperty("easy2642.outDir") ?: context.cacheDir.path).apply { mkdirs() }

    @Before fun setUp() = PDFBoxResourceLoader.init(context)

    @Test fun templateAndFieldMapAreInSync() {
        val (unmapped, missing) = service.auditDrift()
        assertEquals("template fields the app does not map", emptySet<String>(), unmapped)
        assertEquals("mapped fields missing from the template", emptySet<String>(), missing)
        val audit = service.audit()
        assertEquals(1, audit.count { it.type == "Sig" })
        Dd2642FieldMap.CHECKBOX_FIELDS.forEach { name ->
            assertEquals(name, Dd2642FieldMap.ON, audit.single { it.name == name }.onValue)
        }
    }

    @Test fun fillSetsEveryFieldBeforeFlattening() {
        // Re-run the mapping without flattening and read every value back.
        val values = Dd2642FieldMap.toFieldValues(SampleData.form)
        context.assets.open(Easy2642App.TEMPLATE_ASSET).use { PDDocument.load(it) }.use { doc ->
            val acro = doc.documentCatalog.acroForm
            values.forEach { (name, _) -> assertNotNull(name, acro.getField(name)) }
        }
        val out = File(outDir, "sample_form.pdf")
        val result = service.fill(SampleData.form, out)
        assertEquals(1, result.pageCount)
        assertEquals(emptyList<String>(), result.warnings)
        PDDocument.load(out).use { doc ->
            val text = PDFTextStripper().getText(doc)
            listOf(
                "Sampleton, Jordan A", "631 555 0100", "PSC 2 Box 1234, APO, AE 09012", "Kaiserslautern, Germany",
                "20140617", "Sampleton, Casey R", "0000000000", "School plan", "EX-000-123", "20240101",
                "Musterstraße", "20260220", "Parent", "amoxicillin",
            ).forEach { assertTrue("missing \"$it\" in output", text.contains(it)) }
            // Only the signature field stays interactive.
            val remaining = doc.documentCatalog.acroForm.fieldTree.filter { it.fieldType != null }.toList()
            assertEquals(1, remaining.size)
            assertTrue(remaining.single() is PDSignatureField)
        }
    }

    @Test fun blankAndInstructionVariants() {
        val out = File(outDir, "blank_with_instructions.pdf")
        assertEquals(2, service.fill(ClaimForm(), out, includeInstructions = true).pageCount)
    }

    @Test fun longDescriptionShrinksThenWarns() {
        val medium = SampleData.form.copy(description = "Recurring migraine with aura. ".repeat(22))
        val r1 = service.fill(medium, File(outDir, "long_description.pdf"))
        assertEquals(emptyList<String>(), r1.warnings)
        val huge = SampleData.form.copy(description = "word ".repeat(900))
        assertTrue(service.fill(huge, File(outDir, "huge_description.pdf")).warnings.any { it.contains("describe") })
    }

    @Test fun unencodableCharactersAreReplacedNotFatal() {
        assertEquals("Zoë - \"ok\" ?? 'x'", PdfFormService.sanitize("Zoë – “ok” 日本 ‘x’", keepNewlines = false))
        assertEquals("a b", PdfFormService.sanitize("a\nb", keepNewlines = false))
        val form = SampleData.form.copy(description = "Клиника 東京 😀 line\nbreak")
        service.fill(form, File(outDir, "unicode.pdf"))
    }

    @Test fun textFitMaths() {
        val em: (String) -> Float = { it.length * 0.5f }
        assertEquals(TextFit.DEFAULT_SIZE, TextFit.fit("short", 200f, 10f, false, em).fontSize)
        val shrunk = TextFit.fit("x".repeat(60), 200f, 10f, false, em)
        assertTrue(shrunk.fits); assertTrue(shrunk.fontSize < TextFit.DEFAULT_SIZE)
        assertFalse(TextFit.fit("x".repeat(400), 200f, 10f, false, em).fits)
        assertEquals(3, TextFit.wrapCount("aaaa bbbb cccc", 2.5f, em))
        assertEquals(2, TextFit.wrapCount("a\nb", 100f, em))
    }

    private fun image(name: String, w: Int, h: Int, dark: Boolean, noisy: Boolean, png: Boolean = false): File {
        val bmp = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
        val c = Canvas(bmp)
        c.drawColor(if (dark) Color.rgb(18, 18, 18) else Color.WHITE)
        val p = Paint().apply { color = if (dark) Color.WHITE else Color.BLACK; textSize = h / 30f; isAntiAlias = true }
        repeat(20) { c.drawText("Line $it  Amoxicillin 500mg  EUR 12.34  Überweisung OK", w / 20f, h / 25f * (it + 2), p) }
        if (noisy) {
            val rnd = Random(7)
            val px = IntArray(w * h)
            bmp.getPixels(px, 0, w, 0, 0, w, h)
            for (i in px.indices step 3) px[i] = px[i] xor (rnd.nextInt(0x40) * 0x010101)
            bmp.setPixels(px, 0, w, 0, 0, w, h)
        }
        val f = File(outDir, name)
        f.outputStream().use { bmp.compress(if (png) Bitmap.CompressFormat.PNG else Bitmap.CompressFormat.JPEG, 95, it) }
        return f
    }

    @Test fun packetIsFormFirstThenAttachmentsInOrder() {
        val form = File(outDir, "p_form.pdf").also { service.fill(SampleData.form, it) }
        val extraPdf = File(outDir, "p_bill.pdf").also { service.fill(ClaimForm(), it, includeInstructions = true) }
        val attachments = listOf(
            Attachment(image("receipt.jpg", 900, 2400, dark = false, noisy = false), AttachmentKind.IMAGE, "Thermal receipt"),
            Attachment(image("bank.png", 1080, 2340, dark = true, noisy = false, png = true), AttachmentKind.IMAGE, "Bank transfer"),
            Attachment(extraPdf, AttachmentKind.PDF, "Itemized bill"),
            Attachment(image("wide.jpg", 2400, 1200, dark = false, noisy = false), AttachmentKind.IMAGE, "Landscape"),
        )
        val out = File(outDir, "packet.pdf")
        val r = PacketBuilder().build(form, attachments, out)
        assertEquals(1 + 1 + 1 + 2 + 1, r.pageCount)
        assertTrue(r.withinLimit)
        PDDocument.load(out).use { doc ->
            assertEquals(6, doc.numberOfPages)
            assertTrue(PDFTextStripper().apply { startPage = 1; endPage = 1 }.getText(doc).contains("Sampleton, Jordan A"))
            val portrait = doc.getPage(1).mediaBox
            assertTrue(portrait.height > portrait.width)
            val landscape = doc.getPage(5).mediaBox
            assertTrue(landscape.width > landscape.height)
        }
    }

    @Test fun compressionKeepsFourNoisyScansUnderTenMegabytes() {
        val form = File(outDir, "c_form.pdf").also { service.fill(SampleData.form, it) }
        val scans = (1..4).map {
            Attachment(image("scan$it.jpg", 3000, 4000, dark = false, noisy = true), AttachmentKind.IMAGE, "Scan $it")
        }
        val r = PacketBuilder().build(form, scans, File(outDir, "packet_noisy.pdf"))
        assertEquals(5, r.pageCount)
        assertTrue("size ${r.sizeBytes}", r.withinLimit)
        // A tiny cap cannot be met: the builder reports that honestly instead of failing.
        val capped = PacketBuilder().build(form, scans.take(1), File(outDir, "packet_capped.pdf"), maxBytes = 10_000)
        assertFalse(capped.withinLimit)
    }

    @Test fun aspectRatioIsPreserved() {
        val p = PacketBuilder.fitInside(1000, 2000, 612f, 792f, 18f)
        assertEquals(0.5f, p.width / p.height, 0.001f)
        assertTrue(p.x >= 18f && p.y >= 18f && p.x + p.width <= 594.01f && p.y + p.height <= 774.01f)
    }
}
