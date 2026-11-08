<template>
  <article class="post">
    <header>
      <UserAvatar :user-id="post.authorId" :version="post.authorAvatarVersion" :name="post.authorName" />
      <div class="byline">
        <strong>{{ post.authorName }}</strong>
        <small><time :datetime="post.createdAt">{{ timeLabel(post.createdAt) }}</time> · {{ post.visibility === 'FRIENDS' ? '仅好友' : '全部用户' }}</small>
      </div>
      <button v-if="!ownPost" class="author-action" type="button" @click="$emit('contact', post.authorId)">查看联系人</button>
      <el-button v-else text type="info" size="small" :disabled="deleting" @click="remove">删除</el-button>
    </header>
    <div v-if="images.length" ref="photo" class="photo" tabindex="0" role="group" :aria-label="'帖子图片，共 ' + images.length + ' 张'"
      @keydown.left.prevent="previous" @keydown.right.prevent="next"
      @touchstart.passive="startSwipe" @touchend.passive="finishSwipe" @touchcancel.passive="swipeStart = null">
      <img v-if="src && !imageFailed" :src="src" :alt="'第 ' + (index + 1) + ' 张帖子图片'" @error="imageFailed = true" />
      <div v-else-if="imageFailed" class="photo-message">
        <span>图片加载失败</span><el-button size="small" @click="retry++">重新加载</el-button>
      </div>
      <div v-else-if="nearPhoto" class="photo-message" role="status">正在加载图片…</div>
      <template v-if="images.length > 1">
        <span class="position">{{ index + 1 }} / {{ images.length }}</span>
        <button v-if="index > 0" type="button" class="arrow prev" aria-label="上一张图片" @click="previous"><el-icon><ArrowLeft /></el-icon></button>
        <button v-if="index < images.length - 1" type="button" class="arrow next" aria-label="下一张图片" @click="next"><el-icon><ArrowRight /></el-icon></button>
      </template>
    </div>
    <div v-if="post.caption" class="caption">{{ post.caption }}</div>
    <div class="interactions">
      <button class="reaction" :class="{ liked: post.liked }" type="button"
        :aria-pressed="post.liked" :disabled="liking" @click="toggleLike">
        <span class="heart" aria-hidden="true">
          <Transition name="heart-switch">
            <span :key="post.liked">{{ post.liked ? '♥' : '♡' }}</span>
          </Transition>
        </span>
        <span>{{ post.liked ? '已点赞' : '赞' }}</span><span v-if="post.likeCount">{{ post.likeCount }}</span>
      </button>
      <button type="button" class="reaction" :aria-expanded="commentsOpen" @click="toggleComments">
        <el-icon><ChatRound /></el-icon><span>评论</span><span v-if="post.commentCount">{{ post.commentCount }}</span>
      </button>
    </div>
    <section v-if="commentsOpen" class="comments" aria-label="评论">
      <form class="comment-form" @submit.prevent="sendComment">
        <el-input v-model="draft" aria-label="评论内容" placeholder="写条评论…" maxlength="500" :disabled="sending" />
        <el-button native-type="submit" :loading="sending" :disabled="!draft.trim()">发送</el-button>
      </form>
      <p v-if="commentError" class="muted" role="alert">
        {{ commentError }} <button class="retry" type="button" @click="loadComments()">重试</button>
      </p>
      <p v-else-if="commentLoading" class="muted" role="status">正在加载评论…</p>
      <p v-else-if="!comments.length" class="muted">还没有评论</p>
      <ul v-else>
        <li v-for="comment in comments" :key="comment.id">
          <div class="comment-author">
            <UserAvatar :user-id="comment.authorId" :version="comment.authorAvatarVersion" :name="comment.authorName" :size="24" />
            <strong>{{ comment.authorName }}</strong>
            <time :datetime="comment.createdAt">{{ timeLabel(comment.createdAt) }}</time>
          </div>
          <p>{{ comment.content }}</p>
        </li>
      </ul>
      <el-pagination v-if="commentsTotal > commentPageSize" small layout="prev, pager, next"
        :current-page="commentsPage" :page-size="commentPageSize" :total="commentsTotal" @current-change="loadComments" />
    </section>
  </article>
