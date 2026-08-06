<template>
  <div class="waylines-page">
    <aside class="library-panel">
      <div class="library-title">{{ t('waylines.libraryTitle') }}</div>

      <div class="library-body">
        <div class="folder-col">
          <div class="col-header">
            <span>{{ t('waylines.folders') }}</span>
            <el-button
              v-if="canWrite"
              link
              type="primary"
              :icon="FolderAdd"
              :title="t('waylines.createFolder')"
              @click="onCreateFolder"
            />
          </div>
          <el-scrollbar class="col-scroll">
            <div
              v-for="folder in folders"
              :key="folder.id"
              class="folder-item"
              :class="{ active: folder.id === selectedFolderId }"
              @click="selectFolder(folder.id)"
            >
              <el-icon class="folder-icon"><Folder /></el-icon>
              <span class="folder-name" :title="folder.name">{{ folder.name }}</span>
            </div>
            <el-empty v-if="!folders.length" :description="t('waylines.noFolders')" :image-size="48" />
          </el-scrollbar>
        </div>

        <div class="wayline-col">
          <div class="col-header">
            <span>{{ t('waylines.listTitle', { count: total }) }}</span>
            <div class="col-actions">
              <el-button link :icon="Search" :title="t('waylines.namePlaceholder')" @click="toggleSearch" />
              <el-button
                v-if="canWrite"
                link
                type="primary"
                :icon="Plus"
                :title="t('waylines.create')"
                @click="onCreate"
              />
            </div>
          </div>
          <div v-if="searchOpen" class="search-bar">
            <el-input
              v-model="keyword"
              clearable
              size="small"
              :placeholder="t('waylines.namePlaceholder')"
              @keyup.enter="reloadWaylines"
              @clear="reloadWaylines"
            />
          </div>
          <el-scrollbar class="col-scroll">
            <div
              v-for="row in list"
              :key="row.id ?? row.fh2WaylineUuid"
              class="wayline-card"
              :class="{ active: isSelected(row) }"
              @click="onSelect(row)"
            >
              <div class="card-top">
                <span class="card-name" :title="displayName(row)">{{ displayName(row) }}</span>
                <div class="card-ops" @click.stop>
                  <el-button
                    v-if="canWrite"
                    link
                    :icon="EditPen"
                    :title="t('common.edit')"
                    @click="onSelect(row)"
                  />
                  <el-dropdown v-if="canWrite" trigger="click" @command="(cmd: string) => onCardCommand(cmd, row)">
                    <el-button link :icon="MoreFilled" />
                    <template #dropdown>
                      <el-dropdown-menu>
                        <el-dropdown-item command="delete" style="color: var(--el-color-danger)">
                          {{ t('common.delete') }}
                        </el-dropdown-item>
                      </el-dropdown-menu>
                    </template>
                  </el-dropdown>
                </div>
              </div>
              <div class="card-meta">
                <el-icon><Promotion /></el-icon>
                <span>{{ row.aircraftModel || '-' }}</span>
              </div>
              <div class="card-time">
                {{ t('waylines.updatedAt') }} {{ row.updatedAt || '-' }}
              </div>
            </div>
            <el-empty v-if="!list.length" :description="t('waylines.empty')" :image-size="48" />
          </el-scrollbar>
          <div v-if="total > pageSize" class="pager">
            <el-pagination
              v-model:current-page="page"
              :page-size="pageSize"
              layout="prev, pager, next"
              small
              :total="total"
              @current-change="loadWaylines"
            />
          </div>
        </div>
      </div>
    </aside>

    <WaylineEditorPane
      :mode="editorMode"
      :wayline-id="editorWaylineId"
      :wayline-name="editorWaylineName"
      :folder-id="selectedFolderId ?? undefined"
      @created="onEditorCreated"
      @saved="onEditorSaved"
    />
  </div>
</template>

<script setup lang="ts">
import { computed, nextTick, onMounted, ref } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { useI18n } from 'vue-i18n'
import { ElMessage, ElMessageBox } from 'element-plus'
import { EditPen, Folder, FolderAdd, MoreFilled, Plus, Promotion, Search } from '@element-plus/icons-vue'
import WaylineEditorPane from '@/components/WaylineEditorPane.vue'
import {
  createWaylineFolder,
  deleteWayline,
  deleteWaylineByUuid,
  fetchWaylineFolders,
  fetchWaylines,
  type WaylineFolderVO,
  type WaylineVO,
} from '@/api/wayline'
import { useUserStore } from '@/stores/user'

