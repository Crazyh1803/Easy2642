// Fully qualified AcroForm field names of DD Form 2642, SEP 2024 edition, and the mapping from
// ClaimForm onto them. Generated from, and kept identical to, the Android app's Dd2642FieldMap.kt.
import { ClaimForm, InsuranceEntry, emptyInsurance, formatName } from './models';

export const ON = '1';
export const OFF = 'Off';
const P = 'form1[0].Page2[0].';

export const F = {
  PATIENT_NAME: P + 'No1-4[0].Personal[0].Name[0]',
  PHONE_PRIMARY_AREA: P + 'No1-4[0].Personal[0].Phone[0].primary1[0]',
  PHONE_PRIMARY_NUMBER: P + 'No1-4[0].Personal[0].Phone[0].primary2[0]',
  PHONE_SECONDARY_AREA: P + 'No1-4[0].Personal[0].Phone[0].secondary1[0]',
  PHONE_SECONDARY_NUMBER: P + 'No1-4[0].Personal[0].Phone[0].secondary2[0]',
  PATIENT_ADDRESS: P + 'No1-4[0].Address[0].PAddress[0]',
  OVERSEAS_LOCATION: P + 'No1-4[0].Address[0].overseas[0]',
  REL_SELF: P + 'No1-4[0].Relationship[0].a4[0]',
  REL_STEPCHILD: P + 'No1-4[0].Relationship[0].b4[0]',
  REL_SPOUSE: P + 'No1-4[0].Relationship[0].c4[0]',
  REL_FORMER_SPOUSE: P + 'No1-4[0].Relationship[0].d4[0]',
  REL_CHILD: P + 'No1-4[0].Relationship[0].e4[0]',
  REL_OTHER: P + 'No1-4[0].Relationship[0].f4[0]',
  REL_OTHER_SPECIFY: P + 'No1-4[0].Relationship[0].specify4[0]',
  PATIENT_DOB: P + 'No5[0].patient_dob[0]',
  SEX_MALE: P + 'No6[0].male[0]',
  SEX_FEMALE: P + 'No6[0].female[0]',
  ACCIDENT_YES: P + 'No7[0].yes_accident[0]',
  ACCIDENT_NO: P + 'No7[0].no_accident[0]',
  WORK_YES: P + 'No7[0].yes_work[0]',
  WORK_NO: P + 'No7[0].no_work[0]',
  DESCRIPTION: P + 'No8A[0].describe[0]',
  CARE_INPATIENT: P + 'No8BC[0].inpatient[0]',
  CARE_OUTPATIENT: P + 'No8BC[0].outpatient[0]',
  CARE_DAY_SURGERY: P + 'No8BC[0].day_surgery[0]',
  CARE_PHARMACY: P + 'No8BC[0].pharmacy[0]',
  TELEMEDICINE: P + 'No8BC[0].telemedicine[0]',
  URGENT_CARE: P + 'No8BC[0].urgent_care[0]',
  TELEMEDICINE_AUDIO: P + 'No8BC[0].telemedicine_audio[0]',
  AUDIO_REASON: P + 'No8BC[0].audio_reason[0]',
  SPONSOR_NAME: P + 'No9[0].sponsor_fs_name[0]',
  SPONSOR_ID: P + 'No10[0].ssn_dbn[0]',
  OHI_YES: P + 'No11A[0].TxtBox[0].yes11[0]',
  OHI_NO: P + 'No11A[0].TxtBox[0].no11[0]',
  OHI_EMPLOYMENT: P + 'No11BC[0].Checks[0].#subform[0].employment11b[0]',
  OHI_PRIVATE: P + 'No11BC[0].Checks[0].#subform[0].private11b[0]',
  OHI_MEDICARE: P + 'No11BC[0].Checks[0].#subform[1].medicare11b[0]',
  OHI_STUDENT_PLAN: P + 'No11BC[0].Checks[0].#subform[1].student_plan11b[0]',
  OHI_MEDICARE_SUPP: P + 'No11BC[0].Checks[0].#subform[2].medicare_supp11b[0]',
  OHI_PRESCRIPTION: P + 'No11BC[0].Checks[0].#subform[2].prescription11b[0]',
  OHI_OTHER: P + 'No11BC[0].Checks[0].other11b[0]',
  OHI_OTHER_SPECIFY: P + 'No11BC[0].Checks[0].specify11b[0]',
  OHI_TRAVEL: P + 'No11BC[0].travel_insurance11c[0]',
  OHI_MEDICARE_ADVANTAGE: P + 'No11BC[0].medicare_advantage11c[0]',
  OHI_VA_FOREIGN: P + 'No11BC[0].va_foreign_medical11c[0]',
  INS1_NAME: P + 'No11C[0].SecondRow[0].insurance1_name[0]',
  INS2_NAME: P + 'No11C[0].SecondRow[0].insurance2_name[0]',
  INS1_ID: P + 'No11C[0].ThirdRow[0].insurance1_id[0]',
  INS2_ID: P + 'No11C[0].ThirdRow[0].insurance2_id[0]',
  INS1_DATE: P + 'No11C[0].FourthRow[0].insurance1_date[0]',
  INS2_DATE: P + 'No11C[0].FourthRow[0].insurance2_date[0]',
  INS1_DRUG_YES: P + 'No11C[0].FifthRow[0].yes_ins1[0]',
  INS1_DRUG_NO: P + 'No11C[0].FifthRow[0].no_ins1[0]',
  INS2_DRUG_YES: P + 'No11C[0].FifthRow[0].yes_ins2[0]',
  INS2_DRUG_NO: P + 'No11C[0].FifthRow[0].no_ins2[0]',
  SIGNATURE: P + 'No12-13[0].No12[0].SignatureField[0]',
  SIGNED_DATE: P + 'No12-13[0].No12[0].signedDate[0]',
  SIGNER_RELATIONSHIP: P + 'No12-13[0].No12[0].Relationship[0]',
  PAY_US_DOLLAR: P + 'No12-13[0].No13[0].us_dollar[0]',
  PAY_LOCAL_FOREIGN: P + 'No12-13[0].No13[0].local_foreign[0]',
  PROOF_YES: P + 'No12-13[0].No13[0].proof_yes[0]',
  PROOF_NO: P + 'No12-13[0].No13[0].proof_no[0]',
} as const;

