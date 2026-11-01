import { useEffect } from 'react'
import { startSunlight } from './sunlight'

const MOTES = Array.from({ length: 34 }, (_, i) => ({
  // fixed pseudo-random spread so the dust looks natural but is the same on every render
  x: (i * 37) % 100, y: (i * 61) % 100, s: 1.4 + ((i * 13) % 10) / 6, d: 14 + ((i * 7) % 12), delay: -((i * 3.1) % 20),
}))

/**
 * The room the page sits in. Behind the page: the desk, its grain, and window light landing on it.
 * In front of the page (it lets clicks through): the colour of the light, sun shafts with floating dust,
 * the darkening of the corners, and the desk lamp's pool of light after dark.
 */
export default function Backdrop() {
  useEffect(() => { startSunlight() }, [])
  return (
    <>
      <div className="backdrop" aria-hidden="true">
        <div className="gobo" id="gobo"><i /><i /><i /><i /></div>
      </div>
      <div className="room-light" aria-hidden="true">
        <div className="rl tint" />
        <div className="rl shafts"><div className="beams" id="beams">
          <i style={{ left: '8%', width: '9%' }} /><i style={{ left: '20%', width: '5%' }} />
          <i style={{ left: '29%', width: '12%' }} /><i style={{ left: '52%', width: '8%' }} />
          <i style={{ left: '63%', width: '14%' }} /><i style={{ left: '81%', width: '6%' }} />
          <div className="motes">
            {MOTES.map((m, i) => (
              <b key={i} style={{ left: `${m.x}%`, top: `${m.y}%`, width: m.s, height: m.s, animationDuration: `${m.d}s`, animationDelay: `${m.delay}s` }} />
            ))}
          </div>
        </div></div>
        <div className="rl lamp" />
        <div className="rl vignette" />
      </div>
    </>
  )
}
