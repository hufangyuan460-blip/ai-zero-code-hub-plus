<script setup lang="ts">
import { ref, onMounted } from 'vue'
import { useRouter } from 'vue-router'
import { message, Modal } from 'ant-design-vue'
import { EditOutlined, DeleteOutlined } from '@ant-design/icons-vue'
import {
  createApp,
  listMyAppByPage,
  listFeaturedAppByPage,
  removeMyApp,
  type AppVO,
  type AppMyQueryRequest,
  type AppFeaturedQueryRequest,
} from '@/api/app'
import { useUserStore } from '@/stores/user'



const router = useRouter()
const userStore = useUserStore()

const prompt = ref('')
const selectedType = ref('website')
const customType = ref('')
const codeGenType = ref('html')

const myApps = ref<AppVO[]>([])
const myTotal = ref(0)
const myParams = ref<AppMyQueryRequest>({
  pageNumber: 1,
  pageSize: 8,
})

const featuredApps = ref<AppVO[]>([])
const featuredTotal = ref(0)
const featuredParams = ref<AppFeaturedQueryRequest>({
  pageNumber: 1,
  pageSize: 8,
})

const loading = ref(false)
const myCurrent = ref(1)
const featuredCurrent = ref(1)

const loadMyApps = async () => {
  if (!userStore.isLogin) return
  myParams.value.pageNumber = myCurrent.value
  const res = await listMyAppByPage(myParams.value)
  if (res) {
    myApps.value = res.records
    myTotal.value = res.totalRow
  }
}

const loadFeaturedApps = async () => {
  featuredParams.value.pageNumber = featuredCurrent.value
  const res = await listFeaturedAppByPage(featuredParams.value)
  if (res) {
    featuredApps.value = res.records
    featuredTotal.value = res.totalRow
  }
}

const onSearch = async () => {
  if (!prompt.value) {
    message.warning('请输入提示词')
    return
  }
  if (!userStore.isLogin) {
    message.warning('请先登录')
    router.push('/user/login')
    return
  }
  loading.value = true
  try {
    const selectedLabel = appTypes.find(t => t.key === selectedType.value)?.label || '网站'
    const fullPrompt = codeGenType.value === 'chat'
      ? prompt.value
      : `${prompt.value}，应用类型：${selectedLabel}`

    // 默认使用提示词的前10个字符作为应用名称
    const appName = prompt.value.length > 10 ? prompt.value.substring(0, 10) + '...' : prompt.value

    // If chat mode is selected, we still create an app but pass 'chat' intent to next page
    // The backend createApp expects 'html' or 'multi_file' in codeGenType for now to init structure,
    // or we can pass 'chat' if backend supports it.
    // However, backend Enum for App.codeGenType usually stores the *target* code type.
    // Let's assume for 'chat' mode we default to 'html' structure for the App entity, 
    // but the generator page will start in chat mode.
    // Or if codeGenType is 'chat', we pass that. 
    // Let's use 'html' as default storage type if 'chat' is selected, 
    // but pass a flag to the generator page.
    
    // Actually, user might want to decide code type later.
    // Let's check backend AppCreateRequest.
    // Backend: createApp -> app.setCodeGenType(codeGenType)
    // If we pass 'chat' to backend, verify if backend CodeGenTypeEnum has 'chat'.
    // Yes, we added 'CHAT' to backend enum in previous turn.
    
    const appId = await createApp({
      appName: appName,
      initPrompt: fullPrompt,
      codeGenType: codeGenType.value,
    })
    
    message.success('创建成功，正在跳转...')
    const query: Record<string, string> = { initPrompt: fullPrompt }
    if (codeGenType.value === 'chat') {
      query.chatOnly = 'true'
    }
    router.push({ path: `/app/generator/${appId}`, query })
  } catch (e: unknown) {
    message.error((e as Error)?.message || '创建失败')
  } finally {
    loading.value = false
  }
}

const doEdit = (item: AppVO) => {
  router.push(`/app/edit/${item.id}`)
}

const doDelete = (item: AppVO) => {
  Modal.confirm({
    title: '确认删除',
    content: `确定要删除应用 ${item.appName} 吗？`,
    onOk: async () => {
      await removeMyApp(item.id)
      message.success('删除成功')
      loadMyApps()
    }
  })
}

onMounted(() => {
  if (userStore.isLogin) {
    loadMyApps()
  }
  loadFeaturedApps()
})

const onMyPageChange = (page: number) => {
  myCurrent.value = page
  loadMyApps()
}

const onFeaturedPageChange = (page: number) => {
  featuredCurrent.value = page
  loadFeaturedApps()
}

const appTypes = [
  { key: 'website', label: '企业网站', color: 'blue' },
  { key: 'blog', label: '个人博客', color: 'green' },
  { key: 'admin', label: '电商运营后台', color: 'orange' },
  { key: 'community', label: '游戏', color: 'purple' },
  { key: 'custom', label: '自定义', color: 'cyan' },
]
</script>

