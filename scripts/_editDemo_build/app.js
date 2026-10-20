/**
 * editDemo 宿主逻辑：
 *   1. 等 window.FH2（paas.js 在静态页 DOMContentLoaded 自然拉起 paas-core）
 *   2. 加载 wayline-edit-sdk -> new WaylineEditor({hostUrl, api, ws, locale})
 *   3. mount -> loadMap -> 新建（editor.create）/ 编辑（render('edit')）
 *   4. wayline-save 事件后刷新本地航线库（server mock 落盘）
 *
 * 全部依赖指向本机 server.js：镜像静态资源 + mock 网关 + S3 分片 mock + WS 心跳。
 */
(function () {
  'use strict'

  var $ = function (id) { return document.getElementById(id) }
  var statusEl = $('status')
  var btnNew = $('btn-new')
  var libEl = $('library')

  /** 新建航线默认地图中心（天津演示区，与 AiFlow 一致） */
  var CENTER = { latitude: 39.06567939267821, longitude: 117.2186069043915, height: 1500 }

  var editor = null
  var currentId = ''
  var busy = false
  var mapLoaded = false
  var lastResizeKey = ''

  function setStatus(t) { statusEl.textContent = t; console.log('[demo]', t) }

  function waitUntil(pred, timeoutMs, intervalMs) {
    timeoutMs = timeoutMs || 20000
    intervalMs = intervalMs || 50
    if (pred()) return Promise.resolve(true)
    return new Promise(function (resolve) {
      var start = Date.now()
      var timer = setInterval(function () {
        if (pred()) { clearInterval(timer); resolve(true); return }
        if (Date.now() - start >= timeoutMs) { clearInterval(timer); resolve(false) }
      }, intervalMs)
    })
  }

  function loadScript(src) {
    return new Promise(function (resolve, reject) {
      var s = document.createElement('script')
      s.src = src
      s.onload = resolve
      s.onerror = function () { reject(new Error('script load failed: ' + src)) }
      document.head.appendChild(s)
    })
  }

  // ---------- 地图视野与 resize 桥接（对齐 AiFlow WaylineEditorPane） ----------
  function fh2Bridge() { return window.FH2 || null }

  /** 经纬度 -> Cartesian3（优先官方 Cesium，退化时用球面近似，仅相机定位用） */
  function toCartesian(lon, lat, h) {
    var C = window.Cesium
    if (C && C.Cartesian3 && C.Cartesian3.fromDegrees) return C.Cartesian3.fromDegrees(lon, lat, h)
    var rad = Math.PI / 180
    var lonR = lon * rad
    var latR = lat * rad
    var R = 6378137 + h
    return {
      x: R * Math.cos(latR) * Math.cos(lonR),
      y: R * Math.cos(latR) * Math.sin(lonR),
      z: R * Math.sin(latR),
    }
  }

  /** 部分版本 FH2 无 mapAppState：直接驱动 cesiumViewer 相机兜底 */
  function flyCesiumCameras(pos) {
    var fh2 = fh2Bridge()
    var viewers = fh2 && fh2.cesiumViewer
    if (!viewers || typeof viewers !== 'object') return
    var C = window.Cesium
    var dest = toCartesian(pos.longitude, pos.latitude, pos.height)
    Object.keys(viewers).forEach(function (k) {
      var v = viewers[k]
      if (!v || !v.camera || !v.camera.flyTo) return
      try {
        // SDK 地图应用可能用 lookAt 锁定坐标系，先解锁再定位
        if (v.camera.lookAtTransform && C && C.Matrix4 && C.Matrix4.IDENTITY) {
          v.camera.lookAtTransform(C.Matrix4.IDENTITY)
        }
        v.camera.flyTo({ destination: dest, duration: 0 })
      } catch (e) { /* ignore */ }
    })
  }

  function flyMapToCenter() {
    var fh2 = fh2Bridge()
    var mapAppState = fh2 && fh2.mapAppState
    var pos = { longitude: CENTER.longitude, latitude: CENTER.latitude, height: CENTER.height }
    if (!mapAppState || !mapAppState.getMapAppUntilReady) {
      flyCesiumCameras(pos)
      return Promise.resolve()
    }
    return (function attempt(remain) {
      if (remain <= 0) { flyCesiumCameras(pos); return Promise.resolve() }
      return mapAppState.getMapAppUntilReady('global').then(function (map) {
        if (map && map.recenterMap) return map.recenterMap([pos], 0)
        if (map && map.initView) { map.initView([pos]); return null }
        return new Promise(function (r) { setTimeout(r, 150) }).then(function () { return attempt(remain - 1) })
      }).catch(function () {
        return new Promise(function (r) { setTimeout(r, 150) }).then(function () { return attempt(remain - 1) })
      })
    })(20).then(function () { flyCesiumCameras(pos) })
  }

  function notifyEditorResize() {
    if (!editor) return
    var root = document.querySelector('.editor-pane')
    if (!root || root.clientWidth <= 0 || root.clientHeight <= 0) return
    var key = root.clientWidth + 'x' + root.clientHeight
    if (key === lastResizeKey) return
    lastResizeKey = key
    try {
      var fh2 = fh2Bridge()
      var viewers = fh2 && fh2.cesiumViewer
      if (viewers && typeof viewers === 'object') {
        Object.keys(viewers).forEach(function (k) {
          var v = viewers[k]
          if (!v) return
          if (v.resize) v.resize()
          if (v.cesiumWidget && v.cesiumWidget.resize) v.cesiumWidget.resize()
          if (v.scene && v.scene.requestRender) v.scene.requestRender()
        })
      }
      var mas = fh2 && fh2.mapAppState
      if (mas && mas.getMapAppUntilReady) {
        mas.getMapAppUntilReady('global').then(function (map) {
          if (!map) return
          try {
            if (map.resize) map.resize()
            if (map.viewer) {
              if (map.viewer.resize) map.viewer.resize()
              if (map.viewer.cesiumWidget && map.viewer.cesiumWidget.resize) map.viewer.cesiumWidget.resize()
              if (map.viewer.scene && map.viewer.scene.requestRender) map.viewer.scene.requestRender()
            }
          } catch (e) { /* ignore */ }
        }).catch(function () {})
      }
    } catch (e) { /* ignore */ }
  }

  function scheduleResize() {
    requestAnimationFrame(function () {
      requestAnimationFrame(notifyEditorResize)
    })
  }

  if (window.ResizeObserver) {
    new ResizeObserver(scheduleResize).observe(document.querySelector('.editor-pane'))
  }
  window.addEventListener('resize', scheduleResize)

  // ---------- 本地航线库（server mock 落盘的数据） ----------
  function fmtSize(n) {
    if (!n) return ''
    return n < 1024 ? n + 'B' : (n / 1024).toFixed(1) + 'KB'
  }

  function refreshLibrary() {
    return fetch('/api/library').then(function (r) { return r.json() }).then(function (lib) {
      libEl.replaceChildren()
      var current = ''
      ;(lib.list || []).forEach(function (item) {
        if (item.wayline_uuid === currentId) current = item.wayline_name || item.wayline_uuid
        var el = document.createElement('span')
        el.className = 'lib-item'
        var n = document.createElement('span')
        n.className = 'n'
        n.textContent = item.wayline_name || item.wayline_uuid.slice(0, 8)
        n.title = item.wayline_uuid
        var s = document.createElement('span')
        s.className = 's'
        s.textContent = fmtSize(item.kmz_size)
        var dl = document.createElement('a')
        dl.textContent = '下载KMZ'
        dl.href = '/kmz-download/' + item.wayline_uuid
        el.appendChild(n)
        el.appendChild(s)
        el.appendChild(dl)
        // 点击名称 -> 重载页面进入编辑（SDK 要求切换航线销毁重建，整页重载最稳）
        el.addEventListener('click', function (ev) {
          if (ev.target === dl) return
          var q = '?edit=' + encodeURIComponent(item.wayline_uuid) +
            '&name=' + encodeURIComponent(item.wayline_name || '')
          window.location.search = q
        })
        libEl.appendChild(el)
      })
      return current
    }).catch(function () { return '' })
  }

  // ---------- 错误提示（错误码含义对齐 SDK 文档） ----------
  var ERR_MAP = {
    4001: 'WebSocket 鉴权失败',
    4002: 'WebSocket 连接断开',
    5000: 'API 请求失败',
    5001: 'API 鉴权失败',
    6000: '航线文件解析失败',
    6001: 'WASM 加载失败',
    6002: '地图初始化失败',
    7000: '对象存储上传失败',
  }

  function onEditorError(e) {
    console.error('[WaylineEditor]', e)
    var err = e || {}
    var label = ERR_MAP[err.code] || '编辑器错误'
    setStatus(label + (err.code != null ? '（' + err.code + '）：' : '：') + (err.message || ''))
  }

  // ---------- 主流程 ----------
  function boot() {
    setStatus('等待 paas-core（FH2）就绪…')
    return waitUntil(function () { return !!window.FH2 }, 60000, 100).then(function (ok) {
      if (!ok || !window.FH2) throw new Error('FH2 未加载（paas-core 拉取失败，看 Network /paas-core.*.js）')

      setStatus('加载 WaylineEditor SDK…')
      return loadScript('/paasjs-demo/wayline-edit-sdk/index.js')
    }).then(function () {
      if (!window.WaylineEditor) throw new Error('window.WaylineEditor 缺失（SDK 加载异常）')

      var wsProto = window.location.protocol === 'https:' ? 'wss' : 'ws'
      editor = new window.WaylineEditor({
        hostUrl: window.location.origin,
        api: {
          baseUrl: window.location.origin + '/paas-api/gateway',
          auth: {
            authKeys: ['X-User-Token', 'user_id'],
            headers: function () { return { 'X-User-Token': '', user_id: 'paas_test' } },
          },
        },
        ws: {
          url: wsProto + '://' + window.location.host + '/paas-ws/openapi/v0.9/notification/ws/v1/notifications',
        },
        locale: { language: 'zh', messages: window.URANUS_LANGUAGE },
      })

      editor.on('error', onEditorError)
      editor.on('wayline-save', function (payload) {
        var p = payload && typeof payload === 'object' ? payload : {}
        var id = p.waylineId || p.id || currentId
        currentId = id || currentId
        refreshLibrary().then(function (name) {
          setStatus('已保存：' + (name || id || currentId))
        })
      })

      setStatus('挂载编辑器…')
      editor.mount($('wayline-app-container'))

      setStatus('加载地图（本地瓦片）…')
      return editor.loadMap().then(function () {
        mapLoaded = true
        btnNew.disabled = false
        scheduleResize()
        // 初始即定位天津演示区（部分版本 FH2 无 mapAppState，cesiumViewer 直驱兜底）
        flyMapToCenter()
        setTimeout(flyMapToCenter, 1200)

        var q = new URLSearchParams(window.location.search)
        var editId = q.get('edit')
        if (editId) {
          editor.render('edit', {
            waylineId: editId,
            defaultPosition: { latitude: CENTER.latitude, longitude: CENTER.longitude, height: CENTER.height },
          })
          currentId = editId
          setStatus('编辑中：' + (q.get('name') || editId))
        } else {
          setStatus('就绪：点击「新建航线」开始（打点后点编辑器顶部保存图标）')
        }
        refreshLibrary()
      })
    }).catch(function (e) {
      console.error(e)
      setStatus('启动失败：' + (e && e.message ? e.message : e))
    })
  }

  function ensureMap() {
    if (mapLoaded) return Promise.resolve()
    return editor.loadMap().then(function () { mapLoaded = true })
  }

  function startCreate() {
    if (!editor || busy) return
    busy = true
    setStatus('新建航线：请在弹窗中选择机型…')
    return ensureMap().then(function () {
      var createPromise = editor.create({
        centerPoint: { latitude: CENTER.latitude, longitude: CENTER.longitude },
      })
      flyMapToCenter()
      setTimeout(flyMapToCenter, 600)
      return createPromise.then(function (res) {
        var r = res || {}
        currentId = r.waylineId || r.id || ''
        editor.render('edit', {
          waylineId: currentId,
          defaultPosition: { latitude: CENTER.latitude, longitude: CENTER.longitude, height: CENTER.height },
        })
        flyMapToCenter()
        refreshLibrary()
        setStatus('编辑中：' + (r.name || currentId) + '（打点后点顶部保存图标）')
      })
    }).catch(function (e) {
      var msg = e && e.message ? e.message : String(e)
      if (msg === 'User cancelled') { setStatus('已取消新建'); return }
      console.error('[create]', e)
      setStatus('新建失败：' + msg)
    }).then(function () { busy = false })
  }

  btnNew.addEventListener('click', startCreate)

  if (document.readyState === 'loading') {
    document.addEventListener('DOMContentLoaded', boot)
  } else {
    boot()
  }
})()