</template>

<script setup>
import { computed, onBeforeUnmount, ref, watch } from 'vue'
import { ArrowLeft, ArrowRight, ChatRound } from '@element-plus/icons-vue'
import { addPostComment, deletePost, listPostComments, loadPostImage, setPostLike } from '../../api/community-posts'
import { useUserStore } from '../../stores/user'
import { useNearViewport } from '../../composables/useNearViewport'
import UserAvatar from '../../components/UserAvatar.vue'

const props = defineProps({ post: { type: Object, required: true } })
const emit = defineEmits(['change', 'contact', 'deleted'])
const user = useUserStore()
const ownPost = computed(() => String(props.post.authorId) === String(user.user?.id))
const timeLabel = (value) => (value || '').replace('T', ' ').slice(0, 16)
const images = computed(() => props.post.imageIds || [])
const index = ref(0)
const photo = ref(null)
const nearPhoto = useNearViewport(photo)
let swipeStart = null
const src = ref('')
const retry = ref(0)
const imageFailed = ref(false)
const liking = ref(false)
const deleting = ref(false)
const commentsOpen = ref(false)
const draft = ref('')
const comments = ref([])
const commentsPage = ref(1)
const commentPageSize = 10
const commentsTotal = ref(0)
const commentLoading = ref(false)
const commentError = ref('')
const sending = ref(false)
let commentRequest = 0

function previous() { if (index.value > 0) index.value-- }
function next() { if (index.value < images.value.length - 1) index.value++ }
function startSwipe(event) {
  if (event.touches.length !== 1 || event.target.closest('button')) {
    swipeStart = null
    return
  }
  const touch = event.touches[0]
  swipeStart = { id: touch.identifier, x: touch.clientX, y: touch.clientY }
}
function finishSwipe(event) {
  const start = swipeStart
  swipeStart = null
  if (!start || event.touches.length) return
  const touch = Array.from(event.changedTouches).find((t) => t.identifier === start.id)
  if (!touch) return
  const dx = touch.clientX - start.x
  const dy = touch.clientY - start.y
  if (Math.abs(dx) < 45 || Math.abs(dx) < Math.abs(dy) * 1.5) return
  if (dx < 0) next()
  else previous()
}

watch([() => user.token, () => props.post.id, () => images.value[index.value], nearPhoto, retry],
  async ([token, postId, imageId, visible], _, onCleanup) => {
    src.value = ''
    imageFailed.value = false
    if (!token || imageId == null || !visible) return
    const controller = new AbortController()
    let objectUrl = ''
    onCleanup(() => {
      controller.abort()
      if (objectUrl) URL.revokeObjectURL(objectUrl)
    })
    try {
      const blob = await loadPostImage(postId, imageId, controller.signal)
      if (!controller.signal.aborted) {
        objectUrl = URL.createObjectURL(blob)
        src.value = objectUrl
      }
    } catch {
      if (!controller.signal.aborted) imageFailed.value = true
    }
  }, { immediate: true })

async function toggleLike() {
  if (liking.value) return
  liking.value = true
  const liked = !props.post.liked
  try {
    await setPostLike(props.post.id, liked)
    emit('change', { liked, likeCount: Math.max(0, props.post.likeCount + (liked ? 1 : -1)) })
  } catch { /* The API client displays the failure; keep the last confirmed state. */ }
  finally { liking.value = false }
}

async function remove() {
  if (deleting.value) return
  try {
    await ElMessageBox.confirm('确定删除这条帖子及其评论吗？', '删除帖子', {
      type: 'warning', confirmButtonText: '删除', cancelButtonText: '取消'
    })
  } catch { return }
  deleting.value = true
  try {
    await deletePost(props.post.id)
    emit('deleted', props.post.id)
    ElMessage.success('帖子已删除')
  } catch { /* Failure is reported by the API client. */ }
  finally { deleting.value = false }
}

function toggleComments() {
  commentsOpen.value = !commentsOpen.value
  if (commentsOpen.value) loadComments(1)
}

