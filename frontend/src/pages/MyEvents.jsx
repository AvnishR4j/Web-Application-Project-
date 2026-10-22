import { useCallback, useEffect, useState } from 'react'
import { api } from '../api/client'
import EventHistory from './EventHistory'
import EventRegistrations from './EventRegistrations'
import { STATUS_LABEL, byId, describeError, fmt } from './eventUtils'

const toLocal = (iso) => {
  const d = new Date(iso)
  return new Date(d.getTime() - d.getTimezoneOffset() * 60000).toISOString().slice(0, 16)
}
const EMPTY = { clubId: '', title: '', description: '', resourceId: '', start: '', end: '', expectedAttendees: 30 }
const EDITABLE = ['DRAFT', 'REJECTED', 'SUBMITTED', 'ADVISOR_APPROVED', 'APPROVED']
const CANCELLABLE = ['DRAFT', 'REJECTED', 'SUBMITTED', 'ADVISOR_APPROVED', 'APPROVED']

export default function MyEvents() {
  const [events, setEvents] = useState([])
  const [clubs, setClubs] = useState([])
  const [rooms, setRooms] = useState([])
  const [leadDays, setLeadDays] = useState(7)
  const [form, setForm] = useState(EMPTY)
  const [editing, setEditing] = useState(null) // event being edited
  const [error, setError] = useState('')
  const [note, setNote] = useState('')
  const [historyFor, setHistoryFor] = useState(null)
  const [registrationsFor, setRegistrationsFor] = useState(null)
  const [loaded, setLoaded] = useState(false)

  const load = useCallback(() => {
    Promise.all([api('/api/events/mine'), api('/api/clubs/mine'), api('/api/resources'), api('/api/events/rules')])
      .then(([e, c, r, rules]) => {
        setEvents(e); setClubs(c); setRooms(r); setLeadDays(rules.minLeadDays); setLoaded(true)
        setForm((f) => ({ ...f, clubId: f.clubId || c[0]?.id || '', resourceId: f.resourceId || r[0]?.id || '' }))
      })
      .catch((err) => setError(err.message))
  }, [])
  useEffect(load, [load])

  const clubsById = byId(clubs)
  const roomsById = byId(rooms)
  const set = (k) => (e) => setForm({ ...form, [k]: e.target.value })

  function startEdit(ev) {
    setEditing(ev)
    setForm({ clubId: ev.clubId, title: ev.title, description: ev.description || '', resourceId: ev.resourceId,
      start: toLocal(ev.start), end: toLocal(ev.end), expectedAttendees: ev.expectedAttendees })
    setNote(['SUBMITTED', 'ADVISOR_APPROVED', 'APPROVED'].includes(ev.status)
      ? 'Saving changes takes this event back to draft and frees its room. You will need to submit it again.' : '')
  }

  function reset() { setEditing(null); setForm({ ...EMPTY, clubId: clubs[0]?.id || '', resourceId: rooms[0]?.id || '' }); setNote('') }

  async function save(e) {
    e.preventDefault()
    setError('')
    const body = { ...form, start: new Date(form.start).toISOString(), end: new Date(form.end).toISOString(),
      expectedAttendees: Number(form.expectedAttendees) }
    try {
      if (editing) await api(`/api/events/${editing.id}`, { method: 'PUT', body })
      else await api('/api/events', { method: 'POST', body })
      reset(); load()
    } catch (err) { setError(describeError(err)) }
  }

  async function act(id, action, body) {
    setError('')
    try { await api(`/api/events/${id}/${action}`, { method: 'POST', body }); load() }
    catch (err) { setError(describeError(err)) }
  }

  if (!loaded && !error) return <div className="card"><p className="muted">Loading...</p></div>
  if (clubs.length === 0 && !error) {
    return <div className="card"><h1>My events</h1><p className="muted">You are not an organizer of any club yet. Ask a super admin to add you to a club.</p></div>
  }

  return (
    <div className="card">
      <h1>My events</h1>
      {error && <p className="error" role="alert">{error}</p>}
      <table>
        <thead><tr><th>Event</th><th>When</th><th>Venue</th><th>Status</th><th /></tr></thead>
        <tbody>
          {events.map((ev) => (
            <tr key={ev.id}>
              <td>{ev.title}<div className="muted">{clubsById[ev.clubId]?.name}</div></td>
              <td>{fmt(ev.start)}</td>
              <td>{roomsById[ev.resourceId]?.name}</td>
              <td>
                <span className={`badge ${ev.status}`}>{STATUS_LABEL[ev.status]}</span>
                {ev.rejectionReason && <div className="muted">Reason: {ev.rejectionReason}</div>}
              </td>
              <td>
                <div className="row">
                  {['DRAFT', 'REJECTED'].includes(ev.status) && <button onClick={() => act(ev.id, 'submit')}>Submit</button>}
                  {EDITABLE.includes(ev.status) && <button className="secondary" onClick={() => startEdit(ev)}>Edit</button>}
                  {CANCELLABLE.includes(ev.status) && <button className="link" onClick={() => act(ev.id, 'cancel', {})}>Cancel</button>}
                  <button className="link" onClick={() => setHistoryFor(historyFor === ev.id ? null : ev.id)}>History</button>
                  {['APPROVED', 'COMPLETED'].includes(ev.status) && (
                    <button className="link" onClick={() => setRegistrationsFor(registrationsFor === ev.id ? null : ev.id)}>Registrations</button>
                  )}
                </div>
              </td>
            </tr>
          ))}
        </tbody>
      </table>
      {historyFor && <EventHistory eventId={historyFor} />}
      {registrationsFor && <EventRegistrations eventId={registrationsFor} />}

      <form className="stack" onSubmit={save}>
        <h2>{editing ? `Edit "${editing.title}"` : 'New event request'}</h2>
        <p className="muted">Requests must be submitted at least {leadDays} days before the event. The club's faculty advisor reviews first, then the space manager.</p>
        {note && <p className="notice">{note}</p>}
        <div className="grid">
          <label>Club
            <select value={form.clubId} onChange={set('clubId')} disabled={Boolean(editing)} required>
              {clubs.map((c) => <option key={c.id} value={c.id}>{c.name}</option>)}
            </select>
          </label>
          <label>Venue
            <select value={form.resourceId} onChange={set('resourceId')} required>
              {rooms.map((r) => <option key={r.id} value={r.id}>{r.name} ({r.type})</option>)}
            </select>
          </label>
          <label>Seats (expected attendees)<input type="number" min="1" value={form.expectedAttendees} onChange={set('expectedAttendees')} required /></label>
        </div>
        <label>Title<input value={form.title} onChange={set('title')} maxLength={160} required /></label>
        <label>Description<input value={form.description} onChange={set('description')} maxLength={2000} /></label>
        <div className="grid">
          <label>Start<input type="datetime-local" value={form.start} onChange={set('start')} required /></label>
          <label>End<input type="datetime-local" value={form.end} onChange={set('end')} required /></label>
        </div>
        <div className="row">
          <button type="submit">{editing ? 'Save changes' : 'Save as draft'}</button>
          {editing && <button type="button" className="link" onClick={reset}>Stop editing</button>}
        </div>
      </form>
    </div>
  )
}
