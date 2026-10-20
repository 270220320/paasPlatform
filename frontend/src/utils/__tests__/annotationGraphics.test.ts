import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest'
import { pruneAnnotationGraphics } from '../annotationGraphics'

const containerId = 'map-annotation-gis'
const registryKey = `${containerId}-&-ZYWL-&-Annotation`

function drawing(id: string, container = containerId) {
  const visible = new Set([id, `${id}-label`, `${id}-node`])
  const listeners = new Set<() => void>()
  const mousemove = () => { visible.add(id) }
  listeners.add(mousemove)
  const graphic = {
    containerID_: `${container}-maptalks`,
    data_: { ID: id, coordinates: [{ longitude: 117, latitude: 39 }], isEditable: true },
    isEditable_: true,
    isStartDraw_: true,
    mousemove,
    // The SDK clears rendered elements but does not unsubscribe mousemove.
    clear: vi.fn(() => visible.clear()),
  }
  const map = { off: vi.fn((event: string, handler: () => void) => {
    if (event === 'mousemove') listeners.delete(handler)
  }) }
  return { graphic, map, visible, move: () => listeners.forEach((handler) => handler()) }
}

beforeEach(() => { vi.stubGlobal('MapDatas', new Map()) })
afterEach(() => { vi.unstubAllGlobals() })

describe('removed annotation graphics', () => {
  it.each(['polygon', 'circle'])('does not redraw a deleted %s when the mouse moves', (id) => {
    const removed = drawing(id)
    const registry = new Map([[id, removed.graphic]])
    vi.stubGlobal('MapDatas', new Map([[registryKey, registry]]))
    vi.stubGlobal('mapEngines', new Map([[removed.graphic.containerID_, removed.map]]))
    vi.stubGlobal('EditElement', removed.graphic)

    pruneAnnotationGraphics(containerId, [])
    removed.move()

    expect(removed.visible.size).toBe(0)
    expect(registry.size).toBe(0)
    expect(removed.graphic.isEditable_).toBe(false)
    expect(removed.graphic.isStartDraw_).toBe(false)
    expect(removed.graphic.data_.coordinates).toEqual([])
    expect(Reflect.get(window, 'EditElement')).toBeUndefined()
  })

  it('preserves remaining annotations and other map containers', () => {
    const removed = drawing('removed')
    const remaining = drawing('remaining')
    const other = drawing('removed', 'another-map')
    const registry = new Map([['removed', removed.graphic], ['remaining', remaining.graphic]])
    const otherRegistry = new Map([['removed', other.graphic]])
    vi.stubGlobal('MapDatas', new Map([
      [registryKey, registry], ['another-map-&-ZYWL-&-Annotation', otherRegistry],
    ]))
    vi.stubGlobal('mapEngines', new Map([[removed.graphic.containerID_, removed.map]]))
    vi.stubGlobal('EditElement', other.graphic)

    pruneAnnotationGraphics(containerId, ['remaining'])

    expect([...registry.keys()]).toEqual(['remaining'])
    expect(remaining.graphic.clear).not.toHaveBeenCalled()
    expect(remaining.visible.size).toBe(3)
    expect(other.graphic.clear).not.toHaveBeenCalled()
    expect(otherRegistry.get('removed')).toBe(other.graphic)
    expect(Reflect.get(window, 'EditElement')).toBe(other.graphic)
  })

  it('cleans the old draft after a save assigns a database ID, including twin instances', () => {
    const draft = drawing('draft')
    const draft2d = drawing('draft')
    const saved = drawing('903')
    const saved2d = drawing('903')
    const registry = new Map([
      ['draft', draft.graphic], ['draft-2d', draft2d.graphic],
      ['903', saved.graphic], ['903-2d', saved2d.graphic],
    ])
    vi.stubGlobal('MapDatas', new Map([[registryKey, registry]]))
    vi.stubGlobal('mapEngines', new Map([[draft.graphic.containerID_, draft.map]]))

    pruneAnnotationGraphics(containerId, ['903'])
    draft.move()
    pruneAnnotationGraphics(containerId, ['903'])

    expect([...registry.keys()]).toEqual(['903', '903-2d'])
    expect(draft.graphic.clear).toHaveBeenCalledTimes(1)
    expect(draft2d.graphic.clear).toHaveBeenCalledTimes(1)
    expect(saved.graphic.clear).not.toHaveBeenCalled()
    expect(saved2d.graphic.clear).not.toHaveBeenCalled()
    expect(draft.visible.size).toBe(0)
  })

  it('can run before the map has initialized', () => {
    expect(() => pruneAnnotationGraphics(containerId, [])).not.toThrow()
  })
})
