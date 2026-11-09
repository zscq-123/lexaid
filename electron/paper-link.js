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

const examPages = new Set(['ifykymue6828659', 'ifykymue6829407', 'ifykywuc6008402', 'ifykymue6830771']
  .map(id => '/official/2017-09-18/doc-' + id + '.shtml'))
const answerPages = new Set(['ifymfcih4558711', 'ifymfcih4562301', 'ifymesii5426388', 'ifymesii5426929']
  .map(id => '/official/2017-09-25/doc-' + id + '.shtml'))

function isExamLink(raw, result = false) {
  try {
    const url = new URL(raw)
    if (url.protocol !== 'https:' || url.username || url.password || url.port || url.search) return false
    if (url.hostname === 'edu.sina.com.cn') {
      return examPages.has(url.pathname) || (result && answerPages.has(url.pathname))
    }
    return result && (['www.samr.gov.cn', 'www.spp.gov.cn', 'www.court.gov.cn', 'ipc.court.gov.cn'].includes(url.hostname)
      || (url.hostname === 'github.com' && url.pathname.startsWith('/CSHaitao/LexEval/')))
  } catch {
    return false
  }
}

module.exports = { isPaperLink, isExamLink }
