import { ref, watch } from 'vue'

// Share an observer across the media in each scrolling pane.
const panes = new Map()

export function useNearViewport(target) {
  const nearby = ref(false)
  watch(target, (element, _, onCleanup) => {
    nearby.value = false
    if (!element) return
    if (typeof IntersectionObserver === 'undefined') {
      nearby.value = true
      return
    }
    const root = element.closest('.el-main')
    let pane = panes.get(root)
    if (!pane) {
      const targets = new Map()
      const observer = new IntersectionObserver(entries => {
        for (const entry of entries) {
          const visible = targets.get(entry.target)
          if (visible) visible.value = entry.isIntersecting
        }
      }, { root, rootMargin: '256px 0px' })
      pane = { targets, observer }
      panes.set(root, pane)
    }
    pane.targets.set(element, nearby)
    pane.observer.observe(element)
    onCleanup(() => {
      pane.targets.delete(element)
      pane.observer.unobserve(element)
      if (!pane.targets.size) {
        pane.observer.disconnect()
        panes.delete(root)
      }
    })
  }, { immediate: true, flush: 'post' })
  return nearby
}
