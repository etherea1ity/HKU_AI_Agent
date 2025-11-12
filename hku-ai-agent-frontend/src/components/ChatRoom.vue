<template>
  <div class="chat-container">
    <!-- 聊天记录区域 -->
    <div class="chat-messages" ref="messagesContainer">
      <div v-for="(msg, index) in messages" :key="index" class="message-wrapper">
        <!-- AI消息 -->
        <div v-if="!msg.isUser" 
             class="message ai-message" 
             :class="[msg.type]">
          <div class="avatar ai-avatar">
            <AiAvatarFallback :type="aiType" />
          </div>
          <div class="message-bubble">
            <div class="message-content">
              <span v-html="formatMessageContent(msg.content)"></span>
              <span v-if="connectionStatus === 'connecting' && index === messages.length - 1" class="typing-indicator">▋</span>
            </div>
            <div class="message-time">{{ formatTime(msg.time) }}</div>
          </div>
        </div>
        
        <!-- 用户消息 -->
        <div v-else class="message user-message" :class="[msg.type]">
          <div class="message-bubble">
            <div class="message-content">{{ msg.content }}</div>
            <div class="message-time">{{ formatTime(msg.time) }}</div>
          </div>
          <div class="avatar user-avatar">
            <div class="avatar-placeholder">我</div>
          </div>
        </div>
      </div>
    </div>

    <!-- 输入区域 -->
    <div class="chat-input-container">
      <div class="chat-input">
        <textarea 
          v-model="inputMessage" 
          @keydown.enter.prevent="sendMessage"
          placeholder="请输入消息..." 
          class="input-box"
          :disabled="connectionStatus === 'connecting'"
        ></textarea>
        <button 
          @click="sendMessage" 
          class="send-button"
          :disabled="connectionStatus === 'connecting' || !inputMessage.trim()"
        >发送</button>
      </div>
    </div>
  </div>
</template>

<script setup>
import { ref, reactive, onMounted, nextTick, watch, computed } from 'vue'
import AiAvatarFallback from './AiAvatarFallback.vue'

const props = defineProps({
  messages: {
    type: Array,
    default: () => []
  },
  connectionStatus: {
    type: String,
    default: 'disconnected'
  },
  aiType: {
    type: String,
    default: 'default'  // 'love' 或 'super'
  }
})

const emit = defineEmits(['send-message'])

const inputMessage = ref('')
const messagesContainer = ref(null)

// 根据AI类型选择不同头像
const aiAvatar = computed(() => {
  return props.aiType === 'love' 
    ? '/ai-love-avatar.png'  // 恋爱大师头像
    : '/ai-super-avatar.png' // 超级智能体头像
})

// 发送消息
const sendMessage = () => {
  if (!inputMessage.value.trim()) return
  
  emit('send-message', inputMessage.value)
  inputMessage.value = ''
}

// 格式化时间
const formatTime = (timestamp) => {
  const date = new Date(timestamp)
  return date.toLocaleTimeString('zh-CN', { hour: '2-digit', minute: '2-digit' })
}