export const CHECKBOX_FIELDS: string[] = [F.REL_SELF, F.REL_STEPCHILD, F.REL_SPOUSE, F.REL_FORMER_SPOUSE, F.REL_CHILD, F.REL_OTHER, F.SEX_MALE, F.SEX_FEMALE, F.ACCIDENT_YES, F.ACCIDENT_NO, F.WORK_YES, F.WORK_NO, F.CARE_INPATIENT, F.CARE_OUTPATIENT, F.CARE_DAY_SURGERY, F.CARE_PHARMACY, F.TELEMEDICINE, F.URGENT_CARE, F.TELEMEDICINE_AUDIO, F.OHI_YES, F.OHI_NO, F.OHI_EMPLOYMENT, F.OHI_PRIVATE, F.OHI_MEDICARE, F.OHI_STUDENT_PLAN, F.OHI_MEDICARE_SUPP, F.OHI_PRESCRIPTION, F.OHI_OTHER, F.OHI_TRAVEL, F.OHI_MEDICARE_ADVANTAGE, F.OHI_VA_FOREIGN, F.INS1_DRUG_YES, F.INS1_DRUG_NO, F.INS2_DRUG_YES, F.INS2_DRUG_NO, F.PAY_US_DOLLAR, F.PAY_LOCAL_FOREIGN, F.PROOF_YES, F.PROOF_NO];
export const TEXT_FIELDS: string[] = [F.PATIENT_NAME, F.PHONE_PRIMARY_AREA, F.PHONE_PRIMARY_NUMBER, F.PHONE_SECONDARY_AREA, F.PHONE_SECONDARY_NUMBER, F.PATIENT_ADDRESS, F.OVERSEAS_LOCATION, F.REL_OTHER_SPECIFY, F.PATIENT_DOB, F.DESCRIPTION, F.AUDIO_REASON, F.SPONSOR_NAME, F.SPONSOR_ID, F.OHI_OTHER_SPECIFY, F.INS1_NAME, F.INS2_NAME, F.INS1_ID, F.INS2_ID, F.INS1_DATE, F.INS2_DATE, F.SIGNED_DATE, F.SIGNER_RELATIONSHIP];
/** Every fillable field the app writes. The template must contain exactly these plus F.SIGNATURE. */
export const ALL_FIELDS: string[] = [...CHECKBOX_FIELDS, ...TEXT_FIELDS];

/** ISO yyyy-MM-dd to the form's YYYYMMDD. Anything else is passed through unchanged. */
export function formDate(iso: string): string {
  const t = iso.trim();
  return /^\d{4}-\d{2}-\d{2}$/.test(t) ? t.replace(/-/g, '') : t;
}

const box = (on: boolean) => (on ? ON : OFF);

