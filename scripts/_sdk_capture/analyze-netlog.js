#!/usr/bin/env node
/** 分析 runtime-netlog：按端点归类，输出请求/响应结构摘要。用法：node analyze-netlog.js <netlog.json> */
const fs = require('fs')

const raw = JSON.parse(fs.readFileSync(process.argv[2], 'utf8'))
const log = raw.log || []
console.log(`# total entries: ${log.length}, captured at ${raw.capturedAt}, href=${raw.href}\n`)

// 归一化 URL：保留 path，query 保留关键参数（ak/sign 类只留 key 名）
function norm(url) {
  if (!url) return '(no url)'
  let u
  try { u = new URL(url, 'http://__host__/') } catch (e) { return url.slice(0, 160) }
  const keep = []
  u.searchParams.forEach((v, k) => keep.push(/^(ak|sign|X-Amz|token)$/i.test(k) ? k + '=<redacted>' : k + '=' + String(v).slice(0, 24)))
  const q = keep.length ? '?' + keep.join('&') : ''
  return u.pathname + q
}

// 分组：AiFlow 自有 /api/v1 单列；其余全列
const groups = new Map()
for (const e of log) {
  const isHttp = e.kind === 'fetch' || e.kind === 'xhr'
  const key = e.kind === 'ws-open' ? 'WS ' + norm(e.url)
    : e.kind.startsWith('ws-') ? null
    : e.method + ' ' + norm(e.url)
  if (!key) continue
  if (!groups.has(key)) groups.set(key, [])
  groups.get(key).push(e)
}

const own = []
const sdk = []
for (const [k, arr] of groups) {
  (k.includes('/api/v1/') && !k.includes('/paas-api') ? own : sdk).push([k, arr])
}

const fmt = (v) => {
  if (v == null) return '-'
  if (typeof v === 'object') return JSON.stringify(v).slice(0, 400)
  return String(v).slice(0, 400)
}

console.log('================ SDK / 司空侧端点（' + sdk.length + '）================')
for (const [k, arr] of sdk.sort()) {
  const ok = arr.filter((e) => e.status >= 200 && e.status < 300)
  console.log(`\n### ${k}    [${arr.length}次, 成功${ok.length}]`)
  const sample = ok[0] || arr[0]
  if (sample.kind === 'ws-open') continue
  console.log(`  req : ${fmt(sample.req)}`)
  console.log(`  resp: ${fmt(sample.resp || sample.respType)}`)
}

console.log(`\n================ AiFlow 自有 /api/v1（${own.length} 个，仅列名）================`)
own.forEach(([k, arr]) => console.log(`  ${k}    [${arr.length}]`))

// WS 消息单独归类
const wsMsgs = log.filter((e) => e.kind === 'ws-msg')
if (wsMsgs.length) {
  console.log(`\n================ WS 消息（${wsMsgs.length} 帧）================`)
  const byDir = { in: new Map(), out: new Map() }
  for (const m of wsMsgs) {
    const d = byDir[m.dir]
    let biz = m.data && m.data.slice(0, 200)
    if (!biz) continue
    // 按 method/event 字段聚类
    let key = m.dir + ' ' + biz.slice(0, 80)
    if (!d.has(key)) d.set(key, 0)
    d.set(key, d.get(key) + 1)
  }
  for (const [dir, m] of Object.entries(byDir)) {
    console.log(`\n-- ${dir} --`)
    for (const [k, c] of m) console.log(`  [${c}] ${k}`)
  }
}

// PUT/POST 大对象上传（KMZ/快照线索：req 是 Blob 或大字符串）
console.log('\n================ 上传类请求（Blob/大 body）================')
for (const e of log) {
  const isUpload = e.kind === 'fetch' || e.kind === 'xhr'
  if (!isUpload) continue
  const up = e.method === 'PUT' || /upload|snapshot/i.test(e.url || '')
  if (!up) continue
  console.log(`  ${e.method} ${norm(e.url)} status=${e.status} req=${fmt(e.req)} resp=${fmt(e.resp)}`)
}
