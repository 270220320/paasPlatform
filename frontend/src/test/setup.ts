/**
 * Vitest 全局测试环境 setup。
 * jsdom 环境下补齐 fetch / localStorage 的最小实现，供 api 层单测使用。
 */

// jsdom 已内置 localStorage，但部分旧版可能缺失，做一层兜底
if (typeof globalThis.localStorage === 'undefined' || globalThis.localStorage === null) {
  const store = new Map<string, string>()
  Object.defineProperty(globalThis, 'localStorage', {
    value: {
      getItem: (k: string) => store.get(k) ?? null,
      setItem: (k: string, v: string) => void store.set(k, String(v)),
      removeItem: (k: string) => void store.delete(k),
      clear: () => store.clear(),
      key: (i: number) => Array.from(store.keys())[i] ?? null,
      get length() {
        return store.size
      },
    },
    configurable: true,
  })
}
