<template>
  <div class="super-agent-container">
    <div class="header">
      <div class="back-button" @click="goBack">返回</div>
      <h1 class="title">AI超级智能体</h1>
      <div class="header-actions">
        <button class="action-button" @click="createNewChat" title="开始新对话">
          ➕ 新对话
        </button>
        <button class="action-button" @click="toggleHistoryPanel" title="查看历史对话">
          📋 历史记录
        </button>
      </div>
    </div>
    
    <!-- 历史对话侧边栏 -->
    <transition name="slide">
      <div v-if="showHistoryPanel" class="history-panel">
        <div class="history-header">
          <h3>历史对话</h3>
          <button class="close-button" @click="toggleHistoryPanel">✕</button>
        </div>
        <div class="history-list">
          <div 
            v-if="chatList.length === 0" 
            class="empty-state"
          >
            暂无历史对话
          </div>
          <div 
            v-for="chat in chatList" 
            :key="chat.id"
            class="history-item"
            :class="{ active: chat.id === chatId }"
            @click="loadChat(chat.id)"
          >
            <div class="history-item-header">
              <div class="history-item-title">{{ chat.title }}</div>
              <button 
                class="delete-button" 
                @click.stop="deleteChat(chat.id)"
                title="删除对话"
              >
                🗑️
              </button>
            </div>
            <div class="history-item-meta">
              <span class="message-count">{{ chat.messageCount }} 条消息</span>
              <span class="last-time">{{ formatTimestamp(chat.lastTime) }}</span>
            </div>
          </div>
        </div>
      </div>
    </transition>
    
    <!-- 遮罩层 -->
    <transition name="fade">
      <div 
        v-if="showHistoryPanel" 
        class="overlay"
        @click="toggleHistoryPanel"
      ></div>
    </transition>
    
    <div class="content-wrapper">
      <div class="chat-area">
        <!-- 思考步骤显示框 -->
        <div v-if="isThinking || thinkingSteps.length > 0" class="thinking-panel">
          <div class="thinking-header">
            <div class="thinking-icon">
              <div v-if="isThinking" class="spinner"></div>
              <div v-else class="check-icon">✓</div>
            </div>
            <span class="thinking-title">{{ isThinking ? 'HkuManus深度思考中...' : '思考完成' }}</span>
          </div>
          <div class="thinking-steps">
            <div 
              v-for="(step, index) in thinkingSteps" 
              :key="step.time"
              class="thinking-step"
              :class="{ 'step-appear': true }"
            >
              <div class="step-indicator">
                <div class="step-dot"></div>
                <div v-if="index < thinkingSteps.length - 1" class="step-line"></div>
              </div>
              <div class="step-content">{{ step.content }}</div>
            </div>
          </div>
        </div>
        
        <ChatRoom 
          :messages="messages" 
          :connection-status="connectionStatus"
          ai-type="super"
          @send-message="sendMessage"
        />
      </div>
    </div>
    
    <div class="footer-container">
      <AppFooter />
    </div>
  </div>
</template>

<script setup>
import { ref, computed, watch, onMounted, onBeforeUnmount } from 'vue'
import { useRouter } from 'vue-router'
import { useHead } from '@vueuse/head'
import ChatRoom from '../components/ChatRoom.vue'
import AppFooter from '../components/AppFooter.vue'
import { chatWithManus } from '../api'

// 设置页面标题和元数据
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

const router = useRouter()
const messages = ref([])
const connectionStatus = ref('disconnected')
const isThinking = ref(false)
const thinkingSteps = ref([])
const showHistoryPanel = ref(false)
let eventSource = null

// === 对话管理系统 ===

// 获取所有对话列表
function getChatList() {
  const list = localStorage.getItem('superAgent_chatList')
  return list ? JSON.parse(list) : []
}

// 保存对话列表
function saveChatList(list) {
  localStorage.setItem('superAgent_chatList', JSON.stringify(list))
}

// 获取当前活跃的 chatId
function getCurrentChatId() {
  return localStorage.getItem('superAgent_currentChatId')
}

// 设置当前活跃的 chatId
function setCurrentChatId(id) {
  localStorage.setItem('superAgent_currentChatId', id)
}

// 生成唯一的 chatId
function generateChatId() {
  return 'chat_' + Date.now() + '_' + Math.random().toString(36).substr(2, 9)
}

