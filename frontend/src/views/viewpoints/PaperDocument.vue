<template>
  <article ref="reader" class="paper-reader" :lang="paper.languageCode || 'und'" :aria-label="paper.title">
    <nav v-if="pageCount > 1" class="page-controls" aria-label="论文分页">
      <button type="button" :disabled="page === 1" @click="goTo(page - 1)">上一页</button>
      <label class="page-picker">
        <span aria-live="polite">第 {{ page }} 页，共 {{ pageCount }} 页</span>
        <select aria-label="跳转到阅读页" :value="page" @change="goTo(Number($event.target.value))">
          <option v-for="n in pageCount" :key="n" :value="n">
            第 {{ n }} 页{{ sourcePages && sourcePages[n - 1].number !== n ? '（原文第 ' + sourcePages[n - 1].number + ' 页）' : '' }}
          </option>
        </select>
      </label>
      <button type="button" :disabled="page === pageCount" @click="goTo(page + 1)">下一页</button>
    </nav>
    <div class="paper">
      <div ref="viewport" class="paper-viewport" @scroll="syncScroll">
        <div ref="flow" class="paper-flow" :class="{ reflow: !sourcePages }">
          <header v-if="!sourcePages || page === 1" class="masthead">
            <h1 id="paper-title">{{ paper.title }}</h1>
            <p class="authors">{{ paper.scholarName }}</p>
            <p v-if="paper.source || paper.publishYear" class="publication">
              {{ publicationLine(paper) }}
            </p>
            <p v-if="paper.doi" class="identifier">DOI {{ paper.doi }}</p>
          </header>
          <section v-if="paper.summary && (!sourcePages || page === 1)" class="abstract" aria-label="作者摘要">
            <h2>摘要</h2>
            <p v-for="(paragraph, i) in abstractParagraphs" :key="i">{{ paragraph }}</p>
          </section>
          <section v-if="blocks.length" class="body" aria-label="论文正文">
            <template v-for="(block, i) in blocks" :key="i">
              <h2 v-if="block.kind === 'heading'">{{ block.text }}</h2>
              <h3 v-else-if="block.kind === 'subheading'">{{ block.text }}</h3>
              <p v-else :class="{ line: block.kind === 'line' }">{{ block.text }}</p>
            </template>
          </section>
        </div>
      </div>
      <p v-if="!sourcePages && !blocks.length" class="extent">
        {{ paper.summary ? '本条收录了作者摘要，尚未收录全文。' : '本条仅收录论文题录。' }}
      </p>
      <footer v-if="page === pageCount" class="citation">
        <a v-if="sourceLink" :href="sourceLink" target="_blank" rel="noopener noreferrer">
          {{ sourceLink.startsWith('https://doi.org/') ? '通过 DOI 查看来源' : '查看论文来源' }}
        </a>
        <dl>
          <template v-if="paper.sourceDataset"><dt>数据来源</dt><dd>{{ paper.sourceDataset }}</dd></template>
          <template v-if="paper.licenseName"><dt>许可</dt><dd>{{ paper.licenseName }}</dd></template>
          <template v-if="paper.languageCode"><dt>语言</dt><dd>{{ languageLabel }}</dd></template>
          <template v-if="paper.tags?.length"><dt>标签</dt><dd>{{ paper.tags.join('、') }}</dd></template>
        </dl>
      </footer>
      <p v-if="sourcePages || pageCount > 1" class="folio">
        {{ folio }}
      </p>
    </div>
  </article>
</template>

<script setup>
import { computed, nextTick, onBeforeUnmount, onMounted, ref, watch } from 'vue'
import { paperBlocks, paperSourcePages, publicationLine } from './paper-text'

