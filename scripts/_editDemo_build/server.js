#!/usr/bin/env node
/**
 * editDemo 一体化服务器（零依赖，node 18+）：
 *   1. mirror/ 静态资源（司空 paas.js 全家桶本地镜像）
 *   2. /paas-api/gateway/*  mock 网关（回放 + 本地航线库 + 假 STS）
 *   3. /file-storage-privatization/*  S3 分片上传 mock（接住 KMZ 落盘）
 *   4. /tiles_tianjin/*  本地 XYZ 瓦片
 *   5. /paas-ws/*  极简 WebSocket（心跳应答）
 *   6. /api/library、/kmz-download/:uuid  DEMO 自身接口
 * 用法：node server.js   （默认 127.0.0.1:8090）
 */
const http = require('http')
const fs = require('fs')
const path = require('path')
const crypto = require('crypto')

const ROOT = __dirname
const MIRROR = path.join(ROOT, 'mirror')
const REPLAY = path.join(ROOT, 'mock', 'replay')
const STORE = path.join(ROOT, 'mock', 'store')
const KMZ_DIR = path.join(STORE, 'kmz')
const PARTS_DIR = path.join(STORE, 'parts')
const LIB_FILE = path.join(STORE, 'library.json')
const TILES_DIR = path.join(ROOT, 'tiles_tianjin')
const PORT = Number(process.env.PORT || 8090)
const HOST = process.env.HOST || '127.0.0.1'

for (const d of [KMZ_DIR, PARTS_DIR]) fs.mkdirSync(d, { recursive: true })
if (!fs.existsSync(LIB_FILE)) fs.writeFileSync(LIB_FILE, JSON.stringify({ list: [] }, null, 2))

const MIME = {
  '.js': 'application/javascript', '.mjs': 'application/javascript', '.css': 'text/css',
  '.html': 'text/html; charset=utf-8', '.json': 'application/json', '.map': 'application/json',
  '.png': 'image/png', '.jpg': 'image/jpeg', '.jpeg': 'image/jpeg', '.gif': 'image/gif',
  '.svg': 'image/svg+xml', '.ico': 'image/x-icon', '.woff': 'font/woff', '.woff2': 'font/woff2',
  '.ttf': 'font/ttf', '.wasm': 'application/wasm', '.glb': 'model/gltf-binary',
  '.gltf': 'model/gltf+json', '.bin': 'application/octet-stream', '.cur': 'image/x-icon',
  '.kml': 'application/vnd.google-earth.kml+xml', '.kmz': 'application/vnd.google-earth.kmz',
}

// ---------- 工具 ----------
const uuid = () => crypto.randomUUID()
function readLib() {
  try { return JSON.parse(fs.readFileSync(LIB_FILE, 'utf8')) } catch { return { list: [] } }
}
function writeLib(lib) { fs.writeFileSync(LIB_FILE, JSON.stringify(lib, null, 2)) }
function replay(name) {
  try { return fs.readFileSync(path.join(REPLAY, name), 'utf8') } catch { return '{"code":0,"data":{}}' }
}
function sendJson(res, obj, status = 200) {
  const body = typeof obj === 'string' ? obj : JSON.stringify(obj)
  res.writeHead(status, { 'Content-Type': 'application/json', 'Access-Control-Allow-Origin': '*' })
  res.end(body)
}
function sendXml(res, xml, status = 200) {
  res.writeHead(status, { 'Content-Type': 'application/xml' })
  res.end(xml)
}
function readBody(req) {
  return new Promise((resolve) => {
    const chunks = []
    req.on('data', (c) => chunks.push(c))
    req.on('end', () => resolve(Buffer.concat(chunks)))
  })
}
function safeJoin(base, rel) {
  const p = path.normalize(path.join(base, rel))
  return p.startsWith(base) ? p : null
}

