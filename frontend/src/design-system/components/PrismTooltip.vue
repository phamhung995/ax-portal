<script setup>
import { computed, nextTick, onBeforeUnmount, ref, useId } from 'vue'
const props = defineProps({ content: { type: String, default: '' }, placement: { type: String, default: 'top' }, delay: { type: Number, default: 120 }, disabled: Boolean })
const triggerRef = ref(null)
const tipRef = ref(null)
const visible = ref(false)
const position = ref({ top: 0, left: 0 })
const id = useId()
let timer = null
const transform = computed(() => ({ top: 'translate(-50%, -100%)', bottom: 'translate(-50%, 0)', left: 'translate(-100%, -50%)', right: 'translate(0, -50%)' }[props.placement] || 'translate(-50%, -100%)'))
function place() {
  const trigger = triggerRef.value
  const tip = tipRef.value
  if (!trigger || !tip) return
  const r = trigger.getBoundingClientRect()
  const margin = 8
  let top, left
  if (props.placement === 'top') { top = r.top - margin; left = r.left + r.width / 2 }
  else if (props.placement === 'bottom') { top = r.bottom + margin; left = r.left + r.width / 2 }
  else if (props.placement === 'left') { top = r.top + r.height / 2; left = r.left - margin }
  else { top = r.top + r.height / 2; left = r.right + margin }
  const tw = tip.offsetWidth
  const th = tip.offsetHeight
  if (props.placement === 'top' || props.placement === 'bottom') {
    left = Math.min(Math.max(8, left - tw / 2), window.innerWidth - tw - 8)
    if (props.placement === 'top' && top - th < 8) top = r.bottom + margin
    if (props.placement === 'bottom' && top + th > window.innerHeight - 8) top = r.top - th - margin
  } else {
    top = Math.min(Math.max(8, top - th / 2), window.innerHeight - th - 8)
    if (props.placement === 'left' && left - tw < 8) left = r.right + margin
    if (props.placement === 'right' && left + tw > window.innerWidth - 8) left = r.left - tw - margin
  }
  position.value = { top, left }
}
function show() {
  if (props.disabled || visible.value) return
  clearTimeout(timer)
  timer = setTimeout(() => {
    visible.value = true
    nextTick(place)
  }, props.delay)
}
function hide() {
  clearTimeout(timer)
  visible.value = false
}
function onKeydown(event) { if (event.key === 'Escape') hide() }
onBeforeUnmount(() => { clearTimeout(timer) })
</script>
<template>
  <span class="p-tooltip" @mouseenter="show" @mouseleave="hide" @focusin="show" @focusout="hide">
    <span ref="triggerRef" @keydown="onKeydown"><slot /></span>
    <Teleport to="body">
      <span v-if="visible && (content || $slots.default)" ref="tipRef" :id="id" role="tooltip" class="p-tooltip-bubble p-glass" :style="{ top: `${position.top}px`, left: `${position.left}px`, transform }">
        <slot name="content">{{ content }}</slot>
      </span>
    </Teleport>
  </span>
</template>
