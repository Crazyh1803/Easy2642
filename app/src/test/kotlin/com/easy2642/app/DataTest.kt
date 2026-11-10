package com.easy2642.app

import android.content.pm.PackageManager
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.easy2642.app.data.AppDatabase
import com.easy2642.app.data.Repository
import com.easy2642.app.data.Settings
import com.easy2642.core.ClaimRecord
import com.easy2642.core.ClaimState
import com.easy2642.core.Currency
import com.easy2642.core.Dependent
import com.easy2642.core.PersonName
import com.easy2642.core.Relationship
import com.easy2642.core.Sex
import com.easy2642.core.Sponsor
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * Exercises the Room schema and repository against a plain SQLite database.
 * SQLCipher's native library cannot load on a desktop JVM, so encryption at
 * rest is verified on a device (see docs/device-gate-checklist.md).
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], application = android.app.Application::class)
class DataTest {
    private val context = ApplicationProvider.getApplicationContext<android.app.Application>()
    private lateinit var db: AppDatabase
    private lateinit var repo: Repository

    private val sponsor = Sponsor(PersonName("Sampleton", "Casey", "R"), "0000000000", "Army", "Active", "PSC 2 Box 1234", "APO", "AE", "09012", "+49 631 5550100", "1986-01-02", Sex.M)
    private val child = Dependent("d1", Relationship.CHILD, PersonName("Sampleton", "Jordan", "A"), "2014-06-17", Sex.F, "1111111111")
    private val claim = ClaimRecord("c1", "d1", "Jordan Sampleton", "Klinikum Beispiel", "2026-02-14", 8505, Currency.EUR, notes = "ear infection")

    @Before fun open() {
        db = Room.inMemoryDatabaseBuilder(context, AppDatabase::class.java).allowMainThreadQueries().build()
        repo = Repository(db)
    }

    @After fun close() = db.close()

    @Test fun profilesRoundTrip() = runTest {
        assertNull(repo.sponsor.first())
        repo.saveSponsor(sponsor)
        repo.saveDependent(child)
        assertEquals(sponsor, repo.sponsor.first())
        assertEquals(listOf(child), repo.dependents.first())
        repo.saveDependent(child.copy(dob = "2014-06-18"))
        assertEquals("2014-06-18", repo.dependents.first().single().dob)
        repo.deleteDependent("d1")
        assertTrue(repo.dependents.first().isEmpty())
    }

    @Test fun claimStatePersists() = runTest {
        repo.saveClaim(claim)
        assertEquals(ClaimState.NEEDS_FILING, repo.claims.first().single().state)
        repo.saveClaim(repo.claim("c1")!!.withFiled(true, "2026-02-20"))
        repo.saveClaim(repo.claim("c1")!!.withReimbursed(true, "2026-04-01"))
        val stored = repo.claim("c1")!!
        assertEquals(ClaimState.COMPLETED, stored.state)
        assertEquals("2026-02-20", stored.filedDate)
        assertEquals("2026-04-01", stored.reimbursedDate)
        assertEquals(claim.copy(isFiled = true, filedDate = "2026-02-20", isReimbursed = true, reimbursedDate = "2026-04-01"), stored)
        repo.deleteClaim("c1")
        assertTrue(repo.claims.first().isEmpty())
    }

    @Test fun backupRoundTripReplacesEverything() = runTest {
        repo.saveSponsor(sponsor); repo.saveDependent(child); repo.saveClaim(claim)
        val backup = repo.exportBackup()
        assertTrue(backup.contains("Klinikum Beispiel"))
        repo.saveClaim(claim.copy(id = "c2", provider = "Other"))
        repo.deleteDependent("d1")
        repo.importBackup(backup)
        assertEquals(sponsor, repo.sponsor.first())
        assertEquals(listOf(child), repo.dependents.first())
        assertEquals(listOf(claim), repo.claims.first())
    }

    @Test fun signaturesSaveReplaceAndBackUp() = runTest {
        val first = repo.saveSignature("Casey Sampleton", SampleData.signature)
        val replaced = repo.saveSignature(" casey sampleton ", SampleData.signature.copy(strokes = SampleData.signature.strokes.take(1)))
        assertEquals(first.id, replaced.id)
        assertEquals(1, repo.signatures.first().single().signature.strokes.size)
        repo.saveSignature("Jordan Sampleton", SampleData.signature)
        val backup = repo.exportBackup()
        repo.deleteSignature(first.id)
        assertEquals(listOf("Jordan Sampleton"), repo.signatures.first().map { it.signerName })
        repo.importBackup(backup)
        assertEquals(listOf("Casey Sampleton", "Jordan Sampleton"), repo.signatures.first().map { it.signerName })
        assertEquals(SampleData.signature, repo.signatures.first().last().signature)
    }

    @Test fun version1BackupStillImports() = runTest {
        repo.saveSignature("Casey Sampleton", SampleData.signature)
        repo.importBackup("""{"formatVersion":1,"sponsor":null,"dependents":[],"claims":[]}""")
        assertTrue(repo.signatures.first().isEmpty())
    }

