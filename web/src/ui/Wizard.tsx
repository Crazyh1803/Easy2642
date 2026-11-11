import { useEffect, useMemo, useRef, useState } from 'preact/hooks';
import { OutFile, canPickFolder, canShareFiles, downloadAll, readFile, saveToFolder, shareFiles } from '../browser/files';
import { assertReadableImage, encodeImage } from '../browser/images';
import { claimFileName } from '../core/fileName';
import {
  CARE_LABELS, CURRENCIES, CareType, ClaimForm, ClaimRecord, Currency, InsuranceEntry, MANUAL_PATIENT_ID, OHI_LABELS, OUTPUT_LABELS,
  OVERSEAS_OHI_LABELS, OhiType, OutputMode, OverseasOhiType, PAYMENT_LABELS, PaymentCurrency, RELATIONSHIP_LABELS, Relationship,
  SEX_LABELS, SPONSOR_PATIENT_ID, Sex, Signature, SignaturePoint, displayName, emptyForm, emptySponsor, isIsoDate, newId,
  parseAmountMinor, prefillForDependent, prefillForSponsor, signatureIsBlank, todayIso,
} from '../core/models';
import { fillForm } from '../pdf/formService';
import { Attachment, AttachmentCategory, AttachmentKind, CATEGORY_LABELS, PacketResult, buildPacket, buildSeparate, formatSize, pdfPageCount } from '../pdf/packet';
import { AppStore } from '../store/appStore';
import { CameraDialog } from './CameraDialog';
import { Chips, Confirm, DateField, Dialog, Field, MultiChips, Section, YesNo } from './components';
import { NameFields } from './Family';
import { SignaturePad, SignaturePreview } from './SignaturePad';

const TITLES = ['Patient & visit', 'Form details', 'Supporting documents', 'Sign', 'Review & save'];
const VISIT = 0, FORM = 1, DOCUMENTS = 2, SIGN = 3, REVIEW = 4;
const RELATIONSHIPS = Object.keys(RELATIONSHIP_LABELS) as Relationship[];
const SEXES: Sex[] = ['M', 'F'];
const CARE = Object.keys(CARE_LABELS) as CareType[];
const OHI = Object.keys(OHI_LABELS) as OhiType[];
const OVERSEAS_OHI = Object.keys(OVERSEAS_OHI_LABELS) as OverseasOhiType[];
const PAYMENT = Object.keys(PAYMENT_LABELS) as PaymentCurrency[];
const CATEGORIES: AttachmentCategory[] = ['BILL', 'RECEIPT'];
const MODES: OutputMode[] = ['COMBINED', 'SEPARATE'];

let templateCache: Promise<Uint8Array> | null = null;
/** The blank form ships with the app; this is a same-origin fetch of a static file. */
function loadTemplate(): Promise<Uint8Array> {
  templateCache ??= fetch('./forms/dd2642_template.pdf').then(async (r) => {
    if (!r.ok) throw new Error('The form template could not be loaded.');
    return new Uint8Array(await r.arrayBuffer());
  });
  templateCache.catch(() => { templateCache = null; });
  return templateCache;
}

