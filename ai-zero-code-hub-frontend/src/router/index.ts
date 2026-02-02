import { createRouter, createWebHistory } from 'vue-router'
import HomeView from '../views/HomeView.vue'
import { useUserStore } from '@/stores/user'

const router = createRouter({
  history: createWebHistory(import.meta.env.BASE_URL),
  routes: [
    {
      path: '/',
      name: 'home',
      component: HomeView,
    },
    {
      path: '/user/login',
      name: 'userLogin',
      component: () => import('@/pages/user/UserLoginPage.vue'),
    },
    {
      path: '/user/register',
      name: 'userRegister',
      component: () => import('@/pages/user/UserRegisterPage.vue'),
    },
    {
      path: '/admin/users',
      name: 'adminUsers',
      meta: {
        requiresLogin: true,
        requiresAdmin: true,
      },
      component: () => import('@/pages/admin/UserManagePage.vue'),
    },
    {
      path: '/admin/app',
      name: 'adminApp',
      meta: {
        requiresLogin: true,
        requiresAdmin: true,
      },
      component: () => import('@/pages/admin/AppManagePage.vue'),
    },
    {
      path: '/admin/chat',
      name: 'adminChat',
      meta: {
        requiresLogin: true,
        requiresAdmin: true,
      },
      component: () => import('@/pages/admin/ChatManagePage.vue'),
    },
    {
      path: '/app/generator/:appId',
      name: 'appGenerator',
      meta: {
        requiresLogin: true,
      },
      component: () => import('@/pages/app/AppGeneratorPage.vue'),
    },
    {
      path: '/app/edit/:id',
      name: 'appEdit',
      meta: {
        requiresLogin: true,
      },
      component: () => import('@/pages/app/AppEditPage.vue'),
    },
    {
      path: '/about',
      name: 'about',
      // route level code-splitting
      // this generates a separate chunk (About.[hash].js) for this route
      // which is lazy-loaded when the route is visited.
      component: () => import('../views/AboutView.vue'),
    },
  ],
})

router.beforeEach(async (to) => {
  const userStore = useUserStore()
  await userStore.init()

  const requiresLogin = Boolean(to.meta?.requiresLogin)
  const requiresAdmin = Boolean(to.meta?.requiresAdmin)

  if (requiresLogin && !userStore.isLogin) {
    return {
      path: '/user/login',
      query: { redirect: to.fullPath },
    }
  }
  if (requiresAdmin && !userStore.isAdmin) {
    return { path: '/' }
  }
})

export default router
