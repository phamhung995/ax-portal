<script setup lang="ts">
import { useRouter } from 'vue-router'
import PrismButton from '@/design-system/components/PrismButton.vue'
import PrismEmptyState from '@/design-system/components/PrismEmptyState.vue'

const router = useRouter()
</script>

<template>
  <PrismEmptyState title="Access denied" description="You do not have permission to view this page.">
    <PrismButton @click="router.push('/home')">Back to Home</PrismButton>
  </PrismEmptyState>
</template>
