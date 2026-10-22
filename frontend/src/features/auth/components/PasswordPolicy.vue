<script setup lang="ts">
import { computed } from 'vue'
import { CircleCheck, Circle } from 'lucide-vue-next'

const props = defineProps({
  password: { type: String, default: '' },
})

const rules = computed(() => [
  { label: 'Minimum 8 characters', ok: props.password.length >= 8 },
  { label: 'Uppercase letter', ok: /[A-Z]/.test(props.password) },
  { label: 'Lowercase letter', ok: /[a-z]/.test(props.password) },
  { label: 'Number', ok: /[0-9]/.test(props.password) },
  { label: 'Special character', ok: /[^A-Za-z0-9]/.test(props.password) },
])
</script>

<template>
  <ul class="policy" aria-label="Password requirements">
    <li v-for="rule in rules" :key="rule.label" class="policy-item" :class="{ 'is-ok': rule.ok }">
      <component :is="rule.ok ? CircleCheck : Circle" :size="14" aria-hidden="true" />
      <span>{{ rule.label }}</span>
    </li>
  </ul>
</template>

<style scoped>
.policy {
  list-style: none;
  margin: 0;
  padding: 0;
  display: grid;
  gap: var(--ds-space-1);
}
.policy-item {
  display: flex;
  align-items: center;
  gap: var(--ds-space-2);
  font-size: 12px;
  color: var(--ds-color-text-muted);
}
.policy-item.is-ok {
  color: var(--ds-color-success);
}
</style>
