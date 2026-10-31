// A small hand-drawn icon set (24px grid, 1.8 stroke), so the app needs no icon library.
const PATHS = {
  home: 'M3 11.5 12 4l9 7.5M5.5 9.5V20h5v-5.5h3V20h5V9.5',
  calendar: 'M4 6.5h16v13H4zM4 10.5h16M8.5 4v4M15.5 4v4',
  ticket: 'M4 7h16v3.2a2 2 0 0 0 0 3.6V17H4v-3.2a2 2 0 0 0 0-3.6zM14.5 7v10',
  clipboard: 'M8.5 5H6v15h12V5h-2.5M8.5 3.5h7v3h-7zM9 11h6M9 15h4',
  check: 'M4.5 12.5 9.5 17 19.5 7',
  door: 'M6 20V4.5h12V20M4 20h16M14 12.5v.5',
  plus: 'M12 5v14M5 12h14',
  list: 'M9 6.5h11M9 12h11M9 17.5h11M4.5 6.5h.5M4.5 12h.5M4.5 17.5h.5',
  stamp: 'M9 13V9.5a3 3 0 1 1 6 0V13M5 13h14v3H5zM6.5 19.5h11',
  chart: 'M4 20h16M7 20v-6M12 20V8M17 20v-9',
  users: 'M9 11a3.2 3.2 0 1 0 0-6.4A3.2 3.2 0 0 0 9 11zM3.5 19.5c.6-3 2.8-4.8 5.5-4.8s4.9 1.8 5.5 4.8M16 10.5a2.6 2.6 0 1 0 0-5.2M17.5 14.5c1.6.5 2.7 2 3 5',
  club: 'M12 3.5 20 8l-8 4.5L4 8zM4 12l8 4.5 8-4.5M4 16l8 4.5 8-4.5',
  history: 'M4.5 12a7.5 7.5 0 1 0 2.2-5.3L4.5 9M4.5 4.5V9H9M12 8v4.5l3 2',
  sun: 'M12 16.5a4.5 4.5 0 1 0 0-9 4.5 4.5 0 0 0 0 9zM12 2.5v2M12 19.5v2M2.5 12h2M19.5 12h2M5.3 5.3l1.4 1.4M17.3 17.3l1.4 1.4M5.3 18.7l1.4-1.4M17.3 6.7l1.4-1.4',
  moon: 'M19.5 14.5A8 8 0 0 1 9.5 4.5a8 8 0 1 0 10 10z',
  logout: 'M14.5 4.5H19v15h-4.5M10 8l-4 4 4 4M6 12h9.5',
  menu: 'M4 8h16M4 16h16',
  close: 'M6 6l12 12M18 6 6 18',
  pin: 'M12 21s-6.5-5.6-6.5-11a6.5 6.5 0 0 1 13 0C18.5 15.4 12 21 12 21zM12 12.5a2.5 2.5 0 1 0 0-5 2.5 2.5 0 0 0 0 5z',
  clock: 'M12 20.5a8.5 8.5 0 1 0 0-17 8.5 8.5 0 0 0 0 17zM12 7.5V12l3 2',
  qr: 'M4 4h6v6H4zM14 4h6v6h-6zM4 14h6v6H4zM14 14h2v2h-2zM18 14h2M14 18h2M18 18h2v2',
  seat: 'M7 11V6.5A2.5 2.5 0 0 1 9.5 4h5A2.5 2.5 0 0 1 17 6.5V11M5 11h14v4H5zM7 15v5M17 15v5',
  chevron: 'M7 10l5 5 5-5',
  play: 'M8 5.5v13l10.5-6.5z',
}

export default function Icon({ name, title, ...rest }) {
  return (
    <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="1.8" strokeLinecap="round"
      strokeLinejoin="round" aria-hidden={title ? undefined : true} role={title ? 'img' : undefined} {...rest}>
      {title && <title>{title}</title>}
      <path d={PATHS[name]} />
    </svg>
  )
}

/** The Campus Board wordmark, set in the serif like a printed programme. */
export function BrandMark() {
  return <span className="wordmark">Campus Board</span>
}
