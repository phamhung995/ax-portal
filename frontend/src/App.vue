<script setup lang="ts">
import { computed, onMounted, ref } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import PrismToast from '@/design-system/components/PrismToast.vue'
import AppShell from '@/components/layout/AppShell.vue'
import { registerToast } from '@/lib/toast'

const route = useRoute()
const router = useRouter()
const toastRef = ref<InstanceType<typeof PrismToast> | null>(null)

// Keep the shell hidden until the initial navigation is confirmed by the
// (async) auth guard. Without this, AppShell renders on the start location
// before the guard decides the redirect — causing a "home flash" before login.
const ready = ref(false)

onMounted(() => {
  registerToast((options) => toastRef.value?.push(options))
  router.isReady().then(() => {
    ready.value = true
  })
})

const isGuestRoute = computed(() => !!route.meta.requiresGuest)
</script>

<template>
  <PrismToast ref="toastRef" />
  <AppShell v-if="ready && !isGuestRoute" />
  <RouterView v-else-if="ready" />
</template>
