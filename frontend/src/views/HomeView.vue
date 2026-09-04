<template>
  <div v-loading="loading" class="home-ops">
    <div ref="mapEl" class="map-bg" />

    <div class="overlay">
      <aside class="panel-col left">
        <section class="glass panel">
          <header class="panel-hd">
            <strong>{{ t('home.assets') }}</strong>
          </header>

          <div class="dock-block">
            <div class="sub-title">{{ t('home.docks') }}</div>
            <div class="dock-body">
              <div ref="dockChartEl" class="chart dock-chart" />
              <ul class="dock-legend">
                <li v-for="item in dockLegend" :key="item.key">
                  <i :style="{ background: item.color }" />
                  <span>{{ item.label }}</span>
                  <em>{{ item.value }} · {{ item.pct }}</em>
                </li>
              </ul>
            </div>
            <div class="mini-stats">
              <div><label>{{ t('home.aircraft') }}</label><b>{{ summary?.aircraftCount ?? 0 }}</b></div>
            </div>
          </div>

          <div class="stat-grid">
            <div class="stat-cell"><b>{{ summary?.memberCount ?? 0 }}</b><span>{{ t('home.members') }}</span></div>
            <div class="stat-cell"><b>{{ summary?.waylineCount ?? 0 }}</b><span>{{ t('home.waylines') }}</span></div>
            <div class="stat-cell"><b>{{ summary?.mediaCount ?? 0 }}</b><span>{{ t('home.mediaTotal') }}</span></div>
          </div>
        </section>

        <section class="glass panel compact">
          <header class="panel-hd"><strong>{{ t('home.flightData') }}</strong></header>
          <div class="kpi-row kpi-row--2">
            <div class="kpi">
              <b>{{ flight?.sortieCount ?? 0 }}</b>
              <span>{{ t('home.sorties') }}</span>
            </div>
            <div class="kpi">
              <b>{{ Math.round(Number(flight?.durationSec ?? 0)) }}</b>
              <span>{{ t('home.durationSec') }}</span>
            </div>
          </div>
        </section>
      </aside>

      <aside class="panel-col right">
        <section class="glass panel compact">
          <header class="panel-hd"><strong>{{ t('home.tasks') }}</strong></header>
          <div class="chart-block">
            <div class="chart-title">{{ t('home.taskDistribution') }}</div>
            <div ref="missionDistEl" class="chart line-chart" />
          </div>
          <div class="chart-block">
            <div class="chart-title">{{ t('home.executionResult') }}</div>
            <div ref="missionResultEl" class="chart bar-chart" />
          </div>
        </section>

        <!-- 司空设备小窗：地图 + 无人机直播浮层，下拉框切换设备 -->
        <DeviceWindowPane class="glass panel device-window-host" />
      </aside>
    </div>
  </div>
</template>

<script setup lang="ts">
import { computed, nextTick, onBeforeUnmount, onMounted, ref } from 'vue'
import { useI18n } from 'vue-i18n'
import * as echarts from 'echarts/core'
import { BarChart, LineChart, PieChart } from 'echarts/charts'
import {
  GridComponent,
  GraphicComponent,
  LegendComponent,
  TooltipComponent,
} from 'echarts/components'
import { CanvasRenderer } from 'echarts/renderers'
import type { EChartsType } from 'echarts/core'
import L from 'leaflet'
import 'leaflet/dist/leaflet.css'
import {
  fetchDashboardSummary,
  fetchFlightStats,
  fetchMissionStats,
  type DashboardSummary,
  type FlightSeriesPoint,
  type MissionSeriesPoint,
} from '@/api/dashboard'
import { fetchFlyingAircraftLocations, type AircraftLocationVO } from '@/api/paas'
import { listSafetyAreas, type SafetyAreaVO } from '@/api/safety'
import { annotationVoToRecord, fetchAnnotations } from '@/api/map'
import type { AnnotationRecord } from '@/utils/annotationStorage'
import { meApi } from '@/api/auth'
import { useUserStore } from '@/stores/user'
import DeviceWindowPane from '@/components/DeviceWindowPane.vue'

