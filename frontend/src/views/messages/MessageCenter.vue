<template>
  <section class="message-center" aria-label="私信">
    <header class="page-head">
      <div class="heading-copy">
        <p class="eyebrow">即时通讯</p>
        <h1>私信</h1>
        <p class="lede">和好友保持联系，消息实时送达。</p>
      </div>
      <el-button type="primary" :icon="EditPen" @click="openComposer">发起聊天</el-button>
    </header>

    <div class="messenger" :class="{ 'chat-open': activePeer }">
      <aside class="conversation-pane" aria-label="会话列表">
        <div class="list-head">
          <el-input v-model="conversationFilter" clearable aria-label="搜索会话"
            placeholder="搜索会话" @clear="conversationFilter = ''">
            <template #prefix><el-icon><Search /></el-icon></template>
          </el-input>
          <el-button text :icon="Refresh" :loading="conversationsLoading" aria-label="刷新会话"
            @click="loadConversations" />
        </div>
        <div class="connection" :class="{ online: messageStore.connected }" role="status">
          <span class="connection-dot" aria-hidden="true"></span>
          {{ connectionLabel }}
        </div>
        <div v-if="conversationsLoading && !conversations.length" class="list-status" role="status">
          <el-skeleton :rows="4" animated />
        </div>
        <div v-else-if="conversationsError" class="list-status">
          <p role="alert">会话加载失败</p>
          <el-button size="small" @click="loadConversations">重试</el-button>
        </div>
        <div v-else-if="!filteredConversations.length" class="list-status empty">
          <el-icon :size="30"><ChatLineSquare /></el-icon>
          <p>{{ conversationFilter ? '没有匹配的会话' : '还没有会话' }}</p>
          <el-button v-if="!conversationFilter" type="primary" plain size="small" @click="openComposer">发起聊天</el-button>
        </div>
        <div v-else class="conversation-list">
          <button v-for="conversation in filteredConversations" :key="conversation.peerId" type="button"
            class="conversation" :class="{ active: activePeer?.id === conversation.peerId }"
            :aria-current="activePeer?.id === conversation.peerId ? 'true' : undefined"
            @click="selectPeer(peerOf(conversation), { updateUrl: true })">
            <UserAvatar :user-id="conversation.peerId" :version="conversation.avatarVersion"
              :name="displayName(conversation)" :size="38" />
            <span class="conversation-copy">
              <span class="conversation-head">
                <strong>{{ displayName(conversation) }}</strong>
                <span v-if="conversation.pinned || conversation.muted" class="conversation-marks">
                  <el-icon v-if="conversation.pinned" title="置顶"><Top /></el-icon>
                  <el-icon v-if="conversation.muted" title="消息免打扰"><MuteNotification /></el-icon>
                </span>
                <time v-if="conversation.lastMessageAt" :datetime="conversation.lastMessageAt">
                  {{ formatConversationTime(conversation.lastMessageAt) }}
                </time>
              </span>
              <span class="conversation-preview">
                {{ conversation.lastMessageMine ? '我：' : '' }}{{ conversation.lastMessage }}
              </span>
            </span>
            <span v-if="conversation.unreadCount" class="unread-badge" :aria-label="conversation.unreadCount + ' 条未读'">
              {{ conversation.unreadCount > 99 ? '99+' : conversation.unreadCount }}
            </span>
          </button>
        </div>
      </aside>

      <main class="chat-pane" aria-label="聊天窗口">
        <template v-if="activePeer">
          <header class="chat-head">
            <button type="button" class="back-button" aria-label="返回会话列表" @click="closeChat">
              <el-icon><ArrowLeft /></el-icon>
            </button>
            <UserAvatar :user-id="activePeer.id" :version="activePeer.avatarVersion"
              :name="activePeerName" :size="36" />
            <div class="peer-copy">
              <strong>{{ activePeerName }}</strong>
              <small>@{{ activePeer.username }}</small>
            </div>
            <div class="chat-actions">
              <span v-if="!friend" class="not-friend">已不是好友</span>
              <button type="button" class="chat-more" :aria-expanded="infoOpen" aria-label="聊天信息"
                @click="infoOpen = !infoOpen">
                <el-icon><MoreFilled /></el-icon>
              </button>
            </div>
          </header>

          <div ref="scrollArea" class="message-scroll" :class="{ selecting: selectionMode }" role="log"
            aria-live="polite" @scroll.passive="onScroll">
            <button v-if="hasMore" type="button" class="load-more" :disabled="olderLoading" @click="loadOlder">
              {{ olderLoading ? '正在加载…' : '加载更早消息' }}
            </button>
            <div v-if="messageLoading" class="chat-status" role="status">正在加载消息…</div>
            <div v-else-if="!messages.length" class="chat-status empty">
              <el-icon :size="28"><ChatLineSquare /></el-icon>
              <p>还没有消息，发一句问候吧。</p>
            </div>
            <div v-for="message in messages" :key="message.id" class="message-row"
              :class="{ mine: message.senderId === currentUserId, highlighted: highlightedId === message.id }"
              :data-message-id="message.id" @contextmenu.prevent="openContextMenu($event, message)">
              <el-checkbox v-if="selectionMode" class="message-check" :model-value="selectedIds.includes(message.id)"
                :aria-label="'选择消息 ' + message.id" @click.stop @change="toggleSelection(message.id)" />
              <div v-if="!message.recalled && message.quoted" class="message-quote">
                <strong>{{ message.quoted.senderId === currentUserId ? '你' : activePeerName }}</strong>
                <span>{{ message.quoted.preview }}</span>
              </div>
              <div v-if="message.recalled" class="recall-note">
                {{ message.senderId === currentUserId ? '你撤回了一条消息' : '对方撤回了一条消息' }}
              </div>
              <template v-else-if="message.kind === 'IMAGE'">
                <div class="image-bubble">
                  <MessageImage :message-id="message.id" />
                </div>
                <p v-if="message.content" class="attachment-caption">{{ message.content }}</p>
              </template>
              <template v-else-if="message.kind === 'FILE'">
                <div class="file-bubble">
                  <span class="file-type">{{ fileLabel(message.fileName) }}</span>
                  <span class="file-copy">
                    <strong :title="message.fileName">{{ message.fileName || '附件' }}</strong>
                    <small>{{ formatSize(message.fileSize) }}</small>
                  </span>
                  <button type="button" class="file-download" :disabled="downloadingId === message.id"
                    :aria-label="'下载 ' + (message.fileName || '附件')" @click="downloadAttachment(message)">
                    <el-icon><Download /></el-icon>
                  </button>
                </div>
                <p v-if="message.content" class="attachment-caption">{{ message.content }}</p>
              </template>
              <template v-else-if="message.kind === 'VOICE'">
                <MessageVoice :message-id="message.id" :duration-seconds="message.durationSeconds"
                  :mine="message.senderId === currentUserId" />
                <p v-if="message.content" class="attachment-caption">{{ message.content }}</p>
              </template>
              <div v-else class="bubble">{{ message.content }}</div>
              <div class="message-meta">
                <time v-if="message.createdAt" :datetime="message.createdAt">{{ formatMessageTime(message.createdAt) }}</time>
                <span v-if="!message.recalled && message.senderId === currentUserId">
                  {{ message.read ? '已读' : '已送达' }}
                </span>
                <button v-if="canRecall(message)" type="button" class="recall-button" @click="recall(message)">撤回</button>
              </div>
            </div>
          </div>

          <div v-if="selectionMode" class="selection-bar" role="toolbar" aria-label="多选操作">
            <span>已选 {{ selectedIds.length }} 条</span>
            <el-button size="small" :disabled="!selectedIds.length || forwarding" @click="forwardSelected">转发</el-button>
            <el-button size="small" :disabled="!selectedIds.length" @click="favoriteSelected">收藏</el-button>
            <el-button size="small" type="danger" plain :disabled="!selectedIds.length" @click="deleteSelected">删除</el-button>
            <el-button link size="small" @click="exitSelection">取消</el-button>
          </div>
          <p v-if="chatError" class="chat-warning" role="alert">{{ chatError }}</p>
          <form class="composer" @submit.prevent="send">
            <input ref="imageInput" class="attachment-input" type="file" accept="image/jpeg,image/png"
              :disabled="!friend || attachmentSending" @change="pickAttachment($event, 'IMAGE')" />
            <input ref="fileInput" class="attachment-input" type="file" :accept="FILE_ACCEPT"
              :disabled="!friend || attachmentSending" @change="pickAttachment($event, 'FILE')" />
            <div v-if="recording" class="recording-bar" role="status">
              <span class="recording-dot" aria-hidden="true"></span>
              <span>正在录音 {{ formatVoiceTime(recordingSeconds) }}</span>
              <span class="recording-note">最长 5 分钟</span>
              <el-button size="small" :disabled="attachmentSending" @click="cancelRecording">取消</el-button>
              <el-button size="small" type="danger" :disabled="attachmentSending" @click="stopRecording(false)">停止</el-button>
            </div>
            <div v-else-if="voiceClip" class="voice-preview">
              <audio :src="voiceClip.url" controls preload="metadata"></audio>
              <small>{{ formatVoiceTime(voiceClip.seconds) }}</small>
              <el-button size="small" :disabled="attachmentSending" @click="clearVoice">取消</el-button>
              <el-button size="small" type="primary" :loading="attachmentSending" @click="sendVoice">发送语音</el-button>
            </div>
            <div v-if="quoting" class="composer-quote">
              <div>
                <strong>引用 {{ quoting.senderId === currentUserId ? '你' : activePeerName }}</strong>
                <span>{{ previewOf(quoting) }}</span>
              </div>
              <button type="button" aria-label="取消引用" @click="quoting = null">×</button>
            </div>
            <el-input ref="editor" v-model="draft" class="composer-input" type="textarea" :rows="3" resize="none"
              maxlength="2000" :disabled="!friend || sending || attachmentSending || recording"
              :placeholder="friend ? '输入消息…' : '你们已不是好友，无法继续发送'"
              aria-label="消息内容" @keydown.enter.exact="sendOnEnter" />
            <div class="composer-bar">
              <div class="composer-tools">
                <el-popover v-model:visible="emojiOpen" placement="top-start" :width="246" trigger="click">
                  <template #reference>
                    <button type="button" class="tool-icon emoji-trigger" :disabled="!friend || sending"
                      title="插入表情" aria-label="插入表情">☺</button>
                  </template>
                  <div class="emoji-grid">
                    <button v-for="emoji in EMOJIS" :key="emoji" type="button" class="emoji-item"
                      :aria-label="'插入 ' + emoji" @click="insertEmoji(emoji)">{{ emoji }}</button>
                  </div>
                </el-popover>
                <button type="button" class="tool-icon"
                  :disabled="!friend || attachmentSending || sending || messageLoading || recording || Boolean(voiceClip)"
                  title="发送图片" aria-label="发送图片" @click="imageInput?.click()">
                  <el-icon><Picture /></el-icon>
                </button>
                <button type="button" class="tool-icon"
                  :disabled="!friend || attachmentSending || sending || messageLoading || recording || Boolean(voiceClip)"
                  title="发送文件" aria-label="发送文件" @click="fileInput?.click()">
                  <el-icon><Document /></el-icon>
                </button>
                <button type="button" class="tool-icon" :class="{ recording }"
                  :disabled="!friend || attachmentSending || sending || messageLoading || Boolean(voiceClip) || recording"
                  :title="recording ? '正在录音' : '录制语音'" aria-label="录制语音" @click="startRecording">
                  <el-icon><Microphone /></el-icon>
                </button>
                <span v-if="attachmentSending" class="upload-state" role="status">
                  正在上传 {{ attachmentProgress }}%
                </span>
              </div>
              <div class="composer-send">
                <el-button type="primary" native-type="submit" class="send-button"
                  :loading="sending || attachmentSending" :disabled="!canSend">发送</el-button>
              </div>
            </div>
          </form>

          <aside v-if="infoOpen" class="chat-info" aria-label="聊天信息">
            <header class="chat-info-head">
              <h2>聊天信息</h2>
              <button type="button" class="chat-more" aria-label="关闭聊天信息" @click="infoOpen = false">
                <el-icon><Close /></el-icon>
              </button>
            </header>
            <div class="chat-info-person">
              <UserAvatar :user-id="activePeer.id" :version="activePeer.avatarVersion"
                :name="activePeerName" :size="46" />
              <div>
                <strong>{{ activePeerName }}</strong>
                <small>@{{ activePeer.username }}</small>
              </div>
            </div>
            <button type="button" class="info-action" @click="openSearch">
              <el-icon><Search /></el-icon>
              <span>查找聊天内容</span>
              <el-icon class="info-chevron"><ArrowRight /></el-icon>
            </button>
            <div class="info-row">
              <span>消息免打扰</span>
              <el-switch v-model="muted" :loading="settingsSaving" @change="saveSettings" />
            </div>
            <div class="info-row">
              <span>置顶聊天</span>
              <el-switch v-model="pinned" :loading="settingsSaving" @change="saveSettings" />
            </div>
            <button type="button" class="info-clear" :disabled="clearing" @click="clearChatHistory">
              清空聊天记录
            </button>
          </aside>
        </template>
        <div v-else class="chat-placeholder">
          <el-icon :size="40"><ChatLineSquare /></el-icon>
          <h2>选择一个会话</h2>
          <p>从左侧打开好友会话，或点击「发起聊天」选择通讯录好友。</p>
        </div>
      </main>
    </div>

    <el-dialog v-model="composerOpen" title="发起聊天" width="min(480px, 92vw)" append-to-body
      :close-on-click-modal="!friendsLoading">
      <el-input v-model="friendQuery" clearable aria-label="搜索好友" placeholder="搜索好友昵称或账号">
        <template #prefix><el-icon><Search /></el-icon></template>
      </el-input>
      <div v-if="friendsLoading" class="friend-status" role="status">正在加载通讯录…</div>
      <div v-else-if="friendsError" class="friend-status">
        <p role="alert">通讯录加载失败</p>
        <el-button size="small" @click="loadFriends">重试</el-button>
      </div>
      <div v-else-if="!filteredFriends.length" class="friend-status">
        <p>{{ friendQuery ? '没有匹配的好友' : '还没有好友' }}</p>
        <el-button v-if="!friendQuery" size="small" @click="goToContacts">去通讯录添加好友</el-button>
      </div>
      <div v-else class="friend-list">
        <button v-for="person in filteredFriends" :key="person.id" type="button" class="friend-row" @click="startChat(person)">
          <UserAvatar :user-id="person.id" :version="person.avatarVersion"
            :name="person.nickname || person.username" :size="36" />
          <span><strong>{{ person.nickname || person.username }}</strong><small>@{{ person.username }}</small></span>
          <el-icon><ArrowRight /></el-icon>
        </button>
      </div>
      <template #footer>
        <el-button @click="composerOpen = false">关闭</el-button>
      </template>
    </el-dialog>

    <Teleport to="body">
      <div v-if="contextMenu.open" class="context-backdrop" @click="closeContextMenu"
        @contextmenu.prevent="closeContextMenu"></div>
      <div v-if="contextMenu.open" class="message-context-menu" role="menu"
        :style="{ left: contextMenu.x + 'px', top: contextMenu.y + 'px' }" @click.stop>
        <button v-if="contextMenu.message?.content" type="button" role="menuitem" @click="copyMessage">复制</button>
        <button v-if="contextMenu.message?.kind === 'TEXT'" type="button" role="menuitem"
          @click="enlargeMessage">放大阅读</button>
        <button v-if="contextMenu.message?.content" type="button" role="menuitem"
          @click="translateFromMenu">翻译</button>
        <button v-if="contextMenu.message?.content" type="button" role="menuitem"
          @click="searchFromMenu">搜一搜</button>
        <div class="context-sep"></div>
        <button type="button" role="menuitem" @click="forwardFromMenu">转发...</button>
        <button type="button" role="menuitem" @click="favoriteFromMenu">收藏</button>
        <button type="button" role="menuitem" @click="multiSelectFromMenu">多选</button>
        <button type="button" role="menuitem" @click="remindFromMenu">提醒</button>
        <button v-if="!contextMenu.message?.recalled" type="button" role="menuitem" @click="quoteFromMenu">引用</button>
        <button v-if="canRecall(contextMenu.message)" type="button" role="menuitem"
          @click="recallFromMenu">撤回</button>
        <div class="context-sep"></div>
        <button type="button" role="menuitem" class="context-danger" @click="deleteFromMenu">删除</button>
        <button type="button" role="menuitem" class="context-danger" @click="clearFromMenu">清空聊天记录</button>
      </div>
    </Teleport>

    <el-dialog v-model="enlargeOpen" title="放大阅读" width="min(560px, 92vw)" append-to-body>
      <p class="enlarged-text">{{ enlargedText }}</p>
    </el-dialog>

    <el-dialog v-model="searchOpen" title="查找聊天内容" width="min(560px, 92vw)" append-to-body @opened="focusSearch">
      <el-input ref="searchInput" v-model="searchKeyword" clearable maxlength="100" aria-label="查找聊天内容"
        placeholder="输入关键词，查找与当前好友的聊天记录" @keyup.enter="runSearch">
        <template #append><el-button :loading="searchLoading" @click="runSearch">查找</el-button></template>
      </el-input>
      <div v-if="searchLoading" class="search-status" role="status">正在查找…</div>
      <div v-else-if="searchError" class="search-status">
        <p role="alert">查找失败</p>
        <el-button size="small" @click="runSearch">重试</el-button>
      </div>
      <div v-else-if="searchDone && !searchResults.length" class="search-status">没有找到匹配的聊天内容</div>
      <div v-else-if="searchResults.length" class="search-results">
        <button v-for="result in searchResults" :key="result.id" type="button" class="search-result"
          @click="jumpToMessage(result)">
          <span class="search-kind">{{ searchKind(result) }}</span>
          <span class="search-text">{{ searchPreview(result) }}</span>
          <time>{{ formatMessageTime(result.createdAt) }}</time>
        </button>
      </div>
    </el-dialog>

    <el-dialog v-model="forwardOpen" title="转发给" width="min(480px, 92vw)" append-to-body>
      <p class="dialog-tip">将转发 {{ forwardIds.length }} 条消息</p>
      <div v-if="friendsLoading" class="friend-status" role="status">正在加载通讯录…</div>
      <div v-else-if="!forwardCandidates.length" class="friend-status">还没有可以转发的好友</div>
      <div v-else class="friend-list">
        <button v-for="person in forwardCandidates" :key="person.id" type="button" class="friend-row"
          :disabled="forwarding" @click="forwardTo(person)">
          <UserAvatar :user-id="person.id" :version="person.avatarVersion"
            :name="person.nickname || person.username" :size="36" />
          <span><strong>{{ person.nickname || person.username }}</strong><small>@{{ person.username }}</small></span>
          <el-icon><ArrowRight /></el-icon>
        </button>
      </div>
      <template #footer>
        <el-button :disabled="forwarding" @click="forwardOpen = false">关闭</el-button>
      </template>
    </el-dialog>

    <el-dialog v-model="translateOpen" title="翻译" width="min(520px, 92vw)" append-to-body>
      <div v-if="translating" class="search-status" role="status">正在翻译…</div>
      <p v-else class="translation-text">{{ translation }}</p>
      <template #footer>
        <el-button v-if="!translating && translation" @click="copyTranslation">复制译文</el-button>
        <el-button @click="translateOpen = false">关闭</el-button>
      </template>
    </el-dialog>

    <el-dialog v-model="siteSearchOpen" title="搜一搜" width="min(600px, 92vw)" append-to-body>
      <el-input v-model="siteSearchKeyword" clearable aria-label="搜一搜关键词"
        placeholder="搜索法条、案例、学者观点或题目" @keyup.enter="runSiteSearch">
        <template #append><el-button :loading="siteSearchLoading" @click="runSiteSearch">搜索</el-button></template>
      </el-input>
      <div v-if="siteSearchLoading" class="search-status" role="status">正在搜索…</div>
      <div v-else-if="siteSearchError" class="search-status">搜索失败，请重试</div>
      <div v-else-if="siteSearchGroups.length" class="site-search-results">
        <section v-for="group in siteSearchGroups" :key="group.key">
          <h3>{{ group.label }}</h3>
          <router-link v-for="item in group.items" :key="item.id" class="site-search-item"
            :to="siteSearchTarget(item)" @click="siteSearchOpen = false">
            <strong>{{ item.title }}</strong>
            <span v-if="item.summary">{{ item.summary }}</span>
          </router-link>
        </section>
      </div>
      <div v-else-if="siteSearchKeyword.trim()" class="search-status">没有找到匹配的内容</div>
      <div v-else class="search-status">输入关键词搜索资料库</div>
      <template #footer><el-button @click="siteSearchOpen = false">关闭</el-button></template>
    </el-dialog>

    <el-dialog v-model="remindOpen" title="提醒" width="min(380px, 92vw)" append-to-body>
      <p class="dialog-tip">提醒我稍后查看这条消息</p>
      <el-radio-group v-model="remindMinutes" class="remind-options">
        <el-radio :value="60">1 小时后</el-radio>
        <el-radio :value="180">3 小时后</el-radio>
        <el-radio :value="1440">明天</el-radio>
      </el-radio-group>
      <template #footer>
        <el-button :disabled="reminding" @click="remindOpen = false">取消</el-button>
        <el-button type="primary" :loading="reminding" @click="doRemind">设置提醒</el-button>
      </template>
    </el-dialog>
  </section>
