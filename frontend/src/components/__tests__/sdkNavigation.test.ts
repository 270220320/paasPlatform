import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest'
import { createApp, defineComponent, h, nextTick, type Component } from 'vue'
import CockpitDetailView from '@/views/CockpitDetailView.vue'
import WaylineEditorPane from '@/components/WaylineEditorPane.vue'
import { enqueueSdkLifecycle } from '@/utils/sdkContainers'

vi.mock('vue-router', () => ({
  useRoute: () => ({ query: { gatewaySn: 'dock', droneSn: 'drone' } }),
  useRouter: () => ({ push: vi.fn() }),
}))
vi.mock('vue-i18n', () => ({ useI18n: () => ({ t: (key: string) => key }) }))
vi.mock('element-plus', () => ({ ElMessage: { error: vi.fn(), warning: vi.fn() } }))
vi.mock('@/utils/loadScript', () => ({
  loadPaasJs: vi.fn().mockResolvedValue(undefined),
  loadScript: vi.fn().mockResolvedValue(undefined),
  loadStylesheet: vi.fn().mockResolvedValue(undefined),
}))
vi.mock('@/api/paas', () => ({ upsertFh2Wayline: vi.fn() }))
vi.mock('@/utils/waylineNameSync', () => ({ installWaylineRenameWatcher: () => vi.fn() }))
vi.mock('@/composables/usePaasBootstrap', () => ({
  usePaasBootstrap: () => ({
    load: async () => ({ enabled: true, frontHost: '/sdk', serverHost: '/api', gatewayBase: '/gateway' }),
    authConfig: () => ({}),
  }),
}))

function deferred() {
  let resolve!: () => void
  const promise = new Promise<void>((done) => { resolve = done })
  return { promise, resolve }
}

async function flush() {
  for (let i = 0; i < 80; i += 1) await Promise.resolve()
  await nextTick()
}

const cleanups: Array<() => void> = []
function mount(component: Component) {
  const host = document.createElement('div')
  document.body.append(host)
  const app = createApp(component)
  app.component('ElButton', defineComponent({ setup: (_, { slots }) => () => h('button', slots.default?.()) }))
  app.mount(host)
  let mounted = true
  const unmount = () => {
    if (mounted) app.unmount()
    mounted = false
    host.remove()
  }
  cleanups.push(unmount)
  return { host, unmount }
}

function installFh2() {
  const api = {
    initConfig: vi.fn(), initNotifyCenter: vi.fn(),
    loadMap: vi.fn().mockResolvedValue(undefined), loadCockpit: vi.fn(),
    destroyCockpit: vi.fn().mockResolvedValue(true),
    destroyPaasView: vi.fn().mockResolvedValue(undefined),
    destroyMap: vi.fn().mockResolvedValue(undefined),
    destroyDevice: vi.fn().mockResolvedValue(undefined),
    cockpitAppInstance: undefined as undefined | { mountPromise: Promise<void> },
  }
  Object.defineProperty(window, 'FH2', { value: api, configurable: true, writable: true })
  return api
}

beforeEach(() => {
  vi.stubGlobal('ResizeObserver', class { observe() {} disconnect() {} })
  vi.stubGlobal('requestAnimationFrame', vi.fn(() => 1))
  vi.stubGlobal('cancelAnimationFrame', vi.fn())
})

afterEach(async () => {
  cleanups.splice(0).forEach((cleanup) => cleanup())
  await enqueueSdkLifecycle(async () => {})
  document.body.replaceChildren()
  delete window.WaylineEditor
  delete window.FH2
  vi.unstubAllGlobals()
})

describe('SDK 页面切换', () => {
  it('旧驾驶舱 loadMap 未结束时，新页面等待且不会被迟到清理删除', async () => {
    const api = installFh2()
    const loading = deferred()
    api.loadMap.mockImplementationOnce(() => loading.promise)
    const oldPage = mount(CockpitDetailView)
    await vi.waitFor(() => expect(api.loadMap).toHaveBeenCalledTimes(1))
    oldPage.unmount()
    const newPage = mount(CockpitDetailView)
    await flush()
    const callsWhileLoading = api.loadMap.mock.calls.length
    loading.resolve()
    await vi.waitFor(() => expect(api.loadCockpit).toHaveBeenCalledTimes(1))
    await flush()
    expect(callsWhileLoading).toBe(1)
    expect(api.loadMap).toHaveBeenCalledTimes(2)
    expect(newPage.host.querySelector('#map-app-global')?.isConnected).toBe(true)
  })

  it('航线地图加载和编辑器异步销毁均完成后才允许驾驶舱挂载', async () => {
    const api = installFh2()
    const loading = deferred()
    const destroying = deferred()
    const editorMap = vi.fn(() => loading.promise)
    const editorDestroy = vi.fn(() => destroying.promise)
    window.WaylineEditor = class {
      mount() {}
      loadMap = editorMap
      destroy = editorDestroy
      on() {}
      render() {}
      create = vi.fn()
    }
    const editorPage = mount(WaylineEditorPane)
    await vi.waitFor(() => expect(editorMap).toHaveBeenCalledTimes(1))
    editorPage.unmount()
    const cockpit = mount(CockpitDetailView)
    await flush()
    const destroysBeforeMapSettled = editorDestroy.mock.calls.length
    loading.resolve()
    await vi.waitFor(() => expect(editorDestroy).toHaveBeenCalledTimes(1))
    await flush()
    const cockpitMapsBeforeDestroySettled = api.loadMap.mock.calls.length
    destroying.resolve()
    await vi.waitFor(() => expect(api.loadCockpit).toHaveBeenCalledTimes(1))
    expect(destroysBeforeMapSettled).toBe(0)
    expect(cockpitMapsBeforeDestroySettled).toBe(0)
    expect(cockpit.host.querySelector('#map-app-global')?.isConnected).toBe(true)
  })

  it('等待驾驶舱微应用真正挂载后才显示已就绪', async () => {
    const api = installFh2()
    const mounting = deferred()
    api.loadCockpit.mockImplementation(() => {
      api.cockpitAppInstance = { mountPromise: mounting.promise }
    })
    const page = mount(CockpitDetailView)
    await vi.waitFor(() => expect(api.loadCockpit).toHaveBeenCalledTimes(1))
    await flush()
    const readyWhileMounting = page.host.textContent?.includes('cockpit.ready')
    mounting.resolve()
    await vi.waitFor(() => expect(page.host.textContent).toContain('cockpit.ready'))
    expect(readyWhileMounting).toBe(false)
  })
})
