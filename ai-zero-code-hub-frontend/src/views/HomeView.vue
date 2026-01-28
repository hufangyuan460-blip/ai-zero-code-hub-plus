<script setup lang="ts">
import TheWelcome from '../components/TheWelcome.vue'
import { useRouter } from 'vue-router'
import { useUserStore } from '@/stores/user'

const router = useRouter()
const userStore = useUserStore()

const goLogin = () => router.push('/user/login')
const goRegister = () => router.push('/user/register')
const goAdminUsers = () => router.push('/admin/users')
</script>

<template>
  <main>
    <a-card :bordered="false" style="margin-bottom: 16px">
      <a-space direction="vertical" size="middle" style="width: 100%">
        <a-typography-title :level="3" style="margin: 0">首页</a-typography-title>
        <a-typography-text type="secondary" v-if="userStore.isLogin">
          当前登录用户：{{ userStore.currentUser?.userName }}（{{ userStore.currentUser?.userRole }}）
        </a-typography-text>
        <a-typography-text type="secondary" v-else>未登录</a-typography-text>
        <a-space>
          <a-button v-if="!userStore.isLogin" type="primary" @click="goLogin">登录</a-button>
          <a-button v-if="!userStore.isLogin" @click="goRegister">注册</a-button>
          <a-button v-if="userStore.isAdmin" type="primary" @click="goAdminUsers">用户管理</a-button>
        </a-space>
      </a-space>
    </a-card>
    <TheWelcome />
  </main>
</template>
