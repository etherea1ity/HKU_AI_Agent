<template>
  <div class="chat-layout">
    <aside class="sidebar" :class="{ open: isSidebarOpen }">
      <div class="sidebar-header">
        <span class="sidebar-title">AI超级智能体</span>
        <button class="sidebar-close" @click="closeSidebar" aria-label="Close sidebar">×</button>
      </div>
      <button class="sidebar-new-chat" @click="createNewConversation">+ New Chat</button>
      <div class="conversation-list">
        <div
          v-for="conversation in conversations"
          :key="conversation.id"
          :class="['conversation-item', { active: conversation.id === activeConversationId }]"
          @click="openConversation(conversation.id)"
        >
          <div class="conversation-info">
            <div class="conversation-title">{{ conversation.title }}</div>
            <div class="conversation-meta">{{ formatTimestamp(conversation.updatedAt) }}</div>
          </div>
          <button
            class="conversation-menu"
            @click.stop="confirmDeleteConversation(conversation.id)"
            aria-label="Delete conversation"
          >delete</button>
        </div>
      </div>
    </aside>
    <div
      class="sidebar-backdrop"
      v-if="isSidebarOpen && !isDesktop"
      @click="closeSidebar"
    ></div>
  <div :class="['main-panel', { shifted: isDesktop && isSidebarOpen }]">
      <div class="header">
        <div class="header-left">
          <button class="menu-button" @click="toggleSidebar" aria-label="Toggle sidebar">☰</button>
          <button class="back-button" @click="goBack">返回</button>
        </div>
        <h1 class="title">AI超级智能体</h1>
        <div class="chat-meta">
          <span class="chat-label">Session</span>
          <span class="chat-value">{{ chatId }}</span>
        </div>
      </div>
      <div class="content-wrapper">
        <div class="chat-area">
                <div class="deep-panel">
                  <DeepThinkingPanel
                    :visible="deepPanelVisible"
                    :is-thinking="isThinking"
                    :header="thinkingHeader"
                    :subtitle="thinkingSubtitle"
                    :steps="thinkingSteps"
                    :rag-sources="ragSources"
                    :collapsed="isPanelCollapsed"
                    @toggle-collapse="toggleThinkingPanel"
                  />
                </div>
                <div class="chat-room-wrapper">
                  <ChatRoom
                    :messages="messages"
                    :connection-status="connectionStatus"
                    ai-type="super"
                    @send-message="sendMessage"
                  />
                </div>
              </div>
      </div>
    </div>
  </div>
</template>

<script setup>
import { ref, computed, onMounted, onBeforeUnmount } from 'vue'
import { useRouter } from 'vue-router'
import { useHead } from '@vueuse/head'
import ChatRoom from '../components/ChatRoom.vue'
import DeepThinkingPanel from '../components/DeepThinkingPanel.vue'
import { chatWithManus } from '../api'

useHead({
  title: 'AI超级智能体 - HKU AI Agent 平台',
  meta: [
    {
      name: 'description',
      content: 'AI超级智能体是 HKU AI Agent 平台的全能助手，能解答各类专业问题，提供精准建议和解决方案'
    },
    {
      name: 'keywords',
      content: 'AI超级智能体,智能助手,专业问答,AI问答,专业建议,HKU,AI智能体'
    }
  ]
})

const DESKTOP_BREAKPOINT = 1024
const STORAGE_KEY = 'hku_super_chat_history'
const WELCOME_MESSAGE = '你好，我是 AI 超级智能体。\n\n我可以协助分析复杂问题、整合多源信息并给出执行建议。\n\n告诉我你的目标或遇到的挑战，我们一起拆解并规划下一步。'

const SSE_TAGS = {
  THINKING_START: '[THINKING_START]',
  THINKING_END: '[THINKING_END]',
  TOOL_CALL: '[TOOL_CALL]',
  RAG_CONTEXT: '[RAG_CONTEXT]'
}

const router = useRouter()
const isSidebarOpen = ref(false)
const isDesktop = ref(false)
const conversations = ref([])
const activeConversationId = ref('')
const messages = ref([])
const chatId = ref('')
const connectionStatus = ref('disconnected')
const isThinking = ref(false)
const thinkingSteps = ref([])
const ragSources = ref([])
const thinkingHeader = ref('')
const thinkingSubtitle = ref('')
const isPanelCollapsed = ref(false)
let eventSource = null

