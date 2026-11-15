<script setup lang="ts">
import { useRouter } from 'vue-router'
import PrismButton from '@/design-system/components/PrismButton.vue'
import PrismEmptyState from '@/design-system/components/PrismEmptyState.vue'
import { useI18nStore } from '@/stores/i18n'

const router = useRouter()
const { t } = useI18nStore()
</script>

<template>
  <PrismEmptyState :title="t('app.app.accessDeniedTitle')" :description="t('app.app.accessDeniedDesc')">
    <PrismButton @click="router.push('/home')">{{ t('app.app.backToHome') }}</PrismButton>
  </PrismEmptyState>
</template>
