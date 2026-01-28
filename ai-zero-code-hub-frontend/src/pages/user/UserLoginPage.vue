<script setup lang="ts">
import { message } from 'ant-design-vue'
import { reactive } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { useUserStore } from '@/stores/user'

type FormState = {
  userAccount: string
  userPassword: string
}

const router = useRouter()
const route = useRoute()
const userStore = useUserStore()

const formState = reactive<FormState>({
  userAccount: '',
  userPassword: '',
})

const onFinish = async () => {
  try {
    await userStore.login({
      userAccount: formState.userAccount,
      userPassword: formState.userPassword,
    })
    message.success('登录成功')
    const redirect = route.query.redirect
    if (typeof redirect === 'string' && redirect) {
      await router.replace(redirect)
    } else {
      await router.replace('/')
    }
  } catch (e) {
    message.error((e as Error).message)
  }
}

const goRegister = async () => {
  await router.push('/user/register')
}
</script>

<template>
  <a-card :bordered="false" style="max-width: 420px; margin: 0 auto">
    <a-space direction="vertical" size="middle" style="width: 100%">
      <a-typography-title :level="3" style="margin: 0">登录</a-typography-title>

      <a-form layout="vertical" :model="formState" @finish="onFinish">
        <a-form-item
          label="账号"
          name="userAccount"
          :rules="[
            { required: true, message: '请输入账号' },
            { min: 4, message: '账号至少 4 位' },
          ]"
        >
          <a-input v-model:value="formState.userAccount" placeholder="请输入账号" />
        </a-form-item>

        <a-form-item
          label="密码"
          name="userPassword"
          :rules="[
            { required: true, message: '请输入密码' },
            { min: 8, message: '密码至少 8 位' },
          ]"
        >
          <a-input-password v-model:value="formState.userPassword" placeholder="请输入密码" />
        </a-form-item>

        <a-form-item>
          <a-space>
            <a-button type="primary" html-type="submit" :loading="userStore.loading">
              登录
            </a-button>
            <a-button @click="goRegister">注册</a-button>
          </a-space>
        </a-form-item>
      </a-form>
    </a-space>
  </a-card>
</template>

