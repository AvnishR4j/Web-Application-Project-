import { useEffect } from 'react'
import { startSunlight } from './sunlight'

/** The desk under every page: paper grain, window light from the real sun, and a lamp glow after dark. */
export default function Backdrop() {
  useEffect(() => { startSunlight() }, [])
  return (
    <div className="backdrop" aria-hidden="true">
      <div className="gobo" id="gobo"><i /><i /><i /><i /></div>
      <div className="lamp" />
    </div>
  )
}
