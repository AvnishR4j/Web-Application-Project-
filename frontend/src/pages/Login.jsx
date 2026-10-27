import { useEffect, useLayoutEffect, useRef, useState } from 'react'
import { Link, useLocation, useNavigate } from 'react-router-dom'
import { api } from '../api/client'
import { useAuth } from '../auth/AuthContext'
import AuthLayout from '../components/AuthLayout'
import GoogleButton, { GoogleLogo, useAuthConfig } from '../components/GoogleButton'

function MailIcon() {
  return (
    <svg viewBox="0 0 24 24" width="17" height="17" fill="none" stroke="currentColor" strokeWidth="1.8" strokeLinecap="round" strokeLinejoin="round" aria-hidden="true">
      <rect x="3" y="5" width="18" height="14" rx="3" />
      <path d="m4 7 8 6 8-6" />
    </svg>
  )
}

function KeyIcon() {
  return (
    <svg viewBox="0 0 24 24" width="17" height="17" fill="none" stroke="currentColor" strokeWidth="1.8" strokeLinecap="round" strokeLinejoin="round" aria-hidden="true">
      <circle cx="8" cy="15" r="4" />
      <path d="m10.8 12.2 8.2-8.2M16 7l2.5 2.5M14 9l2 2" />
    </svg>
  )
}

const RESEND_SECONDS = 60

/**
 * One card, up to three ways in: Google, a code sent by email, or a password. The switch at the top slides
 * between them; hovering a side with a mouse opens it too, unless the person has started typing.
 */
