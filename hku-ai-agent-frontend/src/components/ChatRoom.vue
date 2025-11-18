<template>
  <div class="chat-container">
    <!-- Conversation history -->
    <div class="chat-messages" ref="messagesContainer">
      <div v-for="(msg, index) in messages" :key="index" class="message-wrapper">
        <!-- AI message -->
        <div
          v-if="!msg.isUser"
          class="message ai-message"
          :class="[msg.type]"
        >
          <div class="avatar ai-avatar">
            <AiAvatarFallback :type="aiType" />
          </div>
          <div class="message-bubble">
            <div class="message-content">
              <span v-html="formatMessageContent(msg.content)"></span>
              <span
                v-if="connectionStatus === 'connecting' && index === messages.length - 1"
                class="typing-indicator"
              >▋</span>
            </div>
            <div class="message-time">{{ formatTime(msg.time) }}</div>
          </div>
        </div>

        <!-- User message -->
        <div v-else class="message user-message" :class="[msg.type]">
          <div class="message-bubble">
            <div class="message-content">{{ msg.content }}</div>
            <div class="message-time">{{ formatTime(msg.time) }}</div>
          </div>
          <div class="avatar user-avatar">
            <div class="avatar-placeholder">Me</div>
          </div>
        </div>
      </div>
    </div>

    <!-- Input area -->
    <div class="chat-input-container">
      <div class="chat-input">
        <textarea
          v-model="inputMessage"
          @keydown.enter.prevent="sendMessage"
          placeholder="Type your message..."
          class="input-box"
          :disabled="connectionStatus === 'connecting'"
        ></textarea>
        <button
          @click="sendMessage"
          class="send-button"
          :disabled="connectionStatus === 'connecting' || !inputMessage.trim()"
        >Send</button>
      </div>
    </div>
  </div>
</template>

<script setup>
import { ref, onMounted, nextTick, watch } from 'vue'
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
    default: 'default' // 'love' or 'super'
  }
})

const emit = defineEmits(['send-message'])

const inputMessage = ref('')
const messagesContainer = ref(null)

// Send a message
const sendMessage = () => {
  if (!inputMessage.value.trim()) {
    return
  }

  emit('send-message', inputMessage.value)
  inputMessage.value = ''
}

// Format time for display
const formatTime = (timestamp) => {
  const date = new Date(timestamp)
  return date.toLocaleTimeString('en-HK', { hour: '2-digit', minute: '2-digit' })
}