</template>

<script setup>
import { computed, nextTick, onBeforeUnmount, onMounted, reactive, ref, watch } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { ElMessage, ElMessageBox } from 'element-plus'
import {
  ArrowLeft, ArrowRight, ChatLineSquare, Close, Document, Download, EditPen, Microphone,
  MoreFilled, MuteNotification, Picture, Refresh, Search, Top
} from '@element-plus/icons-vue'
import { getPerson, listFriends } from '../../api/community-friends'
import { globalSearch } from '../../api/content'
import {
  clearConversation, deleteMessage, favoriteMessage, forwardMessage, getConversation, listConversations,
  loadMessageAttachment, markConversationRead, recallMessage, remindMessage, searchConversation,
  sendAttachmentMessage, sendDirectMessage, translateMessage, updateConversationSettings
} from '../../api/messages'
import MessageImage from '../../components/MessageImage.vue'
import MessageVoice from '../../components/MessageVoice.vue'
import UserAvatar from '../../components/UserAvatar.vue'
import { useMessageStore } from '../../stores/messages'
import { useUserStore } from '../../stores/user'

const route = useRoute()
const router = useRouter()
const user = useUserStore()
const messageStore = useMessageStore()

const conversations = ref([])
const conversationsLoading = ref(true)
const conversationsError = ref(false)
const conversationFilter = ref('')
const activePeer = ref(null)
const messages = ref([])
const messageLoading = ref(false)
const olderLoading = ref(false)
const hasMore = ref(false)
const friend = ref(true)
const chatError = ref('')
const draft = ref('')
const sending = ref(false)
const scrollArea = ref(null)
const composerOpen = ref(false)
const friends = ref([])
const friendsLoading = ref(false)
const friendsError = ref(false)
const friendQuery = ref('')
const imageInput = ref(null)
const fileInput = ref(null)
const attachmentSending = ref(false)
const attachmentProgress = ref(0)
const downloadingId = ref(null)
const clearing = ref(false)
const recording = ref(false)
const contextMenu = reactive({ open: false, x: 0, y: 0, message: null })
const enlargeOpen = ref(false)
const enlargedText = ref('')
const infoOpen = ref(false)
const muted = ref(false)
const pinned = ref(false)
const settingsSaving = ref(false)
const searchOpen = ref(false)
const searchInput = ref(null)
const searchKeyword = ref('')
const searchResults = ref([])
const searchLoading = ref(false)
const searchDone = ref(false)
const searchError = ref(false)
const highlightedId = ref(null)
const quoting = ref(null)
const selectionMode = ref(false)
const selectedIds = ref([])
const forwardOpen = ref(false)
const forwardIds = ref([])
const forwarding = ref(false)
const translateOpen = ref(false)
const translating = ref(false)
const translation = ref('')
const siteSearchOpen = ref(false)
const siteSearchKeyword = ref('')
const siteSearchLoading = ref(false)
const siteSearchError = ref(false)
const siteSearchResults = ref({})
const remindOpen = ref(false)
const remindMinutes = ref(60)
const reminding = ref(false)
const remindTarget = ref(null)
const recordingSeconds = ref(0)
const voiceClip = ref(null)
const editor = ref(null)
const emojiOpen = ref(false)
const EMOJIS = ['😀', '😄', '😁', '😂', '🤣', '😊', '😍', '🤔', '👍', '👏', '🙏', '🎉', '❤️', '🔥', '✅', '📚', '⚖️', '💡']
const SEARCH_GROUPS = [
  ['provisions', '法条'], ['cases', '案例'], ['viewpoints', '学者观点与论文'], ['questions', '题目']
]
const siteSearchGroups = computed(() => SEARCH_GROUPS
  .map(([key, label]) => ({ key, label, items: siteSearchResults.value[key] }))
  .filter(group => group.items?.length))
