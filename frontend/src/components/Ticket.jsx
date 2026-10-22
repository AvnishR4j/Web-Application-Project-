import Icon from './Icon'

const timeOf = (iso) => new Date(iso).toLocaleTimeString([], { hour: 'numeric', minute: '2-digit' })

/** An event as an admit ticket: date block, details, and a perforated stub for the viewer's place and action. */
export default function Ticket({ start, end, title, club, venue, description, seats, stub }) {
  const s = new Date(start)
  return (
    <article className="ticket">
      <div className="ticket-date" aria-label={s.toLocaleDateString([], { dateStyle: 'full' })}>
        <span className="mon">{s.toLocaleDateString([], { month: 'short' })}</span>
        <span className="day">{s.getDate()}</span>
        <span className="dow">{s.toLocaleDateString([], { weekday: 'short' })}</span>
      </div>
      <div className="ticket-body">
        <h3>{title}</h3>
        <div className="ticket-meta">
          <span><Icon name="clock" />{timeOf(start)} to {timeOf(end)}</span>
          {venue && <span><Icon name="pin" />{venue}</span>}
          {club && <span><Icon name="club" />{club}</span>}
        </div>
        {description && <p>{description}</p>}
        {seats && (
          <div className="ticket-seats">
            <span className={`seatbar${seats.left === 0 ? ' full' : ''}`} aria-hidden="true">
              <i style={{ width: `${Math.min(100, (seats.confirmed / Math.max(1, seats.capacity)) * 100)}%` }} />
            </span>
            <span>
              {seats.left > 0 ? `${seats.left} of ${seats.capacity} seats left` : `Full, ${seats.capacity} seats`}
              {seats.waitlisted > 0 && `, ${seats.waitlisted} waiting`}
            </span>
          </div>
        )}
      </div>
      <div className="ticket-stub">{stub}</div>
    </article>
  )
}

/** Groups items by calendar day, in order. */
export function byDay(items, getDate) {
  const groups = []
  for (const item of items) {
    const d = new Date(getDate(item))
    const key = d.toDateString()
    let g = groups.find((x) => x.key === key)
    if (!g) { g = { key, date: d, items: [] }; groups.push(g) }
    g.items.push(item)
  }
  return groups
}

export function dayLabel(d) {
  const today = new Date(); today.setHours(0, 0, 0, 0)
  const that = new Date(d); that.setHours(0, 0, 0, 0)
  const diff = Math.round((that - today) / 86400000)
  if (diff === 0) return 'Today'
  if (diff === 1) return 'Tomorrow'
  return d.toLocaleDateString([], { weekday: 'long', day: 'numeric', month: 'long' })
}
