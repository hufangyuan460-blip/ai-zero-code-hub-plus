<script setup lang="ts">
import { ref, onMounted, nextTick, computed } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { message } from 'ant-design-vue'
import { getMyAppInfo, deployApp, type AppVO } from '@/api/app'
import { listChatHistoryByPage, type ChatHistoryVO } from '@/api/chat'
import { useUserStore } from '@/stores/user'

const route = useRoute()
const router = useRouter()
const userStore = useUserStore()

const appId = route.params.appId as string
const app = ref<AppVO>()
const loading = ref(false)
const deploying = ref(false)
const codeGenType = ref('html') // 下拉选择：支持 'html' | 'multi_file' | 'vue_project'
const previewLoading = ref(false)

// Chat
interface Message {
  role: 'user' | 'ai'
  content: string
  loading?: boolean
}
const messages = ref<Message[]>([])
const inputPrompt = ref('')
const chatContainer = ref<HTMLElement>()

// History Pagination
const hasMore = ref(false)
const lastCreateTime = ref<string | undefined>(undefined)
const historyLoading = ref(false)

// Preview
const previewUrl = computed(() => {
  if (!app.value) return ''
  const base = `http://localhost:8123/api/static/${codeGenType.value || 'website'}_${app.value.id}`
  const path = codeGenType.value === 'vue_project' ? '/dist/index.html' : '/index.html'
  return `${base}${path}?t=${new Date().getTime()}`
})
const iframeRef = ref<HTMLIFrameElement>()

// Fetch App Info
const loadAppInfo = async () => {
  if (!appId) return
  loading.value = true
  try {
    const res = await getMyAppInfo(appId)
    if (res) {
      app.value = res
      if (res.codeGenType) {
        codeGenType.value = res.codeGenType
      }
    } else {
      message.error('应用不存在')
    }
  } catch {
    message.error('加载应用失败')
  } finally {
    loading.value = false
  }
}

