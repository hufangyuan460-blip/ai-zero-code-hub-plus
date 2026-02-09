<script setup lang="ts">
import { ref, onMounted, nextTick, computed, watch } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { message } from 'ant-design-vue'
import { getMyAppInfo, deployApp, getDownloadLink, type AppVO } from '@/api/app'
import { listChatHistoryByPage, type ChatHistoryVO } from '@/api/chat'
import { request } from '@/api/request'
import { useUserStore } from '@/stores/user'
import { useVisualEditor } from '@/composables/useVisualEditor'
import { FormOutlined, SendOutlined } from '@ant-design/icons-vue'

const route = useRoute()
const router = useRouter()
const userStore = useUserStore()
const { isEditMode, selectedElement, toggleEditMode, clearSelection, exitEditMode, initVisualEditor } = useVisualEditor()

const appId = route.params.appId as string
const app = ref<AppVO>()
const loading = ref(false)
const deploying = ref(false)
const deployedUrl = ref<string>('')
const codeGenType = ref('vue_project')
const codeGenTypeMap: Record<string, string> = {
  html: '原生 HTML',
  multi_file: '原生多文件',
  vue_project: 'Vue 工程项目(复杂项目)'
}
const previewLoading = ref(false)
// const coverStatus = ref<'idle' | 'loading' | 'loaded' | 'error'>('idle')
// const coverUrl = computed(() => app.value?.cover || '')
// const fallbackCover = `data:image/svg+xml;utf8,${encodeURIComponent('<svg xmlns="http://www.w3.org/2000/svg" width="640" height="360" viewBox="0 0 640 360"><rect width="640" height="360" fill="%23f5f5f5"/><text x="50%" y="50%" dominant-baseline="middle" text-anchor="middle" fill="%23999" font-size="20">暂无封面</text></svg>')}`
// const coverDisplayUrl = computed(() => {
//   // 如果正在生成中且没有封面，显示"应用生成中"
//   const isGenerating = messages.value.some(m => m.loading)
//   if (!coverUrl.value && isGenerating) {
//     // 使用一个简单的SVG作为生成中占位图
//     return `data:image/svg+xml;utf8,${encodeURIComponent('<svg xmlns="http://www.w3.org/2000/svg" width="640" height="360" viewBox="0 0 640 360"><rect width="640" height="360" fill="#f0f2f5"/><text x="50%" y="50%" dominant-baseline="middle" text-anchor="middle" fill="#1890ff" font-size="24" font-family="sans-serif">应用生成中...</text><circle cx="320" cy="240" r="20" stroke="#1890ff" stroke-width="3" fill="none"><animate attributeName="stroke-dasharray" from="0 150" to="150 150" dur="2s" repeatCount="indefinite"/><animate attributeName="stroke-dashoffset" from="0" to="-150" dur="2s" repeatCount="indefinite"/></circle></svg>')}`
//   }

//   if (!coverUrl.value || coverStatus.value === 'error') {
//     return fallbackCover
//   }
//   return coverUrl.value
// })

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
  if (deployedUrl.value) return deployedUrl.value
  if (!app.value) return ''
  // Use relative path for local preview to support Same-Origin (via proxy)
  return `/api/static/${codeGenType.value || 'website'}_${app.value.id}/index.html?t=${new Date().getTime()}`
})
const iframeRef = ref<HTMLIFrameElement>()
const downloading = ref(false)
const downloadStatus = ref<'idle' | 'preparing' | 'downloading' | 'success' | 'error'>('idle')
const downloadProgress = ref(0)
const downloadText = computed(() => {
  if (downloadStatus.value === 'preparing') return '准备中'
  if (downloadStatus.value === 'downloading') return '下载中'
  if (downloadStatus.value === 'success') return '已完成'
  if (downloadStatus.value === 'error') return '重试下载'
  return '下载源码'
})