// ---------- mock 网关 ----------
async function handleGateway(req, res, url) {
  const p = url.pathname.replace(/^\/paas-api\/gateway/, '')
  const q = url.searchParams
  const log = (tag) => console.log(`  [gw] ${req.method} ${p} ${tag || ''}`)
  const ok0 = (data, msg) => ({ code: 0, data, message: msg || 'success', request_id: uuid() })

  // 地图配置：瓦片地址改指本地（保持绝对 URL 结构，动态 host）
  if (req.method === 'GET' && p === '/device/api/v1/manager/map-configs') {
    log('-> local tiles')
    const cfg = JSON.parse(replay('map-configs.json'))
    cfg.data.map_config_list = [{
      name: 'tj-local', type: 'custom',
      link: `http://${req.headers.host}/tiles_tianjin/{z}/{x}/{y}.jpg`,
      street_link: '', min_z: 8, max_z: 16,
    }]
    return sendJson(res, cfg)
  }
  if (req.method === 'GET' && p.startsWith('/device/api/v1/manager/device/topologies')) {
    log('-> replay topologies'); return sendJson(res, replay('topologies.json'))
  }
  if (req.method === 'GET' && p === '/device/api/v1/manager/devices') {
    log('-> replay devices'); return sendJson(res, replay('devices.json'))
  }
  if (req.method === 'GET' && p.startsWith('/pf-map/api/v1/elements')) {
    log('-> replay elements'); return sendJson(res, replay('pf-map-elements.json'))
  }
  if (req.method === 'GET' && p.startsWith('/pf-map/api/v1/element-icons')) {
    log('-> empty icons'); return sendJson(res, ok0({ data: [] }))
  }
  if (req.method === 'GET' && p.startsWith('/pf-terra/api/v1/models/files')) {
    log('-> replay terra files'); return sendJson(res, replay('pf-terra-files.json'))
  }
  if (req.method === 'POST' && p === '/pf-terra/api/v1/models/elevation-files-list') {
    log('-> empty elevation list'); return sendJson(res, ok0({ model_list: [], elevation_uuid: '' }))
  }
  if (req.method === 'GET' && p.startsWith('/dem/api/v2/terrain-tiles')) {
    // 无高程数据：与真实私有化一致（编辑器仅弹提示，不阻塞）
    log('-> no terrain (235404)')
    return sendJson(res, { code: 235404, message: 'layer json not exist' })
  }
  if (req.method === 'GET' && p.startsWith('/geo_resource/')) {
    log('-> empty suggestion'); return sendJson(res, ok0({ suggestions: [] }))
  }
  if (req.method === 'GET' && p.startsWith('/live/')) {
    log('-> empty live models'); return sendJson(res, ok0({ list: [] }))
  }
  if (req.method === 'GET' && p.startsWith('/pf-wayline/editor/v1/action-media/list')) {
    log('-> empty action-media'); return sendJson(res, ok0({}))
  }
  if (req.method === 'POST' && p === '/pf-wayline/api/v1/model-list') {
    log('-> empty model-list'); return sendJson(res, ok0({ model_list: [] }))
  }
  if (req.method === 'POST' && p === '/pf-wayline/editor/v1/image-crop') {
    log('-> empty image-crop'); return sendJson(res, ok0({}))
  }

  // 假 STS：endpoint 指回本服务 → SDK 的 S3 上传/下载全部落在本地
  if (req.method === 'POST' && p === '/pf-wayline/editor/v1/sts') {
    const body = JSON.parse((await readBody(req)).toString('utf8') || '{}')
    log(`-> mock sts prefix=${body.object_key_prefix || ''}`)
    return sendJson(res, ok0({
      bucket: 'file-storage-privatization',
      endpoint: `http://${req.headers.host}`,
      region: 'us-east-1',
      provider: 'minio',
      object_key_prefix: body.object_key_prefix || '',
      credentials: {
        access_key_id: 'MOCKAKIDEXAMPLE',
        access_key_secret: 'mocksecret',
        security_token: 'mocktoken',
        expire: body.expire || 900,
        platform: 1,
      },
    }))
  }

  // 保存注册：落元数据到本地航线库（KMZ 已由 S3 mock 接住）
  if (req.method === 'POST' && p === '/pf-wayline/editor/v1/upload') {
    const body = JSON.parse((await readBody(req)).toString('utf8') || '{}')
    const lib = readLib()
    let item = body.wayline_uuid ? lib.list.find((x) => x.wayline_uuid === body.wayline_uuid) : null
    if (!item) {
      item = { wayline_uuid: uuid() }
      lib.list.unshift(item)
    }
    Object.assign(item, {
      kmz_object_key: body.kmz_object_key,
      wayline_name: body.wayline_name,
      updated_at: Math.floor(Date.now() / 1000),
      kmz_size: fs.existsSync(path.join(KMZ_DIR, body.kmz_object_key))
        ? fs.statSync(path.join(KMZ_DIR, body.kmz_object_key)).size : 0,
    })
    writeLib(lib)
    console.log(`  [gw] SAVE ✅ name=${item.wayline_name} uuid=${item.wayline_uuid} key=${item.kmz_object_key} (${item.kmz_size}B)`)
    return sendJson(res, ok0({
      ext_data: {},
      kmz_data: { survey_area_scope: {} },
      kmz_object_key: body.kmz_object_key,
      wayline_name: body.wayline_name,
      wayline_uuid: item.wayline_uuid,
    }))
  }

  // 航线详情：本地库元数据 + 本地 KMZ 下载 URL
  if (req.method === 'GET' && p === '/pf-wayline/api/v1/details') {
    const wid = q.get('wayline_uuid')
    const item = readLib().list.find((x) => x.wayline_uuid === wid)
    if (!item) { log(`details miss ${wid}`); return sendJson(res, { code: 40004, message: 'wayline not found' }) }
    log(`-> local details ${item.wayline_name}`)
    const dl = `http://${req.headers.host}/file-storage-privatization/${item.kmz_object_key}`
    return sendJson(res, ok0({
      ext_data: {},
      kmz_data: { drone_type: '0-100-1', is_wayline_validity_check: true, payload_types: ['1-53-2'], survey_area_scope: {} },
      kmz_object_key: item.kmz_object_key,
      kmz_without_res_url: dl,
      md5: '',
      suffix: '.kmz',
      updated_at: item.updated_at,
      url: dl,
      wayline_name: item.wayline_name,
      wayline_point_nums: 0,
      wayline_uuid: item.wayline_uuid,
    }))
  }

  console.log(`  [gw][UNHANDLED] ${req.method} ${p}${url.search}`)
  return sendJson(res, ok0({}, 'mocked-empty'))
}

