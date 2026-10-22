<script setup>
import { computed, nextTick, ref, watch } from 'vue'

/**
 * PrismOtpInput — 6-digit OTP input (Prism DS extension, built for the
 * User Management module per SRS).
 *
 * Behavior:
 * - 6 boxes, `inputmode=numeric`, mono font, 44px touch target.
 * - Auto-advance on digit entry; Backspace steps back; Arrow keys navigate.
 * - Paste distributes digits across boxes.
 * - States: default / error / expired (via `state` prop).
 * - a11y: role=group + per-box aria-label, aria-invalid on error.
 */
const props = defineProps({
  modelValue: { type: String, default: '' },
  length: { type: Number, default: 6 },
  disabled: Boolean,
  state: { type: String, default: 'default' }, // default | error | expired
  error: { type: String, default: '' },
  label: { type: String, default: 'Mã OTP' },
})
const emit = defineEmits(['update:modelValue'])

const boxes = ref([])
const value = computed({
  get: () => props.modelValue,
  set: (v) => emit('update:modelValue', v),
})

function digits() {
  return props.modelValue.replace(/\D/g, '').slice(0, props.length)
}

function focusBox(index) {
  nextTick(() => {
    const box = boxes.value[index]
    if (!box) return
    box.focus()
    box.select?.()
  })
}

function onInput(event, index) {
  const raw = event.target.value.replace(/\D/g, '')
  if (!raw) {
    event.target.value = ''
    return
  }
  const chars = digits().split('')
  for (const ch of raw) chars.push(ch)
  const next = chars.slice(0, props.length).join('')
  value.value = next
  event.target.value = ''
  const nextIndex = Math.min(index + raw.length, props.length - 1)
  if (nextIndex !== index) focusBox(nextIndex)
}

function onKeydown(event, index) {
  if (props.disabled) return
  if (event.key === 'Backspace') {
    event.preventDefault()
    const chars = digits().split('')
    if (chars[index]) {
      chars[index] = ''
      value.value = chars.join('')
    } else if (index > 0) {
      chars[index - 1] = ''
      value.value = chars.join('')
      focusBox(index - 1)
    }
    return
  }
  if (event.key === 'ArrowLeft' && index > 0) {
    event.preventDefault()
    focusBox(index - 1)
    return
  }
  if (event.key === 'ArrowRight' && index < props.length - 1) {
    event.preventDefault()
    focusBox(index + 1)
  }
}

function onPaste(event) {
  event.preventDefault()
  const pasted = (event.clipboardData?.getData('text') ?? '').replace(/\D/g, '')
  if (!pasted) return
  value.value = pasted.slice(0, props.length)
  focusBox(Math.min(pasted.length, props.length - 1))
}

watch(
  () => props.modelValue,
  (v) => {
    // Keep DOM boxes in sync when value is set externally (e.g. paste).
    nextTick(() => {
      boxes.value.forEach((box, i) => {
        if (box) box.value = v[i] ?? ''
      })
    })
  }
)
</script>

<template>
  <div class="p-otp" :class="{ 'is-error': state === 'error', 'is-expired': state === 'expired' }">
    <p v-if="error" class="p-field-error" role="alert">{{ error }}</p>
    <div role="group" :aria-label="label" class="p-otp-boxes" @paste="onPaste">
      <input
        v-for="i in length"
        :key="i"
        :ref="(el) => (boxes[i - 1] = el)"
        class="p-otp-box"
        type="text"
        inputmode="numeric"
        autocomplete="one-time-code"
        maxlength="1"
        :value="modelValue[i - 1] ?? ''"
        :disabled="disabled"
        :aria-label="`${label} số thứ ${i}`"
        :aria-invalid="state === 'error' || undefined"
        @input="onInput($event, i - 1)"
        @keydown="onKeydown($event, i - 1)"
      />
    </div>
  </div>
</template>

<style scoped>
.p-otp {
  display: grid;
  gap: var(--ds-space-2);
}
.p-otp-boxes {
  display: flex;
  gap: var(--ds-space-2);
}
.p-otp-box {
  width: var(--ds-control-height);
  height: var(--ds-control-height);
  text-align: center;
  font: 20px var(--ds-font-mono);
  color: var(--ds-color-text-primary);
  background: var(--ds-input-background);
  border: 1px solid var(--ds-color-border);
  border-radius: var(--ds-radius-sm);
  outline: none;
  transition: border-color var(--ds-duration-fast) var(--ds-ease),
    box-shadow var(--ds-duration-fast) var(--ds-ease);
}
.p-otp-box:hover:not(:disabled) {
  background: var(--ds-input-hover-background);
}
.p-otp-box:focus {
  border-color: var(--ds-color-accent);
  box-shadow: var(--ds-shadow-glow);
}
.p-otp-box:disabled {
  opacity: 0.5;
  cursor: not-allowed;
}
.is-error .p-otp-box {
  border-color: var(--ds-color-danger);
}
.is-expired .p-otp-box {
  border-color: var(--ds-color-warning);
  opacity: 0.7;
}
</style>
