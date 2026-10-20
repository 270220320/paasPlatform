#!/usr/bin/env node
/** 第四轮：dump 全部 `${前缀变量}/子路径` 模板调用点。用法：node extract-apis4.js <bundle.js> */
const fs = require('fs')
const src = fs.readFileSync(process.argv[2], 'utf8')
// 匹配任意 ${var}/path 模板（var 为压缩后的短标识符），过滤出业务 path
const re = /\$\{[A-Za-z_$][A-Za-z0-9_$]{0,5}\}(\/[A-Za-z0-9_\-/{}.]{2,80})/g
let m
const hits = new Map()
while ((m = re.exec(src)) !== null) {
  const full = m[0]
  const path = m[1]
  if (!/\.(js|css|png|svg|woff|jpg|html)$/i.test(path)) {
    if (!hits.has(full)) hits.set(full, 0)
    hits.set(full, hits.get(full) + 1)
  }
}
;[...hits.keys()].sort().forEach((k) => console.log(String(hits.get(k)).padStart(3), k))
console.log('---total', hits.size)
