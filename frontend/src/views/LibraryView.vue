<template>
  <div>
    <div class="toolbar">
      <el-select v-model="mediaType" clearable :placeholder="t('library.typePlaceholder')" style="width: 120px" @change="load">
        <el-option :label="t('library.image')" value="image" />
        <el-option :label="t('library.video')" value="video" />
      </el-select>
      <el-button @click="load">{{ t('common.refresh') }}</el-button>
    </div>

    <el-empty v-if="!list.length && !loadError" :description="t('library.empty')" />
    <div v-else class="media-grid">
      <el-card v-for="item in list" :key="item.id" shadow="hover" class="media-card">
        <div class="media-preview">
          <el-image
            v-if="item.mediaType === 'image' && item.previewUrl"
            :src="item.previewUrl"
            :preview-src-list="[item.previewUrl]"
            fit="cover"
            preview-teleported
          />
          <div
            v-else-if="item.mediaType === 'video' && item.previewUrl"
            class="video-cover"
            @click="onPlay(item)"
          >
            <video :src="item.previewUrl" preload="metadata" muted />
            <div class="play-btn" aria-hidden="true">▶</div>
          </div>
          <div v-else class="media-placeholder">
            {{ item.mediaType === 'video' ? t('library.videoLoadFailed') : t('library.imageLoadFailed') }}
          </div>
        </div>
        <div class="media-details">
          <el-tooltip :content="item.name" placement="top">
            <div class="media-name">{{ item.name }}</div>
          </el-tooltip>
          <div class="media-meta">
            <span>{{ item.mediaType === 'image' ? t('library.image') : t('library.video') }}</span>
            <span>{{ formatBytes(item.sizeBytes) }}</span>
          </div>
          <div class="media-meta">
            <span>{{ item.payload || t('library.unknownPayload') }}</span>
            <span>{{ formatDateTime(item.createdAt) }}</span>
          </div>
          <div class="media-actions">
            <el-button
              v-if="item.mediaType === 'video' && item.previewUrl"
              link
              type="primary"
              @click="onPlay(item)"
            >
              {{ t('library.play') }}
            </el-button>
            <el-button link type="primary" @click="onDownload(item)">{{ t('library.download') }}</el-button>
            <el-button v-if="canWrite" link type="danger" @click="onTrash(item)">{{ t('common.delete') }}</el-button>
          </div>
        </div>
      </el-card>
    </div>

    <div class="pager">
      <el-pagination
        v-model:current-page="page"
        v-model:page-size="pageSize"
        layout="total, prev, pager, next"
        :total="total"
        @current-change="load"
      />
    </div>

    <el-dialog
      v-model="playerVisible"
      :title="playing?.name || t('library.videoPlay')"
      width="860px"
      destroy-on-close
      @closed="onPlayerClosed"
    >
      <video
        v-if="playing?.previewUrl"
        class="player"
        :src="playing.previewUrl"
        controls
        autoplay
      />
    </el-dialog>
  </div>
</template>

<script setup lang="ts">
import { computed, onMounted, ref } from 'vue'
import { useI18n } from 'vue-i18n'
import { ElMessage } from 'element-plus'
import {
  fetchMedia,
  fetchMediaDownloadUrl,
  trashMedia,
  type MediaVO,
} from '@/api/library'
import { useUserStore } from '@/stores/user'

const { t } = useI18n()
const userStore = useUserStore()
const canWrite = computed(() => !!userStore.user?.permissions?.includes('library:write'))
type MediaItem = MediaVO & { previewUrl?: string }

const list = ref<MediaItem[]>([])
const mediaType = ref('')
const page = ref(1)
const pageSize = ref(20)
const total = ref(0)
const loadError = ref('')
const playerVisible = ref(false)
const playing = ref<MediaItem | null>(null)

function pad(n: number) {
  return String(n).padStart(2, '0')
}

function formatDateTime(value?: string | number | null) {
  if (value === undefined || value === null || value === '') return '-'
  let date: Date
  if (typeof value === 'number') {
    date = new Date(value < 1e12 ? value * 1000 : value)
  } else if (/^\d+$/.test(value.trim())) {
    const n = Number(value)
    date = new Date(n < 1e12 ? n * 1000 : n)
  } else {
    date = new Date(value)
  }
  if (Number.isNaN(date.getTime())) return String(value)
  return `${date.getFullYear()}-${pad(date.getMonth() + 1)}-${pad(date.getDate())} ${pad(date.getHours())}:${pad(date.getMinutes())}:${pad(date.getSeconds())}`
}

