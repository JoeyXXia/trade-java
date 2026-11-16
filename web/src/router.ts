import { createRouter, createWebHistory, type RouterHistory } from 'vue-router'
import type { Pinia } from 'pinia'
import { useSession } from './stores/session'

export function makeRouter(pinia: Pinia, history: RouterHistory = createWebHistory()) {
  const router = createRouter({ history, routes: [
    { path: '/', component: () => import('./views/HomePage.vue') },
    { path: '/login', component: () => import('./views/LoginPage.vue') },
    { path: '/account', component: () => import('./views/AccountPage.vue'), meta: { auth: true } },
    { path: '/operations', component: () => import('./views/WorkspacePage.vue'), props: { title: '运营工作台' }, meta: { auth: true, roles: ['OPERATOR', 'ADMIN'] } },
    { path: '/admin', component: () => import('./views/WorkspacePage.vue'), props: { title: '管理工作台' }, meta: { auth: true, roles: ['ADMIN'] } },
    { path: '/forbidden', component: () => import('./views/ForbiddenPage.vue') },
    { path: '/:pathMatch(.*)*', redirect: '/' },
  ] })
  router.beforeEach(async to => {
    const session = useSession(pinia)
    await session.ensureReady()
    if (to.meta.auth && !session.user) return { path: '/login', query: { redirect: to.path } }
    if (Array.isArray(to.meta.roles) && !to.meta.roles.includes(session.user?.role)) return '/forbidden'
    if (to.path === '/login' && session.user) return '/account'
  })
  return router
}