// 获取指定对话的消息历史
function getChatMessages(chatId) {
  const messages = localStorage.getItem(`superAgent_messages_${chatId}`)
  return messages ? JSON.parse(messages) : []
}

// 保存指定对话的消息历史
function saveChatMessages(chatId, messages) {
  localStorage.setItem(`superAgent_messages_${chatId}`, JSON.stringify(messages))
  // 同时更新对话列表中的最后更新时间和标题
  updateChatInList(chatId, messages)
}

// 更新对话列表中的对话信息
function updateChatInList(chatId, messages) {
  let list = getChatList()
  const index = list.findIndex(c => c.id === chatId)
  
  // 生成对话标题（使用第一条用户消息）
  const firstUserMsg = messages.find(m => m.isUser)
  const title = firstUserMsg ? firstUserMsg.content.substring(0, 30) : '新对话'
  
  if (index >= 0) {
    list[index] = {
      id: chatId,
      title: title,
      lastTime: Date.now(),
      messageCount: messages.length
    }
  } else {
    list.unshift({
      id: chatId,
      title: title,
      lastTime: Date.now(),
      messageCount: messages.length
    })
  }
  
  saveChatList(list)
}

// 初始化当前对话
const chatId = ref(getCurrentChatId() || generateChatId())

// 加载对话历史
function loadChat(id) {
  // 切换前，先保存当前对话（确保不丢失数据）
  if (messages.value.length > 0 && chatId.value !== id) {
    saveChatMessages(chatId.value, messages.value)
  }
  
  // 切换到新对话
  chatId.value = id
  setCurrentChatId(id)
  messages.value = getChatMessages(id)
  thinkingSteps.value = []
  showHistoryPanel.value = false
}

// 创建新对话
function createNewChat() {
  // 创建新对话前，先保存当前对话
  if (messages.value.length > 0) {
    saveChatMessages(chatId.value, messages.value)
  }
  
  const newChatId = generateChatId()
  chatId.value = newChatId
  setCurrentChatId(newChatId)
  messages.value = []
  thinkingSteps.value = []
  showHistoryPanel.value = false
}

// 删除对话
function deleteChat(id) {
  // 删除消息记录
  localStorage.removeItem(`superAgent_messages_${id}`)
  // 从列表中移除
  let list = getChatList()
  list = list.filter(c => c.id !== id)
  saveChatList(list)
  // 如果删除的是当前对话，创建新对话
  if (chatId.value === id) {
    createNewChat()
  }
}

// 切换历史面板显示
function toggleHistoryPanel() {
  showHistoryPanel.value = !showHistoryPanel.value
}

// 获取对话列表（计算属性）
const chatList = computed(() => {
  return getChatList().sort((a, b) => b.lastTime - a.lastTime)
})

// 监听 messages 变化，自动保存（使用防抖避免频繁保存）
let saveTimer = null
watch(messages, (newMessages) => {
  if (newMessages.length > 0) {
    // 清除之前的定时器
    if (saveTimer) {
      clearTimeout(saveTimer)
    }
    // 延迟保存，避免在流式输出过程中频繁保存不完整的内容
    saveTimer = setTimeout(() => {
      saveChatMessages(chatId.value, newMessages)
      saveTimer = null
    }, 2000) // 2秒后保存（作为备份机制）
  }
}, { deep: true })

// 页面加载时，加载当前对话的历史
onMounted(() => {
  const savedMessages = getChatMessages(chatId.value)
  if (savedMessages.length > 0) {
    messages.value = savedMessages
  } else {
    // 如果是新对话，添加欢迎消息
    addMessage('你好，我是HKU Manus超级智能体。我可以解答各类问题，提供专业建议，请问有什么可以帮助你的吗？', false)
  }
})

// 添加消息到列表
const addMessage = (content, isUser, type = '') => {
  messages.value.push({
    content,
    isUser,
    type,
    time: new Date().getTime()
  })
}

