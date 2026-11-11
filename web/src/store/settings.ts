// Non-sensitive preferences only. No personal information is ever written here.
import { OutputMode } from '../core/models';
import { KeyValueStore } from './vault';

const K = { disclaimer: 'easy2642.disclaimerAccepted', output: 'easy2642.outputMode', tipYear: 'easy2642.tipPromptYear' };

export class Settings {
  constructor(private readonly store: KeyValueStore) {}

  get disclaimerAccepted(): boolean { return this.store.getItem(K.disclaimer) === 'true'; }
  set disclaimerAccepted(v: boolean) { this.store.setItem(K.disclaimer, String(v)); }

  /** Default for new claims: one combined PDF, or form / bill / receipt as separate files. */
  get outputMode(): OutputMode { return this.store.getItem(K.output) === 'SEPARATE' ? 'SEPARATE' : 'COMBINED'; }
  set outputMode(v: OutputMode) { this.store.setItem(K.output, v); }

  /** Year the Oktoberfest tip reminder was last shown; 0 if never. */
  get lastTipPromptYear(): number { return Number(this.store.getItem(K.tipYear) ?? 0) || 0; }
  set lastTipPromptYear(v: number) { this.store.setItem(K.tipYear, String(v)); }

  clear(): void { Object.values(K).forEach((k) => this.store.removeItem(k)); }
}
