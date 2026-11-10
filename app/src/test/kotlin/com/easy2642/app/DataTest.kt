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
