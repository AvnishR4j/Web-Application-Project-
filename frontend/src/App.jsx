import { useEffect, useRef, useState } from 'react'
import { Link, NavLink, Navigate, Route, Routes, useLocation, useNavigate } from 'react-router-dom'
import { api } from './api/client'
import { ROLES, useAuth } from './auth/AuthContext'
import ProtectedRoute from './auth/ProtectedRoute'
import Icon, { BrandMark } from './components/Icon'
import Backdrop from './components/Backdrop'
import { campusHour, hhmm, sunTimes } from './components/sunlight'
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
import ForgotPassword from './pages/ForgotPassword'
import Privacy from './pages/Privacy'
import ResetPassword from './pages/ResetPassword'
import Unsubscribe from './pages/Unsubscribe'
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
      { to: '/', icon: 'clock', label: 'Today', short: 'Today', end: true },
      { to: '/events', icon: 'calendar', label: 'Programme', short: 'Programme' },
      { to: '/registrations', icon: 'ticket', label: 'My tickets', short: 'Tickets' },
      { to: '/resources', icon: 'door', label: 'Rooms', short: 'Rooms' },
    ] },
    { name: 'Organize', links: [
      has(ORGANIZERS) && { to: '/my-events', icon: 'clipboard', label: 'My events', short: 'My events' },
      has(BOOKERS) && { to: '/book', icon: 'plus', label: 'Book a room', short: 'Book' },
      has(BOOKERS) && { to: '/bookings', icon: 'list', label: 'My bookings', short: 'Bookings' },
    ] },
    { name: 'Review', links: [
      has(ADVISORS) && { to: '/advisor', icon: 'check', label: 'Advisor review', short: 'Review', count: counts.advisor },
      has(MANAGERS) && { to: '/event-approvals', icon: 'stamp', label: 'Event approvals', short: 'Approve events', count: counts.events },
      has(MANAGERS) && { to: '/approvals', icon: 'door', label: 'Booking approvals', short: 'Approve rooms', count: counts.bookings },
    ] },
    { name: 'Manage', links: [
      has(MANAGERS) && { to: '/reports', icon: 'chart', label: 'Reports', short: 'Reports' },
      role === ROLES.SUPER_ADMIN && { to: '/clubs', icon: 'club', label: 'Clubs', short: 'Clubs' },
      role === ROLES.SUPER_ADMIN && { to: '/admin/users', icon: 'users', label: 'People and roles', short: 'People' },
      role === ROLES.SUPER_ADMIN && { to: '/admin/audit', icon: 'history', label: 'Audit log', short: 'Audit' },
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

/** A small popover that closes on outside click, Escape and navigation. */
function usePopover() {
  const [open, setOpen] = useState(false)
  const ref = useRef(null)
  const location = useLocation()
  useEffect(() => setOpen(false), [location.pathname])
  useEffect(() => {
    if (!open) return undefined
    const away = (e) => { if (ref.current && !ref.current.contains(e.target)) setOpen(false) }
    const esc = (e) => { if (e.key === 'Escape') setOpen(false) }
    document.addEventListener('pointerdown', away)
    document.addEventListener('keydown', esc)
    return () => { document.removeEventListener('pointerdown', away); document.removeEventListener('keydown', esc) }
  }, [open])
  return { open, setOpen, ref }
}

function SunTimes() {
  const { rise, set } = sunTimes()
  const h = campusHour()
  const day = h >= rise && h < set
  return <span className="suntimes">Patiala · {day ? `sunset ${hhmm(set)}` : `sunrise ${hhmm(rise)}`}</span>
}

function Shell({ children }) {
  const { user, logout } = useAuth()
  const navigate = useNavigate()
  const location = useLocation()
  const counts = useReviewCounts(user?.role)
  const desk = usePopover()
  const me = usePopover()
  const sheet = usePopover()

  if (!user) return <div className="app signed-out"><Backdrop />{children}</div>

  const groups = navFor(user.role, counts)
  const campus = groups.find((g) => g.name === 'Campus')?.links || []
  const deskGroups = groups.filter((g) => g.name !== 'Campus')
  const waiting = deskGroups.flatMap((g) => g.links).reduce((n, l) => n + (l.count || 0), 0)
  const deskActive = deskGroups.some((g) => g.links.some((l) => location.pathname.startsWith(l.to)))
  const initials = user.name.split(' ').map((w) => w[0]).slice(0, 2).join('').toUpperCase()
  const signOut = () => { logout(); navigate('/login') }

  const link = (l) => (
    <NavLink key={l.to} to={l.to} end={l.end} className={({ isActive }) => (isActive ? 'on' : undefined)}>
      {l.label}{l.count > 0 && <span className="count" aria-label={`${l.count} waiting`}>{l.count}</span>}
    </NavLink>
  )

  return (
    <div className="app">
      <Backdrop />
      <header className="masthead">
        <Link to="/" className="brand" aria-label="Campus Board, today"><BrandMark /></Link>
        <nav className="mainnav" aria-label="Main">
          {campus.map(link)}
          {deskGroups.length > 0 && (
            <div className="pop" ref={desk.ref}>
              <button type="button" className={`navbtn${deskActive ? ' on' : ''}`} aria-expanded={desk.open} onClick={() => desk.setOpen((o) => !o)}>
                Desk{waiting > 0 && <span className="count">{waiting}</span>}<Icon name="chevron" className="chev" />
              </button>
              <div className={`popsheet desk${desk.open ? ' open' : ''}`} role="menu">
                {deskGroups.map((g) => (
                  <div key={g.name} className="popgroup">
                    <span className="poplabel">{g.name}</span>
                    {g.links.map(link)}
                  </div>
                ))}
              </div>
            </div>
          )}
        </nav>
        <div className="mast-right">
          <SunTimes />
          <div className="pop" ref={me.ref}>
            <button type="button" className="avatar" aria-label="Your account" aria-expanded={me.open} onClick={() => me.setOpen((o) => !o)}>{initials}</button>
            <div className={`popsheet me${me.open ? ' open' : ''}`}>
              <strong>{user.name}</strong>
              <span className="muted">{ROLE_NAME[user.role] || user.role}</span>
              <span className="muted small">{user.email}</span>
              <button type="button" className="secondary" onClick={signOut}><Icon name="logout" />Log out</button>
            </div>
          </div>
          <div className="pop menu-pop" ref={sheet.ref}>
            <button type="button" className="menu-btn" aria-label="Menu" aria-expanded={sheet.open} onClick={() => sheet.setOpen((o) => !o)}>
              <Icon name={sheet.open ? 'close' : 'menu'} />
            </button>
            <div className={`popsheet mobile${sheet.open ? ' open' : ''}`}>
              {groups.map((g) => (
                <div key={g.name} className="popgroup">
                  <span className="poplabel">{g.name === 'Campus' ? 'Campus' : g.name}</span>
                  {g.links.map(link)}
                </div>
              ))}
              <div className="popgroup">
                <span className="poplabel">{user.name} · {ROLE_NAME[user.role]}</span>
                <button type="button" className="secondary" onClick={signOut}><Icon name="logout" />Log out</button>
              </div>
            </div>
          </div>
        </div>
      </header>
      <main className="content page" id="main" key={location.pathname}>{children}</main>
    </div>
  )
}

export default function App() {
  return (
    <Shell>
        <Routes>
          <Route path="/login" element={<Login />} />
          <Route path="/register" element={<Register />} />
          <Route path="/privacy" element={<Privacy />} />
          <Route path="/forgot-password" element={<ForgotPassword />} />
          <Route path="/reset-password" element={<ResetPassword />} />
          <Route path="/unsubscribe" element={<Unsubscribe />} />
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
