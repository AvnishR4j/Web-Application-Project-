import { useCallback, useEffect, useState } from 'react'
import { api } from '../api/client'

const EMPTY = { name: '', description: '', advisorEmail: '', organizers: '' }

export default function Clubs() {
  const [clubs, setClubs] = useState([])
  const [form, setForm] = useState(EMPTY)
  const [error, setError] = useState('')

  const load = useCallback(() => { api('/api/clubs').then(setClubs).catch((e) => setError(e.message)) }, [])
  useEffect(load, [load])
  const set = (k) => (e) => setForm({ ...form, [k]: e.target.value })

  async function create(e) {
    e.preventDefault()
    setError('')
    try {
      await api('/api/clubs', { method: 'POST', body: {
        name: form.name, description: form.description, advisorEmail: form.advisorEmail,
        organizerEmails: form.organizers.split(',').map((s) => s.trim()).filter(Boolean),
      } })
      setForm(EMPTY); load()
    } catch (err) { setError(err.message) }
  }

  return (
    <div className="card">
      <h1>Clubs</h1>
      {error && <p className="error" role="alert">{error}</p>}
      <table>
        <thead><tr><th>Club</th><th>Faculty advisor</th><th>Organizers</th></tr></thead>
        <tbody>
          {clubs.map((c) => (
            <tr key={c.id}><td>{c.name}</td><td>{c.advisorEmail}</td><td>{(c.organizerEmails || []).join(', ')}</td></tr>
          ))}
        </tbody>
      </table>
      <form className="stack" onSubmit={create}>
        <h2>Add a club</h2>
        <p className="muted">The advisor must have the FACULTY_ADVISOR role and organizers the CLUB_ORGANIZER role (set on the Users page).</p>
        <div className="grid">
          <label>Name<input value={form.name} onChange={set('name')} required /></label>
          <label>Advisor email<input type="email" value={form.advisorEmail} onChange={set('advisorEmail')} required /></label>
        </div>
        <label>Organizer emails (comma separated)<input value={form.organizers} onChange={set('organizers')} /></label>
        <label>Description<input value={form.description} onChange={set('description')} /></label>
        <div><button type="submit">Add club</button></div>
      </form>
    </div>
  )
}
