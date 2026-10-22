import { Link, Navigate, Route, Routes, useNavigate } from 'react-router-dom'
import { ROLES, useAuth } from './auth/AuthContext'
import ProtectedRoute from './auth/ProtectedRoute'
import AdminUsers from './pages/AdminUsers'
import ApprovalQueue from './pages/ApprovalQueue'
import BookResource from './pages/BookResource'
import Clubs from './pages/Clubs'
import Events from './pages/Events'
import Home from './pages/Home'
import Login from './pages/Login'
import MyBookings from './pages/MyBookings'
import MyEvents from './pages/MyEvents'
import Register from './pages/Register'
import Resources from './pages/Resources'
import ReviewQueue from './pages/ReviewQueue'

const MANAGERS = [ROLES.RESOURCE_ADMIN, ROLES.SUPER_ADMIN]
const BOOKERS = [ROLES.CLUB_ORGANIZER, ROLES.RESOURCE_ADMIN, ROLES.SUPER_ADMIN]
const ORGANIZERS = [ROLES.CLUB_ORGANIZER, ROLES.SUPER_ADMIN]
const ADVISORS = [ROLES.FACULTY_ADVISOR, ROLES.SUPER_ADMIN]

function Nav() {
  const { user, logout } = useAuth()
  const navigate = useNavigate()
  return (
    <header className="nav">
      <Link to="/" className="brand">Campus Events and Booking</Link>
      {user && (
        <nav className="nav-links">
          <Link to="/events">Events</Link>
          {ORGANIZERS.includes(user.role) && <Link to="/my-events">My events</Link>}
          {ADVISORS.includes(user.role) && <Link to="/advisor">Advisor review</Link>}
          {MANAGERS.includes(user.role) && <Link to="/event-approvals">Event approvals</Link>}
          <Link to="/resources">Resources</Link>
          {BOOKERS.includes(user.role) && <Link to="/book">Book</Link>}
          {BOOKERS.includes(user.role) && <Link to="/bookings">My bookings</Link>}
          {MANAGERS.includes(user.role) && <Link to="/approvals">Booking approvals</Link>}
          {user.role === ROLES.SUPER_ADMIN && <Link to="/clubs">Clubs</Link>}
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
          <Route element={<ProtectedRoute />}>
            <Route path="/resources" element={<Resources />} />
            <Route path="/events" element={<Events />} />
          </Route>
          <Route element={<ProtectedRoute allow={ORGANIZERS} />}>
            <Route path="/my-events" element={<MyEvents />} />
          </Route>
          <Route element={<ProtectedRoute allow={ADVISORS} />}>
            <Route path="/advisor" element={<ReviewQueue key="advisor" title="Advisor review"
              intro="Events submitted by the clubs you advise. Approving sends them to the space manager."
              queuePath="/api/events/advisor-queue" approveAction="advisor-approve" rejectAction="advisor-reject"
              empty="Nothing waiting for your review." />} />
          </Route>
          <Route element={<ProtectedRoute allow={BOOKERS} />}>
            <Route path="/book" element={<BookResource />} />
            <Route path="/bookings" element={<MyBookings />} />
          </Route>
          <Route element={<ProtectedRoute allow={MANAGERS} />}>
            <Route path="/approvals" element={<ApprovalQueue />} />
            <Route path="/event-approvals" element={<ReviewQueue key="manager" title="Event approvals"
              intro="Events already approved by their club's advisor. Approving confirms the room and publishes the event."
              queuePath="/api/events/manager-queue" approveAction="manager-approve" rejectAction="manager-reject"
              empty="No events waiting for final approval." />} />
          </Route>
          <Route element={<ProtectedRoute allow={[ROLES.SUPER_ADMIN]} />}>
            <Route path="/admin/users" element={<AdminUsers />} />
            <Route path="/clubs" element={<Clubs />} />
          </Route>
          <Route path="*" element={<Navigate to="/" replace />} />
        </Routes>
      </main>
    </>
  )
}
