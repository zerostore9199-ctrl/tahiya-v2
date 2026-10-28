/**
 * Re-encode any picked/captured image as a JPEG no larger than `maxSide` pixels.
 * Applies the EXIF rotation and drops all metadata (GPS, device info) before the image leaves the device,
 * and converts WebP/PNG so the server only ever receives a plain JPEG.
 */
export async function toJpeg(src: Blob, maxSide = 1600, quality = 0.9): Promise<Blob> {
  let width: number, height: number;
  let draw: (ctx: CanvasRenderingContext2D, w: number, h: number) => void;
  let release = () => {};
  if (typeof createImageBitmap === 'function') {
    const bmp = await createImageBitmap(src);
    width = bmp.width; height = bmp.height;
    draw = (ctx, w, h) => ctx.drawImage(bmp, 0, 0, w, h);
    release = () => bmp.close();
  } else {
    const url = URL.createObjectURL(src);
    const img = new Image();
    await new Promise<void>((ok, fail) => { img.onload = () => ok(); img.onerror = () => fail(new Error('decode')); img.src = url; });
    width = img.naturalWidth; height = img.naturalHeight;
    draw = (ctx, w, h) => ctx.drawImage(img, 0, 0, w, h);
    release = () => URL.revokeObjectURL(url);
  }
  try {
    if (!width || !height) throw new Error('empty image');
    const scale = Math.min(1, maxSide / Math.max(width, height));
    const c = document.createElement('canvas');
    c.width = Math.max(1, Math.round(width * scale));
    c.height = Math.max(1, Math.round(height * scale));
    const ctx = c.getContext('2d')!;
    ctx.fillStyle = '#fff';
    ctx.fillRect(0, 0, c.width, c.height);
    draw(ctx, c.width, c.height);
    return await new Promise<Blob>((ok, fail) => c.toBlob(b => (b ? ok(b) : fail(new Error('encode'))), 'image/jpeg', quality));
  } finally {
    release();
  }
}
