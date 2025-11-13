<template>
  <div class="chat-layout">
    <aside class="sidebar" :class="{ open: isSidebarOpen }">
      <div class="sidebar-header">
        <span class="sidebar-title">HKU Campus Companion</span>
        <button class="sidebar-close" @click="closeSidebar" aria-label="Close sidebar">&times;</button>
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
          <button class="menu-button" @click="toggleSidebar" aria-label="Toggle sidebar">&#9776;</button>
          <button class="back-button" @click="goBack">返回</button>
        </div>
        <h1 class="title">HKU Campus Companion</h1>
        <div class="chat-meta">
          <span class="chat-label">Chat ID</span>
          <span class="chat-value">{{ chatId }}</span>
        </div>
      </div>
      <div class="content-wrapper">
        <div class="chat-area">
          <ChatRoom
            :messages="messages"
            :connection-status="connectionStatus"
            ai-type="love"
            @send-message="sendMessage"
          />
        </div>
      </div>
    </div>
  </div>
</template>

<script setup>
import { ref, onMounted, onBeforeUnmount } from 'vue'
import { useRouter } from 'vue-router'
import { useHead } from '@vueuse/head'
import ChatRoom from '../components/ChatRoom.vue'
import { chatWithLoveApp } from '../api'

useHead({
  title: 'HKU Campus Companion - HKU AI Agent Platform',
  meta: [
    {
      name: 'description',
      content: 'HKU Campus Companion is the HKU AI Agent platform assistant for campus enquiries, offering guidance on courses, administration, and student life.'
    },
    {
      name: 'keywords',
      content: 'HKU, campus assistant, course advisory, administration, student life, AI chat, HKU AI Agent'
    }
  ]
})

const DESKTOP_BREAKPOINT = 1024
const STORAGE_KEY = 'hku_campus_chat_history'
const WELCOME_MESSAGE = '欢迎来到 HKU Campus Companion。\n\n我可以协助你查询课程安排、行政流程、校园设施与最新公告。\n\n告诉我你遇到的问题或想了解的主题，我们一起整理答案。'

const router = useRouter()
const isSidebarOpen = ref(false)
const isDesktop = ref(false)
const conversations = ref([])
const activeConversationId = ref('')
const messages = ref([])
const chatId = ref('')
const connectionStatus = ref('disconnected')
let eventSource = null

const generateChatId = () => `campus_${Math.random().toString(36).substring(2, 10)}`
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
    updatedAt: typeof raw?.updatedAt === 'number' ? raw.updatedAt : Date.now()
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
}

const syncActiveConversation = ({ userMessage } = {}) => {
  const index = conversations.value.findIndex((item) => item.id === activeConversationId.value)
  if (index === -1) {
    return
  }
  const conversation = conversations.value[index]
  conversation.messages = messages.value.map((msg) => ({ ...msg }))
  conversation.updatedAt = Date.now()

  if (userMessage) {
    const trimmed = userMessage.trim()
    if (trimmed && (!conversation.title || conversation.title === 'New chat')) {
      conversation.title = trimmed.length > 36 ? `${trimmed.slice(0, 36)}...` : trimmed
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
  return new Date(timestamp).toLocaleString('en-HK', {
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
    updatedAt: Date.now()
  }
}

const createNewConversation = () => {
  if (connectionStatus.value === 'connecting') {
    return
  }
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

  const aiMessageIndex = messages.value.length
  addMessage('', false)

  connectionStatus.value = 'connecting'
  eventSource = chatWithLoveApp(message, chatId.value)

  eventSource.onmessage = (event) => {
    const data = event.data
    if (data && data !== '[DONE]') {
      if (aiMessageIndex < messages.value.length) {
        let chunk = data
        const currentContent = messages.value[aiMessageIndex].content
        const needsSpace = currentContent && !/\s$/.test(currentContent) && !/^[\s.,;:!?)/-]/.test(chunk)
        if (needsSpace) {
          chunk = ' ' + chunk
        }
        messages.value[aiMessageIndex].content += chunk
        syncActiveConversation()
      }
    }

    if (data === '[DONE]') {
      connectionStatus.value = 'disconnected'
      eventSource?.close()
      eventSource = null
      syncActiveConversation()
    }
  }

  eventSource.onerror = (error) => {
    console.error('SSE Error:', error)
    connectionStatus.value = 'error'
    eventSource?.close()
    eventSource = null
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
  background: rgba(15, 118, 110, 0.24);
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
  background: #0b5ba6;
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

@media (min-width: 768px) {
  .chat-area {
    padding: 24px 40px;
  }
}

button {
  font-family: inherit;
}
</style>
