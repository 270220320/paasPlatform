<template>

  <div class="profile-page">

    <div class="profile-card">

      <h2 class="profile-title">{{ t('profile.title') }}</h2>

      <!-- 基本信息 -->
      <section class="block">

        <div class="block-title">{{ t('profile.basicInfo') }}</div>

        <div class="info-row">

          <span class="label">{{ t('profile.account') }}</span>

          <span class="value">{{ userStore.user?.account }}</span>

        </div>

        <div class="info-row">

          <span class="label">{{ t('profile.role') }}</span>

          <span class="value">{{ userStore.user?.roleName }}</span>

        </div>

      </section>

      <el-divider />

      <!-- 邮箱维护 -->
      <section class="block">

        <div class="block-title">{{ t('profile.emailSection') }}</div>

        <el-form ref="emailFormRef" :model="emailForm" :rules="emailRules" label-position="top" @submit.prevent>

          <el-form-item :label="t('profile.email')" prop="email">

            <el-input v-model="emailForm.email" :placeholder="t('profile.emailPlaceholder')" clearable />

          </el-form-item>

          <el-button type="primary" :loading="emailLoading" @click="onSaveEmail">

            {{ t('profile.saveEmail') }}

          </el-button>

        </el-form>

      </section>

      <el-divider />

      <!-- 修改密码 -->
      <section class="block">

        <div class="block-title">{{ t('profile.passwordSection') }}</div>

        <el-form ref="pwdFormRef" :model="pwdForm" :rules="pwdRules" label-position="top" @submit.prevent>

          <el-form-item :label="t('profile.oldPassword')" prop="oldPassword">

            <el-input v-model="pwdForm.oldPassword" type="password" show-password :placeholder="t('profile.oldPasswordPlaceholder')" />

          </el-form-item>

          <el-form-item :label="t('profile.newPassword')" prop="newPassword">

            <el-input v-model="pwdForm.newPassword" type="password" show-password :placeholder="t('profile.newPasswordPlaceholder')" />

          </el-form-item>

          <el-form-item :label="t('profile.confirmPassword')" prop="confirmPassword">

            <el-input v-model="pwdForm.confirmPassword" type="password" show-password :placeholder="t('profile.confirmPasswordPlaceholder')" />

          </el-form-item>

          <el-button type="primary" :loading="pwdLoading" @click="onChangePassword">

            {{ t('profile.submitPassword') }}

          </el-button>

        </el-form>

      </section>

    </div>

  </div>

</template>



<script setup lang="ts">

import { onMounted, reactive, ref } from 'vue'

import { useRouter } from 'vue-router'

import { useI18n } from 'vue-i18n'

import type { FormInstance, FormRules } from 'element-plus'

import { ElMessage } from 'element-plus'

import { changePasswordApi, meApi, updateEmailApi } from '@/api/auth'

import { useUserStore } from '@/stores/user'



const { t } = useI18n()

const router = useRouter()

const userStore = useUserStore()



const emailFormRef = ref<FormInstance>()

const pwdFormRef = ref<FormInstance>()

const emailLoading = ref(false)

const pwdLoading = ref(false)



const emailForm = reactive({ email: '' })

const pwdForm = reactive({

  oldPassword: '',

  newPassword: '',

  confirmPassword: '',

})



const emailRules: FormRules<typeof emailForm> = {

  email: [

    { required: true, message: t('profile.emailPlaceholder'), trigger: 'blur' },

    { type: 'email', message: t('profile.emailInvalid'), trigger: 'blur' },

  ],

}



const pwdRules: FormRules<typeof pwdForm> = {

  oldPassword: [{ required: true, message: t('profile.oldPasswordPlaceholder'), trigger: 'blur' }],

  newPassword: [

    { required: true, message: t('profile.newPasswordPlaceholder'), trigger: 'blur' },

    { min: 6, max: 64, message: t('profile.newPasswordPlaceholder'), trigger: 'blur' },

  ],

  confirmPassword: [

    { required: true, message: t('profile.confirmPasswordPlaceholder'), trigger: 'blur' },

    {

      validator: (_rule, value, callback) => {

        if (value !== pwdForm.newPassword) {

          callback(new Error(t('profile.passwordMismatch')))

        } else {

          callback()

        }

      },

      trigger: 'blur',

    },

  ],

}



async function onSaveEmail() {

  const valid = await emailFormRef.value?.validate().catch(() => false)

  if (!valid) return

  emailLoading.value = true

  try {

    const email = emailForm.email.trim()

    await updateEmailApi(email)

    if (userStore.user) {

      userStore.user.email = email

    }

    ElMessage.success(t('profile.emailSaveSuccess'))

  } finally {

    emailLoading.value = false

  }

}



async function onChangePassword() {

  const valid = await pwdFormRef.value?.validate().catch(() => false)

  if (!valid) return

  pwdLoading.value = true

  try {

    await changePasswordApi(pwdForm.oldPassword, pwdForm.newPassword)

    ElMessage.success(t('profile.passwordSaveSuccess'))

    userStore.clear()

    await router.replace({ name: 'login' })

  } finally {

    pwdLoading.value = false

  }

}



onMounted(async () => {

  try {

    const { data } = await meApi()

    userStore.setSession(userStore.token, data.data)

    emailForm.email = data.data.email || ''

  } catch {

    emailForm.email = userStore.user?.email || ''

  }

})

</script>



<style scoped lang="scss">

.profile-page {

  display: flex;

  justify-content: center;

  padding-top: 20px;

}



.profile-card {

  width: 560px;

  max-width: 100%;

  padding: 28px 32px;

  border-radius: 10px;

  background: #141414;

  border: 1px solid #414243;

  box-shadow: 0 8px 24px rgba(0, 0, 0, 0.45);

  color: #e5eaf3;

}



.profile-title {

  margin: 0 0 20px;

  font-size: 20px;

  color: #e5eaf3;

}



.block-title {

  font-size: 14px;

  font-weight: 600;

  color: #cfd3dc;

  margin-bottom: 16px;

}



.info-row {

  display: flex;

  align-items: baseline;

  gap: 12px;

  margin-bottom: 10px;

  font-size: 13px;



  .label {

    width: 56px;

    color: #8b919a;

    flex-shrink: 0;

  }



  .value {

    color: #cfd3dc;

  }

}



:deep(.el-divider) {

  border-color: #363637;

  margin: 20px 0;

}

</style>