// Format message content: sanitise input and preserve plain-text line breaks
const formatMessageContent = (content) => {
  if (!content) {
    return ''
  }

  let processedContent = content
    .replace(/[\u0000-\u001f\u007f]/g, '')
    .replace(/[\u{1F600}-\u{1F64F}]/gu, '😊')
    .replace(/[\u{1F300}-\u{1F5FF}]/gu, '')
    .replace(/[\u{1F680}-\u{1F6FF}]/gu, '')
    .replace(/[\u{1F900}-\u{1F9FF}]/gu, '')
    .replace(/[\u{2600}-\u{26FF}]/gu, '')
    .replace(/[\u{2700}-\u{27BF}]/gu, '')

  const downloadButtons = []
  processedContent = processedContent.replace(/\[DOWNLOAD_LINK\](.*?)\[\/DOWNLOAD_LINK\]/g, (match, url) => {
    const placeholder = `__DOWNLOAD_BTN_${downloadButtons.length}__`
    downloadButtons.push(url.trim())
    return placeholder
  })

  let escapedContent = processedContent
    .replace(/&/g, '&amp;')
    .replace(/</g, '&lt;')
    .replace(/>/g, '&gt;')
    .replace(/"/g, '&quot;')
    .replace(/'/g, '&#039;')

  escapedContent = escapedContent.replace(/\r\n/g, '\n')
  const paragraphBlocks = escapedContent.split(/\n{2,}/)
  escapedContent = paragraphBlocks
    .map((block) => block.split('\n').join('<br>'))
    .join('<br><br>')

  downloadButtons.forEach((url, index) => {
    const placeholder = `__DOWNLOAD_BTN_${index}__`
    const safeUrl = url.replace(/&amp;/g, '&')
    escapedContent = escapedContent.replace(
      placeholder,
      `<a href="${safeUrl}" target="_blank" class="download-btn" download>📥 Download PDF</a>`
    )
  })

  const urlRegex = /(https?:\/\/[^\s<]+)/g
  escapedContent = escapedContent.replace(urlRegex, (url) => {
    if (escapedContent.indexOf(`href="${url}"`) !== -1) {
      return url
    }

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

  return escapedContent
}

const scrollToBottom = async () => {
  await nextTick()
  if (messagesContainer.value) {
    messagesContainer.value.scrollTop = messagesContainer.value.scrollHeight
  }
}

watch(() => props.messages.length, () => {
  scrollToBottom()
})

watch(() => props.messages.map((m) => m.content).join(''), () => {
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
  height: 100%;
  min-height: 520px;
  background-color: #f8fafc;
  border-radius: 12px;
  box-shadow: 0 8px 28px rgba(15, 23, 42, 0.12);
  overflow: hidden;
  position: relative;
}

.chat-messages {
  flex: 1;
  overflow-y: auto;
  padding: 24px 28px 32px;
  display: flex;
  flex-direction: column;
  background: linear-gradient(180deg, #f8fafc 0%, #f1f5f9 100%);
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
  margin-left: auto;
  flex-direction: row;
}

.ai-message {
  margin-right: auto;
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
  margin-left: 8px;
}

.ai-avatar {
  margin-right: 8px;
}

.avatar-placeholder {
  width: 100%;
  height: 100%;
  display: flex;
  align-items: center;
  justify-content: center;
  background-color: #007bff;
  color: #fff;
  font-weight: 600;
}

.message-bubble {
  padding: 12px;
  border-radius: 18px;
  position: relative;
  word-wrap: break-word;
  min-width: 100px;
}

.user-message .message-bubble {
  background-color: #007bff;
  color: #fff;
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

.message-content :deep(a.download-btn) {
  display: inline-block;
  padding: 12px 24px;
  margin: 16px 0 8px 0;
  background: linear-gradient(135deg, #667eea 0%, #764ba2 100%);
  color: #fff !important;
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
  margin: 0 24px 24px;
  background-color: #fff;
  border: 1px solid rgba(148, 163, 184, 0.35);
  box-shadow: 0 6px 24px rgba(15, 23, 42, 0.08);
  padding: 12px 16px 12px 20px;
  display: flex;
  align-items: center;
}

.chat-input {
  display: flex;
  width: 100%;
  gap: 12px;
  align-items: center;
}

.input-box {
  flex-grow: 1;
  border: 1px solid rgba(148, 163, 184, 0.45);
  border-radius: 16px;
  padding: 12px 16px;
  font-size: 15px;
  resize: none;
  min-height: 48px;
  max-height: 120px;
  outline: none;
  transition: border-color 0.2s, box-shadow 0.2s;
  overflow-y: auto;
  background-color: #f8fafc;
}

.input-box::-webkit-scrollbar {
  display: none;
}

.input-box:focus {
  border-color: #0b5ba6;
  box-shadow: 0 0 0 3px rgba(11, 91, 166, 0.13);
  background-color: #fff;
}

.send-button {
  background: linear-gradient(135deg, #0b5ba6, #2563eb);
  color: #fff;
  border: none;
  border-radius: 14px;
  padding: 10px 20px;
  font-size: 15px;
  cursor: pointer;
  transition: transform 0.2s, box-shadow 0.2s;
  min-width: 88px;
}

.send-button:hover:not(:disabled) {
  transform: translateY(-1px);
  box-shadow: 0 8px 18px rgba(37, 99, 235, 0.35);
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

.input-box:disabled,
.send-button:disabled {
  opacity: 0.6;
  cursor: not-allowed;
}

@media (max-width: 768px) {
  .message {
    max-width: 95%;
  }

  .message-content {
    font-size: 15px;
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
    margin: 0 16px 16px;
    padding: 10px 14px 10px 16px;
  }

  .chat-messages {
    padding: 16px 18px 28px;
  }
}

.ai-answer {
  animation: fadeIn 0.3s ease-in-out;
}

.ai-error {
  opacity: 0.7;
}

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
