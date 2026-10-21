import { useCallback, useEffect, useState } from 'react'
import { api } from '../api/client'
import { ROLES, useAuth } from '../auth/AuthContext'

const ROLE_LIST = Object.values(ROLES)
const EMPTY = { name: '', email: '', password: '', department: '', role: ROLES.CLUB_ORGANIZER }

export default function AdminUsers() {
  const { user: me } = useAuth()
  const [users, setUsers] = useState([])
  const [form, setForm] = useState(EMPTY)
  const [error, setError] = useState('')

  const load = useCallback(() => {
    api('/api/admin/users').then(setUsers).catch((e) => setError(e.message))
  }, [])

  useEffect(load, [load])

  const set = (key) => (e) => setForm({ ...form, [key]: e.target.value })

  async function create(e) {
    e.preventDefault()
    setError('')
    try {
      await api('/api/admin/users', { method: 'POST', body: form })
      setForm(EMPTY)
      load()
    } catch (err) {
      setError(err.message)
    }
  }

  async function changeRole(id, role) {
    setError('')
    try {
      await api(`/api/admin/users/${id}/role`, { method: 'PATCH', body: { role } })
      load()
    } catch (err) {
      setError(err.message)
    }
  }

  return (
    <div className="card">
      <h1>Users and roles</h1>
      {error && <p className="error" role="alert">{error}</p>}
      <table>
        <thead>
          <tr><th>Name</th><th>Email</th><th>Department</th><th>Role</th></tr>
        </thead>
        <tbody>
          {users.map((u) => (
            <tr key={u.id}>
              <td>{u.name}</td>
              <td>{u.email}</td>
              <td>{u.department}</td>
              <td>
                <select
                  value={u.role}
                  disabled={u.email === me.email}
                  onChange={(e) => changeRole(u.id, e.target.value)}
                >
                  {ROLE_LIST.map((r) => <option key={r}>{r}</option>)}
                </select>
              </td>
            </tr>
          ))}
        </tbody>
      </table>

      <h2>Create an account</h2>
      <form className="grid" onSubmit={create}>
        <input placeholder="Name" value={form.name} onChange={set('name')} required />
        <input type="email" placeholder="Email" value={form.email} onChange={set('email')} required />
        <input placeholder="Department" value={form.department} onChange={set('department')} />
        <input type="password" placeholder="Temporary password (8+ chars)" minLength={8}
               value={form.password} onChange={set('password')} required />
        <select value={form.role} onChange={set('role')}>
          {ROLE_LIST.map((r) => <option key={r}>{r}</option>)}
        </select>
        <button>Create</button>
      </form>
    </div>
  )
}
