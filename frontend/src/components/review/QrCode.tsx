import qrcode from 'qrcode-generator'
import { useMemo } from 'react'

/** A QR code drawn as SVG on the client (no image request, nothing leaves the page). */
export function QrCode({ text, size = 200, label }: { text: string; size?: number; label: string }) {
  const { count, path } = useMemo(() => {
    const qr = qrcode(0, 'M')
    qr.addData(text)
    qr.make()
    const n = qr.getModuleCount()
    let d = ''
    for (let r = 0; r < n; r++) {
      for (let c = 0; c < n; c++) {
        if (qr.isDark(r, c)) d += `M${c + 2} ${r + 2}h1v1h-1z`
      }
    }
    return { count: n, path: d }
  }, [text])
  return (
    <svg
      role="img"
      aria-label={label}
      width={size}
      height={size}
      viewBox={`0 0 ${count + 4} ${count + 4}`}
      shapeRendering="crispEdges"
      className="rounded-lg bg-white"
    >
      <rect width="100%" height="100%" fill="#fff" />
      <path d={path} fill="#000" />
    </svg>
  )
}
