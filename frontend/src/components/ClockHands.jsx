import { useEffect, useState } from 'react'

const reducedMotion = () => window.matchMedia && window.matchMedia('(prefers-reduced-motion: reduce)').matches

/** The current time, refreshed every animation frame (smooth sweep) or once a second with reduced motion. */
export function useLiveTime() {
  const [t, setT] = useState(() => new Date())
  useEffect(() => {
    if (reducedMotion()) {
      const id = setInterval(() => setT(new Date()), 1000)
      return () => clearInterval(id)
    }
    let raf
    const loop = () => { setT(new Date()); raf = requestAnimationFrame(loop) }
    raf = requestAnimationFrame(loop)
    return () => cancelAnimationFrame(raf)
  }, [])
  return t
}

/**
 * Hour, minute and second hands for an SVG clock face centred at (cx, cy).
 * Lengths are in the SVG's own units. The second hand sweeps; the others follow it continuously.
 */
export default function ClockHands({ cx, cy, hour, minute, second, tail = 22, className = '' }) {
  const t = useLiveTime()
  const s = t.getSeconds() + (reducedMotion() ? 0 : t.getMilliseconds() / 1000)
  const m = t.getMinutes() + s / 60
  const h = (t.getHours() % 12) + m / 60
  const rot = (deg) => `rotate(${deg.toFixed(3)} ${cx} ${cy})`
  return (
    <g className={`hands ${className}`} aria-hidden="true">
      <line className="h-hour" x1={cx} y1={cy + 10} x2={cx} y2={cy - hour} transform={rot(h * 30)} />
      <line className="h-min" x1={cx} y1={cy + 14} x2={cx} y2={cy - minute} transform={rot(m * 6)} />
      <g transform={rot(s * 6)}>
        <line className="h-sec" x1={cx} y1={cy + tail} x2={cx} y2={cy - second} />
        <circle className="h-sec-dot" cx={cx} cy={cy + tail} r="3.2" />
      </g>
      <circle className="h-cap" cx={cx} cy={cy} r="6.5" />
      <circle className="h-cap-in" cx={cx} cy={cy} r="2.4" />
    </g>
  )
}
