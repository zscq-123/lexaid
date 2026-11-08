// Only send links to the publication sites represented in the paper catalogue
// to the system browser. Other popup requests stay blocked.
const paperHosts = new Set([
  'doi.org', 'clj.ac', 'www.clj.ac', 'huaxia.ac', 'www.huaxia.ac',
  'www.hanspub.org', 'www.mdpi.com', 'openalex.org', 'doaj.org', 'osf.io',
  'utrechtjournal.org', 'tilburglawreview.com'
])

function isPaperLink(raw) {
  try {
    const url = new URL(raw)
    return url.protocol === 'https:' && !url.username && !url.password && !url.port &&
      paperHosts.has(url.hostname)
  } catch {
    return false
  }
}

module.exports = { isPaperLink }
