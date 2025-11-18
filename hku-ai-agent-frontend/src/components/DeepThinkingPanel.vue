<template>
  <div v-if="visible" class="deep-panel" :class="{ collapsed }">
    <div class="panel-header" @click="emitToggle">
      <div class="header-left">
        <div class="status-icon">
          <div v-if="isThinking" class="spinner" />
          <div v-else class="check-icon">✓</div>
        </div>
        <div class="status-text">
          <span class="status-title">{{ headerText }}</span>
          <span v-if="subtitle" class="status-subtitle">{{ subtitle }}</span>
        </div>
      </div>
      <button class="collapse-btn" type="button" @click.stop="emitToggle">
        {{ collapsed ? 'Expand' : 'Collapse' }}
      </button>
    </div>
    <div v-show="!collapsed" class="panel-body">
      <div v-if="steps.length" class="steps-block">
        <div class="block-title">Reasoning Steps</div>
        <div class="steps-list">
          <div v-for="(step, index) in steps" :key="`${step.time}-${index}`" class="step-item">
            <div class="step-indicator">
              <div class="step-dot" />
              <div v-if="index < steps.length - 1" class="step-line" />
            </div>
            <div class="step-content">{{ step.content }}</div>
          </div>
        </div>
      </div>
      <div v-if="ragSources.length" class="rag-block">
        <div class="block-title">RAG Retrieval Insights</div>
        <div class="rag-list">
          <div v-for="(source, index) in ragSources" :key="index" class="rag-item">
            <div class="rag-title">{{ source.title }}</div>
            <div class="rag-snippet">{{ source.snippet }}</div>
            <a v-if="source.source" class="rag-link" :href="source.source" target="_blank" rel="noopener noreferrer">View source -></a>
          </div>
        </div>
      </div>
      <div v-if="!steps.length && !ragSources.length" class="empty-block">
        No reasoning or retrieval details are available yet.
      </div>
    </div>
  </div>
</template>

<script setup>
import { computed } from 'vue'

const props = defineProps({
  visible: {
    type: Boolean,
    default: false
  },
  isThinking: {
    type: Boolean,
    default: false
  },
  header: {
    type: String,
    default: ''
  },
  subtitle: {
    type: String,
    default: ''
  },
  steps: {
    type: Array,
    default: () => []
  },
  ragSources: {
    type: Array,
    default: () => []
  },
  collapsed: {
    type: Boolean,
    default: false
  }
})

const emit = defineEmits(['toggle-collapse'])

const headerText = computed(() => {
  if (props.header) {
    return props.header
  }
  return props.isThinking ? 'Thinking deeply...' : 'Analysis complete'
})

const emitToggle = () => {
  emit('toggle-collapse')
}
</script>

<style scoped>
.deep-panel {
  background: linear-gradient(135deg, #3f51b5 0%, #312e81 100%);
  border-radius: 16px;
  padding: 20px;
  margin-bottom: 16px;
  box-shadow: 0 12px 24px rgba(15, 23, 42, 0.25);
  color: #f8fafc;
  transition: all 0.25s ease;
}

.deep-panel.collapsed {
  padding-bottom: 12px;
}

.panel-header {
  display: flex;
  align-items: center;
  justify-content: space-between;
  cursor: pointer;
  gap: 12px;
}

.header-left {
  display: flex;
  align-items: center;
  gap: 12px;
}

.status-icon {
  width: 32px;
  height: 32px;
  display: flex;
  align-items: center;
  justify-content: center;
}

.spinner {
  width: 22px;
  height: 22px;
  border: 3px solid rgba(248, 250, 252, 0.3);
  border-top-color: #f8fafc;
  border-radius: 50%;
  animation: spinnerRotate 1s linear infinite;
}

.check-icon {
  width: 24px;
  height: 24px;
  border-radius: 50%;
  background: #f8fafc;
  color: #312e81;
  display: flex;
  align-items: center;
  justify-content: center;
  font-size: 16px;
  font-weight: 600;
}

.status-text {
  display: flex;
  flex-direction: column;
  gap: 4px;
}

.status-title {
  font-size: 16px;
  font-weight: 600;
}

.status-subtitle {
  font-size: 13px;
  color: rgba(248, 250, 252, 0.76);
}

.collapse-btn {
  border: none;
  border-radius: 999px;
  padding: 6px 14px;
  background: rgba(255, 255, 255, 0.16);
  color: inherit;
  font-size: 13px;
  cursor: pointer;
  transition: background 0.2s ease;
}

.collapse-btn:hover {
  background: rgba(255, 255, 255, 0.28);
}

.panel-body {
  margin-top: 18px;
  display: flex;
  flex-direction: column;
  gap: 18px;
}

.block-title {
  font-size: 14px;
  font-weight: 600;
  margin-bottom: 12px;
}

.steps-list {
  display: flex;
  flex-direction: column;
  gap: 12px;
}

.step-item {
  display: flex;
  align-items: flex-start;
  gap: 12px;
}

.step-indicator {
  display: flex;
  flex-direction: column;
  align-items: center;
  margin-top: 2px;
}

.step-dot {
  width: 10px;
  height: 10px;
  border-radius: 50%;
  background: #f8fafc;
  box-shadow: 0 2px 6px rgba(248, 250, 252, 0.4);
}

.step-line {
  width: 2px;
  flex: 1;
  background: rgba(248, 250, 252, 0.35);
  margin-top: 4px;
}

.step-content {
  flex: 1;
  font-size: 14px;
  line-height: 1.6;
  color: rgba(248, 250, 252, 0.95);
}

.rag-list {
  display: flex;
  flex-direction: column;
  gap: 12px;
}

.rag-item {
  background: rgba(15, 23, 42, 0.25);
  border-radius: 12px;
  padding: 12px 14px;
  backdrop-filter: blur(6px);
}

.rag-title {
  font-size: 14px;
  font-weight: 600;
}

.rag-snippet {
  margin-top: 6px;
  font-size: 13px;
  line-height: 1.6;
  color: rgba(248, 250, 252, 0.82);
}

.rag-link {
  display: inline-block;
  margin-top: 8px;
  font-size: 13px;
  color: #c7d2fe;
  text-decoration: none;
}

.rag-link:hover {
  text-decoration: underline;
}

.empty-block {
  font-size: 13px;
  color: rgba(248, 250, 252, 0.7);
  text-align: center;
  padding: 12px 0;
}

@keyframes spinnerRotate {
  to {
    transform: rotate(360deg);
  }
}

@media (max-width: 640px) {
  .deep-panel {
    padding: 16px;
  }

  .panel-body {
    gap: 14px;
  }

  .step-content {
    font-size: 13px;
  }

  .rag-item {
    padding: 10px 12px;
  }
}
</style>
