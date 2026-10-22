import QRCode from 'qrcode'
import { useCallback, useEffect, useState } from 'react'
import { Link, useParams } from 'react-router-dom'
import { api } from '../api/client'

// Shown by the organizer at the venue (projector or laptop). The code changes every 30 seconds.
export default function CheckinScreen() {
  const { id } = useParams()
  const [data, setData] = useState(null)
  const [qr, setQr] = useState('')
  const [error, setError] = useState('')
  const [secondsLeft, setSecondsLeft] = useState(0)

  const refresh = useCallback(() => {
    api(`/api/events/${id}/checkin-code`)
      .then(async (d) => {
        setData(d); setError('')
        const url = `${window.location.origin}/checkin/${id}?code=${d.code}`
        setQr(await QRCode.toDataURL(url, { width: 320, margin: 1 }))
      })
      .catch((e) => setError(e.message))
  }, [id])

  useEffect(() => {
    refresh()
    const poll = setInterval(refresh, 5000) // picks up the new code and the live check-in count
    return () => clearInterval(poll)
  }, [refresh])

  useEffect(() => {
    if (!data) return undefined
    const tick = () => setSecondsLeft(Math.max(0, Math.ceil((new Date(data.expiresAt) - Date.now()) / 1000)))
    tick()
    const t = setInterval(tick, 500)
    return () => clearInterval(t)
  }, [data])

  return (
    <div className="card" style={{ textAlign: 'center' }}>
      <h1>Scan to check in</h1>
      {error && <p className="error" role="alert">{error}</p>}
      {data && (
        <>
          {qr && <img src={qr} alt={`Check-in QR code ${data.code}`} width="320" height="320" style={{ background: '#fff', padding: 8, borderRadius: 8 }} />}
          <p className="muted">Or open <strong>Check in</strong> on My registrations and type:</p>
          <p style={{ fontSize: '2.6rem', letterSpacing: '0.3em', fontFamily: 'ui-monospace, monospace', margin: '4px 0' }}>{data.code}</p>
          <p className="muted">New code in {secondsLeft}s</p>
          <p style={{ fontSize: '1.4rem' }}><strong>{data.checkedIn}</strong> of {data.registered} registered have checked in</p>
        </>
      )}
      <p><Link to="/my-events">Back to My events</Link></p>
    </div>
  )
}
