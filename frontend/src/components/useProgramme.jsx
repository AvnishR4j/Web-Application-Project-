import { useCallback, useEffect, useState } from 'react'
import { Link } from 'react-router-dom'
import { api, peek } from '../api/client'
import { byId, describeError } from '../pages/eventUtils'
import Ticket, { dayLabel, seatStamp, serial } from './Ticket'

/**
 * Published events with seats, clubs and rooms, plus a renderer that prints each as a ticket.
 * Taking a seat registers (or joins the waitlist), tears the stub and stamps the ticket.
 */
export default function useProgramme() {
  const [state, setState] = useState(() => {
    // Draw the last-seen programme at once; the fresh one replaces it a moment later.
    const [events, seats, clubs, rooms] = ['/api/events', '/api/events/seats', '/api/clubs', '/api/resources'].map(peek)
    return events && seats
      ? { events: [...events].sort((a, b) => new Date(a.start) - new Date(b.start)), seats: Object.fromEntries(seats.map((x) => [x.eventId, x])), clubs: byId(clubs || []), rooms: byId(rooms || []), loaded: true }
      : { events: [], seats: {}, clubs: {}, rooms: {}, loaded: false }
  })
  const [error, setError] = useState('')

  const load = useCallback((fresh = false) => {
    const opt = fresh ? { fresh: true } : undefined
    return Promise.all([api('/api/events', opt), api('/api/events/seats', opt), api('/api/clubs').catch(() => []), api('/api/resources').catch(() => [])])
      .then(([events, seats, clubs, rooms]) => setState({
        events: [...events].sort((a, b) => new Date(a.start) - new Date(b.start)),
        seats: Object.fromEntries(seats.map((x) => [x.eventId, x])),
        clubs: byId(clubs), rooms: byId(rooms), loaded: true,
      }))
      .catch((err) => { setError(err.message); setState((s) => ({ ...s, loaded: true })) })
  }, [])
  useEffect(() => { load() }, [load])

  async function act(id, action) {
    setError('')
    try {
      await api(`/api/events/${id}/${action}`, { method: 'POST' })
    } catch (err) {
      setError(describeError(err))
      throw err
    }
  }

  function ticketFor(e, i, { describe = false, dated = false } = {}) {
    const s = state.seats[e.id]
    const now = Date.now()
    const started = new Date(e.start).getTime() <= now
    const ended = new Date(e.end).getTime() <= now
    const left = s ? Math.max(0, s.capacity - s.confirmed) : null
    const stamp = s?.myStatus ? seatStamp(s.myStatus, s.myWaitlistPosition, e.start) : null
    const take = !s || s.myStatus || started ? null : {
      label: left > 0 ? 'Take a seat' : 'Join waitlist',
      onTake: async () => {
        await act(e.id, 'register')
        const fresh = await api('/api/events/seats', { fresh: true })
        const mine = fresh.find((x) => x.eventId === e.id)
        return seatStamp(mine?.myStatus || 'CONFIRMED', mine?.myWaitlistPosition, e.start)
      },
    }
    const actions = s?.myStatus && !started ? (<>
      <Link to="/registrations">My tickets</Link>
      <button type="button" className="link" onClick={() => act(e.id, 'unregister').then(() => load(true)).catch(() => {})}>
        {s.myStatus === 'WAITLISTED' ? 'Leave the waitlist' : 'Give up my seat'}
      </button>
      {s.myStatus === 'WAITLISTED' && <span className="muted">You move up automatically if someone cancels.</span>}
    </>) : null
    return (
      <Ticket key={e.id} index={i} start={e.start} end={e.end} title={e.title}
        club={state.clubs[e.clubId]?.name} venue={state.rooms[e.resourceId]?.name} number={serial(e.id)}
        description={describe ? e.description : null} day={dated ? dayLabel(new Date(e.start)) : null}
        live={started && !ended} ended={ended}
        seats={s && { left, capacity: s.capacity }}
        take={take} stubNote={ended ? 'Ended' : started ? 'Under way' : ''}
        stamp={stamp} actions={actions} onSettled={() => load(true)} />
    )
  }

  return { ...state, error, reload: load, ticketFor }
}
