import { createContext, useCallback, useContext, useEffect, useMemo, useState } from 'react'
import { api, tokenStore } from '../api/client'

export const ROLES = {
  STUDENT: 'STUDENT',
  CLUB_ORGANIZER: 'CLUB_ORGANIZER',
  FACULTY_ADVISOR: 'FACULTY_ADVISOR',
  RESOURCE_ADMIN: 'RESOURCE_ADMIN',
  SUPER_ADMIN: 'SUPER_ADMIN',
}

const AuthContext = createContext(null)

export function AuthProvider({ children }) {
  const [user, setUser] = useState(null)
  const [loading, setLoading] = useState(Boolean(tokenStore.get()))

  // Restore the session after a page refresh.
  useEffect(() => {
    if (!tokenStore.get()) return
    api('/api/users/me')
      .then(setUser)
      .catch(() => tokenStore.clear())
      .finally(() => setLoading(false))
  }, [])

  const handleAuth = useCallback((res) => {
    tokenStore.set(res.token)
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
