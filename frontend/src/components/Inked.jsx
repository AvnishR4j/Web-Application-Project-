/** Headlines rise into place word by word. Whole words keep the font's own spacing (kerning) intact. */
export default function Inked({ children, delay = 0.15 }) {
  let n = 0
  const word = (w) => (
    <span key={`${w}-${n}`} className="ink-word">
      <span className="ink-in" style={{ animationDelay: `${delay + n++ * 0.09}s` }}>{w}</span>
    </span>
  )
  return children.flatMap((part, i) => {
    const italic = typeof part !== 'string'
    const text = italic ? part.props.children : part
    const pieces = text.split(/(\s+)/).filter(Boolean).map((w) => (/^\s+$/.test(w) ? ' ' : word(w)))
    return italic ? [<em key={`em-${i}`}>{pieces}</em>] : pieces
  })
}
