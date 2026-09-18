<script setup lang="ts">
import { ref, onMounted, nextTick, computed } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { message } from 'ant-design-vue'
import { getMyAppInfo, deployApp, getDownloadLink, cancelGeneration, captureAndUploadScreenshot, createGeneration, getGeneration, generationStreamUrl, type AppVO } from '@/api/app'
import { listChatHistoryByPage, type ChatHistoryVO } from '@/api/chat'
import { request } from '@/api/request'
import { useUserStore } from '@/stores/user'
import { useVisualEditor } from '@/composables/useVisualEditor'
import { FormOutlined, SendOutlined, FullscreenOutlined } from '@ant-design/icons-vue'
import { canStartAutoDeployment } from '@/utils/generationRunPolicy'

const route = useRoute()
const router = useRouter()
const userStore = useUserStore()
const { isEditMode, selectedElement, toggleEditMode, clearSelection, exitEditMode, initVisualEditor } = useVisualEditor()

const appId = route.params.appId as string
const app = ref<AppVO>()
const loading = ref(false)
const deploying = ref(false)
const deployFailed = ref(false)
const deployError = ref('')
const deployedUrl = ref<string>('')
const codeGenType = ref('')
const codeGenTypeMap: Record<string, string> = {
  html: '原生 HTML',
  multi_file: '原生多文件',
  vue_project: 'Vue 工程项目(复杂项目)'
}
const displayCodeGenType = computed(() => {
  const type = app.value?.codeGenType || codeGenType.value
  if (!type) return '加载中'
  return codeGenTypeMap[type] || type
})
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
type ExecutionMode = 'DIRECT' | 'WORKFLOW'
type RunStatus = 'PENDING' | 'RUNNING' | 'SUCCEEDED' | 'FAILED' | 'CANCELLED' | 'TIMED_OUT'
interface WorkflowStep {
  name: string
  status: string
}
const messages = ref<Message[]>([])
const inputPrompt = ref('')
const chatContainer = ref<HTMLElement>()
const executionMode = ref<ExecutionMode>('DIRECT')
const lastExecutionMode = ref<ExecutionMode>('DIRECT')
const workflowSteps = ref<WorkflowStep[]>([])
const workflowFailed = ref(false)
const workflowError = ref('')
const lastFailedPrompt = ref('')
const activeRunId = ref<string | null>(null)
const runStatus = ref<RunStatus | null>(null)
const runCurrentStep = ref('')
const runRetryCount = ref(0)
const runMaxRetryCount = ref(2)
const cancellingRun = ref(false)
const settlingRun = ref(false)
const isGenerating = computed(() => messages.value.some(message => message.loading) || settlingRun.value)
const canCancelGeneration = computed(() => Boolean(
  activeRunId.value && (runStatus.value === 'PENDING' || runStatus.value === 'RUNNING'),
))
const executionModeDescription = computed(() => executionMode.value === 'WORKFLOW'
  ? '会执行规划、资源收集、质检和构建，耗时较长。'
  : '速度快，适合小修改和连续对话。')
const runStatusLabelMap: Record<string, string> = {
  PENDING: '排队中',
  RUNNING: '运行中',
  SUCCEEDED: '已完成',
  FAILED: '失败',
  CANCELLED: '已取消',
  TIMED_OUT: '已超时',
}
const runStatusLabel = computed(() => runStatusLabelMap[runStatus.value || ''] || '未开始')
const activeRunStorageKey = computed(() => `agent:active-run:${appId}`)
const activeRunSequenceStorageKey = computed(() => `${activeRunStorageKey.value}:sequence`)

// History Pagination
const hasMore = ref(false)
const lastCreateTime = ref<string | undefined>(undefined)
const historyLoading = ref(false)

