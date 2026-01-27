<script setup lang="ts">
import { RouterView } from 'vue-router'
import GlobalHeader from '@/components/GlobalHeader.vue'
import GlobalFooter from '@/components/GlobalFooter.vue'
import { GLOBAL_MENU_ITEMS } from '@/config/menu'
</script>

<template>
  <a-layout class="basic-layout">
    <a-layout-header class="basic-layout-header">
      <GlobalHeader :menu-items="GLOBAL_MENU_ITEMS" />
    </a-layout-header>

    <a-layout-content class="basic-layout-content">
      <div class="basic-layout-content-inner">
        <RouterView />
      </div>
    </a-layout-content>

    <a-layout-footer class="basic-layout-footer">
      <GlobalFooter />
    </a-layout-footer>
  </a-layout>
</template>

<style scoped>
.basic-layout {
  min-height: 100vh;
}

.basic-layout-header {
  position: sticky;
  top: 0;
  z-index: 10;
  padding-inline: 24px;
  background: #fff;
  border-bottom: 1px solid rgba(5, 5, 5, 0.06);
}

.basic-layout-content {
  flex: 1;
  padding: 24px;
  background: #f5f5f5;
}

.basic-layout-content-inner {
  max-width: 1200px;
  margin: 0 auto;
}

.basic-layout-footer {
  background: #fff;
  border-top: 1px solid rgba(5, 5, 5, 0.06);
}

@media (max-width: 768px) {
  .basic-layout-header {
    padding-inline: 12px;
  }

  .basic-layout-content {
    padding: 12px;
  }
}
</style>
