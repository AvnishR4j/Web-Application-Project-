/** Letters ink onto the page one after another. Words never break in the middle. */
export default function Inked({ children, delay = 0.15 }) {
  let n = 0
  const word = (w) => (
    <span key={`${w}-${n}`} style={{ display: 'inline-block', whiteSpace: 'nowrap' }}>
      {[...w].map((ch) => <span key={n} className="ink-in" style={{ animationDelay: `${delay + n++ * 0.035}s` }}>{ch}</span>)}
    </span>
  )
  return children.flatMap((part, i) => {
    const italic = typeof part !== 'string'
    const text = italic ? part.props.children : part
    const pieces = text.split(/(\s+)/).filter(Boolean).map((w) => (/^\s+$/.test(w) ? ' ' : word(w)))
    return italic ? [<em key={`em-${i}`}>{pieces}</em>] : pieces
  })
}

