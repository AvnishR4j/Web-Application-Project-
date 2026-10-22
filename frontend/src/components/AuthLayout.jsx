import { BrandMark } from './Icon'

/** Sign-in screens: a chalkboard on the left showing what the app does, the form on the right. */
export default function AuthLayout({ children }) {
  return (
    <div className="auth">
      <section className="auth-board" aria-label="About Campus Board">
        <div className="brand"><BrandMark />Campus Board</div>
        <div>
          <h1>Every room, every club, one board.</h1>
          <p>Book rooms and labs without clashes, take club events through approval, register for what is on, and check in with a code that changes every 30 seconds.</p>
        </div>
        <div className="chalk-schedule" aria-hidden="true">
          <div className="chalk-line"><span>9 am</span><b className="c-yellow" /></div>
          <div className="chalk-line"><span>11 am</span><b className="c-white" /></div>
          <div className="chalk-line"><span>2 pm</span><b className="c-blue" /></div>
          <div className="chalk-line"><span>5 pm</span><b className="c-green" /></div>
        </div>
      </section>
      <section className="auth-form">{children}</section>
    </div>
  )
}
