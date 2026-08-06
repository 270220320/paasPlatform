<template>
  <div>
    <el-table :data="roles" border>
      <el-table-column prop="name" label="角色" width="160" />
      <el-table-column prop="code" label="编码" width="140" />
      <el-table-column prop="description" label="说明" min-width="180" />
      <el-table-column label="锁定" width="90">
        <template #default="{ row }">{{ row.locked ? '是' : '否' }}</template>
      </el-table-column>
      <el-table-column label="操作" width="140">
        <template #default="{ row }">
          <el-button link type="primary" @click="openEdit(row)">{{ row.locked ? '查看' : '编辑权限' }}</el-button>
        </template>
      </el-table-column>
    </el-table>

    <el-dialog v-model="visible" :title="editing?.locked ? '查看角色权限' : '编辑角色权限'" width="640px">
      <el-form label-width="80px">
        <el-form-item label="名称">
          <el-input v-model="form.name" :disabled="!!editing?.locked" />
        </el-form-item>
        <el-form-item label="说明">
          <el-input v-model="form.description" :disabled="!!editing?.locked" />
        </el-form-item>
        <el-form-item label="权限">
          <el-checkbox-group v-model="form.permissionIds" :disabled="!!editing?.locked">
            <el-checkbox v-for="p in permissions" :key="p.id" :value="p.id">
              {{ p.name }}（{{ p.code }}）
            </el-checkbox>
          </el-checkbox-group>
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="visible = false">关闭</el-button>
        <el-button v-if="!editing?.locked" type="primary" :loading="saving" @click="save">保存</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<script setup lang="ts">
import { onMounted, reactive, ref } from 'vue'
import { ElMessage } from 'element-plus'
import {
  fetchPermissions,
  fetchRoleDetail,
  fetchRoles,
  updateRole,
  type PermissionVO,
  type RoleVO,
} from '@/api/iam'

const roles = ref<RoleVO[]>([])
const permissions = ref<PermissionVO[]>([])
const visible = ref(false)
const saving = ref(false)
const editing = ref<RoleVO | null>(null)
const form = reactive({ name: '', description: '', permissionIds: [] as number[] })

async function load() {
  const [roleRes, permRes] = await Promise.all([fetchRoles(), fetchPermissions()])
  roles.value = roleRes.data.data
  permissions.value = permRes.data.data
}

async function openEdit(row: RoleVO) {
  const { data } = await fetchRoleDetail(row.id)
  editing.value = data.data
  form.name = data.data.name
  form.description = data.data.description || ''
  form.permissionIds = [...(data.data.permissionIds || [])]
  visible.value = true
}

async function save() {
  if (!editing.value) return
  saving.value = true
  try {
    await updateRole(editing.value.id, {
      name: form.name,
      description: form.description,
      permissionIds: form.permissionIds,
    })
    ElMessage.success('角色权限已保存')
    visible.value = false
    await load()
  } finally {
    saving.value = false
  }
}

onMounted(load)
</script>