    @Test fun migrationFromVersion1KeepsData() = runTest {
        val name = "migration-test.db"
        context.deleteDatabase(name)
        context.openOrCreateDatabase(name, 0, null).use { v1 ->
            v1.execSQL("CREATE TABLE `sponsor` (`id` INTEGER NOT NULL, `lastName` TEXT NOT NULL, `firstName` TEXT NOT NULL, `middleInitial` TEXT NOT NULL, `dodIdOrSsn` TEXT NOT NULL, `branchOfService` TEXT NOT NULL, `dutyStatus` TEXT NOT NULL, `streetAddress` TEXT NOT NULL, `city` TEXT NOT NULL, `state` TEXT NOT NULL, `zipCode` TEXT NOT NULL, `phoneNumber` TEXT NOT NULL, `dob` TEXT NOT NULL, `sex` TEXT, PRIMARY KEY(`id`))")
            v1.execSQL("CREATE TABLE `dependents` (`id` TEXT NOT NULL, `relationship` TEXT NOT NULL, `lastName` TEXT NOT NULL, `firstName` TEXT NOT NULL, `middleInitial` TEXT NOT NULL, `dob` TEXT NOT NULL, `sex` TEXT, `dodId` TEXT NOT NULL, PRIMARY KEY(`id`))")
            v1.execSQL("CREATE TABLE `claims` (`id` TEXT NOT NULL, `patientId` TEXT NOT NULL, `patientName` TEXT NOT NULL, `provider` TEXT NOT NULL, `dateOfService` TEXT NOT NULL, `amountMinor` INTEGER NOT NULL, `currency` TEXT NOT NULL, `dossierUri` TEXT NOT NULL, `isPacketGenerated` INTEGER NOT NULL, `isFiled` INTEGER NOT NULL, `filedDate` TEXT, `isReimbursed` INTEGER NOT NULL, `reimbursedDate` TEXT, `notes` TEXT NOT NULL, PRIMARY KEY(`id`))")
            v1.execSQL("INSERT INTO claims VALUES ('c1','d1','Jordan Sampleton','Klinikum Beispiel','2026-02-14',8505,'EUR','',1,1,'2026-02-20',0,NULL,'ear infection')")
            v1.execSQL("INSERT INTO dependents VALUES ('d1','CHILD','Sampleton','Jordan','A','2014-06-17','F','1111111111')")
            v1.version = 1
        }
        val migrated = Room.databaseBuilder(context, AppDatabase::class.java, name)
            .addMigrations(AppDatabase.MIGRATION_1_2).allowMainThreadQueries().build()
        try {
            val r = Repository(migrated)
            assertEquals(claim.copy(isPacketGenerated = true, isFiled = true, filedDate = "2026-02-20"), r.claims.first().single())
            assertEquals(child, r.dependents.first().single())
            r.saveSignature("Casey", SampleData.signature)
            assertEquals(1, r.signatures.first().size)
        } finally {
            migrated.close()
            context.deleteDatabase(name)
        }
    }

    @Test fun invalidBackupChangesNothing() = runTest {
        repo.saveClaim(claim)
        assertTrue(runCatching { repo.importBackup("not json") }.isFailure)
        assertTrue(runCatching { repo.importBackup("""{"formatVersion":99}""") }.isFailure)
        assertEquals(listOf(claim), repo.claims.first())
    }

    @Test fun settingsHoldNoPiiAndClear() {
        val settings = Settings(context)
        assertFalse(settings.disclaimerAccepted)
        settings.disclaimerAccepted = true
        assertTrue(settings.disclaimerAccepted)
        settings.clear()
        assertFalse(settings.disclaimerAccepted)
        assertNull(settings.claimsFolder)
    }

    @Test fun outputModeAndTipYearPersist() {
        val settings = Settings(context)
        assertEquals(com.easy2642.core.OutputMode.COMBINED, settings.outputMode)
        assertEquals(0, settings.lastTipPromptYear)
        settings.outputMode = com.easy2642.core.OutputMode.SEPARATE
        settings.lastTipPromptYear = 2026
        val reread = Settings(context)
        assertEquals(com.easy2642.core.OutputMode.SEPARATE, reread.outputMode)
        assertEquals(2026, reread.lastTipPromptYear)
        assertFalse(com.easy2642.core.Oktoberfest.shouldPrompt(java.time.LocalDate.of(2026, 9, 25), reread.lastTipPromptYear))
        settings.clear()
        assertEquals(com.easy2642.core.OutputMode.COMBINED, Settings(context).outputMode)
    }

    @Test fun workFilesArePurged() {
        val f = java.io.File(WorkFiles.packets(context), "x.pdf").apply { writeText("x") }
        val g = java.io.File(WorkFiles.attachments(context), "y.img").apply { writeText("y") }
        WorkFiles.purge(context)
        assertFalse(f.exists()); assertFalse(g.exists())
    }

    @Test fun manifestRequestsNoPermissions() {
        val info = context.packageManager.getPackageInfo(context.packageName, PackageManager.GET_PERMISSIONS)
        val requested = info.requestedPermissions.orEmpty().toList()
        // AndroidX contributes one app-private signature permission; no system permission is allowed.
        assertEquals(emptyList<String>(), requested.filter { it.startsWith("android.permission.") })
        assertEquals(emptyList<String>(), requested.filterNot { it.startsWith(context.packageName) })
    }
}
