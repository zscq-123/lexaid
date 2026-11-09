import { getActiveMock } from '../../api/mock-exams'

export const roomPath = id => '/mock-exams/run/' + id
export const resultPath = id => '/mock-exams/result/' + id
const marker = id => 'lexaid_mock_active_' + id
let verifiedToken = ''
let tryingToken = ''
let pending
let current = null

export function rememberExam(user, id) {
  verifiedToken = user.token
  current = id || null
  if (user.user?.id != null) {
    if (current) localStorage.setItem(marker(user.user.id), String(current))
    else localStorage.removeItem(marker(user.user.id))
  }
}

// The server owns the lock. The account-scoped marker also restores the room while offline.
export async function activeExamFor(user) {
  const token = user.token
  if (!token) return null
  const stored = user.user?.id != null && localStorage.getItem(marker(user.user.id))
  if (verifiedToken === token) return stored || current
  if (tryingToken === token && pending) return pending
  tryingToken = token
  pending = getActiveMock().then(session => {
    if (user.token !== token) return null
    rememberExam(user, session?.status === 'ACTIVE' ? session.id : null)
    return current
  }).catch(() => user.token === token && stored || null)
  const mine = pending
  try { return await mine } finally { if (pending === mine) pending = null }
}
