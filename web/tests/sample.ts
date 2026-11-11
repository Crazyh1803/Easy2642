import { ClaimForm, Signature, emptyForm } from '../src/core/models';

/** Synthetic data. Never real PII. */
export const sampleForm: ClaimForm = {
  ...emptyForm(),
  patientName: { lastName: 'Sampleton', firstName: 'Jordan', middleInitial: 'A' },
  primaryPhone: { areaCode: '+49', number: '631 555 0100' },
  secondaryPhone: { areaCode: '555', number: '010-0199' },
  patientAddress: 'PSC 2 Box 1234, APO, AE 09012',
  overseasLocation: 'Kaiserslautern, Germany',
  relationship: 'CHILD',
  patientDob: '2014-06-17',
  sex: 'F',
  accidentRelated: false,
  workRelated: false,
  description:
    'Acute otitis media, right ear. Fever and ear pain for two days; seen at the paediatric urgent care clinic. ' +
    'Examination, tympanometry and a prescription for amoxicillin (Diagnose: H66.9). Follow-up visit after seven days — no further treatment needed.',
  careType: 'OUTPATIENT',
  urgentCare: true,
  sponsorName: { lastName: 'Sampleton', firstName: 'Casey', middleInitial: 'R' },
  sponsorId: '0000000000',
  hasOtherInsurance: true,
  ohiTypes: ['PRIVATE', 'OTHER'],
  ohiOtherSpecify: 'School plan',
  overseasOhiTypes: ['TRAVEL_INSURANCE'],
  insurance1: { nameAndAddress: 'Example Mutual, 1 Sample Way, Springfield, VA 22150', idNumber: 'EX-000-123', effectiveDate: '2024-01-01', drugCoverage: true },
  insurance2: { nameAndAddress: 'Muster Versicherung AG, Musterstraße 1, 67655 Kaiserslautern', idNumber: 'MV-555', effectiveDate: '2025-03-15', drugCoverage: false },
  signedDate: '2026-02-20',
  signerRelationship: 'Parent',
  paymentCurrency: 'US_DOLLAR',
  paidProvider: true,
};

export const sampleSignature: Signature = {
  strokes: [
    Array.from({ length: 41 }, (_, i) => ({ x: 20 + i * 9, y: 80 + 45 * Math.sin(i / 2.5) })),
    Array.from({ length: 13 }, (_, i) => ({ x: 60 + i * 25, y: 140 - i * 2 })),
  ],
};