const createEmptyDeepThinkingState = () => ({
  steps: [],
  ragSources: [],
  header: '',
  subtitle: '',
  collapsed: false
})

// 默认显示深度思考面板（可折叠），但用户可收起
const deepPanelAlwaysVisible = ref(true)
const deepPanelVisible = computed(() => {
  return (
    deepPanelAlwaysVisible.value ||
    isThinking.value ||
    thinkingSteps.value.length > 0 ||
    ragSources.value.length > 0 ||
    Boolean(thinkingHeader.value)
  )
})

const toggleThinkingPanel = () => {
  isPanelCollapsed.value = !isPanelCollapsed.value
}

function applyDeepThinkingState(state) {
  const data = state || createEmptyDeepThinkingState()
  thinkingSteps.value = Array.isArray(data.steps) ? data.steps.map((item) => ({ ...item })) : []
  ragSources.value = Array.isArray(data.ragSources) ? data.ragSources.map((item) => ({ ...item })) : []
  thinkingHeader.value = data.header || ''
  thinkingSubtitle.value = data.subtitle || ''
  isPanelCollapsed.value = Boolean(data.collapsed)
}

const captureDeepThinkingState = () => ({
  steps: thinkingSteps.value.map((item) => ({ ...item })),
  ragSources: ragSources.value.map((item) => ({ ...item })),
  header: thinkingHeader.value,
  subtitle: thinkingSubtitle.value,
  collapsed: isPanelCollapsed.value
})

const clearDeepThinkingState = () => {
  isThinking.value = false
  thinkingSteps.value = []
  ragSources.value = []
  thinkingHeader.value = ''
  thinkingSubtitle.value = ''
}

const parseRagSources = (raw) => {
  if (!raw) {
    return []
  }
  try {
    const parsed = JSON.parse(raw)
    const list = Array.isArray(parsed)
      ? parsed
      : Array.isArray(parsed?.sources)
        ? parsed.sources
        : []

    return list
      .map((item) => ({
        title: String(item?.title || '相关资料'),
        snippet: String(item?.snippet || '').trim(),
        source: item?.source ? String(item.source) : ''
      }))
      .filter((item) => item.title || item.snippet)
  } catch (error) {
    console.warn('Failed to parse RAG payload', error)
    return []
  }
}

const generateChatId = () => `super_${Math.random().toString(36).substring(2, 10)}`
const buildWelcomeMessage = () => WELCOME_MESSAGE

const sanitizeMessage = (raw) => ({
  content: typeof raw?.content === 'string' ? raw.content : '',
  isUser: Boolean(raw?.isUser),
  time: typeof raw?.time === 'number' ? raw.time : Date.now()
})

const sanitizeConversation = (raw) => {
  const id = raw?.id || raw?.chatId || generateChatId()
  const chatIdentifier = raw?.chatId || id
  const messageList = Array.isArray(raw?.messages) ? raw.messages.map(sanitizeMessage) : []
  const messagesWithFallback = messageList.length
    ? messageList
    : [{ content: buildWelcomeMessage(), isUser: false, time: Date.now() }]
  return {
    id,
    chatId: chatIdentifier,
    title: typeof raw?.title === 'string' && raw.title.trim() ? raw.title : 'New chat',
    messages: messagesWithFallback,
    createdAt: typeof raw?.createdAt === 'number' ? raw.createdAt : Date.now(),
    updatedAt: typeof raw?.updatedAt === 'number' ? raw.updatedAt : Date.now(),
    deepThinking: raw?.deepThinking || createEmptyDeepThinkingState()
  }
}

const saveConversations = () => {
  try {
    localStorage.setItem(STORAGE_KEY, JSON.stringify(conversations.value))
  } catch (error) {
    console.error('Failed to persist conversations', error)
  }
}

const setActiveConversation = (conversation) => {
  activeConversationId.value = conversation.id
  chatId.value = conversation.chatId
  messages.value = conversation.messages.map((msg) => ({ ...msg }))
  connectionStatus.value = 'disconnected'
  applyDeepThinkingState(conversation.deepThinking)
}

