import { beforeEach, describe, expect, it, vi } from 'vitest'

const { httpGet } = vi.hoisted(() => ({ httpGet: vi.fn() }))

vi.mock('../http', () => ({ default: { get: httpGet } }))
vi.mock('element-plus', () => ({ ElMessage: { error: vi.fn() } }))
vi.mock('@/i18n', () => ({ default: { global: { t: (key: string) => key } } }))

import { ElMessage } from 'element-plus'
import { exportWayline } from '../wayline'

function respOf(body: string | Blob, contentType: string, disposition?: string) {
  const blob = typeof body === 'string' ? new Blob([body], { type: contentType }) : body
  return {
    data: blob,
    headers: disposition ? { 'content-disposition': disposition } : {},
  }
}

beforeEach(() => {
  vi.clearAllMocks()
})

describe('exportWayline', () => {
  /**
   * 回归：后端错误 ApiResult 与导出封装 JSON 同为 application/json，
   * 曾按 Content-Type 一刀切当错误处理，导致正常导出报“请求失败”且不下载。
   */
  it('application/json 的导出封装（无 code 字段）应正常返回 blob', async () => {
    const exportJson = JSON.stringify({ format: 'uav-wayline-export', version: 1, wayline: { name: '巡检' } })
    httpGet.mockResolvedValue(respOf(exportJson, 'application/json',
      "attachment; filename*=UTF-8''%E5%B7%A1%E6%A3%80.json"))

    const result = await exportWayline(1)

    expect(result.filename).toBe('巡检.json')
    expect(await result.blob.text()).toBe(exportJson)
    expect(ElMessage.error).not.toHaveBeenCalled()
  })

  it('错误 ApiResult（code 非 0）应弹错并 reject', async () => {
    httpGet.mockResolvedValue(respOf(
      JSON.stringify({ code: 40001, message: '航线不存在', data: null }),
      'application/json'))

    await expect(exportWayline(1)).rejects.toMatchObject({ code: 40001 })
    expect(ElMessage.error).toHaveBeenCalledWith('航线不存在')
  })

  it('KMZ 响应应直接返回 blob', async () => {
    const kmz = new Blob([new Uint8Array([0x50, 0x4b, 0x03, 0x04])], { type: 'application/vnd.google-earth.kmz' })
    httpGet.mockResolvedValue(respOf(kmz, 'application/vnd.google-earth.kmz',
      "attachment; filename*=UTF-8''a.kmz"))

    const result = await exportWayline(1)

    expect(result.filename).toBe('a.kmz')
    expect(result.blob.type).toBe('application/vnd.google-earth.kmz')
    expect(ElMessage.error).not.toHaveBeenCalled()
  })
})
