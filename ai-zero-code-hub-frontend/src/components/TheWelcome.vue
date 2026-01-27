<script setup lang="ts">
import { ref } from 'vue'
import { HealthControllerService } from '@/api/client'

const healthLoading = ref(false)
const healthResult = ref<string>('')

const callHealth = async () => {
  healthLoading.value = true
  healthResult.value = ''
  try {
    const res = await HealthControllerService.healthCheck()
    healthResult.value = JSON.stringify(res, null, 2)
  } catch (e) {
    healthResult.value = String(e)
  } finally {
    healthLoading.value = false
  }
}
</script>

<template>
  <a-space direction="vertical" size="large" style="width: 100%">
    <a-card :bordered="false">
      <a-space direction="vertical" size="middle" style="width: 100%">
        <a-typography-title :level="4" style="margin: 0">前端请求代码（OpenAPI）</a-typography-title>
        <a-typography-paragraph style="margin: 0">
          已接入 Axios + OpenAPI 生成。生成命令：
          <a-typography-text code>npm run openapi:generate:url</a-typography-text>
        </a-typography-paragraph>
        <a-typography-paragraph style="margin: 0">
          后端文档：
          <a href="http://localhost:8123/api/doc.html" target="_blank" rel="noopener noreferrer">
            http://localhost:8123/api/doc.html
          </a>
        </a-typography-paragraph>
      </a-space>
    </a-card>

    <a-card :bordered="false">
      <a-space direction="vertical" size="middle" style="width: 100%">
        <a-typography-title :level="4" style="margin: 0">接口连通性测试</a-typography-title>
        <a-space>
          <a-button type="primary" :loading="healthLoading" @click="callHealth">调用 /health/</a-button>
        </a-space>
        <a-typography-paragraph v-if="healthResult" style="margin: 0">
          <a-typography-text code>
            <pre style="margin: 0; white-space: pre-wrap">{{ healthResult }}</pre>
          </a-typography-text>
        </a-typography-paragraph>
      </a-space>
    </a-card>
  </a-space>
</template>
