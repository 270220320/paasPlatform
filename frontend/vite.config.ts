import { defineConfig } from 'vite'
import vue from '@vitejs/plugin-vue'
import UnoCSS from 'unocss/vite'
import fs from 'node:fs'
import path from 'node:path'
import { execSync } from 'node:child_process'
import type { Plugin } from 'vite'

/** 司空私有化后端（真实地址，仅 Vite 代理目标） */
const FH2_SERVER = 'http://192.168.41.185:30812'
/** dji2 route /safety 等后台 */
const ROUTE_API = 'http://192.168.41.185:56789'

/**
 * 后端若在 WSL2 运行，Windows 的 127.0.0.1 常转发失败（NAT / localhost proxy）。
 * 启动 Vite 时解析 WSL eth0 IP，代理到该地址。
 */
function resolveBackendTarget(): string {
  const fromEnv = process.env.VITE_API_PROXY_TARGET?.trim()
  if (fromEnv) return fromEnv
  try {
    const ip = execSync('wsl -e bash -lc "hostname -I | awk \'{print $1}\'"', {
      encoding: 'utf8',
      timeout: 5000,
      windowsHide: true,
    })
      .trim()
      .split(/\s+/)[0]
    if (ip && /^\d+\.\d+\.\d+\.\d+$/.test(ip)) {
      console.log(`[vite] API proxy -> http://${ip}:8080 (WSL)`)
      return `http://${ip}:8080`
    }
  } catch {
    // ignore
  }
  console.log('[vite] API proxy -> http://127.0.0.1:8080')
  return 'http://127.0.0.1:8080'
}

/** 将 zywlgis 的 Cesium 等静态库链到 / 复制到 public/libs */
function copyZywlgisLibs(): Plugin {
  return {
    name: 'copy-zywlgis-libs-to-public',
    config() {
      const src = path.join(__dirname, 'node_modules/zywlgis/release/libs')
      const dest = path.join(__dirname, 'public/libs')
      if (!fs.existsSync(src)) {
        console.warn('[zywlgis] 未找到 node_modules/zywlgis/release/libs，空域地图可能无法加载 Cesium')
        return
      }

      let needsRecreate = false
      if (fs.existsSync(dest)) {
        try {
          const stats = fs.lstatSync(dest)
          if (stats.isSymbolicLink()) {
            const target = fs.readlinkSync(dest)
            if (!fs.existsSync(target)) {
              fs.unlinkSync(dest)
              needsRecreate = true
            }
          }
        } catch {
          try {
            fs.unlinkSync(dest)
          } catch {
            // ignore
          }
          needsRecreate = true
        }
      } else {
        needsRecreate = true
      }

      if (!needsRecreate) return

      try {
        fs.symlinkSync(src, dest, 'junction')
        console.log('[zywlgis] public/libs 符号链接已创建')
      } catch {
        console.log('[zywlgis] 符号链接失败，改为复制 public/libs（首次可能较慢）…')
        fs.cpSync(src, dest, { recursive: true })
      }
    },
  }
}

const API_TARGET = resolveBackendTarget()

export default defineConfig({
  plugins: [vue(), UnoCSS(), copyZywlgisLibs()],
  resolve: {
    alias: {
      '@': path.resolve(__dirname, 'src'),
    },
  },
  optimizeDeps: {
    include: ['zywlgis', 'jquery', '@turf/turf'],
  },
  server: {
    port: 5173,
    host: true,
    proxy: {
      '/api': {
        target: API_TARGET,
        changeOrigin: true,
      },
      /**
       * 对齐已通项目：
       *   serverUrl  = ${paasOrigin}/paas-api
       *   apiBaseUrl = ${paasOrigin}/paas-api/gateway
       * 浏览器只访问同源 /paas-api/*，由这里转发到 30812。
       */
      '/paas-api': {
        target: FH2_SERVER,
        changeOrigin: true,
        rewrite: (p) => p.replace(/^\/paas-api/, ''),
        ws: true,
      },
      /**
       * 对齐已通 paasVue：驾驶舱 / 通知中心 WS 使用独立路径，
       * 避免与 /paas-api HTTP 代理互相干扰。
       */
      '/paas-ws': {
        target: 'ws://192.168.41.185:30812',
        changeOrigin: true,
        ws: true,
        rewrite: (p) => p.replace(/^\/paas-ws/, ''),
      },
      /** 可选：同源代理 frontHost 静态资源（paas.js / Cesium 等）；运行时 URL 仍以库表 frontHost 为准 */
      '/paas-front': {
        target: 'http://192.168.41.185',
        changeOrigin: true,
        rewrite: (p) => p.replace(/^\/paas-front/, ''),
      },
      /** dji2 后台：航线 /route，飞行计划 /task，安全区 /safety */
      '/route-api': {
        target: ROUTE_API,
        changeOrigin: true,
        rewrite: (p) => p.replace(/^\/route-api/, ''),
      },
    },
  },
})
