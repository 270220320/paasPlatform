<template>
  <div>
    <div class="toolbar">
      <el-button v-if="canWrite" type="primary" @click="openCreate">添加人员</el-button>
      <el-select v-model="query.roleId" clearable placeholder="全部角色" style="width: 160px" @change="load">
        <el-option v-for="r in roles" :key="r.id" :label="r.name" :value="r.id" />
      </el-select>
      <el-input v-model="query.keyword" clearable placeholder="账号/名称" style="width: 220px" @keyup.enter="load" />
      <el-button @click="load">刷新</el-button>
    </div>

    <el-table :data="list" border stripe>
      <el-table-column prop="account" label="账号" min-width="180" />
      <el-table-column prop="displayName" label="人员名称" min-width="140" />
      <el-table-column prop="roleName" label="角色" width="140" />
      <el-table-column prop="joinMethod" label="加入方式" width="140" />
      <el-table-column prop="joinedAt" label="加入时间" width="180" />
      <el-table-column prop="status" label="状态" width="100">
        <template #default="{ row }">
          <el-tag :type="row.status === 'active' ? 'success' : 'info'" size="small">{{ statusText(row.status) }}</el-tag>
        </template>
      </el-table-column>
      <el-table-column v-if="canWrite" label="操作" width="180" fixed="right">
        <template #default="{ row }">
          <el-button link type="primary" @click="openEdit(row)">编辑</el-button>
          <el-button link type="danger" @click="onDelete(row)">删除</el-button>
        </template>
      </el-table-column>
    </el-table>

    <div class="pager">
      <el-pagination
        v-model:current-page="query.page"
        v-model:page-size="query.pageSize"
        layout="total, prev, pager, next"
        :total="total"
        @current-change="load"
      />
    </div>

    <el-dialog v-model="createVisible" title="添加人员" width="480px">
      <el-form :model="createForm" label-width="90px">
        <el-form-item label="账号"><el-input v-model="createForm.account" /></el-form-item>
        <el-form-item label="名称"><el-input v-model="createForm.displayName" /></el-form-item>
        <el-form-item label="角色">
          <el-select v-model="createForm.roleId" style="width: 100%">
            <el-option v-for="r in roles" :key="r.id" :label="r.name" :value="r.id" />
          </el-select>
        </el-form-item>
        <el-form-item label="初始密码"><el-input v-model="createForm.password" type="password" show-password /></el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="createVisible = false">取消</el-button>
        <el-button type="primary" :loading="saving" @click="submitCreate">确定</el-button>
      </template>
    </el-dialog>

    <el-dialog v-model="editVisible" title="编辑人员角色" width="480px">
      <el-form :model="editForm" label-width="90px">
        <el-form-item label="账号"><el-input v-model="editForm.account" disabled /></el-form-item>
        <el-form-item label="名称"><el-input v-model="editForm.displayName" /></el-form-item>
        <el-form-item label="角色">
          <el-select v-model="editForm.roleId" style="width: 100%">
            <el-option v-for="r in roles" :key="r.id" :label="r.name" :value="r.id" />
          </el-select>
        </el-form-item>
        <el-form-item label="状态">
          <el-select v-model="editForm.status" style="width: 100%">
            <el-option label="正常" value="active" />
            <el-option label="禁用" value="disabled" />
          </el-select>
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="editVisible = false">取消</el-button>
        <el-button type="primary" :loading="saving" @click="submitEdit">保存</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<script setup lang="ts">
import { computed, onMounted, reactive, ref } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import {
  createMember,
  deleteMember,
  fetchMembers,
  fetchRoleOptions,
  updateMember,
  type MemberVO,
  type RoleVO,
} from '@/api/iam'
import { useUserStore } from '@/stores/user'

const userStore = useUserStore()
const canWrite = computed(() => !!userStore.user?.permissions?.includes('member:write'))

const list = ref<MemberVO[]>([])
const total = ref(0)
const roles = ref<RoleVO[]>([])
const query = reactive({ roleId: undefined as number | undefined, keyword: '', page: 1, pageSize: 50 })
const createVisible = ref(false)
const editVisible = ref(false)
const saving = ref(false)
const createForm = reactive({ account: '', displayName: '', roleId: 4, password: '123456' })
const editForm = reactive({ id: 0, account: '', displayName: '', roleId: 4, status: 'active' })

function statusText(status: string) {
  return status === 'active' ? '正常' : status === 'disabled' ? '禁用' : status
}

async function load() {
  const { data } = await fetchMembers({ ...query })
  list.value = data.data.list
  total.value = data.data.total
}

async function loadRoles() {
  const { data } = await fetchRoleOptions()
  roles.value = data.data
}

function openCreate() {
  createForm.account = ''
  createForm.displayName = ''
  createForm.roleId = roles.value.find((r) => r.code === 'operator')?.id || roles.value[0]?.id || 4
  createForm.password = '123456'
  createVisible.value = true
}

function openEdit(row: MemberVO) {
  editForm.id = row.id
  editForm.account = row.account
  editForm.displayName = row.displayName
  editForm.roleId = row.roleId
  editForm.status = row.status
  editVisible.value = true
}

async function submitCreate() {
  saving.value = true
  try {
    await createMember({ ...createForm })
    ElMessage.success('已添加')
    createVisible.value = false
    await load()
  } finally {
    saving.value = false
  }
}

async function submitEdit() {
  saving.value = true
  try {
    await updateMember(editForm.id, {
      displayName: editForm.displayName,
      roleId: editForm.roleId,
      status: editForm.status,
    })
    ElMessage.success('已保存')
    editVisible.value = false
    await load()
  } finally {
    saving.value = false
  }
}

async function onDelete(row: MemberVO) {
  await ElMessageBox.confirm(`确定删除人员 ${row.displayName} 吗？`, '确认删除', { type: 'warning' })
  await deleteMember(row.id)
  ElMessage.success('已删除')
  await load()
}

onMounted(async () => {
  await loadRoles()
  await load()
})
</script>

<style scoped lang="scss">
.toolbar {
  display: flex;
  gap: 10px;
  margin-bottom: 14px;
  flex-wrap: wrap;
}
.pager {
  margin-top: 14px;
  display: flex;
  justify-content: flex-end;
}
</style>
