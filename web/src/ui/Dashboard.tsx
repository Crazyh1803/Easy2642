import { useState } from 'preact/hooks';
import {
  ClaimFilter, ClaimRecord, ClaimState, SPONSOR_PATIENT_ID, STATE_LABELS, claimState, formatAmount, matchesFilter, todayIso,
  withFiled, withReimbursed,
} from '../core/models';
import { AppStore } from '../store/appStore';
import { Confirm, Dialog } from './components';

const STATES: ClaimState[] = ['NEEDS_FILING', 'AWAITING_REIMBURSEMENT', 'COMPLETED'];

export function Dashboard({ store, onNewClaim }: { store: AppStore; onNewClaim: () => void }) {
  const [filter, setFilter] = useState<ClaimFilter>({ patientId: null, state: null, query: '' });
  const [notesFor, setNotesFor] = useState<ClaimRecord | null>(null);
  const [notes, setNotes] = useState('');
  const [deleting, setDeleting] = useState<ClaimRecord | null>(null);
  const { sponsor, dependents, claims } = store.data;

  const known = [
    ...(sponsor ? [{ id: SPONSOR_PATIENT_ID, name: sponsor.name.firstName || 'Sponsor' }] : []),
    ...dependents.map((d) => ({ id: d.id, name: d.name.firstName || d.name.lastName })),
  ];
  // Claims may reference a patient whose profile was since removed.
  const orphans = claims
    .filter((c) => !known.some((k) => k.id === c.patientId))
    .filter((c, i, all) => all.findIndex((x) => x.patientId === c.patientId) === i)
    .map((c) => ({ id: c.patientId, name: c.patientName.split(' ')[0] || 'Other' }));
  const patients = [...known, ...orphans];
  const shown = claims.filter((c) => matchesFilter(filter, c));

  return (
    <div class="screen">
      <header class="screen-head">
        <h2>Claims</h2>
        <button type="button" class="primary" onClick={onNewClaim}>New claim</button>
      </header>
      <input
        type="search"
        class="search"
        placeholder="Search provider or notes"
        aria-label="Search provider or notes"
        value={filter.query}
        onInput={(e) => setFilter({ ...filter, query: (e.target as HTMLInputElement).value })}
      />
      <div class="chip-row" role="group" aria-label="Filter by patient">
        <button type="button" class="chip" aria-pressed={filter.patientId === null} onClick={() => setFilter({ ...filter, patientId: null })}>All</button>
        {patients.map((p) => (
          <button type="button" class="chip" aria-pressed={filter.patientId === p.id} onClick={() => setFilter({ ...filter, patientId: p.id })}>{p.name}</button>
        ))}
      </div>
      <div class="chip-row" role="group" aria-label="Filter by status">
        <button type="button" class="chip" aria-pressed={filter.state === null} onClick={() => setFilter({ ...filter, state: null })}>All</button>
        {STATES.map((s) => (
          <button type="button" class="chip" aria-pressed={filter.state === s} onClick={() => setFilter({ ...filter, state: s })}>{STATE_LABELS[s]}</button>
        ))}
      </div>

      {shown.length === 0 ? (
        <p class="empty">
          {claims.length === 0 ? 'No claims yet. Choose "New claim" to prepare your first DD Form 2642 packet.' : 'No claims match these filters.'}
        </p>
      ) : (
        <ul class="cards">
          {shown.map((c) => (
            <li class="card" key={c.id}>
              <div class="card-head">
                <div>
                  <strong>{c.provider || 'Unnamed provider'}</strong>
                  <div class="muted">{[c.patientName, c.dateOfService, formatAmount(c)].filter(Boolean).join(' · ')}</div>
                  <div class="state">{STATE_LABELS[claimState(c)]}</div>
                </div>
                <div class="card-actions">
                  <button type="button" onClick={() => { setNotes(c.notes); setNotesFor(c); }}>Notes</button>
                  <button type="button" onClick={() => setDeleting(c)} aria-label={`Delete claim for ${c.provider}`}>Delete</button>
                </div>
              </div>
              <label class="check">
                <input type="checkbox" checked={c.isFiled} onChange={(e) => store.saveClaim(withFiled(c, (e.target as HTMLInputElement).checked, todayIso()))} />
                <span>Filed Online{c.isFiled && c.filedDate ? ` · ${c.filedDate}` : ''}</span>
              </label>
              <label class="check">
                <input type="checkbox" checked={c.isReimbursed} onChange={(e) => store.saveClaim(withReimbursed(c, (e.target as HTMLInputElement).checked, todayIso()))} />
                <span>Reimbursed{c.isReimbursed && c.reimbursedDate ? ` · ${c.reimbursedDate}` : ''}</span>
              </label>
              {c.notes && <p class="notes">{c.notes}</p>}
            </li>
          ))}
        </ul>
      )}

      {notesFor && (
        <Dialog
          title="Notes"
          onClose={() => setNotesFor(null)}
          actions={
            <>
              <button type="button" onClick={() => setNotesFor(null)}>Cancel</button>
              <button type="button" class="primary" onClick={() => { store.saveClaim({ ...notesFor, notes: notes.trim() }); setNotesFor(null); }}>Save</button>
            </>
          }
        >
          <textarea rows={4} aria-label="Notes" value={notes} onInput={(e) => setNotes((e.target as HTMLTextAreaElement).value)} />
        </Dialog>
      )}
      {deleting && (
        <Confirm
          title="Delete this claim?"
          body="The tracker entry is removed. PDFs you already saved stay where you saved them."
          confirmLabel="Delete"
          danger
          onCancel={() => setDeleting(null)}
          onConfirm={() => { store.deleteClaim(deleting.id); setDeleting(null); }}
        />
      )}
    </div>
  );
}
