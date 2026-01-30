<script setup lang="ts">
import { ref, onMounted, nextTick, computed } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { message } from 'ant-design-vue'
import { getMyAppInfo, deployApp, type AppVO } from '@/api/app'
import { useUserStore } from '@/stores/user'

const route = useRoute()
const router = useRouter()
const userStore = useUserStore()

const appId = route.params.appId as string
const app = ref<AppVO>()
const loading = ref(false)
const deploying = ref(false)

// Chat
interface Message {
  role: 'user' | 'ai'
  content: string
  loading?: boolean
}
const messages = ref<Message[]>([])
const inputPrompt = ref('')
const chatContainer = ref<HTMLElement>()

// Preview
const previewUrl = computed(() => {
  if (!app.value) return ''
  // Use timestamp to force refresh
  return `http://localhost:8123/api/static/${app.value.codeGenType || 'website'}_${app.value.id}/index.html?t=${new Date().getTime()}`
})
const iframeRef = ref<HTMLIFrameElement>()

// Fetch App Info
const loadAppInfo = async () => {
  if (!appId) return
  loading.value = true
  try {
    const res = await getMyAppInfo(Number(appId))
    if (res.data) {
      app.value = res.data
    } else {
      message.error('应用不存在')
    }
  } catch (e: any) {
    message.error('加载应用失败')
  } finally {
    loading.value = false
  }
}

// SSE Generation
const onGenerate = async (prompt: string) => {
  if (!app.value || !prompt) return
  
  // Add User Message
  messages.value.push({ role: 'user', content: prompt })
  inputPrompt.value = ''
  
  // Add AI Placeholder
  const aiMsgIndex = messages.value.push({ role: 'ai', content: '', loading: true }) - 1
  
  scrollToBottom()

  // Start SSE
  // Note: EventSource does not support custom headers, but supports cookies via withCredentials
  const eventSource = new EventSource(
    `http://localhost:8123/api/app/chat/gen/code?appId=${appId}&userMessage=${encodeURIComponent(prompt)}`,
    { withCredentials: true }
  )

  eventSource.onmessage = (event) => {
    try {
      const data = JSON.parse(event.data)
      if (data.content) {
        messages.value[aiMsgIndex].content += data.content
        scrollToBottom()
      }
    } catch (e) {
      // Ignore parse error
    }
  }

  eventSource.addEventListener('done', () => {
    messages.value[aiMsgIndex].loading = false
    eventSource.close()
    refreshPreview()
  })
  
  eventSource.addEventListener('error', (event) => {
    const data = JSON.parse(event.data)
    message.error(data.message)
    messages.value[aiMsgIndex].loading = false
    messages.value[aiMsgIndex].content += '\n[生成出错]'
    eventSource.close()
  })

  eventSource.onerror = (event) => {
    console.error('SSE Error', event)
    eventSource.close()
    if (messages.value[aiMsgIndex].loading) {
        messages.value[aiMsgIndex].loading = false
        messages.value[aiMsgIndex].content += '\n[生成出错]'
    }
  }
}

const refreshPreview = () => {
  if (iframeRef.value) {
    // Reload iframe
    iframeRef.value.src = iframeRef.value.src
  }
}

const scrollToBottom = () => {
  nextTick(() => {
    if (chatContainer.value) {
      chatContainer.value.scrollTop = chatContainer.value.scrollHeight
    }
  })
}

const handleDeploy = async () => {
  if (!app.value) return
  deploying.value = true
  try {
    const res = await deployApp({ appId: app.value.id })
    if (res.data) {
      message.success('部署成功')
      window.open(res.data, '_blank')
    }
  } catch (e: any) {
    message.error('部署失败')
  } finally {
    deploying.value = false
  }
}

const onSendMessage = () => {
    if (inputPrompt.value.trim()) {
        onGenerate(inputPrompt.value)
    }
}

onMounted(async () => {
  await loadAppInfo()
  const initPrompt = route.query.initPrompt as string
  if (initPrompt) {
    // Clear query param to avoid re-trigger on reload
    router.replace({ query: { ...route.query, initPrompt: undefined } })
    onGenerate(initPrompt)
  }
})
</script>

