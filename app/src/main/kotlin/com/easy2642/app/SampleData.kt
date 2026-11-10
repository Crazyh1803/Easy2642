package com.easy2642.app

import com.easy2642.core.CareType
import com.easy2642.core.ClaimForm
import com.easy2642.core.InsuranceEntry
import com.easy2642.core.OhiType
import com.easy2642.core.OverseasOhiType
import com.easy2642.core.PaymentCurrency
import com.easy2642.core.PersonName
import com.easy2642.core.Phone
import com.easy2642.core.Relationship
import com.easy2642.core.Sex
import com.easy2642.core.Signature
import com.easy2642.core.SignaturePoint

/** Synthetic data for the developer harness, tests and store screenshots. Never real PII. */
object SampleData {
    val form = ClaimForm(
        patientName = PersonName("Sampleton", "Jordan", "A"),
        primaryPhone = Phone("+49", "631 555 0100"),
        secondaryPhone = Phone("555", "010-0199"),
        patientAddress = "PSC 2 Box 1234, APO, AE 09012",
        overseasLocation = "Kaiserslautern, Germany",
        relationship = Relationship.CHILD,
        patientDob = "2014-06-17",
        sex = Sex.F,
        accidentRelated = false,
        workRelated = false,
        description = "Acute otitis media, right ear. Fever and ear pain for two days; seen at the " +
            "paediatric urgent care clinic. Examination, tympanometry and a prescription for " +
            "amoxicillin (Diagnose: H66.9). Follow-up visit after seven days — no further treatment needed.",
        careType = CareType.OUTPATIENT,
        urgentCare = true,
        sponsorName = PersonName("Sampleton", "Casey", "R"),
        sponsorId = "0000000000",
        hasOtherInsurance = true,
        ohiTypes = setOf(OhiType.PRIVATE, OhiType.OTHER),
        ohiOtherSpecify = "School plan",
        overseasOhiTypes = setOf(OverseasOhiType.TRAVEL_INSURANCE),
        insurance1 = InsuranceEntry("Example Mutual, 1 Sample Way, Springfield, VA 22150", "EX-000-123", "2024-01-01", true),
        insurance2 = InsuranceEntry("Muster Versicherung AG, Musterstraße 1, 67655 Kaiserslautern", "MV-555", "2025-03-15", false),
        signedDate = "2026-02-20",
        signerRelationship = "Parent",
        paymentCurrency = PaymentCurrency.US_DOLLAR,
        paidProvider = true,
    )

    /** A synthetic scribble standing in for a handwritten signature. */
    val signature = Signature(
        listOf(
            (0..40).map { SignaturePoint(20f + it * 9f, 80f + 45f * kotlin.math.sin(it / 2.5).toFloat()) },
            (0..12).map { SignaturePoint(60f + it * 25f, 140f - it * 2f) },
        ),
    )
}