const syncActiveConversation = ({ userMessage } = {}) => {
  const index = conversations.value.findIndex((item) => item.id === activeConversationId.value)
  if (index === -1) {
    return
  }
  const conversation = conversations.value[index]
  conversation.messages = messages.value.map((msg) => ({ ...msg }))
  conversation.updatedAt = Date.now()
  conversation.deepThinking = captureDeepThinkingState()

  if (userMessage) {
    const trimmed = userMessage.trim()
    if (trimmed && (!conversation.title || conversation.title === 'New chat')) {
      conversation.title = trimmed.length > 36 ? `${trimmed.slice(0, 36)}…` : trimmed
    }
  }

  if (index > 0) {
    conversations.value.splice(index, 1)
    conversations.value.unshift(conversation)
    activeConversationId.value = conversation.id
  }

  saveConversations()
}

const handleResize = () => {
  const desktop = window.innerWidth >= DESKTOP_BREAKPOINT
  if (desktop && !isDesktop.value) {
    isSidebarOpen.value = false
  }
  if (!desktop && isDesktop.value) {
    isSidebarOpen.value = false
  }
  isDesktop.value = desktop
}

const toggleSidebar = () => {
  isSidebarOpen.value = !isSidebarOpen.value
}

const closeSidebar = () => {
  isSidebarOpen.value = false
}

const formatTimestamp = (timestamp) => {
  if (!timestamp) {
    return ''
  }
  return new Date(timestamp).toLocaleString('zh-HK', {
    month: 'short',
    day: 'numeric',
    hour: '2-digit',
    minute: '2-digit'
  })
}

const createConversation = () => {
  const id = generateChatId()
  return {
    id,
    chatId: id,
    title: 'New chat',
    messages: [{ content: buildWelcomeMessage(), isUser: false, time: Date.now() }],
    createdAt: Date.now(),
    updatedAt: Date.now(),
    deepThinking: createEmptyDeepThinkingState()
  }
}

const createNewConversation = () => {
  if (connectionStatus.value === 'connecting') {
    return
  }
  syncActiveConversation()
  if (eventSource) {
    eventSource.close()
    eventSource = null
  }
  const conversation = createConversation()
  conversations.value.unshift(conversation)
  saveConversations()
  setActiveConversation(conversation)
  closeSidebar()
}

const openConversation = (id) => {
  const conversation = conversations.value.find((item) => item.id === id)
  if (!conversation) {
    return
  }
  syncActiveConversation()
  if (eventSource) {
    eventSource.close()
    eventSource = null
  }
  setActiveConversation(conversation)
  closeSidebar()
}

const confirmDeleteConversation = (id) => {
  const conversation = conversations.value.find((item) => item.id === id)
  if (!conversation) {
    return
  }
  const confirmed = window.confirm('Delete this conversation? This action cannot be undone.')
  if (!confirmed) {
    return
  }
  const index = conversations.value.findIndex((item) => item.id === id)
  if (index !== -1) {
    conversations.value.splice(index, 1)
    saveConversations()
  }

  if (activeConversationId.value === id) {
    clearDeepThinkingState()
    if (conversations.value.length) {
      setActiveConversation(conversations.value[0])
    } else {
      const newConversation = createConversation()
      conversations.value.unshift(newConversation)
      saveConversations()
      setActiveConversation(newConversation)
    }
  }
}

const addMessage = (content, isUser) => {
  messages.value.push({
    content,
    isUser,
    time: Date.now()
  })
  syncActiveConversation({ userMessage: isUser ? content : undefined })
}