export function Wizard({ store, onClose }: { store: AppStore; onClose: () => void }) {
  const claimId = useRef(newId()).current;
  const [step, setStep] = useState(VISIT);
  const [patientId, setPatientId] = useState<string | null>(null);
  const [form, setFormState] = useState<ClaimForm>({ ...emptyForm(), signedDate: todayIso() });
  const [provider, setProvider] = useState('');
  const [dateOfService, setDateOfService] = useState('');
  const [amountText, setAmountText] = useState('');
  const [currency, setCurrency] = useState<Currency>('USD');
  const [notes, setNotes] = useState('');
  const [attachments, setAttachments] = useState<Attachment[]>([]);
  const [signature, setSignature] = useState<Signature | null>(null);
  const [signerName, setSignerName] = useState('');
  const [outputMode, setOutputMode] = useState<OutputMode>(store.settings.outputMode);
  const [outputs, setOutputs] = useState<PacketResult[]>([]);
  const [warnings, setWarnings] = useState<string[]>([]);
  const [busy, setBusy] = useState(false);
  const [message, setMessage] = useState('');
  const [savedTo, setSavedTo] = useState('');
  const [confirmLeave, setConfirmLeave] = useState(false);
  const top = useRef<HTMLDivElement>(null);

  const { sponsor, dependents } = store.data;
  // Any change to the claim invalidates files that were already built.
  const invalidate = () => { setOutputs([]); setSavedTo(''); };
  const setForm = (patch: Partial<ClaimForm>) => { setFormState((f) => ({ ...f, ...patch })); invalidate(); };

  const visitErrors = [
    !form.patientName.lastName.trim() && 'Patient last name',
    !provider.trim() && 'Provider',
    !isIsoDate(dateOfService) && 'Date of service',
    amountText.trim() && parseAmountMinor(amountText) === null && 'Amount',
  ].filter(Boolean) as string[];
  const signErrors = [
    signatureIsBlank(signature) && 'Signature',
    !isIsoDate(form.signedDate) && 'Date signed',
    !form.signerRelationship.trim() && 'Relationship to patient',
  ].filter(Boolean) as string[];
  const formGaps = [
    !form.patientAddress.trim() && "3a Patient's address",
    form.relationship === null && '4 Relationship to sponsor',
    !isIsoDate(form.patientDob) && '5 Date of birth',
    form.sex === null && '6 Sex',
    (form.accidentRelated === null || form.workRelated === null) && '7 Accident / work related',
    !form.description.trim() && '8a Description of illness or injury',
    form.careType === null && '8b Type of care',
    !form.sponsorId.trim() && "10 Sponsor's SSN or DBN",
    form.hasOtherInsurance === null && '11 Other health insurance',
  ].filter(Boolean) as string[];

  const fileName = (part?: string) => claimFileName(form.patientName.lastName, provider, dateOfService, part);

  /** Moves between steps. The review step cannot be reached unsigned. */
  const goTo = (target: number) => {
    const next = Math.max(VISIT, Math.min(REVIEW, target));
    setMessage('');
    setStep(next === REVIEW && signErrors.length ? SIGN : next);
  };
  // Each step starts at the top.
  useEffect(() => { top.current?.scrollIntoView({ block: 'start' }); }, [step]);

  const selectPatient = (id: string) => {
    const dep = dependents.find((d) => d.id === id);
    setPatientId(id);
    setFormState((f) =>
      id === SPONSOR_PATIENT_ID && sponsor ? prefillForSponsor(sponsor, f)
      : dep ? prefillForDependent(sponsor ?? emptySponsor(), dep, f)
      : f,
    );
    invalidate();
  };

  const relabel = (list: Attachment[]) => list.map((a, i) => ({ ...a, label: `${a.label.split(' #')[0]} #${i + 1}` }));
  const addAttachment = (kind: AttachmentKind, label: string, data: Uint8Array) => {
    setAttachments((list) => relabel([...list, { id: newId(), kind, label, category: 'BILL', data }]));
    invalidate();
  };

  const addFiles = async (files: File[]) => {
    if (!files.length) return;
    setBusy(true);
    let failed = 0;
    for (const file of files) {
      try {
        const data = await readFile(file);
        const isPdf = file.type === 'application/pdf' || /\.pdf$/i.test(file.name);
        if (isPdf) {
          await pdfPageCount(data);
          addAttachment('PDF', 'PDF', data);
        } else {
          await assertReadableImage(data);
          addAttachment('IMAGE', 'Image', data);
        }
      } catch {
        failed++;
      }
    }
    setBusy(false);
    setMessage(failed ? `${failed} file(s) could not be read and were skipped (unsupported format, password-protected or damaged).` : '');
  };

  /** Fills and signs the form, writes the output file(s) and records the claim in the tracker. */
  const build = async (mode: OutputMode = outputMode) => {
    if (busy) return;
    if (!signature || signErrors.length) {
      // Hard rule: nothing is generated without a signature.
      setStep(SIGN);
      setMessage('Sign the form before the packet can be created.');
      return;
    }
    setBusy(true);
    setOutputs([]);
    setSavedTo('');
    setMessage('');
    try {
      const filled = await fillForm(await loadTemplate(), form, signature);
      const built = mode === 'COMBINED'
        ? [await buildPacket(filled.bytes, attachments, fileName(), encodeImage)]
        : await buildSeparate(filled.bytes, attachments, (part) => fileName(part), encodeImage);
      const existing = store.data.claims.find((c) => c.id === claimId);
      const record: ClaimRecord = {
        ...(existing ?? { id: claimId, dossierUri: '', isFiled: false, filedDate: null, isReimbursed: false, reimbursedDate: null }),
        patientId: patientId ?? MANUAL_PATIENT_ID,
        patientName: displayName(form.patientName),
        provider: provider.trim(),
        dateOfService,
        amountMinor: parseAmountMinor(amountText) ?? 0,
        currency,
        isPacketGenerated: true,
        notes: notes.trim(),
      };
      await store.saveClaim(record);
      setWarnings(filled.warnings);
      setOutputs(built);
    } catch (e) {
      setMessage(`The packet could not be built. Check the attached files and try again. (${(e as Error).message})`);
    } finally {
      setBusy(false);
    }
  };

  useEffect(() => {
    if (step === REVIEW && outputs.length === 0 && !busy) void build();
  }, [step]);

  const back = () => {
    setMessage('');
    if (step > VISIT) setStep(step - 1);
    else if (provider.trim() || attachments.length) setConfirmLeave(true);
    else onClose();
  };

  return (
    <div class="screen wizard">
      <div ref={top} />
      <header class="screen-head">
        <button type="button" onClick={back} aria-label="Back">← Back</button>
        <h2>{step + 1} of 5 · {TITLES[step]}</h2>
      </header>
      <progress max={5} value={step + 1} aria-label={`Step ${step + 1} of 5`} />
      {message && <p class="error" role="alert">{message}</p>}

      {step === VISIT && (
        <>
          <Section title="Who is the patient?">
            {!sponsor && dependents.length === 0 ? (
              <p class="muted">No family profiles saved yet. Set them up once under Family to pre-fill every claim, or type the details in below.</p>
            ) : (
              <div class="chip-row" role="group" aria-label="Patient">
                {sponsor && <button type="button" class="chip" aria-pressed={patientId === SPONSOR_PATIENT_ID} onClick={() => selectPatient(SPONSOR_PATIENT_ID)}>{sponsor.name.firstName || 'Sponsor'}</button>}
                {dependents.map((d) => (
                  <button type="button" class="chip" aria-pressed={patientId === d.id} onClick={() => selectPatient(d.id)}>{d.name.firstName || d.name.lastName}</button>
                ))}
              </div>
            )}
            <NameFields name={form.patientName} onChange={(patientName) => setForm({ patientName })} />
          </Section>
          <Section title="The visit">
            <Field label="Provider (doctor, clinic or pharmacy)" value={provider} onInput={(v) => { setProvider(v); invalidate(); }} />
            <DateField label="Date of service" value={dateOfService} onInput={(v) => { setDateOfService(v); invalidate(); }} />
            <Field label="Amount charged" inputMode="decimal" value={amountText} onInput={setAmountText} invalid={visitErrors.includes('Amount')} hint="For your tracker; the form itself has no amount box" />
            <Chips label="Currency" required options={CURRENCIES} selected={currency} text={(c) => c} onSelect={(c) => c && setCurrency(c)} />
            <Field label="Notes" multiline value={notes} onInput={setNotes} />
            {visitErrors.length > 0 && <p class="muted">Still needed: {visitErrors.join(', ')}</p>}
          </Section>
        </>
      )}

      {step === FORM && <FormStep form={form} setForm={setForm} />}

      {step === DOCUMENTS && (
        <DocumentsStep
          attachments={attachments}
          busy={busy}
          onCapture={(jpeg) => addAttachment('IMAGE', 'Photo', jpeg)}
          onFiles={addFiles}
          onChange={(list) => { setAttachments(relabel(list)); invalidate(); }}
        />
      )}

      {step === SIGN && (
        <SignStep
          store={store}
          form={form}
          setForm={setForm}
          signature={signature}
          signerName={signerName}
          errors={signErrors}
          onApply={(sig, name) => { setSignature(sig); setSignerName(name); invalidate(); }}
        />
      )}

      {step === REVIEW && (
        <ReviewStep
          outputs={outputs}
          busy={busy}
          warnings={warnings}
          formGaps={formGaps}
          outputMode={outputMode}
          savedTo={savedTo}
          onMode={(m) => { setOutputMode(m); void build(m); }}
          onRetry={() => void build()}
          onSaved={setSavedTo}
          onError={setMessage}
        />
      )}

      <footer class="wizard-nav">
        {step === VISIT && <button type="button" class="primary" disabled={visitErrors.length > 0} onClick={() => goTo(FORM)}>Next</button>}
        {step === FORM && <button type="button" class="primary" onClick={() => goTo(DOCUMENTS)}>Next</button>}
        {step === DOCUMENTS && <button type="button" class="primary" disabled={busy} onClick={() => goTo(SIGN)}>Next: sign</button>}
        {/* The packet cannot be created until the form is signed. */}
        {step === SIGN && <button type="button" class="primary" disabled={signErrors.length > 0} onClick={() => goTo(REVIEW)}>Build packet</button>}
        {step === REVIEW && <button type="button" class="primary" onClick={onClose}>Done</button>}
      </footer>

      {confirmLeave && (
        <Confirm
          title="Discard this claim?"
          body="What you entered and any attached documents will be discarded."
          confirmLabel="Discard"
          danger
          onCancel={() => setConfirmLeave(false)}
          onConfirm={onClose}
        />
      )}
    </div>
  );
}

