<template>

  <div class="login-page">

    <div class="login-card">

      <div class="brand">

        <div class="mark">UP</div>

        <div>

          <h1>UAVPlatform</h1>

        </div>

      </div>

      <el-form ref="formRef" :model="form" :rules="rules" label-position="top" @submit.prevent>

        <el-form-item :label="t('login.account')" prop="account">

          <el-input v-model="form.account" :placeholder="t('login.accountPlaceholder')" clearable />

        </el-form-item>

        <el-form-item :label="t('login.password')" prop="password">

          <el-input

            v-model="form.password"

            type="password"

            :placeholder="t('login.passwordPlaceholder')"

            show-password

            @keyup.enter="onSubmit"

          />

        </el-form-item>

        <el-button type="primary" class="w-full" :loading="loading" @click="onSubmit">

          {{ t('login.submit') }}

        </el-button>

      </el-form>

    </div>

  </div>

</template>



<script setup lang="ts">

import { computed, reactive, ref } from 'vue'

import { useRoute, useRouter } from 'vue-router'

import { useI18n } from 'vue-i18n'

import type { FormInstance, FormRules } from 'element-plus'

import { ElMessage } from 'element-plus'

import { loginApi } from '@/api/auth'

import { useUserStore } from '@/stores/user'



const { t } = useI18n()

const router = useRouter()

const route = useRoute()

const userStore = useUserStore()



const formRef = ref<FormInstance>()

const loading = ref(false)

const form = reactive({

  account: 'admin@aiflow.local',

  password: '123456',

})



const rules = computed<FormRules<typeof form>>(() => ({

  account: [{ required: true, message: t('login.accountPlaceholder'), trigger: 'blur' }],

  password: [{ required: true, message: t('login.passwordPlaceholder'), trigger: 'blur' }],

}))



async function onSubmit() {

  const valid = await formRef.value?.validate().catch(() => false)

  if (!valid) return

  loading.value = true

  try {

    const { data } = await loginApi(form.account, form.password)

    userStore.setSession(data.data.accessToken, data.data.user)

    ElMessage.success(t('login.success'))

    const redirect = typeof route.query.redirect === 'string' ? route.query.redirect : '/'

    await router.replace(redirect)

  } finally {

    loading.value = false

  }

}

</script>



<style scoped lang="scss">

.login-page {

  min-height: 100vh;

  display: flex;

  align-items: center;

  justify-content: center;

  background:

    radial-gradient(ellipse at 20% 20%, rgba(64, 158, 255, 0.18), transparent 45%),

    radial-gradient(ellipse at 80% 80%, rgba(103, 194, 58, 0.1), transparent 40%),

    #0a0a0a;

}



.login-card {

  width: 400px;

  padding: 32px;

  border-radius: 8px;

  background: #141414;

  border: 1px solid #414243;

  box-shadow: 0 8px 24px rgba(0, 0, 0, 0.45);

}



.brand {

  display: flex;

  gap: 12px;

  align-items: center;

  margin-bottom: 24px;



  h1 {

    margin: 0;

    font-size: 22px;

  }



  p {

    margin: 4px 0 0;

    color: #a3a6ad;

    font-size: 13px;

  }

}



.mark {

  width: 40px;

  height: 40px;

  border-radius: 8px;

  display: grid;

  place-items: center;

  font-weight: 700;

  color: #fff;

  background: linear-gradient(135deg, #409eff, #337ecc);

}



.w-full {

  width: 100%;

  margin-top: 8px;

}

</style>

