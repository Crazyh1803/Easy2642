package com.easy2642.core

import kotlinx.serialization.Serializable

/** Block 4 of DD Form 2642 (SEP 2024). */
@Serializable
enum class Relationship(val label: String) {
    SELF("Self"),
    SPOUSE("Spouse"),
    CHILD("Natural or adopted child"),
    STEPCHILD("Stepchild"),
    FORMER_SPOUSE("Former spouse"),
    OTHER("Other"),
}

@Serializable
enum class Sex(val label: String) { M("Male"), F("Female") }

/** Block 8b. */
@Serializable
enum class CareType(val label: String) {
    INPATIENT("Inpatient"),
    OUTPATIENT("Outpatient"),
    DAY_SURGERY("Day surgery"),
    PHARMACY("Pharmacy"),
}

/** Block 11b. */
@Serializable
enum class OhiType(val label: String) {
    EMPLOYMENT("Employment (group)"),
    PRIVATE("Private (non-group)"),
    MEDICARE("Medicare"),
    STUDENT_PLAN("Student plan"),
    MEDICARE_SUPPLEMENTAL("Medicare supplemental"),
    PRESCRIPTION_PLAN("Prescription plan"),
    OTHER("Other"),
}

/** Block 11c (overseas claims only). */
@Serializable
enum class OverseasOhiType(val label: String) {
    TRAVEL_INSURANCE("Travel insurance"),
    MEDICARE_ADVANTAGE("Medicare Advantage"),
    VA_FOREIGN_MEDICAL("VA Foreign Medical Program"),
}

/** Block 13. */
@Serializable
enum class PaymentCurrency(val label: String) { US_DOLLAR("US dollar"), LOCAL_FOREIGN("Local foreign") }

@Serializable
data class PersonName(
    val lastName: String = "",
    val firstName: String = "",
    val middleInitial: String = "",
) {
    /** "Last, First M" as the form asks for. */
    fun formatted(): String {
        val given = listOf(firstName.trim(), middleInitial.trim()).filter { it.isNotEmpty() }.joinToString(" ")
        return listOf(lastName.trim(), given).filter { it.isNotEmpty() }.joinToString(", ")
    }

    fun display(): String = listOf(firstName.trim(), lastName.trim()).filter { it.isNotEmpty() }.joinToString(" ")
}

@Serializable
data class Phone(val areaCode: String = "", val number: String = "") {
    companion object {
        private val split = Regex("""^\s*\(?(\+?\d{1,4})\)?[\s\-./]+(.+)$""")

        /** Splits "(555) 123-4567" or "+49 631 1234" into area/country code and the remainder. */
        fun parse(raw: String): Phone {
            val m = split.find(raw) ?: return Phone("", raw.trim())
            return Phone(m.groupValues[1], m.groupValues[2].trim())
        }
    }
}

@Serializable
data class InsuranceEntry(
    val nameAndAddress: String = "",
    val idNumber: String = "",
    /** ISO yyyy-MM-dd, or blank. */
    val effectiveDate: String = "",
    val drugCoverage: Boolean? = null,
) {
    val isBlank: Boolean
        get() = nameAndAddress.isBlank() && idNumber.isBlank() && effectiveDate.isBlank() && drugCoverage == null
}

/**
 * Everything printed on page 2 of DD Form 2642 (SEP 2024), except the
 * signature in block 12a which the claimant adds by hand or with a CAC.
 * Dates are ISO yyyy-MM-dd strings; null booleans leave both boxes blank.
 */
@Serializable
data class ClaimForm(
    val patientName: PersonName = PersonName(),
    val primaryPhone: Phone = Phone(),
    val secondaryPhone: Phone = Phone(),
    val patientAddress: String = "",
    val overseasLocation: String = "",
    val relationship: Relationship? = null,
    val relationshipOther: String = "",
    val patientDob: String = "",
    val sex: Sex? = null,
    val accidentRelated: Boolean? = null,
    val workRelated: Boolean? = null,
    val description: String = "",
    val careType: CareType? = null,
    val telemedicine: Boolean = false,
    val urgentCare: Boolean = false,
    val telemedicineAudioOnly: Boolean = false,
    val audioOnlyReason: String = "",
    val sponsorName: PersonName = PersonName(),
    val sponsorId: String = "",
    val hasOtherInsurance: Boolean? = null,
    val ohiTypes: Set<OhiType> = emptySet(),
    val ohiOtherSpecify: String = "",
    val overseasOhiTypes: Set<OverseasOhiType> = emptySet(),
    val insurance1: InsuranceEntry = InsuranceEntry(),
    val insurance2: InsuranceEntry = InsuranceEntry(),
    val signedDate: String = "",
    val signerRelationship: String = "",
    val paymentCurrency: PaymentCurrency? = null,
    val paidProvider: Boolean? = null,
)