// ---------- S3 分片上传 mock ----------
async function handleS3(req, res, url) {
  const key = decodeURIComponent(url.pathname.replace(/^\/file-storage-privatization\/?/, ''))
  const q = url.searchParams
  const dest = safeJoin(KMZ_DIR, key)
  if (!dest) { res.writeHead(400); return res.end('bad key') }

  // 初始化分片
  if (req.method === 'POST' && q.has('uploads')) {
    const uploadId = crypto.randomBytes(24).toString('hex')
    fs.mkdirSync(path.join(PARTS_DIR, uploadId), { recursive: true })
    fs.writeFileSync(path.join(PARTS_DIR, uploadId, '.key'), key)
    console.log(`  [s3] init multipart ${key} uploadId=${uploadId.slice(0, 12)}...`)
    return sendXml(res, `<?xml version="1.0" encoding="UTF-8"?><InitiateMultipartUploadResult><Bucket>file-storage-privatization</Bucket><Key>${key}</Key><UploadId>${uploadId}</UploadId></InitiateMultipartUploadResult>`)
  }
  // 上传分片：body 即 KMZ 分片数据（单分片=完整 KMZ）
  if (req.method === 'PUT' && q.has('partNumber') && q.has('uploadId')) {
    const body = await readBody(req)
    const dir = path.join(PARTS_DIR, q.get('uploadId'))
    fs.mkdirSync(dir, { recursive: true })
    fs.writeFileSync(path.join(dir, q.get('partNumber')), body)
    const etag = crypto.createHash('md5').update(body).digest('hex')
    console.log(`  [s3] put part#${q.get('partNumber')} ${body.length}B -> ${key}`)
    res.writeHead(200, { ETag: `"${etag}"` })
    return res.end()
  }
  // 合并分片
  if (req.method === 'POST' && q.has('uploadId')) {
    await readBody(req)
    const uploadId = q.get('uploadId')
    const dir = path.join(PARTS_DIR, uploadId)
    const key2 = fs.existsSync(path.join(dir, '.key')) ? fs.readFileSync(path.join(dir, '.key'), 'utf8') : key
    const parts = fs.readdirSync(dir).filter((f) => /^\d+$/.test(f)).sort((a, b) => Number(a) - Number(b))
    const merged = Buffer.concat(parts.map((n) => fs.readFileSync(path.join(dir, n))))
    fs.mkdirSync(path.dirname(safeJoin(KMZ_DIR, key2)), { recursive: true })
    fs.writeFileSync(safeJoin(KMZ_DIR, key2), merged)
    fs.rmSync(dir, { recursive: true, force: true })
    const etag = crypto.createHash('md5').update(merged).digest('hex')
    console.log(`  [s3] complete ✅ ${key2} = ${merged.length}B (md5=${etag})`)
    return sendXml(res, `<?xml version="1.0" encoding="UTF-8"?><CompleteMultipartUploadResult><Location>file-storage-privatization/${key2}</Location><Bucket>file-storage-privatization</Bucket><Key>${key2}</Key><ETag>"${etag}"</ETag></CompleteMultipartUploadResult>`)
  }
  // 下载（预签名 GetObject 或直接）
  if (req.method === 'GET' || req.method === 'HEAD') {
    const f = safeJoin(KMZ_DIR, key)
    if (!fs.existsSync(f)) { res.writeHead(404); return res.end('not found') }
    const data = fs.readFileSync(f)
    res.writeHead(req.method === 'HEAD' ? 200 : 200, {
      'Content-Type': 'application/octet-stream',
      'Content-Length': data.length,
      'Access-Control-Allow-Origin': '*',
    })
    return req.method === 'HEAD' ? res.end() : res.end(data)
  }
  if (req.method === 'DELETE') { res.writeHead(204); return res.end() }
  res.writeHead(400); res.end('unsupported')
}