echarts.use([
  PieChart,
  LineChart,
  BarChart,
  GridComponent,
  GraphicComponent,
  LegendComponent,
  TooltipComponent,
  CanvasRenderer,
])

const { t } = useI18n()
const userStore = useUserStore()

const loading = ref(false)
const summary = ref<DashboardSummary | null>(null)
const flight = ref<{ sortieCount: number; durationSec: number; distanceM: number; series: FlightSeriesPoint[] } | null>(null)
const missionSeries = ref<MissionSeriesPoint[]>([])

const mapEl = ref<HTMLElement | null>(null)
const dockChartEl = ref<HTMLElement | null>(null)
const missionDistEl = ref<HTMLElement | null>(null)
const missionResultEl = ref<HTMLElement | null>(null)

let map: L.Map | null = null
let aircraftLayer: L.LayerGroup | null = null
let noflyLayer: L.LayerGroup | null = null
let annotationLayer: L.LayerGroup | null = null
let locationTimer: ReturnType<typeof setInterval> | null = null
let noflyTimer: ReturnType<typeof setInterval> | null = null
let annotationTimer: ReturnType<typeof setInterval> | null = null
let dockChart: EChartsType | null = null
let distChart: EChartsType | null = null
let resultChart: EChartsType | null = null
let resizeObserver: ResizeObserver | null = null

const AIRCRAFT_POLL_MS = 5000
const NOFLY_POLL_MS = 30000
const ANNOTATION_POLL_MS = 30000
const AIRCRAFT_ICON_SIZE = 36
/** 首页禁飞区描边基准线宽（屏像素）；缩放时不超过该值，缩小视野时变细，避免看起来变粗 */
const NOFLY_STROKE_BASE = 2
const NOFLY_STROKE_REF_ZOOM = 14
const ANNOTATION_STROKE_BASE = 2

function isSyncedSafetyArea(item: SafetyAreaVO) {
  const status = String(item.syncStatus || '').trim().toLowerCase()
  return status === 'synced' || status === 'synchronized' || status === 'success'
}

function pathStrokeWeight(base = NOFLY_STROKE_BASE) {
  const zoom = map?.getZoom() ?? NOFLY_STROKE_REF_ZOOM
  const scaled = base * Math.pow(2, Math.min(0, zoom - NOFLY_STROKE_REF_ZOOM))
  return Math.max(1, Number(scaled.toFixed(2)))
}

function noflyStrokeWeight() {
  return pathStrokeWeight(NOFLY_STROKE_BASE)
}

function syncOverlayStrokeWeights() {
  const noflyWeight = noflyStrokeWeight()
  const annotationWeight = pathStrokeWeight(ANNOTATION_STROKE_BASE)
  noflyLayer?.eachLayer((layer) => {
    if (layer instanceof L.Path) {
      layer.setStyle({ weight: noflyWeight })
    }
  })
  annotationLayer?.eachLayer((layer) => {
    if (layer instanceof L.Path) {
      layer.setStyle({ weight: annotationWeight })
    }
  })
}

function aircraftMarkerIcon(headingDeg?: number) {
  const heading = Number.isFinite(Number(headingDeg)) ? Number(headingDeg) : 0
  // SVG 机头朝上（北），按航向顺时针旋转
  const html = `<span class="home-aircraft-marker__wrap" style="transform:rotate(${heading}deg)">
    <svg class="home-aircraft-marker__svg" viewBox="0 0 24 24" aria-hidden="true">
      <path fill="#409eff" stroke="#fff" stroke-width="1.2" stroke-linejoin="round"
        d="M12 2.2 L14.2 9.2 L21 11.1 L21 13.2 L14.2 12.6 L12.8 18.8 L15.2 20.2 L15.2 21.8 L12 20.6 L8.8 21.8 L8.8 20.2 L11.2 18.8 L9.8 12.6 L3 13.2 L3 11.1 L9.8 9.2 Z"/>
    </svg>
  </span>`
  return L.divIcon({
    className: 'home-aircraft-marker',
    html,
    iconSize: [AIRCRAFT_ICON_SIZE, AIRCRAFT_ICON_SIZE],
    iconAnchor: [AIRCRAFT_ICON_SIZE / 2, AIRCRAFT_ICON_SIZE / 2],
  })
}

