import { describe, expect, it } from 'vitest';
import { claimFileName } from '../src/core/fileName';
import { ALL_FIELDS, CHECKBOX_FIELDS, F, OFF, ON, TEXT_FIELDS, toFieldValues } from '../src/core/fieldMap';
import {
  ClaimRecord, Dependent, Sponsor, claimState, emptyForm, emptySponsor, fitSignature, formatAmount, matchesFilter,
  parseAmountMinor, parsePhone, prefillForDependent, prefillForSponsor, signatureIsBlank, withFiled, withReimbursed, isIsoDate,
} from '../src/core/models';
import { oktoberfestEnd, oktoberfestIsOn, oktoberfestStart, shouldPromptForTip } from '../src/core/oktoberfest';
import { Settings } from '../src/store/settings';
import { Vault, WrongPasscodeError, emptyData, parseBackup, VAULT_KEY } from '../src/store/vault';

const sponsor: Sponsor = {
  ...emptySponsor(), name: { lastName: 'Testerson', firstName: 'Alex', middleInitial: 'Q' }, dodIdOrSsn: '0000000000',
  streetAddress: 'PSC 1 Box 100', city: 'APO', state: 'AE', zipCode: '09000', phoneNumber: '+49 631 5550100', dob: '1985-03-09', sex: 'M',
};
const child: Dependent = { id: 'd1', relationship: 'CHILD', name: { lastName: 'Testerson', firstName: 'Sam', middleInitial: '' }, dob: '2015-11-02', sex: 'F', dodId: '1111111111' };
const claim: ClaimRecord = {
  id: 'c1', patientId: 'd1', patientName: 'Sam Testerson', provider: 'Klinikum', dateOfService: '2026-02-14', amountMinor: 8505,
  currency: 'EUR', dossierUri: '', isPacketGenerated: false, isFiled: false, filedDate: null, isReimbursed: false, reimbursedDate: null, notes: 'ear infection',
};
const memoryStore = () => {
  const m = new Map<string, string>();
  return { getItem: (k: string) => m.get(k) ?? null, setItem: (k: string, v: string) => void m.set(k, v), removeItem: (k: string) => void m.delete(k), raw: m };
};
const local = (y: number, m: number, d: number) => new Date(y, m - 1, d);
const ymd = (d: Date) => [d.getFullYear(), d.getMonth() + 1, d.getDate()];

describe('field map', () => {
  it('has disjoint, complete field sets', () => {
    expect(CHECKBOX_FIELDS.filter((f) => TEXT_FIELDS.includes(f))).toEqual([]);
    expect(ALL_FIELDS.length).toBe(61);
    expect(Object.keys(toFieldValues(emptyForm())).sort()).toEqual([...ALL_FIELDS].sort());
    expect(ALL_FIELDS.includes(F.SIGNATURE)).toBe(false);
  });
  it('leaves a blank form blank', () => {
    const v = toFieldValues(emptyForm());
    CHECKBOX_FIELDS.forEach((f) => expect(v[f]).toBe(OFF));
    TEXT_FIELDS.forEach((f) => expect(v[f]).toBe(''));
  });
  it('maps a dependent prefill onto blocks 1-10', () => {
    const v = toFieldValues(prefillForDependent(sponsor, child, emptyForm()));
    expect(v[F.PATIENT_NAME]).toBe('Testerson, Sam');
    expect(v[F.PHONE_PRIMARY_AREA]).toBe('+49');
    expect(v[F.PHONE_PRIMARY_NUMBER]).toBe('631 5550100');
    expect(v[F.PATIENT_ADDRESS]).toBe('PSC 1 Box 100, APO, AE 09000');
    expect(v[F.REL_CHILD]).toBe(ON);
    expect([F.REL_SELF, F.REL_SPOUSE, F.REL_CHILD, F.REL_STEPCHILD, F.REL_FORMER_SPOUSE, F.REL_OTHER].filter((f) => v[f] === ON).length).toBe(1);
    expect(v[F.PATIENT_DOB]).toBe('20151102');
    expect(v[F.SEX_FEMALE]).toBe(ON);
    expect(v[F.SPONSOR_NAME]).toBe('Testerson, Alex Q');
    expect(v[F.SPONSOR_ID]).toBe('0000000000');
  });
  it('writes "Same" when the sponsor is the patient', () => {
    const v = toFieldValues(prefillForSponsor(sponsor, emptyForm()));
    expect(v[F.REL_SELF]).toBe(ON);
    expect(v[F.SPONSOR_NAME]).toBe('Same');
  });
  it('prints insurance details only when OHI is yes', () => {
    const form = { ...emptyForm(), hasOtherInsurance: false, ohiTypes: ['PRIVATE', 'OTHER'] as const, ohiOtherSpecify: 'x', insurance1: { nameAndAddress: 'Acme', idNumber: 'A1', effectiveDate: '2024-01-01', drugCoverage: true } };
    const no = toFieldValues({ ...form, ohiTypes: [...form.ohiTypes] });
    expect(no[F.OHI_NO]).toBe(ON); expect(no[F.OHI_PRIVATE]).toBe(OFF); expect(no[F.INS1_NAME]).toBe('');
    const yes = toFieldValues({ ...form, ohiTypes: [...form.ohiTypes], hasOtherInsurance: true });
    expect(yes[F.OHI_YES]).toBe(ON); expect(yes[F.OHI_PRIVATE]).toBe(ON); expect(yes[F.OHI_OTHER_SPECIFY]).toBe('x');
    expect(yes[F.INS1_DATE]).toBe('20240101'); expect(yes[F.INS1_DRUG_YES]).toBe(ON);
  });
});

