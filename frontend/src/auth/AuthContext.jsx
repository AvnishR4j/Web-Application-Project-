import { createContext, useCallback, useContext, useEffect, useMemo, useState } from 'react'
import { api, peek, seed, tokenStore } from '../api/client'

export const ROLES = {
  STUDENT: 'STUDENT',
  CLUB_ORGANIZER: 'CLUB_ORGANIZER',
  FACULTY_ADVISOR: 'FACULTY_ADVISOR',
  RESOURCE_ADMIN: 'RESOURCE_ADMIN',
  SUPER_ADMIN: 'SUPER_ADMIN',
}

const AuthContext = createContext(null)

export function AuthProvider({ children }) {
  // After a refresh, show the app straight away with the last-known user and check the session behind it.
  const cached = tokenStore.get() ? peek('/api/users/me') : undefined
  const [user, setUser] = useState(cached || null)
  const [loading, setLoading] = useState(Boolean(tokenStore.get()) && !cached)

  useEffect(() => {
    if (!tokenStore.get()) return
    api('/api/users/me', { fresh: true })
      .then(setUser)
      .catch((err) => {
        // Only a rejected session signs you out; a slow or unreachable server does not.
        if (err.status === 401 || err.status === 403) { tokenStore.clear(); setUser(null) }
      })
      .finally(() => setLoading(false))
  }, [])

  const handleAuth = useCallback((res) => {
    tokenStore.set(res.token)
    seed('/api/users/me', res.user)
    setUser(res.user)
    return res.user
  }, [])

  const login = useCallback(
    async (email, password) =>
      handleAuth(await api('/api/auth/login', { method: 'POST', body: { email, password } })),
    [handleAuth],
  )

  const register = useCallback(
    async (form) => handleAuth(await api('/api/auth/register', { method: 'POST', body: form })),
    [handleAuth],
  )

  const loginWithGoogle = useCallback(
    async (credential) =>
      handleAuth(await api('/api/auth/google', { method: 'POST', body: { credential } })),
    [handleAuth],
  )

  const logout = useCallback(() => {
    tokenStore.clear()
    setUser(null)
  }, [])

  const value = useMemo(
    () => ({ user, loading, login, loginWithGoogle, acceptSession: handleAuth, register, logout }),
    [user, loading, login, loginWithGoogle, handleAuth, register, logout],
  )
  return <AuthContext.Provider value={value}>{children}</AuthContext.Provider>
}

export function useAuth() {
  return useContext(AuthContext)
}