function FormStep({ form: f, setForm }: { form: ClaimForm; setForm: (patch: Partial<ClaimForm>) => void }) {
  const insurance = (title: string, e: InsuranceEntry, onChange: (e: InsuranceEntry) => void) => (
    <>
      <h4>{title}</h4>
      <Field label="11d Name and address" multiline value={e.nameAndAddress} onInput={(v) => onChange({ ...e, nameAndAddress: v })} />
      <Field label="11e Identification number" value={e.idNumber} onInput={(v) => onChange({ ...e, idNumber: v })} />
      <DateField label="11f Effective date" value={e.effectiveDate} onInput={(v) => onChange({ ...e, effectiveDate: v })} />
      <YesNo label="11g Drug coverage?" value={e.drugCoverage} onChange={(v) => onChange({ ...e, drugCoverage: v })} />
    </>
  );
  return (
    <>
      <p class="muted">These are the blocks on page 2 of DD Form 2642. Pre-filled values can be changed for this claim only.</p>
      <Section title="2 · Patient's telephone">
        <div class="row">
          <Field class="narrow" label="Area/country" inputMode="tel" value={f.primaryPhone.areaCode} onInput={(v) => setForm({ primaryPhone: { ...f.primaryPhone, areaCode: v } })} />
          <Field class="grow" label="Primary number" inputMode="tel" value={f.primaryPhone.number} onInput={(v) => setForm({ primaryPhone: { ...f.primaryPhone, number: v } })} />
        </div>
        <div class="row">
          <Field class="narrow" label="Area/country" inputMode="tel" value={f.secondaryPhone.areaCode} onInput={(v) => setForm({ secondaryPhone: { ...f.secondaryPhone, areaCode: v } })} />
          <Field class="grow" label="Secondary number" inputMode="tel" value={f.secondaryPhone.number} onInput={(v) => setForm({ secondaryPhone: { ...f.secondaryPhone, number: v } })} />
        </div>
      </Section>
      <Section title="3 · Address">
        <Field label="3a Patient's address" multiline value={f.patientAddress} onInput={(v) => setForm({ patientAddress: v })} hint="Street, apt, city, state/country and ZIP. No PO boxes except rural routes." />
        <Field label="3b Overseas only: state/country where care was given" value={f.overseasLocation} onInput={(v) => setForm({ overseasLocation: v })} hint="Leave blank if the same as 3a" />
      </Section>
      <Section title="4–6 · Patient">
        <Chips label="4 Relationship to sponsor" options={RELATIONSHIPS} selected={f.relationship} text={(r) => RELATIONSHIP_LABELS[r]} onSelect={(relationship) => setForm({ relationship })} />
        {f.relationship === 'OTHER' && <Field label="Specify relationship" value={f.relationshipOther} onInput={(v) => setForm({ relationshipOther: v })} />}
        <DateField label="5 Date of birth" value={f.patientDob} onInput={(v) => setForm({ patientDob: v })} />
        <Chips label="6 Sex" options={SEXES} selected={f.sex} text={(s) => SEX_LABELS[s]} onSelect={(sex) => setForm({ sex })} />
      </Section>
      <Section title="7 · Is the condition…">
        <YesNo label="Accident related?" value={f.accidentRelated} onChange={(v) => setForm({ accidentRelated: v })} />
        <YesNo label="Work related?" value={f.workRelated} onChange={(v) => setForm({ workRelated: v })} />
        {(f.accidentRelated || f.workRelated) && <p class="error">If yes, the claims processor also requires DD Form 2527 (Statement of Personal Injury).</p>}
      </Section>
      <Section title="8 · Illness and care">
        <Field label="8a Illness, injury or symptoms treated" multiline value={f.description} onInput={(v) => setForm({ description: v })} hint={`If an injury, say how it happened. ${f.description.length} characters (about 600 fit comfortably).`} />
        <Chips label="8b Was the care" options={CARE} selected={f.careType} text={(c) => CARE_LABELS[c]} onSelect={(careType) => setForm({ careType })} />
        <div class="chips" role="group" aria-label="8c Overseas claims only">
          <span class="chips-label">8c Overseas claims only</span>
          <div class="chip-row">
            <button type="button" class="chip" aria-pressed={f.telemedicine} onClick={() => setForm({ telemedicine: !f.telemedicine })}>Telemedicine</button>
            <button type="button" class="chip" aria-pressed={f.urgentCare} onClick={() => setForm({ urgentCare: !f.urgentCare })}>Urgent care</button>
            <button type="button" class="chip" aria-pressed={f.telemedicineAudioOnly} onClick={() => setForm({ telemedicineAudioOnly: !f.telemedicineAudioOnly })}>Telemedicine, audio only</button>
          </div>
        </div>
        {f.telemedicineAudioOnly && <Field label="Reason for audio only" value={f.audioOnlyReason} onInput={(v) => setForm({ audioOnlyReason: v })} />}
      </Section>
      <Section title="9–10 · Sponsor">
        {f.relationship === 'SELF'
          ? <p class="muted">Block 9 will read "Same" because the patient is the sponsor.</p>
          : <NameFields name={f.sponsorName} onChange={(sponsorName) => setForm({ sponsorName })} />}
        <Field label="10 Sponsor's SSN or DoD Benefits Number" inputMode="numeric" value={f.sponsorId} onInput={(v) => setForm({ sponsorId: v })} />
      </Section>
      <Section title="11 · Other health insurance">
        <YesNo label="Is the patient covered by any other health insurance (including travel or national health insurance overseas)?" value={f.hasOtherInsurance} onChange={(v) => setForm({ hasOtherInsurance: v })} />
        {f.hasOtherInsurance && (
          <>
            <MultiChips label="11b Type of coverage" options={OHI} selected={f.ohiTypes} text={(o) => OHI_LABELS[o]} onChange={(ohiTypes) => setForm({ ohiTypes })} />
            {f.ohiTypes.includes('OTHER') && <Field label="Specify other coverage" value={f.ohiOtherSpecify} onInput={(v) => setForm({ ohiOtherSpecify: v })} />}
            <MultiChips label="11c Overseas claims only" options={OVERSEAS_OHI} selected={f.overseasOhiTypes} text={(o) => OVERSEAS_OHI_LABELS[o]} onChange={(overseasOhiTypes) => setForm({ overseasOhiTypes })} />
            {insurance('Insurance 1', f.insurance1, (insurance1) => setForm({ insurance1 }))}
            {insurance('Insurance 2 (if any)', f.insurance2, (insurance2) => setForm({ insurance2 }))}
            <p class="muted">Remember to attach the other insurer's Explanation of Benefits.</p>
          </>
        )}
      </Section>
      <Section title="13 · Overseas claims only">
        <Chips label="Payment in" options={PAYMENT} selected={f.paymentCurrency} text={(p) => PAYMENT_LABELS[p]} onSelect={(paymentCurrency) => setForm({ paymentCurrency })} />
        <YesNo label="Did you make payment to the provider?" value={f.paidProvider} onChange={(v) => setForm({ paidProvider: v })} />
      </Section>
    </>
  );
}

