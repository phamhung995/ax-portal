<script setup>
import { computed, nextTick, ref, useId, watch } from 'vue'
import { useFloatingPanel } from '../useFloatingPanel.js'
const props = defineProps({ items: { type: Array, default: () => [] }, label: { type: String, default: 'Thực đơn' } })
const emit = defineEmits(['select'])
const triggerRef = ref(null)
const { open, panelRef, panelPos, panelTo, inDialog, togglePanel, closePanel } = useFloatingPanel(triggerRef, { matchWidth: true, align: 'right' })
const activeIndex = ref(0)
const menuId = useId()
const triggerId = useId()
const enabled = computed(() => props.items.filter(i => !i.disabled && i.type !== 'separator'))
watch(open, value => {
  if (value) {
    activeIndex.value = 0
    nextTick(() => focusItem(0))
  }
})
function focusItem(idx) {
  nextTick(() => panelRef.value?.querySelectorAll('[role=menuitem]')?.[idx]?.focus())
}
function onKeydown(event) {
  if (!open.value && (event.key === 'ArrowDown' || event.key === 'Enter' || event.key === ' ')) {
    event.preventDefault()
    togglePanel()
    return
  }
  if (!open.value) return
  if (event.key === 'ArrowDown' || event.key === 'ArrowUp') {
    event.preventDefault()
    const n = enabled.value.length
    if (!n) return
    activeIndex.value = (activeIndex.value + (event.key === 'ArrowDown' ? 1 : -1) + n) % n
    focusItem(activeIndex.value)
    return
  }
  if (event.key === 'Home' || event.key === 'End') {
    event.preventDefault()
    activeIndex.value = event.key === 'Home' ? 0 : enabled.value.length - 1
    focusItem(activeIndex.value)
    return
  }
  if (event.key === 'Escape') {
    closePanel()
    nextTick(() => triggerRef.value?.focus())
  }
}
function select(item) {
  closePanel()
  nextTick(() => triggerRef.value?.focus())
  emit('select', item)
}
</script>
<template>
  <div class="p-dropdown">
    <button ref="triggerRef" type="button" :id="triggerId" class="p-dropdown-trigger" :aria-haspopup="'menu'" :aria-expanded="open" :aria-controls="open ? menuId : undefined" @click="togglePanel" @keydown="onKeydown">
      <slot name="trigger">{{ label }}</slot>
      <svg width="14" height="14" viewBox="0 0 14 14" fill="none" aria-hidden="true"><path d="m3.5 5.5 3.5 3.5 3.5-3.5" stroke="currentColor" stroke-width="1.5" stroke-linecap="round" stroke-linejoin="round" /></svg>
    </button>
    <Teleport :to="panelTo">
      <div v-if="open" ref="panelRef" class="p-dropdown-panel p-glass" :style="{ top: `${panelPos.top}px`, left: `${panelPos.left}px` }">
        <ul :id="menuId" role="menu" :aria-label="label" class="p-dropdown-menu">
          <template v-for="(item, i) in items" :key="i">
            <li v-if="item.type === 'separator'" role="separator" class="p-dropdown-sep"></li>
            <li v-else role="none">
              <button type="button" role="menuitem" class="p-dropdown-item" :class="{ 'is-active': enabled[activeIndex] === item, 'is-disabled': item.disabled }" :disabled="item.disabled" @focus="activeIndex = enabled.indexOf(item)" @mouseenter="activeIndex = enabled.indexOf(item)" @click="select(item)">
                <slot :name="`icon-${item.value ?? i}`"><slot name="icon" /></slot>
                <span>{{ item.label }}</span>
                <span v-if="item.danger" class="p-dropdown-danger" aria-hidden="true"></span>
              </button>
            </li>
          </template>
        </ul>
      </div>
    </Teleport>
  </div>
</template>