// Preview
const previewUrl = computed(() => {
  if (deployedUrl.value) return deployedUrl.value
  if (!app.value) return ''
  // Use relative path for local preview to support Same-Origin (via proxy)
  // 修正预览路径逻辑：
  // 1. 如果是 vue_project，目录通常是 vue_project_{appId}
  // 2. 如果是 html 或 multi_file，目录通常是 website_{appId}
  const type = app.value?.codeGenType || codeGenType.value
  const dirPrefix = type === 'vue_project' ? 'vue_project' : 'website'
  return `/api/static/${dirPrefix}_${app.value.id}/index.html?t=${new Date().getTime()}`
})
const iframeRef = ref<HTMLIFrameElement>()
const downloading = ref(false)
const downloadStatus = ref<'idle' | 'preparing' | 'downloading' | 'success' | 'error'>('idle')
const downloadProgress = ref(0)
const canDownload = computed(() => {
  if (deploying.value) return false
  if (deployedUrl.value) return true
  return Boolean(app.value?.deployKey && app.value?.deployedTime)
})
const downloadText = computed(() => {
  if (downloadStatus.value === 'preparing') return '准备中'
  if (downloadStatus.value === 'downloading') return '下载中'
  if (downloadStatus.value === 'success') return '已完成'
  if (downloadStatus.value === 'error') return '重试下载'
  if (!canDownload.value) return deployFailed.value ? '请先重试部署' : '部署完成后下载'
  return '下载源码'
})

const getErrorMessage = (error: unknown, fallback: string) => {
  if (error instanceof Error && error.message) return error.message
  if (typeof error === 'object' && error !== null) {
    const responseMessage = (error as { response?: { data?: { message?: string } } }).response?.data?.message
    if (responseMessage) return responseMessage
  }
  return fallback
}

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
      // 使用后端返回的实际类型
      codeGenType.value = res.codeGenType || 'html'
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