// Fetch App Info
const loadAppInfo = async (silent = false) => {
  if (!appId) return
  if (!silent) {
    loading.value = true
  }
  try {
    const res = await getMyAppInfo(appId)
    if (res) {
      app.value = res
      // 强制使用 Vue 工程项目类型，确保触发工具调用与部署链路
      codeGenType.value = 'vue_project'
      if (res.deployKey && res.deployedTime) {
        deployedUrl.value = `/api/app/${res.deployKey}/index.html`
      } else {
        deployedUrl.value = ''
      }
    } else {
      message.error('应用不存在')
    }
  } catch {
    message.error('加载应用失败')
  } finally {
    if (!silent) {
      loading.value = false
    }
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

// 自动部署超时计时器
let deployTimeoutTimer: ReturnType<typeof setTimeout> | null = null;

const resetDeployTimeout = () => {
  if (deployTimeoutTimer) {
    clearTimeout(deployTimeoutTimer);
  }
  if (codeGenType.value === 'vue_project') {
    return
  }
  // 如果 60 秒没有收到新消息，强制结束并部署
  deployTimeoutTimer = setTimeout(() => {
    console.log('SSE Stream timeout, forcing deploy...');
    forceDeploy();
  }, 60000); // 60s 超时
};

const forceDeploy = async () => {
  if (eventSourceRef.value) {
    eventSourceRef.value.close();
  }
  if (messages.value[aiMsgIndexRef.value]) {
    messages.value[aiMsgIndexRef.value]!.loading = false;
  }

  if (app.value && codeGenType.value === 'vue_project') {
      try {
        deploying.value = true
        message.loading({ content: '生成完毕（或长时间未响应），正在自动部署中（Vue项目构建可能需要数分钟），请耐心等待...', key: 'auto_deploy', duration: 0 })
        const url = await deployApp({ appId: app.value.id })
        if (url) {
          deployedUrl.value = url
          message.success({ content: '部署成功，已更新预览', key: 'auto_deploy' })
        }
      } catch (e) {
        message.error({ content: '自动部署失败', key: 'auto_deploy' })
      } finally {
        deploying.value = false
        // 刷新历史记录
        loadHistory(false)
      }
    } else {
      deployedUrl.value = ''
      refreshPreview()
      loadHistory(false)
    }
};

// SSE Generation
const eventSourceRef = ref<EventSource | null>(null);
const aiMsgIndexRef = ref<number>(0);

const onGenerate = async (prompt: string) => {
  if (!app.value || !prompt) return

  // Add User Message
  messages.value.push({ role: 'user', content: prompt })
  inputPrompt.value = ''

  // Add AI Placeholder
  const aiMsgIndex = messages.value.push({ role: 'ai', content: '', loading: true }) - 1
  aiMsgIndexRef.value = aiMsgIndex;

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
  eventSourceRef.value = eventSource;

  // 启动超时计时器
  resetDeployTimeout();

  eventSource.onmessage = (event) => {
    // 收到任意消息都重置超时计时器
    resetDeployTimeout();

    try {
      let data;
      try {
          data = JSON.parse(event.data);
      } catch {
          data = event.data;
      }

      if (typeof data === 'object' && data !== null) {
          if (data.type) {
             if (data.type === 'ai_response' && typeof data.data === 'string') {
               messages.value[aiMsgIndex]!.content += data.data
             } else if (data.type === 'tool_request') {
               messages.value[aiMsgIndex]!.content += '\n\n[选择工具] 写入文件\n\n'
             } else if (data.type === 'tool_executed') {
               try {
                 const args = JSON.parse(data.arguments || '{}')
                 const filePath = args.relativeFilePath || ''
                 const content = args.content || ''
                 const suffix = (filePath.split('.').pop() || '')
                 const block = `\n\n[工具调用] 写入文件 ${filePath}\n\`\`\`${suffix}\n${content}\n\`\`\`\n\n`
                 messages.value[aiMsgIndex]!.content += block
               } catch {
                 messages.value[aiMsgIndex]!.content += JSON.stringify(data)
               }
             } else {
               messages.value[aiMsgIndex]!.content += JSON.stringify(data)
             }
          } else if (data.content) {
             messages.value[aiMsgIndex]!.content += data.content
          } else {
             messages.value[aiMsgIndex]!.content += JSON.stringify(data)
          }
      } else {
         messages.value[aiMsgIndex]!.content += data
      }

    } catch (e) {
      console.error("Error processing SSE message:", e);
      messages.value[aiMsgIndex]!.content += event.data
    }
    scrollToBottom()
  }

  eventSource.addEventListener('done', async () => {
    if (deployTimeoutTimer) clearTimeout(deployTimeoutTimer);
    messages.value[aiMsgIndex]!.loading = false
    eventSource.close()

    if (app.value && codeGenType.value === 'vue_project') {
      try {
        deploying.value = true
        message.loading({ content: '生成完毕，正在自动部署中（Vue项目构建可能需要数分钟），请耐心等待...', key: 'auto_deploy', duration: 0 })
        const url = await deployApp({ appId: app.value.id })
        if (url) {
          deployedUrl.value = url
          message.success({ content: '部署成功，已更新预览', key: 'auto_deploy' })
        }
      } catch (e) {
        message.error({ content: '自动部署失败', key: 'auto_deploy' })
      } finally {
        deploying.value = false
        // 刷新历史记录
        loadHistory(false)
      }
    } else {
      deployedUrl.value = ''
      refreshPreview()
      loadHistory(false)
    }
  })

  // EventSource 'error' events do not provide data payload; rely on onerror handler below

  eventSource.onerror = (event) => {
    if (deployTimeoutTimer) clearTimeout(deployTimeoutTimer);
    console.error('SSE Error', event)
    eventSource.close()
    if (messages.value[aiMsgIndex]!.loading) {
        messages.value[aiMsgIndex]!.loading = false
        messages.value[aiMsgIndex]!.content += '\n[生成出错或连接中断]'
    }
    // 出错也尝试刷新历史记录，捕获后端已保存的错误信息
    loadHistory(false)

    // 连接中断时不强制部署，避免部署不完整的代码
    // 只有在超时的情况下（上面的 setTimeout）才尝试强制部署
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


const parseFileName = (disposition?: string) => {
  if (!disposition) return ''
  const match = disposition.match(/filename\*?=(?:UTF-8''|utf-8'')?([^;]+)/i)
  if (!match?.[1]) return ''
  const fileName = match[1].trim().replace(/(^"|"$)/g, '')
  try {
    return decodeURIComponent(fileName)
  } catch {
    return fileName
  }
}

const saveBlob = (blob: Blob, fileName: string) => {
  const url = URL.createObjectURL(blob)
  const link = document.createElement('a')
  link.href = url
  link.download = fileName
  document.body.appendChild(link)
  link.click()
  link.remove()
  URL.revokeObjectURL(url)
}

const handleDownload = async () => {
  if (!app.value) return
  downloading.value = true
  downloadStatus.value = 'preparing'
  downloadProgress.value = 0
  try {
    const link = await getDownloadLink(app.value.id)
    downloadStatus.value = 'downloading'
    const res = await request.get(link, {
      responseType: 'blob',
      transformResponse: data => data,
      onDownloadProgress: (event) => {
        if (event.total) {
          downloadProgress.value = Math.round((event.loaded / event.total) * 100)
        }
      }
    })
    const blob = res.data instanceof Blob ? res.data : new Blob([res.data])
    if (!blob.size) {
      throw new Error('下载内容为空')
    }
    const fileName = parseFileName(res.headers['content-disposition']) || `${app.value.id}.zip`
    saveBlob(blob, fileName)
    downloadStatus.value = 'success'
    message.success('下载完成')
  } catch (e) {
    downloadStatus.value = 'error'
    message.error((e as Error)?.message || '下载失败')
  } finally {
    downloading.value = false
    if (downloadStatus.value === 'success') {
      setTimeout(() => {
        downloadStatus.value = 'idle'
        downloadProgress.value = 0
      }, 1500)
    }
  }
}

const onSendMessage = () => {
    let prompt = inputPrompt.value.trim();
    if (selectedElement.value) {
        const elInfo = `\n\n【用户选中的元素信息】\n标签: ${selectedElement.value.tagName}\nXPath: ${selectedElement.value.xpath}\n文本内容: ${selectedElement.value.text}\nHTML片段: ${selectedElement.value.html}\n`;
        prompt += elInfo;

        // Clear selection and exit edit mode
        exitEditMode(iframeRef.value);
    }

    if (prompt) {
        onGenerate(prompt)
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

// watch(coverUrl, (value) => {
//   if (value) {
//     coverStatus.value = 'loading'
//   } else {
//     coverStatus.value = 'idle'
//   }
// }, { immediate: true })

// const onCoverLoad = () => {
//   coverStatus.value = 'loaded'
// }

// const onCoverError = () => {
//   coverStatus.value = 'error'
// }

const onIframeLoad = () => {
  if (isEditMode.value && iframeRef.value) {
    initVisualEditor(iframeRef.value)
    // Re-enable edit mode in the new iframe document
    setTimeout(() => {
      iframeRef.value?.contentWindow?.postMessage({ type: 'VE_START' }, '*')
    }, 100)
  }
}
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
        <a-tag color="blue" style="font-size: 14px; padding: 4px 10px;">{{ codeGenTypeMap[codeGenType] || codeGenType }}</a-tag>
      </div>
      <div class="right">
        <div class="download-wrap">
          <a-button :loading="downloading" @click="handleDownload">{{ downloadText }}</a-button>
          <a-progress v-if="downloadStatus === 'downloading' && downloadProgress > 0" :percent="downloadProgress" size="small" :show-info="false" />
          <span v-else-if="downloadStatus === 'error'" class="download-error">下载失败</span>
        </div>
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
          <div v-if="selectedElement" style="margin-bottom: 10px; width: 100%;">
              <a-alert type="info" show-icon closable @close="clearSelection(iframeRef)">
                  <template #message>
                      <span style="font-weight: bold;">已选中元素:</span> &lt;{{ selectedElement.tagName }}&gt; {{ selectedElement.text.length > 50 ? selectedElement.text.substring(0, 50) + '...' : selectedElement.text }}
                  </template>
              </a-alert>
          </div>
          <div class="input-controls">
            <a-button
                :type="isEditMode ? 'primary' : 'default'"
                :danger="isEditMode"
                @click="toggleEditMode(iframeRef)"
                style="margin-right: 8px"
                :title="isEditMode ? '退出编辑模式' : '进入可视化编辑模式'"
            >
                <template #icon><FormOutlined /></template>
            </a-button>
            <a-textarea
                v-model:value="inputPrompt"
                placeholder="描述越详细，页面越具体，可以一步一步完善生成效果..."
                :auto-size="{ minRows: 2, maxRows: 6 }"
                @pressEnter.prevent="onSendMessage"
            />
            <a-button type="primary" shape="circle" @click="onSendMessage" style="margin-left: 8px">
                <template #icon><SendOutlined /></template>
            </a-button>
          </div>
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
                @load="onIframeLoad"
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
.right {
    display: flex;
    align-items: center;
    gap: 12px;
}
.download-wrap {
    display: flex;
    flex-direction: column;
    gap: 4px;
    align-items: flex-start;
}
.download-error {
    color: #ff4d4f;
    font-size: 12px;
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
  flex-direction: column;
}
.input-controls {
  display: flex;
  align-items: flex-end;
  width: 100%;
}
.preview-area {
  flex: 1;
  display: flex;
  flex-direction: column;
  background: #fff;
}
.cover-section {
  padding: 16px 20px 8px 20px;
}
.cover-title {
  display: flex;
  align-items: center;
  gap: 8px;
  font-size: 14px;
  color: #333;
  margin-bottom: 10px;
}
.cover-status {
  font-size: 12px;
  color: #999;
}
.cover-status.error {
  color: #ff4d4f;
}
.cover-body {
  position: relative;
  width: 100%;
  padding-top: 56.25%;
  background: #fafafa;
  border-radius: 8px;
  overflow: hidden;
}
.cover-image {
  position: absolute;
  top: 0;
  left: 0;
  width: 100%;
  height: 100%;
  object-fit: cover;
}
.cover-spin {
  position: absolute;
  top: 50%;
  left: 50%;
  transform: translate(-50%, -50%);
  z-index: 1;
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
