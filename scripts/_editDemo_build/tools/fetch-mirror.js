#!/usr/bin/env node
/**
 * 司空 paas.js 静态资源镜像爬虫：从 76.141 递归抓取 WaylineEditor 运行所需全部资源。
 * 已存在的文件跳过（支持增量补抓）；404 记录到 tools/missing.txt 供分析。
 * 用法：node tools/fetch-mirror.mjs
 */
const fs = require('fs')
const path = require('path')

const BASE = 'http://192.168.76.141'
const MIRROR = path.join(__dirname, '..', 'mirror')
const CESIUM_DIR = 'Cesium-1.116.0-fix-27-20260509122436'

// 种子：paas.js 硬编码清单 + 已知入口
const SEEDS = [
  'paas.js',
  'paas.css',
  'paas-core.54517b54926d70c7.js',
  `${CESIUM_DIR}/Cesium.js`,
  `${CESIUM_DIR}/Widgets/widgets.css`,
  'wasm-v06.00.00.128/libppe.js',
  'wasm-v06.00.00.128/libwpmz.js',
  'wasm-v06.00.00.128/libppe.wasm',
  'wasm-v06.00.00.128/libwpmz.wasm',
  'ar-wasm-2025.11-6/ArCore.js',
  'ar-wasm-2025.11-6/ArCore.wasm',
  'lidar-wasm-v11/liblidar.js',
  'lidar-wasm-v11/liblidar.wasm',
  'mine-decoder/MineDecoder.js',
  'mine-decoder/MineDecoder.wasm',
  'drtc-0.27.0.139/sdk.js',
  'paasjs-demo/wayline-edit-sdk/index.js',
  'paasjs-demo/wayline-edit-sdk/index.css',
  'map-app/index.html',
]

const queue = [...SEEDS]
const seen = new Set(queue)
const missing = []
let fetched = 0
let skipped = 0

// 从内容中发现更多资源
function discover(p, content) {
  const found = new Set()
  const add = (ref) => {
    if (!ref || ref.startsWith('http') || ref.startsWith('data:') || ref.startsWith('//')) return
    // 绝对路径（/xxx）去根斜杠；相对路径按基准目录拼
    let full = ref.startsWith('/') ? ref.slice(1) : path.posix.join(path.posix.dirname(p), ref)
    full = full.split('?')[0].split('#')[0]
    if (!/\.(js|css|wasm|json|png|jpe?g|svg|gif|woff2?|ttf|gltf|glb|kml|bin|cur|ico|html)$/i.test(full)) return
    if (full.length > 200) return
    found.add(full)
  }

  if (/\.(js|mjs)$/i.test(p)) {
    // 字符串字面量里的资源路径（Cesium 风格相对引用 + hash chunk + wasm）
    const patterns = [
      /["'`]((?:Workers|Assets|ThirdParty|Widgets|Core)\/[A-Za-z0-9_\-/.]{2,80}\.(?:js|json|wasm|glb|bin|png|kml))["'`]/g,
      /["'`]([A-Za-z0-9_\-/.]{2,60}\.[a-f0-9]{6,}\.(?:js|css|png|woff2?|gltf|glb|wasm))["'`]/g,
      /["'`]([A-Za-z0-9_\-/.]{2,60}\.wasm)["'`]/g,
      /["'`](assets\/[A-Za-z0-9_\-/.]{2,80}\.(?:gltf|glb|png|svg|json))["'`]/g,
      /["'`](static\/(?:js|css)\/[A-Za-z0-9_.\-]+\.js)["'`]/g,
    ]
    for (const re of patterns) {
      let m
      while ((m = re.exec(content)) !== null) {
        let ref = m[1]
        // Cesium 内部相对引用（Workers/... 等）基准是 Cesium 目录
        if (/^(Workers|Assets|ThirdParty|Widgets|Core)\//.test(ref) && p.startsWith(CESIUM_DIR)) {
          ref = `${CESIUM_DIR}/${ref}`
        }
        add(ref)
      }
    }
  }
  if (/\.css$/i.test(p)) {
    let m
    const re = /url\((['"]?)([^'")]+)\1\)/g
    while ((m = re.exec(content)) !== null) add(m[2])
  }
  if (/\.html?$/i.test(p)) {
    let m
    const res = [/<script[^>]+src=["']([^"']+)["']/g, /<link[^>]+href=["']([^"']+)["']/g]
    for (const re of res) while ((m = re.exec(content)) !== null) add(m[1])
  }
  return [...found]
}

async function fetchOne(p) {
  const dest = path.join(MIRROR, p)
  if (fs.existsSync(dest) && fs.statSync(dest).size > 0) {
    skipped++
    // 已有文件也要扫描内容以发现新引用（增量模式发现链不断）
    return discover(p, fs.readFileSync(dest, 'utf8'))
  }
  fs.mkdirSync(path.dirname(dest), { recursive: true })
  try {
    const res = await fetch(`${BASE}/${encodeURI(p)}`)
    if (!res.ok) {
      if (res.status === 404) missing.push(p)
      else console.warn(`  [${res.status}] ${p}`)
      return []
    }
    const buf = Buffer.from(await res.arrayBuffer())
    fs.writeFileSync(dest, buf)
    fetched++
    if (fetched % 20 === 0) console.log(`  ... fetched ${fetched} (queue ${queue.length})`)
    // 二进制不扫内容；文本类扫描
    if (/\.(js|mjs|css|html?|json)$/i.test(p) && buf.length < 40 * 1024 * 1024) {
      return discover(p, buf.toString('utf8'))
    }
    return []
  } catch (e) {
    console.warn(`  [err] ${p}: ${e.message}`)
    return []
  }
}

async function main() {
  console.log(`mirror -> ${MIRROR}\nbase   -> ${BASE}`)
  let rounds = 0
  while (queue.length && rounds < 30) {
    rounds++
    const batch = queue.splice(0, queue.length)
    console.log(`round ${rounds}: ${batch.length} items`)
    const conc = 8
    const results = []
    for (let i = 0; i < batch.length; i += conc) {
      const chunk = batch.slice(i, i + conc)
      results.push(...(await Promise.all(chunk.map(fetchOne))))
    }
    for (const refs of results) {
      for (const r of refs) {
        if (!seen.has(r)) {
          seen.add(r)
          queue.push(r)
        }
      }
    }
  }
  fs.writeFileSync(path.join(__dirname, 'missing.txt'), missing.sort().join('\n'))
  console.log(`\ndone: fetched=${fetched} skipped(already)=${skipped} missing404=${missing.length}`)
  if (missing.length) console.log(`missing list -> tools/missing.txt（前 20 条）:\n  ` + missing.slice(0, 20).join('\n  '))
}
main()
