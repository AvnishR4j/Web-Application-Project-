import { useCallback, useEffect, useState } from 'react'
import { Link } from 'react-router-dom'
import { api, peek } from '../api/client'
import Ticket, { dayLabel, seatStamp, serial, stampDate } from '../components/Ticket'
import { STATUS_LABEL, describeError } from './eventUtils'

const CHECKIN_OPENS_MS = 30 * 60 * 1000

function FeedbackForm({ eventId, onDone }) {
  const [rating, setRating] = useState(0)
  const [comment, setComment] = useState('')
  const [error, setError] = useState('')
  async function send(e) {
    e.preventDefault()
    setError('')
    try { await api(`/api/events/${eventId}/feedback`, { method: 'POST', body: { rating, comment } }); onDone() }
    catch (err) { setError(describeError(err)) }
  }
  return (
    <form onSubmit={send} className="feedback">
      <span className="muted">How was it?</span>
      <div className="stars" role="radiogroup" aria-label="Rating out of 5">
        {[1, 2, 3, 4, 5].map((n) => (
          <button key={n} type="button" role="radio" aria-checked={rating === n} aria-label={`${n} out of 5`}
            className={`star${rating >= n ? ' on' : ''}`} onClick={() => setRating(n)}>★</button>
        ))}
      </div>
      <input placeholder="A line about it (optional)" value={comment} onChange={(e) => setComment(e.target.value)} maxLength={1000} />
      <button disabled={rating === 0}>Send</button>
      {error && <span className="error">{error}</span>}
    </form>
  )
}

/** Your tickets: every place you hold, stamped with where it stands. */
export default function MyRegistrations() {
  const [items, setItems] = useState(() => peek('/api/registrations/mine') || [])
  const [error, setError] = useState('')
  const [loaded, setLoaded] = useState(() => Boolean(peek('/api/registrations/mine')))
  const [standing, setStanding] = useState(() => peek('/api/registrations/standing') || null)

  const load = useCallback(() => {
    api('/api/registrations/mine', { fresh: true }).then(setItems).catch((e) => setError(e.message)).finally(() => setLoaded(true))
    api('/api/registrations/standing').then(setStanding).catch(() => setStanding(null))
  }, [])
  useEffect(load, [load])

  async function cancel(eventId) {
    setError('')
    try { await api(`/api/events/${eventId}/unregister`, { method: 'POST' }); load() }
    catch (err) { setError(describeError(err)) }
  }

  const now = Date.now()
  const upcoming = items.filter((x) => new Date(x.end).getTime() > now && x.registration.status !== 'CANCELLED')
  const past = items.filter((x) => !upcoming.includes(x))

  function describe(x, i) {
    const { registration: r, start, end, eventStatus, waitlistPosition, checkedIn, feedbackGiven } = x
    const s = new Date(start).getTime()
    const e = new Date(end).getTime()
    const started = now >= s
    const finished = now > e
    let stamp = null
    let actions = null
    if (eventStatus !== 'APPROVED' && eventStatus !== 'COMPLETED') {
      stamp = { top: 'Event', big: 'Void', bottom: STATUS_LABEL[eventStatus]?.toLowerCase(), tone: 'grey' }
    } else if (r.status === 'CANCELLED') {
      stamp = { top: 'Seat', big: 'Given up', bottom: 'Cancelled', tone: 'grey' }
    } else if (checkedIn) {
      stamp = seatStamp('CHECKED_IN', null, start)
      actions = started && !feedbackGiven ? <FeedbackForm eventId={r.eventId} onDone={load} />
        : feedbackGiven ? <span className="muted">Thanks for your feedback.</span> : null
    } else if (r.status === 'WAITLISTED') {
      stamp = seatStamp('WAITLISTED', waitlistPosition, start)
      actions = !started && <button type="button" className="link" onClick={() => cancel(r.eventId)}>Leave the waitlist</button>
    } else if (finished) {
      stamp = { top: 'Not attended', big: stampDate(new Date(start)), bottom: 'No check-in', tone: 'grey' }
    } else {
      stamp = { ...seatStamp('CONFIRMED', null, start), bottom: r.promoted ? 'From waitlist' : 'Thapar' }
      const open = now >= s - CHECKIN_OPENS_MS && now <= e
      actions = (<>
        {open ? <Link to={`/checkin/${r.eventId}`}><button>Check in now</button></Link>
          : <span className="muted">Check-in opens 30 minutes before the start.</span>}
        {!started && <button type="button" className="link" onClick={() => cancel(r.eventId)}>Give up my seat</button>}
      </>)
    }
    return (
      <Ticket key={r.id} index={i} start={start} end={end} title={x.eventTitle} day={dayLabel(new Date(start))}
        venue="Your ticket" number={serial(r.id)} live={started && !finished && r.status === 'CONFIRMED'} ended={finished || r.status === 'CANCELLED'}
        stamp={stamp} actions={actions || null} />
    )
  }

  return (
    <div className="card">
      <div className="page-head">
        <div>
          <p className="eyebrow">My tickets</p>
          <h1>Your seats, <em>kept.</em></h1>
          <p>Every place you hold. Check in from here when the event starts; give up a seat early so the next person can have it.</p>
        </div>
        <Link to="/events"><button className="secondary">Find more events</button></Link>
      </div>
      {error && <p className="notice bad" role="alert" style={{ marginBottom: 20 }}>{error}</p>}
      {standing && standing.limit > 0 && standing.noShows > 0 && (
        <p className={`notice ${standing.blocked ? 'bad' : 'info'}`} role="status" style={{ marginBottom: 24 }}>
          {standing.blocked
            ? `You missed ${standing.noShows} events you registered for in the last ${standing.windowDays} days, so new registrations are paused for now.`
            : `You missed ${standing.noShows} event${standing.noShows > 1 ? 's' : ''} you registered for in the last ${standing.windowDays} days. At ${standing.limit}, new registrations pause. Give up your seat ahead if you cannot come.`}
        </p>
      )}
      {!loaded ? (
        <div className="ticket-grid">{[0, 1].map((i) => <div key={i} className="tk-ghost" />)}</div>
      ) : items.length === 0 ? (
        <div className="empty"><p>No tickets yet.</p><Link to="/events"><button>Browse the programme</button></Link></div>
      ) : (
        <>
          {upcoming.length ? <div className="ticket-grid">{upcoming.map(describe)}</div>
            : <div className="empty"><p>Nothing coming up.</p><Link to="/events"><button>Browse the programme</button></Link></div>}
          {past.length > 0 && (<>
            <h2 className="date-group" style={{ marginTop: 56 }}>Past and given up</h2>
            <div className="ticket-grid">{past.map((x, i) => describe(x, i + upcoming.length))}</div>
          </>)}
        </>
      )}
    </div>
  )
}
