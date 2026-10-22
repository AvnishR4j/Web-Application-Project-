import { useEffect, useRef, useState } from 'react'
import { Link, useParams, useSearchParams } from 'react-router-dom'
import { api } from '../api/client'
import { describeError } from './eventUtils'

// Opened by scanning the organizer's QR code (code in the link), or from My registrations (type the code).
export default function CheckIn() {
  const { id } = useParams()
  const [params] = useSearchParams()
  const [code, setCode] = useState(params.get('code') || '')
  const [done, setDone] = useState(null)
  const [error, setError] = useState('')
  const [busy, setBusy] = useState(false)
  const tried = useRef(false)

  async function submit(value) {
    setError(''); setBusy(true)
    try { setDone(await api(`/api/events/${id}/checkin`, { method: 'POST', body: { code: value } })) }
    catch (err) { setError(describeError(err)) }
    finally { setBusy(false) }
  }

  useEffect(() => {
    const fromLink = params.get('code')
    if (fromLink && !tried.current) { tried.current = true; submit(fromLink) }
  }, []) // eslint-disable-line react-hooks/exhaustive-deps

  if (done) {
    return (
      <div className="card narrow">
        <h1>You are checked in</h1>
        <p className="notice good">Checked in at {new Date(done.checkedInAt).toLocaleTimeString([], { timeStyle: 'short' })}. Enjoy the event.</p>
        <p>After the event, you can give feedback from <Link to="/registrations">My registrations</Link>.</p>
      </div>
    )
  }
  return (
    <form className="card narrow" onSubmit={(e) => { e.preventDefault(); submit(code) }}>
      <h1>Check in</h1>
      <p className="muted">Scan the QR code on the organizer's screen, or type the 6-character code shown under it.</p>
      <label>Code
        <input value={code} onChange={(e) => setCode(e.target.value.toUpperCase())} maxLength={8}
          autoCapitalize="characters" autoComplete="off" style={{ fontSize: '1.4rem', letterSpacing: '0.2em' }} required />
      </label>
      {error && <p className="error" role="alert">{error}</p>}
      <button disabled={busy}>{busy ? 'Checking in...' : 'Check in'}</button>
    </form>
  )
}