/** Values for every field in ALL_FIELDS. */
export function toFieldValues(f: ClaimForm): Record<string, string> {
  const v: Record<string, string> = {};
  const yesNo = (yes: string, no: string, value: boolean | null) => {
    v[yes] = box(value === true);
    v[no] = box(value === false);
  };
  v[F.PATIENT_NAME] = formatName(f.patientName);
  v[F.PHONE_PRIMARY_AREA] = f.primaryPhone.areaCode.trim();
  v[F.PHONE_PRIMARY_NUMBER] = f.primaryPhone.number.trim();
  v[F.PHONE_SECONDARY_AREA] = f.secondaryPhone.areaCode.trim();
  v[F.PHONE_SECONDARY_NUMBER] = f.secondaryPhone.number.trim();
  v[F.PATIENT_ADDRESS] = f.patientAddress.trim();
  v[F.OVERSEAS_LOCATION] = f.overseasLocation.trim();

  v[F.REL_SELF] = box(f.relationship === 'SELF');
  v[F.REL_STEPCHILD] = box(f.relationship === 'STEPCHILD');
  v[F.REL_SPOUSE] = box(f.relationship === 'SPOUSE');
  v[F.REL_FORMER_SPOUSE] = box(f.relationship === 'FORMER_SPOUSE');
  v[F.REL_CHILD] = box(f.relationship === 'CHILD');
  v[F.REL_OTHER] = box(f.relationship === 'OTHER');
  v[F.REL_OTHER_SPECIFY] = f.relationship === 'OTHER' ? f.relationshipOther.trim() : '';

  v[F.PATIENT_DOB] = formDate(f.patientDob);
  v[F.SEX_MALE] = box(f.sex === 'M');
  v[F.SEX_FEMALE] = box(f.sex === 'F');
  yesNo(F.ACCIDENT_YES, F.ACCIDENT_NO, f.accidentRelated);
  yesNo(F.WORK_YES, F.WORK_NO, f.workRelated);

  v[F.DESCRIPTION] = f.description.trim();
  v[F.CARE_INPATIENT] = box(f.careType === 'INPATIENT');
  v[F.CARE_OUTPATIENT] = box(f.careType === 'OUTPATIENT');
  v[F.CARE_DAY_SURGERY] = box(f.careType === 'DAY_SURGERY');
  v[F.CARE_PHARMACY] = box(f.careType === 'PHARMACY');
  v[F.TELEMEDICINE] = box(f.telemedicine);
  v[F.URGENT_CARE] = box(f.urgentCare);
  v[F.TELEMEDICINE_AUDIO] = box(f.telemedicineAudioOnly);
  v[F.AUDIO_REASON] = f.telemedicineAudioOnly ? f.audioOnlyReason.trim() : '';

  // Form instruction 9: if sponsor and patient are the same, enter "same".
  v[F.SPONSOR_NAME] = f.relationship === 'SELF' ? 'Same' : formatName(f.sponsorName);
  v[F.SPONSOR_ID] = f.sponsorId.trim();

  yesNo(F.OHI_YES, F.OHI_NO, f.hasOtherInsurance);
  const ohi = f.hasOtherInsurance === true;
  v[F.OHI_EMPLOYMENT] = box(ohi && f.ohiTypes.includes('EMPLOYMENT'));
  v[F.OHI_PRIVATE] = box(ohi && f.ohiTypes.includes('PRIVATE'));
  v[F.OHI_MEDICARE] = box(ohi && f.ohiTypes.includes('MEDICARE'));
  v[F.OHI_STUDENT_PLAN] = box(ohi && f.ohiTypes.includes('STUDENT_PLAN'));
  v[F.OHI_MEDICARE_SUPP] = box(ohi && f.ohiTypes.includes('MEDICARE_SUPPLEMENTAL'));
  v[F.OHI_PRESCRIPTION] = box(ohi && f.ohiTypes.includes('PRESCRIPTION_PLAN'));
  v[F.OHI_OTHER] = box(ohi && f.ohiTypes.includes('OTHER'));
  v[F.OHI_OTHER_SPECIFY] = ohi && f.ohiTypes.includes('OTHER') ? f.ohiOtherSpecify.trim() : '';
  v[F.OHI_TRAVEL] = box(ohi && f.overseasOhiTypes.includes('TRAVEL_INSURANCE'));
  v[F.OHI_MEDICARE_ADVANTAGE] = box(ohi && f.overseasOhiTypes.includes('MEDICARE_ADVANTAGE'));
  v[F.OHI_VA_FOREIGN] = box(ohi && f.overseasOhiTypes.includes('VA_FOREIGN_MEDICAL'));

  const i1: InsuranceEntry = ohi ? f.insurance1 : emptyInsurance();
  const i2: InsuranceEntry = ohi ? f.insurance2 : emptyInsurance();
  v[F.INS1_NAME] = i1.nameAndAddress.trim();
  v[F.INS1_ID] = i1.idNumber.trim();
  v[F.INS1_DATE] = formDate(i1.effectiveDate);
  yesNo(F.INS1_DRUG_YES, F.INS1_DRUG_NO, i1.drugCoverage);
  v[F.INS2_NAME] = i2.nameAndAddress.trim();
  v[F.INS2_ID] = i2.idNumber.trim();
  v[F.INS2_DATE] = formDate(i2.effectiveDate);
  yesNo(F.INS2_DRUG_YES, F.INS2_DRUG_NO, i2.drugCoverage);

  v[F.SIGNED_DATE] = formDate(f.signedDate);
  v[F.SIGNER_RELATIONSHIP] = f.signerRelationship.trim();
  v[F.PAY_US_DOLLAR] = box(f.paymentCurrency === 'US_DOLLAR');
  v[F.PAY_LOCAL_FOREIGN] = box(f.paymentCurrency === 'LOCAL_FOREIGN');
  yesNo(F.PROOF_YES, F.PROOF_NO, f.paidProvider);
  return v;
}
