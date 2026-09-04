import { createRouter, createWebHistory } from 'vue-router'
import { useUserStore } from '@/stores/user'

const router = createRouter({
  history: createWebHistory(),
  routes: [
    {
      path: '/login',
      name: 'login',
      component: () => import('@/views/LoginView.vue'),
      meta: { public: true },
    },
    {
      path: '/',
      component: () => import('@/layouts/MainLayout.vue'),
      children: [
        {
          path: '',
          name: 'home',
          component: () => import('@/views/HomeView.vue'),
        },
        {
          path: 'dashboard',
          redirect: '/',
        },
        {
          path: 'live',
          name: 'live-screen',
          component: () => import('@/views/LiveScreenView.vue'),
          meta: { fullscreen: true },
        },
        {
          path: 'map',
          name: 'map',
          component: () => import('@/views/MapView.vue'),
        },
        {
          path: 'airspace',
          name: 'airspace',
          component: () => import('@/views/AirspaceView.vue'),
          meta: { fullscreen: true },
        },
        {
          path: 'waylines',
          name: 'waylines',
          component: () => import('@/views/WaylinesView.vue'),
        },
        {
          path: 'waylines/editor',
          name: 'wayline-editor',
          component: () => import('@/views/WaylineEditorView.vue'),
          meta: { fullscreen: true },
        },
        {
          path: 'missions',
          name: 'missions',
          component: () => import('@/views/MissionsView.vue'),
        },
        {
          path: 'missions/create',
          name: 'mission-create',
          component: () => import('@/views/MissionCreateView.vue'),
        },
        {
          path: 'missions/:id',
          name: 'mission-detail',
          component: () => import('@/views/MissionDetailView.vue'),
        },
        {
          path: 'missions/:planId/replay/:executionId',
          name: 'mission-replay',
          component: () => import('@/views/MissionReplayView.vue'),
          meta: { fullscreen: true },
        },
        {
          path: 'cockpit',
          name: 'cockpit',
          component: () => import('@/views/CockpitListView.vue'),
        },
        {
          path: 'cockpit/detail',
          name: 'cockpit-detail',
          component: () => import('@/views/CockpitDetailView.vue'),
          meta: { fullscreen: true },
        },
        {
          path: 'library',
          name: 'library',
          component: () => import('@/views/LibraryView.vue'),
        },
        {
          path: 'data',
          redirect: '/library',
        },
        {
          path: 'algorithms',
          redirect: '/',
        },
        {
          path: 'members',
          name: 'members',
          component: () => import('@/views/MembersView.vue'),
        },
        {
          path: 'roles',
          name: 'roles',
          component: () => import('@/views/RolesView.vue'),
        },
        {
          path: 'devices',
          name: 'devices',
          component: () => import('@/views/DeviceManagerView.vue'),
        },
        {
          path: 'devices/manager',
          redirect: '/devices',
        },
        {
          path: 'profile',
          name: 'profile',
          component: () => import('@/views/ProfileView.vue'),
        },
      ],
    },
    {
      path: '/:pathMatch(.*)*',
      redirect: '/',
    },
  ],
})

router.beforeEach((to) => {
  const userStore = useUserStore()
  if (!to.meta.public && !userStore.token) {
    return { name: 'login', query: { redirect: to.fullPath } }
  }
  if (to.name === 'login' && userStore.token) {
    return { name: 'home' }
  }
  return true
})

export default router
