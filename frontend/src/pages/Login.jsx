import { useLayoutEffect, useRef, useState } from 'react'
import { Link, useLocation, useNavigate } from 'react-router-dom'
import { useAuth } from '../auth/AuthContext'
import AuthLayout from '../components/AuthLayout'
import GoogleButton, { GoogleLogo, useAuthConfig } from '../components/GoogleButton'

function MailIcon() {
  return (
    <svg viewBox="0 0 24 24" width="18" height="18" fill="none" stroke="currentColor" strokeWidth="1.8" strokeLinecap="round" strokeLinejoin="round" aria-hidden="true">
      <rect x="3" y="5" width="18" height="14" rx="3" />
      <path d="m4 7 8 6 8-6" />
    </svg>
  )
}

/**
 * One card, two ways in. The switch at the top slides between "Google" and "Password"; hovering a side
 * with a mouse opens it too, unless the person has already started typing a password.
 */
export default function Login() {
  const { login } = useAuth()
  const navigate = useNavigate()
  const location = useLocation()
  const config = useAuthConfig()
  const [email, setEmail] = useState('')
  const [password, setPassword] = useState('')
  const [error, setError] = useState('')
  const [busy, setBusy] = useState(false)
  const [mode, setMode] = useState('google')
  const [height, setHeight] = useState(undefined)
  const panes = useRef({})
  const form = useRef(null)
  const hoverTimer = useRef(0)

  const hasGoogle = Boolean(config?.googleClientId)
  const active = hasGoogle ? mode : 'password'

  // The card grows or shrinks smoothly to fit whichever side is showing.
  useLayoutEffect(() => {
    const el = panes.current[active]
    if (!el) return undefined
    const measure = () => setHeight(el.offsetHeight)
    measure()
    const ro = new ResizeObserver(measure)
    ro.observe(el)
    return () => ro.disconnect()
  }, [active, hasGoogle, error])

  function arrived() {
    const from = location.state?.from
    navigate(typeof from === 'string' && from.startsWith('/') ? from : '/', { replace: true })
  }

  function choose(next) {
    if (next === mode) return
    setError('')
    setMode(next)
  }

  // Hover opens a side after a short pause, so sweeping the mouse across does not flicker,
  // and never pulls someone away from a password they are typing.
  function hover(next, e) {
    if (e.pointerType !== 'mouse') return
    const typing = email || password || form.current?.contains(document.activeElement)
    if (next === 'google' && typing) return
    clearTimeout(hoverTimer.current)
    hoverTimer.current = setTimeout(() => choose(next), 140)
  }

  async function submit(e) {
    e.preventDefault()
    setError('')
    setBusy(true)
    try {
      await login(email, password)
      arrived()
    } catch (err) {
      setError(err.message)
    } finally {
      setBusy(false)
    }
  }

  return (
    <AuthLayout>
      <div className="signin-card">
        <header className="signin-head">
          <h2>Welcome back</h2>
          <p>Sign in to book rooms and join events.</p>
        </header>

        {hasGoogle && (
          <div className="signin-switch" role="tablist" aria-label="How to sign in" data-active={active}>
            <span className="signin-thumb" aria-hidden="true" />
            <button
              type="button" role="tab" aria-selected={active === 'google'}
              onClick={() => choose('google')} onPointerEnter={(e) => hover('google', e)} onPointerLeave={() => clearTimeout(hoverTimer.current)}
            >
              <GoogleLogo size={16} /> Google
            </button>
            <button
              type="button" role="tab" aria-selected={active === 'password'}
              onClick={() => choose('password')} onPointerEnter={(e) => hover('password', e)} onPointerLeave={() => clearTimeout(hoverTimer.current)}
            >
              <MailIcon /> Password
            </button>
          </div>
        )}

        <div className="signin-panes" style={{ height }}>
          {hasGoogle && (
            <section ref={(el) => { panes.current.google = el }} className="signin-pane from-left" data-on={active === 'google'} role="tabpanel" aria-hidden={active !== 'google'}>
              <GoogleButton clientId={config.googleClientId} domains={config.googleDomains} onDone={arrived} onError={setError} />
            </section>
          )}
          <section ref={(el) => { panes.current.password = el }} className="signin-pane from-right" data-on={active === 'password'} role="tabpanel" aria-hidden={active !== 'password'}>
            <form ref={form} onSubmit={submit} className="signin-form">
              <label>
                College email
                <input type="email" autoComplete="email" placeholder="you@thapar.edu" value={email} onChange={(e) => setEmail(e.target.value)} required />
              </label>
              <label>
                Password
                <input type="password" autoComplete="current-password" placeholder="••••••••" value={password} onChange={(e) => setPassword(e.target.value)} required />
              </label>
              <button disabled={busy}>{busy ? 'Logging in…' : 'Log in'}</button>
            </form>
          </section>
        </div>

        {error && <p className="notice bad signin-error" role="alert">{error}</p>}

        {config?.passwordSignup !== false && (
          <p className="signin-foot">
            New student? <Link to="/register">Create an account</Link>
          </p>
        )}
      </div>
    </AuthLayout>
  )
}
