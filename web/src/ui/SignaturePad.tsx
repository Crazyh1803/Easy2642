import { useEffect, useRef } from 'preact/hooks';
import { Signature, SignaturePoint, fitSignature } from '../core/models';

function ink(canvas: HTMLCanvasElement): string {
  return getComputedStyle(canvas).color || '#000';
}

function drawStrokes(ctx: CanvasRenderingContext2D, strokes: SignaturePoint[][], color: string, width: number, dx = 0, dy = 0) {
  ctx.strokeStyle = color;
  ctx.fillStyle = color;
  ctx.lineWidth = width;
  ctx.lineCap = 'round';
  ctx.lineJoin = 'round';
  for (const s of strokes) {
    if (!s.length) continue;
    if (s.length === 1) {
      ctx.beginPath();
      ctx.arc(s[0].x + dx, s[0].y + dy, width / 2, 0, Math.PI * 2);
      ctx.fill();
      continue;
    }
    ctx.beginPath();
    ctx.moveTo(s[0].x + dx, s[0].y + dy);
    for (const p of s.slice(1)) ctx.lineTo(p.x + dx, p.y + dy);
    ctx.stroke();
  }
}

/**
 * Drawing surface for mouse, touch or pen. Strokes are reported in CSS pixels of the pad;
 * only their shape is used later, so the pad's size does not matter.
 */
export function SignaturePad({ strokes, onChange }: { strokes: SignaturePoint[][]; onChange: (s: SignaturePoint[][]) => void }) {
  const ref = useRef<HTMLCanvasElement>(null);
  const latest = useRef(strokes);
  latest.current = strokes;
  const drawing = useRef<{ id: number; base: SignaturePoint[][]; stroke: SignaturePoint[] } | null>(null);

  const paint = () => {
    const canvas = ref.current;
    if (!canvas) return;
    const dpr = window.devicePixelRatio || 1;
    const rect = canvas.getBoundingClientRect();
    if (canvas.width !== Math.round(rect.width * dpr) || canvas.height !== Math.round(rect.height * dpr)) {
      canvas.width = Math.round(rect.width * dpr);
      canvas.height = Math.round(rect.height * dpr);
    }
    const ctx = canvas.getContext('2d')!;
    ctx.setTransform(dpr, 0, 0, dpr, 0, 0);
    ctx.clearRect(0, 0, rect.width, rect.height);
    ctx.globalAlpha = 0.25;
    ctx.strokeStyle = ink(canvas);
    ctx.lineWidth = 1;
    ctx.beginPath();
    ctx.moveTo(12, rect.height * 0.78);
    ctx.lineTo(rect.width - 12, rect.height * 0.78);
    ctx.stroke();
    ctx.globalAlpha = 1;
    drawStrokes(ctx, latest.current, ink(canvas), 2.5);
  };

  useEffect(paint);
  useEffect(() => {
    window.addEventListener('resize', paint);
    return () => window.removeEventListener('resize', paint);
  }, []);

  const point = (e: PointerEvent): SignaturePoint => {
    const r = ref.current!.getBoundingClientRect();
    return { x: e.clientX - r.left, y: e.clientY - r.top };
  };
  const down = (e: PointerEvent) => {
    if (drawing.current) return;
    e.preventDefault();
    ref.current!.setPointerCapture(e.pointerId);
    drawing.current = { id: e.pointerId, base: latest.current, stroke: [point(e)] };
    onChange([...drawing.current.base, drawing.current.stroke]);
  };
  const move = (e: PointerEvent) => {
    const d = drawing.current;
    if (!d || d.id !== e.pointerId) return;
    e.preventDefault();
    const events = typeof e.getCoalescedEvents === 'function' ? e.getCoalescedEvents() : [];
    d.stroke = [...d.stroke, ...(events.length ? events : [e]).map(point)];
    onChange([...d.base, d.stroke]);
  };
  const up = (e: PointerEvent) => {
    if (drawing.current?.id === e.pointerId) drawing.current = null;
  };

  return (
    <canvas
      ref={ref}
      class="sigpad"
      role="img"
      aria-label="Signature area. Draw your signature here with a mouse, finger or pen."
      onPointerDown={down}
      onPointerMove={move}
      onPointerUp={up}
      onPointerCancel={up}
    />
  );
}

/** Read-only rendering of a signature, scaled to fit. */
export function SignaturePreview({ signature }: { signature: Signature }) {
  const ref = useRef<HTMLCanvasElement>(null);
  useEffect(() => {
    const canvas = ref.current!;
    const dpr = window.devicePixelRatio || 1;
    const rect = canvas.getBoundingClientRect();
    canvas.width = Math.round(rect.width * dpr);
    canvas.height = Math.round(rect.height * dpr);
    const ctx = canvas.getContext('2d')!;
    ctx.setTransform(dpr, 0, 0, dpr, 0, 0);
    ctx.clearRect(0, 0, rect.width, rect.height);
    const pad = 6;
    drawStrokes(ctx, fitSignature(signature, rect.width - 2 * pad, rect.height - 2 * pad), ink(canvas), 2, pad, pad);
  });
  return <canvas ref={ref} class="sigpreview" role="img" aria-label="Signature preview" />;
}
