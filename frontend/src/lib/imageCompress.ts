/**
 * Client-side image helpers for base64 data-URI storage (skill description
 * images, feedback screenshots). Mirrors the logic in RichTextEditor.vue.
 */

const MAX_DIM = 1280 // longest side after compression
const JPEG_QUALITY = 0.85
const WARN_SIZE = 500 * 1024 // above this → compress; at or below → keep original

export function readAsDataUrl(file: File): Promise<string> {
  return new Promise((resolve, reject) => {
    const reader = new FileReader()
    reader.onload = () => resolve(typeof reader.result === 'string' ? reader.result : '')
    reader.onerror = () => reject(new Error('Could not read file'))
    reader.readAsDataURL(file)
  })
}

/**
 * Compress an image via canvas: scale the longest side down to MAX_DIM and
 * re-encode as JPEG (white background for transparent PNGs).
 */
export function compressImage(file: File): Promise<string> {
  return new Promise((resolve, reject) => {
    const url = URL.createObjectURL(file)
    const img = document.createElement('img')
    img.onload = () => {
      const scale = Math.min(1, MAX_DIM / Math.max(img.width, img.height))
      const w = Math.max(1, Math.round(img.width * scale))
      const h = Math.max(1, Math.round(img.height * scale))
      const canvas = document.createElement('canvas')
      canvas.width = w
      canvas.height = h
      const ctx = canvas.getContext('2d')
      if (!ctx) {
        URL.revokeObjectURL(url)
        reject(new Error('Canvas not available'))
        return
      }
      ctx.fillStyle = '#fff'
      ctx.fillRect(0, 0, w, h)
      ctx.drawImage(img, 0, 0, w, h)
      URL.revokeObjectURL(url)
      const compressed = canvas.toDataURL('image/jpeg', JPEG_QUALITY)
      // keep whichever is smaller (rarely, re-encoding can be larger)
      if (compressed.length < file.size * 1.34) resolve(compressed)
      else void readAsDataUrl(file).then(resolve)
    }
    img.onerror = () => {
      URL.revokeObjectURL(url)
      reject(new Error('Could not read image'))
    }
    img.src = url
  })
}

/**
 * Convert a picked image file to a data URI, auto-compressing files above
 * WARN_SIZE. Returns '' when the file is not a supported image type.
 */
export async function fileToDataUri(file: File): Promise<string> {
  const ext = file.name.split('.').pop()?.toLowerCase() ?? ''
  if (!['jpeg', 'jpg', 'png', 'webp'].includes(ext)) return ''
  if (file.size > 5 * 1024 * 1024) return ''
  return file.size <= WARN_SIZE
    ? readAsDataUrl(file)
    : compressImage(file)
}
