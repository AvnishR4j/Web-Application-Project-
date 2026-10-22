import { Link, Navigate, Route, Routes, useNavigate } from 'react-router-dom'
import { ROLES, useAuth } from './auth/AuthContext'
import ProtectedRoute from './auth/ProtectedRoute'
import AdminUsers from './pages/AdminUsers'
import ApprovalQueue from './pages/ApprovalQueue'
import BookResource from './pages/BookResource'
import Home from './pages/Home'
import Login from './pages/Login'
import MyBookings from './pages/MyBookings'
import Register from './pages/Register'
import Resources from './pages/Resources'

const MANAGERS = [ROLES.RESOURCE_ADMIN, ROLES.SUPER_ADMIN]
const BOOKERS = [ROLES.CLUB_ORGANIZER, ROLES.RESOURCE_ADMIN, ROLES.SUPER_ADMIN]

function Nav() {
  const { user, logout } = useAuth()
  const navigate = useNavigate()
  return (
    <header className="nav">
      <Link to="/" className="brand">Campus Events and Booking</Link>
      {user && (
        <nav className="nav-links">
          <Link to="/resources">Resources</Link>
          {BOOKERS.includes(user.role) && <Link to="/book">Book</Link>}
          {BOOKERS.includes(user.role) && <Link to="/bookings">My bookings</Link>}
          {MANAGERS.includes(user.role) && <Link to="/approvals">Approvals</Link>}
        </nav>
      )}
      {user && (
        <div className="nav-right">
          <span className="muted">{user.name}</span>
          <button className="link" onClick={() => { logout(); navigate('/login') }}>Log out</button>
        </div>
      )}
    </header>
  )
}

export default function App() {
  return (
    <>
      <Nav />
      <main>
        <Routes>
          <Route path="/login" element={<Login />} />
          <Route path="/register" element={<Register />} />
          <Route element={<ProtectedRoute />}>
            <Route path="/" element={<Home />} />
          </Route>
          <Route path="/resources" element={<ProtectedRoute />}>
            <Route index element={<Resources />} />
          </Route>
          <Route element={<ProtectedRoute allow={BOOKERS} />}>
            <Route path="/book" element={<BookResource />} />
            <Route path="/bookings" element={<MyBookings />} />
          </Route>
          <Route element={<ProtectedRoute allow={MANAGERS} />}>
            <Route path="/approvals" element={<ApprovalQueue />} />
          </Route>
          <Route element={<ProtectedRoute allow={[ROLES.SUPER_ADMIN]} />}>
            <Route path="/admin/users" element={<AdminUsers />} />
          </Route>
          <Route path="*" element={<Navigate to="/" replace />} />
        </Routes>
      </main>
    </>
  )
}
