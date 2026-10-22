<script setup>
import { computed, ref, useId, watch } from 'vue'
import PrismFormField from './PrismFormField.vue'
import { useFloatingPanel } from '../useFloatingPanel.js'
const props = defineProps({ modelValue: { type: String, default: '#67e8f9' }, id: String, label: String, hint: String, error: String, disabled: Boolean, required: Boolean, presets: { type: Array, default: () => ['#67e8f9', '#a58aef', '#9ce8d0', '#f3d28c', '#ffacc4', '#38bdf8', '#f472b6', '#facc15'] } })
const emit = defineEmits(['update:modelValue'])
const generatedId = useId()
const controlRef = ref(null)
const { open, panelRef, panelPos, panelTo, inDialog, togglePanel, closePanel } = useFloatingPanel(controlRef, { matchWidth: true })
const hexDraft = ref('')

function hexToRgb(hex) {
  const m = /^#?([0-9a-f]{6})$/i.exec(hex.trim())
  if (!m) return null
  const n = parseInt(m[1], 16)
  return { r: (n >> 16) & 255, g: (n >> 8) & 255, b: n & 255 }
}
function rgbToHex(r, g, b) {
  return '#' + [r, g, b].map(v => Math.round(Math.min(255, Math.max(0, v))).toString(16).padStart(2, '0')).join('')
}
function rgbToHsl(r, g, b) {
  r /= 255; g /= 255; b /= 255
  const max = Math.max(r, g, b), min = Math.min(r, g, b)
  let h = 0, s = 0
  const l = (max + min) / 2
  if (max !== min) {
    const d = max - min
    s = l > 0.5 ? d / (2 - max - min) : d / (max + min)
    if (max === r) h = (g - b) / d + (g < b ? 6 : 0)
    else if (max === g) h = (b - r) / d + 2
    else h = (r - g) / d + 4
    h /= 6
  }
  return { h: Math.round(h * 360), s, l }
}
function hslToHex(h, s, l) {
  h /= 360
  const q = l < 0.5 ? l * (1 + s) : l + s - l * s
  const p = 2 * l - q
  const conv = t => {
    if (t < 0) t += 1
    if (t > 1) t -= 1
    if (t < 1 / 6) return p + (q - p) * 6 * t
    if (t < 1 / 2) return q
    if (t < 2 / 3) return p + (q - p) * (2 / 3 - t) * 6
    return p
  }
  return rgbToHex(conv(h + 1 / 3) * 255, conv(h) * 255, conv(h - 1 / 3) * 255)
}
const current = computed(() => {
  const rgb = hexToRgb(props.modelValue) || { r: 103, g: 232, b: 249 }
  return { ...rgb, ...rgbToHsl(rgb.r, rgb.g, rgb.b) }
})
function commit(value) {
  const rgb = hexToRgb(value)
  if (rgb) emit('update:modelValue', rgbToHex(rgb.r, rgb.g, rgb.b))
}
function onHue(event) {
  emit('update:modelValue', hslToHex(Number(event.target.value), current.value.s, current.value.l))
}
function onSaturation(event) {
  emit('update:modelValue', hslToHex(current.value.h, Number(event.target.value) / 100, current.value.l))
}
function onLightness(event) {
  emit('update:modelValue', hslToHex(current.value.h, current.value.s, Number(event.target.value) / 100))
}
function onPreset(hex) {
  commit(hex)
  closePanel()
}
// Hex input: draft riêng để gõ được; sync lại khi panel mở hoặc model đổi từ slider/preset
function onHexInput(event) { hexDraft.value = event.target.value }
function onHexCommit(event) {
  commit(hexDraft.value)
  hexDraft.value = props.modelValue
  event.target.value = props.modelValue
}
function onKeydown(event) {
  if (event.key === 'Escape') closePanel()
}
watch([open, () => props.modelValue], () => { hexDraft.value = props.modelValue })
</script>
<template>
  <PrismFormField :id="id || generatedId" :label="label" :hint="hint" :error="error" :required="required" :disabled="disabled" v-slot="field">
    <div ref="controlRef" class="p-colorpicker" :class="{ 'is-open': open }">
      <button type="button" class="p-colorpicker-trigger p-input-wrap" :disabled="disabled" :aria-haspopup="'dialog'" :aria-expanded="open" :aria-label="'Chọn màu' + (label ? ' ' + label : '')" @click="togglePanel" @keydown="onKeydown">
        <span class="p-colorpicker-swatch" :style="{ background: modelValue }" aria-hidden="true"></span>
        <span class="p-colorpicker-hex">{{ modelValue }}</span>
        <svg width="14" height="14" viewBox="0 0 14 14" fill="none" aria-hidden="true"><path d="m3.5 5.5 3.5 3.5 3.5-3.5" stroke="currentColor" stroke-width="1.5" stroke-linecap="round" stroke-linejoin="round" /></svg>
      </button>
    </div>
    <Teleport :to="panelTo">
      <div v-if="open" ref="panelRef" class="p-colorpicker-panel p-glass" :class="{ 'p-colorpicker-panel--in-dialog': inDialog }" :style="{ top: `${panelPos.top}px`, left: `${panelPos.left}px` }">
        <div class="p-colorpicker-preview" :style="{ background: modelValue }" aria-hidden="true"></div>
        <div class="p-colorpicker-presets" role="group" :aria-label="'Màu có sẵn'">
          <button v-for="hex in presets" :key="hex" type="button" class="p-colorpicker-preset" :class="{ 'is-active': hex.toLowerCase() === modelValue.toLowerCase() }" :style="{ background: hex }" :aria-label="'Màu ' + hex" :aria-pressed="hex.toLowerCase() === modelValue.toLowerCase()" @click="onPreset(hex)"></button>
        </div>
        <label class="p-colorpicker-hue">
          <span>Hue</span>
          <input type="range" min="0" max="360" :value="current.h" :disabled="disabled" :aria-label="'Thang màu (hue) ' + current.h + ' độ'" @input="onHue" />
        </label>
        <label class="p-colorpicker-hue">
          <span>Saturation</span>
          <input type="range" min="0" max="100" :value="Math.round(current.s * 100)" :disabled="disabled" :aria-label="'Độ bão hòa (saturation) ' + Math.round(current.s * 100) + '%'" @input="onSaturation" />
        </label>
        <label class="p-colorpicker-hue">
          <span>Lightness</span>
          <input type="range" min="0" max="100" :value="Math.round(current.l * 100)" :disabled="disabled" :aria-label="'Độ sáng (lightness) ' + Math.round(current.l * 100) + '%'" @input="onLightness" />
        </label>
        <label class="p-colorpicker-hex">
          <span>Hex</span>
          <input type="text" :value="hexDraft" spellcheck="false" autocomplete="off" :aria-label="'Mã màu hex' + (label ? ' ' + label : '')" @input="onHexInput" @blur="onHexCommit" @keydown.enter="onHexCommit" />
        </label>
      </div>
    </Teleport>
  </PrismFormField>
</template>
