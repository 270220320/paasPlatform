#!/usr/bin/env node
/** 从 76.141 网关现拉完整回放数据（GET 类），存 mock/replay/。用法：node tools/fetch-replay.js */
const fs = require('fs')
const path = require('path')

const GW = 'http://192.168.76.141:30812/gateway'
const OUT = path.join(__dirname, '..', 'mock', 'replay')
fs.mkdirSync(OUT, { recursive: true })

const TARGETS = [
  ['devices.json', '/device/api/v1/manager/devices'],
  ['map-configs.json', '/device/api/v1/manager/map-configs'],
  ['topologies.json', '/device/api/v1/manager/device/topologies/grouped?page_num=1&page_size=999'],
  ['pf-map-elements.json', '/pf-map/api/v1/elements/lists'],
  ['pf-terra-files.json', '/pf-terra/api/v1/models/files?file_all_types[]=9&file_all_types[]=8&get_sub_file=true'],
  ['wayline-details-sample.json', '/pf-wayline/api/v1/details?wayline_uuid=8b3adb84-10ed-41fc-9f8c-f7d8a4c17307'],
]

async function main() {
  for (const [file, p] of TARGETS) {
    const url = GW + p
    const res = await fetch(url, { headers: { auth_keys: 'user_id', user_id: 'paas_test', 'X-User-Token': '' } })
    const text = await res.text()
    fs.writeFileSync(path.join(OUT, file), text)
    let code = '?'
    try { code = JSON.parse(text).code } catch (e) { /* keep */ }
    console.log(`  [${res.status} code=${code}] ${file}  ${(text.length / 1024).toFixed(1)}KB  ${p.slice(0, 60)}`)
  }
}
main().catch((e) => { console.error(e); process.exit(1) })
