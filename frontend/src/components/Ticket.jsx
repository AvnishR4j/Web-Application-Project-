import { useEffect, useRef, useState } from 'react'

const hm = (iso) => new Date(iso).toLocaleTimeString([], { hour: '2-digit', minute: '2-digit', hour12: false })

/**
 * An event printed as a ticket with a perforated "Admit one" stub.
 *
 * take:  { label, onTake } shows a button on the stub. onTake() does the real work and resolves to the stamp to
 *        press ({ top, big, bottom, tone }) or rejects; on success the stub tears off and the stamp thumps down.
 * stamp: a stamp that is already on the ticket (the stub is gone). Removing it later re-attaches the stub.
 */
export default function Ticket({
  start, end, title, club, venue, number, description, live, ended, day,
  seats, take, stubNote, stamp, actions, index = 0, onSettled,
}) {
  const [phase, setPhase] = useState(stamp ? 'torn' : 'whole')
  const [pressed, setPressed] = useState(null) // a stamp pressed in this session (animated)
  const [busy, setBusy] = useState(false)
  const hadStamp = useRef(Boolean(stamp))

  useEffect(() => {
    if (stamp && !hadStamp.current && phase === 'whole') setPhase('torn')
    if (!stamp && hadStamp.current) { setPressed(null); setPhase('untearing'); setTimeout(() => setPhase('whole'), 820) }
    hadStamp.current = Boolean(stamp)
  }, [stamp]) // eslint-disable-line react-hooks/exhaustive-deps

  async function tear() {
    if (!take || busy) return
    setBusy(true)
    try {
      const s = await take.onTake()
      setPressed(s)
      setPhase('tearing')
      setTimeout(() => { setPhase('torn'); onSettled?.() }, 1250)
    } catch {
      // the page shows the error; the ticket stays whole
    } finally {
      setBusy(false)
    }
  }

  const shown = pressed || stamp
  const r0 = index % 2 ? 0.8 : -0.8
  const r1 = index % 3 === 1 ? 0.35 : index % 3 === 2 ? -0.25 : 0

  return (
    <article className={`tk-wrap${ended ? ' ended' : ''}`}>
      <div className="tk-paper" style={{ '--delay': `${0.08 + index * 0.07}s`, '--r0': `${r0}deg`, '--r1': `${r1}deg` }}>
        <div className={`tk ${phase}`}>
          <div className="tk-main">
            {day && <div className="tk-day">{day}</div>}
            <div className="tk-when">{hm(start)}<span> – {hm(end)}</span></div>
            <h3>{live && <span className="live-dot" aria-label="Happening now" />}{title}</h3>
            {club && <div className="tk-club">{club}</div>}
            {description && <p className="tk-desc">{description}</p>}
            <div className="tk-spacer" />
            <div className="tk-foot"><span>{venue || 'Campus'}</span>{number && <span>No. {number}</span>}</div>
            {shown && (
              <div className={`stamp ${shown.tone || ''}${pressed ? ' thump' : ''}`} aria-label={`${shown.top} ${shown.big} ${shown.bottom || ''}`}>
                <div>{shown.top}<b>{shown.big}</b>{shown.bottom}</div>
              </div>
            )}
          </div>
          <div className="tk-stub" aria-hidden={phase === 'torn'}>
            <span className="admit">Admit one</span>
            {seats ? (
              <div className="n">{seats.left > 0 ? seats.left : '—'}<small>{seats.left > 0 ? (seats.left === 1 ? 'seat left' : 'seats left') : 'full'}</small></div>
            ) : <div className="n">&nbsp;</div>}
            {take ? (
              <button type="button" className="take" onClick={tear} disabled={busy}>{busy ? 'One moment…' : take.label}</button>
            ) : <span className="quiet">{stubNote || ''}</span>}
          </div>
        </div>
      </div>
      {actions && <div className="tk-actions">{actions}</div>}
    </article>
  )
}

/** Groups items by calendar day, in order. */
export function byDay(items, getDate) {
  const groups = []
  for (const item of items) {
    const d = new Date(getDate(item))
    const key = d.toDateString()
    let g = groups.find((x) => x.key === key)
    if (!g) { g = { key, date: d, items: [] }; groups.push(g) }
    g.items.push(item)
  }
  return groups
}

export function dayLabel(d) {
  const today = new Date(); today.setHours(0, 0, 0, 0)
  const that = new Date(d); that.setHours(0, 0, 0, 0)
  const diff = Math.round((that - today) / 86400000)
  if (diff === 0) return 'Today'
  if (diff === 1) return 'Tomorrow'
  return d.toLocaleDateString([], { weekday: 'long', day: 'numeric', month: 'long' })
}

/** A stable ticket number from an id, like a printed serial. */
export function serial(id) {
  let h = 0
  for (const c of String(id)) h = (h * 31 + c.charCodeAt(0)) % 9973
  return String(h).padStart(4, '0')
}

export const stampDate = (d = new Date()) => d.toLocaleDateString('en-GB', { day: '2-digit', month: 'short' })

/** The stamp for a seat state. */
export function seatStamp(status, position, when) {
  const d = when ? new Date(when) : new Date()
  if (status === 'CONFIRMED') return { top: 'Admitted', big: stampDate(d), bottom: 'Thapar', tone: '' }
  if (status === 'WAITLISTED') return { top: 'Waitlist', big: position ? `No. ${position}` : 'Queued', bottom: 'In line', tone: 'sepia' }
  if (status === 'CHECKED_IN') return { top: 'Checked in', big: stampDate(d), bottom: 'Thapar', tone: 'green' }
  return null
}
