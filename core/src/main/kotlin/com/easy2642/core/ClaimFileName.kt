package com.easy2642.core

import java.text.Normalizer

object ClaimFileName {
    private const val MAX_PART = 40

    /** `Claim_DD2642_[PatientLastName]_[Provider]_[YYYYMMDD].pdf` */
    fun build(patientLastName: String, provider: String, dateOfServiceIso: String): String {
        val date = Dd2642FieldMap.formDate(dateOfServiceIso).filter(Char::isDigit).ifEmpty { "undated" }
        return "Claim_DD2642_${part(patientLastName, "Patient")}_${part(provider, "Provider")}_$date.pdf"
    }

    /** ASCII letters and digits only, so the name is safe on every document provider. */
    private fun part(raw: String, fallback: String): String {
        val ascii = Normalizer.normalize(raw.replace("ß", "ss").replace(Regex("['’]"), ""), Normalizer.Form.NFD)
            .replace(Regex("\\p{M}+"), "")
        val words = ascii.split(Regex("[^A-Za-z0-9]+")).filter { it.isNotEmpty() }
        val joined = words.joinToString("") { it.replaceFirstChar(Char::uppercaseChar) }.take(MAX_PART)
        return joined.ifEmpty { fallback }
    }
}

/** Pre-fills blocks 1-6, 9 and 10 from the family store. */
object ClaimPrefill {
    fun forSponsor(s: Sponsor, base: ClaimForm = ClaimForm()): ClaimForm = base.copy(
        patientName = s.name,
        primaryPhone = Phone.parse(s.phoneNumber),
        patientAddress = s.formattedAddress(),
        relationship = Relationship.SELF,
        relationshipOther = "",
        patientDob = s.dob,
        sex = s.sex,
        sponsorName = s.name,
        sponsorId = s.dodIdOrSsn,
        signerRelationship = "Self",
    )

    fun forDependent(s: Sponsor, d: Dependent, base: ClaimForm = ClaimForm()): ClaimForm = base.copy(
        patientName = d.name,
        primaryPhone = Phone.parse(s.phoneNumber),
        patientAddress = s.formattedAddress(),
        relationship = d.relationship,
        relationshipOther = "",
        patientDob = d.dob,
        sex = d.sex,
        sponsorName = s.name,
        sponsorId = s.dodIdOrSsn,
        signerRelationship = "",
    )
}