const forwardCandidates = computed(() => friends.value)

const IMAGE_TYPES = ['image/jpeg', 'image/png']
const IMAGE_EXTENSIONS = ['jpg', 'jpeg', 'png']
const FILE_EXTENSIONS = ['pdf', 'docx', 'pptx', 'xlsx', 'txt', 'md']
const FILE_ACCEPT = '.pdf,.docx,.pptx,.xlsx,.txt,.md'
const IMAGE_MAX_BYTES = 4 * 1024 * 1024

let conversationRequest = null
let historyRequest = null
let readTimer = null
let unsubscribe = null
let mediaRecorder = null
let recordingStream = null
let recordingChunks = []
let recordingTimer = null
let recordingTimeout = null
let recordingStartedAt = 0
let discardRecording = false
let recordingStopping = false
let recordingFinished = false
let mounted = true
let searchRequest = null
let highlightTimer = null

const currentUserId = computed(() => Number(user.user?.id) || 0)
const activePeerName = computed(() => activePeer.value
  ? activePeer.value.nickname || activePeer.value.username || '好友'
  : '')
const canSend = computed(() => Boolean(friend.value && draft.value.trim() && !sending.value
  && !attachmentSending.value && !messageLoading.value && !recording.value && !voiceClip.value))
const connectionLabel = computed(() => messageStore.connected
  ? '实时连接已建立'
  : messageStore.connecting ? '正在建立实时连接…' : '轮询同步中')
const filteredConversations = computed(() => {
  const term = conversationFilter.value.trim().toLowerCase()
  if (!term) return conversations.value
  return conversations.value.filter(item => [item.nickname, item.username, item.lastMessage]
    .some(value => value?.toLowerCase().includes(term)))
})
const filteredFriends = computed(() => {
  const term = friendQuery.value.trim().toLowerCase()
  if (!term) return friends.value
  return friends.value.filter(person => [person.nickname, person.username]
    .some(value => value?.toLowerCase().includes(term)))
})

async function loadConversations() {
  conversationRequest?.abort()
  const request = new AbortController()
  conversationRequest = request
  conversationsLoading.value = true
  conversationsError.value = false
  try {
    const data = await listConversations(request.signal)
    if (request.signal.aborted) return
    conversations.value = data
    const active = data.find(item => item.peerId === activePeer.value?.id)
    if (active) {
      activePeer.value = peerOf(active)
      muted.value = Boolean(active.muted)
      pinned.value = Boolean(active.pinned)
      if (active.unreadCount) void markRead(active.peerId)
    }
  } catch {
    if (!request.signal.aborted) conversationsError.value = true
  } finally {
    if (!request.signal.aborted) conversationsLoading.value = false
  }
}

function peerOf(item) {
  return {
    id: Number(item.peerId ?? item.id),
    username: item.username,
    nickname: item.nickname,
    avatarVersion: item.avatarVersion
  }
}

function displayName(item) {
  return item.nickname || item.username || '好友'
}

async function selectPeer(peer, { updateUrl = true } = {}) {
  if (!peer?.id) return
  const same = activePeer.value?.id === peer.id
  activePeer.value = peer
  if (updateUrl && Number(route.query.user) !== peer.id) {
    await router.replace({ path: '/messages', query: { user: String(peer.id) } })
  }
  if (same) return
  infoOpen.value = false
  searchOpen.value = false
  emojiOpen.value = false
  selectionMode.value = false
  selectedIds.value = []
  quoting.value = null
  if (recording.value) stopRecording(true)
  clearVoice()
  clearTimeout(readTimer)
  readTimer = null
  messages.value = []
  hasMore.value = false
  friend.value = true
  chatError.value = ''
  await loadHistory(peer.id, true)
  void markRead(peer.id)
}

async function loadHistory(peerId, initial = false) {
  historyRequest?.abort()
  const request = new AbortController()
  historyRequest = request
  messageLoading.value = initial
  try {
    const data = await getConversation(peerId, { size: 30 }, request.signal)
    if (request.signal.aborted) return
    messages.value = data.records
    hasMore.value = data.hasMore
    friend.value = data.friend
    muted.value = Boolean(data.muted)
    pinned.value = Boolean(data.pinned)
    if (initial) await scrollToBottom()
  } catch (error) {
    if (request.signal.aborted) return
    if (error.response?.status === 403) {
      friend.value = false
      chatError.value = '你们已不是好友，无法继续发送消息。'
    } else {
      chatError.value = '消息加载失败，请稍后重试。'
    }
  } finally {
    if (!request.signal.aborted) messageLoading.value = false
  }
}

async function loadOlder() {
  if (!hasMore.value || olderLoading.value || messageLoading.value || !activePeer.value || !messages.value.length) return
  const area = scrollArea.value
  const oldHeight = area?.scrollHeight || 0
  const oldTop = area?.scrollTop || 0
  const peerId = activePeer.value.id
  const beforeId = messages.value[0].id
  olderLoading.value = true
  try {
    const data = await getConversation(peerId, { beforeId, size: 30 })
    if (activePeer.value?.id !== peerId) return
    messages.value = [...data.records, ...messages.value]
    hasMore.value = data.hasMore
    friend.value = data.friend
    await nextTick()
    if (area) area.scrollTop = area.scrollHeight - oldHeight + oldTop
  } catch {
    // The API client reports the failure; keep the currently loaded messages.
  } finally {
    olderLoading.value = false
  }
}

