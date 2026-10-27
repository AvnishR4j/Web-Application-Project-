import { useState } from 'react'
import { Link, useLocation, useNavigate } from 'react-router-dom'
import { useAuth } from '../auth/AuthContext'
import AuthLayout from '../components/AuthLayout'
import GoogleButton, { useAuthConfig } from '../components/GoogleButton'

export default function Login() {
  const { login } = useAuth()
  const navigate = useNavigate()
  const location = useLocation()
  const [email, setEmail] = useState('')
  const [password, setPassword] = useState('')
  const [error, setError] = useState('')
  const [busy, setBusy] = useState(false)
  const config = useAuthConfig()

  function arrived() {
    const from = location.state?.from
    navigate(typeof from === 'string' && from.startsWith('/') ? from : '/', { replace: true })
  }

  async function submit(e) {
    e.preventDefault()
    setError('')
    setBusy(true)
    try {
      await login(email, password)
      // Go back to where the user was heading (for example a scanned check-in link).
      arrived()
    } catch (err) {
      setError(err.message)
    } finally {
      setBusy(false)
    }
  }

  return (
    <AuthLayout>
      <form onSubmit={submit}>
        <h2>Log in</h2>
        {config?.googleClientId && (
          <>
            <GoogleButton clientId={config.googleClientId} domains={config.googleDomains} onDone={arrived} onError={setError} />
            <p className="muted" style={{ margin: 0, textAlign: 'center' }}>or use a password</p>
          </>
        )}
        <label>
          College email
          <input type="email" autoComplete="email" value={email} onChange={(e) => setEmail(e.target.value)} required />
        </label>
        <label>
          Password
          <input type="password" autoComplete="current-password" value={password} onChange={(e) => setPassword(e.target.value)} required />
        </label>
        {error && <p className="notice bad" role="alert">{error}</p>}
        <button disabled={busy}>{busy ? 'Logging in...' : 'Log in'}</button>
        {config?.passwordSignup !== false && (
          <p className="muted" style={{ margin: 0 }}>
            New student? <Link to="/register">Create an account</Link>
          </p>
        )}
        <p className="demo-hint">
          Trying the demo? Use <code>student@campus.test</code>, <code>organizer@</code>, <code>advisor@</code>,{' '}
          <code>resources@</code> or <code>admin@campus.test</code>. Ask the team for the password.
        </p>
      </form>
    </AuthLayout>
  )
}
