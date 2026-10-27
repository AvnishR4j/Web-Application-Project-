import { useState } from 'react'
import { Link } from 'react-router-dom'
import { api } from '../api/client'
import AuthLayout from '../components/AuthLayout'

export default function ForgotPassword() {
  const [email, setEmail] = useState('')
  const [sent, setSent] = useState(false)
  const [error, setError] = useState('')
  const [busy, setBusy] = useState(false)

  async function submit(e) {
    e.preventDefault()
    setError('')
    setBusy(true)
    try {
      await api('/api/auth/password/forgot', { method: 'POST', body: { email } })
      setSent(true)
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
          <h2>{sent ? 'Check your inbox' : 'Forgot password?'}</h2>
          <p>{sent ? `If ${email} has an account, a reset link is on its way. It works for 30 minutes.` : "Enter your email and we'll send you a link to choose a new one."}</p>
        </header>
        {!sent && (
          <>
            <label>
              Email
              <input type="email" autoComplete="email" value={email} onChange={(e) => setEmail(e.target.value)} placeholder="you@thapar.edu" required autoFocus />
            </label>
            {error && <p className="notice bad signin-error" role="alert">{error}</p>}
            <button disabled={busy}>{busy ? 'Sending…' : 'Send reset link'}</button>
          </>
        )}
        {sent && <p className="signin-hint">Nothing after a few minutes? Check your spam folder, or try again.</p>}
        <p className="signin-foot"><Link to="/login">Back to sign in</Link></p>
      </form>
    </AuthLayout>
  )
}
