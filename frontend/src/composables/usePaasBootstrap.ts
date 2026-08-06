import { ref } from 'vue'
import { ElMessage } from 'element-plus'
import { fetchPaasBootstrap, type PaasBootstrap } from '@/api/paas'

const cache = ref<PaasBootstrap | null>(null)

/**
 * 对齐已通 paasVue：
 *   serverUrl  = ${paasOrigin}/paas-api              （不带 /gateway，给 loadMap）
 *   apiBaseUrl = ${paasOrigin}/paas-api/gateway     （带 /gateway，给 initConfig）
 *   wssUrl     = ${ws}://${host}/paas-ws/openapi/... （独立 WS 代理）
 */
export function withPaasProxy(cfg: PaasBootstrap): PaasBootstrap {
  if (typeof window === 'undefined') {
    return cfg
  }
  const paasOrigin = window.location.origin
  const wsProto = window.location.protocol === 'https:' ? 'wss' : 'ws'
  return {
    ...cfg,
    serverHost: `${paasOrigin}/paas-api`,
    gatewayBase: `${paasOrigin}/paas-api/gateway`,
    wssUrl: `${wsProto}://${window.location.host}/paas-ws/openapi/v0.9/notification/ws/v1/notifications`,
  }
}

export const withDevProxy = withPaasProxy

export function usePaasBootstrap() {
  async function load(force = false) {
    if (cache.value && !force) return cache.value
    const { data } = await fetchPaasBootstrap()
    const cfg = withPaasProxy(data.data)
    if (!cfg?.enabled) {
      ElMessage.warning('司空 PaaS 集成未启用')
    }
    if (cfg?.userToken) {
      localStorage.setItem('x-user-token', cfg.userToken)
    }
    cache.value = cfg
    return cfg
  }

  /**
   * 官方 IAuth：authKeys + headers 函数。
   * Apifox 示例只用 X-User-Token（api.auth / ws.auth 共用同一对象）。
   */
  function authConfig(cfg: PaasBootstrap) {
    return {
      authKeys: ['X-User-Token'],
      headers: () => ({
        'X-User-Token': localStorage.getItem('x-user-token') || cfg.userToken || '',
      }),
    }
  }

  return { cache, load, authConfig }
}