<template>
  <div class="app-generator">
    <!-- Header -->
    <header class="header">
      <div class="left">
        <a-button type="text" @click="router.back()">
            <template #icon>
                <span style="font-size: 20px;">&lt;</span>
            </template>
        </a-button>
        <span class="app-name">{{ app?.appName || '加载中...' }}</span>
        <a-tag v-if="app?.codeGenType">{{ app.codeGenType }}</a-tag>
      </div>
      <div class="right">
        <a-button type="primary" :loading="deploying" @click="handleDeploy">部署</a-button>
      </div>
    </header>

    <!-- Content -->
    <div class="content">
      <!-- Chat Area -->
      <div class="chat-area">
        <div class="message-list" ref="chatContainer">
          <div 
            v-for="(msg, index) in messages" 
            :key="index" 
            :class="['message', msg.role]"
          >
            <div class="avatar">
                {{ msg.role === 'ai' ? '🤖' : '👤' }}
            </div>
            <div class="bubble">
                <div v-if="msg.role === 'ai'" style="white-space: pre-wrap;">{{ msg.content }}</div>
                <div v-else>{{ msg.content }}</div>
                <div v-if="msg.loading" class="typing-indicator">...</div>
            </div>
          </div>
        </div>
        <div class="input-area">
          <a-textarea
            v-model:value="inputPrompt"
            placeholder="描述越详细，页面越具体，可以一步一步完善生成效果..."
            :auto-size="{ minRows: 2, maxRows: 6 }"
            @pressEnter.prevent="onSendMessage"
          />
          <a-button type="primary" shape="circle" @click="onSendMessage" style="margin-left: 8px">
             ↑
          </a-button>
        </div>
      </div>

      <!-- Preview Area -->
      <div class="preview-area">
        <div class="preview-header">
            生成后的网页展示
        </div>
        <div class="iframe-container">
            <iframe 
                v-if="app"
                ref="iframeRef"
                :src="previewUrl" 
                title="App Preview"
                style="width: 100%; height: 100%; border: none;"
            ></iframe>
            <div v-else class="empty-preview">
                请先生成应用
            </div>
        </div>
      </div>
    </div>
  </div>
</template>

<style scoped>
.app-generator {
  height: calc(100vh - 64px); /* Subtract global header height if needed, assuming layout handles it */
  display: flex;
  flex-direction: column;
  background: #fff;
}
.header {
  height: 60px;
  border-bottom: 1px solid #f0f0f0;
  display: flex;
  justify-content: space-between;
  align-items: center;
  padding: 0 24px;
}
.left {
    display: flex;
    align-items: center;
    gap: 12px;
}
.app-name {
    font-size: 18px;
    font-weight: 500;
}
.content {
  flex: 1;
  display: flex;
  overflow: hidden;
}
.chat-area {
  width: 400px;
  border-right: 1px solid #f0f0f0;
  display: flex;
  flex-direction: column;
  background: #f9f9f9;
}
.message-list {
  flex: 1;
  overflow-y: auto;
  padding: 20px;
  display: flex;
  flex-direction: column;
  gap: 20px;
}
.message {
    display: flex;
    gap: 10px;
}
.message.user {
    flex-direction: row-reverse;
}
.avatar {
    width: 32px;
    height: 32px;
    border-radius: 50%;
    background: #e6f7ff;
    display: flex;
    align-items: center;
    justify-content: center;
    font-size: 20px;
}
.message.user .avatar {
    background: #f5f5f5;
}
.bubble {
    max-width: 80%;
    padding: 12px;
    border-radius: 8px;
    background: #fff;
    box-shadow: 0 2px 8px rgba(0,0,0,0.05);
}
.message.user .bubble {
    background: #1890ff;
    color: #fff;
}
.input-area {
  padding: 20px;
  border-top: 1px solid #f0f0f0;
  background: #fff;
  display: flex;
  align-items: flex-end;
}
.preview-area {
  flex: 1;
  display: flex;
  flex-direction: column;
  background: #fff;
}
.preview-header {
    height: 40px;
    display: flex;
    align-items: center;
    justify-content: center;
    color: #999;
    background: #fafafa;
    border-bottom: 1px solid #f0f0f0;
}
.iframe-container {
    flex: 1;
    position: relative;
}
.empty-preview {
    display: flex;
    align-items: center;
    justify-content: center;
    height: 100%;
    color: #ccc;
}
</style>
