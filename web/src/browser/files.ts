// Getting files out of the browser: downloads, the share sheet, and (where supported) a folder.

export function downloadBytes(bytes: Uint8Array | string, fileName: string, type: string): void {
  const url = URL.createObjectURL(new Blob([bytes], { type }));
  const a = document.createElement('a');
  a.href = url;
  a.download = fileName;
  document.body.appendChild(a);
  a.click();
  a.remove();
  setTimeout(() => URL.revokeObjectURL(url), 30_000);
}

export interface OutFile { fileName: string; bytes: Uint8Array }

export async function downloadAll(files: OutFile[]): Promise<void> {
  for (const f of files) {
    downloadBytes(f.bytes, f.fileName, 'application/pdf');
    // Browsers drop rapid-fire downloads; a short gap lets each one start.
    await new Promise((r) => setTimeout(r, 400));
  }
}

const asFiles = (files: OutFile[]) => files.map((f) => new File([f.bytes], f.fileName, { type: 'application/pdf' }));

export function canShareFiles(files: OutFile[]): boolean {
  try {
    return typeof navigator.canShare === 'function' && navigator.canShare({ files: asFiles(files) });
  } catch {
    return false;
  }
}

/** Opens the system share sheet. Resolves false if the user cancelled. */
export async function shareFiles(files: OutFile[]): Promise<boolean> {
  try {
    await navigator.share({ files: asFiles(files), title: 'Claim files' });
    return true;
  } catch (e) {
    if ((e as DOMException).name === 'AbortError') return false;
    throw e;
  }
}

type DirectoryPicker = (options?: { mode?: 'readwrite' }) => Promise<FileSystemDirectoryHandle>;
const directoryPicker = (): DirectoryPicker | undefined => (window as unknown as { showDirectoryPicker?: DirectoryPicker }).showDirectoryPicker;

export const canPickFolder = (): boolean => typeof directoryPicker() === 'function';

/** Lets the user choose a folder and writes every file into it. Resolves null if cancelled. */
export async function saveToFolder(files: OutFile[]): Promise<string | null> {
  let dir: FileSystemDirectoryHandle;
  try {
    dir = await directoryPicker()!({ mode: 'readwrite' });
  } catch (e) {
    if ((e as DOMException).name === 'AbortError') return null;
    throw e;
  }
  for (const f of files) {
    const handle = await dir.getFileHandle(f.fileName, { create: true });
    const writable = await handle.createWritable();
    await writable.write(f.bytes);
    await writable.close();
  }
  return dir.name;
}

export async function readFile(file: File): Promise<Uint8Array> {
  return new Uint8Array(await file.arrayBuffer());
}