function appendMessage(message) {
  if (!message || messages.value.some(item => item.id === message.id)) return
  const area = scrollArea.value
  const nearBottom = !area || area.scrollHeight - area.scrollTop - area.clientHeight < 90
  messages.value.push(message)
  if (message.senderId === currentUserId.value || nearBottom) void scrollToBottom()
}

async function scrollToBottom() {
  await nextTick()
  const area = scrollArea.value
  if (area) area.scrollTop = area.scrollHeight
}

function onScroll() {
  if (contextMenu.open) closeContextMenu()
  if (scrollArea.value && scrollArea.value.scrollTop < 36) void loadOlder()
}

function previewOf(message) {
  if (message.recalled) return '消息已撤回'
  if (message.kind === 'IMAGE') return message.content?.trim() ? message.content.slice(0, 160) : '[图片]'
  if (message.kind === 'FILE') {
    return message.content?.trim() ? message.content.slice(0, 160) : `[文件] ${message.fileName || '附件'}`
  }
  if (message.kind === 'VOICE') return '[语音]'
  return (message.content || '').slice(0, 160)
}

function updateConversation(message, countUnread) {
  const mine = message.senderId === currentUserId.value
  const peerId = mine ? message.recipientId : message.senderId
  const index = conversations.value.findIndex(item => item.peerId === peerId)
  if (index < 0) {
    void loadConversations()
    return
  }
  const conversation = {
    ...conversations.value[index],
    lastMessageId: message.id,
    lastMessage: previewOf(message),
    lastMessageAt: message.createdAt,
    lastMessageMine: mine,
    unreadCount: countUnread ? (conversations.value[index].unreadCount || 0) + 1 : conversations.value[index].unreadCount
  }
  conversations.value = [conversation, ...conversations.value.filter(item => item.peerId !== peerId)]
}

function applyRecallToConversation(message) {
  const mine = message.senderId === currentUserId.value
  const peerId = mine ? message.recipientId : message.senderId
  const index = conversations.value.findIndex(item => item.peerId === peerId)
  if (index < 0) return
  const conversation = { ...conversations.value[index] }
  const isLast = conversation.lastMessageId === message.id
  if (isLast) conversation.lastMessage = '消息已撤回'
  if (!mine && !message.read && conversation.unreadCount > 0) conversation.unreadCount -= 1
  conversations.value = isLast
    ? [conversation, ...conversations.value.filter((item, i) => i !== index)]
    : conversations.value.map((item, i) => i === index ? conversation : item)
}

async function markRead(peerId) {
  const conversation = conversations.value.find(item => item.peerId === peerId)
  const previous = conversation?.unreadCount || 0
  if (conversation) conversation.unreadCount = 0
  if (previous) messageStore.setUnread(Math.max(0, messageStore.unreadTotal - previous))
  try {
    const result = await markConversationRead(peerId)
    if (result && typeof result.unreadTotal === 'number') messageStore.setUnread(result.unreadTotal)
  } catch {
    // Reading is retried the next time the conversation is opened.
  }
}

function scheduleMarkRead(peerId) {
  clearTimeout(readTimer)
  readTimer = setTimeout(() => {
    readTimer = null
    void markRead(peerId)
  }, 300)
}

function sendOnEnter(event) {
  if (event.isComposing || event.keyCode === 229) return
  event.preventDefault()
  void send()
}

function pickAttachment(event, kind) {
  emojiOpen.value = false
  const file = event.target.files?.[0]
  event.target.value = ''
  if (!file || !activePeer.value || !friend.value || attachmentSending.value || messageLoading.value
      || recording.value || voiceClip.value) return
  const extension = file.name.split('.').pop()?.toLowerCase() || ''
  if (kind === 'IMAGE') {
    if (!IMAGE_TYPES.includes(file.type) && !IMAGE_EXTENSIONS.includes(extension)) {
      ElMessage.warning('请选择 JPG 或 PNG 图片')
      return
    }
    if (file.size > IMAGE_MAX_BYTES) {
      ElMessage.warning('图片不能超过 4 MB')
      return
    }
  } else if (!FILE_EXTENSIONS.includes(extension)) {
    ElMessage.warning('仅支持 PDF、DOCX、PPTX、XLSX、TXT 或 MD 文件')
    return
  }
  const peerId = activePeer.value.id
  attachmentSending.value = true
  attachmentProgress.value = 0
  chatError.value = ''
  sendAttachmentMessage({
    recipientId: peerId,
    content: draft.value.trim(),
    kind,
    file,
    quoteId: quoting.value?.id
  }, progress => {
    if (progress.total) attachmentProgress.value = Math.min(100, Math.round(progress.loaded * 100 / progress.total))
  }).then(async message => {
    draft.value = ''
    quoting.value = null
    updateConversation(message, false)
    if (activePeer.value?.id === peerId) {
      appendMessage(message)
      await scrollToBottom()
    }
  }).catch(error => {
    if (error.response?.status === 403) friend.value = false
    chatError.value = error.response?.data?.message || '附件发送失败，请稍后重试。'
  }).finally(() => {
    attachmentSending.value = false
    attachmentProgress.value = 0
  })
}

function voiceExtension(mime) {
  if (mime.includes('mp4') || mime.includes('m4a')) return 'm4a'
  if (mime.includes('ogg')) return 'ogg'
  if (mime.includes('mpeg') || mime.includes('mp3')) return 'mp3'
  if (mime.includes('wav')) return 'wav'
  return 'webm'
}

function insertEmoji(emoji) {
  if (Array.from(draft.value).length >= 2000) {
    ElMessage.warning('单条消息不能超过 2000 字')
    return
  }
  draft.value += emoji
  emojiOpen.value = false
  nextTick(() => editor.value?.focus())
}

function formatVoiceTime(seconds) {
  const value = Math.max(0, Math.round(Number(seconds) || 0))
  return Math.floor(value / 60) + ':' + String(value % 60).padStart(2, '0')
}

function stopTracks() {
  if (recordingStream) {
    for (const track of recordingStream.getTracks()) track.stop()
  }
  recordingStream = null
}

function clearVoice() {
  if (voiceClip.value?.url) URL.revokeObjectURL(voiceClip.value.url)
  voiceClip.value = null
}

async function startRecording() {
  emojiOpen.value = false
  if (recording.value || voiceClip.value || sending.value || attachmentSending.value || messageLoading.value) return
  if (!activePeer.value || !friend.value) return
  if (!navigator.mediaDevices?.getUserMedia || typeof MediaRecorder === 'undefined') {
    ElMessage.warning('当前浏览器不支持语音录制，请使用最新版 Chrome、Edge 或 Safari')
    return
  }
  try {
    const peerId = activePeer.value.id
    const stream = await navigator.mediaDevices.getUserMedia({ audio: true })
    if (!mounted || activePeer.value?.id !== peerId) {
      for (const track of stream.getTracks()) track.stop()
      return
    }
    const candidates = ['audio/webm;codecs=opus', 'audio/webm', 'audio/mp4', 'audio/ogg;codecs=opus']
    const mime = candidates.find(type => MediaRecorder.isTypeSupported?.(type))
    const recorder = mime ? new MediaRecorder(stream, { mimeType: mime }) : new MediaRecorder(stream)
    recordingStream = stream
    mediaRecorder = recorder
    recordingChunks = []
    discardRecording = false
    recordingStopping = false
    recordingFinished = false
    recordingStartedAt = Date.now()
    recordingSeconds.value = 0
    recorder.ondataavailable = event => {
      if (event.data?.size) recordingChunks.push(event.data)
    }
    recorder.onstop = finishRecording
    recorder.onerror = () => {
      ElMessage.error('录音过程中出现问题，请重试')
      stopRecording(true)
    }
    recorder.start(250)
    recording.value = true
    recordingTimeout = setTimeout(() => {
      if (recording.value) stopRecording(false)
    }, 300000)
    recordingTimer = setInterval(() => {
      recordingSeconds.value = Math.floor((Date.now() - recordingStartedAt) / 1000)
      if (recordingSeconds.value >= 300) stopRecording(false)
    }, 500)
  } catch {
    stopTracks()
    recording.value = false
    ElMessage.error('无法使用麦克风，请检查浏览器权限')
  }
}

function stopRecording(discard = false) {
  if (!recording.value || !mediaRecorder || recordingStopping) return
  recordingStopping = true
  discardRecording = discard
  try {
    if (mediaRecorder.state !== 'inactive') mediaRecorder.stop()
    else finishRecording()
  } catch {
    finishRecording()
  }
}

function finishRecording() {
  if (recordingFinished) return
  recordingFinished = true
  const recorder = mediaRecorder
  const seconds = Math.max(1, Math.min(300, Math.round((Date.now() - recordingStartedAt) / 1000)))
  const mime = recorder?.mimeType || recordingChunks[0]?.type || 'audio/webm'
  const blob = new Blob(recordingChunks, { type: mime })
  const discard = discardRecording
  if (recordingTimer) clearInterval(recordingTimer)
  recordingTimer = null
  if (recordingTimeout) clearTimeout(recordingTimeout)
  recordingTimeout = null
  mediaRecorder = null
  recordingChunks = []
  discardRecording = false
  recordingStopping = false
  recording.value = false
  recordingSeconds.value = 0
  stopTracks()
  if (discard || !mounted) return
  if (!blob.size) {
    ElMessage.warning('没有录到声音，请重试')
    return
  }
  if (blob.size > 20 * 1024 * 1024) {
    ElMessage.warning('语音消息不能超过 20 MB')
    return
  }
  clearVoice()
  voiceClip.value = { blob, url: URL.createObjectURL(blob), mime, seconds }
}

function cancelRecording() {
  if (recording.value) stopRecording(true)
  else clearVoice()
}

async function sendVoice() {
  const clip = voiceClip.value
  if (!clip || !activePeer.value || !friend.value || attachmentSending.value) return
  const peerId = activePeer.value.id
  attachmentSending.value = true
  attachmentProgress.value = 0
  chatError.value = ''
  try {
    const file = new File([clip.blob], 'voice.' + voiceExtension(clip.mime), { type: clip.mime })
    const message = await sendAttachmentMessage({
      recipientId: peerId,
      content: draft.value.trim(),
      kind: 'VOICE',
      file,
      duration: clip.seconds,
      quoteId: quoting.value?.id
    }, progress => {
      if (progress.total) attachmentProgress.value = Math.min(100, Math.round(progress.loaded * 100 / progress.total))
    })
    draft.value = ''
    quoting.value = null
    clearVoice()
    updateConversation(message, false)
    if (activePeer.value?.id === peerId) {
      appendMessage(message)
      await scrollToBottom()
    }
  } catch (error) {
    if (error.response?.status === 403) friend.value = false
    chatError.value = error.response?.data?.message || '语音发送失败，请稍后重试。'
  } finally {
    attachmentSending.value = false
    attachmentProgress.value = 0
  }
}

