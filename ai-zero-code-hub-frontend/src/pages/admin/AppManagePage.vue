<script setup lang="ts">
import { ref, onMounted } from 'vue'
import { message, Modal } from 'ant-design-vue'
import { useRouter } from 'vue-router'
import { adminListAppByPage, adminRemoveApp, adminUpdateApp, type AppVO, type AppAdminQueryRequest } from '@/api/app'

const router = useRouter()

const apps = ref<AppVO[]>([])
const total = ref(0)
const loading = ref(false)
const searchParams = ref<AppAdminQueryRequest>({
  current: 1,
  pageSize: 10,
})

const columns = [
  { title: 'ID', dataIndex: 'id', width: 60 },
  { title: '应用名称', dataIndex: 'appName' },
  { title: '封面', dataIndex: 'cover', key: 'cover' },
  { title: '类型', dataIndex: 'codeGenType' },
  { title: '状态', dataIndex: 'appStatus' },
  { title: '优先级', dataIndex: 'priority' },
  { title: '创建人', dataIndex: 'userId' },
  { title: '创建时间', dataIndex: 'createTime' },
  { title: '操作', key: 'action', width: 200 },
]

const loadData = async () => {
  loading.value = true
  try {
    const res = await adminListAppByPage(searchParams.value)
    if (res.data) {
      apps.value = res.data.records
      total.value = res.data.totalRow
    }
  } catch (e: any) {
    message.error('加载失败')
  } finally {
    loading.value = false
  }
}

const onSearch = () => {
  searchParams.value.current = 1
  loadData()
}

const onPageChange = (page: number, pageSize: number) => {
  searchParams.value.current = page
  searchParams.value.pageSize = pageSize
  loadData()
}

const doDelete = async (record: AppVO) => {
  Modal.confirm({
    title: '确认删除',
    content: `确定要删除应用 ${record.appName} 吗？`,
    onOk: async () => {
      const res = await adminRemoveApp(record.id)
      if (res.data) {
        message.success('删除成功')
        loadData()
      } else {
        message.error('删除失败')
      }
    }
  })
}

const doEdit = (record: AppVO) => {
  router.push(`/app/edit/${record.id}`)
}

const doSetFeatured = async (record: AppVO) => {
    // Set priority to 99
    try {
        const res = await adminUpdateApp({ id: record.id, priority: 99 })
        if (res.data) {
            message.success('已设为精选')
            loadData()
        }
    } catch (e: any) {
        message.error('设置失败')
    }
}

onMounted(() => {
  loadData()
})
</script>

<template>
  <div class="app-manage">
    <a-form layout="inline" :model="searchParams" @finish="onSearch" style="margin-bottom: 24px">
      <a-form-item label="应用名称">
        <a-input v-model:value="searchParams.appName" placeholder="请输入应用名称" allow-clear />
      </a-form-item>
       <a-form-item>
        <a-button type="primary" html-type="submit">查询</a-button>
      </a-form-item>
    </a-form>
    
    <a-table
      :columns="columns"
      :dataSource="apps"
      :pagination="{
        current: searchParams.current,
        pageSize: searchParams.pageSize,
        total: total,
        onChange: onPageChange
      }"
      :loading="loading"
      rowKey="id"
    >
      <template #bodyCell="{ column, record }">
        <template v-if="column.key === 'cover'">
             <img v-if="record.cover" :src="record.cover" style="width: 40px; height: 40px; border-radius: 4px; object-fit: cover;" />
        </template>
        <template v-else-if="column.key === 'action'">
          <a-space>
            <a-button type="link" @click="doEdit(record)">编辑</a-button>
            <a-button type="link" danger @click="doDelete(record)">删除</a-button>
            <a-button type="link" @click="doSetFeatured(record)" v-if="record.priority !== 99">精选</a-button>
          </a-space>
        </template>
      </template>
    </a-table>
  </div>
</template>
