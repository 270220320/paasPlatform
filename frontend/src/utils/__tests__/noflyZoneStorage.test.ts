import { beforeEach, describe, expect, it } from 'vitest'
import {
  createNoFlyZone,
  endZoneEdit,
  loadNoFlyZones,
  saveNoFlyZones,
  startZoneEdit,
  type NoFlyZoneRecord,
} from '../noflyZoneStorage'

describe('createNoFlyZone', () => {
  it('多边形：默认名称、type、坐标空', () => {
    const zone = createNoFlyZone(3)
    expect(zone.type).toBe(3)
    expect(zone.name).toBe('多边形限飞区')
    expect(zone.coordinates).toEqual([])
    expect(zone.symbol.radius).toBeUndefined()
    expect(zone._centerPointShow).toBe(false)
  })

  it('圆形：带 radius 并显示中心点', () => {
    const zone = createNoFlyZone(4)
    expect(zone.type).toBe(4)
    expect(zone.name).toBe('圆形限飞区')
    expect(zone.symbol.radius).toBe(300)
    expect(zone._centerPointShow).toBe(true)
  })

  it('自定义名称覆盖默认', () => {
    const zone = createNoFlyZone(3, '禁飞区A')
    expect(zone.name).toBe('禁飞区A')
    expect(zone.customLabel.content).toBe('禁飞区A')
  })

  it('ID 唯一（连续创建不重复）', () => {
    const a = createNoFlyZone(3)
    const b = createNoFlyZone(3)
    expect(a.ID).toBeTruthy()
    expect(a.ID).not.toBe(b.ID)
  })
})

describe('startZoneEdit / endZoneEdit', () => {
  it('start 开启编辑态并恢复中心点显示', () => {
    const zone = endZoneEdit(createNoFlyZone(4))
    const editing = startZoneEdit(zone)
    expect(editing.isEditable).toBe(true)
    expect(editing._mapClickAddNode).toBe(true)
    expect(editing._centerPointShow).toBe(true)
  })

  it('end 关闭编辑态', () => {
    const zone = startZoneEdit(createNoFlyZone(3))
    const done = endZoneEdit(zone)
    expect(done.isEditable).toBe(false)
    expect(done._mapClickAddNode).toBe(false)
    expect(done._centerPointShow).toBe(false)
  })
})

describe('saveNoFlyZones / loadNoFlyZones', () => {
  beforeEach(() => localStorage.clear())

  it('保存后能读回，且加载时强制结束编辑态', () => {
    const editing = startZoneEdit(createNoFlyZone(3, 'A'))
    saveNoFlyZones([editing])
    const loaded = loadNoFlyZones()
    expect(loaded).toHaveLength(1)
    expect(loaded[0].name).toBe('A')
    expect(loaded[0].isEditable).toBe(false)
    expect(loaded[0]._mapClickAddNode).toBe(false)
  })

  it('加载时 visible 为 undefined 默认 true', () => {
    const zone: NoFlyZoneRecord = { ...createNoFlyZone(3), visible: undefined as unknown as boolean }
    saveNoFlyZones([zone])
    const [loaded] = loadNoFlyZones()
    expect(loaded.visible).toBe(true)
  })

  it('无数据时返回空数组', () => {
    expect(loadNoFlyZones()).toEqual([])
  })

  it('损坏 JSON 不抛错，返回空数组', () => {
    localStorage.setItem('aiflow-nofly-zones', '{not json')
    expect(loadNoFlyZones()).toEqual([])
  })
})
