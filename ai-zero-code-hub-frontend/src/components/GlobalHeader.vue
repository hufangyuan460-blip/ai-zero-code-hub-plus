<script setup lang="ts">
import type { MenuProps } from 'ant-design-vue'
import { computed, ref } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import type { GlobalMenuItem } from '@/config/menu'
import fallbackLogoUrl from '@/assets/logo.svg'

type Props = {
  title?: string
  menuItems: GlobalMenuItem[]
}

const props = withDefaults(defineProps<Props>(), {
  title: 'AI 零代码平台',
})

const route = useRoute()
const router = useRouter()

const logoSrc = ref<string>('/logo.png')

const antMenuItems = computed<MenuProps['items']>(() =>
  props.menuItems.map((item) => ({
    key: item.path,
    label: item.label,
  })),
)

const selectedKeys = computed(() => [route.path])

const onMenuClick: MenuProps['onClick'] = ({ key }) => {
  router.push(String(key))
}

const onLogoError = () => {
  logoSrc.value = fallbackLogoUrl
}

const goHome = () => {
  router.push('/')
}
</script>

<template>
  <div class="global-header">
    <div class="left" @click="goHome">
      <img class="logo" :src="logoSrc" alt="logo" @error="onLogoError" />
      <div class="title">{{ props.title }}</div>
    </div>

    <a-menu
      class="menu"
      mode="horizontal"
      :items="antMenuItems"
      :selected-keys="selectedKeys"
      @click="onMenuClick"
    />

    <div class="right">
      <a-button type="primary">登录</a-button>
    </div>
  </div>
</template>

<style scoped>
.global-header {
  display: flex;
  align-items: center;
  gap: 16px;
  height: 64px;
}

.left {
  display: flex;
  align-items: center;
  gap: 12px;
  min-width: 220px;
  cursor: pointer;
  user-select: none;
}

.logo {
  width: 32px;
  height: 32px;
  object-fit: contain;
}

.title {
  font-size: 16px;
  font-weight: 600;
  color: rgba(0, 0, 0, 0.88);
  white-space: nowrap;
}

.menu {
  flex: 1;
  min-width: 0;
  border-bottom: 0;
}

.right {
  display: flex;
  align-items: center;
  justify-content: flex-end;
  min-width: 120px;
}

@media (max-width: 768px) {
  .left {
    min-width: 160px;
  }

  .title {
    display: none;
  }
}
</style>
