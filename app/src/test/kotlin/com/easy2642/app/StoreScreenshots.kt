package com.easy2642.app

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.net.Uri
import android.os.Looper
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.runtime.Composable
import androidx.lifecycle.ViewModelProvider
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.easy2642.app.data.AppDatabase
import com.easy2642.app.data.Repository
import com.easy2642.app.pdf.AttachmentCategory
import com.easy2642.app.pdf.AttachmentKind
import com.easy2642.app.ui.DashboardScreen
import com.easy2642.app.ui.Easy2642Theme
import com.easy2642.app.ui.FamilyScreen
import com.easy2642.app.ui.NewClaimScreen
import com.easy2642.app.ui.NewClaimViewModel
import com.easy2642.app.ui.SettingsScreen
import com.easy2642.app.ui.Steps
import com.easy2642.app.ui.pdfRasterizer
import com.easy2642.app.ui.renderPdf
import com.easy2642.core.ClaimRecord
import com.easy2642.core.Currency
import com.easy2642.core.Dependent
import com.easy2642.core.PersonName
import com.easy2642.core.Relationship
import com.easy2642.core.Sex
import com.easy2642.core.Sponsor
import com.github.takahirom.roborazzi.captureRoboImage
import com.tom_roush.pdfbox.pdmodel.PDDocument
import com.tom_roush.pdfbox.rendering.PDFRenderer
import java.io.File
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assume.assumeTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/**
 * Renders the Play Store screenshots from the real screens with synthetic data.
 * Skipped unless a target folder is given:
 *
 *   ./gradlew :app:testDebugUnitTest --tests '*StoreScreenshots*' -PscreenshotDir=docs/store/screenshots
 *
 * 1080 x 1920 px. Everything shown is made up; no real person's details appear.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], application = Easy2642App::class, qualifiers = "w360dp-h640dp-xxhdpi")
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class StoreScreenshots {
    private val outDir = System.getProperty("easy2642.screenshotDir")?.let(::File)
    private val app = ApplicationProvider.getApplicationContext<Easy2642App>()
    private lateinit var db: AppDatabase

    private val sponsor = Sponsor(
        PersonName("Sampleton", "Casey", "R"), "0000000000", "Army", "Active duty",
        "PSC 2 Box 1234", "APO", "AE", "09012", "+49 631 555 0100", "1986-01-02", Sex.M,
    )
    private val jordan = Dependent("d-jordan", Relationship.CHILD, PersonName("Sampleton", "Jordan", "A"), "2014-06-17", Sex.F, "0000000000")
    private val riley = Dependent("d-riley", Relationship.SPOUSE, PersonName("Sampleton", "Riley", "M"), "1987-09-23", Sex.F, "0000000000")

    @Before fun setUp() {
        assumeTrue("pass -PscreenshotDir=<dir> to render store screenshots", outDir != null)
        outDir!!.mkdirs()
        db = Room.inMemoryDatabaseBuilder(app, AppDatabase::class.java).allowMainThreadQueries().build()
        val repo = Repository(db)
        app.repositoryOverride = repo
        app.settings.disclaimerAccepted = true
        // The platform PdfRenderer is not available off-device; rasterise the same PDF with PDFBox.
        pdfRasterizer = { file, width, maxPages ->
            PDDocument.load(file).use { doc ->
                val renderer = PDFRenderer(doc)
                (0 until minOf(doc.numberOfPages, maxPages)).map { renderer.renderImage(it, width / doc.getPage(it).mediaBox.width) }
            }
        }
        runBlocking {
            repo.saveSponsor(sponsor)
            repo.saveDependent(jordan)
            repo.saveDependent(riley)
            repo.saveSignature("Casey Sampleton", SampleData.signature)
            listOf(
                ClaimRecord("c1", jordan.id, "Jordan Sampleton", "Kinderklinik Beispiel", "2026-02-14", 8505, Currency.EUR, isPacketGenerated = true, notes = "Ear infection, urgent care visit"),
                ClaimRecord("c2", riley.id, "Riley Sampleton", "Zahnarztpraxis Muster", "2026-01-28", 21000, Currency.EUR, isPacketGenerated = true, isFiled = true, filedDate = "2026-02-02"),
                ClaimRecord("c3", SPONSOR, "Casey Sampleton", "Apotheke am Markt", "2026-01-09", 3460, Currency.EUR, isPacketGenerated = true, isFiled = true, filedDate = "2026-01-12", isReimbursed = true, reimbursedDate = "2026-02-20", notes = "Prescription refill"),
                ClaimRecord("c4", jordan.id, "Jordan Sampleton", "Example Pediatrics", "2025-12-03", 12000, Currency.USD, isPacketGenerated = true, isFiled = true, filedDate = "2025-12-05", isReimbursed = true, reimbursedDate = "2026-01-15"),
            ).forEach { repo.saveClaim(it) }
        }
    }

    @After fun tearDown() {
        if (::db.isInitialized) db.close()
        app.repositoryOverride = null
        pdfRasterizer = ::renderPdf
    }

    /** Lets coroutines, Room and background work settle between interactions. */
    private fun settle(rounds: Int = 12) {
        repeat(rounds) {
            shadowOf(Looper.getMainLooper()).idle()
            Thread.sleep(60)
        }
        shadowOf(Looper.getMainLooper()).idle()
    }

    private fun show(content: @Composable () -> Unit): ComponentActivity {
        val activity = Robolectric.buildActivity(ComponentActivity::class.java).setup().get()
        activity.setContent { Easy2642Theme { content() } }
        settle()
        return activity
    }

    private fun ComponentActivity.shoot(name: String) {
        settle()
        window.decorView.captureRoboImage(File(outDir, name))
    }

    private fun sampleImage(name: String, title: String): Uri {
        val bmp = Bitmap.createBitmap(900, 1300, Bitmap.Config.ARGB_8888)
        val c = Canvas(bmp)
        c.drawColor(Color.WHITE)
        val p = Paint().apply { color = Color.BLACK; textSize = 44f; isAntiAlias = true }
        c.drawText(title, 60f, 120f, p)
        p.textSize = 30f
        listOf("SAMPLE - not a real document", "Patient: Jordan Sampleton", "Date: 14.02.2026", "Consultation            45,05", "Tympanometry            25,00", "Medication              15,00", "Total EUR               85,05")
            .forEachIndexed { i, line -> c.drawText(line, 60f, 220f + i * 60f, p) }
        val f = File(app.cacheDir, name)
        f.outputStream().use { bmp.compress(Bitmap.CompressFormat.JPEG, 90, it) }
        return Uri.fromFile(f)
    }

    @Test fun dashboard() = show { DashboardScreen(app, onNewClaim = {}) }.shoot("1_claim_tracker.png")

    @Test fun family() = show { FamilyScreen(app) }.shoot("2_family_profiles.png")

    @Test fun settings() = show { SettingsScreen(app, onOpenHarness = null) }.shoot("7_settings.png")

    @Test fun claimWizard() {
        val activity = show { NewClaimScreen(app, onClose = {}, onOpenFamily = {}) }
        val vm = ViewModelProvider(activity)[NewClaimViewModel::class.java]
        settle()

        vm.selectPatient(jordan.id)
        vm.update { it.copy(provider = "Kinderklinik Beispiel", dateOfService = "2026-02-14", amountText = "85.05", currency = Currency.EUR, notes = "Ear infection, urgent care visit") }
        activity.shoot("3_new_claim_patient_and_visit.png")

        vm.updateForm {
            SampleData.form.copy(
                patientName = it.patientName, primaryPhone = it.primaryPhone, patientAddress = it.patientAddress,
                relationship = it.relationship, patientDob = it.patientDob, sex = it.sex,
                sponsorName = it.sponsorName, sponsorId = it.sponsorId,
                signedDate = "2026-02-20", signerRelationship = "Parent",
            )
        }
        vm.goTo(Steps.FORM)
        activity.shoot("4_form_details.png")

        vm.addAttachments(listOf(sampleImage("bill.jpg", "RECHNUNG / INVOICE")), AttachmentKind.IMAGE, "Scan")
        settle(25)
        vm.addAttachments(listOf(sampleImage("receipt.jpg", "ZAHLUNGSBELEG / RECEIPT")), AttachmentKind.IMAGE, "Image")
        settle(25)
        vm.state.value.attachments.getOrNull(1)?.let { vm.setCategory(it, AttachmentCategory.RECEIPT) }
        vm.goTo(Steps.DOCUMENTS)
        activity.shoot("5_supporting_documents.png")

        vm.useSavedSignature(runBlocking { app.repository.signatures.let { flow -> flow.first() }.first() })
        vm.goTo(Steps.SIGN)
        activity.shoot("6_sign.png")

        vm.goTo(Steps.REVIEW)
        var waited = 0
        while (vm.state.value.outputs.isEmpty() && waited++ < 200) settle(2)
        check(vm.state.value.outputs.isNotEmpty()) { "packet was not built: ${vm.state.value.message}" }
        settle(40)
        activity.shoot("8_review_and_save.png")
    }

    private companion object {
        const val SPONSOR = com.easy2642.core.SPONSOR_PATIENT_ID
    }
}
