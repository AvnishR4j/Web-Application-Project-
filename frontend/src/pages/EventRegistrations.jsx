import { useEffect, useState } from 'react'
import { api } from '../api/client'
import { fmt } from './eventUtils'

// Registrant list for the club's organizers, its advisor and admins.
export default function EventRegistrations({ eventId }) {
  const [data, setData] = useState(null)
  const [error, setError] = useState('')
  useEffect(() => {
    api(`/api/events/${eventId}/registrations`).then(setData).catch((e) => setError(e.message))
  }, [eventId])
  if (error) return <p className="error">{error}</p>
  if (!data) return null
  return (
    <div className="notice" style={{ marginBottom: 24 }}>
      <strong>Registrations: {data.confirmed.length} of {data.capacity} seats taken, {data.waitlist.length} waitlisted</strong>
      <div className="grid" style={{ marginTop: 8 }}>
        <div>
          <em>Registered</em>
          <ol>{data.confirmed.map((r) => <li key={r.id}>{r.userEmail}{r.promoted && ' (from waitlist)'}</li>)}</ol>
        </div>
        <div>
          <em>Waitlist, in order</em>
          <ol>{data.waitlist.map((r) => <li key={r.id}>{r.userEmail} <span className="muted">since {fmt(r.waitlistedAt)}</span></li>)}</ol>
        </div>
      </div>
    </div>
  )
}
