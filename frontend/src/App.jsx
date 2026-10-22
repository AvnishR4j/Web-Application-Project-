import { useEffect, useState } from 'react'
import { Link, NavLink, Navigate, Route, Routes, useLocation, useNavigate } from 'react-router-dom'
import { api } from './api/client'
import { ROLES, useAuth } from './auth/AuthContext'
import ProtectedRoute from './auth/ProtectedRoute'
import Icon, { BrandMark } from './components/Icon'
import { useTheme } from './components/theme'
import AdminUsers from './pages/AdminUsers'
import AuditLog from './pages/AuditLog'
import ApprovalQueue from './pages/ApprovalQueue'
import BookResource from './pages/BookResource'
import CheckIn from './pages/CheckIn'
import CheckinScreen from './pages/CheckinScreen'
import Clubs from './pages/Clubs'
import EventReport from './pages/EventReport'
import Events from './pages/Events'
import Home from './pages/Home'
import Login from './pages/Login'
import MyBookings from './pages/MyBookings'
import MyEvents from './pages/MyEvents'
import MyRegistrations from './pages/MyRegistrations'
import Register from './pages/Register'
import Reports from './pages/Reports'
import Resources from './pages/Resources'
import ReviewQueue from './pages/ReviewQueue'

const MANAGERS = [ROLES.RESOURCE_ADMIN, ROLES.SUPER_ADMIN]
const BOOKERS = [ROLES.CLUB_ORGANIZER, ROLES.RESOURCE_ADMIN, ROLES.SUPER_ADMIN]
const ORGANIZERS = [ROLES.CLUB_ORGANIZER, ROLES.SUPER_ADMIN]
const ADVISORS = [ROLES.FACULTY_ADVISOR, ROLES.SUPER_ADMIN]
const INSIDERS = [ROLES.CLUB_ORGANIZER, ROLES.FACULTY_ADVISOR, ROLES.RESOURCE_ADMIN, ROLES.SUPER_ADMIN]

const ROLE_NAME = {
  STUDENT: 'Student',
  CLUB_ORGANIZER: 'Club organizer',
  FACULTY_ADVISOR: 'Faculty advisor',
  RESOURCE_ADMIN: 'Resource admin',
  SUPER_ADMIN: 'Super admin',
}

/** Links grouped by what each role does. Review links carry a live count of what is waiting. */
function navFor(role, counts) {
  const has = (list) => list.includes(role)
  const groups = [
    { name: 'Campus', links: [
      { to: '/', icon: 'home', label: 'Today', end: true },
      { to: '/events', icon: 'calendar', label: 'Events' },
      { to: '/registrations', icon: 'ticket', label: 'My tickets' },
      { to: '/resources', icon: 'door', label: 'Rooms and equipment' },
    ] },
    { name: 'Organize', links: [
      has(ORGANIZERS) && { to: '/my-events', icon: 'clipboard', label: 'My events' },
      has(BOOKERS) && { to: '/book', icon: 'plus', label: 'Book a room' },
      has(BOOKERS) && { to: '/bookings', icon: 'list', label: 'My bookings' },
    ] },
    { name: 'Review', links: [
      has(ADVISORS) && { to: '/advisor', icon: 'check', label: 'Advisor review', count: counts.advisor },
      has(MANAGERS) && { to: '/event-approvals', icon: 'stamp', label: 'Event approvals', count: counts.events },
      has(MANAGERS) && { to: '/approvals', icon: 'stamp', label: 'Booking approvals', count: counts.bookings },
    ] },
    { name: 'Manage', links: [
      has(MANAGERS) && { to: '/reports', icon: 'chart', label: 'Reports' },
      role === ROLES.SUPER_ADMIN && { to: '/clubs', icon: 'club', label: 'Clubs' },
      role === ROLES.SUPER_ADMIN && { to: '/admin/users', icon: 'users', label: 'People and roles' },
      role === ROLES.SUPER_ADMIN && { to: '/admin/audit', icon: 'history', label: 'Audit log' },
    ] },
  ]
  return groups.map((g) => ({ ...g, links: g.links.filter(Boolean) })).filter((g) => g.links.length)
}

function useReviewCounts(role) {
  const location = useLocation()
  const [counts, setCounts] = useState({})
  useEffect(() => {
    if (!role) return
    const jobs = []
    if (ADVISORS.includes(role)) jobs.push(api('/api/events/advisor-queue').then((l) => ['advisor', l.length]))
    if (MANAGERS.includes(role)) {
      jobs.push(api('/api/events/manager-queue').then((l) => ['events', l.length]))
      jobs.push(api('/api/bookings?status=PENDING').then((l) => ['bookings', l.filter((b) => !b.eventId).length]))
    }
    Promise.all(jobs.map((j) => j.catch(() => null)))
      .then((pairs) => setCounts(Object.fromEntries(pairs.filter(Boolean))))
  }, [role, location.pathname])
  return counts
}