async function loadComments(page = commentsPage.value) {
  const request = ++commentRequest
  commentsPage.value = page
  commentLoading.value = true
  commentError.value = ''
  try {
    const result = await listPostComments(props.post.id, { page, size: commentPageSize })
    if (request === commentRequest) {
      comments.value = result.records
      commentsTotal.value = result.total
      emit('change', { commentCount: result.total })
    }
  } catch {
    if (request === commentRequest) commentError.value = '评论加载失败。'
  } finally {
    if (request === commentRequest) commentLoading.value = false
  }
}

async function sendComment() {
  const content = draft.value.trim()
  if (!content || sending.value) return
  sending.value = true
  try {
    await addPostComment(props.post.id, content)
    draft.value = ''
    emit('change', { commentCount: props.post.commentCount + 1 })
    await loadComments(1)
  } catch { /* Keep the draft so the user can retry. */ }
  finally { sending.value = false }
}
onBeforeUnmount(() => { commentRequest++ })
</script>

<style scoped>
.post { overflow: hidden; background: #fff; border: 1px solid #dde4ed; border-radius: 12px; }
header { display: flex; align-items: center; gap: 10px; padding: 15px 18px; }
.byline { flex: 1; min-width: 0; }
.byline strong { display: block; overflow-wrap: anywhere; color: #233754; }
.byline small { display: block; margin-top: 3px; color: #67778b; font-size: 12px; }
.author-action, .retry { border: none; background: none; color: #315d97; font: inherit; font-size: 13px; cursor: pointer; }
.photo { position: relative; width: 100%; aspect-ratio: 4 / 3; max-height: 550px; background: #f0f3f7; touch-action: pan-y pinch-zoom; }
.photo img { display: block; width: 100%; height: 100%; object-fit: contain; }
.photo-message { display: grid; height: 100%; gap: 8px; align-content: center; justify-items: center; color: #6a7888; }
.position { position: absolute; top: 12px; right: 12px; padding: 3px 10px; color: white; background: #1b293dd4; font-size: 12px; border-radius: 15px; }
.arrow { display: grid; place-items: center; width: 34px; height: 34px; border: none; border-radius: 50%; position: absolute; top: 50%; transform: translateY(-50%); background: #fffc; color: #2a3f5a; cursor: pointer; }
.prev { left: 10px; } .next { right: 10px; }
.caption { white-space: pre-wrap; overflow-wrap: anywhere; line-height: 1.65; padding: 3px 18px 10px; color: #263750; }
.photo + .caption { padding-top: 15px; }
.interactions { display: flex; gap: 7px; padding: 4px 12px 12px; }
.reaction { display: inline-flex; align-items: center; gap: 6px; padding: 6px; border: none; border-radius: 5px; color: #586c83; background: transparent; font: inherit; cursor: pointer; }
.reaction .el-icon { font-size: 18px; }
.heart { display: grid; place-items: center; width: 19px; font-family: system-ui, sans-serif; font-size: 23px; line-height: 18px; text-align: center; }
.heart > span { grid-area: 1 / 1; }
@media (prefers-reduced-motion: no-preference) {
  .heart-switch-enter-active { transition: transform var(--lex-motion-enter) var(--lex-ease-out), opacity var(--lex-motion-fast) ease-out; }
  .heart-switch-leave-active { transition: opacity var(--lex-motion-fast) ease-out; }
  .heart-switch-enter-from { opacity: 0; transform: scale(.82); }
  .heart-switch-leave-to { opacity: 0; }
}
.reaction:hover { background: #f3f6fb; }
.reaction.liked { color: #b24659; }
.comments { padding: 14px 18px; border-top: 1px solid #e6ebf1; }
.comment-form, .comment-author { display: flex; align-items: center; gap: 8px; }
.comments ul { list-style: none; }
.comments li { margin-top: 13px; }
.comment-author strong { overflow-wrap: anywhere; }
.comment-author time { margin-left: auto; }
.comment-author time, .muted { font-size: 12px; color: #68788d; }
.comments li p { white-space: pre-wrap; overflow-wrap: anywhere; margin-top: 2px; line-height: 1.65; }
.muted { margin: 14px 0 0; }
.comments :deep(.el-pagination) { margin-top: 10px; }
</style>
