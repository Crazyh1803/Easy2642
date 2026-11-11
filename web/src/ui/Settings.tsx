import { useRef, useState } from 'preact/hooks';
import { downloadBytes } from '../browser/files';
import { OUTPUT_LABELS, OutputMode, todayIso } from '../core/models';
import { AppStore } from '../store/appStore';
import { MIN_PASSCODE_LENGTH } from '../store/vault';
import { Confirm, DISCLAIMER, Dialog, Field, LINKS, PRIVACY_SUMMARY } from './components';

const MODES: OutputMode[] = ['COMBINED', 'SEPARATE'];

function Row(p: { title: string; detail: string; onClick: () => void; danger?: boolean }) {
  return (
    <button type="button" class={`settings-row ${p.danger ? 'danger-text' : ''}`} onClick={p.onClick}>
      <span class="settings-title">{p.title}</span>
      <span class="muted">{p.detail}</span>
    </button>
  );
}

export function SettingsScreen({ store, onLock }: { store: AppStore; onLock: () => void }) {
  const [mode, setMode] = useState(store.settings.outputMode);
  const [dialog, setDialog] = useState<'output' | 'privacy' | 'disclaimer' | 'backup' | 'clear' | 'passcode' | null>(null);
  const [restoreText, setRestoreText] = useState<string | null>(null);
  const [status, setStatus] = useState('');
  const [newPass, setNewPass] = useState('');
  const [newPass2, setNewPass2] = useState('');
  const [passError, setPassError] = useState('');
  const fileInput = useRef<HTMLInputElement>(null);

  const changePasscode = async () => {
    if (newPass.length < MIN_PASSCODE_LENGTH) return setPassError(`Use at least ${MIN_PASSCODE_LENGTH} characters.`);
    if (newPass !== newPass2) return setPassError('The two passcodes do not match.');
    await store.changePasscode(newPass);
    setNewPass(''); setNewPass2(''); setPassError(''); setDialog(null);
    setStatus('Passcode changed');
  };

  return (
    <div class="screen">
      <header class="screen-head"><h2>Settings</h2></header>
      {status && <p class="muted" role="status">{status}</p>}

      <h3 class="group">Saving claims</h3>
      <Row title="Default output" detail={mode === 'COMBINED' ? 'One combined PDF' : 'Separate form, bill and receipt files'} onClick={() => setDialog('output')} />

      <h3 class="group">Your data</h3>
      <Row title="Export backup" detail="Save profiles and claims to a file" onClick={() => setDialog('backup')} />
      <Row title="Restore from backup" detail="Replace everything with a backup file (from this site or the Android app)" onClick={() => fileInput.current?.click()} />
      <input
        ref={fileInput}
        type="file"
        accept="application/json,.json,text/plain"
        hidden
        aria-label="Backup file to restore"
        onChange={async (e) => {
          const input = e.target as HTMLInputElement;
          const file = input.files?.[0];
          input.value = '';
          if (file) setRestoreText(await file.text());
        }}
      />
      <Row title="Change passcode" detail="Re-encrypts your saved data under a new passcode" onClick={() => setDialog('passcode')} />
      <Row title="Lock now" detail="Requires the passcode to open again" onClick={onLock} />
      <Row title="Clear all local data" detail="Erase profiles, claims, signatures, passcode and settings" danger onClick={() => setDialog('clear')} />

      <h3 class="group">About</h3>
      <Row title="Privacy" detail="Nothing leaves this device unless you send it" onClick={() => setDialog('privacy')} />
      <Row title="Disclaimer" detail="Independent tool, not affiliated with TRICARE, DHA or DoD" onClick={() => setDialog('disclaimer')} />
      <a class="settings-row" href={LINKS.author} target="_blank" rel="noopener noreferrer">
        <span class="settings-title">About the author</span><span class="muted">appsbydan.com (opens in a new tab)</span>
      </a>
      <a class="settings-row" href={LINKS.tip} target="_blank" rel="noopener noreferrer">
        <span class="settings-title">Tip the developer</span><span class="muted">Optional. Opens in a new tab</span>
      </a>
      <p class="muted version">2642 Easy for web 1.0.0 · DD Form 2642, SEP 2024 edition</p>

      {dialog === 'output' && (
        <Dialog title="Default output" onClose={() => setDialog(null)}>
          {MODES.map((m) => (
            <label class="check">
              <input type="radio" name="output" checked={mode === m} onChange={() => { store.settings.outputMode = m; setMode(m); }} />
              <span>
                {OUTPUT_LABELS[m]}
                <small>{m === 'COMBINED' ? 'Form, bill and receipts in one file' : 'DD 2642, bill and receipt / proof of payment as three files'}</small>
              </span>
            </label>
          ))}
          <p class="muted">You can still switch for an individual claim on its review step.</p>
        </Dialog>
      )}
      {(dialog === 'privacy' || dialog === 'disclaimer') && (
        <Dialog
          title={dialog === 'privacy' ? 'Privacy' : 'Disclaimer'}
          onClose={() => setDialog(null)}
          actions={
            <>
              {dialog === 'privacy' && <a class="button" href={LINKS.privacy} target="_blank" rel="noopener noreferrer">Read the full policy</a>}
              <button type="button" onClick={() => setDialog(null)}>Close</button>
            </>
          }
        >
          <p>{dialog === 'privacy' ? PRIVACY_SUMMARY : DISCLAIMER}</p>
        </Dialog>
      )}
      {dialog === 'backup' && (
        <Confirm
          title="Export backup"
          body="The backup file contains your family profiles, claim list and saved signatures as readable, unencrypted text, including the sponsor's SSN or DBN. Save it only somewhere you trust."
          confirmLabel="Download backup"
          onCancel={() => setDialog(null)}
          onConfirm={() => {
            downloadBytes(store.exportBackup(), `2642Easy_Backup_${todayIso().replace(/-/g, '')}.json`, 'application/json');
            setDialog(null);
            setStatus('Backup downloaded');
          }}
        />
      )}
      {restoreText !== null && (
        <Confirm
          title="Restore from backup?"
          body="This replaces all profiles, claims and saved signatures currently in the app with the contents of the backup file."
          confirmLabel="Replace my data"
          danger
          onCancel={() => setRestoreText(null)}
          onConfirm={async () => {
            const text = restoreText;
            setRestoreText(null);
            try {
              await store.importBackup(text);
              setStatus('Backup restored');
            } catch {
              setStatus('That file is not a valid 2642 Easy backup. Nothing was changed.');
            }
          }}
        />
      )}
      {dialog === 'clear' && (
        <Confirm
          title="Clear all local data?"
          body="This permanently erases every profile, every claim in the tracker, saved signatures, your passcode and all settings from this browser. PDFs you already saved are not touched. This cannot be undone."
          confirmLabel="Erase everything"
          danger
          onCancel={() => setDialog(null)}
          onConfirm={() => store.clearAll()}
        />
      )}
      {dialog === 'passcode' && (
        <Dialog
          title="Change passcode"
          onClose={() => setDialog(null)}
          actions={
            <>
              <button type="button" onClick={() => setDialog(null)}>Cancel</button>
              <button type="button" class="primary" onClick={changePasscode}>Change passcode</button>
            </>
          }
        >
          <Field label="New passcode" type="password" value={newPass} onInput={setNewPass} autoComplete="new-password" />
          <Field label="Type it again" type="password" value={newPass2} onInput={setNewPass2} autoComplete="new-password" />
          {passError && <p class="error" role="alert">{passError}</p>}
        </Dialog>
      )}
    </div>
  );
}
