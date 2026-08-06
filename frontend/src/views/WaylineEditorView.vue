<script setup lang="ts">
import { onMounted } from 'vue'
import { useRoute, useRouter } from 'vue-router'

/** 兼容旧链接：编辑器已内嵌到航线库页 */
const route = useRoute()
const router = useRouter()

onMounted(() => {
  router.replace({
    name: 'waylines',
    query: {
      mode: route.query.mode,
      waylineId: route.query.waylineId,
      name: route.query.name,
      folderId: route.query.folderId,
    },
  })
})
</script>

<template>
  <div />
</template>
