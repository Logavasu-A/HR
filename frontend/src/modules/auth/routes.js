import Login from './pages/Login.vue'

export default [
  { path: '/login', component: Login, meta: { bare: true, open: true } },
  { path: '/change-password', component: () => import('./pages/ChangePassword.vue') },
]