package com.easy2642.core

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import com.easy2642.core.Dd2642FieldMap as F

class CoreTest {
    private val sponsor = Sponsor(
        name = PersonName("Testerson", "Alex", "Q"),
        dodIdOrSsn = "0000000000",
        streetAddress = "PSC 1 Box 100",
        city = "APO",
        state = "AE",
        zipCode = "09000",
        phoneNumber = "+49 631 5550100",
        dob = "1985-03-09",
        sex = Sex.M,
    )
    private val child = Dependent("d1", Relationship.CHILD, PersonName("Testerson", "Sam", ""), "2015-11-02", Sex.F, "1111111111")

    @Test fun fieldSetsAreDisjointAndComplete() {
        assertTrue((F.CHECKBOX_FIELDS intersect F.TEXT_FIELDS).isEmpty())
        assertEquals(61, F.ALL_FIELDS.size)
        assertEquals(F.ALL_FIELDS, F.toFieldValues(ClaimForm()).keys)
        assertFalse(F.SIGNATURE in F.ALL_FIELDS)
    }

    @Test fun blankFormLeavesEveryBoxOffAndTextEmpty() {
        val v = F.toFieldValues(ClaimForm())
        F.CHECKBOX_FIELDS.forEach { assertEquals(it, F.OFF, v[it]) }
        F.TEXT_FIELDS.forEach { assertEquals(it, "", v[it]) }
    }

    @Test fun dependentPrefillMapsBlocks1To10() {
        val v = F.toFieldValues(ClaimPrefill.forDependent(sponsor, child))
        assertEquals("Testerson, Sam", v[F.PATIENT_NAME])
        assertEquals("+49", v[F.PHONE_PRIMARY_AREA])
        assertEquals("631 5550100", v[F.PHONE_PRIMARY_NUMBER])
        assertEquals("PSC 1 Box 100, APO, AE 09000", v[F.PATIENT_ADDRESS])
        assertEquals(F.ON, v[F.REL_CHILD])
        assertEquals(1, listOf(F.REL_SELF, F.REL_SPOUSE, F.REL_CHILD, F.REL_STEPCHILD, F.REL_FORMER_SPOUSE, F.REL_OTHER).count { v[it] == F.ON })
        assertEquals("20151102", v[F.PATIENT_DOB])
        assertEquals(F.ON, v[F.SEX_FEMALE]); assertEquals(F.OFF, v[F.SEX_MALE])
        assertEquals("Testerson, Alex Q", v[F.SPONSOR_NAME])
        assertEquals("0000000000", v[F.SPONSOR_ID])
    }

    @Test fun sponsorAsPatientWritesSame() {
        val v = F.toFieldValues(ClaimPrefill.forSponsor(sponsor))
        assertEquals(F.ON, v[F.REL_SELF])
        assertEquals("Same", v[F.SPONSOR_NAME])
        assertEquals("19850309", v[F.PATIENT_DOB])
    }

    @Test fun yesNoPairsAreExclusive() {
        val yes = F.toFieldValues(ClaimForm(accidentRelated = true, workRelated = false, paidProvider = true))
        assertEquals(F.ON, yes[F.ACCIDENT_YES]); assertEquals(F.OFF, yes[F.ACCIDENT_NO])
        assertEquals(F.OFF, yes[F.WORK_YES]); assertEquals(F.ON, yes[F.WORK_NO])
        assertEquals(F.ON, yes[F.PROOF_YES]); assertEquals(F.OFF, yes[F.PROOF_NO])
    }

    @Test fun insuranceDetailsOnlyPrintWhenOhiIsYes() {
        val form = ClaimForm(
            hasOtherInsurance = false,
            ohiTypes = setOf(OhiType.PRIVATE, OhiType.OTHER),
            ohiOtherSpecify = "x",
            insurance1 = InsuranceEntry("Acme", "A1", "2024-01-01", true),
        )
        val no = F.toFieldValues(form)
        assertEquals(F.ON, no[F.OHI_NO]); assertEquals(F.OFF, no[F.OHI_PRIVATE]); assertEquals("", no[F.INS1_NAME])
        val yes = F.toFieldValues(form.copy(hasOtherInsurance = true))
        assertEquals(F.ON, yes[F.OHI_YES]); assertEquals(F.ON, yes[F.OHI_PRIVATE]); assertEquals(F.ON, yes[F.OHI_OTHER])
        assertEquals("x", yes[F.OHI_OTHER_SPECIFY]); assertEquals("20240101", yes[F.INS1_DATE])
        assertEquals(F.ON, yes[F.INS1_DRUG_YES]); assertEquals(F.OFF, yes[F.INS2_DRUG_NO])
    }

