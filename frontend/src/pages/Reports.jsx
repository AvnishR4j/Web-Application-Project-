import { useCallback, useEffect, useState } from 'react'
import { api } from '../api/client'
import { fmt } from './eventUtils'

const day = 86400000
const toDateInput = (d) => new Date(d.getTime() - d.getTimezoneOffset() * 60000).toISOString().slice(0, 10)
const startOfDay = (s) => new Date(`${s}T00:00`)
const pct = (v) => (v == null ? '-' : `${v}%`)
const hours = (m) => `${Math.round(m / 6) / 10} h`

export default function Reports() {
  const [from, setFrom] = useState(toDateInput(new Date(Date.now() - 30 * day)))
  const [to, setTo] = useState(toDateInput(new Date(Date.now() + 30 * day)))
  const [usage, setUsage] = useState(null)
  const [att, setAtt] = useState(null)
  const [error, setError] = useState('')

  const load = useCallback(() => {
    setError('')
    const f = startOfDay(from).toISOString()
    const t = new Date(startOfDay(to).getTime() + day).toISOString() // include the whole "to" day
    Promise.all([api(`/api/reports/utilization?from=${f}&to=${t}`), api(`/api/reports/attendance?from=${f}&to=${t}`)])
      .then(([u, a]) => { setUsage(u); setAtt(a) })
      .catch((e) => setError(e.message))
  }, [from, to])
  useEffect(load, [load])

  return (
    <div className="card">
      <h1>Reports</h1>
      <div className="row" style={{ marginBottom: 16 }}>
        <label>From<input type="date" value={from} onChange={(e) => setFrom(e.target.value)} /></label>
        <label>To<input type="date" value={to} onChange={(e) => setTo(e.target.value)} /></label>
      </div>
      {error && <p className="error" role="alert">{error}</p>}

      <h2>Room and equipment use</h2>
      <p className="muted">Approved booked time as a share of opening hours in this range. Pending requests are not counted.</p>
      {!usage ? <p className="muted">Loading...</p> : (
        <table>
          <thead><tr><th>Resource</th><th>Bookings</th><th>For events</th><th>Booked</th><th>Open</th><th style={{ width: '30%' }}>Use</th></tr></thead>
          <tbody>
            {usage.map((u) => (
              <tr key={u.resourceId}>
                <td>{u.name} <span className="muted">({u.type.toLowerCase()}{u.active ? '' : ', inactive'})</span></td>
                <td>{u.bookings}</td>
                <td>{u.eventBookings}</td>
                <td>{hours(u.bookedMinutes)}</td>
                <td>{hours(u.openMinutes)}</td>
                <td>
                  <div className="row" style={{ flexWrap: 'nowrap' }}>
                    <div style={{ flex: 1, background: 'var(--border)', borderRadius: 3, height: 10 }}>
                      <div style={{ width: `${Math.min(100, u.utilization || 0)}%`, background: 'var(--accent)', height: 10, borderRadius: 3 }} />
                    </div>
                    <span style={{ width: 52, textAlign: 'right' }}>{pct(u.utilization)}</span>
                  </div>
                </td>
              </tr>
            ))}
          </tbody>
        </table>
      )}

      <h2>Attendance</h2>
      <p className="muted">Events that have already ended in this range.</p>
      {!att ? <p className="muted">Loading...</p> : (
        <>
          <div className="grid" style={{ marginBottom: 16 }}>
            {[['Events', att.events], ['Registered', att.registered], ['Attended', att.attended],
              ['No-shows', att.noShows], ['Attendance', pct(att.attendanceRate)]].map(([label, value]) => (
              <div key={label} className="notice"><div className="muted">{label}</div><div style={{ fontSize: '1.6rem', fontWeight: 600 }}>{value}</div></div>
            ))}
          </div>
          {att.byClub.length > 0 && (
            <table>
              <thead><tr><th>Club</th><th>Events</th><th>Registered</th><th>Attended</th><th>Attendance</th></tr></thead>
              <tbody>{att.byClub.map((c) => <tr key={c.clubName}><td>{c.clubName}</td><td>{c.events}</td><td>{c.registered}</td><td>{c.attended}</td><td>{pct(c.attendanceRate)}</td></tr>)}</tbody>
            </table>
          )}
          {att.byEvent.length === 0 ? <p className="muted">No events have ended in this range.</p> : (
            <table>
              <thead><tr><th>Event</th><th>Club</th><th>When</th><th>Registered</th><th>Attended</th><th>No-shows</th><th>Attendance</th></tr></thead>
              <tbody>{att.byEvent.map((e) => (
                <tr key={e.eventId}><td>{e.title}</td><td>{e.clubName}</td><td>{fmt(e.start)}</td><td>{e.registered}</td><td>{e.attended}</td><td>{e.noShows}</td><td>{pct(e.attendanceRate)}</td></tr>
              ))}</tbody>
            </table>
          )}
        </>
      )}
    </div>
  )
}
