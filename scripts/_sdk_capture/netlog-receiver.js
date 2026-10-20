/** 本地流量接收器：POST /save 存 JSON 文件，GET /health 探活。用法：node netlog-receiver.js */
const http = require('http')
const fs = require('fs')
const path = require('path')

const OUT = path.join(__dirname, 'runtime-netlog-' + new Date().toISOString().replace(/[:.]/g, '-') + '.json')
let saved = 0

http
  .createServer((req, res) => {
    res.setHeader('Access-Control-Allow-Origin', '*')
    res.setHeader('Access-Control-Allow-Methods', 'POST, GET, OPTIONS')
    res.setHeader('Access-Control-Allow-Headers', '*')
    if (req.method === 'OPTIONS') {
      res.writeHead(204)
      return res.end()
    }
    if (req.method === 'GET' && req.url === '/health') {
      res.writeHead(200)
      return res.end('ok saved=' + saved)
    }
    if (req.method === 'POST' && req.url === '/save') {
      const chunks = []
      req.on('data', (c) => chunks.push(c))
      req.on('end', () => {
        const buf = Buffer.concat(chunks)
        fs.writeFileSync(OUT, buf)
        saved++
        console.log(`[netlog] saved #${saved}: ${(buf.length / 1024).toFixed(1)} KB -> ${path.basename(OUT)}`)
        res.writeHead(200)
        res.end('saved')
      })
      return
    }
    res.writeHead(404)
    res.end()
  })
  .listen(9971, '127.0.0.1', () => console.log('[netlog] receiver on http://127.0.0.1:9971, out ->', path.basename(OUT)))
