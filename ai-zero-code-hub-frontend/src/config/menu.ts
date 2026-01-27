export type GlobalMenuItem = {
  key: string
  label: string
  path: string
}

export const GLOBAL_MENU_ITEMS: GlobalMenuItem[] = [
  {
    key: 'home',
    label: '首页',
    path: '/',
  },
  {
    key: 'about',
    label: '关于',
    path: '/about',
  },
]
