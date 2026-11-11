import { useEffect, useState } from 'preact/hooks';
import { Dependent, RELATIONSHIP_LABELS, Relationship, SEX_LABELS, Sex, Sponsor, displayName, emptyName, emptySponsor, newId, PersonName, SavedSignature } from '../core/models';
import { AppStore } from '../store/appStore';
import { Chips, Confirm, DateField, Dialog, Field, Section } from './components';
import { SignaturePreview } from './SignaturePad';

const SEXES: Sex[] = ['M', 'F'];
const DEPENDENT_RELATIONSHIPS: Relationship[] = ['SPOUSE', 'CHILD', 'STEPCHILD', 'FORMER_SPOUSE', 'OTHER'];

export function NameFields({ name, onChange }: { name: PersonName; onChange: (n: PersonName) => void }) {
  return (
    <>
      <Field label="Last name" value={name.lastName} onInput={(v) => onChange({ ...name, lastName: v })} />
      <div class="row">
        <Field class="grow" label="First name" value={name.firstName} onInput={(v) => onChange({ ...name, firstName: v })} />
        <Field class="narrow" label="MI" value={name.middleInitial} maxLength={1} onInput={(v) => onChange({ ...name, middleInitial: v.slice(0, 1).toUpperCase() })} />
      </div>
    </>
  );
}

export function Family({ store }: { store: AppStore }) {
  const [sponsor, setSponsor] = useState<Sponsor>(store.data.sponsor ?? emptySponsor());
  const [saved, setSaved] = useState(false);
  const [editing, setEditing] = useState<Dependent | null>(null);
  const [removing, setRemoving] = useState<Dependent | null>(null);
  const [removingSig, setRemovingSig] = useState<SavedSignature | null>(null);
  const set = (patch: Partial<Sponsor>) => { setSponsor({ ...sponsor, ...patch }); setSaved(false); };

  // Pick up a restored backup while this screen is open.
  useEffect(() => { if (store.data.sponsor) setSponsor(store.data.sponsor); }, [store.data.sponsor]);

  return (
    <div class="screen">
      <header class="screen-head"><h2>Family profiles</h2></header>
      <p class="muted">Saved once, encrypted in this browser, and reused to pre-fill blocks 1–6, 9 and 10 of every new claim.</p>

      <Section title="Sponsor">
        <NameFields name={sponsor.name} onChange={(name) => set({ name })} />
        <Field label="DoD Benefits Number (DBN) or SSN" inputMode="numeric" value={sponsor.dodIdOrSsn} onInput={(v) => set({ dodIdOrSsn: v })} />
        <DateField label="Date of birth" value={sponsor.dob} onInput={(v) => set({ dob: v })} />
        <Chips label="Sex" options={SEXES} selected={sponsor.sex} text={(s) => SEX_LABELS[s]} onSelect={(sex) => set({ sex })} />
        <Field label="Branch of service" value={sponsor.branchOfService} onInput={(v) => set({ branchOfService: v })} hint="For your records; not printed on the form" />
        <Field label="Duty status" value={sponsor.dutyStatus} onInput={(v) => set({ dutyStatus: v })} hint="For your records; not printed on the form" />
        <Field label="Street / APO / FPO address" value={sponsor.streetAddress} onInput={(v) => set({ streetAddress: v })} />
        <Field label="City (or APO / FPO)" value={sponsor.city} onInput={(v) => set({ city: v })} />
        <div class="row">
          <Field class="grow" label="State / country" value={sponsor.state} onInput={(v) => set({ state: v })} />
          <Field class="grow" label="ZIP" inputMode="numeric" value={sponsor.zipCode} onInput={(v) => set({ zipCode: v })} />
        </div>
        <Field label="Phone (with area/country code)" inputMode="tel" value={sponsor.phoneNumber} onInput={(v) => set({ phoneNumber: v })} hint="e.g. (555) 010-0100 or +49 631 5550100" />
        <div class="actions">
          <button type="button" class="primary" onClick={async () => { await store.saveSponsor(sponsor); setSaved(true); }}>Save sponsor</button>
          {saved && <span class="muted" role="status">Sponsor saved</span>}
        </div>
      </Section>

      <Section title="Dependents">
        {store.data.dependents.length === 0 && <p class="muted">No dependents added yet.</p>}
        <ul class="cards">
          {store.data.dependents.map((d) => (
            <li class="card card-head" key={d.id}>
              <div>
                <strong>{displayName(d.name) || 'Unnamed'}</strong>
                <div class="muted">{[RELATIONSHIP_LABELS[d.relationship], d.dob].filter(Boolean).join(' · ')}</div>
              </div>
              <div class="card-actions">
                <button type="button" onClick={() => setEditing(d)} aria-label={`Edit ${displayName(d.name)}`}>Edit</button>
                <button type="button" onClick={() => setRemoving(d)} aria-label={`Remove ${displayName(d.name)}`}>Remove</button>
              </div>
            </li>
          ))}
        </ul>
        <button
          type="button"
          onClick={() =>
            // Most families use the sponsor's number for every member, so start from it.
            setEditing({ id: newId(), relationship: 'SPOUSE', name: { ...emptyName(), lastName: sponsor.name.lastName }, dob: '', sex: null, dodId: sponsor.dodIdOrSsn })
          }
        >
          Add dependent
        </button>
      </Section>

      <Section title="Saved signatures">
        {store.data.signatures.length === 0 && <p class="muted">None yet. You can save one when you sign your first claim.</p>}
        <ul class="cards">
          {store.data.signatures.map((s) => (
            <li class="card card-head" key={s.id}>
              <div class="grow">
                <strong>{s.signerName}</strong>
                <SignaturePreview signature={s.signature} />
              </div>
              <div class="card-actions">
                <button type="button" onClick={() => setRemovingSig(s)} aria-label={`Delete ${s.signerName}'s saved signature`}>Delete</button>
              </div>
            </li>
          ))}
        </ul>
      </Section>

      {editing && <DependentDialog initial={editing} sponsorNumber={sponsor.dodIdOrSsn} onCancel={() => setEditing(null)} onSave={async (d) => { await store.saveDependent(d); setEditing(null); }} />}
      {removing && (
        <Confirm
          title={`Remove ${displayName(removing.name)}?`}
          body="Existing claims for this person stay in the tracker."
          confirmLabel="Remove"
          danger
          onCancel={() => setRemoving(null)}
          onConfirm={() => { store.deleteDependent(removing.id); setRemoving(null); }}
        />
      )}
      {removingSig && (
        <Confirm
          title={`Delete ${removingSig.signerName}'s signature?`}
          body="It will have to be drawn again for the next claim."
          confirmLabel="Delete"
          danger
          onCancel={() => setRemovingSig(null)}
          onConfirm={() => { store.deleteSignature(removingSig.id); setRemovingSig(null); }}
        />
      )}
    </div>
  );
}