@Serializable
data class Sponsor(
    val name: PersonName = PersonName(),
    val dodIdOrSsn: String = "",
    /** Not printed on the SEP 2024 form; kept for the family record. */
    val branchOfService: String = "",
    /** Not printed on the SEP 2024 form; kept for the family record. */
    val dutyStatus: String = "",
    val streetAddress: String = "",
    val city: String = "",
    val state: String = "",
    val zipCode: String = "",
    val phoneNumber: String = "",
    val dob: String = "",
    val sex: Sex? = null,
) {
    fun formattedAddress(): String {
        val cityLine = listOf(
            city.trim(),
            listOf(state.trim(), zipCode.trim()).filter { it.isNotEmpty() }.joinToString(" "),
        ).filter { it.isNotEmpty() }.joinToString(", ")
        return listOf(streetAddress.trim(), cityLine).filter { it.isNotEmpty() }.joinToString(", ")
    }
}

@Serializable
data class Dependent(
    val id: String,
    val relationship: Relationship = Relationship.SPOUSE,
    val name: PersonName = PersonName(),
    val dob: String = "",
    val sex: Sex? = null,
    val dodId: String = "",
)

@Serializable
enum class Currency { USD, EUR, GBP, JPY, KRW, OTHER }

@Serializable
data class ClaimRecord(
    val id: String,
    /** [SPONSOR_PATIENT_ID] or a [Dependent.id]. */
    val patientId: String,
    val patientName: String,
    val provider: String,
    val dateOfService: String,
    /** Amount in minor units (cents) to avoid floating point drift. */
    val amountMinor: Long = 0,
    val currency: Currency = Currency.USD,
    val dossierUri: String = "",
    val isPacketGenerated: Boolean = false,
    val isFiled: Boolean = false,
    val filedDate: String? = null,
    val isReimbursed: Boolean = false,
    val reimbursedDate: String? = null,
    val notes: String = "",
) {
    val state: ClaimState
        get() = when {
            isReimbursed -> ClaimState.COMPLETED
            isFiled -> ClaimState.AWAITING_REIMBURSEMENT
            else -> ClaimState.NEEDS_FILING
        }

    fun formattedAmount(): String = "%s %d.%02d".format(currency.name, amountMinor / 100, amountMinor % 100)

    /** Toggling "filed" off also clears reimbursement, which cannot precede filing. */
    fun withFiled(filed: Boolean, today: String): ClaimRecord =
        if (filed) copy(isFiled = true, filedDate = filedDate ?: today)
        else copy(isFiled = false, filedDate = null, isReimbursed = false, reimbursedDate = null)

    /** Marking reimbursed implies the claim was filed. */
    fun withReimbursed(reimbursed: Boolean, today: String): ClaimRecord =
        if (reimbursed) copy(
            isFiled = true,
            filedDate = filedDate ?: today,
            isReimbursed = true,
            reimbursedDate = reimbursedDate ?: today,
        ) else copy(isReimbursed = false, reimbursedDate = null)
}

enum class ClaimState(val label: String) {
    NEEDS_FILING("Needs Filing"),
    AWAITING_REIMBURSEMENT("Awaiting Reimbursement"),
    COMPLETED("Completed"),
}

const val SPONSOR_PATIENT_ID = "sponsor"

/** Dashboard filter; null members mean "All". */
data class ClaimFilter(
    val patientId: String? = null,
    val state: ClaimState? = null,
    val query: String = "",
) {
    fun matches(c: ClaimRecord): Boolean {
        if (patientId != null && c.patientId != patientId) return false
        if (state != null && c.state != state) return false
        val q = query.trim()
        if (q.isEmpty()) return true
        return c.provider.contains(q, ignoreCase = true) || c.notes.contains(q, ignoreCase = true)
    }
}

/** Parses "1,234.50", "1234,5" or "12" into minor units; null if not a valid amount. */
fun parseAmountMinor(raw: String): Long? {
    var s = raw.trim().replace(" ", "")
    if (s.isEmpty()) return null
    val lastSep = maxOf(s.lastIndexOf('.'), s.lastIndexOf(','))
    val decimals = if (lastSep >= 0) s.length - lastSep - 1 else 0
    val (whole, frac) = if (lastSep >= 0 && decimals in 1..2) {
        s.substring(0, lastSep) to s.substring(lastSep + 1).padEnd(2, '0')
    } else {
        s to "00"
    }
    s = whole.replace(",", "").replace(".", "")
    if (s.isEmpty() || !s.all(Char::isDigit) || !frac.all(Char::isDigit)) return null
    return s.toLongOrNull()?.let { it * 100 + frac.toLong() }
}

@Serializable
data class FamilyBackup(
    val formatVersion: Int = 1,
    val sponsor: Sponsor? = null,
    val dependents: List<Dependent> = emptyList(),
    val claims: List<ClaimRecord> = emptyList(),
)
