import { Link, Navigate, Route, Routes, useNavigate } from 'react-router-dom'
import { ROLES, useAuth } from './auth/AuthContext'
import ProtectedRoute from './auth/ProtectedRoute'
import AdminUsers from './pages/AdminUsers'
import Home from './pages/Home'
import Login from './pages/Login'
import Register from './pages/Register'

function Nav() {
  const { user, logout } = useAuth()
  const navigate = useNavigate()
  return (
    <header className="nav">
      <Link to="/" className="brand">Campus Events and Booking</Link>
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
          <Route element={<ProtectedRoute allow={[ROLES.SUPER_ADMIN]} />}>
            <Route path="/admin/users" element={<AdminUsers />} />
          </Route>
          <Route path="*" element={<Navigate to="/" replace />} />
        </Routes>
      </main>
    </>
  )
}
