export type GlobalMenuItem = {
  key: string
  label: string
  path: string
  requiresLogin?: boolean
  requiresAdmin?: boolean
}

export const GLOBAL_MENU_ITEMS: GlobalMenuItem[] = [
  {
    key: 'home',
    label: '首页',
    path: '/',
  },
  {
    key: 'adminUsers',
    label: '用户管理',
    path: '/admin/users',
    requiresLogin: true,
    requiresAdmin: true,
  },
  {
    key: 'adminApp',
    label: '应用管理',
    path: '/admin/app',
    requiresLogin: true,
    requiresAdmin: true,
  },
  {
    key: 'adminChat',
    label: '对话管理',
    path: '/admin/chat',
    requiresLogin: true,
    requiresAdmin: true,
  },
  {
    key: 'about',
    label: '关于',
    path: '/about',
  },
]
