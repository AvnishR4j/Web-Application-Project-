import { useState } from 'react'
import { Link, useNavigate } from 'react-router-dom'
import { useAuth } from '../auth/AuthContext'

export default function Register() {
  const { register } = useAuth()
  const navigate = useNavigate()
  const [form, setForm] = useState({ name: '', email: '', password: '', department: '' })
  const [error, setError] = useState('')
  const [fields, setFields] = useState({})
  const [busy, setBusy] = useState(false)

  const set = (key) => (e) => setForm({ ...form, [key]: e.target.value })

  async function submit(e) {
    e.preventDefault()
    setError('')
    setFields({})
    setBusy(true)
    try {
      await register(form)
      navigate('/')
    } catch (err) {
      setError(err.message)
      setFields(err.fields || {})
    } finally {
      setBusy(false)
    }
  }

  return (
    <form className="card narrow" onSubmit={submit}>
      <h1>Create a student account</h1>
      <label>
        Name
        <input value={form.name} onChange={set('name')} required />
        {fields.name && <span className="error">{fields.name}</span>}
      </label>
      <label>
        Email
        <input type="email" value={form.email} onChange={set('email')} required />
        {fields.email && <span className="error">{fields.email}</span>}
      </label>
      <label>
        Department
        <input value={form.department} onChange={set('department')} />
      </label>
      <label>
        Password (at least 8 characters)
        <input type="password" value={form.password} onChange={set('password')} minLength={8} required />
        {fields.password && <span className="error">{fields.password}</span>}
      </label>
      {error && <p className="error" role="alert">{error}</p>}
      <button disabled={busy}>{busy ? 'Creating...' : 'Create account'}</button>
      <p className="muted">
        Already registered? <Link to="/login">Log in</Link>
      </p>
      <p className="muted">Club organizer and admin accounts are created by a super admin.</p>
    </form>
  )
}
