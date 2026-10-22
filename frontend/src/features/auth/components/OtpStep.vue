<script setup lang="ts">
import { computed, onBeforeUnmount, ref, watch } from 'vue'
import PrismButton from '@/design-system/components/PrismButton.vue'
import PrismOtpInput from '@/design-system/components/PrismOtpInput.vue'

const props = defineProps({
  modelValue: { type: String, default: '' },
  otpState: { type: String, default: 'default' },
  otpError: { type: String, default: '' },
  loading: { type: Boolean, default: false },
  /** Epoch ms when the OTP was issued; countdown = 5 minutes from this. */
  issuedAt: { type: Number, default: 0 },
  verifyLabel: { type: String, default: 'Verify' },
})
const emit = defineEmits(['update:modelValue', 'verify', 'resend'])

const EXPIRY_MS = 5 * 60 * 1000
const now = ref(Date.now())
let timer: ReturnType<typeof setInterval> | null = null

const remainingMs = computed(() => Math.max(0, props.issuedAt + EXPIRY_MS - now.value))
const expired = computed(() => props.issuedAt > 0 && remainingMs.value === 0)
const countdown = computed(() => {
  const total = Math.ceil(remainingMs.value / 1000)
  const mm = String(Math.floor(total / 60)).padStart(2, '0')
  const ss = String(total % 60).padStart(2, '0')
  return `${mm}:${ss}`
})

function startTimer() {
  stopTimer()
  now.value = Date.now()
  timer = setInterval(() => {
    now.value = Date.now()
    if (expired.value) stopTimer()
  }, 1000)
}
function stopTimer() {
  if (timer) {
    clearInterval(timer)
    timer = null
  }
}

watch(
  () => props.issuedAt,
  (value) => {
    if (value > 0) startTimer()
    else stopTimer()
  },
  { immediate: true }
)
onBeforeUnmount(stopTimer)
</script>

<template>
  <div class="otp-step">
    <PrismOtpInput
      :model-value="modelValue"
      :state="expired ? 'expired' : otpState"
      :error="expired ? 'The code has expired. Please request a new one.' : otpError"
      :disabled="loading || expired"
      label="OTP"
      @update:model-value="emit('update:modelValue', $event)"
    />

    <p class="otp-timer" :class="{ 'is-expired': expired }" role="timer">
      {{ expired ? 'Code expired' : `Code expires in ${countdown}` }}
    </p>

    <div class="otp-actions">
      <PrismButton type="button" :loading="loading" :disabled="expired" @click="emit('verify')">
        {{ verifyLabel }}
      </PrismButton>
      <PrismButton variant="secondary" type="button" :disabled="loading" @click="emit('resend')">
        Resend OTP
      </PrismButton>
    </div>
  </div>
</template>

<style scoped>
.otp-step {
  display: grid;
  gap: var(--ds-space-3);
  justify-items: center;
}
.otp-timer {
  margin: 0;
  font: 13px var(--ds-font-mono);
  color: var(--ds-color-text-secondary);
}
.otp-timer.is-expired {
  color: var(--ds-color-warning);
}
.otp-actions {
  display: flex;
  gap: var(--ds-space-3);
  width: 100%;
}
.otp-actions .p-button {
  flex: 1;
}
</style>
