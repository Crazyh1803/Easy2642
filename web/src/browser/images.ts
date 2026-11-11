// Browser-side image handling: decode whatever the user gave us, honour EXIF rotation,
// flatten transparency onto white, downscale and re-encode as JPEG.
import { EncodedImage, ImageEncoder, Level } from '../pdf/packet';

async function decode(data: Uint8Array): Promise<ImageBitmap> {
  try {
    return await createImageBitmap(new Blob([data]), { imageOrientation: 'from-image' });
  } catch {
    throw new Error('This image format cannot be read by your browser.');
  }
}

async function canvasToJpeg(canvas: HTMLCanvasElement, quality: number): Promise<Uint8Array> {
  const blob = await new Promise<Blob | null>((resolve) => canvas.toBlob(resolve, 'image/jpeg', quality));
  if (!blob) throw new Error('The image could not be encoded.');
  return new Uint8Array(await blob.arrayBuffer());
}

export const encodeImage: ImageEncoder = async (data: Uint8Array, level: Level): Promise<EncodedImage> => {
  const bitmap = await decode(data);
  try {
    const scale = Math.min(level.maxEdgePx / Math.max(bitmap.width, bitmap.height), 1);
    const width = Math.max(1, Math.round(bitmap.width * scale));
    const height = Math.max(1, Math.round(bitmap.height * scale));
    const canvas = document.createElement('canvas');
    canvas.width = width;
    canvas.height = height;
    const ctx = canvas.getContext('2d')!;
    ctx.fillStyle = '#fff';
    ctx.fillRect(0, 0, width, height);
    ctx.imageSmoothingQuality = 'high';
    ctx.drawImage(bitmap, 0, 0, width, height);
    return { jpeg: await canvasToJpeg(canvas, level.quality / 100), width, height };
  } finally {
    bitmap.close();
  }
};

/** Confirms the bytes are an image this browser can decode. */
export async function assertReadableImage(data: Uint8Array): Promise<void> {
  (await decode(data)).close();
}

/** Grabs the current frame of a playing <video> as a JPEG. */
export async function captureFrame(video: HTMLVideoElement): Promise<Uint8Array> {
  const canvas = document.createElement('canvas');
  canvas.width = video.videoWidth;
  canvas.height = video.videoHeight;
  canvas.getContext('2d')!.drawImage(video, 0, 0);
  return canvasToJpeg(canvas, 0.92);
}