// 发送消息
const sendMessage = (message) => {
  addMessage(message, true, 'user-question')
  
  // 保存用户消息
  saveChatMessages(chatId.value, messages.value)
  
  // 连接SSE
  if (eventSource) {
    eventSource.close()
  }
  
  // 重置思考状态
  isThinking.value = false
  thinkingSteps.value = []
  
  // 设置连接状态
  connectionStatus.value = 'connecting'
  
  // 临时存储
  let currentAiMessage = null; // 当前AI消息的引用
  let messageBuffer = ''; // 用于存储累积的消息内容
  
  eventSource = chatWithManus(message, chatId.value)
  
  // 监听SSE消息
  eventSource.onmessage = (event) => {
    const data = event.data
    
    // 处理思考步骤相关的消息
    if (data.startsWith('[THINKING_START]')) {
      isThinking.value = true
      thinkingSteps.value = []
      connectionStatus.value = 'connected'
      return
    }
    
    if (data.startsWith('[THINKING_END]')) {
      // 思考完成，但保留步骤显示
      isThinking.value = false
      return
    }
    
    if (data.startsWith('[TOOL_CALL]')) {
      const stepInfo = data.substring(11) // 移除 [TOOL_CALL] 前缀
      thinkingSteps.value.push({
        content: stepInfo,
        time: Date.now()
      })
      return
    }
    
    // 处理正常消息
    if (data && data !== '[DONE]') {
      // 如果收到正常消息，说明思考已完成
      if (isThinking.value) {
        isThinking.value = false
      }
      
      // 累积消息内容
      messageBuffer += data
      
      // 如果还没有创建消息气泡，创建一个
      if (!currentAiMessage) {
        currentAiMessage = {
          content: messageBuffer,
          isUser: false,
          type: 'ai-answer',
          time: new Date().getTime()
        }
        messages.value.push(currentAiMessage)
      } else {
        // 更新现有消息气泡的内容
        currentAiMessage.content = messageBuffer
      }
    }
    
    if (data === '[DONE]') {
      // 完成后关闭连接和思考状态
      isThinking.value = false
      connectionStatus.value = 'disconnected'
      eventSource.close()
      
      // 重置当前消息引用
      currentAiMessage = null
      messageBuffer = ''
      
      // 消息完成后立即保存（确保保存完整内容）
      saveChatMessages(chatId.value, messages.value)
      
      // 3秒后自动隐藏思考步骤面板
      setTimeout(() => {
        thinkingSteps.value = []
      }, 3000)
    }
  }
  
  // 监听SSE错误
  eventSource.onerror = (error) => {
    console.error('SSE Error:', error)
    connectionStatus.value = 'error'
    isThinking.value = false
    eventSource.close()
    
    // 重置状态
    currentAiMessage = null
    messageBuffer = ''
  }
}

// 返回主页
const goBack = () => {
  router.push('/')
}

// 格式化时间戳
function formatTimestamp(timestamp) {
  const date = new Date(timestamp)
  const now = new Date()
  const diff = now - date
  
  // 小于1分钟
  if (diff < 60000) {
    return '刚刚'
  }
  // 小于1小时
  if (diff < 3600000) {
    return Math.floor(diff / 60000) + '分钟前'
  }
  // 小于24小时
  if (diff < 86400000) {
    return Math.floor(diff / 3600000) + '小时前'
  }
  // 小于7天
  if (diff < 604800000) {
    return Math.floor(diff / 86400000) + '天前'
  }
  // 超过7天，显示日期
  return date.toLocaleDateString('zh-CN', { month: '2-digit', day: '2-digit' })
}

// 组件销毁前关闭SSE连接并保存数据
onBeforeUnmount(() => {
  if (eventSource) {
    eventSource.close()
  }
  // 确保最后一次保存
  if (messages.value.length > 0) {
    saveChatMessages(chatId.value, messages.value)
  }
  // 清除定时器
  if (saveTimer) {
    clearTimeout(saveTimer)
  }
})
</script>

<style scoped>
.super-agent-container {
  display: flex;
  flex-direction: column;
  min-height: 100vh;
  background-color: #f9fbff;
}

.header {
  display: grid;
  grid-template-columns: 1fr auto 1fr;
  align-items: center;
  padding: 16px 24px;
  background-color: #3f51b5;
  color: white;
  box-shadow: 0 2px 8px rgba(0, 0, 0, 0.1);
  position: sticky;
  top: 0;
  z-index: 10;
}

.back-button {
  font-size: 16px;
  cursor: pointer;
  display: flex;
  align-items: center;
  transition: opacity 0.2s;
  justify-self: start;
}