async function downloadAttachment(message) {
  if (downloadingId.value) return
  downloadingId.value = message.id
  try {
    const blob = await loadMessageAttachment(message.id, undefined, { silent: false })
    const url = URL.createObjectURL(blob)
    const link = document.createElement('a')
    link.href = url
    link.download = message.fileName || '附件'
    document.body.appendChild(link)
    link.click()
    link.remove()
    setTimeout(() => URL.revokeObjectURL(url), 1000)
  } catch {
    // The API client reports the failed download.
  } finally {
    downloadingId.value = null
  }
}

function canRecall(message) {
  return Boolean(message && !message.recalled && message.recallable
    && message.senderId === currentUserId.value)
}

async function recall(message) {
  if (!canRecall(message)) return
  try {
    await ElMessageBox.confirm('撤回后对方将看到「消息已撤回」，确定撤回吗？', '撤回消息',
      { confirmButtonText: '撤回', cancelButtonText: '取消', type: 'warning' })
  } catch {
    return
  }
  try {
    const updated = await recallMessage(message.id)
    const index = messages.value.findIndex(item => item.id === message.id)
    if (index >= 0) messages.value[index] = { ...messages.value[index], ...updated }
    applyRecallToConversation(updated)
  } catch (error) {
    ElMessage.warning(error.response?.data?.message || '撤回失败，请稍后重试。')
  }
}

async function send() {
  emojiOpen.value = false
  const content = draft.value.trim()
  if (!content || !activePeer.value || !friend.value || sending.value || attachmentSending.value
      || messageLoading.value || recording.value || voiceClip.value) return
  const peerId = activePeer.value.id
  sending.value = true
  chatError.value = ''
  try {
    const message = await sendDirectMessage(peerId, content, quoting.value?.id)
    draft.value = ''
    quoting.value = null
    updateConversation(message, false)
    if (activePeer.value?.id === peerId) {
      appendMessage(message)
      await scrollToBottom()
    }
  } catch (error) {
    if (error.response?.status === 403) friend.value = false
    chatError.value = error.response?.data?.message || '发送失败，请稍后重试。'
  } finally {
    sending.value = false
  }
}

function onEvent(event) {
  if (event.type === 'MESSAGE' && event.message) {
    const message = event.message
    const mine = message.senderId === currentUserId.value
    const peerId = mine ? message.recipientId : message.senderId
    const active = activePeer.value?.id === peerId
    if (active) {
      appendMessage(message)
      if (!mine) scheduleMarkRead(peerId)
    }
    updateConversation(message, !mine && !active)
    return
  }
  if (event.type === 'DELETE') {
    const messageId = Number(event.messageId)
    messages.value = messages.value.filter(message => message.id !== messageId)
    void loadConversations()
    return
  }
  if (event.type === 'CLEAR') {
    const peerId = Number(event.peerId)
    conversations.value = conversations.value.filter(item => item.peerId !== peerId)
    if (activePeer.value?.id === peerId) {
      messages.value = []
      hasMore.value = false
      chatError.value = ''
      infoOpen.value = false
      searchOpen.value = false
      selectionMode.value = false
      selectedIds.value = []
      quoting.value = null
    }
    return
  }
  if (event.type === 'RECALL' && event.message) {
    const recalled = event.message
    const index = messages.value.findIndex(item => item.id === recalled.id)
    if (index >= 0) messages.value[index] = { ...messages.value[index], ...recalled }
    applyRecallToConversation(recalled)
    return
  }
  if (event.type === 'READ') {
    const peerId = event.readerId === currentUserId.value ? event.peerId : event.readerId
    const cutoff = parseDate(event.readAt)
    if (peerId === activePeer.value?.id) {
      for (const message of messages.value) {
        const created = parseDate(message.createdAt)
        if (message.senderId === currentUserId.value && (!cutoff || !created || created <= cutoff)) {
          message.read = true
        }
      }
    }
    return
  }
  if (event.type === 'POLL' && !messageStore.connected) {
    if (activePeer.value) void refreshLatest()
    void loadConversations()
    return
  }
  if (event.type === 'CONNECTED') {
    void loadConversations()
    if (activePeer.value) void refreshLatest()
  }
}

async function refreshLatest() {
  if (!activePeer.value || messageLoading.value) return
  const peerId = activePeer.value.id
  try {
    const data = await getConversation(peerId, { size: 30 })
    if (activePeer.value?.id !== peerId) return
    friend.value = data.friend
    for (const message of data.records) {
      const index = messages.value.findIndex(item => item.id === message.id)
      if (index >= 0) messages.value[index] = { ...messages.value[index], ...message }
      else appendMessage(message)
    }
    if (data.records.some(message => message.senderId !== currentUserId.value)) scheduleMarkRead(peerId)
  } catch {
    // Polling is best effort; the visible error state is reserved for the initial load.
  }
}

function openContextMenu(event, message) {
  const width = 184
  const height = 480
  contextMenu.message = message
  contextMenu.x = Math.max(8, Math.min(event.clientX, window.innerWidth - width - 8))
  contextMenu.y = Math.max(8, Math.min(event.clientY, window.innerHeight - height - 8))
  contextMenu.open = true
}

function closeContextMenu() {
  contextMenu.open = false
}

async function copyMessage() {
  const text = contextMenu.message?.content
  if (!text) {
    ElMessage.warning('这条消息没有可复制的文字')
    return
  }
  try {
    await navigator.clipboard.writeText(text)
    ElMessage.success('已复制')
    closeContextMenu()
  } catch {
    ElMessage.error('复制失败，请检查浏览器剪贴板权限')
  }
}

function enlargeMessage() {
  const text = contextMenu.message?.content
  closeContextMenu()
  if (!text) return
  enlargedText.value = text
  enlargeOpen.value = true
}

function recallFromMenu() {
  const message = contextMenu.message
  closeContextMenu()
  if (message) void recall(message)
}

function clearFromMenu() {
  closeContextMenu()
  void clearChatHistory()
}

function favoriteFromMenu() {
  const message = contextMenu.message
  closeContextMenu()
  if (message) void favoriteOne(message)
}

function forwardFromMenu() {
  const message = contextMenu.message
  closeContextMenu()
  if (message) openForward([message.id])
}

function quoteFromMenu() {
  quoting.value = contextMenu.message
  closeContextMenu()
  nextTick(() => editor.value?.focus())
}

function multiSelectFromMenu() {
  const message = contextMenu.message
  closeContextMenu()
  if (!message) return
  selectionMode.value = true
  selectedIds.value = [message.id]
}

function remindFromMenu() {
  remindTarget.value = contextMenu.message
  remindMinutes.value = 60
  closeContextMenu()
  if (remindTarget.value) remindOpen.value = true
}

function deleteFromMenu() {
  const message = contextMenu.message
  closeContextMenu()
  if (message) void deleteOne(message)
}

function translateFromMenu() {
  const message = contextMenu.message
  closeContextMenu()
  if (!message) return
  translateOpen.value = true
  translating.value = true
  translation.value = ''
  translateMessage(message.id).then(data => {
    translation.value = data.translation
  }).catch(error => {
    translation.value = error.response?.data?.message || '翻译失败，请稍后再试。'
  }).finally(() => {
    translating.value = false
  })
}

function searchFromMenu() {
  const message = contextMenu.message
  closeContextMenu()
  if (!message) return
  const text = message.content?.trim() || message.fileName || ''
  if (!text) {
    ElMessage.warning('这条消息没有可搜索的文字')
    return
  }
  siteSearchKeyword.value = text.slice(0, 100)
  siteSearchResults.value = {}
  siteSearchError.value = false
  siteSearchOpen.value = true
  void runSiteSearch()
}

async function runSiteSearch() {
  const keyword = siteSearchKeyword.value.trim()
  if (!keyword || siteSearchLoading.value) return
  siteSearchLoading.value = true
  siteSearchError.value = false
  try {
    siteSearchResults.value = await globalSearch(keyword)
  } catch {
    siteSearchError.value = true
  } finally {
    siteSearchLoading.value = false
  }
}

function siteSearchTarget(item) {
  const paths = { PROVISION: '/provisions/', CASE: '/cases/', VIEWPOINT: '/viewpoints/' }
  return paths[item.type] ? paths[item.type] + item.id
    : { path: '/ai-chat', query: { contextType: 'QUESTION', contextId: String(item.id) } }
}

function copyTranslation() {
  if (!translation.value) return
  navigator.clipboard.writeText(translation.value)
    .then(() => ElMessage.success('译文已复制'))
    .catch(() => ElMessage.error('复制失败'))
}

function toggleSelection(id) {
  const index = selectedIds.value.indexOf(id)
  if (index >= 0) selectedIds.value.splice(index, 1)
  else selectedIds.value.push(id)
}

function exitSelection() {
  selectionMode.value = false
  selectedIds.value = []
}

function forwardSelected() {
  if (selectedIds.value.length) openForward([...selectedIds.value])
}

async function favoriteSelected() {
  const ids = [...selectedIds.value]
  if (!ids.length) return
  try {
    for (const id of ids) await favoriteMessage(id)
    ElMessage.success(`已收藏 ${ids.length} 条消息`)
    exitSelection()
  } catch (error) {
    ElMessage.warning(error.response?.data?.message || '收藏失败')
  }
}

async function deleteSelected() {
  const ids = [...selectedIds.value]
  if (!ids.length) return
  try {
    await ElMessageBox.confirm(`删除选中的 ${ids.length} 条消息？仅对你生效。`, '删除消息',
      { confirmButtonText: '删除', cancelButtonText: '取消', type: 'warning' })
  } catch {
    return
  }
  try {
    for (const id of ids) await deleteMessage(id)
    const removed = new Set(ids)
    messages.value = messages.value.filter(message => !removed.has(message.id))
    exitSelection()
    void loadConversations()
    ElMessage.success('已删除')
  } catch (error) {
    ElMessage.warning(error.response?.data?.message || '删除失败')
  }
}

