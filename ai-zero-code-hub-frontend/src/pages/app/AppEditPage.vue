<script setup lang="ts">
import { ref, onMounted } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { message } from 'ant-design-vue'
import { getMyAppInfo, updateMyApp, adminGetAppInfo, adminUpdateApp, type AppVO } from '@/api/app'
import { listChatHistoryByPage, type ChatHistoryVO } from '@/api/chat'
import { useUserStore } from '@/stores/user'

const route = useRoute()
const router = useRouter()
const userStore = useUserStore()

const id = route.params.id as string
const form = ref<any>({})
const loading = ref(false)
const submitting = ref(false)
const historyList = ref<ChatHistoryVO[]>([])
const historyLoading = ref(false)
const hasMore = ref(false)
const lastCreateTime = ref<string | undefined>(undefined)

const loadData = async () => {
  if (!id) return
  loading.value = true
  try {
    // Check if admin
    let res
    if (userStore.isAdmin) {
       res = await adminGetAppInfo(id)
    } else {
       res = await getMyAppInfo(id)
    }
    
    if (res) {
      form.value = res
    } else {
      message.error('加载失败')
    }
  } catch (e: any) {
    message.error('加载失败')
  } finally {
    loading.value = false
  }
}

const loadHistory = async (isLoadMore = false) => {
  if (!id) return
  historyLoading.value = true
  try {
    const res = await listChatHistoryByPage({
      appId: id,
      pageSize: 10,
      lastCreateTime: isLoadMore ? lastCreateTime.value : undefined
    })
    if (res && res.records && res.records.length > 0) {
      const records = [...res.records].reverse()
      if (isLoadMore) {
        historyList.value.unshift(...records)
      } else {
        historyList.value = records
      }
      const oldest = res.records[res.records.length - 1]!
      lastCreateTime.value = oldest.createTime
      hasMore.value = res.records.length >= 10
    } else {
      hasMore.value = false
    }
  } catch {
    message.error('加载历史记录失败')
  } finally {
    historyLoading.value = false
  }
}

const decodeMaybe = (text: string) => {
  try {
    if (/%[0-9A-Fa-f]{2}/.test(text)) {
      return decodeURIComponent(text)
    }
    return text
  } catch {
    return text
  }
}

const onSubmit = async () => {
  submitting.value = true
  try {
    let res
    if (userStore.isAdmin) {
        await adminUpdateApp({
            id: form.value.id,
            appName: form.value.appName,
            cover: form.value.cover,
            priority: form.value.priority
        })
    } else {
        await updateMyApp({
            id: form.value.id,
            appName: form.value.appName
        })
    }
    
    message.success('更新成功')
    router.back()
  } catch (e: any) {
    message.error('更新失败')
  } finally {
    submitting.value = false
  }
}

onMounted(() => {
  loadData()
  loadHistory(false)
})
</script>

<template>
  <div class="app-edit">
    <h2>编辑应用</h2>
    <a-form :model="form" @finish="onSubmit" layout="vertical" style="max-width: 600px; margin: 0 auto;">
      <a-form-item label="应用名称" name="appName" :rules="[{ required: true, message: '请输入应用名称' }]">
        <a-input v-model:value="form.appName" />
      </a-form-item>
      
      <a-form-item label="应用封面" name="cover" v-if="userStore.isAdmin">
        <a-input v-model:value="form.cover" placeholder="请输入图片 URL" />
      </a-form-item>
      
      <a-form-item label="优先级" name="priority" v-if="userStore.isAdmin">
        <a-input-number v-model:value="form.priority" />
      </a-form-item>
      
      <a-form-item>
        <a-button type="primary" html-type="submit" :loading="submitting">保存</a-button>
        <a-button style="margin-left: 10px" @click="router.back()">取消</a-button>
      </a-form-item>
    </a-form>
    
    <div style="max-width: 800px; margin: 24px auto;">
      <a-card title="历史对话">
        <div style="margin-bottom: 8px; text-align: center;">
          <a-button type="link" size="small" :loading="historyLoading" v-if="hasMore" @click="loadHistory(true)">加载更多历史消息</a-button>
        </div>
        <a-list :data-source="historyList" :renderItem="(item: ChatHistoryVO) => null">
          <template #renderItem="{ item }: { item: ChatHistoryVO }">
            <a-list-item>
              <a-list-item-meta :title="item.messageType === '1' ? 'AI' : '用户'">
                <template #description>
                  <div style="white-space: pre-wrap;">{{ decodeMaybe(item.content) }}</div>
                  <div style="color: #999; margin-top: 4px;">{{ item.createTime }}</div>
                </template>
              </a-list-item-meta>
            </a-list-item>
          </template>
        </a-list>
        <div v-if="!historyLoading && historyList.length === 0" style="text-align: center; color: #999;">暂无历史对话</div>
      </a-card>
    </div>
  </div>
</template>

<style scoped>
.app-edit {
  max-width: 800px;
  margin: 0 auto;
  padding: 24px;
}
</style>
