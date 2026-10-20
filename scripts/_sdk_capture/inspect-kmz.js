#!/usr/bin/env node
/**
 * 零依赖 KMZ 解析器（zip + deflate 用 node 内置 zlib）。
 * 解析 DJI 航线 KMZ：条目清单 + template.kml / waylines.wpml 摘要。
 * 用法：node inspect-kmz.js <kmz文件>   （缺省解析工作区内 sample-wayline.kmz）
 */
const fs = require('fs')
const path = require('path')
const zlib = require('zlib')

const src = process.argv[2] || path.join(__dirname, 'sample-wayline.kmz')
const buf = fs.readFileSync(src)
console.log(`# ${src}  (${(buf.length / 1024).toFixed(1)} KB)\n`)

// ---- 最小 zip reader ----
function findEocd(b) {
  for (let i = b.length - 22; i >= 0; i--) {
    if (b.readUInt32LE(i) === 0x06054b50) return i
  }
  throw new Error('not a zip (no EOCD)')
}
const eocd = findEocd(buf)
const entries = []
let p = buf.readUInt32LE(eocd + 16) // central dir offset
const count = buf.readUInt16LE(eocd + 10)
for (let n = 0; n < count; n++) {
  if (buf.readUInt32LE(p) !== 0x02014b50) break
  const method = buf.readUInt16LE(p + 10)
  const csize = buf.readUInt32LE(p + 20)
  const usize = buf.readUInt32LE(p + 24)
  const nameLen = buf.readUInt16LE(p + 28)
  const extraLen = buf.readUInt16LE(p + 30)
  const localOff = buf.readUInt32LE(p + 42)
  const name = buf.slice(p + 46, p + 46 + nameLen).toString('utf8')
  entries.push({ name, method, csize, usize, localOff })
  p += 46 + nameLen + extraLen
}
function readEntry(e) {
  const lp = e.localOff
  if (buf.readUInt32LE(lp) !== 0x04034b50) throw new Error('bad local header: ' + e.name)
  const nameLen = buf.readUInt16LE(lp + 26)
  const extraLen = buf.readUInt16LE(lp + 28)
  const start = lp + 30 + nameLen + extraLen
  const data = buf.slice(start, start + e.csize)
  if (e.method === 0) return data
  if (e.method === 8) return zlib.inflateRawSync(data)
  throw new Error('unsupported method ' + e.method)
}

console.log('## entries:')
for (const e of entries) {
  console.log(`  ${e.name}  method=${e.method} ${e.csize}B→${e.usize}B`)
}

// ---- 解析 wpmz 内容 ----
function get(...names) {
  for (const n of names) {
    const e = entries.find((x) => x.name.toLowerCase().endsWith(n.toLowerCase()) || x.name === n)
    if (e) return readEntry(e).toString('utf8')
  }
  return null
}
const kml = get('wpmz/template.kml')
const wpml = get('wpmz/waylines.wpml')
const res = get('wpmz/res')

console.log('\n## template.kml 摘要:')
if (kml) {
  const names = [...kml.matchAll(/<name>([^<]*)<\/name>/g)].map((m) => m[1])
  console.log(`  文档名: ${names[0] || '(无)'}`)
  const placemarks = (kml.match(/<Placemark>/g) || []).length
  console.log(`  Placemark(航点)数: ${placemarks}`)
  const coords = [...kml.matchAll(/<coordinates>([^<]+)<\/coordinates>/g)].map((m) => m[1].trim())
  console.log(`  坐标串 ${coords.length} 段，前 3 段:`)
  coords.slice(0, 3).forEach((c) => console.log(`    ${c}`))
  const execH = [...kml.matchAll(/<(?:wpml:)?ExecuteHeight>([\d.]+)<\/(?:wpml:)?ExecuteHeight>/g)].map((m) => +m[1])
  if (execH.length) console.log(`  航点高度: ${execH.slice(0, 8).join(', ')}${execH.length > 8 ? ' ...' : ''} (m)`)
  const sp = kml.match(/<(?:wpml:)?autoFlightSpeed>([\d.]+)<\/(?:wpml:)?autoFlightSpeed>/)
  if (sp) console.log(`  自动飞行速度: ${sp[1]} m/s`)
  const tpl = kml.match(/<(?:wpml:)?TemplateType>([^<]+)<\/(?:wpml:)?TemplateType>/)
  if (tpl) console.log(`  模板类型: ${tpl[1]}`)
  const drone = kml.match(/<(?:wpml:)?droneEnumValue>(\d+)<\/(?:wpml:)?droneEnumValue>/)
  if (drone) console.log(`  机型枚举值: ${drone[1]}`)
} else {
  console.log('  (未找到)')
}

console.log('\n## waylines.wpml 摘要:')
if (wpml) {
  const wps = (wpml.match(/<(?:wpml:)?Waypoint[\s>]/g) || []).length
  console.log(`  Waypoint 节点数: ${wps}`)
  const actionGroups = (wpml.match(/<(?:wpml:)?ActionGroup[\s>]/g) || []).length
  console.log(`  ActionGroup: ${actionGroups}`)
  const funcTypes = {}
  for (const m of wpml.matchAll(/<(?:wpml:)?actionActuatorFunc>([^<]+)<\/(?:wpml:)?actionActuatorFunc>/g)) {
    funcTypes[m[1]] = (funcTypes[m[1]] || 0) + 1
  }
  console.log(`  动作类型分布: ${JSON.stringify(funcTypes)}`)
  const payloads = [...new Set([...wpml.matchAll(/<(?:wpml:)?payloadInfo>[\s\S]{0,200}?<(?:wpml:)?payloadEnumValue>(\d+)<\/(?:wpml:)?payloadEnumValue>/g)].map((m) => m[1]))]
  if (payloads.length) console.log(`  相机负载枚举: ${payloads.join(', ')}`)
  const turn = [...new Set([...wpml.matchAll(/<(?:wpml:)?waypointTurnMode>([^<]+)<\/(?:wpml:)?waypointTurnMode>/g)].map((m) => m[1]))]
  if (turn.length) console.log(`  转弯模式: ${turn.join(', ')}`)
  const mission = wpml.match(/<(?:wpml:)?missionConfig>[\s\S]{0,900}/)
  if (mission) console.log(`  missionConfig: ${mission[0].replace(/\s+/g, ' ').slice(0, 500)}...`)
} else {
  console.log('  (未找到)')
}
if (res != null) console.log(`\n## res 资源目录存在（长度 ${(res.length / 1024).toFixed(1)} KB，快照等资源）`)
