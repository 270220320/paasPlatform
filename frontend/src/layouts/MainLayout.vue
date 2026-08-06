<template>

  <el-container class="layout">

    <el-header class="header">

      <div class="brand">

        <span class="mark">UP</span>

        <strong>UAVPlatform</strong>

      </div>

      <el-menu

        mode="horizontal"

        :ellipsis="false"

        router

        :default-active="active"

        :key="active"

        background-color="#141414"

        text-color="#cfd3dc"

        active-text-color="#409eff"

      >

        <el-menu-item index="/">{{ t('nav.home') }}</el-menu-item>

        <el-menu-item v-if="can('dashboard:read')" index="/dashboard">{{ t('nav.dashboard') }}</el-menu-item>

        <el-menu-item v-if="can('map:read')" index="/map">{{ t('nav.map') }}</el-menu-item>

        <el-menu-item v-if="can('map:read')" index="/airspace">{{ t('nav.airspace') }}</el-menu-item>

        <el-menu-item v-if="can('device:read')" index="/devices">{{ t('nav.devices') }}</el-menu-item>

        <el-menu-item v-if="can('wayline:read')" index="/waylines">{{ t('nav.waylines') }}</el-menu-item>

        <el-menu-item v-if="can('mission:read')" index="/missions">{{ t('nav.missions') }}</el-menu-item>

        <el-menu-item v-if="can('device:read')" index="/cockpit">{{ t('nav.cockpit') }}</el-menu-item>

        <el-menu-item v-if="can('library:read')" index="/library">{{ t('nav.library') }}</el-menu-item>

        <el-menu-item v-if="can('algorithm:read')" index="/algorithms">{{ t('nav.algorithms') }}</el-menu-item>

        <el-menu-item v-if="can('member:read')" index="/members">{{ t('nav.members') }}</el-menu-item>

        <el-menu-item v-if="can('role:manage')" index="/roles">{{ t('nav.roles') }}</el-menu-item>

      </el-menu>

      <div class="right">

        <LocaleSwitcher />

        <span class="user">{{ userStore.user?.displayName }}</span>

        <el-button link type="primary" @click="onLogout">{{ t('nav.logout') }}</el-button>

      </div>

    </el-header>

    <el-main class="main" :class="{ 'main--bleed': isBleedPage, 'main--bleed-light': isDevicePage }">

      <router-view />

    </el-main>

  </el-container>

</template>



<script setup lang="ts">

import { computed } from 'vue'

import { useRoute, useRouter } from 'vue-router'

import { useI18n } from 'vue-i18n'

import { ElMessage } from 'element-plus'

import { logoutApi } from '@/api/auth'

import { useUserStore } from '@/stores/user'

import LocaleSwitcher from '@/components/LocaleSwitcher.vue'



const { t } = useI18n()

const route = useRoute()

const router = useRouter()

const userStore = useUserStore()

const active = computed(() => {
  const path = route.path
  // 子路由保持父级菜单高亮（如 /missions/12 → /missions）
  const parents = [
    '/missions',
    '/waylines',
    '/cockpit',
    '/library',
    '/devices',
    '/algorithms',
    '/members',
    '/roles',
    '/dashboard',
    '/airspace',
    '/map',
  ]
  const hit = parents.find((prefix) => path === prefix || path.startsWith(`${prefix}/`))
  return hit || path
})

const isDevicePage = computed(() => route.name === 'devices')

const isBleedPage = computed(
  () =>
    route.name === 'home' ||
    route.name === 'airspace' ||
    route.name === 'map' ||
    route.name === 'devices' ||
    route.name === 'waylines' ||
    route.name === 'mission-create' ||
    route.name === 'mission-replay' ||
    route.name === 'cockpit-detail' ||
    route.name === 'wayline-editor',
)



function can(code: string) {

  return !!userStore.user?.permissions?.includes(code)

}



async function onLogout() {

  try {

    await logoutApi()

  } catch {

    // ignore

  }

  userStore.clear()

  ElMessage.success(t('layout.logoutSuccess'))

  await router.replace({ name: 'login' })

}

</script>



<style scoped lang="scss">

.layout {

  height: 100vh;

  background: #0a0a0a;

}



.header {

  height: 60px;

  flex-shrink: 0;

  display: flex;

  align-items: center;

  gap: 16px;

  border-bottom: 1px solid #363637;

  background: #141414;

}



.brand {

  display: flex;

  align-items: center;

  gap: 8px;

  color: #e5eaf3;

  white-space: nowrap;

}



.mark {

  width: 28px;

  height: 28px;

  border-radius: 6px;

  display: grid;

  place-items: center;

  background: linear-gradient(135deg, #409eff, #337ecc);

  color: #fff;

  font-size: 12px;

  font-weight: 700;

}



.right {

  margin-left: auto;

  display: flex;

  align-items: center;

  gap: 12px;

}



.user {

  color: #a3a6ad;

  font-size: 13px;

}



.main {

  flex: 1;

  min-height: 0;

  overflow: auto;

  padding: 20px;

}



.main--bleed {

  padding: 0;

  overflow: hidden;

}



.main--bleed-light {

  background: #fff;

}



:deep(.el-menu) {

  flex: 1;

  border-bottom: none;

}

</style>

