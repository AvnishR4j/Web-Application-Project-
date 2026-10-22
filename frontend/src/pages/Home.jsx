import { Link } from 'react-router-dom'
import { ROLES, useAuth } from '../auth/AuthContext'

// What each role can do today, with links to the pages that do it.
const ROLE_INFO = {
  [ROLES.STUDENT]: {
    title: 'Student',
    links: [['/events', 'Browse and register for upcoming events'], ['/registrations', 'My registrations and waitlist places'],
      ['/resources', 'See rooms, labs and equipment']],
    later: [],
  },
  [ROLES.CLUB_ORGANIZER]: {
    title: 'Club organizer',
    links: [['/my-events', 'Create and track event requests'], ['/book', 'Book a room, lab or equipment'],
      ['/bookings', 'My booking requests']],
    later: [],
  },
  [ROLES.FACULTY_ADVISOR]: {
    title: 'Faculty advisor',
    links: [['/advisor', 'Review event requests from your clubs'], ['/events', 'Upcoming events']],
    later: [],
  },
  [ROLES.RESOURCE_ADMIN]: {
    title: 'Resource admin',
    links: [['/event-approvals', 'Give final approval to events'], ['/approvals', 'Review booking requests'],
      ['/resources', 'Manage rooms, labs and equipment']],
    later: ['Utilization reports'],
  },
  [ROLES.SUPER_ADMIN]: {
    title: 'Super admin',
    links: [['/admin/users', 'Manage users and roles'], ['/clubs', 'Manage clubs'],
      ['/event-approvals', 'Event approvals']],
    later: ['Audit log and reports'],
  },
}

export default function Home() {
  const { user } = useAuth()
  const info = ROLE_INFO[user.role] || { title: user.role, links: [], later: [] }
  return (
    <div className="card">
      <h1>Welcome, {user.name}</h1>
      <p className="muted">
        Signed in as <strong>{info.title}</strong> ({user.email})
      </p>
      <ul>
        {info.links.map(([to, label]) => (
          <li key={to}><Link to={to}>{label}</Link></li>
        ))}
      </ul>
      {info.later.length > 0 && (
        <>
          <h2>Coming in later phases</h2>
          <ul className="muted">{info.later.map((item) => <li key={item}>{item}</li>)}</ul>
        </>
      )}
    </div>
  )
}
