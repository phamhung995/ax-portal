<script setup lang="ts">
import PrismEmptyState from '@/design-system/components/PrismEmptyState.vue'
import { useI18nStore } from '@/stores/i18n'

const { t } = useI18nStore()
</script>

<template>
  <PrismEmptyState
    :title="t('app.app.placeholderTitle')"
    :description="t('app.app.placeholderDesc')"
  />
</template>
