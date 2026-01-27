<script setup lang="ts">
import TheWelcome from '../components/TheWelcome.vue'
</script>

<template>
  <main>
    <a-card :bordered="false" style="margin-bottom: 16px">
      <a-space direction="vertical" size="middle" style="width: 100%">
        <a-typography-title :level="3" style="margin: 0">首页</a-typography-title>
        <a-typography-text type="secondary">
          全局基础布局已生效：顶部导航 + 中间内容区 + 底部版权。
        </a-typography-text>
        <a-space>
          <a-button type="primary">登录</a-button>
          <a-button>了解更多</a-button>
        </a-space>
      </a-space>
    </a-card>
    <TheWelcome />
  </main>
</template>