// Load Chat History
const loadHistory = async (isLoadMore = false) => {
  if (!appId) return
  historyLoading.value = true
  try {
    const res = await listChatHistoryByPage({
      appId,
      pageSize: 10,
      lastCreateTime: isLoadMore ? lastCreateTime.value : undefined
    })
    
    if (res && res.records && res.records.length > 0) {
      // Backend returns newest first (DESC by createTime)
      // We want to prepend them to our messages list
      
      const newMessages: Message[] = res.records.map((item: ChatHistoryVO) => ({
        role: item.messageType === 'aiMessage' ? 'ai' : 'user',
        content: item.content
      }))
      
      // Reverse to get chronological order (oldest to newest)
      newMessages.reverse()
      
      if (isLoadMore) {
        messages.value.unshift(...newMessages)
      } else {
        messages.value = newMessages
      }
      
      // Update cursor (the oldest message in the newly fetched batch)
      // Since backend returned [Newest ... Oldest], the last item in res.records is the oldest
      const oldestRecord = res.records[res.records.length - 1]!
      lastCreateTime.value = oldestRecord.createTime
      
      // If we got a full page, assume there might be more
      hasMore.value = res.records.length >= 10
      
      if (!isLoadMore) {
        scrollToBottom()
      }
    } else {
        hasMore.value = false
    }
  } catch {
    message.error('加载历史记录失败')
  } finally {
    historyLoading.value = false
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
  const type = codeGenType.value
  // Fix garbled characters: use encodeURIComponent for userMessage
  // And ensure backend handles encoding correctly (it usually does with URL parameters)
  // Double check if backend requires specific charset in content-type, but GET query params are standard.
  const eventSource = new EventSource(
    `/api/app/chat/gen/code?appId=${appId}&userMessage=${encodeURIComponent(prompt)}&codeGenType=${type}`,
    { withCredentials: true }
  )

  eventSource.onmessage = (event) => {
    try {
      // 1. Check if the event data is a valid JSON string
      let data;
      try {
          data = JSON.parse(event.data);
      } catch {
          // Not a JSON object, treat as raw string
          data = event.data;
      }

      // 2. Handle structured data vs raw string
      // The backend usually sends JSON with 'content' field for chunks
      // But sometimes (e.g. from some models) it might send raw text or different structure
      if (typeof data === 'object' && data !== null) {
          if (data.content) {
             messages.value[aiMsgIndex]!.content += data.content
          } else {
             // Fallback: append stringified object or specific field if known
             // For now, if no content field, maybe ignore or append raw
             // console.warn("Received object without content field:", data);
             // Optionally append entire JSON if debugging:
             // messages.value[aiMsgIndex].content += JSON.stringify(data);
          }
      } else {
         // Raw string data
         messages.value[aiMsgIndex]!.content += data
      }

    } catch (e) {
      console.error("Error processing SSE message:", e);
      messages.value[aiMsgIndex]!.content += event.data
    }
    scrollToBottom()
  }

  eventSource.addEventListener('done', () => {
    messages.value[aiMsgIndex]!.loading = false
    eventSource.close()
    refreshPreview()
    // 生成完成后刷新历史记录
    loadHistory(false)
  })
  
  // EventSource 'error' events do not provide data payload; rely on onerror handler below

  eventSource.onerror = (event) => {
    console.error('SSE Error', event)
    eventSource.close()
    if (messages.value[aiMsgIndex]!.loading) {
        messages.value[aiMsgIndex]!.loading = false
        messages.value[aiMsgIndex]!.content += '\n[生成出错]'
    }
    // 出错也尝试刷新历史记录，捕获后端已保存的错误信息
    loadHistory(false)
  }
}

const refreshPreview = () => {
  if (iframeRef.value) {
    previewLoading.value = true
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
    const deployUrl = await deployApp({ appId: app.value.id })
    if (deployUrl) {
      message.success('部署成功')
      window.open(deployUrl, '_blank')
    }
  } catch {
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
  await loadHistory(false)
  
  const initPrompt = route.query.initPrompt
    ? decodeURIComponent(route.query.initPrompt as string)
    : ''
  // Only auto-send initPrompt if:
  // 1. It exists
  // 2. It's the owner of the app
  // 3. There is no chat history yet
  if (initPrompt && app.value && userStore.currentUser?.id === app.value.userId && messages.value.length === 0) {
    // Clear query param to avoid re-trigger on reload
    const newQuery = { ...route.query }
    delete newQuery.initPrompt
    delete newQuery.chatOnly
    
    router.replace({ query: newQuery })
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
        <a-tag color="blue">{{ codeGenType === 'vue_project' ? 'Vue 工程项目' : (codeGenType === 'multi_file' ? '原生多文件' : '原生 HTML') }}</a-tag>
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
          <div v-if="hasMore" class="load-more">
             <a-button type="link" size="small" :loading="historyLoading" @click="loadHistory(true)">加载更多历史消息</a-button>
          </div>
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
            <div v-if="messages.some(m => m.loading)" class="preview-loading">
                <a-spin tip="正在生成代码中，请稍候..." />
            </div>
            <!-- 
                Show iframe if:
                1. App info is loaded
                2. Not currently generating (loading handled by overlay above)
                3. We have a preview URL
                4. AND: We have at least some history OR the app is deployed/featured (priority > 0)
                   OR simply always show it and let it 404 if not found (better for existing apps with lost history)
            -->
            <iframe 
                v-if="app && !messages.some(m => m.loading)"
                ref="iframeRef"
                :src="previewUrl" 
                title="App Preview"
                style="width: 100%; height: 100%; border: none;"
            ></iframe>
            <div v-else-if="!messages.some(m => m.loading)" class="empty-preview">
                {{ app ? '暂无预览' : '请先生成应用' }}
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
.load-more {
    text-align: center;
    margin-bottom: 10px;
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
.preview-loading {
    position: absolute;
    top: 0;
    left: 0;
    right: 0;
    bottom: 0;
    display: flex;
    align-items: center;
    justify-content: center;
    background: rgba(255, 255, 255, 0.8);
    z-index: 10;
}
.empty-preview {
    display: flex;
    align-items: center;
    justify-content: center;
    height: 100%;
    color: #ccc;
}
</style>