async function favoriteOne(message) {
  try {
    await favoriteMessage(message.id)
    ElMessage.success('已收藏，可在个人中心查看')
  } catch (error) {
    ElMessage.warning(error.response?.data?.message || '收藏失败')
  }
}

function openForward(ids) {
  forwardIds.value = ids.filter(Boolean)
  if (!forwardIds.value.length) return
  forwardOpen.value = true
  if (!friends.value.length) void loadFriends()
}

async function forwardTo(person) {
  if (forwarding.value || !forwardIds.value.length) return
  forwarding.value = true
  try {
    for (const id of forwardIds.value) await forwardMessage(id, person.id)
    ElMessage.success(`已转发给 ${person.nickname || person.username}`)
    forwardOpen.value = false
    exitSelection()
    void loadConversations()
  } catch (error) {
    ElMessage.warning(error.response?.data?.message || '转发失败')
  } finally {
    forwarding.value = false
  }
}

async function deleteOne(message) {
  try {
    await ElMessageBox.confirm('删除这条消息？仅对你生效，对方仍可看到。', '删除消息',
      { confirmButtonText: '删除', cancelButtonText: '取消', type: 'warning' })
  } catch {
    return
  }
  try {
    await deleteMessage(message.id)
    messages.value = messages.value.filter(item => item.id !== message.id)
    void loadConversations()
    ElMessage.success('已删除')
  } catch (error) {
    ElMessage.warning(error.response?.data?.message || '删除失败')
  }
}

async function doRemind() {
  if (!remindTarget.value || reminding.value) return
  reminding.value = true
  try {
    await remindMessage(remindTarget.value.id, remindMinutes.value)
    remindOpen.value = false
    ElMessage.success('已设置提醒，到时会通知你')
  } catch (error) {
    ElMessage.warning(error.response?.data?.message || '设置提醒失败')
  } finally {
    reminding.value = false
  }
}

async function saveSettings() {
  if (!activePeer.value || settingsSaving.value) return
  settingsSaving.value = true
  try {
    const data = await updateConversationSettings(activePeer.value.id, {
      muted: Boolean(muted.value),
      pinned: Boolean(pinned.value)
    })
    muted.value = Boolean(data.muted)
    pinned.value = Boolean(data.pinned)
    await loadConversations()
  } catch {
    // The API client reports the failure; reload restores the saved switch state.
    await loadConversations()
  } finally {
    settingsSaving.value = false
  }
}

function openSearch() {
  searchOpen.value = true
  searchKeyword.value = ''
  searchResults.value = []
  searchDone.value = false
  searchError.value = false
}

function focusSearch() {
  nextTick(() => searchInput.value?.focus())
}

async function runSearch() {
  const keyword = searchKeyword.value.trim()
  if (!keyword || !activePeer.value) return
  searchRequest?.abort()
  const request = new AbortController()
  searchRequest = request
  searchLoading.value = true
  searchDone.value = false
  searchError.value = false
  try {
    const data = await searchConversation(activePeer.value.id, keyword, request.signal)
    if (request.signal.aborted) return
    searchResults.value = data
    searchDone.value = true
  } catch {
    if (!request.signal.aborted) searchError.value = true
  } finally {
    if (!request.signal.aborted) searchLoading.value = false
  }
}

function searchKind(message) {
  return { TEXT: '文本', IMAGE: '图片', FILE: '文件', VOICE: '语音' }[message.kind] || '消息'
}

function searchPreview(message) {
  return previewOf(message).slice(0, 100)
}

async function jumpToMessage(message) {
  searchOpen.value = false
  await nextTick()
  const target = scrollArea.value?.querySelector(`[data-message-id="${message.id}"]`)
  if (target) {
    target.scrollIntoView({ block: 'center' })
    highlightedId.value = message.id
    if (highlightTimer) clearTimeout(highlightTimer)
    highlightTimer = setTimeout(() => { highlightedId.value = null }, 1800)
  } else if (message.kind === 'TEXT' && message.content) {
    enlargedText.value = message.content
    enlargeOpen.value = true
  } else {
    ElMessage.info('这条消息较早，可在搜索结果中查看原文')
  }
}

async function clearChatHistory() {
  if (!activePeer.value || clearing.value) return
  try {
    await ElMessageBox.confirm(
      `清空与「${activePeerName.value}」的聊天记录？仅对你生效，对方仍可看到这些消息。`,
      '清空聊天记录',
      { confirmButtonText: '清空', cancelButtonText: '取消', type: 'warning' })
  } catch {
    return
  }
  const peerId = activePeer.value.id
  clearing.value = true
  try {
    const result = await clearConversation(peerId)
    messages.value = []
    hasMore.value = false
    chatError.value = ''
    conversations.value = conversations.value.filter(item => item.peerId !== peerId)
    infoOpen.value = false
    searchOpen.value = false
    selectionMode.value = false
    selectedIds.value = []
    quoting.value = null
    if (result && typeof result.unreadTotal === 'number') messageStore.setUnread(result.unreadTotal)
    emojiOpen.value = false
    ElMessage.success('聊天记录已清空')
  } catch {
    // The API client reports the failure.
  } finally {
    clearing.value = false
  }
}

function openComposer() {
  composerOpen.value = true
  friendQuery.value = ''
  void loadFriends()
}

async function loadFriends() {
  friendsLoading.value = true
  friendsError.value = false
  try {
    friends.value = await listFriends()
  } catch {
    friendsError.value = true
  } finally {
    friendsLoading.value = false
  }
}

async function startChat(person) {
  composerOpen.value = false
  await selectPeer(peerOf(person), { updateUrl: true })
}

function goToContacts() {
  composerOpen.value = false
  router.push({ path: '/community', query: { tab: 'friends' } })
}

function closeChat(updateUrl = true) {
  infoOpen.value = false
  searchOpen.value = false
  emojiOpen.value = false
  selectionMode.value = false
  selectedIds.value = []
  quoting.value = null
  selectionMode.value = false
  selectedIds.value = []
  quoting.value = null
  if (recording.value) stopRecording(true)
  clearVoice()
  historyRequest?.abort()
  messageLoading.value = false
  olderLoading.value = false
  activePeer.value = null
  messages.value = []
  hasMore.value = false
  chatError.value = ''
  clearTimeout(readTimer)
  readTimer = null
  if (updateUrl && route.query.user) router.replace({ path: '/messages' })
}

async function openFromQuery(value) {
  const id = Number(value)
  if (!Number.isSafeInteger(id) || id <= 0 || id === activePeer.value?.id) return
  try {
    const person = await getPerson(id)
    await selectPeer(peerOf(person), { updateUrl: false })
  } catch {
    // The API client explains the failed contact lookup.
  }
}

function fileLabel(fileName) {
  const extension = (fileName || '').split('.').pop()?.toLowerCase()
  return extension ? extension.toUpperCase() : 'FILE'
}

function formatSize(bytes) {
  if (!Number.isFinite(bytes) || bytes <= 0) return '0 B'
  const units = ['B', 'KB', 'MB', 'GB']
  let value = bytes
  let unit = 0
  while (value >= 1024 && unit < units.length - 1) {
    value /= 1024
    unit += 1
  }
  return `${unit === 0 || value >= 10 ? Math.round(value) : value.toFixed(1)} ${units[unit]}`
}

function formatConversationTime(value) {
  const date = parseDate(value)
  if (!date) return ''
  const now = new Date()
  if (sameDay(date, now)) return clock(date)
  const yesterday = new Date(now)
  yesterday.setDate(now.getDate() - 1)
  if (sameDay(date, yesterday)) return '昨天'
  if (date.getFullYear() === now.getFullYear()) return date.getMonth() + 1 + '月' + date.getDate() + '日'
  return date.getFullYear() + '年' + (date.getMonth() + 1) + '月' + date.getDate() + '日'
}

function formatMessageTime(value) {
  const date = parseDate(value)
  if (!date) return ''
  const now = new Date()
  if (sameDay(date, now)) return clock(date)
  return (date.getMonth() + 1) + '月' + date.getDate() + '日 ' + clock(date)
}

function parseDate(value) {
  if (!value) return null
  const date = new Date(String(value).replace(' ', 'T'))
  return Number.isNaN(date.getTime()) ? null : date
}

function sameDay(first, second) {
  return first.getFullYear() === second.getFullYear() && first.getMonth() === second.getMonth()
    && first.getDate() === second.getDate()
}

function clock(date) {
  return String(date.getHours()).padStart(2, '0') + ':' + String(date.getMinutes()).padStart(2, '0')
}

watch(() => route.query.user, value => {
  if (!value) {
    if (activePeer.value) closeChat(false)
    return
  }
  const id = Number(value)
  if (Number.isSafeInteger(id) && id > 0 && id !== activePeer.value?.id) void openFromQuery(id)
})

function onEscape(event) {
  if (event.key === 'Escape') closeContextMenu()
}

onMounted(() => {
  window.addEventListener('keydown', onEscape)
  unsubscribe = messageStore.subscribe(onEvent)
  messageStore.ensureConnected()
  void loadConversations()
  const id = Number(route.query.user)
  if (Number.isSafeInteger(id) && id > 0) void openFromQuery(id)
})

onBeforeUnmount(() => {
  window.removeEventListener('keydown', onEscape)
  mounted = false
  if (recording.value) stopRecording(true)
  clearVoice()
  conversationRequest?.abort()
  historyRequest?.abort()
  searchRequest?.abort()
  clearTimeout(readTimer)
  if (highlightTimer) clearTimeout(highlightTimer)
  unsubscribe?.()
})
</script>