    @Test fun otherRelationshipSpecifyOnlyWhenOther() {
        assertEquals("", F.toFieldValues(ClaimForm(relationship = Relationship.SPOUSE, relationshipOther = "ward"))[F.REL_OTHER_SPECIFY])
        assertEquals("ward", F.toFieldValues(ClaimForm(relationship = Relationship.OTHER, relationshipOther = "ward"))[F.REL_OTHER_SPECIFY])
    }

    @Test fun phoneParsing() {
        assertEquals(Phone("555", "123-4567"), Phone.parse("(555) 123-4567"))
        assertEquals(Phone("", "5551234567"), Phone.parse("5551234567"))
        assertEquals(Phone("", ""), Phone.parse(""))
    }

    @Test fun fileNames() {
        assertEquals("Claim_DD2642_Testerson_KlinikumKaiserslautern_20260214.pdf",
            ClaimFileName.build("Testerson", "Klinikum Kaiserslautern", "2026-02-14"))
        assertEquals("Claim_DD2642_Muller_DrSchafersPraxis_20260214.pdf",
            ClaimFileName.build("Müller", "Dr. Schäfer's Praxis", "2026-02-14"))
        assertEquals("Claim_DD2642_Patient_Provider_undated.pdf", ClaimFileName.build(" ", "../..", ""))
        assertFalse(ClaimFileName.build("a/b", "c\\d:e", "2026-01-01").contains(Regex("[/\\\\:]")))
    }

    @Test fun amounts() {
        assertEquals(123450L, parseAmountMinor("1,234.50"))
        assertEquals(123450L, parseAmountMinor("1.234,5"))
        assertEquals(1200L, parseAmountMinor("12"))
        assertEquals(100000L, parseAmountMinor("1,000"))
        assertNull(parseAmountMinor("abc")); assertNull(parseAmountMinor(""))
        assertEquals("EUR 85.05", claim().copy(amountMinor = 8505, currency = Currency.EUR).formattedAmount())
    }

    private fun claim() = ClaimRecord("c1", "d1", "Sam Testerson", "Klinikum", "2026-02-14", notes = "ear infection")

    @Test fun claimStateTransitions() {
        val c = claim()
        assertEquals(ClaimState.NEEDS_FILING, c.state)
        val filed = c.withFiled(true, "2026-02-20")
        assertEquals(ClaimState.AWAITING_REIMBURSEMENT, filed.state); assertEquals("2026-02-20", filed.filedDate)
        val done = filed.withReimbursed(true, "2026-03-30")
        assertEquals(ClaimState.COMPLETED, done.state); assertEquals("2026-02-20", done.filedDate)
        val direct = c.withReimbursed(true, "2026-03-30")
        assertTrue(direct.isFiled); assertEquals("2026-03-30", direct.filedDate)
        val unfiled = done.withFiled(false, "x")
        assertEquals(ClaimState.NEEDS_FILING, unfiled.state); assertNull(unfiled.reimbursedDate)
    }

    @Test fun filters() {
        val c = claim()
        assertTrue(ClaimFilter().matches(c))
        assertTrue(ClaimFilter(patientId = "d1", state = ClaimState.NEEDS_FILING, query = "KLINIK").matches(c))
        assertTrue(ClaimFilter(query = "ear").matches(c))
        assertFalse(ClaimFilter(patientId = SPONSOR_PATIENT_ID).matches(c))
        assertFalse(ClaimFilter(state = ClaimState.COMPLETED).matches(c))
        assertFalse(ClaimFilter(query = "dentist").matches(c))
    }
}