function DocumentsStep(p: {
  attachments: Attachment[];
  busy: boolean;
  onCapture: (jpeg: Uint8Array) => void;
  onFiles: (files: File[]) => void;
  onChange: (list: Attachment[]) => void;
}) {
  const [camera, setCamera] = useState(false);
  const [dragging, setDragging] = useState(false);
  const input = useRef<HTMLInputElement>(null);
  const move = (i: number, delta: number) => {
    const list = [...p.attachments];
    const [item] = list.splice(i, 1);
    list.splice(i + delta, 0, item);
    p.onChange(list);
  };
  return (
    <>
      <p>Add the itemized bill, pharmacy receipts and proof of payment, and mark each one as a bill or a receipt. In a combined packet they follow the form in the order shown here; as separate files, bills and receipts each get their own PDF.</p>
      <div
        class={`dropzone ${dragging ? 'dragging' : ''}`}
        onDragOver={(e) => { e.preventDefault(); setDragging(true); }}
        onDragLeave={() => setDragging(false)}
        onDrop={(e) => { e.preventDefault(); setDragging(false); p.onFiles([...(e.dataTransfer?.files ?? [])]); }}
      >
        <div class="actions">
          <button type="button" class="primary" onClick={() => setCamera(true)}>Use camera</button>
          <button type="button" onClick={() => input.current?.click()}>Upload files</button>
        </div>
        <small>Photos, screenshots or PDFs. On a computer you can also drag files onto this box.</small>
        <input
          ref={input}
          type="file"
          multiple
          hidden
          accept="image/*,application/pdf,.pdf"
          aria-label="Files to attach"
          onChange={(e) => {
            const el = e.target as HTMLInputElement;
            p.onFiles([...(el.files ?? [])]);
            el.value = '';
          }}
        />
      </div>
      {p.busy && <p class="muted" role="status">Reading files…</p>}
      {p.attachments.length === 0 && !p.busy && <p class="muted">Nothing attached yet. A claim without an itemized bill will be returned.</p>}
      <ul class="cards">
        {p.attachments.map((a, i) => (
          <li class="card" key={a.id}>
            <div class="card-head">
              <div>
                <strong>{a.label}</strong>
                <div class="muted">{a.kind === 'PDF' ? 'PDF' : 'Image'} · {formatSize(a.data.length)}</div>
              </div>
              <div class="card-actions">
                <button type="button" disabled={i === 0} onClick={() => move(i, -1)} aria-label={`Move ${a.label} up`}>↑</button>
                <button type="button" disabled={i === p.attachments.length - 1} onClick={() => move(i, 1)} aria-label={`Move ${a.label} down`}>↓</button>
                <button type="button" onClick={() => p.onChange(p.attachments.filter((x) => x.id !== a.id))} aria-label={`Remove ${a.label}`}>Remove</button>
              </div>
            </div>
            <div class="chip-row" role="group" aria-label={`Type of ${a.label}`}>
              {CATEGORIES.map((c) => (
                <button type="button" class="chip" aria-pressed={a.category === c} onClick={() => p.onChange(p.attachments.map((x) => (x.id === a.id ? { ...x, category: c } : x)))}>
                  {CATEGORY_LABELS[c]}
                </button>
              ))}
            </div>
          </li>
        ))}
      </ul>
      {camera && <CameraDialog onCapture={p.onCapture} onClose={() => setCamera(false)} />}
    </>
  );
}

