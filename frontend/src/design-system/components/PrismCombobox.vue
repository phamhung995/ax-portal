<script setup>
import { computed, nextTick, ref, useId, watch } from 'vue'
import PrismFormField from './PrismFormField.vue'
import { useFloatingPanel } from '../useFloatingPanel.js'
defineOptions({ inheritAttrs: false })
const props = defineProps({ modelValue: [String, Number, Array], options: { type: Array, default: () => [] }, id: String, label: String, hint: String, error: String, placeholder: String, disabled: Boolean, required: Boolean, multiple: Boolean, clearable: Boolean })
const emit = defineEmits(['update:modelValue'])
const generatedId = useId()
const listboxId = useId()
const controlRef = ref(null)
const { open, panelRef, panelPos, panelTo, inDialog, openPanel, closePanel, togglePanel } = useFloatingPanel(controlRef, { matchWidth: true })
const query = ref('')
const activeIndex = ref(0)
const normalize = value => String(value ?? '').normalize('NFD').replace(/[\u0300-\u036f]/g, '').toLowerCase()
const selected = computed(() => props.multiple ? (Array.isArray(props.modelValue) ? props.modelValue : []) : props.modelValue)
const selectedLabels = computed(() => {
  if (!props.multiple) {
    if (selected.value == null || selected.value === '') return ''
    const option = props.options.find(o => (o.value ?? o) === selected.value)
    return option ? (option.label ?? option) : String(selected.value)
  }
  return selected.value.map(v => { const o = props.options.find(x => (x.value ?? x) === v); return o ? (o.label ?? o) : String(v) })
})
// Chips shown inside the input (multiple mode), in selection order.
const filtered = computed(() => props.options.filter(o => normalize(o.label ?? o).includes(normalize(query.value))))
const activeOptionId = computed(() => open.value && filtered.value[activeIndex.value] ? `${listboxId}-opt-${activeIndex.value}` : undefined)
watch(activeIndex, () => {
  nextTick(() => panelRef.value?.querySelector('[data-active=true]')?.scrollIntoView({ block: 'nearest' }))
})
function toggle() {
  if (props.disabled) return
  if (open.value) { closePanel(); return }
  query.value = ''
  activeIndex.value = 0
  openPanel()
  nextTick(() => controlRef.value?.querySelector('input')?.focus())
}
function pick(option) {
  if (option.disabled) return
  const value = option.value ?? option
  if (props.multiple) {
    const next = selected.value.includes(value) ? selected.value.filter(v => v !== value) : [...selected.value, value]
    emit('update:modelValue', next)
    query.value = ''
    activeIndex.value = 0
  } else {
    emit('update:modelValue', value)
    closePanel()
    nextTick(() => controlRef.value?.querySelector('input')?.focus())
  }
}
function clear() {
  emit('update:modelValue', props.multiple ? [] : null)
  query.value = ''
}
function onInput(event) {
  query.value = event.target.value
  activeIndex.value = 0
  if (!open.value) openPanel()
}
function onKeydown(event) {
  if (event.key === 'Escape') { closePanel(); return }
  if (event.key === 'ArrowDown' || event.key === 'ArrowUp') {
    event.preventDefault()
    if (!open.value) { openPanel(); return }
    const n = filtered.value.length
    if (!n) return
    activeIndex.value = (activeIndex.value + (event.key === 'ArrowDown' ? 1 : -1) + n) % n
    return
  }
  if (event.key === 'Enter') {
    if (open.value && filtered.value[activeIndex.value]) { event.preventDefault(); pick(filtered.value[activeIndex.value]) }
    return
  }
  if (event.key === ' ' && props.multiple) {
    if (open.value && filtered.value[activeIndex.value]) { event.preventDefault(); pick(filtered.value[activeIndex.value]) }
    return
  }
  if (event.key === 'Backspace' && props.multiple && !query.value && selected.value.length) {
    emit('update:modelValue', selected.value.slice(0, -1))
  }
}
</script>
<template>
  <PrismFormField :id="id || generatedId" :label="label" :hint="hint" :error="error" :required="required" :disabled="disabled" v-slot="field">
    <div ref="controlRef" class="p-combobox" :class="{ 'is-open': open }">
      <div class="p-input-wrap" :class="{ 'is-multi': multiple, 'has-chips': multiple && selected.length }">
        <slot name="prefix" />
        <template v-if="multiple">
          <div class="p-multi-field">
            <span v-for="(chip, i) in selectedLabels" :key="i" class="p-chip">
              <span class="p-chip-text">{{ chip }}</span>
              <button type="button" class="p-chip-remove" :aria-label="'Gỡ ' + chip" @click.stop="emit('update:modelValue', selected.filter((_, j) => j !== i))">
                <svg width="10" height="10" viewBox="0 0 10 10" fill="none" aria-hidden="true"><path d="m2.5 2.5 5 5m0-5-5 5" stroke="currentColor" stroke-width="1.4" stroke-linecap="round" /></svg>
              </button>
            </span>
            <input v-bind="$attrs" :id="id || generatedId" type="text" role="combobox" :value="query" :placeholder="selected.length ? '' : placeholder" :disabled="disabled" :required="required" :aria-invalid="field.invalid || undefined" :aria-describedby="field.describedby" :aria-expanded="open" :aria-controls="listboxId" :aria-activedescendant="activeOptionId" autocomplete="off" @input="onInput" @keydown="onKeydown" @click="toggle" />
          </div>
        </template>
        <input v-else v-bind="$attrs" :id="id || generatedId" type="text" role="combobox" :value="open ? query : selectedLabels" :placeholder="placeholder" :disabled="disabled" :required="required" :aria-invalid="field.invalid || undefined" :aria-describedby="field.describedby" :aria-expanded="open" :aria-controls="listboxId" :aria-activedescendant="activeOptionId" :aria-autocomplete="'list'" autocomplete="off" @input="onInput" @keydown="onKeydown" @click="toggle" />
        <span v-if="clearable && (multiple ? selected.length : selected != null && selected !== '')" class="p-combobox-clear" role="button" tabindex="0" :aria-label="'Xóa giá trị' + (label ? ' ' + label : '')" @click.stop="clear" @keydown.enter.stop="clear" @keydown.space.prevent.stop="clear">
          <svg width="12" height="12" viewBox="0 0 12 12" fill="none" aria-hidden="true"><path d="m3 3 6 6M9 3 3 9" stroke="currentColor" stroke-width="1.5" stroke-linecap="round" /></svg>
        </span>
        <button type="button" class="p-combobox-caret" tabindex="-1" :aria-label="open ? 'Đóng danh sách' : 'Mở danh sách'" :disabled="disabled" @click.stop="toggle">
          <svg width="14" height="14" viewBox="0 0 14 14" fill="none" aria-hidden="true"><path d="m3.5 5.5 3.5 3.5 3.5-3.5" stroke="currentColor" stroke-width="1.5" stroke-linecap="round" stroke-linejoin="round" /></svg>
        </button>
      </div>
    </div>
    <Teleport :to="panelTo">
      <div v-if="open" ref="panelRef" class="p-combobox-panel p-glass" :style="{ top: `${panelPos.top}px`, left: `${panelPos.left}px` }">
        <ul :id="listboxId" role="listbox" :aria-multiselectable="multiple || undefined" class="p-combobox-list">
          <li v-for="(option, i) in filtered" :key="i" :id="`${listboxId}-opt-${i}`" role="option" :data-active="i === activeIndex || undefined" :aria-selected="(multiple ? selected : [selected]).includes(option.value ?? option)" :class="{ 'is-active': i === activeIndex, 'is-disabled': option.disabled }" @mouseenter="activeIndex = i" @click="pick(option)">
            <span v-if="multiple" class="p-combobox-checkbox" :class="{ 'is-checked': selected.includes(option.value ?? option) }" :aria-hidden="true">
              <svg v-if="selected.includes(option.value ?? option)" width="10" height="10" viewBox="0 0 10 10" fill="none"><path d="m1.8 5.2 2.2 2.2 4.2-4.6" stroke="currentColor" stroke-width="1.6" stroke-linecap="round" stroke-linejoin="round" /></svg>
            </span>
            {{ option.label ?? option }}
          </li>
          <li v-if="!filtered.length" class="p-combobox-empty">Không có kết quả</li>
        </ul>
      </div>
    </Teleport>
  </PrismFormField>
</template>
