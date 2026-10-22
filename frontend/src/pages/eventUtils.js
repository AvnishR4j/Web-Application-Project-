export const fmt = (iso) => new Date(iso).toLocaleString([], { dateStyle: 'medium', timeStyle: 'short' })

export const STATUS_LABEL = {
  DRAFT: 'Draft',
  SUBMITTED: 'Waiting for advisor',
  ADVISOR_APPROVED: 'Waiting for space manager',
  APPROVED: 'Approved',
  REJECTED: 'Rejected',
  CANCELLED: 'Cancelled',
  COMPLETED: 'Completed',
}

// Turns an API error into a readable line, including each broken booking rule from a 409.
export function describeError(err) {
  const rules = Object.values(err.fields || {})
  return rules.length ? `${err.message}: ${rules.join(' ')}` : err.message
}

export const byId = (list) => Object.fromEntries(list.map((x) => [x.id, x]))
