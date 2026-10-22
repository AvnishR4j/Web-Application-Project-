import { useEffect, useState } from 'react'
import { api, ApiError } from '../api/client'

const fmt = (iso) => new Date(iso).toLocaleString([], { dateStyle: 'medium', timeStyle: 'short' })
const toIso = (local) => new Date(local).toISOString()

export default function BookResource() {
  const [resources, setResources] = useState([])
  const [form, setForm] = useState({ resourceId: '', title: '', start: '', end: '', attendees: 1 })
  const [violations, setViolations] = useState(null) // null = not checked yet
  const [busy, setBusy] = useState([])
  const [done, setDone] = useState(null)
  const [error, setError] = useState('')
  // One key per submit attempt: if the request is retried (double click, flaky network) it cannot book twice.
  const [idempotencyKey, setKey] = useState(() => crypto.randomUUID())

  useEffect(() => {
    api('/api/resources').then((list) => {
      setResources(list)
      if (list.length) setForm((f) => ({ ...f, resourceId: list[0].id }))
    }).catch((e) => setError(e.message))
  }, [])

  // Show what is already taken in the next 14 days for the chosen resource.
  useEffect(() => {
    if (!form.resourceId) return
    const from = new Date().toISOString()
    const to = new Date(Date.now() + 14 * 86400000).toISOString()
    api(`/api/bookings/schedule?resourceId=${form.resourceId}&from=${from}&to=${to}`)
      .then(setBusy)
      .catch(() => setBusy([]))
  }, [form.resourceId, done])

  const set = (key) => (e) => {
    setForm({ ...form, [key]: e.target.value })
    setViolations(null)
    setDone(null)
  }

  const body = () => ({
    resourceId: form.resourceId,
    title: form.title,
    start: toIso(form.start),
    end: toIso(form.end),
    attendees: Number(form.attendees),
  })

  async function check() {
    setError('')
    try {
      const result = await api('/api/bookings/check', { method: 'POST', body: body() })
      setViolations(result.violations)
    } catch (err) {
      setError(err.message)
    }
  }

  async function submit(e) {
    e.preventDefault()
    setError('')
    try {
      const booking = await api('/api/bookings', {
        method: 'POST', body: body(), headers: { 'Idempotency-Key': idempotencyKey },
      })
      setDone(booking)
      setKey(crypto.randomUUID())
      setViolations(null)
    } catch (err) {
      if (err instanceof ApiError && err.status === 409 && Object.keys(err.fields).length) {
        setViolations(Object.entries(err.fields).map(([code, message]) => ({ code, message })))
        setKey(crypto.randomUUID())
      } else {
        setError(err.message)
      }
    }
  }

  return (
    <div className="card">
      <h1>Book a room, lab or equipment</h1>
      {error && <p className="error" role="alert">{error}</p>}
      <form className="stack" onSubmit={submit}>
        <label>Resource
          <select value={form.resourceId} onChange={set('resourceId')} required>
            {resources.map((r) => <option key={r.id} value={r.id}>{r.name} ({r.type})</option>)}
          </select>
        </label>
        <label>Purpose<input value={form.title} onChange={set('title')} required maxLength={160} /></label>
        <div className="grid">
          <label>Start<input type="datetime-local" value={form.start} onChange={set('start')} required /></label>
          <label>End<input type="datetime-local" value={form.end} onChange={set('end')} required /></label>
          <label>Expected attendees
            <input type="number" min="1" value={form.attendees} onChange={set('attendees')} required />
          </label>
        </div>

        {violations && violations.length === 0 && (
          <p className="notice good" role="status">Available. You can submit this request.</p>
        )}
        {violations && violations.length > 0 && (
          <div className="notice bad" role="alert">
            <strong>This booking cannot be made as it is:</strong>
            <ul>{violations.map((v) => <li key={v.code}>{v.message}</li>)}</ul>
          </div>
        )}
        {done && (
          <p className="notice good" role="status">
            Request submitted ({fmt(done.start)} to {fmt(done.end)}). A resource admin will review it.
            Track it under My bookings.
          </p>
        )}

        <div className="row">
          <button type="button" className="secondary" onClick={check}
            disabled={!form.resourceId || !form.start || !form.end}>
            Check availability
          </button>
          <button type="submit" disabled={!form.resourceId || !form.start || !form.end}>Submit request</button>
        </div>
      </form>

      <h2>Already taken in the next 14 days</h2>
      {busy.length === 0 ? <p className="muted">Nothing booked.</p> : (
        <table>
          <thead><tr><th>From</th><th>To</th><th>Status</th></tr></thead>
          <tbody>
            {busy.map((s, i) => (
              <tr key={i}>
                <td>{fmt(s.start)}</td><td>{fmt(s.end)}</td>
                <td><span className={`badge ${s.status}`}>{s.status}</span></td>
              </tr>
            ))}
          </tbody>
        </table>
      )}
    </div>
  )
}