const sendMessage = (message) => {
  if (!message || !message.trim()) {
    return
  }
  addMessage(message, true)

  if (eventSource) {
    eventSource.close()
    eventSource = null
  }

  clearDeepThinkingState()
  syncActiveConversation()
  const aiMessageIndex = messages.value.length
  addMessage('', false)

  connectionStatus.value = 'connecting'
  eventSource = chatWithManus(message, chatId.value)

  eventSource.onmessage = (event) => {
    const data = event.data
    if (!data) {
      return
    }

    if (data === '[DONE]') {
      connectionStatus.value = 'disconnected'
      eventSource?.close()
      eventSource = null
      syncActiveConversation()
      return
    }

    if (data.startsWith(SSE_TAGS.THINKING_START)) {
      isThinking.value = true
      thinkingSteps.value = []
      ragSources.value = []
      const payload = data.slice(SSE_TAGS.THINKING_START.length).trim()
      thinkingHeader.value = payload || 'AI 深度思考中…'
      thinkingSubtitle.value = 'HKU Manus 正在分析任务'
      isPanelCollapsed.value = false
      connectionStatus.value = 'connected'
      syncActiveConversation()
      return
    }

    if (data.startsWith(SSE_TAGS.THINKING_END)) {
      isThinking.value = false
      const payload = data.slice(SSE_TAGS.THINKING_END.length).trim()
      thinkingSubtitle.value = payload || '思考完成'
      syncActiveConversation()
      return
    }

    if (data.startsWith(SSE_TAGS.TOOL_CALL)) {
      const step = data.slice(SSE_TAGS.TOOL_CALL.length).trim()
      if (step) {
        thinkingSteps.value.push({ content: step, time: Date.now() })
        syncActiveConversation()
      }
      return
    }

    if (data.startsWith(SSE_TAGS.RAG_CONTEXT)) {
      const payload = data.slice(SSE_TAGS.RAG_CONTEXT.length)
      const sources = parseRagSources(payload)
      if (sources.length) {
        ragSources.value = sources
        syncActiveConversation()
      }
      return
    }

    if (aiMessageIndex < messages.value.length) {
      if (isThinking.value) {
        isThinking.value = false
        thinkingSubtitle.value = '输出回复中…'
      }

      messages.value[aiMessageIndex].content += data
      syncActiveConversation()
    }
  }

  eventSource.onerror = (error) => {
    console.error('SSE Error:', error)
    connectionStatus.value = 'error'
    eventSource?.close()
    eventSource = null
    clearDeepThinkingState()
    syncActiveConversation()
  }
}

const goBack = () => {
  router.push('/')
}

onMounted(() => {
  handleResize()
  window.addEventListener('resize', handleResize)

  let stored = []
  try {
    const raw = localStorage.getItem(STORAGE_KEY)
    if (raw) {
      const parsed = JSON.parse(raw)
      if (Array.isArray(parsed)) {
        stored = parsed.map(sanitizeConversation)
      }
    }
  } catch (error) {
    console.error('Failed to restore conversations', error)
  }

  if (!stored.length) {
    stored = [createConversation()]
  }

  conversations.value = stored
  saveConversations()
  setActiveConversation(conversations.value[0])
})

onBeforeUnmount(() => {
  window.removeEventListener('resize', handleResize)
  if (eventSource) {
    eventSource.close()
  }
})
</script>

<style scoped>
.chat-layout {
  display: flex;
  height: 100vh;
  background-color: #f5f7fb;
  color: #0f172a;
}

.sidebar {
  position: fixed;
  top: 0;
  bottom: 0;
  left: 0;
  width: 280px;
  display: flex;
  flex-direction: column;
  background: #0b1f4a;
  color: #f8fafc;
  transform: translateX(-100%);
  transition: transform 0.25s ease;
  z-index: 100;
  box-shadow: 4px 0 16px rgba(15, 23, 42, 0.18);
}

.sidebar.open {
  transform: translateX(0);
}

.sidebar-header {
  display: flex;
  align-items: center;
  justify-content: space-between;
  padding: 20px 24px;
  border-bottom: 1px solid rgba(148, 163, 184, 0.2);
}

.sidebar-title {
  font-size: 16px;
  font-weight: 600;
}

.sidebar-close {
  display: inline-flex;
  align-items: center;
  justify-content: center;
  width: 32px;
  height: 32px;
  background: transparent;
  border: none;
  color: inherit;
  font-size: 22px;
  cursor: pointer;
}

@media (min-width: 1024px) {
  .sidebar-close {
    display: none;
  }
}

.sidebar-new-chat {
  margin: 16px;
  padding: 12px 16px;
  border-radius: 8px;
  border: 1px dashed rgba(255, 255, 255, 0.3);
  background: rgba(15, 23, 42, 0.24);
  color: inherit;
  font-size: 14px;
  cursor: pointer;
  transition: background 0.2s ease, border-color 0.2s ease;
}

