import { useCallback, useEffect, useState } from 'react'
import { Link } from 'react-router-dom'
import { api } from '../api/client'
import Ticket from '../components/Ticket'
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
    <form onSubmit={send} className="stack" style={{ gap: 8, marginBottom: 0 }}>
      <span className="stub-status NONE">How was it?</span>
      <div className="row" role="radiogroup" aria-label="Rating out of 5" style={{ gap: 4 }}>
        {[1, 2, 3, 4, 5].map((n) => (
          <button key={n} type="button" role="radio" aria-checked={rating === n} aria-label={`${n} out of 5`}
            className={rating >= n ? '' : 'secondary'} style={{ minHeight: 36, padding: '4px 0', width: 34 }}
            onClick={() => setRating(n)}>{n}</button>
        ))}
      </div>
      <input placeholder="Comment (optional)" value={comment} onChange={(e) => setComment(e.target.value)} maxLength={1000} />
      <button disabled={rating === 0}>Send feedback</button>
      {error && <span className="error">{error}</span>}
    </form>
  )
}

export default function MyRegistrations() {
  const [items, setItems] = useState([])
  const [error, setError] = useState('')
  const [loaded, setLoaded] = useState(false)
  const [standing, setStanding] = useState(null)

  const load = useCallback(() => {
    api('/api/registrations/mine').then(setItems).catch((e) => setError(e.message)).finally(() => setLoaded(true))
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

  function stub({ registration: r, start, end, eventStatus, waitlistPosition, checkedIn, feedbackGiven }) {
    const s = new Date(start).getTime()
    const e = new Date(end).getTime()
    const started = now >= s
    if (eventStatus !== 'APPROVED' && eventStatus !== 'COMPLETED') {
      return <span className="stub-status NONE">Event {STATUS_LABEL[eventStatus]?.toLowerCase()}</span>
    }
    if (r.status === 'CANCELLED') return <span className="stub-status NONE">You cancelled</span>
    if (checkedIn) {
      if (started && !feedbackGiven) return <FeedbackForm eventId={r.eventId} onDone={load} />
      return (<>
        <span className="stub-status CHECKED_IN">Checked in</span>
        {feedbackGiven && <span className="muted" style={{ fontSize: '0.85rem' }}>Thanks for your feedback.</span>}
      </>)
    }
    if (r.status === 'WAITLISTED') {
      return (<>
        <span className="stub-status WAITLISTED">Waitlist #{waitlistPosition}</span>
        {!started && <button className="secondary" onClick={() => cancel(r.eventId)}>Leave waitlist</button>}
      </>)
    }
    const checkinOpen = now >= s - CHECKIN_OPENS_MS && now <= e
    return (<>
      <span className="stub-status CONFIRMED">{r.promoted ? 'In, from the waitlist' : 'You are in'}</span>
      {checkinOpen && <Link to={`/checkin/${r.eventId}`}><button>Check in</button></Link>}
      {!checkinOpen && !started && <span className="muted" style={{ fontSize: '0.85rem' }}>Check-in opens 30 minutes before the start.</span>}
      {!started && <button className="secondary" onClick={() => cancel(r.eventId)}>Cancel my place</button>}
      {now > e && <span className="muted" style={{ fontSize: '0.85rem' }}>Not checked in.</span>}
    </>)
  }

  const ticket = (x) => (
    <Ticket key={x.registration.id} start={x.start} end={x.end} title={x.eventTitle} stub={stub(x)} />
  )

  return (
    <div className="card">
      <div className="page-head">
        <div>
          <h1>My tickets</h1>
          <p>Your places at events. Check in from here when the event starts.</p>
        </div>
        <Link to="/events"><button className="secondary">Find events</button></Link>
      </div>
      {error && <p className="notice bad" role="alert" style={{ marginBottom: 16 }}>{error}</p>}
      {standing && standing.limit > 0 && standing.noShows > 0 && (
        <p className={`notice ${standing.blocked ? 'bad' : ''}`} role="status" style={{ marginBottom: 16 }}>
          {standing.blocked
            ? `You missed ${standing.noShows} events you registered for in the last ${standing.windowDays} days, so new registrations are paused for now.`
            : `You missed ${standing.noShows} event${standing.noShows > 1 ? 's' : ''} you registered for in the last ${standing.windowDays} days. At ${standing.limit}, new registrations are paused. Cancel ahead if you cannot come.`}
        </p>
      )}
      {!loaded ? (
        <div className="panel"><div className="skeleton" /></div>
      ) : items.length === 0 ? (
        <div className="empty"><p>You have no tickets yet.</p><Link to="/events"><button>Browse events</button></Link></div>
      ) : (
        <>
          <div className="tickets">{upcoming.length ? upcoming.map(ticket) : <div className="empty"><p>Nothing coming up.</p></div>}</div>
          {past.length > 0 && (<>
            <h2>Past and cancelled</h2>
            <div className="tickets">{past.map(ticket)}</div>
          </>)}
        </>
      )}
    </div>
  )
}
