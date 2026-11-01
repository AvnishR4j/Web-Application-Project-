import { useEffect, useRef, useState } from 'react'
import Icon from './Icon'
import { hhmm, onLight, playDay, reducedMotion, releaseScrub, scrubTo, sunTimes } from './sunlight'

const H0 = 6
const H1 = 21

/** The day as a ruler with the sun on it; "Watch the day" runs the light from dawn to dusk. */
export default function SunRuler() {
  const [h, setH] = useState(null)
  const [playing, setPlaying] = useState(false)
  const [dragging, setDragging] = useState(false)
  const line = useRef(null)
  const { rise, set } = sunTimes()
  useEffect(() => onLight(setH), [])
  if (h === null) return null
  const pos = (x) => `${((Math.min(H1, Math.max(H0, x)) - H0) / (H1 - H0)) * 100}%`
  const up = h >= rise && h <= set
  const label = up
    ? (set - h < 1.2 ? 'Golden hour' : h - rise < 1.5 ? 'Morning light' : 'Daylight')
    : h > set && h - set < 1.1 ? 'Dusk' : h < rise && rise - h < 1.1 ? 'Blue hour' : 'Lamp light'

  // Drag along the ruler to see the room at any hour; let go and it returns to now.
  const hourAt = (e) => {
    const b = line.current.getBoundingClientRect()
    return H0 + Math.min(1, Math.max(0, (e.clientX - b.left) / b.width)) * (H1 - H0)
  }
  function down(e) {
    if (playing) return
    e.preventDefault()
    line.current.setPointerCapture(e.pointerId)
    setDragging(true)
    scrubTo(hourAt(e))
  }
  function move(e) { if (dragging) scrubTo(hourAt(e)) }
  function up_(e) {
    if (!dragging) return
    setDragging(false)
    try { line.current.releasePointerCapture(e.pointerId) } catch { /* already released */ }
    releaseScrub()
  }
  function key(e) {
    if (e.key !== 'ArrowLeft' && e.key !== 'ArrowRight') return
    e.preventDefault()
    scrubTo(Math.min(H1, Math.max(H0, h + (e.key === 'ArrowRight' ? 0.5 : -0.5))))
    clearTimeout(key.t); key.t = setTimeout(() => releaseScrub(), 900)
  }

  async function play() {
    setPlaying(true)
    await playDay()
    setPlaying(false)
  }

  return (
    <div className="ruler" aria-label={`Campus time ${hhmm(h)}`}>
      <div className="ruler-top">
        <div className="ruler-now">{hhmm(h)}<small>{label} · Patiala</small></div>
        {!reducedMotion() && (
          <button type="button" className="play" onClick={play} disabled={playing} aria-pressed={playing}>
            <b><Icon name="play" /></b>{playing ? 'Watching…' : 'Watch the day'}
          </button>
        )}
      </div>
      <div className={`sunline${dragging ? ' dragging' : ''}`} ref={line} role="slider" tabIndex={0}
        aria-label="Drag to see the room at another hour" aria-valuemin={H0} aria-valuemax={H1} aria-valuenow={Math.round(h * 10) / 10}
        aria-valuetext={hhmm(h)} onPointerDown={down} onPointerMove={move} onPointerUp={up_} onPointerCancel={up_} onKeyDown={key}>
        <span className="night" style={{ left: 0, width: pos(rise) }} />
        <span className="night" style={{ left: pos(set), right: 0 }} />
        {Array.from({ length: H1 - H0 + 1 }, (_, i) => H0 + i).map((x) => (
          <span key={x}>
            <span className="rt-tick" style={{ left: pos(x) }} />
            {x % 3 === 0 && <span className="rt-lb" style={{ left: pos(x) }}>{String(x).padStart(2, '0')}:00</span>}
          </span>
        ))}
        <span className={`sunmark${up ? '' : ' moon'}`} style={{ left: pos(h) }} />
      </div>
    </div>
  )
}
