import { useEffect, useState } from 'react'
import { Link, useSearchParams } from 'react-router-dom'
import { api } from '../api/client'
import AuthLayout from '../components/AuthLayout'

/** Landing page for the "Stop new-event emails" link. Opts out at once, with a way to undo. */
export default function Unsubscribe() {
  const [params] = useSearchParams()
  const email = params.get('email') || ''
  const token = params.get('token') || ''
  const [subscribed, setSubscribed] = useState(null)
  const [error, setError] = useState('')
  const [busy, setBusy] = useState(false)

  async function set(value) {
    setBusy(true)
    setError('')
    try {
      const res = await api('/api/mail/preferences', { method: 'POST', body: { email, token, subscribed: value } })
      setSubscribed(res.subscribed)
    } catch (err) {
      setError(err.message)
    } finally {
      setBusy(false)
    }
  }

  useEffect(() => {
    if (email && token) set(false)
    else setError('This link is incomplete. Use the link at the bottom of the email.')
  }, []) // eslint-disable-line react-hooks/exhaustive-deps

  return (
    <AuthLayout>
      <div className="signin-card">
        <header className="signin-head">
          <h2>{subscribed === true ? "You're back on the list" : subscribed === false ? 'Unsubscribed' : 'One moment…'}</h2>
          <p>
            {subscribed === false && <>We won't email <strong>{email}</strong> about new events any more. You'll still get emails about events you register for.</>}
            {subscribed === true && <>We'll email <strong>{email}</strong> when new events are announced.</>}
          </p>
        </header>
        {error && <p className="notice bad signin-error" role="alert">{error}</p>}
        {subscribed !== null && (
          <button className="secondary" disabled={busy} onClick={() => set(!subscribed)}>
            {subscribed ? 'Unsubscribe again' : 'Undo: keep sending new-event emails'}
          </button>
        )}
        <p className="signin-foot"><Link to="/login">Go to Campus Board</Link></p>
      </div>
    </AuthLayout>
  )
}
