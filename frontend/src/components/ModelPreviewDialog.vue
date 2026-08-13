<template>
  <el-dialog
    :model-value="visible"
    :title="title || t('missions.reconstructPreview')"
    width="900px"
    destroy-on-close
    @update:model-value="onVisible"
    @closed="dispose"
  >
    <div v-loading="loading" class="viewer-wrap">
      <div v-show="mode === 'tileset'" ref="host" class="viewer-host" />
      <img v-if="mode === 'fallback' && previewUrl" class="fallback" :src="previewUrl" alt="" />
      <div v-if="mode === 'download' || (mode === 'fallback' && tip)" class="tip-box">
        <p class="tip">{{ tip }}</p>
        <el-button v-if="modelUrl" type="primary" @click="openUrl">
          {{ t('missions.reconstructDownloadZip') }}
        </el-button>
      </div>
      <div v-if="error" class="err">{{ error }}</div>
    </div>
    <template #footer>
      <el-button v-if="modelUrl && mode === 'tileset'" type="primary" link @click="openUrl">
        {{ t('missions.reconstructOpenModel') }}
      </el-button>
      <el-button @click="onVisible(false)">{{ t('common.close') }}</el-button>
    </template>
  </el-dialog>
</template>

<script setup lang="ts">
import { nextTick, ref, watch } from 'vue'
import { useI18n } from 'vue-i18n'
import { ensureGisDependencies } from '@/utils/loadGisDeps'

const props = defineProps<{
  visible: boolean
  title?: string
  previewUrl?: string
  modelUrl?: string
}>()

const emit = defineEmits<{
  'update:visible': [value: boolean]
}>()

const { t } = useI18n()
const host = ref<HTMLDivElement | null>(null)
const loading = ref(false)
const error = ref('')
const tip = ref('')
/** tileset = Cesium; fallback = thumbnail; download = zip-only guidance */
const mode = ref<'idle' | 'tileset' | 'fallback' | 'download'>('idle')
let viewer: { destroy?: () => void } | null = null

function onVisible(v: boolean) {
  emit('update:visible', v)
}

function openUrl() {
  if (props.modelUrl) window.open(props.modelUrl, '_blank', 'noopener')
}

/** Cesium 仅支持 3D Tiles；司空 Terra 完成态通常只给 zip / 缩略图。 */
function isCesiumTilesetUrl(url: string): boolean {
  const path = url.split('?')[0].toLowerCase()
  if (!path || /\.zip$/i.test(path)) return false
  return /\.json$/i.test(path) || /tileset\.json/i.test(url) || /\.b3dm$/i.test(path)
}

function dispose() {
  try {
    viewer?.destroy?.()
  } catch {
    // ignore
  }
  viewer = null
  error.value = ''
  tip.value = ''
  mode.value = 'idle'
  if (host.value) host.value.innerHTML = ''
}

async function mountViewer() {
  dispose()
  if (!props.visible) return
  loading.value = true
  error.value = ''
  tip.value = ''
  await nextTick()
  const url = (props.modelUrl || '').trim()
  const preview = (props.previewUrl || '').trim()

  if (!isCesiumTilesetUrl(url)) {
    if (preview) {
      mode.value = 'fallback'
      tip.value = url ? t('missions.reconstructZipOnlyHint') : t('missions.reconstructNoModelUrl')
    } else {
      mode.value = 'download'
      tip.value = url
        ? t('missions.reconstructZipOnlyHint')
        : t('missions.reconstructNoModelUrl')
    }
    loading.value = false
    return
  }

  mode.value = 'tileset'
  try {
    await ensureGisDependencies()
    const Cesium = (window as unknown as { Cesium: any }).Cesium
    if (!Cesium || !host.value) throw new Error('Cesium not ready')
    const v = new Cesium.Viewer(host.value, {
      animation: false,
      timeline: false,
      baseLayerPicker: false,
      geocoder: false,
      homeButton: false,
      sceneModePicker: false,
      navigationHelpButton: false,
      fullscreenButton: true,
      infoBox: false,
      selectionIndicator: false,
    })
    viewer = v
    let tileset: unknown
    if (typeof Cesium.Cesium3DTileset.fromUrl === 'function') {
      tileset = await Cesium.Cesium3DTileset.fromUrl(url)
    } else {
      tileset = new Cesium.Cesium3DTileset({ url })
    }
    v.scene.primitives.add(tileset)
    await v.zoomTo(tileset)
  } catch (e) {
    console.warn('[model-preview]', e)
    if (preview) {
      mode.value = 'fallback'
      tip.value = t('missions.reconstructLoadViewerFailed')
    } else {
      mode.value = 'download'
      tip.value = t('missions.reconstructLoadViewerFailed')
    }
    error.value = ''
  } finally {
    loading.value = false
  }
}

watch(
  () => [props.visible, props.modelUrl, props.previewUrl],
  () => {
    if (props.visible) void mountViewer()
    else dispose()
  },
)
</script>

<style scoped>
.viewer-wrap {
  position: relative;
  min-height: 480px;
  background: #0b1220;
  display: flex;
  flex-direction: column;
  align-items: stretch;
  justify-content: center;
}
.viewer-host {
  width: 100%;
  height: 480px;
}
.fallback {
  display: block;
  max-width: 100%;
  max-height: 360px;
  margin: 16px auto 0;
  object-fit: contain;
}
.tip-box {
  display: flex;
  flex-direction: column;
  align-items: center;
  gap: 16px;
  padding: 48px 24px;
  text-align: center;
}
.tip {
  margin: 0;
  max-width: 520px;
  color: #c0c4cc;
  font-size: 14px;
  line-height: 1.6;
}
.err {
  margin: 8px 16px 16px;
  color: #f56c6c;
  font-size: 13px;
  text-align: center;
}
</style>
