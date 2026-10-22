<script setup>
import { ref } from 'vue'
import { useFloatingPanel } from '../useFloatingPanel.js'
defineProps({ placement: { type: String, default: 'bottom-start' } })
const triggerRef = ref(null)
const { open, panelRef, panelPos, panelTo, inDialog, togglePanel, closePanel } = useFloatingPanel(triggerRef, { align: 'left' })
</script>
<template>
  <span class="p-popover" ref="triggerRef" @click="togglePanel">
    <slot name="trigger" />
    <Teleport to="body">
      <div v-if="open" ref="panelRef" role="dialog" aria-label="Popover" class="p-popover-panel p-glass" :style="{ top: `${panelPos.top}px`, left: `${panelPos.left}px` }">
        <slot />
      </div>
    </Teleport>
  </span>
</template>