const { t } = useI18n()
const route = useRoute()
const router = useRouter()
const userStore = useUserStore()
const canWrite = computed(() => !!userStore.user?.permissions?.includes('wayline:write'))

const folders = ref<WaylineFolderVO[]>([])
const selectedFolderId = ref<number | null>(null)
const list = ref<WaylineVO[]>([])
const keyword = ref('')
const searchOpen = ref(false)
const page = ref(1)
const pageSize = ref(50)
const total = ref(0)

const editorMode = ref<'idle' | 'edit' | 'create'>('idle')
const editorWaylineId = ref('')
const editorWaylineName = ref('')
const selectedKey = ref('')

function displayName(row: WaylineVO) {
  const name = row.name?.trim()
  if (name && name !== row.fh2WaylineUuid) return name
  return name || t('waylines.unnamed')
}

function rowKey(row: WaylineVO) {
  return String(row.fh2WaylineUuid || row.id || '')
}

function isSelected(row: WaylineVO) {
  return selectedKey.value === rowKey(row)
}

function selectFolder(id: number) {
  if (selectedFolderId.value === id) return
  selectedFolderId.value = id
  editorMode.value = 'idle'
  editorWaylineId.value = ''
  editorWaylineName.value = ''
  selectedKey.value = ''
  reloadWaylines()
}

function onSelect(row: WaylineVO) {
  if (!row.fh2WaylineUuid) {
    ElMessage.warning(t('waylines.editUnavailable'))
    return
  }
  selectedKey.value = rowKey(row)
  editorWaylineId.value = row.fh2WaylineUuid
  editorWaylineName.value = displayName(row)
  editorMode.value = 'edit'
}

async function onCreate() {
  if (!canWrite.value) return
  if (selectedFolderId.value == null) {
    ElMessage.warning(t('waylines.selectFolderFirst'))
    return
  }
  selectedKey.value = ''
  editorWaylineId.value = ''
  editorWaylineName.value = ''
  // 重置再切 create，确保连续点击新建也能触发编辑器
  editorMode.value = 'idle'
  await nextTick()
  editorMode.value = 'create'
}

function toggleSearch() {
  searchOpen.value = !searchOpen.value
  if (!searchOpen.value && keyword.value) {
    keyword.value = ''
    reloadWaylines()
  }
}

async function loadFolders() {
  const { data } = await fetchWaylineFolders()
  folders.value = data.data || []
  if (
    folders.value.length &&
    (selectedFolderId.value == null || !folders.value.some((f) => f.id === selectedFolderId.value))
  ) {
    selectedFolderId.value = folders.value[0].id
  }
}

async function loadWaylines() {
  if (selectedFolderId.value == null) {
    list.value = []
    total.value = 0
    return
  }
  const { data } = await fetchWaylines({
    folderId: selectedFolderId.value,
    keyword: keyword.value || undefined,
    page: page.value,
    pageSize: pageSize.value,
  })
  const pageData = data.data as { list: WaylineVO[]; total: number }
  list.value = pageData.list || []
  total.value = pageData.total || 0
}

function reloadWaylines() {
  page.value = 1
  return loadWaylines()
}

async function onCreateFolder() {
  const { value } = await ElMessageBox.prompt(
    t('waylines.folderNamePlaceholder'),
    t('waylines.createFolder'),
    {
      confirmButtonText: t('common.confirm'),
      cancelButtonText: t('common.cancel'),
      inputPattern: /\S+/,
      inputErrorMessage: t('waylines.folderNameRequired'),
    },
  )
  const name = value.trim()
  await createWaylineFolder({ name })
  ElMessage.success(t('waylines.folderCreated'))
  await loadFolders()
  const created = folders.value.find((f) => f.name === name)
  if (created) {
    selectedFolderId.value = created.id
    await reloadWaylines()
  }
}

async function onCardCommand(cmd: string, row: WaylineVO) {
  if (cmd !== 'delete') return
  await ElMessageBox.confirm(t('waylines.deleteConfirm', { name: displayName(row) }), t('common.confirmTitle'))
  if (row.fh2WaylineUuid) {
    await deleteWaylineByUuid(row.fh2WaylineUuid)
  } else if (row.id != null) {
    await deleteWayline(row.id)
  } else {
    ElMessage.warning(t('waylines.editUnavailable'))
    return
  }
  if (isSelected(row)) {
    editorMode.value = 'idle'
    editorWaylineId.value = ''
    editorWaylineName.value = ''
    selectedKey.value = ''
  }
  ElMessage.success(t('waylines.deleted'))
  await loadWaylines()
}