export default function Login() {
  const { login, acceptSession } = useAuth()
  const navigate = useNavigate()
  const location = useLocation()
  const config = useAuthConfig()
  const [email, setEmail] = useState('')
  const [password, setPassword] = useState('')
  const [codeEmail, setCodeEmail] = useState('')
  const [code, setCode] = useState('')
  const [codeSentTo, setCodeSentTo] = useState('')
  const [resendIn, setResendIn] = useState(0)
  const [error, setError] = useState('')
  const [busy, setBusy] = useState(false)
  const [mode, setMode] = useState(null)
  const [height, setHeight] = useState(undefined)
  const panes = useRef({})
  const panesBox = useRef(null)
  const hoverTimer = useRef(0)

  const tabs = [
    config?.googleClientId && { id: 'google', label: 'Google', icon: <GoogleLogo size={16} /> },
    config?.emailCode && { id: 'code', label: 'Email code', icon: <MailIcon /> },
    { id: 'password', label: 'Password', icon: <KeyIcon /> },
  ].filter(Boolean)
  const active = tabs.some((t) => t.id === mode) ? mode : tabs[0].id
  const activeIndex = tabs.findIndex((t) => t.id === active)

  // The card grows or shrinks smoothly to fit whichever side is showing.
  useLayoutEffect(() => {
    const el = panes.current[active]
    if (!el) return undefined
    const measure = () => setHeight(el.offsetHeight)
    measure()
    const ro = new ResizeObserver(measure)
    ro.observe(el)
    return () => ro.disconnect()
  }, [active, tabs.length])

  // Countdown for "Resend code".
  useEffect(() => {
    if (resendIn <= 0) return undefined
    const id = setTimeout(() => setResendIn((s) => s - 1), 1000)
    return () => clearTimeout(id)
  }, [resendIn])

  function arrived() {
    const from = location.state?.from
    navigate(typeof from === 'string' && from.startsWith('/') ? from : '/', { replace: true })
  }

  function choose(next) {
    if (next === active) return
    setError('')
    setMode(next)
  }

  // Hover opens a side after a short pause, so sweeping the mouse across does not flicker,
  // and never pulls someone away from something they are typing.
  function hover(next, e) {
    if (e.pointerType !== 'mouse') return
    const typing = email || password || codeEmail || code || panesBox.current?.contains(document.activeElement)
    if (typing && next !== active) return
    clearTimeout(hoverTimer.current)
    hoverTimer.current = setTimeout(() => choose(next), 140)
  }

  async function run(work) {
    setError('')
    setBusy(true)
    try {
      await work()
    } catch (err) {
      setError(err.message)
    } finally {
      setBusy(false)
    }
  }

  const submitPassword = (e) => {
    e.preventDefault()
    run(async () => {
      await login(email, password)
      arrived()
    })
  }

  const sendCode = (e) => {
    e?.preventDefault()
    run(async () => {
      await api('/api/auth/email-code', { method: 'POST', body: { email: codeEmail } })
      setCodeSentTo(codeEmail.trim().toLowerCase())
      setCode('')
      setResendIn(RESEND_SECONDS)
    })
  }

  const verifyCode = (value) =>
    run(async () => {
      acceptSession(await api('/api/auth/email-code/verify', { method: 'POST', body: { email: codeSentTo, code: value } }))
      arrived()
    })

  function typeCode(e) {
    const digits = e.target.value.replace(/\D/g, '').slice(0, 6)
    setCode(digits)
    if (digits.length === 6 && !busy) verifyCode(digits)
  }

  const side = (id) => {
    const i = tabs.findIndex((t) => t.id === id)
    return i === activeIndex ? 'on' : i < activeIndex ? 'left' : 'right'
  }

  if (!config) {
    return <AuthLayout><div className="signin-card signin-loading" aria-busy="true" /></AuthLayout>
  }

  return (
    <AuthLayout>
      <div className="signin-card">
        <header className="signin-head">
          <h2>Welcome back</h2>
          <p>Sign in to book rooms and join events.</p>
        </header>

        {tabs.length > 1 && (
          <div className="signin-switch" role="tablist" aria-label="How to sign in" style={{ '--tabs': tabs.length, '--at': activeIndex }}>
            <span className="signin-thumb" aria-hidden="true" />
            {tabs.map((t) => (
              <button
                key={t.id} type="button" role="tab" aria-selected={active === t.id}
                onClick={() => choose(t.id)} onPointerEnter={(e) => hover(t.id, e)} onPointerLeave={() => clearTimeout(hoverTimer.current)}
              >
                {t.icon} <span>{t.label}</span>
              </button>
            ))}
          </div>
        )}

        <div ref={panesBox} className="signin-panes" style={{ height }}>
          {config.googleClientId && (
            <section ref={(el) => { panes.current.google = el }} className="signin-pane" data-side={side('google')} role="tabpanel" aria-hidden={active !== 'google'}>
              <GoogleButton clientId={config.googleClientId} domains={config.googleDomains} onDone={arrived} onError={setError} />
            </section>
          )}

          {config.emailCode && (
            <section ref={(el) => { panes.current.code = el }} className="signin-pane" data-side={side('code')} role="tabpanel" aria-hidden={active !== 'code'}>
              {!codeSentTo ? (
                <form onSubmit={sendCode} className="signin-form">
                  <label>
                    College email
                    <input type="email" autoComplete="email" placeholder={`you@${config.googleDomains?.[0] || 'college.edu'}`} value={codeEmail} onChange={(e) => setCodeEmail(e.target.value)} required />
                  </label>
                  <button disabled={busy}>{busy ? 'Sending…' : 'Email me a code'}</button>
                  <p className="signin-hint">No password needed. We'll send a 6-digit code to your inbox.</p>
                </form>
              ) : (
                <form onSubmit={(e) => { e.preventDefault(); verifyCode(code) }} className="signin-form code-step">
                  <p className="signin-hint">We sent a code to <strong>{codeSentTo}</strong>. It works for 10 minutes; check spam if you don't see it.</p>
                  <label>
                    6-digit code
                    <input className="code-input" inputMode="numeric" autoComplete="one-time-code" placeholder="••••••" value={code} onChange={typeCode} maxLength={6} autoFocus required />
                  </label>
                  <button disabled={busy || code.length !== 6}>{busy ? 'Checking…' : 'Sign in'}</button>
                  <div className="code-actions">
                    <button type="button" className="link" onClick={() => { setCodeSentTo(''); setCode(''); setError('') }}>Use another email</button>
                    <button type="button" className="link" disabled={resendIn > 0 || busy} onClick={() => sendCode()}>
                      {resendIn > 0 ? `Resend in ${resendIn}s` : 'Resend code'}
                    </button>
                  </div>
                </form>
              )}
            </section>
          )}

          <section ref={(el) => { panes.current.password = el }} className="signin-pane" data-side={side('password')} role="tabpanel" aria-hidden={active !== 'password'}>
            <form onSubmit={submitPassword} className="signin-form">
              <label>
                College email
                <input type="email" autoComplete="email" placeholder="you@thapar.edu" value={email} onChange={(e) => setEmail(e.target.value)} required />
              </label>
              <div className="field">
                <div className="field-head">
                  <label htmlFor="login-password">Password</label>
                  {config.passwordReset && <Link to="/forgot-password" className="field-link">Forgot password?</Link>}
                </div>
                <input id="login-password" type="password" autoComplete="current-password" placeholder="••••••••" value={password} onChange={(e) => setPassword(e.target.value)} required />
              </div>
              <button disabled={busy}>{busy ? 'Logging in…' : 'Log in'}</button>
            </form>
          </section>
        </div>

        {error && <p className="notice bad signin-error" role="alert">{error}</p>}

        {config.passwordSignup !== false && (
          <p className="signin-foot">
            New student? <Link to="/register">Create an account</Link>
          </p>
        )}
        <p className="signin-foot" style={{ fontSize: '0.82rem' }}><Link to="/privacy">Privacy policy</Link></p>
      </div>
    </AuthLayout>
  )
}