function SignStep(p: {
  store: AppStore;
  form: ClaimForm;
  setForm: (patch: Partial<ClaimForm>) => void;
  signature: Signature | null;
  signerName: string;
  errors: string[];
  onApply: (sig: Signature | null, name: string) => void;
}) {
  const [strokes, setStrokes] = useState<SignaturePoint[][]>([]);
  const [name, setName] = useState('');
  const [save, setSave] = useState(true);
  const [drawing, setDrawing] = useState(false);
  const saved = p.store.data.signatures;
  const applied = p.signature && !drawing;
  return (
    <>
      <p>Block 12 certifies that the claim is correct. The form must be signed here before the packet can be created.</p>
      <p class="muted">The patient signs; a parent or guardian signs for a child under 18.</p>
      <Section title="12a · Signature">
        {applied ? (
          <>
            <div class="card">
              <strong>{p.signerName ? `Signed by ${p.signerName}` : 'Signature applied'}</strong>
              <SignaturePreview signature={p.signature!} />
            </div>
            <button type="button" class="link" onClick={() => { setStrokes([]); setName(p.signerName); setDrawing(true); }}>Sign again</button>
          </>
        ) : (
          <>
            {saved.length > 0 && !drawing && (
              <>
                <span class="chips-label">Use a saved signature</span>
                <div class="chip-row" role="group" aria-label="Saved signatures">
                  {saved.map((s) => <button type="button" class="chip" onClick={() => p.onApply(s.signature, s.signerName)}>{s.signerName}</button>)}
                </div>
                <span class="chips-label">Or sign below</span>
              </>
            )}
            <SignaturePad strokes={strokes} onChange={setStrokes} />
            <Field label="Signer's full name" value={name} onInput={setName} hint="Used to label the saved signature" />
            <label class="check">
              <input type="checkbox" checked={save} onChange={(e) => setSave((e.target as HTMLInputElement).checked)} />
              <span>Save this signature (encrypted in this browser) for future claims</span>
            </label>
            <div class="actions">
              <button
                type="button"
                class="primary"
                disabled={signatureIsBlank({ strokes }) || (save && !name.trim())}
                onClick={async () => {
                  const sig = { strokes };
                  if (save && name.trim()) await p.store.saveSignature(name, sig);
                  p.onApply(sig, name.trim());
                  setDrawing(false);
                }}
              >
                Use this signature
              </button>
              <button type="button" disabled={strokes.length === 0} onClick={() => setStrokes([])}>Clear</button>
              {drawing && p.signature && <button type="button" class="link" onClick={() => setDrawing(false)}>Cancel</button>}
            </div>
          </>
        )}
      </Section>
      <Section title="12b–c">
        <DateField label="12b Date signed" value={p.form.signedDate} onInput={(v) => p.setForm({ signedDate: v })} />
        <Field label="12c Signer's relationship to patient" value={p.form.signerRelationship} onInput={(v) => p.setForm({ signerRelationship: v })} hint="e.g. Self, Parent, Spouse" />
        {p.errors.length > 0 && <p class="error">Still needed: {p.errors.join(', ')}</p>}
      </Section>
    </>
  );
}

