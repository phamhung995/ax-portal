<script setup lang="ts">
import { useId } from 'vue'
import PrismFormField from '@/design-system/components/PrismFormField.vue'

/**
 * Chip multi-select for Category / Process Stage form pickers
 * (mirrors the approved demo: clickable pill chips, violet for
 * categories, cyan for stages).
 */
const props = defineProps<{
  modelValue: number[]
  options: { id: number; name: string }[]
  tone: 'category' | 'stage'
  label: string
  hint?: string
  error?: string
  required?: boolean
  placeholder?: string
}>()
const emit = defineEmits(['update:modelValue'])

const id = useId()

function toggle(optionId: number) {
  const next = props.modelValue.includes(optionId)
    ? props.modelValue.filter((v) => v !== optionId)
    : [...props.modelValue, optionId]
  emit('update:modelValue', next)
}
</script>

<template>
  <PrismFormField :id="id" :label="label" :hint="hint" :error="error" :required="required">
    <div class="tcs" role="group" :aria-label="label">
      <button
        v-for="opt in options"
        :key="opt.id"
        type="button"
        class="tcs-chip"
        :class="[`tcs-chip--${tone}`, { 'is-active': modelValue.includes(opt.id) }]"
        :aria-pressed="modelValue.includes(opt.id)"
        @click="toggle(opt.id)"
      >
        {{ opt.name }}
      </button>
      <span v-if="!options.length" class="tcs-ph">{{ placeholder ?? 'No options available.' }}</span>
    </div>
  </PrismFormField>
</template>

<style scoped>
.tcs {
  display: flex;
  flex-wrap: wrap;
  gap: 8px;
  padding: 10px 12px;
  border: 1px solid var(--ds-color-border);
  border-radius: var(--ds-radius-sm);
  background: var(--ds-input-background);
  min-height: var(--ds-control-height);
  align-items: center;
}
.tcs-chip {
  padding: 4px 12px;
  border-radius: 20px;
  border: 1px solid var(--ds-color-border);
  background: none;
  color: var(--ds-color-text-secondary);
  font: inherit;
  font-size: 12px;
  cursor: pointer;
  transition: border-color var(--ds-duration-fast) var(--ds-ease),
    color var(--ds-duration-fast) var(--ds-ease),
    background var(--ds-duration-fast) var(--ds-ease);
}
.tcs-chip:hover {
  border-color: var(--ds-color-border-strong);
  color: var(--ds-color-text-primary);
}
.tcs-chip--stage.is-active {
  border-color: var(--ds-color-accent);
  color: var(--ds-color-accent);
  background: rgb(103 232 249 / .1);
}
.tcs-chip--category.is-active {
  border-color: var(--ds-color-violet-400, #a58aef);
  color: var(--ds-color-violet-400, #a58aef);
  background: rgb(165 138 239 / .1);
}
.tcs-ph {
  color: var(--ds-color-text-muted);
  font-size: 13px;
}
</style>