async function onEditorCreated(payload: { waylineId: string; name: string }) {
  editorWaylineId.value = payload.waylineId
  editorWaylineName.value = payload.name
  editorMode.value = 'edit'
  selectedKey.value = payload.waylineId
  await reloadWaylines()
}

async function onEditorSaved() {
  await loadWaylines()
}

async function applyRouteQuery() {
  const q = route.query
  if (q.folderId != null && q.folderId !== '') {
    const fid = Number(q.folderId)
    if (Number.isFinite(fid)) selectedFolderId.value = fid
  }
  if (q.mode === 'create') {
    await onCreate()
  } else if (q.mode === 'edit' && q.waylineId) {
    editorWaylineId.value = String(q.waylineId)
    editorWaylineName.value = String(q.name || '')
    editorMode.value = 'edit'
    selectedKey.value = String(q.waylineId)
  }
  if (q.mode || q.waylineId || q.folderId) {
    await router.replace({ name: 'waylines' })
  }
}

onMounted(async () => {
  await loadFolders()
  await applyRouteQuery()
  await loadWaylines()
})
</script>

<style scoped lang="scss">
.waylines-page {
  display: flex;
  height: 100%;
  min-height: 0;
  background: #0a0a0a;
  color: #e5eaf3;
}

.library-panel {
  width: 420px;
  flex-shrink: 0;
  display: flex;
  flex-direction: column;
  border-right: 1px solid #2b2b2c;
  background: #141414;
  min-height: 0;
}

.library-title {
  height: 48px;
  padding: 0 16px;
  display: flex;
  align-items: center;
  font-size: 16px;
  font-weight: 600;
  border-bottom: 1px solid #2b2b2c;
}

.library-body {
  flex: 1;
  min-height: 0;
  display: flex;
}

.folder-col {
  width: 150px;
  flex-shrink: 0;
  border-right: 1px solid #2b2b2c;
  display: flex;
  flex-direction: column;
  min-height: 0;
}

.wayline-col {
  flex: 1;
  min-width: 0;
  display: flex;
  flex-direction: column;
  min-height: 0;
}

.col-header {
  height: 40px;
  padding: 0 10px;
  display: flex;
  align-items: center;
  justify-content: space-between;
  font-size: 13px;
  color: #a3a6ad;
  border-bottom: 1px solid #2b2b2c;
}

.col-actions {
  display: flex;
  align-items: center;
  gap: 2px;
}

.col-scroll {
  flex: 1;
  min-height: 0;
  padding: 8px;
}

.search-bar {
  padding: 8px 8px 0;
}

.folder-item {
  display: flex;
  align-items: center;
  gap: 6px;
  padding: 8px 10px;
  border-radius: 6px;
  cursor: pointer;
  color: #cfd3dc;
  margin-bottom: 4px;

  &:hover {
    background: #1f1f1f;
  }

  &.active {
    background: rgba(64, 158, 255, 0.18);
    color: #409eff;
  }
}

.folder-name {
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
  font-size: 13px;
}

.wayline-card {
  padding: 12px;
  margin-bottom: 8px;
  border-radius: 8px;
  background: #1a1a1a;
  border: 1px solid #2b2b2c;
  cursor: pointer;
  transition: border-color 0.15s ease, background 0.15s ease;

  &:hover {
    border-color: #3a3a3c;
    background: #1f1f1f;
  }

  &.active {
    border-color: #e6a23c;
    background: #221c14;

    .card-name {
      color: #e6a23c;
    }
  }
}

.card-top {
  display: flex;
  align-items: flex-start;
  gap: 8px;
  margin-bottom: 8px;
}

.card-name {
  flex: 1;
  min-width: 0;
  font-size: 14px;
  font-weight: 600;
  color: #e5eaf3;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.card-ops {
  display: flex;
  align-items: center;
  flex-shrink: 0;
  opacity: 0.75;
}

.card-meta,
.card-time {
  display: flex;
  align-items: center;
  gap: 6px;
  font-size: 12px;
  color: #8b919a;
  line-height: 1.5;
}

.card-meta {
  margin-bottom: 4px;
}

.pager {
  padding: 8px;
  display: flex;
  justify-content: center;
  border-top: 1px solid #2b2b2c;
}
</style>
