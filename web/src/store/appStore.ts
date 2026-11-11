// The single source of app state. Personal data lives in memory only while unlocked and is
// written back through the encrypted vault on every change.
import { useEffect, useState } from 'preact/hooks';
import { ClaimRecord, Dependent, FamilyBackup, SavedSignature, Signature, Sponsor, newId } from '../core/models';
import { Settings } from './settings';
import { KeyValueStore, Vault, emptyData, parseBackup } from './vault';

export class AppStore {
  readonly settings: Settings;
  private vault: Vault | null = null;
  data: FamilyBackup = emptyData();
  private listeners = new Set<() => void>();

  constructor(private readonly storage: KeyValueStore) {
    this.settings = new Settings(storage);
  }

  subscribe = (fn: () => void): (() => void) => {
    this.listeners.add(fn);
    return () => this.listeners.delete(fn);
  };
  private emit() { this.listeners.forEach((fn) => fn()); }

  get hasVault(): boolean { return Vault.exists(this.storage); }
  get unlocked(): boolean { return this.vault !== null; }

  async createVault(passcode: string): Promise<void> {
    this.vault = await Vault.create(this.storage, passcode);
    this.data = emptyData();
    this.emit();
  }

  async unlock(passcode: string): Promise<void> {
    const { vault, data } = await Vault.unlock(this.storage, passcode);
    this.vault = vault;
    this.data = data;
    this.emit();
  }

  /** Forgets the key and the decrypted data. */
  lock(): void {
    this.vault = null;
    this.data = emptyData();
    this.emit();
  }

  async changePasscode(newPasscode: string): Promise<void> {
    await this.vault!.changePasscode(newPasscode, this.data);
  }

  private async commit(next: FamilyBackup): Promise<void> {
    if (!this.vault) throw new Error('Locked');
    await this.vault.save(next);
    this.data = next;
    this.emit();
  }

  saveSponsor = (sponsor: Sponsor) => this.commit({ ...this.data, sponsor });

  saveDependent = (d: Dependent) =>
    this.commit({ ...this.data, dependents: [...this.data.dependents.filter((x) => x.id !== d.id), d].sort(byFirstName) });

  deleteDependent = (id: string) => this.commit({ ...this.data, dependents: this.data.dependents.filter((d) => d.id !== id) });

  saveClaim = (c: ClaimRecord) =>
    this.commit({ ...this.data, claims: [...this.data.claims.filter((x) => x.id !== c.id), c].sort(newestFirst) });

  deleteClaim = (id: string) => this.commit({ ...this.data, claims: this.data.claims.filter((c) => c.id !== id) });

  /** Saves a signature under the signer's name, replacing any earlier one for the same name. */
  async saveSignature(signerName: string, signature: Signature): Promise<SavedSignature> {
    const name = signerName.trim();
    const existing = this.data.signatures.find((s) => s.signerName.toLowerCase() === name.toLowerCase());
    const saved: SavedSignature = { id: existing?.id ?? newId(), signerName: existing?.signerName ?? name, signature };
    const others = this.data.signatures.filter((s) => s.id !== saved.id);
    await this.commit({ ...this.data, signatures: [...others, saved].sort((a, b) => a.signerName.localeCompare(b.signerName)) });
    return saved;
  }

  deleteSignature = (id: string) => this.commit({ ...this.data, signatures: this.data.signatures.filter((s) => s.id !== id) });

  /** Plain, readable JSON in the same format the Android app uses. */
  exportBackup(): string {
    return JSON.stringify(this.data, null, 2);
  }

  /** Replaces everything with the backup's contents. Throws if the text is not a valid backup. */
  importBackup(text: string): Promise<void> {
    return this.commit(parseBackup(text));
  }

  /** Erases the encrypted data, the preferences and everything in memory. */
  clearAll(): void {
    Vault.destroy(this.storage);
    this.settings.clear();
    this.vault = null;
    this.data = emptyData();
    this.emit();
  }
}

const byFirstName = (a: Dependent, b: Dependent) =>
  (a.name.firstName + a.name.lastName).localeCompare(b.name.firstName + b.name.lastName);
const newestFirst = (a: ClaimRecord, b: ClaimRecord) => b.dateOfService.localeCompare(a.dateOfService) || a.id.localeCompare(b.id);

/** Re-renders the calling component whenever the store changes. */
export function useStore(store: AppStore): AppStore {
  const [, bump] = useState(0);
  useEffect(() => store.subscribe(() => bump((n) => n + 1)), [store]);
  return store;
}