function DependentDialog(p: { initial: Dependent; sponsorNumber: string; onSave: (d: Dependent) => void; onCancel: () => void }) {
  const [d, setD] = useState(p.initial);
  return (
    <Dialog
      title="Dependent"
      onClose={p.onCancel}
      actions={
        <>
          <button type="button" onClick={p.onCancel}>Cancel</button>
          <button type="button" class="primary" disabled={!displayName(d.name)} onClick={() => p.onSave(d)}>Save</button>
        </>
      }
    >
      <NameFields name={d.name} onChange={(name) => setD({ ...d, name })} />
      <Chips label="Relationship to sponsor" required options={DEPENDENT_RELATIONSHIPS} selected={d.relationship} text={(r) => RELATIONSHIP_LABELS[r]} onSelect={(r) => r && setD({ ...d, relationship: r })} />
      <DateField label="Date of birth" value={d.dob} onInput={(dob) => setD({ ...d, dob })} />
      <Chips label="Sex" options={SEXES} selected={d.sex} text={(s) => SEX_LABELS[s]} onSelect={(sex) => setD({ ...d, sex })} />
      <Field label="DoD Benefits Number (DBN)" inputMode="numeric" value={d.dodId} onInput={(dodId) => setD({ ...d, dodId })} hint="Starts as the sponsor's number; change it if this person has their own" />
      {!d.dodId && p.sponsorNumber && <button type="button" class="link" onClick={() => setD({ ...d, dodId: p.sponsorNumber })}>Use sponsor's number</button>}
    </Dialog>
  );
}
