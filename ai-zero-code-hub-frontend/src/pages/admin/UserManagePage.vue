<script setup lang="ts">
import type { TablePaginationConfig } from 'ant-design-vue'
import { message, Modal } from 'ant-design-vue'
import { computed, onMounted, reactive, ref } from 'vue'
import { userApi } from '@/api/user'
import type { UserAddRequest, UserQueryRequest, UserUpdateRequest, UserVO } from '@/api/types'

type Mode = 'add' | 'edit'

const loading = ref(false)
const dataSource = ref<UserVO[]>([])

const query = reactive<UserQueryRequest>({
  userAccount: '',
  userName: '',
  userRole: '',
  pageNumber: 1,
  pageSize: 10,
  sortField: 'id',
  sortOrder: 'descend',
})

const totalRow = ref(0)

const roleOptions = [
  { label: '用户', value: 'user' },
  { label: '管理员', value: 'admin' },
]

const modalOpen = ref(false)
const modalMode = ref<Mode>('add')
const modalSubmitting = ref(false)

const addForm = reactive<UserAddRequest>({
  userAccount: '',
  userPassword: '',
  userName: '',
  userAvatar: '',
  userProfile: '',
  userRole: 'user',
})

const editForm = reactive<UserUpdateRequest>({
  id: 0,
  userName: '',
  userPassword: '',
  userAvatar: '',
  userProfile: '',
  userRole: 'user',
})

const modalTitle = computed(() => (modalMode.value === 'add' ? '新增用户' : '编辑用户'))

const fetchPage = async () => {
  loading.value = true
  try {
    const page = await userApi.adminPage({
      id: query.id || undefined,
      userAccount: query.userAccount || undefined,
      userName: query.userName || undefined,
      userRole: query.userRole || undefined,
      userProfile: query.userProfile || undefined,
      pageNumber: query.pageNumber,
      pageSize: query.pageSize,
      sortField: query.sortField || undefined,
      sortOrder: query.sortOrder || undefined,
    })
    dataSource.value = page.records ?? []
    totalRow.value = page.totalRow ?? 0
  } catch (e) {
    message.error((e as Error).message)
  } finally {
    loading.value = false
  }
}

const onSearch = async () => {
  query.pageNumber = 1
  await fetchPage()
}

const onReset = async () => {
  query.userAccount = ''
  query.userName = ''
  query.userRole = ''
  query.pageNumber = 1
  query.pageSize = 10
  query.sortField = 'id'
  query.sortOrder = 'descend'
  await fetchPage()
}

const openAdd = () => {
  modalMode.value = 'add'
  addForm.userAccount = ''
  addForm.userPassword = ''
  addForm.userName = ''
  addForm.userAvatar = ''
  addForm.userProfile = ''
  addForm.userRole = 'user'
  modalOpen.value = true
}

const openEdit = (record: UserVO) => {
  modalMode.value = 'edit'
  editForm.id = record.id
  editForm.userName = record.userName ?? ''
  editForm.userAvatar = record.userAvatar ?? ''
  editForm.userProfile = record.userProfile ?? ''
  editForm.userRole = record.userRole ?? 'user'
  editForm.userPassword = ''
  modalOpen.value = true
}

const submitModal = async () => {
  modalSubmitting.value = true
  try {
    if (modalMode.value === 'add') {
      await userApi.adminAdd({
        userAccount: addForm.userAccount,
        userPassword: addForm.userPassword,
        userName: addForm.userName || undefined,
        userAvatar: addForm.userAvatar || undefined,
        userProfile: addForm.userProfile || undefined,
        userRole: addForm.userRole || undefined,
      })
      message.success('新增成功')
    } else {
      await userApi.adminUpdate({
        id: editForm.id,
        userName: editForm.userName || undefined,
        userAvatar: editForm.userAvatar || undefined,
        userProfile: editForm.userProfile || undefined,
        userRole: editForm.userRole || undefined,
        userPassword: editForm.userPassword || undefined,
      })
      message.success('更新成功')
    }
    modalOpen.value = false
    await fetchPage()
  } catch (e) {
    message.error((e as Error).message)
  } finally {
    modalSubmitting.value = false
  }
}

const removeUser = async (record: UserVO) => {
  Modal.confirm({
    title: '确认删除',
    content: `确定删除用户 ${record.userAccount} 吗？`,
    okText: '删除',
    okType: 'danger',
    cancelText: '取消',
    async onOk() {
      try {
        await userApi.adminRemove(record.id)
        message.success('删除成功')
        await fetchPage()
      } catch (e) {
        message.error((e as Error).message)
      }
    },
  })
}

const onTableChange = async (pagination: TablePaginationConfig, _: unknown, sorter: any) => {
  if (pagination.current) query.pageNumber = pagination.current
  if (pagination.pageSize) query.pageSize = pagination.pageSize

  if (sorter?.field && sorter?.order) {
    query.sortField = sorter.field
    query.sortOrder = sorter.order
  } else if (sorter?.field && !sorter?.order) {
    query.sortField = 'id'
    query.sortOrder = 'descend'
  }

  await fetchPage()
}

