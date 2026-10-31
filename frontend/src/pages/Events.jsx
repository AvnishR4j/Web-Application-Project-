import { useState } from 'react'
import { byDay, dayLabel } from '../components/Ticket'
import useProgramme from '../components/useProgramme'

/** Every published event, day by day, printed as tickets. */
export default function Events() {
  const { events, clubs, rooms, loaded, error, ticketFor } = useProgramme()
  const [query, setQuery] = useState('')

  const q = query.trim().toLowerCase()
  const shown = events.filter((e) => !q || [e.title, e.description, clubs[e.clubId]?.name, rooms[e.resourceId]?.name]
    .some((v) => v && v.toLowerCase().includes(q)))
  let n = 0

  return (
    <div className="card">
      <div className="page-head">
        <div>
          <p className="eyebrow">The programme</p>
          <h1>Every event, <em>day by day.</em></h1>
          <p>Approved events from every club. Taking a seat keeps your place; when an event is full you join its waitlist and move up automatically.</p>
        </div>
        <label className="search">
          <span className="sr-only">Search events</span>
          <input type="search" placeholder="Search title, club or room" value={query} onChange={(e) => setQuery(e.target.value)} />
        </label>
      </div>
      {error && <p className="notice bad" role="alert" style={{ marginBottom: 20 }}>{error}</p>}
      {!loaded ? (
        <div className="ticket-grid">{[0, 1, 2].map((i) => <div key={i} className="tk-ghost" />)}</div>
      ) : shown.length === 0 ? (
        <div className="empty"><p>{q ? 'Nothing matches that search.' : 'The programme is empty for now.'}</p><span>{q ? 'Try a club or room name.' : 'Approved club events appear here.'}</span></div>
      ) : byDay(shown, (e) => e.start).map((g) => (
        <section key={g.key}>
          <h2 className="date-group">{dayLabel(g.date)}</h2>
          <div className="ticket-grid">{g.items.map((e) => ticketFor(e, n++, { describe: true }))}</div>
        </section>
      ))}
    </div>
  )
}
