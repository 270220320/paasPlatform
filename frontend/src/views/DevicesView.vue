<template>
  <div>
    <el-tabs v-model="deviceType" @tab-change="load">
      <el-tab-pane label="机场/地面站" name="dock" />
      <el-tab-pane label="飞行器" name="aircraft" />
      <el-tab-pane label="其它物流设备" name="other" />
    </el-tabs>

    <div class="toolbar">
      <el-button v-if="canWrite" type="primary" @click="$router.push({ name: 'device-manager' })">司空设备管理</el-button>
      <el-button v-if="canWrite" @click="onSyncFh2">从司空同步</el-button>
      <el-button v-if="canWrite" @click="showBindCode">设备绑定码</el-button>
      <el-button v-if="canWrite" @click="openIssue">报障</el-button>
      <el-input v-model="keyword" clearable placeholder="SN / 名称" style="width: 220px" @keyup.enter="load" />
      <el-button @click="load">刷新</el-button>
      <el-button @click="loadIssues">刷新报障</el-button>
    </div>

    <el-table :data="list" border>
      <el-table-column prop="model" label="设备型号" min-width="140" />
      <el-table-column prop="sn" label="设备 SN" min-width="160" />
      <el-table-column prop="name" label="设备名称" min-width="140" />
      <el-table-column prop="firmwareVersion" label="固件版本" width="140" />
      <el-table-column prop="firmwareUpgradeStatus" label="固件升级" width="120" />
      <el-table-column prop="workStatus" label="工作状态" width="120" />
      <el-table-column prop="joinedAt" label="加入时间" width="180" />
      <el-table-column prop="onlineAt" label="在线时间" width="180" />
    </el-table>

    <h3 class="section">设备报障</h3>
    <el-table :data="issues" border>
      <el-table-column prop="id" label="ID" width="80" />
      <el-table-column prop="deviceId" label="设备 ID" width="100" />
      <el-table-column prop="description" label="描述" min-width="200" />
      <el-table-column prop="trackingNo" label="跟踪号" width="140" />
      <el-table-column prop="status" label="状态" width="100" />
      <el-table-column prop="createdAt" label="创建时间" width="180" />
    </el-table>

    <el-dialog v-model="bindVisible" title="设备绑定码" width="480px">
      <el-form label-width="110px" v-if="bindCode">
        <el-form-item label="作业空间编码"><el-input :model-value="bindCode.workspaceCode" readonly /></el-form-item>
        <el-form-item label="绑定码"><el-input :model-value="bindCode.code" readonly /></el-form-item>
        <el-form-item label="过期时间"><el-input :model-value="bindCode.expireAt" readonly /></el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="onRefreshBind">刷新绑定码</el-button>
        <el-button type="primary" @click="bindVisible = false">完成</el-button>
      </template>
    </el-dialog>

    <el-dialog v-model="issueVisible" title="设备报障" width="480px">
      <el-form label-width="90px">
        <el-form-item label="设备">
          <el-select v-model="issueForm.deviceId" filterable style="width: 100%">
            <el-option v-for="d in list" :key="d.id" :label="`${d.name} (${d.sn})`" :value="d.id" />
          </el-select>
        </el-form-item>
        <el-form-item label="描述">
          <el-input v-model="issueForm.description" type="textarea" :rows="3" />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="issueVisible = false">取消</el-button>
        <el-button type="primary" @click="submitIssue">提交</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<script setup lang="ts">
import { computed, onMounted, reactive, ref } from 'vue'
import { ElMessage } from 'element-plus'
import {
  createDeviceIssue,
  fetchCurrentBindCode,
  fetchDeviceIssues,
  fetchDevices,
  refreshBindCode,
  type BindCodeVO,
  type DeviceIssueVO,
  type DeviceVO,
} from '@/api/device'
import { syncFh2Devices } from '@/api/paas'
import { useUserStore } from '@/stores/user'

const userStore = useUserStore()
const canWrite = computed(() => !!userStore.user?.permissions?.includes('device:write'))
const deviceType = ref('dock')
const keyword = ref('')
const list = ref<DeviceVO[]>([])
const issues = ref<DeviceIssueVO[]>([])
const bindVisible = ref(false)
const bindCode = ref<BindCodeVO | null>(null)
const issueVisible = ref(false)
const issueForm = reactive({ deviceId: undefined as number | undefined, description: '' })

async function load() {
  const { data } = await fetchDevices({
    deviceType: deviceType.value,
    keyword: keyword.value || undefined,
    page: 1,
    pageSize: 50,
  })
  list.value = data.data.list
}

async function loadIssues() {
  const { data } = await fetchDeviceIssues()
  issues.value = data.data || []
}

async function showBindCode() {
  const { data } = await fetchCurrentBindCode()
  bindCode.value = data.data
  bindVisible.value = true
}

async function onRefreshBind() {
  const { data } = await refreshBindCode()
  bindCode.value = data.data
  ElMessage.success('已刷新绑定码')
}

function openIssue() {
  issueForm.deviceId = list.value[0]?.id
  issueForm.description = ''
  issueVisible.value = true
}

async function submitIssue() {
  if (!issueForm.deviceId || !issueForm.description) {
    ElMessage.warning('请填写设备与描述')
    return
  }
  await createDeviceIssue({ deviceId: issueForm.deviceId, description: issueForm.description })
  ElMessage.success('报障已提交')
  issueVisible.value = false
  await loadIssues()
}

async function onSyncFh2() {
  const { data } = await syncFh2Devices()
  ElMessage.success(`已同步 ${data.data.synced} 台司空设备`)
  await load()
}

onMounted(async () => {
  await Promise.all([load(), loadIssues()])
})
</script>

<style scoped lang="scss">
.toolbar {
  display: flex;
  gap: 10px;
  margin-bottom: 14px;
  flex-wrap: wrap;
}
.section {
  margin: 24px 0 12px;
  color: #e5eaf3;
  font-size: 16px;
  font-weight: 600;
}
</style>
