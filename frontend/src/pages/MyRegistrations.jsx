import { useCallback, useEffect, useState } from 'react'
import { Link } from 'react-router-dom'
import { api } from '../api/client'
import { STATUS_LABEL, describeError, fmt } from './eventUtils'

const REG_LABEL = { CONFIRMED: 'Registered', WAITLISTED: 'Waitlisted', CANCELLED: 'Cancelled' }
const REG_BADGE = { CONFIRMED: 'APPROVED', WAITLISTED: 'PENDING', CANCELLED: 'CANCELLED' }

const CHECKIN_OPENS_MS = 30 * 60 * 1000

function FeedbackForm({ eventId, onDone }) {
  const [rating, setRating] = useState(5)
  const [comment, setComment] = useState('')
  const [error, setError] = useState('')
  async function send(e) {
    e.preventDefault()
    setError('')
    try { await api(`/api/events/${eventId}/feedback`, { method: 'POST', body: { rating: Number(rating), comment } }); onDone() }
    catch (err) { setError(describeError(err)) }
  }
  return (
    <form className="row" onSubmit={send}>
      <select value={rating} onChange={(e) => setRating(e.target.value)} aria-label="Rating">
        {[5, 4, 3, 2, 1].map((n) => <option key={n} value={n}>{n} star{n > 1 ? 's' : ''}</option>)}
      </select>
      <input placeholder="Comment (optional)" value={comment} onChange={(e) => setComment(e.target.value)} maxLength={1000} />
      <button>Send feedback</button>
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

  return (
    <div className="card">
      <h1>My registrations</h1>
      {error && <p className="error" role="alert">{error}</p>}
      {standing && standing.limit > 0 && standing.noShows > 0 && (
        <p className={`notice ${standing.blocked ? 'bad' : ''}`} role="status">
          {standing.blocked
            ? `You missed ${standing.noShows} events you registered for in the last ${standing.windowDays} days, so new registrations are paused for now.`
            : `You missed ${standing.noShows} event${standing.noShows > 1 ? 's' : ''} you registered for in the last ${standing.windowDays} days. At ${standing.limit}, new registrations are paused. Cancel ahead if you cannot come.`}
        </p>
      )}
      {!loaded ? <p className="muted">Loading...</p> : items.length === 0 ? <p className="muted">You have not registered for any events yet.</p> : (
        <table>
          <thead><tr><th>Event</th><th>When</th><th>My place</th><th /></tr></thead>
          <tbody>
            {items.map(({ registration: r, eventTitle, start, end, eventStatus, waitlistPosition, checkedIn, feedbackGiven }) => {
              const now = Date.now()
              const checkinOpen = r.status === 'CONFIRMED' && eventStatus === 'APPROVED' && !checkedIn
                && now >= new Date(start).getTime() - CHECKIN_OPENS_MS && now <= new Date(end).getTime()
              const started = now >= new Date(start).getTime()
              return (
              <tr key={r.id}>
                <td>{eventTitle}
                  {eventStatus !== 'APPROVED' && <div className="muted">Event is {STATUS_LABEL[eventStatus]?.toLowerCase()}</div>}
                </td>
                <td>{fmt(start)}</td>
                <td>
                  <span className={`badge ${REG_BADGE[r.status]}`}>
                    {REG_LABEL[r.status]}{r.status === 'WAITLISTED' && ` #${waitlistPosition}`}
                  </span>
                  {r.promoted && r.status === 'CONFIRMED' && <div className="muted">Moved up from the waitlist</div>}
                  {checkedIn && <div className="muted">Checked in</div>}
                </td>
                <td>
                  <div className="stack" style={{ marginBottom: 0 }}>
                    {checkinOpen && <Link to={`/checkin/${r.eventId}`}><button>Check in</button></Link>}
                    {checkedIn && started && !feedbackGiven && <FeedbackForm eventId={r.eventId} onDone={load} />}
                    {feedbackGiven && <span className="muted">Thanks for your feedback</span>}
                    {r.status !== 'CANCELLED' && eventStatus === 'APPROVED' && !started && (
                      <button className="secondary" onClick={() => cancel(r.eventId)}>
                        {r.status === 'CONFIRMED' ? 'Cancel' : 'Leave waitlist'}
                      </button>
                    )}
                  </div>
                </td>
              </tr>
              )
            })}
          </tbody>
        </table>
      )}
    </div>
  )
}
