import { useState } from 'react'
import { Link, useNavigate, useSearchParams } from 'react-router-dom'
import { api } from '../api/client'
import { useAuth } from '../auth/AuthContext'
import AuthLayout from '../components/AuthLayout'

export default function ResetPassword() {
  const [params] = useSearchParams()
  const token = params.get('token') || ''
  const { acceptSession } = useAuth()
  const navigate = useNavigate()
  const [password, setPassword] = useState('')
  const [again, setAgain] = useState('')
  const [error, setError] = useState('')
  const [busy, setBusy] = useState(false)

  async function submit(e) {
    e.preventDefault()
    if (password !== again) {
      setError("The two passwords don't match.")
      return
    }
    setError('')
    setBusy(true)
    try {
      acceptSession(await api('/api/auth/password/reset', { method: 'POST', body: { token, password } }))
      navigate('/', { replace: true })
    } catch (err) {
      setError(err.message)
    } finally {
      setBusy(false)
    }
  }

  return (
    <AuthLayout>
      <form className="signin-card" onSubmit={submit}>
        <header className="signin-head">
          <h2>Choose a new password</h2>
          <p>At least 8 characters. You'll be signed in straight after.</p>
        </header>
        {!token ? (
          <p className="notice bad signin-error">This link is incomplete. Open the link from your email again.</p>
        ) : (
          <>
            <label>
              New password
              <input type="password" autoComplete="new-password" minLength={8} value={password} onChange={(e) => setPassword(e.target.value)} required autoFocus />
            </label>
            <label>
              Type it again
              <input type="password" autoComplete="new-password" minLength={8} value={again} onChange={(e) => setAgain(e.target.value)} required />
            </label>
            {error && <p className="notice bad signin-error" role="alert">{error}</p>}
            <button disabled={busy}>{busy ? 'Saving…' : 'Save and sign in'}</button>
          </>
        )}
        <p className="signin-foot"><Link to="/forgot-password">Need a new link?</Link></p>
      </form>
    </AuthLayout>
  )
}
