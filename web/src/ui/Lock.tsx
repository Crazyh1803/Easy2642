import { useState } from 'preact/hooks';
import { AppStore } from '../store/appStore';
import { MIN_PASSCODE_LENGTH, WrongPasscodeError } from '../store/vault';
import { Confirm, Field } from './components';

/** First use: choose a passcode. Afterwards: enter it to unlock. */
export function LockScreen({ store }: { store: AppStore }) {
  const creating = !store.hasVault;
  const [pass, setPass] = useState('');
  const [again, setAgain] = useState('');
  const [error, setError] = useState('');
  const [busy, setBusy] = useState(false);
  const [confirmErase, setConfirmErase] = useState(false);

  const submit = async (e: Event) => {
    e.preventDefault();
    setError('');
    if (creating) {
      if (pass.length < MIN_PASSCODE_LENGTH) return setError(`Use at least ${MIN_PASSCODE_LENGTH} characters.`);
      if (pass !== again) return setError('The two passcodes do not match.');
    }
    setBusy(true);
    try {
      if (creating) await store.createVault(pass);
      else await store.unlock(pass);
    } catch (err) {
      setError(err instanceof WrongPasscodeError ? err.message : 'Something went wrong. Please try again.');
      setBusy(false);
    }
  };

  return (
    <main class="lock">
      <img src="./icon-512.png" alt="" width="96" height="96" />
      <h1>2642 Easy</h1>
      <form onSubmit={submit}>
        {creating ? (
          <>
            <p>Choose a passcode. It encrypts everything you save in this browser, and you will type it each time you open the app.</p>
            <p class="warn">There is no way to recover a forgotten passcode. Keep a backup file (Settings → Export backup) somewhere safe.</p>
            <Field label="New passcode" type="password" value={pass} onInput={setPass} autoComplete="new-password" />
            <Field label="Type it again" type="password" value={again} onInput={setAgain} autoComplete="new-password" />
          </>
        ) : (
          <Field label="Passcode" type="password" value={pass} onInput={setPass} autoComplete="current-password" />
        )}
        {error && <p class="error" role="alert">{error}</p>}
        <button type="submit" class="primary" disabled={busy}>{busy ? 'Working…' : creating ? 'Create passcode' : 'Unlock'}</button>
      </form>
      {!creating && (
        <button type="button" class="link" onClick={() => setConfirmErase(true)}>Forgot passcode?</button>
      )}
      {confirmErase && (
        <Confirm
          title="Erase saved data?"
          body="A forgotten passcode cannot be recovered. You can erase everything saved in this browser and start again, then restore from a backup file if you have one. This cannot be undone."
          confirmLabel="Erase everything"
          danger
          onCancel={() => setConfirmErase(false)}
          onConfirm={() => { setConfirmErase(false); store.clearAll(); }}
        />
      )}
    </main>
  );
}
