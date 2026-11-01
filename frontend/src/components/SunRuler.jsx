import { useEffect, useState } from 'react'
import Icon from './Icon'
import { hhmm, onLight, playDay, reducedMotion, sunTimes } from './sunlight'

const H0 = 6
const H1 = 21

/** The day as a ruler with the sun on it; "Watch the day" runs the light from dawn to dusk. */
export default function SunRuler() {
  const [h, setH] = useState(null)
  const [playing, setPlaying] = useState(false)
  const { rise, set } = sunTimes()
  useEffect(() => onLight(setH), [])
  if (h === null) return null
  const pos = (x) => `${((Math.min(H1, Math.max(H0, x)) - H0) / (H1 - H0)) * 100}%`
  const up = h >= rise && h <= set
  const label = up
    ? (set - h < 1.2 ? 'Golden hour' : h - rise < 1.5 ? 'Morning light' : 'Daylight')
    : h > set && h - set < 1.1 ? 'Dusk' : h < rise && rise - h < 1.1 ? 'Blue hour' : 'Lamp light'

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
      <div className="sunline">
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
