import { useCallback, useEffect, useState } from 'react'
import { api } from '../api/client'
import { ROLES, useAuth } from '../auth/AuthContext'

const EMPTY = {
  name: '', type: 'ROOM', location: '', capacity: 0, bufferMinutes: 15,
  openTime: '', closeTime: '', requiresTraining: false,
}

export default function Resources() {
  const { user } = useAuth()
  const canManage = user.role === ROLES.RESOURCE_ADMIN || user.role === ROLES.SUPER_ADMIN
  const [items, setItems] = useState([])
  const [form, setForm] = useState(EMPTY)
  const [error, setError] = useState('')

  const load = useCallback(() => {
    api(`/api/resources${canManage ? '?includeInactive=true' : ''}`)
      .then(setItems)
      .catch((e) => setError(e.message))
  }, [canManage])

  useEffect(load, [load])

  const set = (key) => (e) => {
    const v = e.target.type === 'checkbox' ? e.target.checked : e.target.value
    setForm({ ...form, [key]: v })
  }

  async function create(e) {
    e.preventDefault()
    setError('')
    try {
      await api('/api/resources', {
        method: 'POST',
        body: {
          ...form,
          capacity: Number(form.capacity),
          bufferMinutes: Number(form.bufferMinutes),
          openTime: form.openTime || null,
          closeTime: form.closeTime || null,
        },
      })
      setForm(EMPTY)
      load()
    } catch (err) {
      const first = Object.values(err.fields || {})[0]
      setError(first ? `${err.message}: ${first}` : err.message)
    }
  }

  async function toggleActive(r) {
    setError('')
    try {
      await api(`/api/resources/${r.id}`, {
        method: 'PUT',
        body: {
          name: r.name, type: r.type, location: r.location, capacity: r.capacity,
          bufferMinutes: r.bufferMinutes, openTime: r.openTime, closeTime: r.closeTime,
          requiresTraining: r.requiresTraining, active: !r.active,
        },
      })
      load()
    } catch (err) {
      setError(err.message)
    }
  }

  return (
    <div className="card">
      <h1>Rooms, labs and equipment</h1>
      {error && <p className="error" role="alert">{error}</p>}
      <table>
        <thead>
          <tr>
            <th>Name</th><th>Type</th><th>Location</th><th>Capacity</th>
            <th>Hours</th><th>Buffer</th><th>Training</th>{canManage && <th>Status</th>}
          </tr>
        </thead>
        <tbody>
          {items.map((r) => (
            <tr key={r.id}>
              <td>{r.name}</td>
              <td>{r.type}</td>
              <td>{r.location}</td>
              <td>{r.type === 'EQUIPMENT' ? '-' : r.capacity}</td>
              <td>{r.openTime ? `${r.openTime} to ${r.closeTime}` : 'All day'}</td>
              <td>{r.bufferMinutes} min</td>
              <td>{r.requiresTraining ? 'Required' : 'No'}</td>
              {canManage && (
                <td>
                  <button className="secondary" onClick={() => toggleActive(r)}>
                    {r.active ? 'Deactivate' : 'Activate'}
                  </button>
                </td>
              )}
            </tr>
          ))}
        </tbody>
      </table>

      {canManage && (
        <form className="stack" onSubmit={create}>
          <h2>Add a resource</h2>
          <div className="grid">
            <label>Name<input value={form.name} onChange={set('name')} required /></label>
            <label>Type
              <select value={form.type} onChange={set('type')}>
                <option>ROOM</option><option>LAB</option><option>EQUIPMENT</option>
              </select>
            </label>
            <label>Location<input value={form.location} onChange={set('location')} /></label>
            <label>Capacity<input type="number" min="0" value={form.capacity} onChange={set('capacity')} /></label>
            <label>Buffer (minutes)<input type="number" min="0" max="240" value={form.bufferMinutes} onChange={set('bufferMinutes')} /></label>
            <label>Opens (optional)<input type="time" value={form.openTime} onChange={set('openTime')} /></label>
            <label>Closes (optional)<input type="time" value={form.closeTime} onChange={set('closeTime')} /></label>
          </div>
          <label className="row">
            <input type="checkbox" checked={form.requiresTraining} onChange={set('requiresTraining')} />
            Requires training or sign-off
          </label>
          <div><button type="submit">Add resource</button></div>
        </form>
      )}
    </div>
  )
}
