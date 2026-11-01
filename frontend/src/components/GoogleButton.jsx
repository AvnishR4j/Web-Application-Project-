import { useEffect, useRef, useState } from 'react'
import { api } from '../api/client'
import { useAuth } from '../auth/AuthContext'

export function GoogleLogo({ size = 20 }) {
  return (
    <svg viewBox="0 0 48 48" width={size} height={size} aria-hidden="true">
      <path fill="#EA4335" d="M24 9.5c3.5 0 6.6 1.2 9.1 3.6l6.8-6.8C35.8 2.4 30.3 0 24 0 14.6 0 6.5 5.4 2.6 13.2l7.9 6.1C12.4 13.6 17.7 9.5 24 9.5z"/>
      <path fill="#4285F4" d="M46.5 24.5c0-1.6-.1-3.1-.4-4.5H24v9h12.7c-.6 3-2.3 5.5-4.8 7.2l7.7 6c4.5-4.2 6.9-10.3 6.9-17.7z"/>
      <path fill="#FBBC05" d="M10.5 28.7a14.5 14.5 0 0 1 0-9.4l-7.9-6.1a24 24 0 0 0 0 21.6l7.9-6.1z"/>
      <path fill="#34A853" d="M24 48c6.5 0 11.9-2.1 15.9-5.8l-7.7-6c-2.2 1.4-4.9 2.3-8.2 2.3-6.3 0-11.6-4.1-13.5-9.8l-7.9 6.1C6.5 42.6 14.6 48 24 48z"/>
    </svg>
  )
}

function LockIcon() {
  return (
    <svg viewBox="0 0 24 24" width="13" height="13" fill="none" stroke="currentColor" strokeWidth="2" strokeLinecap="round" aria-hidden="true" style={{ verticalAlign: '-2px', marginRight: 4 }}>
      <rect x="5" y="11" width="14" height="10" rx="2" />
      <path d="M8 11V8a4 4 0 0 1 8 0v3" />
    </svg>
  )
}

const SCRIPT = 'https://accounts.google.com/gsi/client'
let configPromise

/** What the sign-in pages need to know (fetched once). Falls back to "no Google, passwords on" if unreachable. */
export function useAuthConfig() {
  // The sign-in options rarely change, so the page shows the last-known ones at once and checks behind.
  const [config, setConfig] = useState(() => {
    try { return JSON.parse(localStorage.getItem('campus.authConfig')) } catch { return null }
  })
  useEffect(() => {
    configPromise ||= api('/api/auth/config', { fresh: true }).then((c) => {
      try { localStorage.setItem('campus.authConfig', JSON.stringify(c)) } catch { /* unavailable */ }
      return c
    }).catch(() => {
      configPromise = null
      return null
    })
    let live = true
    configPromise.then((c) => { if (live) setConfig((old) => c || old || { googleClientId: '', googleDomains: [], passwordSignup: true }) })
    return () => { live = false }
  }, [])
  return config
}

function loadScript() {
  return new Promise((resolve, reject) => {
    if (window.google?.accounts?.id) return resolve()
    const existing = document.querySelector(`script[src="${SCRIPT}"]`)
    const el = existing || Object.assign(document.createElement('script'), { src: SCRIPT, async: true })
    el.addEventListener('load', resolve, { once: true })
    el.addEventListener('error', () => reject(new Error('Could not load Google sign-in.')), { once: true })
    if (!existing) document.head.appendChild(el)
  })
}

/** "Continue with Google". Renders nothing until the server says a Google client ID is configured. */
export default function GoogleButton({ clientId, domains = [], onDone, onError }) {
  const { loginWithGoogle } = useAuth()
  const box = useRef(null)
  const [signing, setSigning] = useState(false)

  useEffect(() => {
    if (!clientId) return undefined
    let cancelled = false
    loadScript()
      .then(() => {
        if (cancelled || !box.current) return
        window.google.accounts.id.initialize({
          client_id: clientId,
          hd: domains[0], // a hint only; the server enforces the domain
          // Chrome and Edge show their own built-in account picker (FedCM) instead of opening a pop-up window.
          use_fedcm_for_button: true,
          button_auto_select: true,
          context: 'signin',
          callback: async ({ credential }) => {
            setSigning(true)
            try {
              onDone(await loginWithGoogle(credential))
            } catch (err) {
              setSigning(false)
              onError(err.message)
            }
          },
        })
        // Google draws its own button inside an iframe we cannot style, so it is laid invisibly over our own
        // button: ours is what people see, Google's is what receives the click.
        window.google.accounts.id.renderButton(box.current, {
          theme: 'outline',
          size: 'large',
          text: 'continue_with',
          shape: 'rectangular',
          width: Math.min(400, box.current.parentElement?.clientWidth || 320),
        })
      })
      .catch((err) => onError(err.message))
    return () => { cancelled = true }
  }, [clientId]) // eslint-disable-line react-hooks/exhaustive-deps

  if (!clientId) return null
  return (
    <div className="google-signin">
      <div className="google-btn" data-signing={signing} aria-busy={signing}>
        {signing ? (
          <>
            <span className="google-spinner" aria-hidden="true" />
            <span>Signing you in…</span>
          </>
        ) : (
          <>
            <GoogleLogo />
            <span>Continue with Google</span>
            <span className="google-arrow" aria-hidden="true">→</span>
          </>
        )}
        <div ref={box} className="google-overlay" hidden={signing} />
      </div>
      <p className="google-note">
        <LockIcon /> You sign in on Google&apos;s own page. Campus Board never sees your password.
      </p>
      {domains.length > 0 && <p className="google-note">College accounts only: @{domains.join(', @')}</p>}
    </div>
  )
}
