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
   * 官方 IAuth：authKeys + headers 函数，两者 key 必须一一对应（鉴权接入指南硬约束）。
   * 上云 API 2 网关自身不鉴权，而是回调 ISV_WEBHOOK_URL 指向的自建 Webhook 判定；
   * 开发期服务器默认 webhook（pf-gateway-service:8080/gateway/test/webhook-auth）
   * 放行全部请求，约定头 user_id: paas_test（官方文档 2.4）。
   * X-User-Token 保留透传，供后续切换自建 Webhook 时复用（无值时为空串）。
   */
  function authConfig(cfg: PaasBootstrap) {
    return {
      authKeys: ['X-User-Token', 'user_id'],
      headers: () => ({
        'X-User-Token': localStorage.getItem('x-user-token') || cfg.userToken || '',
        user_id: localStorage.getItem('paas-user-id') || 'paas_test',
      }),
    }
  }

  return { cache, load, authConfig }
}
