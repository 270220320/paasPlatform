#!/usr/bin/env node
/**
 * 静态分析 wayline-edit-sdk bundle：提取 API 路径 / WS 端点 / wasm / 子资源引用。
 * 用法：node extract-apis.js <bundle.js>
 */
const fs = require('fs')
const path = require('path')

const file = process.argv[2]
if (!file || !fs.existsSync(file)) {
  console.error('usage: node extract-apis.js <bundle.js>')
  process.exit(1)
}
const src = fs.readFileSync(file, 'utf8')
console.log(`# bundle size: ${(src.length / 1024 / 1024).toFixed(2)} MB\n`)

const uniq = (arr) => [...new Set(arr)].sort()

// 1) 引号包裹的 URL path 字符串（以 / 开头，含业务关键词，排除纯静态资源噪音）
const pathRe = /["'`](\/[A-Za-z0-9_\-/.]{3,140})["'`]/g
const bizKw = /(api|openapi|gateway|wayline|waypoint|map|oss|sts|token|upload|media|login|user|auth|project|org|workspace|notify|notif|config|version|health|tile|terrain|elevation|dem|layer|cesium)/i
let m
const paths = new Set()
while ((m = pathRe.exec(src)) !== null) {
  const p = m[1]
  if (bizKw.test(p) && !/\.(js|css|png|svg|woff2?|ttf|jpg|gif|html|map)$/i.test(p)) {
    paths.add(p)
  }
}
console.log('## API / service paths (' + paths.size + ')')
uniq([...paths]).forEach((p) => console.log('  ' + p))

// 2) 模板字符串里的动态 path（`/xxx/${...}` 形式，取反引号开头部分）
const tplRe = /`\/[A-Za-z0-9_\-/.]{2,80}\$\{/g
const tpls = new Set()
while ((m = tplRe.exec(src)) !== null) {
  tpls.add(m[0].slice(1))
}
console.log('\n## dynamic path templates (' + tpls.size + ')')
uniq([...tpls]).forEach((p) => console.log('  ' + p))

// 3) wasm / worker / 大资源
for (const [label, re] of [
  ['wasm refs', /[A-Za-z0-9_\-/.]{2,80}\.wasm/g],
  ['worker refs', /[A-Za-z0-9_\-/.]{2,80}(?:Worker|worker)\.(?:js|ts)/g],
]) {
  const hits = uniq((src.match(re) || []))
  console.log(`\n## ${label} (${hits.length})`)
  hits.slice(0, 60).forEach((p) => console.log('  ' + p))
}

// 4) ws/wss 端点与 http 端点模板
for (const [label, re] of [
  ['ws endpoints', /["'`](?:wss?| WebSocket|ws):[^"'`]{4,140}["'`]/g],
  ['full urls (http)', /["'`]https?:\/\/[A-Za-z0-9_\-/.{}$]{4,140}["'`]/g],
]) {
  let hits = []
  try { hits = (src.match(re) || []).map((s) => s.slice(1, -1)) } catch (e) {}
  const u = uniq(hits).filter((s) => bizKw.test(s) || /^wss?:/.test(s))
  console.log(`\n## ${label} (${u.length})`)
  u.slice(0, 80).forEach((p) => console.log('  ' + p))
}

// 5) webpack publicPath / 运行时资源根路径线索
for (const [label, re] of [
  ['publicPath hints', /publicPath[^,;{}]{0,120}/g],
  ['data-fh2 hints', /data-fh2[^"'`]{0,80}/g],
]) {
  const hits = uniq((src.match(re) || [])).slice(0, 10)
  console.log(`\n## ${label} (${hits.length})`)
  hits.forEach((p) => console.log('  ' + p.slice(0, 120)))
}
