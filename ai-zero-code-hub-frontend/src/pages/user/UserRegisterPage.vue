<script setup lang="ts">
import { message } from 'ant-design-vue'
import { reactive } from 'vue'
import { useRouter } from 'vue-router'
import { useUserStore } from '@/stores/user'

type FormState = {
  userAccount: string
  userPassword: string
  checkPassword: string
}

const router = useRouter()
const userStore = useUserStore()

const formState = reactive<FormState>({
  userAccount: '',
  userPassword: '',
  checkPassword: '',
})

const onFinish = async () => {
  if (formState.userPassword !== formState.checkPassword) {
    message.error('两次密码不一致')
    return
  }
  try {
    await userStore.register({
      userAccount: formState.userAccount,
      userPassword: formState.userPassword,
      checkPassword: formState.checkPassword,
    })
    message.success('注册成功，请登录')
    await router.replace('/user/login')
  } catch (e) {
    message.error((e as Error).message)
  }
}

const goLogin = async () => {
  await router.push('/user/login')
}
</script>

<template>
  <a-card :bordered="false" style="max-width: 420px; margin: 0 auto">
    <a-space direction="vertical" size="middle" style="width: 100%">
      <a-typography-title :level="3" style="margin: 0">注册</a-typography-title>

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

        <a-form-item
          label="确认密码"
          name="checkPassword"
          :rules="[
            { required: true, message: '请再次输入密码' },
            { min: 8, message: '密码至少 8 位' },
          ]"
        >
          <a-input-password v-model:value="formState.checkPassword" placeholder="请再次输入密码" />
        </a-form-item>

        <a-form-item>
          <a-space>
            <a-button type="primary" html-type="submit" :loading="userStore.loading">
              注册
            </a-button>
            <a-button @click="goLogin">返回登录</a-button>
          </a-space>
        </a-form-item>
      </a-form>
    </a-space>
  </a-card>
</template>

