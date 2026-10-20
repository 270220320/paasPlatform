#!/usr/bin/env node
/**
 * 第三轮：关键前缀的上下文窗口 dump，还原拼接的子路径与调用形态。
 * 用法：node extract-apis3.js <bundle.js>
 */
const fs = require('fs')
const src = fs.readFileSync(process.argv[2], 'utf8')

const anchors = [
  '/pf-wayline/editor/v1',
  '/pf-wayline/api/v1',
  'map-configs',
  'terrain-tiles',
  'elevation-files-list',
  'topologies/grouped',
]

for (const a of anchors) {
  console.log(`\n===== anchor: ${a} =====`)
  let idx = 0
  let n = 0
  while (n < 8) {
    const i = src.indexOf(a, idx)
    if (i < 0) break
    idx = i + a.length
    // 取窗口，压缩到可读：去掉超长 base64/无意义段
    let w = src.slice(Math.max(0, i - 120), i + 220).replace(/\s+/g, ' ')
    w = w.replace(/[A-Za-z0-9+/=]{80,}/g, '<b64>')
    console.log(`  [@${i}] ...${w}...`)
    n++
  }
  if (n === 0) console.log('  (not found)')
}

// 统计 paas-core 特征
console.log('\n===== paas-core string features =====')
for (const kw of ['pf-wayline', 'pf-map', 'pf-terra', 'gateway', 'api/v1', 'openapi', 'notification', 'websocket', 'WebSocket', 'tile', 'tiles', 'cesium', 'Cesium', 'loadMap', 'initConfig']) {
  let c = 0
  let j = -1
  while ((j = src.indexOf(kw, j + 1)) >= 0) c++
  console.log(`  ${kw}: ${c}`)
}
