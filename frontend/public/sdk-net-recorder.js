/**
 * SDK 网络流量记录器（临时抓包工具，跑完即撤）。
 * patch fetch / XHR / WebSocket，记录到 window.__NET_LOG；
 * window.__NETLOG_FLUSH() 把日志 POST 到本地接收器 http://127.0.0.1:9971/save。
 * 必须在所有业务脚本之前加载（index.html 中置于 main.ts 之前）。
 */
(function () {
  'use strict'
  var LOG = []
  var MAX = 3000
  var RECEIVER = 'http://127.0.0.1:9971'
  var origFetch = window.fetch.bind(window)

  function isAsset(url) {
    return /\.(js|css|png|jpe?g|svg|gif|woff2?|ttf|ico|map)(\?|$)/i.test(url)
  }
  function trunc(s, n) {
    if (s == null) return null
    s = String(s)
    return s.length > n ? s.slice(0, n) + '...<truncated ' + s.length + '>' : s
  }
  function bodyInfo(body) {
    if (body == null) return null
    if (typeof body === 'string') return trunc(body, 16384)
    if (typeof FormData !== 'undefined' && body instanceof FormData) {
      var parts = []
      body.forEach(function (v, k) {
        parts.push(k + ': ' + (v && v.name ? v.name + '(' + (v.size || '?') + 'B)' : trunc(String(v), 256)))
      })
      return { type: 'FormData', fields: parts }
    }
    if (typeof Blob !== 'undefined' && body instanceof Blob) {
      return { type: 'Blob', size: body.size, mime: body.type }
    }
    if (body instanceof ArrayBuffer) return { type: 'ArrayBuffer', size: body.byteLength }
    if (typeof URLSearchParams !== 'undefined' && body instanceof URLSearchParams) {
      return trunc(body.toString(), 4096)
    }
    try { return trunc(JSON.stringify(body), 16384) } catch (e) { return String(body) }
  }

  // ---- fetch ----
  window.fetch = function (input, init) {
    var url = typeof input === 'string' ? input : (input && input.url) || String(input)
    var method = ((init && init.method) || (input && input.method) || 'GET').toUpperCase()
    if (isAsset(url)) return origFetch(input, init)
    var rec = { t: Date.now(), kind: 'fetch', url: url, method: method, req: bodyInfo(init && init.body) }
    LOG.push(rec)
    if (LOG.length > MAX) LOG.shift()
    return origFetch(input, init).then(function (resp) {
      rec.status = resp.status
      try {
        var ct = resp.headers.get('content-type') || ''
        if (/json|text|xml|kml/i.test(ct)) {
          resp.clone().text().then(function (tx) { rec.resp = trunc(tx, 32768) }).catch(function () {})
        } else {
          rec.respType = ct || '(empty)'
        }
      } catch (e) { /* ignore */ }
      return resp
    })
  }

  // ---- XMLHttpRequest（axios 走这条）----
  var origOpen = XMLHttpRequest.prototype.open
  var origSend = XMLHttpRequest.prototype.send
  XMLHttpRequest.prototype.open = function (method, url) {
    this.__rec = { t: Date.now(), kind: 'xhr', method: String(method), url: String(url) }
    return origOpen.apply(this, arguments)
  }
  XMLHttpRequest.prototype.send = function (body) {
    var xhr = this
    var rec = xhr.__rec
    if (!rec || isAsset(rec.url)) return origSend.apply(this, arguments)
    rec.req = bodyInfo(body)
    LOG.push(rec)
    if (LOG.length > MAX) LOG.shift()
    xhr.addEventListener('loadend', function () {
      rec.status = xhr.status
      try {
        var rt = xhr.responseType
        if (rt === '' || rt === 'text') rec.resp = trunc(xhr.responseText, 32768)
        else if (rt === 'json') rec.resp = trunc(JSON.stringify(xhr.response), 32768)
        else rec.respType = rt + ' size~' + (xhr.response && xhr.response.size ? xhr.response.size : '?')
      } catch (e) { /* ignore */ }
    })
    return origSend.apply(this, arguments)
  }

  // ---- WebSocket ----
  var OrigWS = window.WebSocket
  function PatchedWS(url, protocols) {
    var ws = protocols !== undefined ? new OrigWS(url, protocols) : new OrigWS(url)
    var wsUrl = String(url)
    LOG.push({ t: Date.now(), kind: 'ws-open', url: wsUrl })
    ws.addEventListener('message', function (ev) {
      var d = typeof ev.data === 'string' ? ev.data : '[binary ' + ((ev.data && ev.data.size) || '?') + 'B]'
      LOG.push({ t: Date.now(), kind: 'ws-msg', url: wsUrl, dir: 'in', data: trunc(d, 8192) })
    })
    ws.addEventListener('close', function (ev) {
      LOG.push({ t: Date.now(), kind: 'ws-close', url: wsUrl, code: ev.code, reason: trunc(ev.reason, 256) })
    })
    var origWsSend = ws.send.bind(ws)
    ws.send = function (data) {
      var d = typeof data === 'string' ? data : '[binary ' + ((data && data.size) || '?') + 'B]'
      LOG.push({ t: Date.now(), kind: 'ws-msg', url: wsUrl, dir: 'out', data: trunc(d, 8192) })
      return origWsSend(data)
    }
    return ws
  }
  PatchedWS.prototype = OrigWS.prototype
  var keys = Object.getOwnPropertyNames(OrigWS)
  for (var i = 0; i < keys.length; i++) {
    try {
      var k = keys[i]
      if (!(k in PatchedWS)) PatchedWS[k] = OrigWS[k]
    } catch (e) { /* ignore */ }
  }
  window.WebSocket = PatchedWS

  // ---- 导出 ----
  window.__NETLOG_COUNT = function () { return LOG.length }
  window.__NETLOG_FLUSH = function () {
    var payload = JSON.stringify({ capturedAt: new Date().toISOString(), href: location.href, count: LOG.length, log: LOG })
    return origFetch(RECEIVER + '/save', { method: 'POST', headers: { 'Content-Type': 'text/plain' }, body: payload })
      .then(function (r) { return 'flushed ' + LOG.length + ' entries, status ' + r.status })
      .catch(function (e) { return 'flush failed: ' + e })
  }
  console.log('[net-recorder] active, __NETLOG_COUNT()=' + LOG.length + ', __NETLOG_FLUSH() to export')
})()