// 格式化消息内容，将链接转换为可点击的超链接
const formatMessageContent = (content) => {
  if (!content) return ''
  
  // 替换无法正确显示的emoji为常用的文本表情
  // 将常见的emoji字符替换为对应的文本表情
  let processedContent = content
    .replace(/[\u{1F600}-\u{1F64F}]/gu, '😊')  // 表情符号
    .replace(/[\u{1F300}-\u{1F5FF}]/gu, '')    // 各种符号
    .replace(/[\u{1F680}-\u{1F6FF}]/gu, '')    // 交通工具
    .replace(/[\u{1F900}-\u{1F9FF}]/gu, '')    // 补充符号
    .replace(/[\u{2600}-\u{26FF}]/gu, '')      // 杂项符号
    .replace(/[\u{2700}-\u{27BF}]/gu, '')      // 装饰符号
  
  // 先提取下载链接（在转义之前），避免标记被转义
  let downloadButtons = []
  processedContent = processedContent.replace(
    /\[DOWNLOAD_LINK\](.*?)\[\/DOWNLOAD_LINK\]/g,
    (match, url) => {
      const placeholder = `__DOWNLOAD_BTN_${downloadButtons.length}__`
      downloadButtons.push(url.trim())
      return placeholder
    }
  )
  
  // 转义HTML特殊字符（防止XSS攻击）
  let escapedContent = processedContent
    .replace(/&/g, '&amp;')
    .replace(/</g, '&lt;')
    .replace(/>/g, '&gt;')
    .replace(/"/g, '&quot;')
    .replace(/'/g, '&#039;')
  
  // 将换行符转换为<br>
  escapedContent = escapedContent.replace(/\n/g, '<br>')
  
  // 恢复下载按钮（不需要转义URL，因为它们在href中）
  downloadButtons.forEach((url, index) => {
    const placeholder = `__DOWNLOAD_BTN_${index}__`
    const safeUrl = url.replace(/&amp;/g, '&') // 确保&没有被重复转义
    escapedContent = escapedContent.replace(
      placeholder,
      `<a href="${safeUrl}" target="_blank" class="download-btn" download>📥 点击下载PDF</a>`
    )
  })
  
  // 检测并转换一般的URL链接
  const urlRegex = /(https?:\/\/[^\s<]+)/g
  escapedContent = escapedContent.replace(urlRegex, (url) => {
    // 跳过已经在<a>标签中的URL
    if (escapedContent.indexOf(`href="${url}"`) !== -1) {
      return url
    }
    
    // 移除URL末尾的标点符号（如 ), ], ., , 等）
    const punctuationRegex = /[)\].,;:!?]+$/
    const match = url.match(punctuationRegex)
    let cleanUrl = url
    let trailingPunctuation = ''
    
    if (match) {
      trailingPunctuation = match[0]
      cleanUrl = url.slice(0, -trailingPunctuation.length)
    }
    
    return `<a href="${cleanUrl}" target="_blank" class="link" rel="noopener noreferrer">${cleanUrl}</a>${trailingPunctuation}`
  })
  
  // 支持简单的Markdown格式
  // 粗体 **text**
  escapedContent = escapedContent.replace(/\*\*([^*]+)\*\*/g, '<strong>$1</strong>')
  
  // 标题 # text
  escapedContent = escapedContent.replace(/^### (.+)$/gm, '<h4>$1</h4>')
  escapedContent = escapedContent.replace(/^## (.+)$/gm, '<h3>$1</h3>')
  escapedContent = escapedContent.replace(/^# (.+)$/gm, '<h2>$1</h2>')
  
  return escapedContent
}

// 自动滚动到底部
const scrollToBottom = async () => {
  await nextTick()
  if (messagesContainer.value) {
    messagesContainer.value.scrollTop = messagesContainer.value.scrollHeight
  }
}

// 监听消息变化与内容变化，自动滚动
watch(() => props.messages.length, () => {
  scrollToBottom()
})

watch(() => props.messages.map(m => m.content).join(''), () => {
  scrollToBottom()
})

onMounted(() => {
  scrollToBottom()
})
</script>

<style scoped>
.chat-container {
  display: flex;
  flex-direction: column;
  height: 70vh;
  min-height: 600px;
  background-color: #f5f5f5;
  border-radius: 8px;
  overflow: hidden;
  position: relative;
}

.chat-messages {
  flex: 1;
  overflow-y: auto;
  padding: 16px;
  padding-bottom: 80px; /* 为输入框留出空间 */
  display: flex;
  flex-direction: column;
  position: absolute;
  top: 0;
  left: 0;
  right: 0;
  bottom: 72px; /* 与输入框高度相匹配 */
}

.message-wrapper {
  margin-bottom: 16px;
  display: flex;
  flex-direction: column;
  width: 100%;
}

.message {
  display: flex;
  align-items: flex-start;
  max-width: 85%;
  margin-bottom: 8px;
}

.user-message {
  margin-left: auto; /* 用户消息靠右 */
  flex-direction: row; /* 正常顺序，先气泡后头像 */
}

.ai-message {
  margin-right: auto; /* AI消息靠左 */
}

.avatar {
  width: 36px;
  height: 36px;
  border-radius: 50%;
  overflow: hidden;
  flex-shrink: 0;
  display: flex;
  align-items: center;
  justify-content: center;
}

.user-avatar {
  margin-left: 8px; /* 用户头像在右侧，左边距 */
}

.ai-avatar {
  margin-right: 8px; /* AI头像在左侧，右边距 */
}

.avatar-placeholder {
  width: 100%;
  height: 100%;
  display: flex;
  align-items: center;
  justify-content: center;
  background-color: #007bff;
  color: white;
  font-weight: bold;
}

.message-bubble {
  padding: 12px;
  border-radius: 18px;
  position: relative;
  word-wrap: break-word;
  min-width: 100px; /* 最小宽度 */
}

.user-message .message-bubble {
  background-color: #007bff;
  color: white;
  border-bottom-right-radius: 4px;
  text-align: left;
}

.ai-message .message-bubble {
  background-color: #e9e9eb;
  color: #333;
  border-bottom-left-radius: 4px;
  text-align: left;
}

.message-content {
  font-size: 16px;
  line-height: 1.5;
  white-space: pre-wrap;
}

/* 链接样式 */
.message-content :deep(a.link) {
  color: #1976d2;
  text-decoration: none;
  transition: all 0.2s;
  border-bottom: 1px solid transparent;
}

.message-content :deep(a.link:hover) {
  color: #1565c0;
  border-bottom-color: #1565c0;
}

/* 下载按钮样式 */
.message-content :deep(a.download-btn) {
  display: inline-block;
  padding: 12px 24px;
  margin: 16px 0 8px 0;
  background: linear-gradient(135deg, #667eea 0%, #764ba2 100%);
  color: white !important;
  border: none;
  border-radius: 10px;
  font-weight: 600;
  font-size: 15px;
  text-decoration: none;
  transition: all 0.3s;
  box-shadow: 0 4px 15px rgba(102, 126, 234, 0.4);
  cursor: pointer;
}

.message-content :deep(a.download-btn:hover) {
  transform: translateY(-3px);
  box-shadow: 0 8px 20px rgba(102, 126, 234, 0.6);
  background: linear-gradient(135deg, #7c8ef5 0%, #8a5ab3 100%);
}

.message-content :deep(a.download-btn:active) {
  transform: translateY(-1px);
  box-shadow: 0 3px 10px rgba(102, 126, 234, 0.4);
}

.message-content :deep(strong) {
  font-weight: 600;
  color: #333;
}

.message-content :deep(h2),
.message-content :deep(h3),
.message-content :deep(h4) {
  margin: 12px 0 8px 0;
  font-weight: 600;
}

.message-content :deep(h2) {
  font-size: 1.3em;
  color: #1976d2;
}

.message-content :deep(h3) {
  font-size: 1.2em;
  color: #333;
}

.message-content :deep(h4) {
  font-size: 1.1em;
  color: #666;
}

.message-time {
  font-size: 12px;
  opacity: 0.7;
  margin-top: 4px;
  text-align: right;
}

.chat-input-container {
  position: absolute;
  bottom: 0;
  left: 0;
  right: 0;
  background-color: white;
  border-top: 1px solid #e0e0e0;
  z-index: 100;
  height: 72px; /* 固定高度 */
  box-shadow: 0 -2px 10px rgba(0, 0, 0, 0.05);
}

.chat-input {
  display: flex;
  padding: 16px;
  height: 100%;
  box-sizing: border-box;
  align-items: center;
}

.input-box {
  flex-grow: 1;
  border: 1px solid #ddd;
  border-radius: 20px;
  padding: 10px 16px;
  font-size: 16px;
  resize: none;
  min-height: 20px;
  max-height: 40px; /* 限制高度 */
  outline: none;
  transition: border-color 0.3s;
  overflow-y: auto;
  scrollbar-width: none; /* Firefox */
  -ms-overflow-style: none; /* IE & Edge */
}

/* 隐藏Webkit浏览器的滚动条 */
.input-box::-webkit-scrollbar {
  display: none;
}

.input-box:focus {
  border-color: #007bff;
}

.send-button {
  margin-left: 12px;
  background-color: #007bff;
  color: white;
  border: none;
  border-radius: 20px;
  padding: 0 20px;
  font-size: 16px;
  cursor: pointer;
  transition: background-color 0.3s;
  height: 40px;
  align-self: center;
}

.send-button:hover:not(:disabled) {
  background-color: #0069d9;
}

.typing-indicator {
  display: inline-block;
  animation: blink 0.7s infinite;
  margin-left: 2px;
}

@keyframes blink {
  0% { opacity: 0; }
  50% { opacity: 1; }
  100% { opacity: 0; }
}

.input-box:disabled, .send-button:disabled {
  opacity: 0.6;
  cursor: not-allowed;
}

/* 响应式设计 */
@media (max-width: 768px) {
  .message {
    max-width: 95%;
  }
  
  .message-content {
    font-size: 15px;
  }
  
  .chat-input {
    padding: 12px;
  }
  
  .input-box {
    padding: 8px 12px;
  }
  
  .send-button {
    padding: 0 15px;
    font-size: 14px;
  }
}

@media (max-width: 480px) {
  .avatar {
    width: 32px;
    height: 32px;
  }
  
  .message-bubble {
    padding: 10px;
  }
  
  .message-content {
    font-size: 14px;
  }
  
  .chat-input-container {
    height: 64px;
  }
  
  .chat-messages {
    bottom: 64px;
  }
}

/* 新增：不同类型消息的样式 */
.ai-answer {
  animation: fadeIn 0.3s ease-in-out;
}

.ai-final {
  /* 最终回答，可以有不同的样式，例如边框高亮等 */
}

.ai-error {
  opacity: 0.7;
}

.user-question {
  /* 用户提问的特殊样式 */
}

/* 连续消息气泡样式 */
.ai-message + .ai-message {
  margin-top: 4px;
}

.ai-message + .ai-message .avatar {
  visibility: hidden;
}

.ai-message + .ai-message .message-bubble {
  border-top-left-radius: 10px;
}
</style> 