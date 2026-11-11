import { useEffect, useRef, useState } from 'preact/hooks';
import { captureFrame } from '../browser/images';
import { Dialog } from './components';

/**
 * Live camera capture using the phone camera or a PC webcam. Frames stay in the browser;
 * the stream is stopped as soon as the dialog closes.
 */
export function CameraDialog({ onCapture, onClose }: { onCapture: (jpeg: Uint8Array) => void; onClose: () => void }) {
  const video = useRef<HTMLVideoElement>(null);
  const stream = useRef<MediaStream | null>(null);
  const [error, setError] = useState('');
  const [ready, setReady] = useState(false);
  const [count, setCount] = useState(0);
  const [busy, setBusy] = useState(false);

  useEffect(() => {
    let cancelled = false;
    (async () => {
      if (!navigator.mediaDevices?.getUserMedia) {
        setError('This browser cannot open a camera here. Use "Upload files" instead.');
        return;
      }
      try {
        const s = await navigator.mediaDevices.getUserMedia({
          audio: false,
          // Prefer the rear camera on phones and the highest resolution on offer, for legible receipts.
          video: { facingMode: { ideal: 'environment' }, width: { ideal: 3840 }, height: { ideal: 2160 } },
        });
        if (cancelled) return s.getTracks().forEach((t) => t.stop());
        stream.current = s;
        video.current!.srcObject = s;
        await video.current!.play();
        setReady(true);
      } catch (e) {
        const name = (e as DOMException).name;
        setError(
          name === 'NotAllowedError' ? 'Camera access was blocked. Allow the camera for this site in your browser, or use "Upload files" instead.'
          : name === 'NotFoundError' ? 'No camera was found on this device. Use "Upload files" instead.'
          : 'The camera could not be started. Use "Upload files" instead.',
        );
      }
    })();
    return () => {
      cancelled = true;
      stream.current?.getTracks().forEach((t) => t.stop());
      stream.current = null;
    };
  }, []);

  const capture = async () => {
    if (!video.current || !ready) return;
    setBusy(true);
    try {
      onCapture(await captureFrame(video.current));
      setCount((n) => n + 1);
    } finally {
      setBusy(false);
    }
  };

  return (
    <Dialog
      title="Take photos"
      wide
      onClose={onClose}
      actions={
        <>
          <span class="muted" role="status">{count === 0 ? 'Fill the frame with the page and hold steady.' : `${count} page${count === 1 ? '' : 's'} added`}</span>
          <button type="button" class="primary" onClick={capture} disabled={!ready || busy}>Capture page</button>
          <button type="button" onClick={onClose}>Done</button>
        </>
      }
    >
      {error ? <p class="error" role="alert">{error}</p> : <video ref={video} class="camera" playsInline muted aria-label="Camera preview" />}
    </Dialog>
  );
}