const columns = [
  { title: 'ID', dataIndex: 'id', key: 'id', sorter: true, width: 90 },
  { title: '账号', dataIndex: 'userAccount', key: 'userAccount', sorter: true, width: 180 },
  { title: '昵称', dataIndex: 'userName', key: 'userName', sorter: true, width: 160 },
  { title: '角色', dataIndex: 'userRole', key: 'userRole', sorter: true, width: 120 },
  { title: '简介', dataIndex: 'userProfile', key: 'userProfile' },
  { title: '创建时间', dataIndex: 'createTime', key: 'createTime', sorter: true, width: 180 },
  { title: '操作', key: 'action', width: 160, fixed: 'right' as const },
]

onMounted(async () => {
  await fetchPage()
})
</script>

<template>
  <a-space direction="vertical" size="middle" style="width: 100%">
    <a-card :bordered="false">
      <a-space direction="vertical" size="middle" style="width: 100%">
        <a-typography-title :level="3" style="margin: 0">用户管理</a-typography-title>

        <a-form layout="inline">
          <a-form-item label="账号">
            <a-input v-model:value="query.userAccount" placeholder="精确匹配" allow-clear style="width: 200px" />
          </a-form-item>
          <a-form-item label="昵称">
            <a-input v-model:value="query.userName" placeholder="模糊匹配" allow-clear style="width: 200px" />
          </a-form-item>
          <a-form-item label="角色">
            <a-select
              v-model:value="query.userRole"
              :options="roleOptions"
              allow-clear
              placeholder="全部"
              style="width: 140px"
            />
          </a-form-item>
          <a-form-item>
            <a-space>
              <a-button type="primary" @click="onSearch">查询</a-button>
              <a-button @click="onReset">重置</a-button>
            </a-space>
          </a-form-item>
        </a-form>

        <a-space>
          <a-button type="primary" @click="openAdd">新增用户</a-button>
        </a-space>
      </a-space>
    </a-card>

    <a-card :bordered="false">
      <a-table
        row-key="id"
        :columns="columns"
        :data-source="dataSource"
        :loading="loading"
        :pagination="{
          current: query.pageNumber,
          pageSize: query.pageSize,
          total: totalRow,
          showSizeChanger: true,
          showTotal: (t: number) => `共 ${t} 条`,
        }"
        :scroll="{ x: 1100 }"
        @change="onTableChange"
      >
        <template #bodyCell="{ column, record }">
          <template v-if="column.key === 'action'">
            <a-space>
              <a-button size="small" @click="openEdit(record)">编辑</a-button>
              <a-button danger size="small" @click="removeUser(record)">删除</a-button>
            </a-space>
          </template>
        </template>
      </a-table>
    </a-card>

    <a-modal
      v-model:open="modalOpen"
      :title="modalTitle"
      :confirm-loading="modalSubmitting"
      ok-text="提交"
      cancel-text="取消"
      @ok="submitModal"
    >
      <a-form v-if="modalMode === 'add'" layout="vertical" :model="addForm">
        <a-form-item
          label="账号"
          name="userAccount"
          :rules="[
            { required: true, message: '请输入账号' },
            { min: 4, message: '账号至少 4 位' },
          ]"
        >
          <a-input v-model:value="addForm.userAccount" placeholder="请输入账号" />
        </a-form-item>

        <a-form-item
          label="密码"
          name="userPassword"
          :rules="[
            { required: true, message: '请输入密码' },
            { min: 8, message: '密码至少 8 位' },
          ]"
        >
          <a-input-password v-model:value="addForm.userPassword" placeholder="请输入密码" />
        </a-form-item>

        <a-form-item label="昵称" name="userName">
          <a-input v-model:value="addForm.userName" allow-clear />
        </a-form-item>

        <a-form-item label="头像" name="userAvatar">
          <a-input v-model:value="addForm.userAvatar" allow-clear />
        </a-form-item>

        <a-form-item label="简介" name="userProfile">
          <a-input v-model:value="addForm.userProfile" allow-clear />
        </a-form-item>

        <a-form-item label="角色" name="userRole">
          <a-select v-model:value="addForm.userRole" :options="roleOptions" />
        </a-form-item>
      </a-form>

      <a-form v-else layout="vertical" :model="editForm">
        <a-form-item label="ID">
          <a-input :value="editForm.id" disabled />
        </a-form-item>

        <a-form-item label="昵称" name="userName">
          <a-input v-model:value="editForm.userName" allow-clear />
        </a-form-item>

        <a-form-item label="头像" name="userAvatar">
          <a-input v-model:value="editForm.userAvatar" allow-clear />
        </a-form-item>

        <a-form-item label="简介" name="userProfile">
          <a-input v-model:value="editForm.userProfile" allow-clear />
        </a-form-item>

        <a-form-item label="角色" name="userRole">
          <a-select v-model:value="editForm.userRole" :options="roleOptions" />
        </a-form-item>

        <a-form-item
          label="重置密码（可选）"
          name="userPassword"
          :rules="[{ min: 8, message: '密码至少 8 位' }]"
        >
          <a-input-password v-model:value="editForm.userPassword" placeholder="不填写则不修改" />
        </a-form-item>
      </a-form>
    </a-modal>
  </a-space>
</template>

