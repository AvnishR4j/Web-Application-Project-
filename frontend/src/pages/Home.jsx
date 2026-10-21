import { Link } from 'react-router-dom'
import { ROLES, useAuth } from '../auth/AuthContext'

// What each role will be able to do, mapped to the workflow plan. Entries become
// real pages in later phases; they are listed here so the role split is visible now.
const ROLE_INFO = {
  [ROLES.STUDENT]: {
    title: 'Student',
    items: ['Browse approved events', 'Register, cancel, join a waitlist', 'Check in by QR code', 'Give feedback after an event'],
  },
  [ROLES.CLUB_ORGANIZER]: {
    title: 'Club organizer',
    items: ['Create event requests with venue and equipment', 'Track approval status and revise requests', 'See registrants and attendance'],
  },
  [ROLES.RESOURCE_ADMIN]: {
    title: 'Resource admin',
    items: ['Manage rooms, labs and equipment', 'Approve or reject booking requests', 'View utilization reports'],
  },
  [ROLES.SUPER_ADMIN]: {
    title: 'Super admin',
    items: ['Manage users and roles', 'Set rules such as buffers and lead times', 'View the audit log'],
  },
}

export default function Home() {
  const { user } = useAuth()
  const info = ROLE_INFO[user.role]
  return (
    <div className="card">
      <h1>Welcome, {user.name}</h1>
      <p className="muted">
        Signed in as <strong>{info.title}</strong> ({user.email})
      </p>
      <h2>Your workspace (arrives in later phases)</h2>
      <ul>
        {info.items.map((item) => (
          <li key={item}>{item}</li>
        ))}
      </ul>
      {user.role === ROLES.SUPER_ADMIN && (
        <p>
          <Link to="/admin/users">Manage users and roles</Link>
        </p>
      )}
    </div>
  )
}
