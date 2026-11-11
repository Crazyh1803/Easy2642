import { ComponentChildren } from 'preact';
import { useEffect, useRef } from 'preact/hooks';

let nextId = 0;
const useId = (prefix: string) => useRef(`${prefix}-${++nextId}`).current;

export function Section(props: { title: string; children?: ComponentChildren }) {
  return (
    <section class="section">
      <h3>{props.title}</h3>
      {props.children}
    </section>
  );
}

interface FieldProps {
  label: string;
  value: string;
  onInput: (v: string) => void;
  hint?: string;
  type?: string;
  inputMode?: 'text' | 'numeric' | 'decimal' | 'tel';
  multiline?: boolean;
  invalid?: boolean;
  autoComplete?: string;
  maxLength?: number;
  class?: string;
}

export function Field(p: FieldProps) {
  const id = useId('f');
  const common = {
    id,
    value: p.value,
    'aria-invalid': p.invalid ? true : undefined,
    'aria-describedby': p.hint ? `${id}-hint` : undefined,
    autoComplete: p.autoComplete ?? 'off',
    maxLength: p.maxLength,
    onInput: (e: Event) => p.onInput((e.target as HTMLInputElement | HTMLTextAreaElement).value),
  };
  return (
    <div class={`field ${p.class ?? ''}`}>
      <label for={id}>{p.label}</label>
      {p.multiline ? <textarea rows={3} {...common} /> : <input type={p.type ?? 'text'} inputMode={p.inputMode} {...common} />}
      {p.hint && <small id={`${id}-hint`}>{p.hint}</small>}
    </div>
  );
}

/** ISO date entry using the browser's own date picker. */
export function DateField(p: { label: string; value: string; onInput: (v: string) => void; hint?: string; invalid?: boolean }) {
  return <Field {...p} type="date" />;
}

/** Single-choice chips; choosing the selected chip clears the choice unless `required`. */
export function Chips<T extends string | boolean>(p: {
  label?: string;
  options: readonly T[];
  selected: T | null;
  text: (o: T) => string;
  onSelect: (o: T | null) => void;
  required?: boolean;
}) {
  return (
    <div class="chips" role="group" aria-label={p.label}>
      {p.label && <span class="chips-label">{p.label}</span>}
      <div class="chip-row">
        {p.options.map((o) => (
          <button
            type="button"
            class="chip"
            aria-pressed={o === p.selected}
            onClick={() => p.onSelect(o === p.selected && !p.required ? null : o)}
          >
            {p.text(o)}
          </button>
        ))}
      </div>
    </div>
  );
}

export function MultiChips<T extends string>(p: { label: string; options: readonly T[]; selected: T[]; text: (o: T) => string; onChange: (v: T[]) => void }) {
  return (
    <div class="chips" role="group" aria-label={p.label}>
      <span class="chips-label">{p.label}</span>
      <div class="chip-row">
        {p.options.map((o) => {
          const on = p.selected.includes(o);
          return (
            <button type="button" class="chip" aria-pressed={on} onClick={() => p.onChange(on ? p.selected.filter((x) => x !== o) : [...p.selected, o])}>
              {p.text(o)}
            </button>
          );
        })}
      </div>
    </div>
  );
}

export function YesNo(p: { label: string; value: boolean | null; onChange: (v: boolean | null) => void }) {
  return <Chips label={p.label} options={[true, false] as const} selected={p.value} text={(v) => (v ? 'Yes' : 'No')} onSelect={p.onChange} />;
}

/** Modal dialog built on the native <dialog> element (focus trap and Escape come for free). */
export function Dialog(p: { title: string; onClose: () => void; children?: ComponentChildren; actions?: ComponentChildren; wide?: boolean }) {
  const ref = useRef<HTMLDialogElement>(null);
  useEffect(() => {
    const d = ref.current!;
    if (!d.open) d.showModal();
    return () => { if (d.open) d.close(); };
  }, []);
  return (
    <dialog ref={ref} class={p.wide ? 'wide' : ''} onCancel={(e) => { e.preventDefault(); p.onClose(); }} aria-label={p.title}>
      <h2>{p.title}</h2>
      <div class="dialog-body">{p.children}</div>
      <div class="dialog-actions">{p.actions ?? <button type="button" onClick={p.onClose}>Close</button>}</div>
    </dialog>
  );
}

export function Confirm(p: { title: string; body: string; confirmLabel: string; danger?: boolean; onConfirm: () => void; onCancel: () => void }) {
  return (
    <Dialog
      title={p.title}
      onClose={p.onCancel}
      actions={
        <>
          <button type="button" onClick={p.onCancel}>Cancel</button>
          <button type="button" class={p.danger ? 'danger' : 'primary'} onClick={p.onConfirm}>{p.confirmLabel}</button>
        </>
      }
    >
      <p>{p.body}</p>
    </Dialog>
  );
}

export const DISCLAIMER =
  '2642 Easy is an independent document preparation tool. It is not affiliated with, endorsed by, or sponsored by TRICARE, the Defense Health Agency (DHA), or the Department of Defense (DoD). Users remain responsible for submitting completed claim packets to their respective claims processor or portal.';
export const DISCLAIMER_SHORT = 'Independent tool. Not affiliated with TRICARE, DHA or DoD.';
export const PRIVACY_SUMMARY =
  '2642 Easy collects no data. Everything you enter is processed and stored only in this browser on this device, encrypted with your passcode. The app makes no network requests after it has loaded. Files leave the device only when you save or share them yourself.';
export const LINKS = {
  author: 'https://appsbydan.com/',
  tip: 'https://buymeacoffee.com/AppsbyDan',
  privacy: 'https://github.com/Crazyh1803/Easy2642/blob/main/PRIVACY.md',
};
