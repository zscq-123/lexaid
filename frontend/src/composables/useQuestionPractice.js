import { ref } from 'vue'
import { useRouter } from 'vue-router'
import { createSession } from '../api/exam'

export function useQuestionPractice() {
  const router = useRouter()
  const starting = ref(false)
  async function practice(questionId) {
    if (starting.value) return
    starting.value = true
    const from = router.currentRoute.value.fullPath
    try {
      const { sessionId } = await createSession({ sourceType: 'QUESTION', sourceId: questionId, count: 1 })
      if (router.currentRoute.value.fullPath === from) await router.push('/exam/run/' + sessionId)
    } catch {
      // The API client displays the server's error.
    } finally {
      starting.value = false
    }
  }
  return { practice, starting }
}
