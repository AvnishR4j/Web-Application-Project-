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
    <div className="card">
      <div className="page-head">
        <div>
          <h1>Check-in</h1>
          <p>Put this screen where people come in. The code changes every 30 seconds, so a photo of it stops working.</p>
        </div>
        <Link to="/my-events"><button className="secondary">Back to My events</button></Link>
      </div>
      {error && <p className="notice bad" role="alert">{error}</p>}
      {data && (
        <div className="panel checkin-screen">
          {qr && <img src={qr} alt={`QR code to check in, code ${data.code}`} width="300" height="300" style={{ background: '#fff', padding: 10, borderRadius: 12 }} />}
          <div>
            <p className="muted" style={{ margin: '0 0 6px' }}>Scan the code, or open My tickets and type</p>
            <div className="checkin-code num" aria-live="polite">{data.code}</div>
            <p className="muted" style={{ margin: '10px 0 22px' }}>New code in {secondsLeft} s</p>
            <div className="row" style={{ alignItems: 'baseline', gap: 8 }}>
              <span className="num" style={{ fontSize: '2.6rem', fontWeight: 800, fontStretch: '120%' }}>{data.checkedIn}</span>
              <span style={{ fontSize: '1.1rem' }}>of {data.registered} registered have checked in</span>
            </div>
            <span className="seatbar" style={{ display: 'block', maxWidth: 360, height: 10, marginTop: 8 }} aria-hidden="true">
              <i style={{ width: `${data.registered ? (data.checkedIn / data.registered) * 100 : 0}%` }} />
            </span>
          </div>
        </div>
      )}
    </div>
  )
}