// ---------- 极简 WebSocket（心跳/订阅应答） ----------
const WS_GUID = '258EAFA5-E914-47DA-95CA-C5AB0DC85B11'
function wsFrame(opcode, payload) {
  const len = payload.length
  let header
  if (len < 126) { header = Buffer.from([0x80 | opcode, len]) }
  else if (len < 65536) { header = Buffer.alloc(4); header[0] = 0x80 | opcode; header[1] = 126; header.writeUInt16BE(len, 2) }
  else { header = Buffer.alloc(10); header[0] = 0x80 | opcode; header[1] = 127; header.writeBigUInt64BE(BigInt(len), 2) }
  return Buffer.concat([header, payload])
}
function wsParse(buf) {
  const frames = []
  let i = 0
  while (i + 2 <= buf.length) {
    const fin = buf[i] & 0x80, opcode = buf[i] & 0x0f
    const masked = buf[i + 1] & 0x80
    let len = buf[i + 1] & 0x7f, off = i + 2
    if (len === 126) { len = buf.readUInt16BE(off); off += 2 }
    else if (len === 127) { len = Number(buf.readBigUInt64BE(off)); off += 8 }
    let mask = null
    if (masked) { mask = buf.slice(off, off + 4); off += 4 }
    if (off + len > buf.length) break
    const payload = Buffer.from(buf.slice(off, off + len))
    if (mask) for (let k = 0; k < payload.length; k++) payload[k] ^= mask[k % 4]
    frames.push({ opcode, payload })
    i = off + len
  }
  return frames
}
function handleUpgrade(req, socket) {
  const key = req.headers['sec-websocket-key']
  if (!key) { socket.destroy(); return }
  const accept = crypto.createHash('sha1').update(key + WS_GUID).digest('base64')
  // SDK 携带子协议（["v1.notify","auth.<b64>"]）发起连接；RFC6455 要求服务端从客户端
  // 列表中选一个回显，否则浏览器立即以 1000 关闭连接，SDK 报 4002 无限重连
  const proto = String(req.headers['sec-websocket-protocol'] || '').split(',')[0].trim()
  socket.write('HTTP/1.1 101 Switching Protocols\r\nUpgrade: websocket\r\nConnection: Upgrade\r\n' +
    `Sec-WebSocket-Accept: ${accept}\r\n` +
    (proto ? `Sec-WebSocket-Protocol: ${proto}\r\n` : '') + '\r\n')
  console.log(`  [ws] connected ${req.url}`)
  let buf = Buffer.alloc(0)
  socket.on('data', (chunk) => {
    buf = Buffer.concat([buf, chunk])
    for (const f of wsParse(buf)) {
      if (f.opcode === 8) { socket.end(); return }
      if (f.opcode === 9) { socket.write(wsFrame(10, f.payload)); continue }
      if (f.opcode === 1 || f.opcode === 2) {
        try {
          const msg = JSON.parse(f.payload.toString('utf8'))
          if (msg.message_type === 'ping') {
            socket.write(wsFrame(1, Buffer.from(JSON.stringify({ message_type: 'pong', message_id: msg.message_id || '', message_data: null, timestamp: Math.floor(Date.now() / 1000) }))))
          } else {
            console.log(`  [ws] ${msg.message_type || '?'} (ignored)`)
          }
        } catch { /* 非 JSON 忽略 */ }
      }
    }
    buf = Buffer.alloc(0)
  })
  socket.on('error', () => {})
}

