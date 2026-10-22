import ClockHands from './ClockHands'
import { BrandMark } from './Icon'

const C = 280
const pt = (r, h) => {
  const a = ((h % 12) / 12) * Math.PI * 2 - Math.PI / 2
  return [C + r * Math.cos(a), C + r * Math.sin(a)]
}
const arc = (r, h1, h2) => {
  const [x1, y1] = pt(r, h1)
  const [x2, y2] = pt(r, h2)
  return `M${x1.toFixed(1)} ${y1.toFixed(1)} A${r} ${r} 0 ${h2 - h1 > 6 ? 1 : 0} 1 ${x2.toFixed(1)} ${y2.toFixed(1)}`
}

/** Sign-in screens: a small live clock above the headline on the left, the form on the right. */
export default function AuthLayout({ children }) {
  const rings = [220, 186, 152, 118]
  return (
    <div className="auth">
      <section className="auth-board" aria-label="About Campus Board">
        <div className="brand"><BrandMark /><span>Campus Board</span></div>
        <div className="auth-hero">
          <svg className="auth-dial" viewBox="0 0 560 560" aria-hidden="true">
            {rings.map((r) => <path key={r} className="t" d={arc(r, 8.1, 19.9)} strokeWidth="18" strokeLinecap="round" />)}
            <path className="a" d={arc(220, 10, 12)} stroke="#e3d3b0" strokeWidth="18" />
            <path className="a" d={arc(186, 14.5, 17)} stroke="#a9c2a6" strokeWidth="18" />
            <path className="a" d={arc(152, 9, 10.5)} stroke="#e3d3b0" strokeWidth="18" />
            <path className="a" d={arc(118, 16, 18.5)} stroke="#e3d3b0" strokeWidth="18" />
            <ClockHands cx={C} cy={C} hour={96} minute={196} second={226} tail={26} />
          </svg>
          <h1>Every room and every event, around the clock.</h1>
          <p>Book rooms and labs without clashes, take club events through approval, register for what is on, and check in with a code that changes every 30 seconds.</p>
        </div>
        <p style={{ color: '#7f858c', fontSize: '0.9rem', margin: 0 }}>Each ring is a room. Each arc is a booking.</p>
      </section>
      <section className="auth-form">{children}</section>
    </div>
  )
}
