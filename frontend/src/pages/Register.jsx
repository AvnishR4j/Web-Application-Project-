import { useState } from 'react'
import { Link, useNavigate } from 'react-router-dom'
import { useAuth } from '../auth/AuthContext'
import AuthLayout from '../components/AuthLayout'
import GoogleButton, { useAuthConfig } from '../components/GoogleButton'

export default function Register() {
  const { register } = useAuth()
  const navigate = useNavigate()
  const [form, setForm] = useState({ name: '', email: '', password: '', department: '' })
  const [error, setError] = useState('')
  const [fields, setFields] = useState({})
  const [busy, setBusy] = useState(false)
  const config = useAuthConfig()

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
    <AuthLayout>
      <form onSubmit={submit} className="signin-card">
        <header className="signin-head">
          <h2>Create your account</h2>
          <p>Students join here. Other roles are set up by a super admin.</p>
        </header>
        {config?.googleClientId && (
          <>
            <GoogleButton clientId={config.googleClientId} domains={config.googleDomains} onDone={() => navigate('/')} onError={setError} />
            {config.passwordSignup !== false && <div className="or-divider"><span>or sign up with a password</span></div>}
          </>
        )}
        {config?.passwordSignup === false && (
          <p className="muted" style={{ margin: 0 }}>Sign-up is with your college Google account. <Link to="/login">Back to log in</Link></p>
        )}
        {config?.passwordSignup !== false && (
          <>
        <label>
          Full name
          <input autoComplete="name" value={form.name} onChange={set('name')} required />
          {fields.name && <span className="error">{fields.name}</span>}
        </label>
        <label>
          College email
          <input type="email" autoComplete="email" value={form.email} onChange={set('email')} required />
          {fields.email && <span className="error">{fields.email}</span>}
        </label>
        <label>
          Department
          <input value={form.department} onChange={set('department')} placeholder="For example Computer Science" />
        </label>
        <label>
          Password, at least 8 characters
          <input type="password" autoComplete="new-password" value={form.password} onChange={set('password')} minLength={8} required />
          {fields.password && <span className="error">{fields.password}</span>}
        </label>
          </>
        )}
        {error && <p className="notice bad" role="alert">{error}</p>}
        {config?.passwordSignup !== false && <button disabled={busy}>{busy ? 'Creating your account...' : 'Create account'}</button>}
        <p className="signin-foot">
          Already registered? <Link to="/login">Log in</Link>
        </p>
      </form>
    </AuthLayout>
  )
}
