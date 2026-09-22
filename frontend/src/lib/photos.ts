/** Matches the server's cap (TrackPhotoService.MAX_EDGE); it re-encodes anyway, this just keeps uploads small. */
const MAX_EDGE = 2000

/**
 * Scales a phone or camera photo down to a JPEG under the 5 MB upload limit. Decoding through
 * createImageBitmap applies the EXIF rotation, so portrait shots arrive upright.
 */
export async function shrinkPhoto(file: File): Promise<Blob> {
  const bitmap = await createImageBitmap(file)
  try {
    const scale = Math.min(1, MAX_EDGE / Math.max(bitmap.width, bitmap.height))
    const canvas = document.createElement('canvas')
    canvas.width = Math.round(bitmap.width * scale)
    canvas.height = Math.round(bitmap.height * scale)
    const ctx = canvas.getContext('2d')
    if (!ctx) return file
    ctx.fillStyle = '#fff' // transparent PNG areas
    ctx.fillRect(0, 0, canvas.width, canvas.height)
    ctx.drawImage(bitmap, 0, 0, canvas.width, canvas.height)
    return await new Promise((resolve) => canvas.toBlob((blob) => resolve(blob ?? file), 'image/jpeg', 0.9))
  } finally {
    bitmap.close()
  }
}