.back-button:hover {
  opacity: 0.8;
}

.back-button:before {
  content: '←';
  margin-right: 8px;
}

.title {
  font-size: 20px;
  font-weight: bold;
  margin: 0;
  text-align: center;
  justify-self: center;
}

.header-actions {
  justify-self: end;
  display: flex;
  gap: 8px;
}

.action-button {
  background: rgba(255, 255, 255, 0.2);
  border: 1px solid rgba(255, 255, 255, 0.3);
  color: white;
  padding: 6px 12px;
  border-radius: 6px;
  cursor: pointer;
  font-size: 14px;
  transition: all 0.2s;
  display: flex;
  align-items: center;
  gap: 4px;
}

.action-button:hover {
  background: rgba(255, 255, 255, 0.3);
  border-color: rgba(255, 255, 255, 0.5);
}

.action-button:active {
  transform: scale(0.95);
}

/* 历史对话侧边栏 */
.history-panel {
  position: fixed;
  top: 0;
  right: 0;
  width: 320px;
  height: 100vh;
  background: white;
  box-shadow: -4px 0 24px rgba(0, 0, 0, 0.15);
  z-index: 1000;
  display: flex;
  flex-direction: column;
}

.history-header {
  display: flex;
  justify-content: space-between;
  align-items: center;
  padding: 20px;
  border-bottom: 1px solid #e0e0e0;
  background: #3f51b5;
  color: white;
}

.history-header h3 {
  margin: 0;
  font-size: 18px;
  font-weight: 600;
}

.close-button {
  background: rgba(255, 255, 255, 0.2);
  border: none;
  color: white;
  width: 32px;
  height: 32px;
  border-radius: 50%;
  cursor: pointer;
  font-size: 18px;
  display: flex;
  align-items: center;
  justify-content: center;
  transition: all 0.2s;
}

.close-button:hover {
  background: rgba(255, 255, 255, 0.3);
}

.history-list {
  flex: 1;
  overflow-y: auto;
  padding: 8px;
}

.empty-state {
  text-align: center;
  color: #999;
  padding: 40px 20px;
  font-size: 14px;
}

.history-item {
  padding: 12px;
  margin-bottom: 8px;
  border-radius: 8px;
  background: #f5f5f5;
  cursor: pointer;
  transition: all 0.2s;
}

.history-item:hover {
  background: #e8e8e8;
  transform: translateX(-2px);
}

.history-item.active {
  background: #e3f2fd;
  border-left: 3px solid #3f51b5;
}

.history-item-header {
  display: flex;
  justify-content: space-between;
  align-items: flex-start;
  margin-bottom: 8px;
}

.history-item-title {
  flex: 1;
  font-size: 14px;
  font-weight: 500;
  color: #333;
  line-height: 1.4;
  overflow: hidden;
  text-overflow: ellipsis;
  display: -webkit-box;
  -webkit-line-clamp: 2;
  -webkit-box-orient: vertical;
}

.delete-button {
  background: transparent;
  border: none;
  cursor: pointer;
  font-size: 14px;
  opacity: 0.5;
  transition: all 0.2s;
  padding: 4px;
}

.delete-button:hover {
  opacity: 1;
  transform: scale(1.2);
}

.history-item-meta {
  display: flex;
  justify-content: space-between;
  align-items: center;
  font-size: 12px;
  color: #666;
}

.message-count {
  color: #3f51b5;
  font-weight: 500;
}

.last-time {
  color: #999;
}

/* 遮罩层 */
.overlay {
  position: fixed;
  top: 0;
  left: 0;
  right: 0;
  bottom: 0;
  background: rgba(0, 0, 0, 0.5);
  z-index: 999;
}

/* 动画 */
.slide-enter-active,
.slide-leave-active {
  transition: transform 0.3s ease;
}

.slide-enter-from {
  transform: translateX(100%);
}

.slide-leave-to {
  transform: translateX(100%);
}

.fade-enter-active,
.fade-leave-active {
  transition: opacity 0.3s ease;
}

.fade-enter-from,
.fade-leave-to {
  opacity: 0;
}

.content-wrapper {
  display: flex;
  flex-direction: column;
  flex: 1;
}

