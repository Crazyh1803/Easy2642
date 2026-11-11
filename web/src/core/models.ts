// Data model shared with the Android app. Field names and enum values match the Android
// backup format (FamilyBackup JSON), so a backup made on one platform restores on the other.

export type Relationship = 'SELF' | 'SPOUSE' | 'CHILD' | 'STEPCHILD' | 'FORMER_SPOUSE' | 'OTHER';
export const RELATIONSHIP_LABELS: Record<Relationship, string> = {
  SELF: 'Self', SPOUSE: 'Spouse', CHILD: 'Natural or adopted child', STEPCHILD: 'Stepchild',
  FORMER_SPOUSE: 'Former spouse', OTHER: 'Other',
};
export type Sex = 'M' | 'F';
export const SEX_LABELS: Record<Sex, string> = { M: 'Male', F: 'Female' };
export type CareType = 'INPATIENT' | 'OUTPATIENT' | 'DAY_SURGERY' | 'PHARMACY';
export const CARE_LABELS: Record<CareType, string> = {
  INPATIENT: 'Inpatient', OUTPATIENT: 'Outpatient', DAY_SURGERY: 'Day surgery', PHARMACY: 'Pharmacy',
};
export type OhiType = 'EMPLOYMENT' | 'PRIVATE' | 'MEDICARE' | 'STUDENT_PLAN' | 'MEDICARE_SUPPLEMENTAL' | 'PRESCRIPTION_PLAN' | 'OTHER';
export const OHI_LABELS: Record<OhiType, string> = {
  EMPLOYMENT: 'Employment (group)', PRIVATE: 'Private (non-group)', MEDICARE: 'Medicare', STUDENT_PLAN: 'Student plan',
  MEDICARE_SUPPLEMENTAL: 'Medicare supplemental', PRESCRIPTION_PLAN: 'Prescription plan', OTHER: 'Other',
};
export type OverseasOhiType = 'TRAVEL_INSURANCE' | 'MEDICARE_ADVANTAGE' | 'VA_FOREIGN_MEDICAL';
export const OVERSEAS_OHI_LABELS: Record<OverseasOhiType, string> = {
  TRAVEL_INSURANCE: 'Travel insurance', MEDICARE_ADVANTAGE: 'Medicare Advantage', VA_FOREIGN_MEDICAL: 'VA Foreign Medical Program',
};
export type PaymentCurrency = 'US_DOLLAR' | 'LOCAL_FOREIGN';
export const PAYMENT_LABELS: Record<PaymentCurrency, string> = { US_DOLLAR: 'US dollar', LOCAL_FOREIGN: 'Local foreign' };
export type Currency = 'USD' | 'EUR' | 'GBP' | 'JPY' | 'KRW' | 'OTHER';
export const CURRENCIES: Currency[] = ['USD', 'EUR', 'GBP', 'JPY', 'KRW', 'OTHER'];
export type OutputMode = 'COMBINED' | 'SEPARATE';
export const OUTPUT_LABELS: Record<OutputMode, string> = { COMBINED: 'One combined PDF', SEPARATE: 'Separate files' };

export interface PersonName { lastName: string; firstName: string; middleInitial: string }
export const emptyName = (): PersonName => ({ lastName: '', firstName: '', middleInitial: '' });

/** "Last, First M" as the form asks for. */
export function formatName(n: PersonName): string {
  const given = [n.firstName.trim(), n.middleInitial.trim()].filter(Boolean).join(' ');
  return [n.lastName.trim(), given].filter(Boolean).join(', ');
}
export const displayName = (n: PersonName): string => [n.firstName.trim(), n.lastName.trim()].filter(Boolean).join(' ');

export interface Phone { areaCode: string; number: string }
/** Splits "(555) 123-4567" or "+49 631 1234" into area/country code and the remainder. */
export function parsePhone(raw: string): Phone {
  const m = /^\s*\(?(\+?\d{1,4})\)?[\s\-./]+(.+)$/.exec(raw);
  return m ? { areaCode: m[1], number: m[2].trim() } : { areaCode: '', number: raw.trim() };
}

export interface InsuranceEntry { nameAndAddress: string; idNumber: string; effectiveDate: string; drugCoverage: boolean | null }
export const emptyInsurance = (): InsuranceEntry => ({ nameAndAddress: '', idNumber: '', effectiveDate: '', drugCoverage: null });

