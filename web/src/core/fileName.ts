import { formDate } from './fieldMap';

const MAX_PART = 40;

/** ASCII letters and digits only, so the name is safe on every file system. */
function part(raw: string, fallback: string): string {
  const ascii = raw.replace(/ß/g, 'ss').replace(/['’]/g, '').normalize('NFD').replace(/\p{M}+/gu, '');
  const words = ascii.split(/[^A-Za-z0-9]+/).filter(Boolean);
  const joined = words.map((w) => w[0].toUpperCase() + w.slice(1)).join('').slice(0, MAX_PART);
  return joined || fallback;
}

/**
 * `Claim_DD2642_[PatientLastName]_[Provider]_[YYYYMMDD].pdf`, or with a trailing `_[suffix]`
 * (`_Form`, `_Bill`, `_Receipt`) when the claim is saved as separate files.
 */
export function claimFileName(patientLastName: string, provider: string, dateOfServiceIso: string, suffix?: string): string {
  const date = formDate(dateOfServiceIso).replace(/\D/g, '') || 'undated';
  return `Claim_DD2642_${part(patientLastName, 'Patient')}_${part(provider, 'Provider')}_${date}${suffix ? `_${suffix}` : ''}.pdf`;
}
