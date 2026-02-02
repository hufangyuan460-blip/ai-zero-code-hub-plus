<script setup lang="ts">
import { ref, onMounted } from 'vue'
import { message } from 'ant-design-vue'
import { adminListChatHistoryByPage, type ChatHistoryVO, type ChatHistoryQueryRequest } from '@/api/chat'

const historyList = ref<ChatHistoryVO[]>([])
const total = ref(0)
const loading = ref(false)
const searchParams = ref<ChatHistoryQueryRequest>({
  pageNumber: 1,
  pageSize: 10,
})

const current = ref(1)

const columns = [
  { title: 'ID', dataIndex: 'id', width: 60 },
  { title: '应用ID', dataIndex: 'appId', width: 100 },
  { title: '用户ID', dataIndex: 'userId', width: 100 },
  { title: '消息类型', dataIndex: 'messageType', customRender: ({ text }: any) => text === 1 ? 'AI' : '用户' },
  { title: '内容', dataIndex: 'content', ellipsis: true },
  { title: '创建时间', dataIndex: 'createTime', width: 180 },
]

const loadData = async () => {
  loading.value = true
  try {
    searchParams.value.pageNumber = current.value
    const res = await adminListChatHistoryByPage(searchParams.value)
    if (res && res.records) {
      historyList.value = res.records
      total.value = res.totalRow
    }
  } catch (e: any) {
    message.error('加载失败')
  } finally {
    loading.value = false
  }
}

const onSearch = () => {
  current.value = 1
  loadData()
}

const onPageChange = (page: number, pageSize: number) => {
  current.value = page
  searchParams.value.pageSize = pageSize
  loadData()
}

onMounted(() => {
  loadData()
})
</script>

<template>
  <div class="chat-manage">
    <a-form layout="inline" :model="searchParams" @finish="onSearch" style="margin-bottom: 24px">
      <a-form-item label="应用ID">
        <a-input v-model:value="searchParams.appId" placeholder="请输入应用ID" allow-clear />
      </a-form-item>
      <a-form-item label="用户ID">
        <a-input v-model:value="searchParams.userId" placeholder="请输入用户ID" allow-clear />
      </a-form-item>
       <a-form-item>
        <a-button type="primary" html-type="submit">查询</a-button>
      </a-form-item>
    </a-form>
    
    <a-table
      :columns="columns"
      :dataSource="historyList"
      :pagination="{
        current: current,
        pageSize: searchParams.pageSize,
        total: total,
        onChange: onPageChange,
        showTotal: (total: number) => `共 ${total} 条`
      }"
      :loading="loading"
      rowKey="id"
    >
    </a-table>
  </div>
</template>