// ---------- 主服务 ----------
const server = http.createServer(async (req, res) => {
  const url = new URL(req.url, `http://${req.headers.host || 'localhost'}`)
  const p = decodeURIComponent(url.pathname)
  try {
    // CORS 预检
    if (req.method === 'OPTIONS') {
      res.writeHead(204, {
        'Access-Control-Allow-Origin': '*', 'Access-Control-Allow-Methods': 'GET,PUT,POST,DELETE,HEAD,OPTIONS',
        'Access-Control-Allow-Headers': '*', 'Access-Control-Expose-Headers': 'ETag',
      })
      return res.end()
    }
    // DEMO 首页
    if (p === '/' || p === '/index.html') {
      res.writeHead(200, { 'Content-Type': 'text/html; charset=utf-8' })
      return res.end(fs.readFileSync(path.join(ROOT, 'index.html')))
    }
    if (p === '/app.js') {
      res.writeHead(200, { 'Content-Type': 'application/javascript' })
      return res.end(fs.readFileSync(path.join(ROOT, 'app.js')))
    }
    // DEMO 航线库 API
    if (p === '/api/library') return sendJson(res, readLib())
    if (p.startsWith('/kmz-download/')) {
      const wid = p.split('/')[2]
      const item = readLib().list.find((x) => x.wayline_uuid === wid)
      if (!item) { res.writeHead(404); return res.end('not found') }
      const f = safeJoin(KMZ_DIR, item.kmz_object_key)
      if (!f || !fs.existsSync(f)) { res.writeHead(404); return res.end('kmz missing') }
      const data = fs.readFileSync(f)
      res.writeHead(200, {
        'Content-Type': 'application/vnd.google-earth.kmz',
        'Content-Disposition': `attachment; filename="${encodeURIComponent(item.wayline_name || 'wayline')}.kmz"`,
        'Content-Length': data.length,
      })
      return res.end(data)
    }
    // mock 网关
    if (p.startsWith('/paas-api/gateway/')) return handleGateway(req, res, url)
    // S3 存储
    if (p.startsWith('/file-storage-privatization/')) return handleS3(req, res, url)
    // 本地瓦片
    if (p.startsWith('/tiles_tianjin/')) {
      const f = safeJoin(TILES_DIR, p.replace(/^\/tiles_tianjin\//, ''))
      if (!f || !fs.existsSync(f)) { res.writeHead(404); return res.end() }
      const data = fs.readFileSync(f)
      res.writeHead(200, { 'Content-Type': '.jpg', 'Content-Length': data.length, 'Cache-Control': 'public, max-age=86400' })
      return res.end(data)
    }
    // 镜像静态资源（paas.js 全家桶）
    const f = safeJoin(MIRROR, p)
    if (f && fs.existsSync(f) && fs.statSync(f).isFile()) {
      const ext = path.extname(f).toLowerCase()
      const data = fs.readFileSync(f)
      res.writeHead(200, {
        'Content-Type': MIME[ext] || 'application/octet-stream',
        'Content-Length': data.length,
        'Cache-Control': 'public, max-age=3600',
      })
      return res.end(data)
    }
    console.log(`  [static][404] ${p}`)
    res.writeHead(404, { 'Content-Type': 'text/plain' })
    res.end('404: ' + p)
  } catch (e) {
    console.error(`[err] ${req.method} ${p}:`, e.message)
    res.writeHead(500); res.end('server error: ' + e.message)
  }
})
server.on('upgrade', handleUpgrade)
server.listen(PORT, HOST, () => {
  console.log(`editDemo server -> http://${HOST}:${PORT}`)
  console.log(`  mirror: ${MIRROR}`)
  console.log(`  store : ${STORE}`)
})