.sidebar-new-chat:hover {
  background: rgba(148, 163, 184, 0.15);
  border-color: rgba(255, 255, 255, 0.4);
}

.conversation-list {
  flex: 1;
  overflow-y: auto;
  padding: 0 8px 16px;
}

.conversation-item {
  display: flex;
  align-items: center;
  justify-content: space-between;
  padding: 12px;
  margin: 4px 8px;
  border-radius: 8px;
  cursor: pointer;
  transition: background 0.2s ease;
}

.conversation-item:hover {
  background: rgba(148, 163, 184, 0.16);
}

.conversation-item.active {
  background: rgba(124, 58, 237, 0.24);
}

.conversation-info {
  flex: 1;
  min-width: 0;
  padding-right: 12px;
}

.conversation-title {
  font-size: 14px;
  font-weight: 500;
  white-space: nowrap;
  overflow: hidden;
  text-overflow: ellipsis;
}

.conversation-meta {
  margin-top: 4px;
  font-size: 12px;
  color: rgba(226, 232, 240, 0.7);
}

.conversation-menu {
  display: inline-flex;
  align-items: center;
  justify-content: center;
  width: 32px;
  height: 32px;
  border: none;
  border-radius: 6px;
  background: transparent;
  color: inherit;
  font-size: 18px;
  cursor: pointer;
}

.conversation-menu:hover {
  background: rgba(148, 163, 184, 0.16);
}

.sidebar-backdrop {
  position: fixed;
  inset: 0;
  background: rgba(15, 23, 42, 0.48);
  z-index: 40;
}

@media (min-width: 1024px) {
  .sidebar-backdrop {
    display: none;
  }
}

.main-panel {
  flex: 1;
  display: flex;
  flex-direction: column;
  margin-left: 0;
  transition: margin-left 0.25s ease;
}

@media (min-width: 1024px) {
  .main-panel.shifted {
    margin-left: 280px;
  }
}

.header {
  display: flex;
  align-items: center;
  justify-content: space-between;
  padding: 16px 24px;
  background: #3f51b5;
  color: #f8fafc;
  box-shadow: 0 2px 8px rgba(15, 23, 42, 0.12);
  position: sticky;
  top: 0;
  z-index: 20;
}

.header-left {
  display: flex;
  align-items: center;
  gap: 12px;
}

.menu-button {
  display: inline-flex;
  align-items: center;
  justify-content: center;
  width: 36px;
  height: 36px;
  border-radius: 8px;
  border: none;
  background: rgba(255, 255, 255, 0.2);
  color: inherit;
  font-size: 18px;
  cursor: pointer;
}

.back-button {
  padding: 0 14px;
  height: 36px;
  border-radius: 8px;
  border: none;
  background: rgba(255, 255, 255, 0.2);
  color: inherit;
  font-size: 14px;
  cursor: pointer;
  transition: background 0.2s ease;
}

.back-button:hover {
  background: rgba(255, 255, 255, 0.3);
}

.title {
  flex: 1;
  text-align: center;
  font-size: 18px;
  font-weight: 600;
  margin: 0 16px;
}

@media (min-width: 768px) {
  .title {
    font-size: 20px;
  }
}

.chat-meta {
  display: flex;
  flex-direction: column;
  align-items: flex-end;
  font-size: 12px;
  line-height: 1.4;
}

.chat-label {
  opacity: 0.7;
}

.chat-value {
  font-family: 'SFMono-Regular', Menlo, Monaco, Consolas, 'Liberation Mono', 'Courier New', monospace;
  font-size: 13px;
}

.content-wrapper {
  display: flex;
  flex-direction: column;
  flex: 1;
  min-height: 0;
}

.chat-area {
  flex: 1;
  min-height: 0;
  padding: 16px;
}

.chat-area {
  display: flex;
  flex-direction: column;
}

.deep-panel {
  flex: 0 0 auto;
}

.chat-room-wrapper {
  flex: 1 1 auto;
  min-height: 0;
  overflow: auto;
}

@media (min-width: 768px) {
  .chat-area {
    padding: 24px 40px;
  }
}

button {
  font-family: inherit;
}

</style>