const DOCK_COLORS = {
  idle: '#67c23a',
  working: '#409eff',
  debugging: '#e6a23c',
  offline: '#909399',
}

const dockLegend = computed(() => {
  const d = summary.value?.dockStatus || { idle: 0, working: 0, debugging: 0, offline: 0 }
  const total = d.idle + d.working + d.debugging + d.offline
  const pct = (n: number) => (total ? `${((n / total) * 100).toFixed(1)}%` : '0.0%')
  return [
    { key: 'idle', label: t('home.dockIdle'), value: d.idle, pct: pct(d.idle), color: DOCK_COLORS.idle },
    { key: 'working', label: t('home.dockWorking'), value: d.working, pct: pct(d.working), color: DOCK_COLORS.working },
    { key: 'debugging', label: t('home.dockDebug'), value: d.debugging, pct: pct(d.debugging), color: DOCK_COLORS.debugging },
    { key: 'offline', label: t('home.dockOffline'), value: d.offline, pct: pct(d.offline), color: DOCK_COLORS.offline },
  ]
})

function queryParams(): Record<string, string> {
  return { range: 'month' }
}

function initMap() {
  if (!mapEl.value || map) return
  // 与空域安全默认中心一致（Leaflet 为 [lat, lng]）
  map = L.map(mapEl.value, {
    zoomControl: false,
    attributionControl: false,
    center: [39.06580547, 117.218304823],
    zoom: 14,
    minZoom: 3,
    maxZoom: 18,
  })
  L.tileLayer('https://server.arcgisonline.com/ArcGIS/rest/services/World_Imagery/MapServer/tile/{z}/{y}/{x}', {
    maxZoom: 18,
  }).addTo(map)
  L.control.zoom({ position: 'bottomright' }).addTo(map)
  noflyLayer = L.layerGroup().addTo(map)
  annotationLayer = L.layerGroup().addTo(map)
  aircraftLayer = L.layerGroup().addTo(map)
  map.on('zoom', syncOverlayStrokeWeights)
}

function renderAircraftMarkers(list: AircraftLocationVO[]) {
  if (!map || !aircraftLayer) return
  aircraftLayer.clearLayers()
  for (const item of list) {
    const lat = Number(item.latitude)
    const lng = Number(item.longitude)
    if (!Number.isFinite(lat) || !Number.isFinite(lng)) continue
    if (Math.abs(lat) < 1e-6 && Math.abs(lng) < 1e-6) continue
    const title = item.name || item.sn
    const height =
      item.height != null && Number.isFinite(Number(item.height))
        ? `${Math.round(Number(item.height))} m`
        : '-'
    const heading =
      item.heading != null && Number.isFinite(Number(item.heading))
        ? `${Math.round(Number(item.heading))}°`
        : '-'
    const popup = [
      `<strong>${escapeHtml(title)}</strong>`,
      `<div>${t('home.aircraftFlying')}${item.modeCode ? ` · ${escapeHtml(item.modeCode)}` : ''}</div>`,
      `<div>SN: ${escapeHtml(item.sn)}</div>`,
      `<div>${t('home.aircraftHeight')}: ${height}</div>`,
      `<div>${t('home.aircraftHeading')}: ${heading}</div>`,
    ].join('')
    L.marker([lat, lng], { icon: aircraftMarkerIcon(item.heading), title })
      .bindPopup(popup)
      .addTo(aircraftLayer)
  }
}

function toLatLngs(item: SafetyAreaVO): L.LatLngExpression[] {
  return (item.coordinates || [])
    .map((c) => {
      const lat = Number(c.latitude)
      const lng = Number(c.longitude)
      if (!Number.isFinite(lat) || !Number.isFinite(lng)) return null
      return [lat, lng] as L.LatLngExpression
    })
    .filter((p): p is L.LatLngExpression => Boolean(p))
}

