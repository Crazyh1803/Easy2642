package com.easy2642.core

/**
 * Fully qualified AcroForm field names of DD Form 2642, SEP 2024 edition
 * (form updated 20250827), and the mapping from [ClaimForm] onto them.
 *
 * Checkbox fields take [ON] or [OFF]; text fields take their literal value.
 */
object Dd2642FieldMap {
    const val ON = "1"
    const val OFF = "Off"

    private const val P = "form1[0].Page2[0]."

    // Blocks 1-3
    const val PATIENT_NAME = P + "No1-4[0].Personal[0].Name[0]"
    const val PHONE_PRIMARY_AREA = P + "No1-4[0].Personal[0].Phone[0].primary1[0]"
    const val PHONE_PRIMARY_NUMBER = P + "No1-4[0].Personal[0].Phone[0].primary2[0]"
    const val PHONE_SECONDARY_AREA = P + "No1-4[0].Personal[0].Phone[0].secondary1[0]"
    const val PHONE_SECONDARY_NUMBER = P + "No1-4[0].Personal[0].Phone[0].secondary2[0]"
    const val PATIENT_ADDRESS = P + "No1-4[0].Address[0].PAddress[0]"
    const val OVERSEAS_LOCATION = P + "No1-4[0].Address[0].overseas[0]"

    // Block 4
    const val REL_SELF = P + "No1-4[0].Relationship[0].a4[0]"
    const val REL_STEPCHILD = P + "No1-4[0].Relationship[0].b4[0]"
    const val REL_SPOUSE = P + "No1-4[0].Relationship[0].c4[0]"
    const val REL_FORMER_SPOUSE = P + "No1-4[0].Relationship[0].d4[0]"
    const val REL_CHILD = P + "No1-4[0].Relationship[0].e4[0]"
    const val REL_OTHER = P + "No1-4[0].Relationship[0].f4[0]"
    const val REL_OTHER_SPECIFY = P + "No1-4[0].Relationship[0].specify4[0]"

    // Blocks 5-7
    const val PATIENT_DOB = P + "No5[0].patient_dob[0]"
    const val SEX_MALE = P + "No6[0].male[0]"
    const val SEX_FEMALE = P + "No6[0].female[0]"
    const val ACCIDENT_YES = P + "No7[0].yes_accident[0]"
    const val ACCIDENT_NO = P + "No7[0].no_accident[0]"
    const val WORK_YES = P + "No7[0].yes_work[0]"
    const val WORK_NO = P + "No7[0].no_work[0]"

    // Block 8
    const val DESCRIPTION = P + "No8A[0].describe[0]"
    const val CARE_INPATIENT = P + "No8BC[0].inpatient[0]"
    const val CARE_OUTPATIENT = P + "No8BC[0].outpatient[0]"
    const val CARE_DAY_SURGERY = P + "No8BC[0].day_surgery[0]"
    const val CARE_PHARMACY = P + "No8BC[0].pharmacy[0]"
    const val TELEMEDICINE = P + "No8BC[0].telemedicine[0]"
    const val URGENT_CARE = P + "No8BC[0].urgent_care[0]"
    const val TELEMEDICINE_AUDIO = P + "No8BC[0].telemedicine_audio[0]"
    const val AUDIO_REASON = P + "No8BC[0].audio_reason[0]"

    // Blocks 9-10
    const val SPONSOR_NAME = P + "No9[0].sponsor_fs_name[0]"
    const val SPONSOR_ID = P + "No10[0].ssn_dbn[0]"

    // Block 11
    const val OHI_YES = P + "No11A[0].TxtBox[0].yes11[0]"
    const val OHI_NO = P + "No11A[0].TxtBox[0].no11[0]"
    const val OHI_EMPLOYMENT = P + "No11BC[0].Checks[0].#subform[0].employment11b[0]"
    const val OHI_PRIVATE = P + "No11BC[0].Checks[0].#subform[0].private11b[0]"
    const val OHI_MEDICARE = P + "No11BC[0].Checks[0].#subform[1].medicare11b[0]"
    const val OHI_STUDENT_PLAN = P + "No11BC[0].Checks[0].#subform[1].student_plan11b[0]"
    const val OHI_MEDICARE_SUPP = P + "No11BC[0].Checks[0].#subform[2].medicare_supp11b[0]"
    const val OHI_PRESCRIPTION = P + "No11BC[0].Checks[0].#subform[2].prescription11b[0]"
    const val OHI_OTHER = P + "No11BC[0].Checks[0].other11b[0]"
    const val OHI_OTHER_SPECIFY = P + "No11BC[0].Checks[0].specify11b[0]"
    const val OHI_TRAVEL = P + "No11BC[0].travel_insurance11c[0]"
    const val OHI_MEDICARE_ADVANTAGE = P + "No11BC[0].medicare_advantage11c[0]"
    const val OHI_VA_FOREIGN = P + "No11BC[0].va_foreign_medical11c[0]"
    const val INS1_NAME = P + "No11C[0].SecondRow[0].insurance1_name[0]"
    const val INS2_NAME = P + "No11C[0].SecondRow[0].insurance2_name[0]"
    const val INS1_ID = P + "No11C[0].ThirdRow[0].insurance1_id[0]"
    const val INS2_ID = P + "No11C[0].ThirdRow[0].insurance2_id[0]"
    const val INS1_DATE = P + "No11C[0].FourthRow[0].insurance1_date[0]"
    const val INS2_DATE = P + "No11C[0].FourthRow[0].insurance2_date[0]"
    const val INS1_DRUG_YES = P + "No11C[0].FifthRow[0].yes_ins1[0]"
    const val INS1_DRUG_NO = P + "No11C[0].FifthRow[0].no_ins1[0]"
    const val INS2_DRUG_YES = P + "No11C[0].FifthRow[0].yes_ins2[0]"
    const val INS2_DRUG_NO = P + "No11C[0].FifthRow[0].no_ins2[0]"