/** Everything printed on page 2 of DD Form 2642 (SEP 2024). Dates are ISO yyyy-MM-dd. */
export interface ClaimForm {
  patientName: PersonName;
  primaryPhone: Phone;
  secondaryPhone: Phone;
  patientAddress: string;
  overseasLocation: string;
  relationship: Relationship | null;
  relationshipOther: string;
  patientDob: string;
  sex: Sex | null;
  accidentRelated: boolean | null;
  workRelated: boolean | null;
  description: string;
  careType: CareType | null;
  telemedicine: boolean;
  urgentCare: boolean;
  telemedicineAudioOnly: boolean;
  audioOnlyReason: string;
  sponsorName: PersonName;
  sponsorId: string;
  hasOtherInsurance: boolean | null;
  ohiTypes: OhiType[];
  ohiOtherSpecify: string;
  overseasOhiTypes: OverseasOhiType[];
  insurance1: InsuranceEntry;
  insurance2: InsuranceEntry;
  signedDate: string;
  signerRelationship: string;
  paymentCurrency: PaymentCurrency | null;
  paidProvider: boolean | null;
}

export const emptyForm = (): ClaimForm => ({
  patientName: emptyName(), primaryPhone: { areaCode: '', number: '' }, secondaryPhone: { areaCode: '', number: '' },
  patientAddress: '', overseasLocation: '', relationship: null, relationshipOther: '', patientDob: '', sex: null,
  accidentRelated: null, workRelated: null, description: '', careType: null, telemedicine: false, urgentCare: false,
  telemedicineAudioOnly: false, audioOnlyReason: '', sponsorName: emptyName(), sponsorId: '', hasOtherInsurance: null,
  ohiTypes: [], ohiOtherSpecify: '', overseasOhiTypes: [], insurance1: emptyInsurance(), insurance2: emptyInsurance(),
  signedDate: '', signerRelationship: '', paymentCurrency: null, paidProvider: null,
});

export interface Sponsor {
  name: PersonName; dodIdOrSsn: string; branchOfService: string; dutyStatus: string;
  streetAddress: string; city: string; state: string; zipCode: string; phoneNumber: string; dob: string; sex: Sex | null;
}
export const emptySponsor = (): Sponsor => ({
  name: emptyName(), dodIdOrSsn: '', branchOfService: '', dutyStatus: '', streetAddress: '', city: '', state: '',
  zipCode: '', phoneNumber: '', dob: '', sex: null,
});
export function formatAddress(s: Sponsor): string {
  const cityLine = [s.city.trim(), [s.state.trim(), s.zipCode.trim()].filter(Boolean).join(' ')].filter(Boolean).join(', ');
  return [s.streetAddress.trim(), cityLine].filter(Boolean).join(', ');
}

export interface Dependent { id: string; relationship: Relationship; name: PersonName; dob: string; sex: Sex | null; dodId: string }

export const SPONSOR_PATIENT_ID = 'sponsor';
export const MANUAL_PATIENT_ID = 'manual';

export interface ClaimRecord {
  id: string; patientId: string; patientName: string; provider: string; dateOfService: string;
  /** Amount in minor units (cents). */
  amountMinor: number; currency: Currency; dossierUri: string; isPacketGenerated: boolean;
  isFiled: boolean; filedDate: string | null; isReimbursed: boolean; reimbursedDate: string | null; notes: string;
}

export type ClaimState = 'NEEDS_FILING' | 'AWAITING_REIMBURSEMENT' | 'COMPLETED';
export const STATE_LABELS: Record<ClaimState, string> = {
  NEEDS_FILING: 'Needs Filing', AWAITING_REIMBURSEMENT: 'Awaiting Reimbursement', COMPLETED: 'Completed',
};
export const claimState = (c: ClaimRecord): ClaimState =>
  c.isReimbursed ? 'COMPLETED' : c.isFiled ? 'AWAITING_REIMBURSEMENT' : 'NEEDS_FILING';

export const formatAmount = (c: ClaimRecord): string =>
  `${c.currency} ${Math.floor(c.amountMinor / 100)}.${String(c.amountMinor % 100).padStart(2, '0')}`;

/** Toggling "filed" off also clears reimbursement, which cannot precede filing. */
export const withFiled = (c: ClaimRecord, filed: boolean, today: string): ClaimRecord =>
  filed ? { ...c, isFiled: true, filedDate: c.filedDate ?? today }
        : { ...c, isFiled: false, filedDate: null, isReimbursed: false, reimbursedDate: null };

/** Marking reimbursed implies the claim was filed. */
export const withReimbursed = (c: ClaimRecord, done: boolean, today: string): ClaimRecord =>
  done ? { ...c, isFiled: true, filedDate: c.filedDate ?? today, isReimbursed: true, reimbursedDate: c.reimbursedDate ?? today }
       : { ...c, isReimbursed: false, reimbursedDate: null };

export interface ClaimFilter { patientId: string | null; state: ClaimState | null; query: string }
export function matchesFilter(f: ClaimFilter, c: ClaimRecord): boolean {
  if (f.patientId !== null && c.patientId !== f.patientId) return false;
  if (f.state !== null && claimState(c) !== f.state) return false;
  const q = f.query.trim().toLowerCase();
  return !q || c.provider.toLowerCase().includes(q) || c.notes.toLowerCase().includes(q);
}

