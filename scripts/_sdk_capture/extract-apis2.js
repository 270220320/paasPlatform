#!/usr/bin/env node
/**
 * 第二轮：提取 API 前缀后的子路径段（拼接段），还原图谱。
 * 用法：node extract-apis2.js <bundle.js>
 */
const fs = require('fs')
const src = fs.readFileSync(process.argv[2], 'utf8')
const uniq = (a) => [...new Set(a)].sort()
let m

// 引号内的相对路径段（第二段拼接件）：以 / 开头、含业务名词、非静态资源
const segRe = /["'`](\/[A-Za-z0-9_\-/{}$.]{3,120})["'`]/g
const segKw = /(workspaces?|waylines?|waypoints?|elements?|models?|files?|folders?|upload|sts|token|config|tile|terrain|elevation|auth|current|detail|create|update|delete|list|page|version|info|status|export|import|bind|unbind)/i
const segs = new Set()
while ((m = segRe.exec(src)) !== null) {
  const p = m[1]
  if (segKw.test(p) && !/\.(js|css|png|svg|woff2?|ttf|jpg|gif|html|map|kml|kmz|xml|json)$/i.test(p)) segs.add(p)
}
console.log('## path segments (' + segs.size + ')')
uniq([...segs]).forEach((p) => console.log('  ' + p))

// url: "/xxx" / url:`/xxx` 形式（axios/fetch 封装常见）
const urlRe = /url\s*:\s*["'`]([^"'`]{3,140})["'`]/g
const urls = new Set()
while ((m = urlRe.exec(src)) !== null) urls.add(m[1])
console.log('\n## url: literals (' + urls.size + ')')
uniq([...urls]).forEach((p) => console.log('  ' + p))

// concat 拼接链："/pf-wayline/api/v1".concat("/xxx") / 前缀+变量
const catRe = /["'`](\/(?:pf-|manage|device|geo_|live|model|reconstruction)[A-Za-z0-9_\-/.]{2,80})["'`]\s*(?:\+|\.concat\()/g
const cats = new Set()
while ((m = catRe.exec(src)) !== null) cats.add(m[1])
console.log('\n## concat prefixes (' + cats.size + ')')
uniq([...cats]).forEach((p) => console.log('  ' + p))

// axios 方法调用线索：get("/x") post("/x")
for (const verb of ['get', 'post', 'put', 'delete']) {
  const re = new RegExp('\\.(?:' + verb + '|\\$' + verb + ')\\s*\\(\\s*["\'`](/[^"\'`]{3,120})["\'`]', 'g')
  const hits = new Set()
  while ((m = re.exec(src)) !== null) hits.add(m[1])
  if (hits.size) {
    console.log(`\n## .${verb}() literals (${hits.size})`)
    uniq([...hits]).forEach((p) => console.log('  ' + p))
  }
}
