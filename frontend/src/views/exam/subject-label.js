export function subjectLabel(row) {
  if (!row) return ''
  const labels = [row.discipline, row.subject, row.codeName, row.bookName].filter(Boolean)
  return [...new Set(labels)].join(' / ') || row.category || ''
}
