import { useEffect, useRef, useState } from 'react'
import { api } from '../api/client'
import { useAuth } from '../auth/AuthContext'

const SCRIPT = 'https://accounts.google.com/gsi/client'
let configPromise

/** What the sign-in pages need to know (fetched once). Falls back to "no Google, passwords on" if unreachable. */
export function useAuthConfig() {
  const [config, setConfig] = useState(null)
  useEffect(() => {
    configPromise ||= api('/api/auth/config', { fresh: true }).catch(() => {
      configPromise = null
      return { googleClientId: '', googleDomains: [], passwordSignup: true }
    })
    let live = true
    configPromise.then((c) => live && setConfig(c))
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

  useEffect(() => {
    if (!clientId) return undefined
    let cancelled = false
    loadScript()
      .then(() => {
        if (cancelled || !box.current) return
        window.google.accounts.id.initialize({
          client_id: clientId,
          hd: domains[0], // a hint only; the server enforces the domain
          callback: async ({ credential }) => {
            try {
              onDone(await loginWithGoogle(credential))
            } catch (err) {
              onError(err.message)
            }
          },
        })
        const dark = document.documentElement.dataset.theme === 'dark'
        window.google.accounts.id.renderButton(box.current, {
          theme: dark ? 'filled_black' : 'outline',
          size: 'large',
          text: 'continue_with',
          shape: 'rectangular',
          width: Math.min(320, box.current.parentElement?.clientWidth || 320),
        })
      })
      .catch((err) => onError(err.message))
    return () => { cancelled = true }
  }, [clientId]) // eslint-disable-line react-hooks/exhaustive-deps

  if (!clientId) return null
  return (
    <div className="google-signin">
      <div ref={box} />
      {domains.length > 0 && <p className="muted" style={{ margin: 0 }}>Use your @{domains.join(' / @')} account.</p>}
    </div>
  )
}
