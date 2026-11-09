export const kindLabels = { SINGLE: '单项选择题', MULTIPLE: '多项选择题', INDEFINITE: '不定项选择题', SHORT: '主观题' }
export const sourceLabels = { REAL: '历年真题', MOCK: '模拟题' }
export const paperLabels = { OBJECTIVE: '客观题卷', SUBJECTIVE: '主观题卷' }

export function clockLabel(milliseconds) {
  const seconds = Math.ceil(Math.max(0, milliseconds) / 1000)
  return [Math.floor(seconds / 3600), Math.floor(seconds / 60) % 60, seconds % 60]
    .map(n => String(n).padStart(2, '0')).join(':')
}

export function selectedKeys(value) {
  if (!value) return []
  try {
    const parsed = JSON.parse(value)
    if (Array.isArray(parsed)) return parsed
  } catch { /* Legacy answer keys can be plain letters. */ }
  return String(value).match(/[A-Z]/g) || []
}
export function answered(kind, value) {
  return kind === 'SHORT' ? Boolean(value?.trim()) : selectedKeys(value).length > 0
}

export function sourceLink(raw) {
  try {
    const url = new URL(raw)
    return url.protocol === 'https:' && !url.username && !url.password ? url.href : ''
  } catch { return '' }
}