const props = defineProps({
  paper: { type: Object, required: true },
  sourceLink: { type: String, default: null }
})
const reader = ref(null)
const viewport = ref(null)
const flow = ref(null)
const page = ref(1)
const measuredPages = ref(1)
const sourcePages = computed(() => paperSourcePages(props.paper))
const pageCount = computed(() => sourcePages.value?.length ?? measuredPages.value)
const folio = computed(() => {
  if (!sourcePages.value) return '第 ' + page.value + ' 页'
  const label = props.paper.sourceDataset === 'Yale-Law-Archive-OSF' ? 'PDF 第 ' : '原文第 '
  return label + sourcePages.value[page.value - 1].number + ' 页'
})
const blocks = computed(() => sourcePages.value
  ? sourcePages.value[page.value - 1]?.blocks ?? []
  : paperBlocks(props.paper.content, props.paper))
const abstractParagraphs = computed(() => (props.paper.summary || '').trim().split(/\r?\n\s*\r?\n/))
const languageLabel = computed(() => {
  const code = props.paper.languageCode
  return { zh: '中文', en: '英文', und: '待核实' }[code?.toLowerCase().split('-')[0]] || code
})

let observer
let anchor
let frame
let measuredWidth = 0
let measuredHeight = 0

// Remember the first visible character so resizing can keep the reader in the same passage.
function rememberPosition() {
  anchor = null
  if (!flow.value || sourcePages.value || page.value === 1) return
  const edge = viewport.value.getBoundingClientRect().left + 1
  const elements = flow.value.querySelectorAll('h1, h2, h3, p')
  let start = 0
  let end = elements.length
  while (start < end) {
    const mid = Math.floor((start + end) / 2)
    if (elements[mid].getBoundingClientRect().right <= edge) start = mid + 1
    else end = mid
  }
  const node = elements[start]?.firstChild
  if (node?.nodeType !== Node.TEXT_NODE || !node.length) return
  const range = document.createRange()
  let low = 0
  let high = node.length - 1
  while (low < high) {
    const mid = Math.floor((low + high) / 2)
    range.setStart(node, mid)
    range.setEnd(node, mid + 1)
    if (range.getBoundingClientRect().right <= edge) low = mid + 1
    else high = mid
  }
  range.setStart(node, low)
  range.setEnd(node, low + 1)
  anchor = range
}

function measure() {
  if (!flow.value || sourcePages.value) return
  const rect = flow.value.getBoundingClientRect()
  if (!rect.width) return
  measuredWidth = rect.width
  measuredHeight = rect.height
  measuredPages.value = Math.max(1, Math.round(flow.value.scrollWidth / rect.width))
  const position = anchor?.getBoundingClientRect()
  const target = position ? Math.floor((position.left - rect.left) / rect.width + 0.001) + 1 : page.value
  page.value = Math.max(1, Math.min(target, measuredPages.value))
  viewport.value.scrollLeft = (page.value - 1) * rect.width
}

function scheduleMeasure() {
  cancelAnimationFrame(frame)
  frame = requestAnimationFrame(measure)
}

function goTo(n) {
  if (n < 1 || n > pageCount.value) return
  page.value = n
  if (!sourcePages.value) {
    viewport.value.scrollLeft = (n - 1) * flow.value.getBoundingClientRect().width
    rememberPosition()
  }
  const main = reader.value.closest('main')
  if (main && reader.value.getBoundingClientRect().top < main.getBoundingClientRect().top) {
    reader.value.scrollIntoView({ block: 'start' })
  }
}

// Keyboard focus or the browser's Find command can also scroll to another column.
function syncScroll() {
  if (sourcePages.value || !flow.value) return
  const { width, height } = flow.value.getBoundingClientRect()
  // A resize can emit scroll before ResizeObserver runs. Preserve the old text anchor.
  if (width !== measuredWidth || height !== measuredHeight) return
  const n = Math.round(viewport.value.scrollLeft / width) + 1
  if (n !== page.value) {
    page.value = Math.min(pageCount.value, Math.max(1, n))
    rememberPosition()
  }
}

watch(() => props.paper, () => {
  anchor = null
  page.value = 1
  measuredPages.value = 1
  nextTick(scheduleMeasure)
})
onMounted(() => {
  observer = new ResizeObserver(scheduleMeasure)
  observer.observe(flow.value)
  document.fonts?.addEventListener('loadingdone', scheduleMeasure)
  measure()
})
onBeforeUnmount(() => {
  observer?.disconnect()
  document.fonts?.removeEventListener('loadingdone', scheduleMeasure)
  cancelAnimationFrame(frame)
})
</script>