function ReviewStep(p: {
  outputs: PacketResult[];
  busy: boolean;
  warnings: string[];
  formGaps: string[];
  outputMode: OutputMode;
  savedTo: string;
  onMode: (m: OutputMode) => void;
  onRetry: () => void;
  onSaved: (where: string) => void;
  onError: (message: string) => void;
}) {
  const [review, setReview] = useState(false);
  const files: OutFile[] = useMemo(() => p.outputs.map((o) => ({ fileName: o.fileName, bytes: o.bytes })), [p.outputs]);
  const previews = useMemo(() => p.outputs.map((o) => URL.createObjectURL(new Blob([o.bytes], { type: 'application/pdf' }))), [p.outputs]);
  useEffect(() => () => previews.forEach((u) => URL.revokeObjectURL(u)), [previews]);
  const separate = p.outputs.length > 1;
  const total = p.outputs.reduce((n, o) => n + o.bytes.length, 0);

  const run = async (action: () => Promise<string | null>) => {
    setReview(false);
    try {
      const where = await action();
      if (where) p.onSaved(where);
    } catch {
      p.onError('Saving failed. The destination may be read-only or unavailable; try another option.');
    }
  };

  return (
    <>
      <Chips label="Save as" required options={MODES} selected={p.outputMode} text={(m) => OUTPUT_LABELS[m]} onSelect={(m) => m && m !== p.outputMode && p.onMode(m)} />
      {p.outputs.length === 0 ? (
        p.busy ? <p role="status">Building your packet…</p> : (
          <>
            <p>The packet has not been built.</p>
            <button type="button" class="primary" onClick={p.onRetry}>Try again</button>
          </>
        )
      ) : (
        <>
          {p.savedTo && <p class="card" role="status">Saved to {p.savedTo}. The claim is in your tracker under "Needs Filing".</p>}
          <ul class="files">
            {p.outputs.map((o) => (
              <li>
                <strong>{o.fileName}</strong>
                <div class="muted">{o.label} · {o.pageCount} page(s) · {formatSize(o.bytes.length)}</div>
                {!o.withinLimit && <p class="error">Larger than the usual 10 MB portal limit even after compression. Remove some pages or split the claim.</p>}
              </li>
            ))}
          </ul>
          {p.warnings.map((w) => <p class="error">{w}</p>)}
          {p.formGaps.length > 0 && <p class="muted">Left blank on the form: {p.formGaps.join('; ')}. Go back to fill them in if they apply.</p>}
          <div class="actions">
            <button type="button" class="primary" disabled={p.busy} onClick={() => setReview(true)}>{separate ? 'Save files' : 'Save packet'}</button>
            {canShareFiles(files) && <button type="button" disabled={p.busy} onClick={() => run(async () => ((await shareFiles(files)) ? 'the app you shared with' : null))}>Share</button>}
          </div>
          {p.outputs.map((o, i) => (
            <div class="preview">
              <div class="preview-head">
                <span>{separate ? o.label : 'Preview'}</span>
                <a href={previews[i]} target="_blank" rel="noopener">Open in a new tab</a>
              </div>
              <iframe src={previews[i]} title={`Preview of ${o.fileName}`} />
            </div>
          ))}
        </>
      )}
      {review && (
        <Dialog
          title="Review before saving"
          onClose={() => setReview(false)}
          actions={
            <>
              <button type="button" onClick={() => setReview(false)}>Cancel</button>
              {canPickFolder() && <button type="button" onClick={() => run(async () => { const dir = await saveToFolder(files); return dir ? `the folder "${dir}"` : null; })}>Choose folder…</button>}
              <button type="button" class="primary" onClick={() => run(async () => { await downloadAll(files); return 'your Downloads folder'; })}>Download</button>
            </>
          }
        >
          <dl class="review">
            <dt>Destination</dt>
            <dd>{canPickFolder() ? 'Your browser\'s Downloads folder, or a folder you choose' : 'Your browser\'s Downloads folder'}</dd>
            {p.outputs.map((o) => (
              <>
                <dt>{separate ? o.label : 'File name'}</dt>
                <dd>{o.fileName}<br />{o.pageCount} page(s) · {formatSize(o.bytes.length)}</dd>
              </>
            ))}
            {separate && (<><dt>Total</dt><dd>{p.outputs.length} files · {formatSize(total)}</dd></>)}
          </dl>
        </Dialog>
      )}
    </>
  );
}