<style scoped>
.message-center {
  display: flex;
  flex-direction: column;
  height: calc(100dvh - 96px);
  max-width: 1080px;
  min-height: 420px;
  margin: 0 auto;
  color: #25374d;
}
.page-head {
  display: flex;
  flex: none;
  align-items: flex-end;
  justify-content: space-between;
  gap: 16px;
  padding: 4px 0 16px;
}
.heading-copy { min-width: 0; }
.eyebrow { color: #2e5fa3; font-size: 12px; font-weight: 650; letter-spacing: .12em; }
h1 { margin: 3px 0 5px; font-size: clamp(24px, 3vw, 31px); font-weight: 680; line-height: 1.2; letter-spacing: -.035em; }
.lede { color: #5a6b80; font-size: 13px; }
.messenger {
  display: grid;
  flex: 1;
  grid-template-columns: 300px minmax(0, 1fr);
  min-height: 0;
  overflow: hidden;
  background: #fff;
  border: 1px solid #dce4ec;
  border-radius: 14px;
}
.conversation-pane {
  display: flex;
  flex-direction: column;
  min-height: 0;
  background: #fbfcfe;
  border-right: 1px solid #e5eaf0;
}
.list-head { display: flex; flex: none; align-items: center; gap: 8px; padding: 12px 12px 8px; }
.list-head .el-input { min-width: 0; }
.connection {
  display: flex;
  flex: none;
  align-items: center;
  gap: 6px;
  padding: 0 14px 9px;
  color: #8a7651;
  font-size: 11px;
}
.connection-dot { width: 7px; height: 7px; border-radius: 50%; background: #b58a3a; }
.connection.online { color: #367154; }
.connection.online .connection-dot { background: #3c8a63; }
.conversation-list { flex: 1; min-height: 0; overflow-y: auto; padding: 2px 8px 8px; }
.conversation {
  display: flex;
  align-items: center;
  gap: 9px;
  width: 100%;
  padding: 10px;
  border: 0;
  border-radius: 10px;
  background: transparent;
  color: inherit;
  text-align: left;
  cursor: pointer;
}
.conversation.active { background: #eaf1f9; }
.conversation-copy { display: flex; flex: 1; flex-direction: column; min-width: 0; gap: 4px; }
.conversation-head { display: flex; align-items: baseline; justify-content: space-between; gap: 6px; }
.conversation-head strong { flex: 1; overflow: hidden; font-size: 14px; font-weight: 620; text-overflow: ellipsis; white-space: nowrap; }
.conversation-marks { display: inline-flex; flex: none; align-items: center; gap: 2px; color: #95a3b3; font-size: 12px; }
.conversation-head time { flex: none; color: #95a3b3; font-size: 10px; }
.conversation-preview { overflow: hidden; color: #7b8a9c; font-size: 12px; text-overflow: ellipsis; white-space: nowrap; }
.unread-badge {
  display: grid;
  place-items: center;
  flex: none;
  min-width: 18px;
  height: 18px;
  padding: 0 5px;
  border-radius: 9px;
  background: #b23b3b;
  color: #fff;
  font-size: 10px;
  font-weight: 650;
}
.list-status {
  display: grid;
  flex: 1;
  align-content: center;
  justify-items: center;
  gap: 10px;
  padding: 24px 18px;
  color: #738296;
  font-size: 13px;
  text-align: center;
}
.list-status.empty .el-icon { color: #8aa4c2; }
.chat-pane { position: relative; display: flex; flex-direction: column; min-width: 0; min-height: 0; }
.chat-head {
  display: flex;
  flex: none;
  align-items: center;
  gap: 10px;
  padding: 12px 16px;
  border-bottom: 1px solid #e5eaf0;
}
.back-button {
  display: none;
  align-items: center;
  justify-content: center;
  width: 32px;
  height: 32px;
  margin-left: -6px;
  border: 0;
  border-radius: 8px;
  background: transparent;
  color: #40658e;
  cursor: pointer;
}
.peer-copy { display: flex; flex-direction: column; min-width: 0; gap: 2px; }
.peer-copy strong { overflow: hidden; font-size: 15px; font-weight: 650; text-overflow: ellipsis; white-space: nowrap; }
.peer-copy small { color: #8190a2; font-size: 11px; }
.chat-actions { display: flex; align-items: center; gap: 8px; margin-left: auto; }
.context-backdrop { position: fixed; inset: 0; z-index: 3000; background: transparent; }
.message-context-menu {
  position: fixed;
  z-index: 3001;
  display: grid;
  width: 184px;
  max-height: calc(100dvh - 16px);
  padding: 6px;
  overflow-y: auto;
  border: 1px solid #e6e9ee;
  border-radius: 9px;
  background: #fff;
  box-shadow: 0 10px 30px #24364d24, 0 1px 4px #24364d12;
}
.message-context-menu button {
  display: flex;
  align-items: center;
  width: 100%;
  min-height: 34px;
  padding: 0 12px;
  border: 0;
  border-radius: 6px;
  background: transparent;
  color: #2f3f53;
  font: inherit;
  font-size: 13px;
  text-align: left;
  cursor: pointer;
}
.message-context-menu button:hover { background: #f3f5f8; }
.message-context-menu .context-danger { color: #b23b3b; }
.context-sep { height: 1px; margin: 5px 8px; background: #eef1f4; }
.enlarged-text {
  max-height: 65dvh;
  overflow: auto;
  color: #26384d;
  font-size: 16px;
  line-height: 1.9;
  overflow-wrap: anywhere;
  white-space: pre-wrap;
}
.not-friend { padding: 2px 8px; border-radius: 10px; background: #fbf1e3; color: #99642a; font-size: 11px; }
.chat-more {
  display: grid;
  place-items: center;
  width: 30px;
  height: 30px;
  border: 0;
  border-radius: 7px;
  background: transparent;
  color: #6d7d90;
  cursor: pointer;
}
.chat-more:hover { background: #f1f4f8; }
.chat-info {
  position: absolute;
  z-index: 20;
  top: 0;
  right: 0;
  bottom: 0;
  display: flex;
  flex-direction: column;
  width: 262px;
  padding: 0 16px;
  overflow-y: auto;
  border-left: 1px solid #e3e8ef;
  background: #fff;
  box-shadow: -8px 0 24px #24364d14;
}
.chat-info-head { display: flex; align-items: center; justify-content: space-between; padding: 12px 0 10px; border-bottom: 1px solid #eef1f4; }
.chat-info-head h2 { color: #2f435a; font-size: 15px; font-weight: 650; }
.chat-info-person { display: flex; align-items: center; gap: 10px; padding: 16px 0; border-bottom: 1px solid #eef1f4; }
.chat-info-person > div { display: flex; flex-direction: column; min-width: 0; gap: 2px; }
.chat-info-person strong { overflow: hidden; color: #2f435a; font-size: 14px; font-weight: 650; text-overflow: ellipsis; white-space: nowrap; }
.chat-info-person small { color: #8997a8; font-size: 11px; }
.info-action {
  display: flex;
  align-items: center;
  gap: 8px;
  width: 100%;
  padding: 13px 0;
  border: 0;
  border-bottom: 1px solid #eef1f4;
  background: transparent;
  color: #34485f;
  font: inherit;
  font-size: 13px;
  text-align: left;
  cursor: pointer;
}
.info-action:hover { color: #2e5fa3; }
.info-chevron { margin-left: auto; color: #a1adba; }
.info-row {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 12px;
  padding: 11px 0;
  border-bottom: 1px solid #eef1f4;
  color: #34485f;
  font-size: 13px;
}
.info-clear {
  margin-top: auto;
  padding: 16px 0;
  border: 0;
  border-top: 1px solid #eef1f4;
  background: transparent;
  color: #b23b3b;
  font: inherit;
  font-size: 13px;
  text-align: center;
  cursor: pointer;
}
.info-clear:disabled { cursor: wait; opacity: .55; }
.message-scroll {
  display: flex;
  flex: 1;
  flex-direction: column;
  gap: 10px;
  min-height: 0;
  overflow-y: auto;
  padding: 16px 18px;
  background: #fcfdff;
}
.load-more {
  align-self: center;
  padding: 3px 10px;
  border: 0;
  border-radius: 12px;
  background: #eef3f8;
  color: #4d6d91;
  font: inherit;
  font-size: 11px;
  cursor: pointer;
}
.load-more:disabled { cursor: wait; opacity: .65; }
.chat-status { display: grid; flex: 1; align-content: center; justify-items: center; gap: 8px; color: #8a98aa; font-size: 13px; text-align: center; }
.chat-status.empty .el-icon { color: #8aa4c2; }
.message-scroll.selecting { padding-inline: 46px; }
.message-row { position: relative; display: flex; flex-direction: column; align-items: flex-start; max-width: min(82%, 620px); gap: 3px; }
.message-row.mine { align-self: flex-end; align-items: flex-end; }
.message-check { position: absolute; top: 6px; left: -30px; }
.message-row.mine .message-check { right: -30px; left: auto; }
.message-quote {
  max-width: min(280px, 62vw);
  padding: 6px 9px;
  border-left: 3px solid #a8bdd5;
  border-radius: 4px;
  background: #f0f4f9;
  color: #60748a;
  font-size: 12px;
  line-height: 1.55;
  overflow-wrap: anywhere;
}
.message-quote strong { display: block; margin-bottom: 1px; color: #3d5b78; font-size: 11px; }
.mine .message-quote { border-left-color: #b9d0eb; background: #f3f7fc; color: #5d6e80; }
.mine .message-quote strong { color: #345b86; }
.selection-bar {
  display: flex;
  flex: none;
  align-items: center;
  flex-wrap: wrap;
  gap: 6px;
  padding: 7px 14px 0;
  color: #52677e;
  font-size: 12px;
}
.selection-bar > span { margin-right: 4px; }
.selection-bar .el-button { margin-left: 0; }
.composer-quote {
  display: flex;
  align-items: center;
  gap: 8px;
  padding: 6px 8px;
  border-left: 3px solid #9db8d4;
  border-radius: 5px;
  background: #f3f7fb;
  color: #5b7087;
  font-size: 12px;
}
.composer-quote > div { display: flex; flex: 1; flex-direction: column; min-width: 0; gap: 1px; }
.composer-quote strong { color: #3f5f7f; font-size: 11px; }
.composer-quote span { overflow: hidden; text-overflow: ellipsis; white-space: nowrap; }
.composer-quote button { border: 0; background: transparent; color: #8294a8; font-size: 17px; cursor: pointer; }
.dialog-tip { margin-bottom: 10px; color: #6e8094; font-size: 13px; }
.translation-text { max-height: 55dvh; overflow: auto; color: #2f435a; font-size: 14px; line-height: 1.8; overflow-wrap: anywhere; white-space: pre-wrap; }
.site-search-results { max-height: 52dvh; margin-top: 12px; overflow-y: auto; }
.site-search-results h3 { margin: 12px 0 4px; color: #6d8095; font-size: 12px; font-weight: 600; }
.site-search-item { display: flex; flex-direction: column; gap: 2px; padding: 7px 2px; border-bottom: 1px solid #eef1f4; color: inherit; text-decoration: none; }
.site-search-item strong { color: #3b536c; font-size: 13px; }
.site-search-item span { color: #8191a2; font-size: 11px; line-height: 1.5; }
.remind-options { display: flex; flex-direction: column; align-items: flex-start; gap: 10px; }
.message-row.highlighted .bubble,
.message-row.highlighted .file-bubble,
.message-row.highlighted .message-voice,
.message-row.highlighted .image-bubble {
  outline: 2px solid #8fb4dd;
  outline-offset: 2px;
}
.bubble {
  padding: 9px 12px;
  border-radius: 12px 12px 12px 4px;
  background: #eef2f7;
  color: #26384d;
  font-size: 14px;
  line-height: 1.65;
  overflow-wrap: anywhere;
  white-space: pre-wrap;
}
.mine .bubble { border-radius: 12px 12px 4px 12px; background: #2e5fa3; color: #fff; }
.message-meta { display: flex; gap: 7px; color: #9aa7b6; font-size: 10px; }
.recall-note {
  padding: 3px 8px;
  border-radius: 10px;
  background: #f1f4f8;
  color: #8391a2;
  font-size: 12px;
  font-style: italic;
}
.recall-button {
  padding: 0 2px;
  border: 0;
  background: transparent;
  color: #98a5b4;
  font: inherit;
  font-size: 10px;
  cursor: pointer;
}
.image-bubble { display: flex; max-width: min(260px, 62vw); }
.attachment-caption {
  max-width: min(260px, 62vw);
  margin-top: 2px;
  color: #56687d;
  font-size: 13px;
  line-height: 1.6;
  overflow-wrap: anywhere;
  white-space: pre-wrap;
}
.file-bubble {
  display: flex;
  align-items: center;
  gap: 9px;
  max-width: min(300px, 68vw);
  padding: 9px 10px;
  border: 1px solid #dbe3ec;
  border-radius: 12px;
  background: #fff;
}
.file-type {
  display: grid;
  place-items: center;
  flex: none;
  width: 40px;
  height: 46px;
  border-radius: 7px;
  background: #edf3fa;
  color: #35618f;
  font-size: 10px;
  font-weight: 700;
}
.file-copy { display: flex; flex: 1; flex-direction: column; min-width: 0; gap: 2px; }
.file-copy strong { overflow: hidden; color: #33485f; font-size: 12px; font-weight: 600; text-overflow: ellipsis; white-space: nowrap; }
.file-copy small { color: #8b99aa; font-size: 10px; }
.file-download {
  display: grid;
  place-items: center;
  flex: none;
  width: 28px;
  height: 28px;
  border: 0;
  border-radius: 7px;
  background: #f1f5fa;
  color: #38618b;
  cursor: pointer;
}
.file-download:disabled { cursor: wait; opacity: .55; }
.chat-warning { flex: none; margin: 0; padding: 7px 16px 0; color: #a34a42; font-size: 12px; }
.composer {
  display: grid;
  flex: none;
  gap: 3px;
  margin: 8px 14px 14px;
  padding: 8px 10px 7px;
  border: 1px solid #e0e5ec;
  border-radius: 10px;
  background: #fff;
  box-shadow: 0 1px 2px #25374d08;
}
.composer-input :deep(.el-textarea__inner) {
  min-height: 78px !important;
  padding: 6px 6px 2px;
  border: 0;
  border-radius: 0;
  background: transparent;
  box-shadow: none;
  color: #25374d;
  font-size: 14px;
  line-height: 1.7;
  resize: none;
}
.composer-input :deep(.el-textarea__inner:focus) { box-shadow: none; }
.composer-input :deep(.el-input__count) { display: none; }
.attachment-input { display: none; }
.composer-bar { display: flex; align-items: center; justify-content: space-between; gap: 8px; min-height: 30px; }
.composer-tools { display: flex; align-items: center; flex-wrap: wrap; gap: 2px; }
.tool-icon {
  display: grid;
  place-items: center;
  width: 30px;
  height: 30px;
  padding: 0;
  border: 0;
  border-radius: 6px;
  background: transparent;
  color: #5f6b7a;
  font-size: 18px;
  cursor: pointer;
}
.tool-icon:disabled { cursor: not-allowed; opacity: .38; }
.tool-icon.recording { color: #b23b3b; }
.emoji-trigger { font-family: var(--el-font-family); font-size: 21px; line-height: 1; }
.emoji-grid { display: grid; grid-template-columns: repeat(6, 1fr); gap: 2px; }
.emoji-item {
  display: grid;
  place-items: center;
  width: 34px;
  height: 34px;
  padding: 0;
  border: 0;
  border-radius: 6px;
  background: transparent;
  font-size: 20px;
  cursor: pointer;
}
.upload-state { margin-left: 4px; color: #6f8094; font-size: 11px; }
.composer-send { display: flex; align-items: center; gap: 8px; margin-left: auto; }
.send-button { min-width: 66px; height: 30px; padding: 0 14px; border-radius: 6px; font-size: 13px; }
.send-button.is-disabled { border-color: #eff1f3; background: #eff1f3; color: #a8b0ba; }
.recording-bar {
  display: flex;
  align-items: center;
  flex-wrap: wrap;
  gap: 8px;
  padding: 7px 10px;
  border: 1px solid #efd3d3;
  border-radius: 10px;
  background: #fff8f8;
  color: #8f3b3b;
  font-size: 12px;
}
.recording-dot {
  width: 8px;
  height: 8px;
  border-radius: 50%;
  background: #c34646;
  animation: recording-pulse 1.1s ease-in-out infinite;
}
.recording-note { color: #a98282; font-size: 11px; }
.recording-bar .el-button { margin-left: 0; }
.recording-bar .el-button:first-of-type { margin-left: auto; }
.voice-preview {
  display: flex;
  align-items: center;
  flex-wrap: wrap;
  gap: 8px;
  padding: 7px 10px;
  border: 1px solid #dbe3ec;
  border-radius: 10px;
  background: #f8fafc;
}
.voice-preview audio { flex: 1; min-width: 170px; height: 34px; }
.voice-preview small { color: #6f8094; font-size: 11px; font-variant-numeric: tabular-nums; }
@keyframes recording-pulse {
  0%, 100% { opacity: 1; transform: scale(1); }
  50% { opacity: .45; transform: scale(.8); }
}
@media (prefers-reduced-motion: reduce) {
  .recording-dot { animation: none; }
}
.chat-placeholder { display: grid; flex: 1; align-content: center; justify-items: center; gap: 8px; padding: 30px; color: #8493a5; text-align: center; }
.chat-placeholder .el-icon { color: #8aa4c2; }
.chat-placeholder h2 { color: #415a76; font-size: 18px; font-weight: 620; }
.chat-placeholder p { max-width: 360px; font-size: 13px; line-height: 1.7; }
.friend-list { max-height: min(45dvh, 380px); margin-top: 10px; overflow-y: auto; }
.friend-row {
  display: flex;
  align-items: center;
  gap: 10px;
  width: 100%;
  padding: 10px 2px;
  border: 0;
  border-bottom: 1px solid #eef1f4;
  background: transparent;
  color: inherit;
  text-align: left;
  cursor: pointer;
}
.friend-row:last-child { border-bottom: 0; }
.friend-row > span { display: flex; flex: 1; flex-direction: column; min-width: 0; gap: 2px; }
.friend-row strong { font-size: 14px; font-weight: 620; }
.friend-row small { color: #8291a3; font-size: 11px; }
.friend-row .el-icon { color: #8fa1b5; }
.friend-status { display: grid; justify-items: center; gap: 8px; padding: 28px 10px; color: #77879a; font-size: 13px; text-align: center; }
.search-status { display: grid; justify-items: center; gap: 8px; padding: 28px 10px; color: #77879a; font-size: 13px; text-align: center; }
.search-results { max-height: 48dvh; margin-top: 10px; overflow-y: auto; }
.search-result {
  display: grid;
  grid-template-columns: auto minmax(0, 1fr) auto;
  align-items: baseline;
  gap: 8px;
  width: 100%;
  padding: 10px 2px;
  border: 0;
  border-bottom: 1px solid #eef1f4;
  background: transparent;
  color: inherit;
  text-align: left;
  cursor: pointer;
}
.search-result:hover { background: #f7f9fc; }
.search-kind { flex: none; padding: 1px 6px; border-radius: 8px; background: #eef3f9; color: #40688f; font-size: 10px; }
.search-text { overflow: hidden; color: #3e536a; font-size: 12px; text-overflow: ellipsis; white-space: nowrap; }
.search-result time { color: #95a3b3; font-size: 10px; white-space: nowrap; }
@media (hover: hover) {
  .conversation:not(.active):hover { background: #f1f5fa; }
  .friend-row:hover { background: #f7f9fc; }
}
@media (max-width: 820px) {
  .message-center { height: calc(100dvh - var(--toolbar-height) - 78px); min-height: 0; }
  .page-head { align-items: flex-start; flex-wrap: wrap; gap: 8px; padding-bottom: 10px; }
  .lede { display: none; }
  .messenger { grid-template-columns: 1fr; border: 0; border-radius: 0; background: transparent; }
  .conversation-pane {
    border: 1px solid #dce4ec;
    border-radius: 14px;
    background: #fff;
  }
  .chat-pane { display: none; border: 1px solid #dce4ec; border-radius: 14px; background: #fff; overflow: hidden; }
  .chat-info { width: 100%; border-left: 0; }
  .messenger.chat-open .conversation-pane { display: none; }
  .messenger.chat-open .chat-pane { display: flex; }
  .back-button { display: inline-flex; }
  .composer { margin: 8px 8px 10px; padding: 7px 8px 6px; }
  .composer-input :deep(.el-textarea__inner) { min-height: 64px !important; }
  .tool-icon { width: 34px; height: 34px; }
  .send-button { height: 32px; }
  .file-bubble, .attachment-caption { max-width: 74vw; }
}
@media (max-width: 480px) {
  .message-center { height: calc(100dvh - var(--toolbar-height) - 78px); }
  .composer { padding-inline: 10px; }
  .message-scroll { padding-inline: 12px; }
  .bubble { font-size: 13px; }
}
</style>