<style scoped>
.page-controls {
  position: sticky;
  top: 0;
  z-index: 2;
  display: flex;
  justify-content: center;
  align-items: center;
  gap: 16px;
  padding: 7px 0;
  background: #f7f8fa;
  color: #495361;
  font-size: 13px;
}
.page-controls button, .page-picker {
  background: white;
  border: 1px solid #d9dee5;
  border-radius: 5px;
  color: inherit;
  font: inherit;
  padding: 5px 10px;
  cursor: pointer;
}
.page-controls button:disabled { color: #858b95; cursor: default; background: transparent; }
.page-controls button:focus-visible, .page-picker:focus-within { outline: 2px solid #2e5fa3; }
.page-picker { position: relative; }
.page-picker::after { content: '▾'; margin-left: 5px; }
.page-picker select { position: absolute; inset: 0; width: 100%; opacity: 0; cursor: pointer; }
.paper {
  --paper-serif: Georgia, 'Songti SC', 'Noto Serif CJK SC', SimSun, serif;
  padding: clamp(24px, 3vw, 42px) clamp(20px, 6vw, 76px);
  background: white;
  border: 1px solid #dfe3e8;
  box-shadow: 0 2px 8px #27374a0a;
  color: #222932;
  overflow-wrap: anywhere;
}
.paper-viewport { overflow: hidden; overflow-anchor: none; }
.paper-flow.reflow {
  /* A single visible column per sheet. The browser breaks the text at actual line boxes. */
  column-count: 1;
  column-fill: auto;
  column-gap: 0;
  max-height: clamp(420px, calc(100dvh - 285px), 1000px);
}
.folio {
  margin: 18px 0 0;
  color: #67717d;
  text-align: center;
  font-size: 13px;
  font-variant-numeric: tabular-nums;
}
.masthead, .body .line { break-inside: avoid-column; }
.paper-flow :is(h1, h2, h3) { break-after: avoid-column; }
.paper-flow p { orphans: 2; widows: 2; }
.masthead { text-align: center; padding-bottom: 24px; border-bottom: 1px solid #c9cdd3; }
.masthead h1 {
  margin: 0 auto 16px;
  font: bold clamp(24px, 2.5vw, 34px)/1.4 var(--paper-serif);
  text-wrap: balance;
}
.authors { font: 17px/1.65 var(--paper-serif); }
.publication, .identifier, .extent, .citation { color: #59616b; font-size: 13px; line-height: 1.65; }
.publication { margin-top: 8px; }
.identifier { margin-top: 3px; }
.abstract { margin: 24px 0 0; }
.abstract h2 { font: bold 17px/1.6 var(--paper-serif); margin-bottom: 4px; }
.abstract p { font: 15px/1.85 var(--paper-serif); margin-bottom: 0.5em; }
.body { margin-top: 28px; font: 17px/1.9 var(--paper-serif); }
.body h2 { font-size: 1.22em; line-height: 1.55; margin: 1.5em 0 0.5em; }
.body h3 { font-size: 1.07em; line-height: 1.55; margin: 1.15em 0 0.45em; }
.body > :first-child { margin-top: 0; }
.body p { margin: 0 0 0.8em; }
.paper:lang(zh) .body p:not(.line) { text-indent: 2em; }
.body .line { white-space: pre-wrap; }
.extent { margin-top: 18px; }
.citation { margin-top: 28px; padding-top: 15px; border-top: 1px solid #dfe3e8; }
.citation a { text-decoration: underline; text-underline-offset: 3px; }
.citation dl { display: grid; grid-template-columns: max-content minmax(0, 1fr); gap: 2px 12px; margin-top: 6px; }
.citation dd { margin: 0; }
@media (max-width: 600px) {
  .paper { padding: 26px 18px; }
  .body { font-size: 16px; }
}
</style>