<template>
  <div class="home">
    <!-- Hero Section -->
    <div class="hero-section">
      <h1 class="title">一句话 <span class="icon">🐱</span> 呈所想</h1>
      <p class="subtitle">与 AI 对话轻松创建应用和网站</p>

      <div class="search-box">
        <a-input-search
          v-model:value="prompt"
          placeholder="使用 NoCode 创建一个高效的小工具，帮我计算..."
          enter-button="生成"
          size="large"
          @search="onSearch"
          :loading="loading"
        />
        <div style="margin-top: 16px; display: flex; justify-content: center; gap: 16px; align-items: center;">
            <span>生成模式：</span>
            <a-radio-group v-model:value="codeGenType">
                <a-radio-button value="html">原生 HTML</a-radio-button>
                <a-radio-button value="multi_file">原生多文件</a-radio-button>
                <a-radio-button value="chat">仅聊天</a-radio-button>
            </a-radio-group>
        </div>
        <div class="tags">
           <a-tag
             v-for="type in appTypes"
             :key="type.key"
             :color="selectedType === type.key ? type.color : 'default'"
             class="type-tag"
             @click="selectedType = type.key"
           >
             {{ type.label }}
           </a-tag>
           <a-input
                v-if="selectedType === 'custom'"
                v-model:value="customType"
                placeholder="请输入类型"
                size="small"
                style="width: 120px; margin-left: 8px;"
           />
        </div>
      </div>
    </div>

    <!-- My Apps -->
    <div class="section" v-if="userStore.isLogin">
      <h2>我的作品</h2>
      <a-list
        :grid="{ gutter: 16, xs: 1, sm: 2, md: 3, lg: 4, xl: 4, xxl: 4 }"
        :dataSource="myApps"
      >
        <template #renderItem="{ item }">
          <a-list-item>
             <a-card hoverable @click="router.push(`/app/generator/${item.id}`)">
                <template #cover>
                  <div class="card-cover" :style="{ backgroundImage: `url(${item.cover || 'https://gw.alipayobjects.com/zos/rmsportal/JiqGstEfoWAOHiTxclqi.png'})` }"></div>
                </template>
                <template #actions v-if="userStore.currentUser?.id === item.userId">
                    <EditOutlined key="edit" @click.stop="doEdit(item)" />
                    <DeleteOutlined key="delete" @click.stop="doDelete(item)" />
                </template>
                <a-card-meta :title="item.appName">
                  <template #description>
                    <div class="card-desc">{{ item.appDesc || '暂无描述' }}</div>
                  </template>
                </a-card-meta>
             </a-card>
          </a-list-item>
        </template>
      </a-list>
      <div class="pagination-wrapper" v-if="myTotal > 0">
        <a-pagination
          v-model:current="myCurrent"
          :total="myTotal"
          :pageSize="myParams.pageSize"
          @change="onMyPageChange"
          show-less-items
        />
      </div>
    </div>

    <!-- Featured Apps -->
    <div class="section">
      <h2>精选案例</h2>
      <a-list
        :grid="{ gutter: 16, xs: 1, sm: 2, md: 3, lg: 4, xl: 4, xxl: 4 }"
        :dataSource="featuredApps"
      >
        <template #renderItem="{ item }">
          <a-list-item>
             <a-card hoverable @click="router.push(`/app/generator/${item.id}`)">
                <template #cover>
                  <div class="card-cover" :style="{ backgroundImage: `url(${item.cover || 'https://gw.alipayobjects.com/zos/rmsportal/JiqGstEfoWAOHiTxclqi.png'})` }"></div>
                </template>
                <a-card-meta :title="item.appName">
                  <template #description>
                    <div class="card-desc">{{ item.appDesc || '暂无描述' }}</div>
                  </template>
                </a-card-meta>
             </a-card>
          </a-list-item>
        </template>
      </a-list>
      <div class="pagination-wrapper" v-if="featuredTotal > 0">
        <a-pagination
          v-model:current="featuredCurrent"
          :total="featuredTotal"
          :pageSize="featuredParams.pageSize"
          @change="onFeaturedPageChange"
          show-less-items
        />
      </div>
    </div>
  </div>
</template>

<style scoped>
.home {
  max-width: 1200px;
  margin: 0 auto;
  padding: 20px;
}
.hero-section {
  text-align: center;
  padding: 50px 0;
  background: linear-gradient(180deg, #e6f7ff 0%, #ffffff 100%);
  border-radius: 8px;
  margin-bottom: 40px;
}
.title {
  font-size: 32px;
  font-weight: bold;
  margin-bottom: 16px;
}
.icon {
  font-size: 40px;
  vertical-align: middle;
}
.subtitle {
  font-size: 16px;
  color: #666;
  margin-bottom: 32px;
}
.search-box {
  max-width: 800px;
  margin: 0 auto;
  padding: 0 20px;
}
.tags {
  margin-top: 16px;
  display: flex;
  justify-content: center;
  gap: 8px;
  flex-wrap: wrap;
}
.type-tag {
  cursor: pointer;
  padding: 4px 12px;
  font-size: 14px;
}
.section {
  margin-bottom: 40px;
}
.section h2 {
  font-size: 24px;
  margin-bottom: 20px;
  font-weight: 600;
}
.card-cover {
  height: 160px;
  background-size: cover;
  background-position: center;
  background-repeat: no-repeat;
}
.card-desc {
  overflow: hidden;
  text-overflow: ellipsis;
  display: -webkit-box;
  -webkit-line-clamp: 2;
  -webkit-box-orient: vertical;
  height: 44px;
}
.pagination-wrapper {
  text-align: center;
  margin-top: 24px;
}
</style>
