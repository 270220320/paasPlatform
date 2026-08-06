<template>
  <el-select
    :model-value="current"
    size="small"
    class="locale-switcher"
    @change="onChange"
  >
    <el-option
      v-for="item in localeOptions"
      :key="item.value"
      :label="item.label"
      :value="item.value"
    />
  </el-select>
</template>

<script setup lang="ts">
import { computed } from 'vue'
import { useI18n } from 'vue-i18n'
import { localeOptions, setLocale, type AppLocale } from '@/i18n'

const { locale } = useI18n()
const current = computed(() => locale.value as AppLocale)

function onChange(value: string) {
  setLocale(value as AppLocale)
}
</script>

<style scoped>
.locale-switcher {
  width: 110px;
}
</style>
