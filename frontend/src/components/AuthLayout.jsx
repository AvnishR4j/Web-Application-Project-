import { Link } from 'react-router-dom'
import Inked from './Inked'
import { BrandMark } from './Icon'
import { hhmm, sunTimes } from './sunlight'

/** Sign-in screens: the programme's cover on the left, the form on its own sheet on the right. */
export default function AuthLayout({ children }) {
  const { rise, set } = sunTimes()
  return (
    <div className="auth">
      <section className="auth-board" aria-label="About Campus Board">
        <Link to="/login" className="brand"><BrandMark /></Link>
        <div className="auth-hero">
          <h1><Inked>{['Every room, every event, ', <em key="e">one desk.</em>]}</Inked></h1>
          <p>Book rooms and labs without clashes, take club events through approval, and keep your seat at what’s on, all at Thapar.</p>
        </div>
        <div className="auth-foot">
          <span>Patiala · sunrise {hhmm(rise)} · sunset {hhmm(set)}</span>
          <Link to="/privacy">Privacy</Link>
        </div>
      </section>
      <section className="auth-form">{children}</section>
    </div>
  )
}