const handleFullscreen = () => {
  if (previewUrl.value) {
    window.open(previewUrl.value, '_blank')
  } else {
    message.warning('暂无预览链接')
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

const deployCurrentApp = async (loadingMsg: string) => {
  if (!app.value || deploying.value) return false

  deploying.value = true
  deployFailed.value = false
  deployError.value = ''
  message.loading({ content: loadingMsg, key: 'auto_deploy', duration: 0 })

  try {
    const url = await deployApp({ appId: app.value.id })
    if (!url) {
      throw new Error('部署接口未返回部署地址')
    }

    deployedUrl.value = url
    deployFailed.value = false
    message.success({ content: '部署成功，已更新预览', key: 'auto_deploy' })

    try {
      const fullUrl = window.location.origin + url
      await captureAndUploadScreenshot(app.value.id, fullUrl)
      console.log('自动生成封面成功')
    } catch (screenshotError) {
      console.error('自动生成封面失败', screenshotError)
    }
    return true
  } catch (error) {
    deployFailed.value = true
    deployError.value = getErrorMessage(error, '部署失败，请稍后重试')
    message.error({ content: `部署失败：${deployError.value}`, key: 'auto_deploy' })
    return false
  } finally {
    deploying.value = false
    loadHistory(false)
    if (codeGenType.value !== 'vue_project') {
      refreshPreview()
    }
  }
}

const retryDeploy = async () => {
  await deployCurrentApp('正在重新部署，请耐心等待...')
}

// SSE Generation
const eventSourceRef = ref<EventSource | null>(null);
const aiMsgIndexRef = ref<number>(0);
const reconnecting = ref(false)
const reconnectAttempt = ref(0)
const reconnectTimer = ref<ReturnType<typeof setTimeout> | null>(null)
const reconnectAttempts = new Map<string, number>()
const terminalRunIds = new Set<string>()
const businessErrorRunIds = new Set<string>()
const deploymentRunIds = new Set<string>()
const deploymentInFlightRunIds = new Set<string>()
const MAX_RECONNECT_ATTEMPTS = 5

const deploymentStorageKey = (runId: string) => `agent:deployment:${appId}:${runId}`
const getDeploymentState = (runId: string) => sessionStorage.getItem(deploymentStorageKey(runId))
const markDeploymentStarted = (runId: string) => {
  deploymentInFlightRunIds.add(runId)
  sessionStorage.setItem(deploymentStorageKey(runId), 'in_progress')
}
const markDeploymentSucceeded = (runId: string) => {
  deploymentInFlightRunIds.delete(runId)
  deploymentRunIds.add(runId)
  sessionStorage.setItem(deploymentStorageKey(runId), 'succeeded')
}
const clearDeploymentAfterFailure = (runId: string) => {
  deploymentInFlightRunIds.delete(runId)
  deploymentRunIds.delete(runId)
  sessionStorage.removeItem(deploymentStorageKey(runId))
}
const lastSequenceForRun = (runId: string) => {
  const stored = sessionStorage.getItem(activeRunSequenceStorageKey.value)
  if (!stored) return 0
  try {
    const value = JSON.parse(stored) as { runId?: string, sequence?: number }
    return value.runId === runId && Number.isFinite(value.sequence) ? Number(value.sequence) : 0
  } catch {
    return 0
  }
}
const persistSequence = (runId: string, sequence: unknown) => {
  const numericSequence = typeof sequence === 'number' ? sequence : Number(sequence)
  if (!Number.isFinite(numericSequence) || numericSequence <= 0) return
  if (numericSequence <= lastSequenceForRun(runId)) return
  sessionStorage.setItem(activeRunSequenceStorageKey.value, JSON.stringify({ runId, sequence: numericSequence }))
}

const appendToAiMessage = (index: number, content: string) => {
  if (content && messages.value[index]) {
    messages.value[index]!.content += content
  }
}

const parseEventData = (eventData: string): unknown => {
  try {
    return JSON.parse(eventData)
  } catch {
    return eventData
  }
}

const updateRunStateFromEvent = (eventData: string, eventSequence?: string): Record<string, unknown> | null => {
  const data = parseEventData(eventData)
  if (typeof data !== 'object' || data === null) return null
  const typedData = data as Record<string, unknown>
  if (typeof typedData.runId === 'string') {
    activeRunId.value = typedData.runId
    sessionStorage.setItem(activeRunStorageKey.value, typedData.runId)
    persistSequence(typedData.runId, typedData.sequence ?? eventSequence)
  }
  if (typeof typedData.status === 'string') {
    const status = typedData.status as RunStatus
    if (['PENDING', 'RUNNING', 'SUCCEEDED', 'FAILED', 'CANCELLED', 'TIMED_OUT'].includes(status)) {
      runStatus.value = status
    }
  }
  if (typeof typedData.currentStep === 'string') runCurrentStep.value = typedData.currentStep
  if (typeof typedData.retryCount === 'number') runRetryCount.value = typedData.retryCount
  if (typeof typedData.retryCount === 'string' && Number.isFinite(Number(typedData.retryCount))) {
    runRetryCount.value = Number(typedData.retryCount)
  }
  if (typeof typedData.maxRetryCount === 'number') runMaxRetryCount.value = typedData.maxRetryCount
  if (typeof typedData.maxRetryCount === 'string' && Number.isFinite(Number(typedData.maxRetryCount))) {
    runMaxRetryCount.value = Number(typedData.maxRetryCount)
  }
  return typedData
}

const clearActiveRun = () => {
  activeRunId.value = null
  cancellingRun.value = false
  sessionStorage.removeItem(activeRunStorageKey.value)
  sessionStorage.removeItem(activeRunSequenceStorageKey.value)
  reconnecting.value = false
  reconnectAttempt.value = 0
  if (reconnectTimer.value) {
    clearTimeout(reconnectTimer.value)
    reconnectTimer.value = null
  }
}

const appendGenerationMessage = (eventData: string, messageIndex: number) => {
  const data = parseEventData(eventData)
  if (typeof data !== 'object' || data === null) {
    appendToAiMessage(messageIndex, String(data))
    return
  }

  const typedData = data as Record<string, unknown>
  if (typeof typedData.message === 'string') {
    appendToAiMessage(messageIndex, typedData.message)
    return
  }
  if (typedData.type === 'ai_response' && typeof typedData.data === 'string') {
    appendToAiMessage(messageIndex, typedData.data)
    return
  }
  if (typedData.type === 'tool_request') {
    const toolName = typeof typedData.name === 'string' ? typedData.name : '工具'
    appendToAiMessage(messageIndex, `\n\n[选择工具] ${toolName}\n\n`)
    return
  }
  if (typedData.type === 'tool_executed') {
    let path = ''
    try {
      const args = JSON.parse(typeof typedData.arguments === 'string' ? typedData.arguments : '{}') as Record<string, unknown>
      path = typeof args.relativeFilePath === 'string'
        ? args.relativeFilePath
        : typeof args.relativeDirPath === 'string' ? args.relativeDirPath : ''
    } catch {
      // 工具参数只用于内部处理，不直接展示给用户。
    }
    const toolName = typeof typedData.name === 'string' ? typedData.name : '工具'
    const actionMap: Record<string, string> = {
      writeFile: '写入文件',
      modifyFile: '修改文件',
      readFile: '读取文件',
      deleteFile: '删除文件',
      getProjectFileTree: '获取目录结构',
    }
    appendToAiMessage(messageIndex, `\n\n[工具调用] ${actionMap[toolName] || toolName}${path ? `：${path}` : ''}\n\n`)
    return
  }
  if (typeof typedData.content === 'string') {
    appendToAiMessage(messageIndex, typedData.content)
    return
  }
  appendToAiMessage(messageIndex, JSON.stringify(data))
}

const handleWorkflowStep = (eventData: string) => {
  const data = parseEventData(eventData)
  if (typeof data !== 'object' || data === null) return
  const stepData = data as Record<string, unknown>
  if (typeof stepData.step !== 'string') return
  const status = typeof stepData.status === 'string' ? stepData.status : '进行中'
  const existing = workflowSteps.value.find(step => step.name === stepData.step)
  if (existing) {
    existing.status = status
  } else {
    workflowSteps.value.push({ name: stepData.step, status })
  }
}

const markBusinessError = (runId: string, messageText: string, prompt: string, messageIndex: number) => {
  workflowFailed.value = true
  workflowError.value = messageText
  lastFailedPrompt.value = prompt
  if (!businessErrorRunIds.has(runId)) {
    businessErrorRunIds.add(runId)
    appendToAiMessage(messageIndex, `\n[生成失败：${messageText}]`)
  }
  if (messages.value[messageIndex]) messages.value[messageIndex]!.loading = false
  settlingRun.value = true
}

const finishRun = async (runId: string, status: RunStatus, messageText: string, messageIndex: number, prompt = '') => {
  if (terminalRunIds.has(runId)) return
  terminalRunIds.add(runId)
  if (eventSourceRef.value) {
    eventSourceRef.value.close()
    eventSourceRef.value = null
  }
  if (messages.value[messageIndex]) messages.value[messageIndex]!.loading = false
  if (status === 'CANCELLED' && !businessErrorRunIds.has(runId)) {
    appendToAiMessage(messageIndex, '\n[生成任务已取消]')
    workflowFailed.value = true
    workflowError.value = '生成任务已取消'
  } else if ((status === 'FAILED' || status === 'TIMED_OUT') && !businessErrorRunIds.has(runId)) {
    const safeMessage = messageText || (status === 'TIMED_OUT' ? '生成任务超时，请稍后重试' : '生成失败，请稍后重试')
    appendToAiMessage(messageIndex, `\n[生成失败：${safeMessage}]`)
    workflowFailed.value = true
    workflowError.value = safeMessage
    lastFailedPrompt.value = prompt || lastFailedPrompt.value
  }
  runStatus.value = status
  clearActiveRun()
  settlingRun.value = false
  await loadHistory(false)
  const deploymentState = getDeploymentState(runId)
  if (status === 'SUCCEEDED' && deploymentState === 'in_progress' && !deploymentInFlightRunIds.has(runId)) {
    deployFailed.value = true
    deployError.value = '上次部署状态未知，请重试部署'
  }
  if (status === 'SUCCEEDED' && app.value
    && canStartAutoDeployment(runId, deploymentRunIds, deploymentInFlightRunIds, deploymentState as 'in_progress' | 'succeeded' | null)) {
    markDeploymentStarted(runId)
    const loadingMsg = codeGenType.value === 'vue_project'
      ? '生成完毕，正在自动部署中（Vue项目构建可能需要数分钟），请耐心等待...'
      : '生成完毕，正在自动部署中...'
    const deployed = await deployCurrentApp(loadingMsg)
    if (deployed) {
      markDeploymentSucceeded(runId)
    } else {
      clearDeploymentAfterFailure(runId)
    }
  }
}

const scheduleRunReconnect = (runId: string, messageIndex: number, prompt: string) => {
  if (terminalRunIds.has(runId) || reconnectTimer.value) return
  const attempt = reconnectAttempts.get(runId) || 0
  if (attempt >= MAX_RECONNECT_ATTEMPTS) {
    reconnecting.value = true
    void getGeneration(runId).then(async status => {
      updateRunStateFromEvent(JSON.stringify(status))
      if (status.status !== 'PENDING' && status.status !== 'RUNNING') {
        await finishRun(runId, status.status, status.errorMessage || '', messageIndex, prompt)
      } else {
        message.warning('连接恢复失败，生成任务仍在服务器执行中，可继续取消或稍后刷新查看状态')
      }
    }).catch(() => {
      message.warning('暂时无法查询生成状态，任务仍保留在服务器，可继续取消或稍后刷新')
    })
    return
  }
  const nextAttempt = attempt + 1
  reconnectAttempts.set(runId, nextAttempt)
  reconnectAttempt.value = nextAttempt
  reconnecting.value = true
  const delay = Math.min(1000 * (2 ** (nextAttempt - 1)), 8000)
  reconnectTimer.value = setTimeout(async () => {
    reconnectTimer.value = null
    if (terminalRunIds.has(runId)) return
    try {
      const status = await getGeneration(runId)
      updateRunStateFromEvent(JSON.stringify(status))
      if (status.status !== 'PENDING' && status.status !== 'RUNNING') {
        await finishRun(runId, status.status, status.errorMessage || '', messageIndex, prompt)
        return
      }
      openRunStream(runId, messageIndex, prompt, true)
    } catch {
      scheduleRunReconnect(runId, messageIndex, prompt)
    }
  }, delay)
}

const handleNetworkDisconnect = (runId: string, messageIndex: number, prompt: string) => {
  if (terminalRunIds.has(runId)) return
  if (eventSourceRef.value) {
    eventSourceRef.value.close()
    eventSourceRef.value = null
  }
  // A browser/network error is not a server FAILED state. Keep sessionStorage and
  // the cooperative cancel path available while the bounded reconnect loop runs.
  reconnecting.value = true
  scheduleRunReconnect(runId, messageIndex, prompt)
}

const openRunStream = (runId: string, messageIndex: number, prompt: string, isReconnect = false) => {
  if (terminalRunIds.has(runId)) return
  if (eventSourceRef.value) eventSourceRef.value.close()
  const eventSource = new EventSource(generationStreamUrl(runId, lastSequenceForRun(runId)), { withCredentials: true })
  eventSourceRef.value = eventSource
  reconnecting.value = false
  if (!isReconnect) {
    reconnectAttempts.set(runId, 0)
    reconnectAttempt.value = 0
  }
  const handleMessageEvent = (event: MessageEvent) => {
    updateRunStateFromEvent(event.data, event.lastEventId)
    try {
      appendGenerationMessage(event.data, messageIndex)
    } catch {
      appendToAiMessage(messageIndex, event.data)
    }
    scrollToBottom()
  }
  eventSource.addEventListener('message', handleMessageEvent)
  eventSource.addEventListener('run_started', event => {
    const typedEvent = event as MessageEvent
    updateRunStateFromEvent(typedEvent.data, typedEvent.lastEventId)
  })
  eventSource.addEventListener('workflow_start', event => {
    const typedEvent = event as MessageEvent
    updateRunStateFromEvent(typedEvent.data, typedEvent.lastEventId)
    workflowFailed.value = false
    workflowError.value = ''
    const data = parseEventData((event as MessageEvent).data)
    if (typeof data === 'object' && data !== null && typeof (data as Record<string, unknown>).message === 'string'
      && !workflowSteps.value.some(step => step.name === '增强工作流')) {
      workflowSteps.value.push({ name: '增强工作流', status: (data as Record<string, unknown>).message as string })
    }
  })
  eventSource.addEventListener('step_started', event => {
    const typedEvent = event as MessageEvent
    updateRunStateFromEvent(typedEvent.data, typedEvent.lastEventId)
    handleWorkflowStep((event as MessageEvent).data)
  })
  eventSource.addEventListener('step_completed', event => {
    const typedEvent = event as MessageEvent
    updateRunStateFromEvent(typedEvent.data, typedEvent.lastEventId)
    handleWorkflowStep((event as MessageEvent).data)
  })
  eventSource.addEventListener('workflow_completed', event => {
    const typedEvent = event as MessageEvent
    updateRunStateFromEvent(typedEvent.data, typedEvent.lastEventId)
    handleWorkflowStep(JSON.stringify({ step: '增强工作流', status: '完成' }))
  })
  // Business failures use a dedicated event name. EventSource.onerror remains
  // reserved for network/protocol failures and must not start a reconnect.
  eventSource.addEventListener('generation_error', event => {
    const typedEvent = event as MessageEvent
    const data = updateRunStateFromEvent(typedEvent.data || '', typedEvent.lastEventId)
    const errorMessage = typeof data?.message === 'string' ? data.message : '生成失败，请稍后重试'
    markBusinessError(runId, errorMessage, prompt, messageIndex)
    scrollToBottom()
  })
  eventSource.addEventListener('cancelled', event => {
    const typedEvent = event as MessageEvent
    const data = updateRunStateFromEvent(typedEvent.data || '', typedEvent.lastEventId)
    void finishRun(runId, 'CANCELLED', typeof data?.message === 'string' ? data.message : '生成任务已取消', messageIndex, prompt)
  })
  eventSource.addEventListener('done', event => {
    const typedEvent = event as MessageEvent
    const data = updateRunStateFromEvent(typedEvent.data || '', typedEvent.lastEventId)
    const candidateStatus = data?.status as RunStatus
    const finalStatus = ['SUCCEEDED', 'FAILED', 'CANCELLED', 'TIMED_OUT'].includes(candidateStatus)
      ? candidateStatus
      : 'FAILED'
    void finishRun(runId, finalStatus, typeof data?.message === 'string' ? data.message : '', messageIndex, prompt)
  })
  eventSource.addEventListener('replay_reset', event => {
    const typedEvent = event as MessageEvent
    const data = updateRunStateFromEvent(typedEvent.data || '', typedEvent.lastEventId)
    const firstAvailable = Number(data?.firstAvailableSequence)
    if (Number.isFinite(firstAvailable) && firstAvailable > 0) {
      persistSequence(runId, firstAvailable - 1)
    }
    // The missing prefix cannot be reconstructed from the bounded server
    // window. Querying the authoritative state before resuming prevents a
    // stale client from changing deployment or terminal state.
    void getGeneration(runId).then(async status => {
      updateRunStateFromEvent(JSON.stringify(status))
      if (status.status !== 'PENDING' && status.status !== 'RUNNING') {
        await finishRun(runId, status.status, status.errorMessage || '', messageIndex, prompt)
      } else if (!terminalRunIds.has(runId)) {
        openRunStream(runId, messageIndex, prompt, true)
      }
    }).catch(() => {
      reconnecting.value = true
      message.warning('连接恢复中，暂时无法查询生成状态')
    })
  })
  eventSource.onerror = () => handleNetworkDisconnect(runId, messageIndex, prompt)
}

const onGenerate = async (prompt: string, requestedMode: ExecutionMode = executionMode.value) => {
  if (!app.value || !prompt || isGenerating.value || deploying.value) return

  deployFailed.value = false
  deployError.value = ''
  workflowFailed.value = false
  workflowError.value = ''
  activeRunId.value = null
  runStatus.value = 'PENDING'
  runCurrentStep.value = '排队中'
  runRetryCount.value = 0
  runMaxRetryCount.value = 2
  cancellingRun.value = false
  settlingRun.value = false
  sessionStorage.removeItem(activeRunSequenceStorageKey.value)
  lastExecutionMode.value = requestedMode
  if (requestedMode === 'WORKFLOW') {
    workflowSteps.value = []
  }

  // Add User Message
  messages.value.push({ role: 'user', content: prompt })
  inputPrompt.value = ''

  // Add AI Placeholder
  const aiMsgIndex = messages.value.push({ role: 'ai', content: '', loading: true }) - 1
  aiMsgIndexRef.value = aiMsgIndex;

  scrollToBottom()

  const type = codeGenType.value
  const mode = requestedMode
  let createdRun
  try {
    // 先 POST 创建运行，再用 runId 建立 SSE；提示词不会进入 URL。
    createdRun = await createGeneration({
      appId,
      userMessage: prompt,
      codeGenType: type,
      executionMode: mode,
    })
    activeRunId.value = createdRun.runId
    runStatus.value = createdRun.status
    sessionStorage.setItem(activeRunStorageKey.value, createdRun.runId)
  } catch (error) {
    const errorMessage = getErrorMessage(error, '创建生成任务失败')
    messages.value[aiMsgIndex]!.loading = false
    messages.value[aiMsgIndex]!.content = `[生成失败：${errorMessage}]`
    workflowFailed.value = true
    workflowError.value = errorMessage
    lastFailedPrompt.value = prompt
    runStatus.value = 'FAILED'
    settlingRun.value = false
    message.error(errorMessage)
    return
  }

  openRunStream(createdRun.runId, aiMsgIndex, prompt)
}

const cancelCurrentGeneration = async () => {
  if (!activeRunId.value || !canCancelGeneration.value || cancellingRun.value) return
  cancellingRun.value = true
  try {
    await cancelGeneration(activeRunId.value)
    message.info('已请求取消生成，正在等待服务端停止当前步骤')
  } catch (error) {
    cancellingRun.value = false
    message.error(getErrorMessage(error, '取消生成失败'))
  }
}

const retryWithDirectMode = () => {
  if (!lastFailedPrompt.value || isGenerating.value) return
  executionMode.value = 'DIRECT'
  onGenerate(lastFailedPrompt.value, 'DIRECT')
}

const restoreActiveGeneration = async () => {
  const savedRunId = sessionStorage.getItem(activeRunStorageKey.value)
  if (!savedRunId || isGenerating.value) return
  try {
    const status = await getGeneration(savedRunId)
    updateRunStateFromEvent(JSON.stringify(status))
    lastExecutionMode.value = status.executionMode
    runCurrentStep.value = status.currentStep || '处理中'
    if (status.status !== 'PENDING' && status.status !== 'RUNNING') {
      await finishRun(savedRunId, status.status, status.errorMessage || '', -1)
      return
    }

    const aiMsgIndex = messages.value.push({ role: 'ai', content: '', loading: true }) - 1
    settlingRun.value = false
    openRunStream(savedRunId, aiMsgIndex, '', false)
  } catch (error) {
    reconnecting.value = true
    if (error) console.warn('恢复生成任务状态失败', error)
    const recoveryIndex = messages.value.push({ role: 'ai', content: '', loading: true }) - 1
    aiMsgIndexRef.value = recoveryIndex
    scheduleRunReconnect(savedRunId, recoveryIndex, '')
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
  if (!canDownload.value) {
    message.warning(deployFailed.value ? '应用尚未成功部署，请先重试部署' : '部署完成后才能下载源码')
    return
  }
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
  await restoreActiveGeneration()

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
        <a-tag color="blue" style="font-size: 14px; padding: 4px 10px;">{{ displayCodeGenType }}</a-tag>
      </div>
      <div class="right">
        <div class="download-wrap">
          <a-button
            v-if="deployFailed"
            type="primary"
            :loading="deploying"
            @click="retryDeploy"
          >
            重试部署
          </a-button>
          <a-button :loading="downloading" :disabled="!canDownload" @click="handleDownload">{{ downloadText }}</a-button>
          <a-progress v-if="downloadStatus === 'downloading' && downloadProgress > 0" :percent="downloadProgress" size="small" :show-info="false" />
          <span v-else-if="downloadStatus === 'error'" class="download-error">下载失败</span>
          <span v-if="deployFailed" class="deploy-error">部署失败：{{ deployError }}</span>
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
          <div v-if="lastExecutionMode === 'WORKFLOW' && (workflowSteps.length > 0 || workflowFailed)" class="workflow-status-card">
            <div class="workflow-status-title">增强工作流</div>
            <div v-for="step in workflowSteps" :key="step.name" class="workflow-step">
              <span>{{ step.name }}</span>
              <span :class="{ 'workflow-step-failed': step.status.includes('失败') }">{{ step.status }}</span>
            </div>
            <div v-if="workflowFailed" class="workflow-error-message">
              {{ workflowError || '工作流执行失败' }}
              <a-button type="link" size="small" @click="retryWithDirectMode">以快速生成重试</a-button>
            </div>
          </div>
          <div v-if="runStatus" class="run-status-card">
            <span>模式：{{ lastExecutionMode }}</span>
            <span>状态：{{ runStatusLabel }}</span>
            <span>步骤：{{ runCurrentStep || '处理中' }}</span>
            <span>重试：{{ runRetryCount }}/{{ runMaxRetryCount }}</span>
            <span v-if="reconnecting" class="run-reconnecting">连接恢复中（第 {{ reconnectAttempt }}/{{ MAX_RECONNECT_ATTEMPTS }} 次）</span>
            <span v-if="activeRunId" class="run-id">runId：{{ activeRunId }}</span>
            <a-button
              v-if="canCancelGeneration"
              danger
              size="small"
              :loading="cancellingRun"
              @click="cancelCurrentGeneration"
            >
              取消生成
            </a-button>
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
            <a-select
                v-model:value="executionMode"
                :disabled="isGenerating || deploying"
                style="width: 152px"
                :title="executionModeDescription"
            >
                <a-select-option value="DIRECT">快速生成（DIRECT）</a-select-option>
                <a-select-option value="WORKFLOW">增强工作流（WORKFLOW）</a-select-option>
            </a-select>
            <span class="execution-mode-description">{{ executionModeDescription }}</span>
            <a-textarea
                v-model:value="inputPrompt"
                placeholder="描述越详细，页面越具体，可以一步一步完善生成效果..."
                :auto-size="{ minRows: 2, maxRows: 6 }"
                :disabled="isGenerating || deploying"
                @pressEnter.prevent="onSendMessage"
            />
            <a-button type="primary" shape="circle" :disabled="isGenerating || deploying" @click="onSendMessage" style="margin-left: 8px">
                <template #icon><SendOutlined /></template>
            </a-button>
          </div>
        </div>
      </div>

      <!-- Preview Area -->
      <div class="preview-area">
        <div class="preview-header">
            <span>生成后的网页展示</span>
            <a-button type="link" @click="handleFullscreen" title="全屏查看">
              <template #icon><FullscreenOutlined /></template>
              全屏
            </a-button>
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
.deploy-error {
    color: #ff4d4f;
    font-size: 12px;
    max-width: 360px;
    white-space: normal;
}
.workflow-status-card {
    width: 100%;
    margin-bottom: 10px;
    padding: 10px 12px;
    border: 1px solid #d9e8ff;
    border-radius: 6px;
    background: #f5f9ff;
    color: #44546a;
    font-size: 12px;
}
.run-status-card {
    width: 100%;
    margin-bottom: 10px;
    padding: 8px 12px;
    border: 1px solid #e6e6e6;
    border-radius: 6px;
    background: #fafafa;
    color: #595959;
    font-size: 12px;
    display: flex;
    align-items: center;
    gap: 12px;
    flex-wrap: wrap;
}
.run-id {
    color: #8c8c8c;
    font-family: monospace;
}
.workflow-status-title {
    margin-bottom: 6px;
    color: #1677ff;
    font-weight: 600;
}
.workflow-step {
    display: flex;
    justify-content: space-between;
    gap: 10px;
    line-height: 22px;
}
.workflow-step-failed,
.workflow-error-message {
    color: #ff4d4f;
}
.workflow-error-message {
    margin-top: 5px;
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
.execution-mode-description {
  width: 130px;
  margin: 0 8px;
  color: #8c8c8c;
  font-size: 12px;
  line-height: 18px;
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
  height: 48px;
  border-bottom: 1px solid #f0f0f0;
  display: flex;
  align-items: center;
  justify-content: space-between;
  padding: 0 20px;
  font-weight: 500;
  background: #fafafa;
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