function renderSyncedNoFlyZones(list: SafetyAreaVO[]) {
  if (!map || !noflyLayer) return
  noflyLayer.clearLayers()
  const weight = noflyStrokeWeight()
  for (const item of list) {
    if (!isSyncedSafetyArea(item)) continue
    const color = item.color || '#E23C39'
    const name = item.name || t('home.noflyZone')
    const pathStyle = {
      color,
      weight,
      fillColor: color,
      fillOpacity: 0.28,
      opacity: 0.9,
      lineJoin: 'round' as const,
      lineCap: 'round' as const,
    }
    let layer: L.Layer | null = null
    if (Number(item.zoneType) === 4) {
      const center = item.coordinates?.[0]
      const lat = Number(center?.latitude)
      const lng = Number(center?.longitude)
      const radius = Number(item.radiusM)
      if (!Number.isFinite(lat) || !Number.isFinite(lng) || !Number.isFinite(radius) || radius <= 0) continue
      layer = L.circle([lat, lng], { ...pathStyle, radius })
    } else {
      const latlngs = toLatLngs(item)
      if (latlngs.length < 3) continue
      layer = L.polygon(latlngs, pathStyle)
    }
    layer.bindPopup(`<strong>${escapeHtml(name)}</strong><div>${t('home.noflySynced')}</div>`)
    layer.addTo(noflyLayer)
  }
}

function annotationTypeLabel(type: AnnotationRecord['type']) {
  if (type === 4) return t('home.annotationCircle')
  if (type === 2) return t('home.annotationLine')
  return t('home.annotationPolygon')
}

function annotationLatLngs(item: AnnotationRecord): L.LatLngExpression[] {
  return (item.coordinates || [])
    .map((c) => {
      const lat = Number(c.latitude)
      const lng = Number(c.longitude)
      if (!Number.isFinite(lat) || !Number.isFinite(lng)) return null
      return [lat, lng] as L.LatLngExpression
    })
    .filter((p): p is L.LatLngExpression => Boolean(p))
}

function renderAnnotations(list: AnnotationRecord[]) {
  if (!map || !annotationLayer) return
  annotationLayer.clearLayers()
  const weight = pathStrokeWeight(ANNOTATION_STROKE_BASE)
  for (const item of list) {
    if (item.visible === false) continue
    const color = item.symbol?.polygonColor || item.symbol?.lineColor || '#2F80ED'
    const fillOpacity = Number(item.symbol?.polygonAlpha ?? (item.type === 2 ? 0 : 0.28))
    const name = item.customLabel?.content || item.name || t('home.annotation')
    const pathStyle = {
      color,
      weight,
      fillColor: color,
      fillOpacity: Number.isFinite(fillOpacity) ? fillOpacity : 0.28,
      opacity: 0.95,
      lineJoin: 'round' as const,
      lineCap: 'round' as const,
    }
    let layer: L.Layer | null = null
    if (item.type === 4) {
      const center = item.coordinates?.[0]
      const lat = Number(center?.latitude)
      const lng = Number(center?.longitude)
      const radius = Number(item.symbol?.radius)
      if (!Number.isFinite(lat) || !Number.isFinite(lng) || !Number.isFinite(radius) || radius <= 0) continue
      layer = L.circle([lat, lng], { ...pathStyle, radius })
    } else if (item.type === 2) {
      const latlngs = annotationLatLngs(item)
      if (latlngs.length < 2) continue
      layer = L.polyline(latlngs, { ...pathStyle, fillOpacity: 0 })
    } else {
      const latlngs = annotationLatLngs(item)
      if (latlngs.length < 3) continue
      layer = L.polygon(latlngs, pathStyle)
    }
    layer.bindPopup(
      `<strong>${escapeHtml(name)}</strong><div>${escapeHtml(annotationTypeLabel(item.type))}</div>`,
    )
    layer.addTo(annotationLayer)
  }
}

