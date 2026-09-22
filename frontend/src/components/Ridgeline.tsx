/**
 * Layered Sahyadri ridgeline at first light — flat-topped basalt plateaus,
 * a pinnacle or two, and valley mist. Pure SVG so the hero needs no photo assets.
 */
export function Ridgeline({ className = '' }: { className?: string }) {
  return (
    <svg
      viewBox="0 0 1440 600"
      preserveAspectRatio="xMidYMax slice"
      className={className}
      aria-hidden="true"
    >
      <defs>
        <linearGradient id="sky" x1="0" y1="0" x2="0" y2="1">
          <stop offset="0%" stopColor="#0b2118" />
          <stop offset="55%" stopColor="#1b4a33" />
          <stop offset="100%" stopColor="#b95e1b" stopOpacity="0.55" />
        </linearGradient>
        <radialGradient id="sun" cx="0.5" cy="0.5" r="0.5">
          <stop offset="0%" stopColor="#fbe6d6" />
          <stop offset="60%" stopColor="#e79558" stopOpacity="0.9" />
          <stop offset="100%" stopColor="#d9772b" stopOpacity="0" />
        </radialGradient>
        <linearGradient id="mist" x1="0" y1="0" x2="0" y2="1">
          <stop offset="0%" stopColor="#fbe6d6" stopOpacity="0" />
          <stop offset="60%" stopColor="#fbe6d6" stopOpacity="0.12" />
          <stop offset="100%" stopColor="#fbe6d6" stopOpacity="0" />
        </linearGradient>
      </defs>

      <rect width="1440" height="600" fill="url(#sky)" />
      <circle cx="1080" cy="300" r="120" fill="url(#sun)" />

      {/* far range */}
      <path
        fill="#3f9468"
        fillOpacity="0.35"
        d="M0 360 L120 330 L210 338 L260 300 L420 300 L470 330 L600 320 L680 280 L820 280 L860 312 L990 318 L1060 290 L1210 292 L1260 322 L1440 310 L1440 600 L0 600 Z"
      />
      <rect y="300" width="1440" height="120" fill="url(#mist)" />

      {/* middle range — mesas and a pinnacle */}
      <path
        fill="#245f42"
        d="M0 420 L90 400 L150 404 L190 360 L340 358 L380 392 L470 398 L520 370 L548 318 L566 318 L590 372 L700 380 L760 352 L930 350 L980 390 L1100 396 L1150 366 L1300 364 L1340 398 L1440 392 L1440 600 L0 600 Z"
      />
      <rect y="380" width="1440" height="110" fill="url(#mist)" />

      {/* near range */}
      <path
        fill="#143626"
        d="M0 480 L140 462 L230 470 L300 440 L460 436 L520 470 L640 478 L720 456 L800 452 L850 474 L1000 482 L1080 450 L1240 446 L1300 476 L1440 470 L1440 600 L0 600 Z"
      />

      {/* foreground */}
      <path fill="#0b2118" d="M0 540 L200 522 L420 532 L640 516 L900 530 L1160 514 L1440 528 L1440 600 L0 600 Z" />

      {/* a small group of six on the near ridge */}
      <g fill="#fbe6d6" fillOpacity="0.85">
        {[0, 1, 2, 3, 4, 5].map((i) => (
          <rect key={i} x={318 + i * 14} y={i % 2 ? 424 : 422} width="3" height="14" rx="1.5" />
        ))}
      </g>
    </svg>
  )
}