describe('helpers', () => {
  it('parses phones, amounts and dates', () => {
    expect(parsePhone('(555) 123-4567')).toEqual({ areaCode: '555', number: '123-4567' });
    expect(parsePhone('5551234567')).toEqual({ areaCode: '', number: '5551234567' });
    expect(parseAmountMinor('1,234.50')).toBe(123450);
    expect(parseAmountMinor('1.234,5')).toBe(123450);
    expect(parseAmountMinor('12')).toBe(1200);
    expect(parseAmountMinor('1,000')).toBe(100000);
    expect(parseAmountMinor('abc')).toBeNull();
    expect(formatAmount({ ...claim, amountMinor: 8505 })).toBe('EUR 85.05');
    expect(isIsoDate('2026-02-14')).toBe(true);
    expect(isIsoDate('2026-02-30')).toBe(false);
    expect(isIsoDate('14/02/2026')).toBe(false);
  });
  it('builds file names', () => {
    expect(claimFileName('Testerson', 'Klinikum Kaiserslautern', '2026-02-14')).toBe('Claim_DD2642_Testerson_KlinikumKaiserslautern_20260214.pdf');
    expect(claimFileName('Müller', "Dr. Schäfer's Praxis", '2026-02-14')).toBe('Claim_DD2642_Muller_DrSchafersPraxis_20260214.pdf');
    expect(claimFileName(' ', '../..', '')).toBe('Claim_DD2642_Patient_Provider_undated.pdf');
    expect(claimFileName('Testerson', 'Klinikum', '2026-02-14', 'Bill')).toBe('Claim_DD2642_Testerson_Klinikum_20260214_Bill.pdf');
  });
  it('tracks claim state and filters', () => {
    expect(claimState(claim)).toBe('NEEDS_FILING');
    const filed = withFiled(claim, true, '2026-02-20');
    expect(claimState(filed)).toBe('AWAITING_REIMBURSEMENT');
    const done = withReimbursed(filed, true, '2026-03-30');
    expect(claimState(done)).toBe('COMPLETED'); expect(done.filedDate).toBe('2026-02-20');
    expect(withReimbursed(claim, true, '2026-03-30').filedDate).toBe('2026-03-30');
    expect(claimState(withFiled(done, false, 'x'))).toBe('NEEDS_FILING');
    expect(matchesFilter({ patientId: 'd1', state: 'NEEDS_FILING', query: 'KLINIK' }, claim)).toBe(true);
    expect(matchesFilter({ patientId: null, state: null, query: 'ear' }, claim)).toBe(true);
    expect(matchesFilter({ patientId: 'sponsor', state: null, query: '' }, claim)).toBe(false);
    expect(matchesFilter({ patientId: null, state: 'COMPLETED', query: '' }, claim)).toBe(false);
  });
  it('knows the Oktoberfest window and prompts once a year', () => {
    expect(ymd(oktoberfestStart(2023))).toEqual([2023, 9, 16]);
    expect(ymd(oktoberfestStart(2025))).toEqual([2025, 9, 20]);
    expect(ymd(oktoberfestStart(2026))).toEqual([2026, 9, 19]);
    expect(ymd(oktoberfestEnd(2026))).toEqual([2026, 10, 4]);
    expect(oktoberfestIsOn(local(2026, 9, 18))).toBe(false);
    expect(oktoberfestIsOn(local(2026, 9, 19))).toBe(true);
    expect(oktoberfestIsOn(local(2026, 10, 4))).toBe(true);
    expect(oktoberfestIsOn(local(2026, 10, 5))).toBe(false);
    expect(shouldPromptForTip(local(2026, 9, 25), 2025)).toBe(true);
    expect(shouldPromptForTip(local(2026, 9, 25), 2026)).toBe(false);
    expect(shouldPromptForTip(local(2026, 11, 10), 2025)).toBe(false);
  });
  it('judges and fits signatures', () => {
    expect(signatureIsBlank(null)).toBe(true);
    expect(signatureIsBlank({ strokes: [Array.from({ length: 20 }, () => ({ x: 5, y: 5 }))] })).toBe(true);
    const sig = { strokes: [Array.from({ length: 21 }, (_, i) => ({ x: 100 + i * 10, y: 50 + (i % 2) * 40 }))] };
    expect(signatureIsBlank(sig)).toBe(false);
    const fitted = fitSignature(sig, 280, 10).flat();
    expect(Math.max(...fitted.map((p) => p.x))).toBeCloseTo(50, 2);
    expect(Math.min(...fitted.map((p) => p.y))).toBeCloseTo(0, 2);
    expect(Math.max(...fitted.map((p) => p.y))).toBeCloseTo(10, 2);
  });
});

