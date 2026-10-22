import { createRouter, createWebHistory } from 'vue-router'
import { useAuthStore, ensureSession, TOKEN_KEY, REFRESH_KEY } from '@/stores/auth'

const router = createRouter({
  history: createWebHistory(import.meta.env.BASE_URL),
  routes: [
    {
      path: '/',
      redirect: '/home',
    },
    // Guest (unauthenticated) routes
    {
      path: '/login',
      name: 'login',
      component: () => import('@/features/auth/views/LoginView.vue'),
      meta: { requiresGuest: true },
    },
    {
      path: '/register',
      name: 'register',
      component: () => import('@/features/auth/views/RegisterView.vue'),
      meta: { requiresGuest: true },
    },
    {
      path: '/forgot-password',
      name: 'forgot-password',
      component: () => import('@/features/auth/views/ForgotPasswordView.vue'),
      meta: { requiresGuest: true },
    },
    {
      path: '/reset-password',
      name: 'reset-password',
      component: () => import('@/features/auth/views/ResetPasswordView.vue'),
      meta: { requiresGuest: true },
    },
    // Authenticated routes (wrapped in AppShell)
    {
      path: '/home',
      name: 'home',
      component: () => import('@/features/home/views/HomeView.vue'),
      meta: { requiresAuth: true },
    },
    {
      path: '/skills',
      name: 'skills',
      component: () => import('@/features/skills/views/SkillManagementView.vue'),
      meta: { requiresAuth: true },
    },
    {
      path: '/ai-news',
      name: 'ai-news',
      component: () => import('@/features/ai-news/views/AiNewsView.vue'),
      meta: { requiresAuth: true },
    },
    {
      path: '/skill-requests',
      name: 'skill-requests',
      component: () => import('@/features/skill-requests/views/SkillRequestManagementView.vue'),
      meta: { requiresAuth: true },
    },
    {
      path: '/skill-requests/:id',
      name: 'skill-request-detail',
      component: () => import('@/features/skill-requests/views/SkillRequestDetailView.vue'),
      meta: { requiresAuth: true },
    },
    {
      path: '/users',
      name: 'users',
      component: () => import('@/features/users/views/UserManagementView.vue'),
      meta: { requiresAuth: true, requiresAdmin: true },
    },
    {
      path: '/organizations',
      name: 'organizations',
      component: () => import('@/features/orgs/views/OrganizationManagementView.vue'),
      meta: { requiresAuth: true, requiresAdmin: true },
    },
    {
      path: '/feedback',
      name: 'feedback',
      component: () => import('@/features/feedback/views/FeedbackManagementView.vue'),
      meta: { requiresAuth: true, requiresAdmin: true },
    },
    {
      path: '/settings',
      name: 'settings',
      component: () => import('@/features/users/views/SettingsView.vue'),
      meta: { requiresAuth: true },
    },
    {
      path: '/access-denied',
      name: 'access-denied',
      component: () => import('@/features/app/AccessDeniedView.vue'),
      meta: { requiresAuth: true },
    },
    {
      path: '/:pathMatch(.*)*',
      redirect: '/home',
    },
  ],
})

router.beforeEach(async (to) => {
  const auth = useAuthStore()

  // Resolve the session before deciding the redirect: an expired access token
  // is silently refreshed so the user is not bounced to /home first.
  //
  // When the destination is a guest page (e.g. /login) and a token exists,
  // force a server-side verification (refresh) BEFORE redirecting to /home.
  // A stale/blacklisted token (e.g. after logout) must not render Home first
  // and then bounce back to login — the login page must appear directly.
  if (auth.isAuthenticated) {
    const ok = await ensureSession(!!to.meta.requiresGuest)
    if (ok) {
      auth.token = localStorage.getItem(TOKEN_KEY) ?? auth.token
      auth.refreshToken = localStorage.getItem(REFRESH_KEY) ?? auth.refreshToken
    } else {
      auth.clearSession()
    }
  }

  if (to.meta.requiresAuth && !auth.isAuthenticated) {
    return { name: 'login', query: { redirect: to.fullPath } }
  }

  if (to.meta.requiresGuest && auth.isAuthenticated) {
    return { name: 'home' }
  }

  if (to.meta.requiresAdmin && !auth.isAdmin) {
    return { name: 'access-denied' }
  }
})

export default router