.chat-area {
  flex: 1;
  padding: 16px;
  overflow: hidden;
  position: relative;
  /* 设置最小高度确保内容显示正常 */
  min-height: calc(100vh - 56px - 180px); /* 100vh减去头部高度和页脚高度 */
  margin-bottom: 16px; /* 为页脚留出空间 */
}

.footer-container {
  margin-top: auto;
}

/* 思考面板样式 */
.thinking-panel {
  background: linear-gradient(135deg, #667eea 0%, #764ba2 100%);
  border-radius: 16px;
  padding: 20px;
  margin-bottom: 16px;
  box-shadow: 0 8px 24px rgba(102, 126, 234, 0.3);
  animation: slideDown 0.4s ease-out;
}

@keyframes slideDown {
  from {
    opacity: 0;
    transform: translateY(-20px);
  }
  to {
    opacity: 1;
    transform: translateY(0);
  }
}

.thinking-header {
  display: flex;
  align-items: center;
  margin-bottom: 16px;
  color: white;
}

.thinking-icon {
  width: 32px;
  height: 32px;
  margin-right: 12px;
  display: flex;
  align-items: center;
  justify-content: center;
}

.spinner {
  width: 24px;
  height: 24px;
  border: 3px solid rgba(255, 255, 255, 0.3);
  border-top-color: white;
  border-radius: 50%;
  animation: spin 1s linear infinite;
}

@keyframes spin {
  to {
    transform: rotate(360deg);
  }
}

.check-icon {
  width: 24px;
  height: 24px;
  background: white;
  border-radius: 50%;
  display: flex;
  align-items: center;
  justify-content: center;
  color: #667eea;
  font-size: 16px;
  font-weight: bold;
  animation: checkPop 0.4s ease-out;
}

@keyframes checkPop {
  0% {
    transform: scale(0);
  }
  50% {
    transform: scale(1.2);
  }
  100% {
    transform: scale(1);
  }
}

.thinking-title {
  font-size: 16px;
  font-weight: 600;
  color: white;
}

.thinking-steps {
  background: rgba(255, 255, 255, 0.1);
  border-radius: 12px;
  padding: 16px;
  backdrop-filter: blur(10px);
}

.thinking-step {
  display: flex;
  align-items: flex-start;
  margin-bottom: 12px;
  animation: stepAppear 0.3s ease-out;
}

.thinking-step:last-child {
  margin-bottom: 0;
}

@keyframes stepAppear {
  from {
    opacity: 0;
    transform: translateX(-10px);
  }
  to {
    opacity: 1;
    transform: translateX(0);
  }
}

.step-indicator {
  display: flex;
  flex-direction: column;
  align-items: center;
  margin-right: 12px;
  padding-top: 2px;
}

.step-dot {
  width: 12px;
  height: 12px;
  background: white;
  border-radius: 50%;
  box-shadow: 0 2px 8px rgba(255, 255, 255, 0.5);
  flex-shrink: 0;
}

.step-line {
  width: 2px;
  flex: 1;
  min-height: 20px;
  background: rgba(255, 255, 255, 0.3);
  margin-top: 4px;
}

.step-content {
  flex: 1;
  color: white;
  font-size: 14px;
  line-height: 1.6;
  padding-top: 0;
}

/* 响应式样式 */
@media (max-width: 768px) {
  .header {
    padding: 12px 16px;
  }
  
  .title {
    font-size: 18px;
  }
  
  .chat-area {
    padding: 12px;
    min-height: calc(100vh - 48px - 160px); /* 调整计算值 */
    margin-bottom: 12px;
  }
  
  .thinking-panel {
    padding: 16px;
    margin-bottom: 12px;
  }
  
  .thinking-title {
    font-size: 14px;
  }
  
  .step-content {
    font-size: 13px;
  }
  
  .history-panel {
    width: 280px;
  }
  
  .action-button {
    padding: 4px 8px;
    font-size: 12px;
  }
}

@media (max-width: 480px) {
  .header {
    padding: 10px 12px;
  }
  
  .back-button {
    font-size: 14px;
  }
  
  .title {
    font-size: 16px;
  }
  
  .chat-area {
    padding: 8px;
    min-height: calc(100vh - 42px - 150px); /* 再次调整计算值 */
    margin-bottom: 8px;
  }
}
</style> 