describe('vault', () => {
  const FAST = 1000; // PBKDF2 rounds for tests only

  it('round-trips data and rejects the wrong passcode', async () => {
    const store = memoryStore();
    expect(Vault.exists(store)).toBe(false);
    const vault = await Vault.create(store, 'correct horse', FAST);
    await vault.save({ ...emptyData(), sponsor, dependents: [child], claims: [claim] });
    expect(Vault.exists(store)).toBe(true);
    const { data } = await Vault.unlock(store, 'correct horse');
    expect(data.sponsor).toEqual(sponsor);
    expect(data.dependents).toEqual([child]);
    expect(data.claims).toEqual([claim]);
    await expect(Vault.unlock(store, 'wrong horse!')).rejects.toBeInstanceOf(WrongPasscodeError);
  });

  it('stores nothing readable', async () => {
    const store = memoryStore();
    const vault = await Vault.create(store, 'correct horse', FAST);
    await vault.save({ ...emptyData(), sponsor, claims: [claim] });
    const raw = store.getItem(VAULT_KEY)!;
    for (const secret of ['Testerson', '0000000000', 'Klinikum', 'ear infection', 'correct horse']) expect(raw.includes(secret)).toBe(false);
    const first = JSON.parse(raw).iv;
    await vault.save({ ...emptyData(), sponsor });
    expect(JSON.parse(store.getItem(VAULT_KEY)!).iv).not.toBe(first);
  });

  it('changes the passcode and can be destroyed', async () => {
    const store = memoryStore();
    const vault = await Vault.create(store, 'first-pass', FAST);
    const data = { ...emptyData(), sponsor };
    await vault.changePasscode('second-pass', data);
    await expect(Vault.unlock(store, 'first-pass')).rejects.toBeInstanceOf(WrongPasscodeError);
    expect((await Vault.unlock(store, 'second-pass')).data.sponsor).toEqual(sponsor);
    await expect(Vault.create(store, 'short', FAST)).rejects.toThrow(/at least/);
    Vault.destroy(store);
    expect(Vault.exists(store)).toBe(false);
  });

  it('reads Android backups and rejects other files', () => {
    const android = '{"formatVersion":1,"sponsor":null,"dependents":[{"id":"d1","relationship":"CHILD","name":{"lastName":"T","firstName":"S","middleInitial":""},"dob":"2015-11-02","sex":"F","dodId":"1"}],"claims":[]}';
    const b = parseBackup(android);
    expect(b.dependents.length).toBe(1);
    expect(b.signatures).toEqual([]);
    expect(() => parseBackup('{"formatVersion":99}')).toThrow();
    expect(() => parseBackup('not json')).toThrow();
    expect(() => parseBackup('[]')).toThrow();
  });

  it('keeps settings free of personal data', () => {
    const store = memoryStore();
    const s = new Settings(store);
    expect(s.outputMode).toBe('COMBINED'); expect(s.disclaimerAccepted).toBe(false); expect(s.lastTipPromptYear).toBe(0);
    s.outputMode = 'SEPARATE'; s.disclaimerAccepted = true; s.lastTipPromptYear = 2026;
    const again = new Settings(store);
    expect(again.outputMode).toBe('SEPARATE'); expect(again.disclaimerAccepted).toBe(true); expect(again.lastTipPromptYear).toBe(2026);
    s.clear();
    expect(new Settings(store).outputMode).toBe('COMBINED');
  });
});
