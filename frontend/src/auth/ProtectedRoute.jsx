import { Navigate, Outlet } from 'react-router-dom'
import { useAuth } from './AuthContext'

/**
 * Hides pages from the wrong role. This is only a convenience for the user:
 * the real enforcement is on the server, which rejects the API calls anyway.
 */
export default function ProtectedRoute({ allow }) {
  const { user, loading } = useAuth()
  if (loading) return <p className="muted">Loading...</p>
  if (!user) return <Navigate to="/login" replace />
  if (allow && !allow.includes(user.role)) return <Navigate to="/" replace />
  return <Outlet />
}
