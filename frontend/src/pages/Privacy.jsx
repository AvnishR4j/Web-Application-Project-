import { Link } from 'react-router-dom'
import { BrandMark } from '../components/Icon'

/** Public privacy policy (linked from Google's sign-in screen). */
export default function Privacy() {
  return (
    <article className="legal">
      <Link to="/login" className="brand"><BrandMark /></Link>
      <h1>Privacy policy</h1>
      <p className="muted">Last updated 27 October 2026</p>

      <p>
        Campus Board is a student project for booking college rooms and running club events at Thapar Institute.
        This page explains what it stores and why.
      </p>

      <h2>What we collect</h2>
      <ul>
        <li><strong>From Google sign-in:</strong> your name and college email address. Nothing else. We do not see or store your Google password, contacts, files or any other Google data.</li>
        <li><strong>What you add while using it:</strong> your department, bookings you request, events you create or register for, check-ins, and feedback you leave.</li>
        <li><strong>An audit log</strong> of approvals and other admin actions, so decisions can be traced.</li>
      </ul>

      <h2>How it is used</h2>
      <p>
        Only to run the booking and event features: showing your registrations, telling organizers who is attending,
        and letting staff approve requests. We do not sell, share or advertise with your data, and there is no tracking or analytics.
      </p>

      <h2>Where it is kept</h2>
      <p>
        In a MongoDB Atlas database, reached through our server on Render; the website itself is served by Vercel.
        Access is limited to signed-in users according to their role.
      </p>

      <h2>Your choices</h2>
      <p>
        You can stop using the service at any time. To have your account and its data deleted, email{' '}
        <a href="mailto:avnish9569@gmail.com">avnish9569@gmail.com</a>. You can also remove Campus Board's access from your
        Google account settings under "Third-party apps".
      </p>

      <h2>Contact</h2>
      <p>Questions about this policy: <a href="mailto:avnish9569@gmail.com">avnish9569@gmail.com</a>.</p>
    </article>
  )
}