/** Parses "1,234.50", "1234,5" or "12" into minor units; null if not a valid amount. */
export function parseAmountMinor(raw: string): number | null {
  const s = raw.trim().replace(/ /g, '');
  if (!s) return null;
  const lastSep = Math.max(s.lastIndexOf('.'), s.lastIndexOf(','));
  const decimals = lastSep >= 0 ? s.length - lastSep - 1 : 0;
  let whole = s;
  let frac = '00';
  if (lastSep >= 0 && decimals >= 1 && decimals <= 2) {
    whole = s.slice(0, lastSep);
    frac = s.slice(lastSep + 1).padEnd(2, '0');
  }
  whole = whole.replace(/[.,]/g, '');
  if (!/^\d+$/.test(whole) || !/^\d+$/.test(frac)) return null;
  return Number(whole) * 100 + Number(frac);
}

export interface SignaturePoint { x: number; y: number }
/** A handwritten signature as vector strokes in the pad's coordinates (y downwards). */
export interface Signature { strokes: SignaturePoint[][] }
export interface SavedSignature { id: string; signerName: string; signature: Signature }

const MIN_POINTS = 8;
const MIN_EXTENT = 24;
export function signatureBounds(sig: Signature) {
  const pts = sig.strokes.flat();
  if (!pts.length) return { left: 0, top: 0, width: 1, height: 1 };
  const xs = pts.map((p) => p.x);
  const ys = pts.map((p) => p.y);
  const left = Math.min(...xs);
  const top = Math.min(...ys);
  return { left, top, width: Math.max(Math.max(...xs) - left, 1), height: Math.max(Math.max(...ys) - top, 1) };
}
/** A scribble needs some extent to count as a signature; a stray tap does not. */
export function signatureIsBlank(sig: Signature | null): boolean {
  if (!sig) return true;
  const pts = sig.strokes.flat();
  if (pts.length < MIN_POINTS) return true;
  const xs = pts.map((p) => p.x);
  const ys = pts.map((p) => p.y);
  return Math.max(...xs) - Math.min(...xs) < MIN_EXTENT && Math.max(...ys) - Math.min(...ys) < MIN_EXTENT;
}
/** Strokes scaled into a box, aspect ratio kept, left-aligned and vertically centred (y still down). */
export function fitSignature(sig: Signature, boxWidth: number, boxHeight: number): SignaturePoint[][] {
  const b = signatureBounds(sig);
  const scale = Math.min(boxWidth / b.width, boxHeight / b.height);
  const offsetY = (boxHeight - b.height * scale) / 2;
  return sig.strokes.filter((s) => s.length).map((s) => s.map((p) => ({ x: (p.x - b.left) * scale, y: (p.y - b.top) * scale + offsetY })));
}

/** Same shape as the Android app's FamilyBackup. */
export interface FamilyBackup {
  formatVersion: number;
  sponsor: Sponsor | null;
  dependents: Dependent[];
  claims: ClaimRecord[];
  signatures: SavedSignature[];
}

/** Pre-fills blocks 1-6, 9 and 10 from the family store, keeping everything else on the form. */
export function prefillForSponsor(s: Sponsor, base: ClaimForm): ClaimForm {
  return {
    ...base, patientName: s.name, primaryPhone: parsePhone(s.phoneNumber), patientAddress: formatAddress(s),
    relationship: 'SELF', relationshipOther: '', patientDob: s.dob, sex: s.sex, sponsorName: s.name,
    sponsorId: s.dodIdOrSsn, signerRelationship: 'Self',
  };
}
export function prefillForDependent(s: Sponsor, d: Dependent, base: ClaimForm): ClaimForm {
  return {
    ...base, patientName: d.name, primaryPhone: parsePhone(s.phoneNumber), patientAddress: formatAddress(s),
    relationship: d.relationship, relationshipOther: '', patientDob: d.dob, sex: d.sex, sponsorName: s.name,
    sponsorId: s.dodIdOrSsn, signerRelationship: '',
  };
}

export const isIsoDate = (s: string): boolean => {
  if (!/^\d{4}-\d{2}-\d{2}$/.test(s)) return false;
  const d = new Date(`${s}T00:00:00Z`);
  return !Number.isNaN(d.getTime()) && d.toISOString().slice(0, 10) === s;
};
export const todayIso = (): string => {
  const d = new Date();
  return `${d.getFullYear()}-${String(d.getMonth() + 1).padStart(2, '0')}-${String(d.getDate()).padStart(2, '0')}`;
};
export const newId = (): string => crypto.randomUUID();