    // Blocks 12-13
    /** Left untouched: the claimant signs by hand or with a CAC. */
    const val SIGNATURE = P + "No12-13[0].No12[0].SignatureField[0]"
    const val SIGNED_DATE = P + "No12-13[0].No12[0].signedDate[0]"
    const val SIGNER_RELATIONSHIP = P + "No12-13[0].No12[0].Relationship[0]"
    const val PAY_US_DOLLAR = P + "No12-13[0].No13[0].us_dollar[0]"
    const val PAY_LOCAL_FOREIGN = P + "No12-13[0].No13[0].local_foreign[0]"
    const val PROOF_YES = P + "No12-13[0].No13[0].proof_yes[0]"
    const val PROOF_NO = P + "No12-13[0].No13[0].proof_no[0]"

    val CHECKBOX_FIELDS: Set<String> = setOf(
        REL_SELF, REL_STEPCHILD, REL_SPOUSE, REL_FORMER_SPOUSE, REL_CHILD, REL_OTHER,
        SEX_MALE, SEX_FEMALE, ACCIDENT_YES, ACCIDENT_NO, WORK_YES, WORK_NO,
        CARE_INPATIENT, CARE_OUTPATIENT, CARE_DAY_SURGERY, CARE_PHARMACY,
        TELEMEDICINE, URGENT_CARE, TELEMEDICINE_AUDIO,
        OHI_YES, OHI_NO, OHI_EMPLOYMENT, OHI_PRIVATE, OHI_MEDICARE, OHI_STUDENT_PLAN,
        OHI_MEDICARE_SUPP, OHI_PRESCRIPTION, OHI_OTHER, OHI_TRAVEL, OHI_MEDICARE_ADVANTAGE, OHI_VA_FOREIGN,
        INS1_DRUG_YES, INS1_DRUG_NO, INS2_DRUG_YES, INS2_DRUG_NO,
        PAY_US_DOLLAR, PAY_LOCAL_FOREIGN, PROOF_YES, PROOF_NO,
    )

    val TEXT_FIELDS: Set<String> = setOf(
        PATIENT_NAME, PHONE_PRIMARY_AREA, PHONE_PRIMARY_NUMBER, PHONE_SECONDARY_AREA, PHONE_SECONDARY_NUMBER,
        PATIENT_ADDRESS, OVERSEAS_LOCATION, REL_OTHER_SPECIFY, PATIENT_DOB, DESCRIPTION, AUDIO_REASON,
        SPONSOR_NAME, SPONSOR_ID, OHI_OTHER_SPECIFY,
        INS1_NAME, INS2_NAME, INS1_ID, INS2_ID, INS1_DATE, INS2_DATE,
        SIGNED_DATE, SIGNER_RELATIONSHIP,
    )

    /** Every fillable field the app writes. The template must contain exactly these plus [SIGNATURE]. */
    val ALL_FIELDS: Set<String> = CHECKBOX_FIELDS + TEXT_FIELDS

    /** ISO yyyy-MM-dd to the form's YYYYMMDD. Anything else is passed through unchanged. */
    fun formDate(iso: String): String {
        val t = iso.trim()
        return if (Regex("""\d{4}-\d{2}-\d{2}""").matches(t)) t.replace("-", "") else t
    }

    private fun box(on: Boolean) = if (on) ON else OFF

    private fun MutableMap<String, String>.yesNo(yesField: String, noField: String, value: Boolean?) {
        this[yesField] = box(value == true)
        this[noField] = box(value == false)
    }

