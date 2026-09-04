import { describe, expect, it } from 'vitest'
import { mapCockpitDevices } from '../paas'
import type { DeviceVO } from '../device'

/** 构造一条机场主记录，模拟司空设备管理接口返回结构 */
function dockPayload(overrides: Record<string, unknown> = {}) {
  return {
    code: 0,
    message: 'OK',
    data: {
      list: [
        {
          device_sn: '8UUXN2K00A0144',
          device_callsign: '',
          device_model: { key: '3-3-0', domain: '3', type: '3', sub_type: '0', name: 'DJI Dock 3', class: 'airport' },
          device_online_status: true,
          device_working_status: 1,
          device_state: {
            sub_device: {
              device_sn: '1581F8HGX252500A009X',
              device_online_status: 0,
              device_paired: 1,
              device_model_key: '0-100-1',
            },
          },
          associate_drone_device_info: {
            device_sn: '1581F8HGX252500A009X',
            device_model: { key: '0-100-1', name: 'Matrice 4TD', class: 'drone' },
            device_online_status: true,
          },
          ...overrides,
        },
      ],
    },
  }
}

describe('mapCockpitDevices', () => {
  it('从 device_state.sub_device 提取飞行器 SN', () => {
    const [dock] = mapCockpitDevices(dockPayload())
    expect(dock.sn).toBe('8UUXN2K00A0144')
    expect(dock.gatewaySn).toBe('8UUXN2K00A0144')
    expect(dock.droneSn).toBe('1581F8HGX252500A009X')
  })

  it('sub_device 缺失时回退到 associate_drone_device_info', () => {
    const payload = dockPayload({})
    delete (payload.data.list[0] as Record<string, unknown>).device_state
    const [dock] = mapCockpitDevices(payload)
    expect(dock.droneSn).toBe('1581F8HGX252500A009X')
  })

  /**
   * 回归：asDeviceList 会写入空串 cockpit_drone_sn:''，
   * 用 ?? 无法回退到 sub_device，导致飞行器 SN 丢失。
   */
  it('空串 cockpit_drone_sn 不应挡住 sub_device 回退（回归 ?? bug）', () => {
    // 模拟 asDeviceList 处理后写入的 cockpit_drone_sn: ''
    const payload = dockPayload({ cockpit_drone_sn: '' })
    const [dock] = mapCockpitDevices(payload)
    expect(dock.droneSn).toBe('1581F8HGX252500A009X')
  })

  it('无飞行器时 droneSn 为空串', () => {
    const payload = dockPayload({})
    delete (payload.data.list[0] as Record<string, unknown>).device_state
    delete (payload.data.list[0] as Record<string, unknown>).associate_drone_device_info
    const [dock] = mapCockpitDevices(payload)
    expect(dock.droneSn).toBe('')
  })

  it('机场名称空 callsign 时回退到 device_model.name', () => {
    const [dock] = mapCockpitDevices(dockPayload())
    expect(dock.name).toBe('DJI Dock 3')
  })

  it('deviceType 来自 device_model.class', () => {
    const [dock] = mapCockpitDevices(dockPayload())
    expect(dock.deviceType).toBe('airport')
  })

  it('model 优先取 device_model.name', () => {
    const [dock] = mapCockpitDevices(dockPayload())
    expect(dock.model).toBe('DJI Dock 3')
  })

  /**
   * 回归（2026-09-03 实际故障）：机场在线（device_online_status=true）、
   * 飞行器舱内未开机（sub_device.device_online_status=0）是 Dock 常态，
   * 旧逻辑“机场在线时飞行器优先”把机场误报为离线。修复后机场状态以主记录为准。
   */
  it('onlineState：机场在线 + 飞行器未开机(0) → 机场判在线（回归）', () => {
    const [dock] = mapCockpitDevices(dockPayload())
    expect(dock.onlineState).toBe('online')
  })

  it('onlineState：机场在线时飞行器在线(1)→online', () => {
    const payload = dockPayload({})
    ;((payload.data.list[0] as Record<string, unknown>).device_state as Record<string, unknown>)
      .sub_device = { device_sn: 'DRONE1', device_online_status: 1 }
    const [dock] = mapCockpitDevices(payload)
    expect(dock.onlineState).toBe('online')
  })

  /**
   * 回归（2026-08-28 实际故障）：机场离线一周（device_online_status=false），
   * 但 device_state 是最后一次 OSD 快照，sub_device.device_online_status 残留 1，
   * 旧逻辑“飞行器优先”误报在线。修复后机场主记录优先，离线必判离线。
   */
  it('onlineState：机场离线时 sub_device 快照残留 1 不得误报在线（回归）', () => {
    const payload = dockPayload({
      device_online_status: false,
      device_state: {
        sub_device: {
          device_sn: '1581F8HGX252500A009X',
          device_online_status: 1,
          device_paired: 1,
        },
      },
      associate_drone_device_info: {
        device_sn: '1581F8HGX252500A009X',
        device_model: { key: '0-100-1', name: 'Matrice 4TD', class: 'drone' },
        device_online_status: false,
      },
    })
    const [dock] = mapCockpitDevices(payload)
    expect(dock.onlineState).toBe('offline')
  })

  it('接口 code 非 0 时 data 仍会被解析（上层 fetchCockpitDevices 负责抛错）', () => {
    const payload = { code: 50001, message: 'invalid', data: { list: [] } }
    expect(mapCockpitDevices(payload)).toEqual<DeviceVO[]>([])
  })

  it('data.list 为空数组时返回空数组', () => {
    expect(mapCockpitDevices({ code: 0, data: { list: [] } })).toEqual([])
  })
})