function Shell({ children }) {
  const { user, logout } = useAuth()
  const navigate = useNavigate()
  const location = useLocation()
  const [theme, toggleTheme] = useTheme()
  const [open, setOpen] = useState(false)
  const counts = useReviewCounts(user?.role)
  useEffect(() => setOpen(false), [location.pathname])

  if (!user) return <div className="app signed-out">{children}</div>

  const themeButton = (
    <button className="ghost icon" onClick={toggleTheme} aria-label={theme === 'dark' ? 'Switch to whiteboard (light) theme' : 'Switch to chalkboard (dark) theme'}>
      <Icon name={theme === 'dark' ? 'sun' : 'moon'} />
      <span>{theme === 'dark' ? 'Whiteboard' : 'Chalkboard'}</span>
    </button>
  )

  return (
    <div className="app">
      <header className="topbar">
        <Link to="/" className="brand"><BrandMark />Campus Board</Link>
        <button className="ghost icon" onClick={() => setOpen(true)} aria-label="Open menu" aria-expanded={open}>
          <Icon name="menu" /> Menu
        </button>
      </header>
      {open && <div className="scrim" onClick={() => setOpen(false)} />}
      <nav className={`rail${open ? ' open' : ''}`} aria-label="Main">
        <div className="row" style={{ justifyContent: 'space-between' }}>
          <Link to="/" className="brand"><BrandMark />Campus Board</Link>
          {open && <button className="ghost" onClick={() => setOpen(false)} aria-label="Close menu"><Icon name="close" width="18" /></button>}
        </div>
        {navFor(user.role, counts).map((g) => (
          <div key={g.name}>
            <div className="rail-group">{g.name}</div>
            {g.links.map((l) => (
              <NavLink key={l.to} to={l.to} end={l.end} className={({ isActive }) => `rail-link${isActive ? ' active' : ''}`}>
                <Icon name={l.icon} />
                <span>{l.label}</span>
                {l.count > 0 && <span className="rail-count" aria-label={`${l.count} waiting`}>{l.count}</span>}
              </NavLink>
            ))}
          </div>
        ))}
        <div className="rail-foot">
          <div className="who">
            <strong>{user.name}</strong>
            <span>{ROLE_NAME[user.role] || user.role}</span>
          </div>
          <div className="rail-actions">
            {themeButton}
            <button className="ghost icon" onClick={() => { logout(); navigate('/login') }}>
              <Icon name="logout" /> Log out
            </button>
          </div>
        </div>
      </nav>
      <main className="content" id="main">{children}</main>
    </div>
  )
}

export default function App() {
  return (
    <Shell>
        <Routes>
          <Route path="/login" element={<Login />} />
          <Route path="/register" element={<Register />} />
          <Route element={<ProtectedRoute />}>
            <Route path="/" element={<Home />} />
          </Route>
          <Route element={<ProtectedRoute />}>
            <Route path="/resources" element={<Resources />} />
            <Route path="/events" element={<Events />} />
            <Route path="/registrations" element={<MyRegistrations />} />
            <Route path="/checkin/:id" element={<CheckIn />} />
          </Route>
          <Route element={<ProtectedRoute allow={ORGANIZERS} />}>
            <Route path="/my-events" element={<MyEvents />} />
          </Route>
          <Route element={<ProtectedRoute allow={INSIDERS} />}>
            <Route path="/events/:id/checkin" element={<CheckinScreen />} />
            <Route path="/events/:id/report" element={<EventReport />} />
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
            <Route path="/reports" element={<Reports />} />
            <Route path="/event-approvals" element={<ReviewQueue key="manager" title="Event approvals"
              intro="Events already approved by their club's advisor. Approving confirms the room and publishes the event."
              queuePath="/api/events/manager-queue" approveAction="manager-approve" rejectAction="manager-reject"
              empty="No events waiting for final approval." />} />
          </Route>
          <Route element={<ProtectedRoute allow={[ROLES.SUPER_ADMIN]} />}>
            <Route path="/admin/users" element={<AdminUsers />} />
            <Route path="/clubs" element={<Clubs />} />
            <Route path="/admin/audit" element={<AuditLog />} />
          </Route>
          <Route path="*" element={<Navigate to="/" replace />} />
        </Routes>
    </Shell>
  )
}