function formatBytes(n: number) {
  if (!n && n !== 0) return '-'
  const units = ['B', 'KB', 'MB', 'GB', 'TB']
  let v = n
  let i = 0
  while (v >= 1024 && i < units.length - 1) {
    v /= 1024
    i++
  }
  return `${v.toFixed(i === 0 ? 0 : 1)} ${units[i]}`
}

async function load() {
  loadError.value = ''
  try {
    const m = await fetchMedia({
      mediaType: mediaType.value || undefined,
      page: page.value,
      pageSize: pageSize.value,
    })
    const media = m.data.data.list || []
    list.value = media
    total.value = m.data.data.total || 0
    const previewResults = await Promise.allSettled(
      media.map(async (item) => {
        const { data } = await fetchMediaDownloadUrl(item.id)
        return { id: item.id, url: data.data.downloadUrl }
      }),
    )
    const previewUrls = new Map(
      previewResults
        .filter((result): result is PromiseFulfilledResult<{ id: number; url: string }> => result.status === 'fulfilled')
        .map((result) => [result.value.id, result.value.url]),
    )
    list.value = media.map((item) => ({ ...item, previewUrl: previewUrls.get(item.id) }))
  } catch (e: unknown) {
    list.value = []
    total.value = 0
    const msg =
      (e as { response?: { data?: { message?: string } } })?.response?.data?.message ||
      (e as Error)?.message ||
      t('library.loadFailed')
    loadError.value = msg
    ElMessage.error(msg)
  }
}

function onPlay(item: MediaItem) {
  if (!item.previewUrl) {
    ElMessage.warning(t('library.noVideoUrl'))
    return
  }
  playing.value = item
  playerVisible.value = true
}

function onPlayerClosed() {
  playing.value = null
}

async function onDownload(row: MediaVO) {
  const { data } = await fetchMediaDownloadUrl(row.id)
  const url = data.data.downloadUrl
  if (!url) {
    ElMessage.warning(t('library.noDownloadUrl'))
    return
  }
  window.open(url, '_blank')
}

async function onTrash(row: MediaVO) {
  await trashMedia(row.id)
  ElMessage.success(t('library.deleted'))
  await load()
}

onMounted(load)
</script>

<style scoped lang="scss">
.toolbar {
  display: flex;
  gap: 12px;
  margin-bottom: 16px;
  flex-wrap: wrap;
  align-items: center;
}
.pager {
  margin-top: 16px;
  display: flex;
  justify-content: flex-end;
}
.media-grid {
  display: grid;
  grid-template-columns: repeat(auto-fill, minmax(240px, 1fr));
  gap: 16px;
}
.media-card {
  overflow: hidden;
  background: #141414;
  border-color: #414243;
}
.media-preview {
  height: 180px;
  margin: -20px -20px 0;
  background: #252525;
}
.media-preview :deep(.el-image),
.media-preview video {
  width: 100%;
  height: 100%;
  display: block;
  object-fit: cover;
}
.video-cover {
  position: relative;
  width: 100%;
  height: 100%;
  cursor: pointer;
}
.play-btn {
  position: absolute;
  inset: 0;
  margin: auto;
  width: 48px;
  height: 48px;
  border-radius: 50%;
  background: rgba(0, 0, 0, 0.55);
  color: #fff;
  display: grid;
  place-items: center;
  font-size: 18px;
  pointer-events: none;
}
.media-placeholder {
  height: 100%;
  display: grid;
  place-items: center;
  color: #a3a6ad;
}
.player {
  width: 100%;
  max-height: 70vh;
  background: #000;
  display: block;
}
.media-details {
  padding-top: 14px;
}
.media-name {
  overflow: hidden;
  color: #e5eaf3;
  font-weight: 600;
  text-overflow: ellipsis;
  white-space: nowrap;
}
.media-meta {
  display: flex;
  justify-content: space-between;
  gap: 8px;
  margin-top: 8px;
  color: #a3a6ad;
  font-size: 12px;
}
.media-meta span {
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}
.media-actions {
  margin-top: 10px;
}
</style>