    /** Values for every field in [ALL_FIELDS]. */
    fun toFieldValues(f: ClaimForm): Map<String, String> = buildMap {
        put(PATIENT_NAME, f.patientName.formatted())
        put(PHONE_PRIMARY_AREA, f.primaryPhone.areaCode.trim())
        put(PHONE_PRIMARY_NUMBER, f.primaryPhone.number.trim())
        put(PHONE_SECONDARY_AREA, f.secondaryPhone.areaCode.trim())
        put(PHONE_SECONDARY_NUMBER, f.secondaryPhone.number.trim())
        put(PATIENT_ADDRESS, f.patientAddress.trim())
        put(OVERSEAS_LOCATION, f.overseasLocation.trim())

        put(REL_SELF, box(f.relationship == Relationship.SELF))
        put(REL_STEPCHILD, box(f.relationship == Relationship.STEPCHILD))
        put(REL_SPOUSE, box(f.relationship == Relationship.SPOUSE))
        put(REL_FORMER_SPOUSE, box(f.relationship == Relationship.FORMER_SPOUSE))
        put(REL_CHILD, box(f.relationship == Relationship.CHILD))
        put(REL_OTHER, box(f.relationship == Relationship.OTHER))
        put(REL_OTHER_SPECIFY, if (f.relationship == Relationship.OTHER) f.relationshipOther.trim() else "")

        put(PATIENT_DOB, formDate(f.patientDob))
        put(SEX_MALE, box(f.sex == Sex.M))
        put(SEX_FEMALE, box(f.sex == Sex.F))
        yesNo(ACCIDENT_YES, ACCIDENT_NO, f.accidentRelated)
        yesNo(WORK_YES, WORK_NO, f.workRelated)

        put(DESCRIPTION, f.description.trim())
        put(CARE_INPATIENT, box(f.careType == CareType.INPATIENT))
        put(CARE_OUTPATIENT, box(f.careType == CareType.OUTPATIENT))
        put(CARE_DAY_SURGERY, box(f.careType == CareType.DAY_SURGERY))
        put(CARE_PHARMACY, box(f.careType == CareType.PHARMACY))
        put(TELEMEDICINE, box(f.telemedicine))
        put(URGENT_CARE, box(f.urgentCare))
        put(TELEMEDICINE_AUDIO, box(f.telemedicineAudioOnly))
        put(AUDIO_REASON, if (f.telemedicineAudioOnly) f.audioOnlyReason.trim() else "")

        // Form instruction 9: if sponsor and patient are the same, enter "same".
        put(SPONSOR_NAME, if (f.relationship == Relationship.SELF) "Same" else f.sponsorName.formatted())
        put(SPONSOR_ID, f.sponsorId.trim())

        yesNo(OHI_YES, OHI_NO, f.hasOtherInsurance)
        val ohi = f.hasOtherInsurance == true
        put(OHI_EMPLOYMENT, box(ohi && OhiType.EMPLOYMENT in f.ohiTypes))
        put(OHI_PRIVATE, box(ohi && OhiType.PRIVATE in f.ohiTypes))
        put(OHI_MEDICARE, box(ohi && OhiType.MEDICARE in f.ohiTypes))
        put(OHI_STUDENT_PLAN, box(ohi && OhiType.STUDENT_PLAN in f.ohiTypes))
        put(OHI_MEDICARE_SUPP, box(ohi && OhiType.MEDICARE_SUPPLEMENTAL in f.ohiTypes))
        put(OHI_PRESCRIPTION, box(ohi && OhiType.PRESCRIPTION_PLAN in f.ohiTypes))
        put(OHI_OTHER, box(ohi && OhiType.OTHER in f.ohiTypes))
        put(OHI_OTHER_SPECIFY, if (ohi && OhiType.OTHER in f.ohiTypes) f.ohiOtherSpecify.trim() else "")
        put(OHI_TRAVEL, box(ohi && OverseasOhiType.TRAVEL_INSURANCE in f.overseasOhiTypes))
        put(OHI_MEDICARE_ADVANTAGE, box(ohi && OverseasOhiType.MEDICARE_ADVANTAGE in f.overseasOhiTypes))
        put(OHI_VA_FOREIGN, box(ohi && OverseasOhiType.VA_FOREIGN_MEDICAL in f.overseasOhiTypes))

        val i1 = if (ohi) f.insurance1 else InsuranceEntry()
        val i2 = if (ohi) f.insurance2 else InsuranceEntry()
        put(INS1_NAME, i1.nameAndAddress.trim())
        put(INS1_ID, i1.idNumber.trim())
        put(INS1_DATE, formDate(i1.effectiveDate))
        yesNo(INS1_DRUG_YES, INS1_DRUG_NO, i1.drugCoverage)
        put(INS2_NAME, i2.nameAndAddress.trim())
        put(INS2_ID, i2.idNumber.trim())
        put(INS2_DATE, formDate(i2.effectiveDate))
        yesNo(INS2_DRUG_YES, INS2_DRUG_NO, i2.drugCoverage)

        put(SIGNED_DATE, formDate(f.signedDate))
        put(SIGNER_RELATIONSHIP, f.signerRelationship.trim())
        put(PAY_US_DOLLAR, box(f.paymentCurrency == PaymentCurrency.US_DOLLAR))
        put(PAY_LOCAL_FOREIGN, box(f.paymentCurrency == PaymentCurrency.LOCAL_FOREIGN))
        yesNo(PROOF_YES, PROOF_NO, f.paidProvider)
    }
}
