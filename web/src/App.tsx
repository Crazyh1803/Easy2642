import { useEffect, useState } from 'preact/hooks';
import { shouldPromptForTip } from './core/oktoberfest';
import { AppStore, useStore } from './store/appStore';
import { DISCLAIMER, DISCLAIMER_SHORT, Dialog, LINKS, PRIVACY_SUMMARY } from './ui/components';
import { Dashboard } from './ui/Dashboard';
import { Family } from './ui/Family';
import { LockScreen } from './ui/Lock';
import { SettingsScreen } from './ui/Settings';
import { Wizard } from './ui/Wizard';

/** Set to false to remove the yearly tip reminder (the Settings link stays). */
const TIP_REMINDER_ENABLED = true;
/** The app locks itself after this long without any interaction. */
const IDLE_LOCK_MS = 15 * 60 * 1000;

type Tab = 'claims' | 'family' | 'settings';
const TABS: { id: Tab; label: string }[] = [
  { id: 'claims', label: 'Claims' },
  { id: 'family', label: 'Family' },
  { id: 'settings', label: 'Settings' },
];

export function App({ store: s }: { store: AppStore }) {
  const store = useStore(s);
  const [accepted, setAccepted] = useState(store.settings.disclaimerAccepted);
  // Shown once a year during Oktoberfest, never in the session where the first-run notice appears.
  const [tipPrompt, setTipPrompt] = useState(
    TIP_REMINDER_ENABLED && store.settings.disclaimerAccepted && shouldPromptForTip(new Date(), store.settings.lastTipPromptYear),
  );
  const [tab, setTab] = useState<Tab>('claims');
  const [wizard, setWizard] = useState(false);

  // Lock after a period of inactivity, so an unattended shared computer does not stay open.
  useEffect(() => {
    if (!store.unlocked) return;
    let timer = window.setTimeout(() => store.lock(), IDLE_LOCK_MS);
    const reset = () => {
      window.clearTimeout(timer);
      timer = window.setTimeout(() => store.lock(), IDLE_LOCK_MS);
    };
    const events = ['pointerdown', 'keydown', 'input'] as const;
    events.forEach((e) => window.addEventListener(e, reset, { passive: true }));
    return () => {
      window.clearTimeout(timer);
      events.forEach((e) => window.removeEventListener(e, reset));
    };
  }, [store.unlocked]);

  // A locked or erased app never keeps a half-finished claim on screen.
  useEffect(() => { if (!store.unlocked) setWizard(false); }, [store.unlocked]);
  // Erasing everything also resets the first-run notice.
  useEffect(() => { if (!store.settings.disclaimerAccepted) setAccepted(false); }, [store.unlocked, store.hasVault]);

  const dismissTip = () => {
    store.settings.lastTipPromptYear = new Date().getFullYear();
    setTipPrompt(false);
  };

  if (!accepted) {
    return (
      <main class="lock">
        <img src="./icon-512.png" alt="" width="96" height="96" />
        <h1>Before you start</h1>
        <p>{DISCLAIMER}</p>
        <p>{PRIVACY_SUMMARY}</p>
        <button type="button" class="primary" onClick={() => { store.settings.disclaimerAccepted = true; setAccepted(true); }}>I understand</button>
      </main>
    );
  }

  return (
    <>
      {!store.unlocked ? (
        <LockScreen store={store} />
      ) : (
        <div class="shell">
          {!wizard && (
            <nav class="tabs" aria-label="Sections">
              <span class="brand"><img src="./icon-512.png" alt="" width="28" height="28" /> 2642 Easy</span>
              {TABS.map((t) => (
                <button type="button" aria-current={tab === t.id ? 'page' : undefined} onClick={() => setTab(t.id)}>{t.label}</button>
              ))}
            </nav>
          )}
          <main>
            {wizard ? <Wizard store={store} onClose={() => setWizard(false)} />
              : tab === 'claims' ? <Dashboard store={store} onNewClaim={() => setWizard(true)} />
              : tab === 'family' ? <Family store={store} />
              : <SettingsScreen store={store} onLock={() => store.lock()} />}
          </main>
        </div>
      )}
      {/* The disclaimer is visible on every screen of the app. */}
      <footer class="disclaimer">{DISCLAIMER_SHORT}</footer>
      {tipPrompt && store.unlocked && (
        <Dialog
          title="O'zapft is!"
          onClose={dismissTip}
          actions={
            <>
              <button type="button" onClick={dismissTip}>Not now</button>
              <a class="button primary" href={LINKS.tip} target="_blank" rel="noopener noreferrer" onClick={dismissTip}>Leave a tip</a>
            </>
          }
        >
          <p>Oktoberfest has opened. If 2642 Easy has saved you some paperwork this year, you can buy the developer a beer. Entirely optional; you'll see this at most once a year.</p>
        </Dialog>
      )}
    </>
  );
}
