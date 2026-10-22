import { Navigate, Outlet, useLocation } from 'react-router-dom'
import { useAuth } from './AuthContext'

/**
 * Hides pages from the wrong role. This is only a convenience for the user:
 * the real enforcement is on the server, which rejects the API calls anyway.
 * Remembers where the user was going, so a scanned check-in link survives the login page.
 */
export default function ProtectedRoute({ allow }) {
  const { user, loading } = useAuth()
  const location = useLocation()
  if (loading) return <p className="muted">Loading...</p>
  if (!user) return <Navigate to="/login" replace state={{ from: location.pathname + location.search }} />
  if (allow && !allow.includes(user.role)) return <Navigate to="/" replace />
  return <Outlet />
}
