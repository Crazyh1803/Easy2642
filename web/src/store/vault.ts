// Encrypted on-device storage for the web app.
//
// Everything personal (profiles, claims, saved signatures) is one JSON document encrypted with
// AES-256-GCM. The key is derived from the user's passcode with PBKDF2 and only ever lives in
// memory, so the data cannot be read without the passcode, and locks when the tab closes.
import { FamilyBackup } from '../core/models';

export interface KeyValueStore {
  getItem(key: string): string | null;
  setItem(key: string, value: string): void;
  removeItem(key: string): void;
}

export const VAULT_KEY = 'easy2642.vault';
export const PBKDF2_ITERATIONS = 600_000;
export const MIN_PASSCODE_LENGTH = 6;

interface Envelope { v: 1; iterations: number; salt: string; iv: string; data: string }

export const emptyData = (): FamilyBackup => ({ formatVersion: 1, sponsor: null, dependents: [], claims: [], signatures: [] });

const toB64 = (bytes: Uint8Array): string => {
  let s = '';
  for (const b of bytes) s += String.fromCharCode(b);
  return btoa(s);
};
const fromB64 = (s: string): Uint8Array => Uint8Array.from(atob(s), (c) => c.charCodeAt(0));

async function deriveKey(passcode: string, salt: Uint8Array, iterations: number): Promise<CryptoKey> {
  const material = await crypto.subtle.importKey('raw', new TextEncoder().encode(passcode), 'PBKDF2', false, ['deriveKey']);
  return crypto.subtle.deriveKey(
    { name: 'PBKDF2', salt, iterations, hash: 'SHA-256' },
    material,
    { name: 'AES-GCM', length: 256 },
    false,
    ['encrypt', 'decrypt'],
  );
}

export class WrongPasscodeError extends Error {
  constructor() { super('That passcode is not correct.'); }
}

/** An unlocked vault. Holds the key in memory; `save` re-encrypts with a fresh IV each time. */
export class Vault {
  private constructor(
    private readonly store: KeyValueStore,
    private key: CryptoKey,
    private salt: Uint8Array,
    private iterations: number,
  ) {}

  static exists(store: KeyValueStore): boolean {
    return store.getItem(VAULT_KEY) !== null;
  }

  /** Creates a new, empty vault protected by `passcode`, replacing any existing one. */
  static async create(store: KeyValueStore, passcode: string, iterations = PBKDF2_ITERATIONS): Promise<Vault> {
    if (passcode.length < MIN_PASSCODE_LENGTH) throw new Error(`Use at least ${MIN_PASSCODE_LENGTH} characters.`);
    const salt = crypto.getRandomValues(new Uint8Array(16));
    const vault = new Vault(store, await deriveKey(passcode, salt, iterations), salt, iterations);
    await vault.save(emptyData());
    return vault;
  }

  /** Opens the existing vault. Throws WrongPasscodeError if the passcode does not decrypt it. */
  static async unlock(store: KeyValueStore, passcode: string): Promise<{ vault: Vault; data: FamilyBackup }> {
    const raw = store.getItem(VAULT_KEY);
    if (raw === null) throw new Error('No saved data to unlock.');
    const env = JSON.parse(raw) as Envelope;
    const salt = fromB64(env.salt);
    const key = await deriveKey(passcode, salt, env.iterations);
    let plain: ArrayBuffer;
    try {
      plain = await crypto.subtle.decrypt({ name: 'AES-GCM', iv: fromB64(env.iv) }, key, fromB64(env.data));
    } catch {
      throw new WrongPasscodeError();
    }
    const data = { ...emptyData(), ...(JSON.parse(new TextDecoder().decode(plain)) as Partial<FamilyBackup>) };
    return { vault: new Vault(store, key, salt, env.iterations), data };
  }

  async save(data: FamilyBackup): Promise<void> {
    const iv = crypto.getRandomValues(new Uint8Array(12));
    const cipher = await crypto.subtle.encrypt({ name: 'AES-GCM', iv }, this.key, new TextEncoder().encode(JSON.stringify(data)));
    const env: Envelope = { v: 1, iterations: this.iterations, salt: toB64(this.salt), iv: toB64(iv), data: toB64(new Uint8Array(cipher)) };
    this.store.setItem(VAULT_KEY, JSON.stringify(env));
  }

  /** Re-encrypts everything under a new passcode. */
  async changePasscode(newPasscode: string, data: FamilyBackup): Promise<void> {
    if (newPasscode.length < MIN_PASSCODE_LENGTH) throw new Error(`Use at least ${MIN_PASSCODE_LENGTH} characters.`);
    this.salt = crypto.getRandomValues(new Uint8Array(16));
    this.key = await deriveKey(newPasscode, this.salt, this.iterations);
    await this.save(data);
  }

  static destroy(store: KeyValueStore): void {
    store.removeItem(VAULT_KEY);
  }
}

/** Validates text as a backup made by either the web or the Android app. Throws if it is not one. */
export function parseBackup(text: string): FamilyBackup {
  const b = JSON.parse(text) as Partial<FamilyBackup>;
  if (typeof b !== 'object' || b === null || b.formatVersion !== 1) throw new Error('Not a 2642 Easy backup.');
  if (!Array.isArray(b.dependents ?? []) || !Array.isArray(b.claims ?? []) || !Array.isArray(b.signatures ?? [])) {
    throw new Error('Not a 2642 Easy backup.');
  }
  return { formatVersion: 1, sponsor: b.sponsor ?? null, dependents: b.dependents ?? [], claims: b.claims ?? [], signatures: b.signatures ?? [] };
}
