import axios from 'axios'

const API_BASE_URL = process.env.NODE_ENV === 'production'
  ? '/api'
  : 'http://localhost:8123/api'

const request = axios.create({
  baseURL: API_BASE_URL,
  timeout: 60000
})

const buildQueryString = (params = {}) => {
  return Object.keys(params)
    .filter((key) => {
      const value = params[key]
      return value !== undefined && value !== null && value !== ''
    })
    .map((key) => `${encodeURIComponent(key)}=${encodeURIComponent(params[key])}`)
    .join('&')
}

export const connectSSE = (url, params = {}, onMessage, onError) => {
  const queryString = buildQueryString(params)
  const fullUrl = `${API_BASE_URL}${url}${queryString ? `?${queryString}` : ''}`

  const eventSource = new EventSource(fullUrl)

  eventSource.onmessage = (event) => {
    const data = event.data
    if (data === '[DONE]') {
      onMessage?.('[DONE]')
    } else {
      onMessage?.(data)
    }
  }

  eventSource.onerror = (error) => {
    onError?.(error)
    eventSource.close()
  }

  return eventSource
}

export const chatWithLoveApp = (message, chatId) => {
  return connectSSE('/ai/love_app/chat/sse', { message, chatId })
}

export const chatWithManus = (message, chatId) => {
  return connectSSE('/ai/manus/chat', { message, chatId: chatId ?? 'default' })
}

export default {
  chatWithLoveApp,
  chatWithManus,
  request
}