function escapeHtml(text: string) {
  return String(text)
    .replace(/&/g, '&amp;')
    .replace(/</g, '&lt;')
    .replace(/>/g, '&gt;')
    .replace(/"/g, '&quot;')
}

async function loadAircraftLocations() {
  try {
    const { data } = await fetchFlyingAircraftLocations()
    renderAircraftMarkers(data.data || [])
  } catch {
    // interceptor / SDK unavailable → keep previous markers
  }
}

async function loadSyncedNoFlyZones() {
  try {
    const result = await listSafetyAreas()
    renderSyncedNoFlyZones(result.list || [])
  } catch {
    // 无 map:read 或接口失败时不打断首页
  }
}

async function loadAnnotations() {
  try {
    const { data } = await fetchAnnotations()
    const mapped = (data.data || [])
      .map(annotationVoToRecord)
      .filter((item): item is AnnotationRecord => Boolean(item))
    renderAnnotations(mapped)
  } catch {
    // 无权限或接口失败时不打断首页
  }
}

function startAircraftPolling() {
  stopAircraftPolling()
  void loadAircraftLocations()
  locationTimer = setInterval(() => {
    if (document.visibilityState === 'hidden') return
    void loadAircraftLocations()
  }, AIRCRAFT_POLL_MS)
}

function stopAircraftPolling() {
  if (locationTimer) {
    clearInterval(locationTimer)
    locationTimer = null
  }
}

function startNoFlyPolling() {
  stopNoFlyPolling()
  void loadSyncedNoFlyZones()
  noflyTimer = setInterval(() => {
    if (document.visibilityState === 'hidden') return
    void loadSyncedNoFlyZones()
  }, NOFLY_POLL_MS)
}

function stopNoFlyPolling() {
  if (noflyTimer) {
    clearInterval(noflyTimer)
    noflyTimer = null
  }
}

function startAnnotationPolling() {
  stopAnnotationPolling()
  void loadAnnotations()
  annotationTimer = setInterval(() => {
    if (document.visibilityState === 'hidden') return
    void loadAnnotations()
  }, ANNOTATION_POLL_MS)
}

function stopAnnotationPolling() {
  if (annotationTimer) {
    clearInterval(annotationTimer)
    annotationTimer = null
  }
}

function ensureChart(el: HTMLElement | null | undefined, existing: EChartsType | null) {
  if (!el) return existing
  if (existing) return existing
  return echarts.init(el, undefined, { renderer: 'canvas' })
}

function renderDockChart() {
  dockChart = ensureChart(dockChartEl.value, dockChart)
  if (!dockChart) return
  const d = summary.value?.dockStatus || { idle: 0, working: 0, debugging: 0, offline: 0 }
  const data = [
    { name: t('home.dockIdle'), value: d.idle, itemStyle: { color: DOCK_COLORS.idle } },
    { name: t('home.dockWorking'), value: d.working, itemStyle: { color: DOCK_COLORS.working } },
    { name: t('home.dockDebug'), value: d.debugging, itemStyle: { color: DOCK_COLORS.debugging } },
    { name: t('home.dockOffline'), value: d.offline, itemStyle: { color: DOCK_COLORS.offline } },
  ]
  const total = data.reduce((s, i) => s + i.value, 0)
  dockChart.setOption({
    animationDuration: 400,
    series: [
      {
        type: 'pie',
        radius: ['62%', '82%'],
        center: ['50%', '50%'],
        silent: true,
        label: { show: false },
        data: total ? data : [{ name: '-', value: 1, itemStyle: { color: '#333' } }],
      },
    ],
    graphic: [
      {
        type: 'text',
        left: 'center',
        top: '42%',
        style: {
          text: String(total),
          fill: '#e5eaf3',
          fontSize: 22,
          fontWeight: 700,
          textAlign: 'center',
        },
      },
      {
        type: 'text',
        left: 'center',
        top: '58%',
        style: {
          text: t('home.docks'),
          fill: '#8b919a',
          fontSize: 12,
          textAlign: 'center',
        },
      },
    ],
  })
}

function renderMissionCharts() {
  distChart = ensureChart(missionDistEl.value, distChart)
  resultChart = ensureChart(missionResultEl.value, resultChart)
  const series = missionSeries.value
  const dates = series.map((s) => s.date.slice(5))
  const planned = series.map((s) => s.plannedCount)
  const manual = series.map((s) => s.manualCount)
  const success = series.map((s) => s.successCount)
  const paused = series.map((s) => s.pausedCount)
  const failed = series.map((s) => s.failedCount)

  distChart?.setOption({
    animationDuration: 400,
    grid: { left: 28, right: 12, top: 24, bottom: 24 },
    tooltip: { trigger: 'axis' },
    legend: {
      top: 0,
      right: 0,
      textStyle: { color: '#a3a6ad', fontSize: 11 },
      itemWidth: 10,
      itemHeight: 6,
    },
    xAxis: {
      type: 'category',
      data: dates.length ? dates : ['-'],
      axisLabel: { color: '#8b919a', fontSize: 10 },
      axisLine: { lineStyle: { color: '#3a3a3c' } },
    },
    yAxis: {
      type: 'value',
      splitLine: { lineStyle: { color: 'rgba(255,255,255,0.06)' } },
      axisLabel: { color: '#8b919a', fontSize: 10 },
    },
    series: [
      {
        name: t('home.plannedTasks'),
        type: 'line',
        smooth: true,
        showSymbol: false,
        areaStyle: { color: 'rgba(64,158,255,0.18)' },
        lineStyle: { width: 2, color: '#409eff' },
        data: planned.length ? planned : [0],
      },
      {
        name: t('home.manualTasks'),
        type: 'line',
        smooth: true,
        showSymbol: false,
        areaStyle: { color: 'rgba(103,194,58,0.12)' },
        lineStyle: { width: 2, color: '#67c23a' },
        data: manual.length ? manual : [0],
      },
    ],
  })

  resultChart?.setOption({
    animationDuration: 400,
    grid: { left: 28, right: 12, top: 24, bottom: 24 },
    tooltip: { trigger: 'axis' },
    legend: {
      top: 0,
      right: 0,
      textStyle: { color: '#a3a6ad', fontSize: 11 },
      itemWidth: 10,
      itemHeight: 6,
    },
    xAxis: {
      type: 'category',
      data: dates.length ? dates : ['-'],
      axisLabel: { color: '#8b919a', fontSize: 10 },
      axisLine: { lineStyle: { color: '#3a3a3c' } },
    },
    yAxis: {
      type: 'value',
      splitLine: { lineStyle: { color: 'rgba(255,255,255,0.06)' } },
      axisLabel: { color: '#8b919a', fontSize: 10 },
    },
    series: [
      {
        name: t('home.execSuccess'),
        type: 'bar',
        stack: 'r',
        barWidth: 10,
        itemStyle: { color: '#67c23a' },
        data: success.length ? success : [0],
      },
      {
        name: t('home.execPaused'),
        type: 'bar',
        stack: 'r',
        itemStyle: { color: '#e6a23c' },
        data: paused.length ? paused : [0],
      },
      {
        name: t('home.execFailed'),
        type: 'bar',
        stack: 'r',
        itemStyle: { color: '#f56c6c' },
        data: failed.length ? failed : [0],
      },
    ],
  })
}

function resizeCharts() {
  map?.invalidateSize()
  dockChart?.resize()
  distChart?.resize()
  resultChart?.resize()
}

async function load() {
  loading.value = true
  try {
    const params = queryParams()
    const [s, f, m] = await Promise.all([
      fetchDashboardSummary(params),
      fetchFlightStats(params),
      fetchMissionStats(params),
    ])
    summary.value = s.data.data
    flight.value = f.data.data
    missionSeries.value = m.data.data?.series || []
    await nextTick()
    renderDockChart()
    renderMissionCharts()
    resizeCharts()
  } catch {
    // interceptor
  } finally {
    loading.value = false
  }
}

onMounted(async () => {
  try {
    const { data } = await meApi()
    userStore.setSession(userStore.token, data.data)
  } catch {
    // ignore
  }
  await nextTick()
  initMap()
  await load()
  startAircraftPolling()
  startNoFlyPolling()
  startAnnotationPolling()
  resizeObserver = new ResizeObserver(() => resizeCharts())
  if (mapEl.value) resizeObserver.observe(mapEl.value)
  window.addEventListener('resize', resizeCharts)
})

onBeforeUnmount(() => {
  stopAircraftPolling()
  stopNoFlyPolling()
  stopAnnotationPolling()
  window.removeEventListener('resize', resizeCharts)
  resizeObserver?.disconnect()
  dockChart?.dispose()
  distChart?.dispose()
  resultChart?.dispose()
  aircraftLayer?.clearLayers()
  aircraftLayer = null
  annotationLayer?.clearLayers()
  annotationLayer = null
  noflyLayer?.clearLayers()
  noflyLayer = null
  map?.remove()
  map = null
})
</script>

<style scoped lang="scss">
.home-ops {
  position: relative;
  height: calc(100vh - 60px);
  min-height: 640px;
  overflow: hidden;
  background: #0a0a0a;
}

.map-bg {
  position: absolute;
  inset: 0;
  z-index: 0;
}

:deep(.home-aircraft-marker) {
  background: transparent;
  border: none;
}

:deep(.home-aircraft-marker__wrap) {
  display: flex;
  width: 36px;
  height: 36px;
  align-items: center;
  justify-content: center;
  transform-origin: center center;
  filter: drop-shadow(0 1px 2px rgba(0, 0, 0, 0.55));
}

:deep(.home-aircraft-marker__svg) {
  display: block;
  width: 28px;
  height: 28px;
}

.overlay {
  position: absolute;
  inset: 0;
  z-index: 2;
  pointer-events: none;
  display: grid;
  /* 右列（任务 + 设备小窗）：SDK 小窗浮层设计宽约 400px，
     需再加面板内边距 28px、边框 2px、滚动条 6px 及余量，给足 448px */
  grid-template-columns: minmax(300px, 360px) 1fr 448px;
  grid-template-rows: 1fr;
  gap: 12px;
  padding: 12px 14px 16px;
}

.panel-col {
  display: flex;
  flex-direction: column;
  gap: 12px;
  min-height: 0;
  /* 列本身不拦地图；否则空白区域会挡住右下角缩放 */
  pointer-events: none;

  &.left {
    grid-column: 1;
  }

  &.right {
    grid-column: 3;
    /* 任务 + 设备小窗总高超视口时可滚动；开启 auto 让滚轮生效，
       地图缩放控件层级更高（leaflet-control z-index:5）不受影响 */
    pointer-events: auto;
    overflow-y: auto;
    overscroll-behavior: contain;
    scrollbar-width: thin;
    scrollbar-color: rgba(255, 255, 255, 0.18) transparent;

    &::-webkit-scrollbar {
      width: 6px;
    }

    &::-webkit-scrollbar-thumb {
      background: rgba(255, 255, 255, 0.18);
      border-radius: 3px;
    }

    &::-webkit-scrollbar-track {
      background: transparent;
    }
  }

  .panel {
    pointer-events: auto;
  }
}

.glass {
  background: rgba(12, 14, 18, 0.72);
  backdrop-filter: blur(12px);
  border: 1px solid rgba(255, 255, 255, 0.08);
  border-radius: 10px;
  box-shadow: 0 8px 28px rgba(0, 0, 0, 0.35);
}

.panel {
  padding: 12px 14px 14px;
  color: #e5eaf3;

  &.compact {
    flex: 0 0 auto;
  }

  &.grow {
    flex: 1;
    min-height: 0;
    display: flex;
    flex-direction: column;
  }
}

/* 设备小窗：固定 360px 高，内部自行管理小窗浮层 */
.device-window-host {
  flex: 0 0 360px;
  height: 360px;
  overflow: hidden;
}

.panel-hd {
  display: flex;
  align-items: center;
  justify-content: space-between;
  margin-bottom: 10px;

  strong {
    font-size: 14px;
  }

  .tag {
    font-size: 11px;
    color: #8b919a;
    border: 1px solid rgba(255, 255, 255, 0.12);
    border-radius: 4px;
    padding: 1px 6px;
  }
}

.sub-title {
  font-size: 12px;
  color: #a3a6ad;
  margin-bottom: 6px;
}

.dock-body {
  display: grid;
  grid-template-columns: 140px 1fr;
  gap: 8px;
  align-items: center;
}

.dock-chart {
  height: 140px;
}

.dock-legend {
  list-style: none;
  margin: 0;
  padding: 0;
  display: flex;
  flex-direction: column;
  gap: 8px;

  li {
    display: grid;
    grid-template-columns: 8px 1fr auto;
    gap: 8px;
    align-items: center;
    font-size: 12px;
    color: #cfd3dc;

    i {
      width: 8px;
      height: 8px;
      border-radius: 50%;
    }

    em {
      font-style: normal;
      color: #8b919a;
      font-size: 11px;
    }
  }
}

.mini-stats {
  display: grid;
  grid-template-columns: 1fr;
  gap: 8px;
  margin-top: 10px;

  div {
    background: rgba(255, 255, 255, 0.03);
    border-radius: 6px;
    padding: 8px 10px;
    display: flex;
    justify-content: space-between;
    align-items: baseline;

    label {
      font-size: 12px;
      color: #8b919a;
    }

    b {
      font-size: 16px;
    }
  }
}

.stat-grid {
  display: grid;
  grid-template-columns: 1fr 1fr 1fr;
  gap: 8px;
  margin-top: 12px;
}

.stat-cell {
  background: rgba(255, 255, 255, 0.03);
  border-radius: 6px;
  padding: 10px;
  text-align: center;

  b {
    display: block;
    font-size: 18px;
    line-height: 1.2;
  }

  span {
    font-size: 12px;
    color: #8b919a;
  }
}

.kpi-row {
  display: grid;
  grid-template-columns: repeat(3, 1fr);
  gap: 8px;

  &--2 {
    grid-template-columns: repeat(2, 1fr);
  }
}

.kpi {
  text-align: center;
  background: rgba(255, 255, 255, 0.03);
  border-radius: 6px;
  padding: 10px 6px;

  b {
    display: block;
    font-size: 18px;
  }

  span {
    font-size: 11px;
    color: #8b919a;
  }
}

.chart-block {
  flex: 0 0 auto;
  display: flex;
  flex-direction: column;

  + .chart-block {
    margin-top: 8px;
  }
}

.chart-title {
  font-size: 12px;
  color: #a3a6ad;
  margin-bottom: 2px;
}

.line-chart,
.bar-chart {
  height: 190px;
  flex: none;
}

:deep(.el-radio-group) {
  background: rgba(12, 14, 18, 0.72);
  backdrop-filter: blur(10px);
  border-radius: 6px;
  padding: 2px;
}

:deep(.el-radio-button__inner) {
  background: transparent;
  border: none !important;
  color: #cfd3dc;
  box-shadow: none !important;
}

:deep(.el-radio-button.is-active .el-radio-button__inner) {
  background: rgba(64, 158, 255, 0.25);
  color: #fff;
}

:deep(.el-date-editor) {
  background: rgba(12, 14, 18, 0.72);
  box-shadow: none;
  --el-fill-color-blank: transparent;
}

:deep(.leaflet-control-container) {
  /* 叠在侧栏空白区之上，保证加减号可点 */
  z-index: 5 !important;
  pointer-events: none;
}

:deep(.leaflet-control) {
  pointer-events: auto;
}

:deep(.leaflet-control-zoom) {
  border: none !important;
  margin-bottom: 18px !important;
  margin-right: 10px !important;

  a {
    background: rgba(12, 14, 18, 0.78) !important;
    color: #e5eaf3 !important;
    border-bottom-color: #2b2b2c !important;
    width: 32px !important;
    height: 32px !important;
    line-height: 32px !important;
  }
}

@media (max-width: 1200px) {
  .overlay {
    grid-template-columns: 1fr;
    overflow: auto;
    pointer-events: auto;
  }

  .panel-col.left,
  .panel-col.right {
    grid-column: 1;
  }

  .map-bg {
    opacity: 0.35;
  }
}
</style>
