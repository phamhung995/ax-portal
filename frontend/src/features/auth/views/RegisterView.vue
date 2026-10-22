<script setup lang="ts">
import { computed, ref } from 'vue'
import PrismButton from '@/design-system/components/PrismButton.vue'
import PrismInput from '@/design-system/components/PrismInput.vue'
import PrismPasswordInput from '@/design-system/components/PrismPasswordInput.vue'
import PrismAmbientBackground from '@/design-system/components/PrismAmbientBackground.vue'
import OtpStep from '@/features/auth/components/OtpStep.vue'
import PasswordPolicy from '@/features/auth/components/PasswordPolicy.vue'
import { authApi } from '@/features/auth/api'
import { toUserError } from '@/lib/api'
import { toastSuccess } from '@/lib/toast'

const step = ref<'form' | 'otp'>('form')
const name = ref('')
const email = ref('')
const password = ref('')
const confirmPassword = ref('')
const otp = ref('')
const otpState = ref<'default' | 'error' | 'expired'>('default')
const otpError = ref('')
const issuedAt = ref(0)
const loading = ref(false)
const error = ref('')

const strength = computed(() => {
  const p = password.value
  if (!p) return 0
  let score = 0
  if (p.length >= 8) score++
  if (/[A-Z]/.test(p)) score++
  if (/[0-9]/.test(p)) score++
  if (/[^A-Za-z0-9]/.test(p)) score++
  return score
})

async function onSendOtp() {
  error.value = ''
  if (!name.value.trim()) return (error.value = 'Full name is required.')
  if (!email.value) return (error.value = 'Email is required.')
  if (password.value !== confirmPassword.value) return (error.value = 'Passwords do not match.')
  loading.value = true
  try {
    await authApi.requestRegisterOtp(email.value)
    otp.value = ''
    otpState.value = 'default'
    otpError.value = ''
    issuedAt.value = Date.now()
    step.value = 'otp'
  } catch (err) {
    error.value = toUserError(err).message
  } finally {
    loading.value = false
  }
}

async function onVerify() {
  error.value = ''
  otpState.value = 'default'
  otpError.value = ''
  loading.value = true
  try {
    await authApi.verifyRegisterOtp(email.value, otp.value)
    await authApi.register({
      name: name.value.trim(),
      email: email.value,
      password: password.value,
      confirmPassword: confirmPassword.value,
    })
    toastSuccess('Registration successful. Please sign in.')
    window.location.assign(`${import.meta.env.BASE_URL}login`)
  } catch (err) {
    const apiError = toUserError(err)
    if (apiError.code === 'OTP_INVALID') {
      otpState.value = 'error'
      otpError.value = apiError.message
    } else if (apiError.code === 'OTP_EXPIRED') {
      otpState.value = 'expired'
      otpError.value = apiError.message
    } else {
      error.value = apiError.message
    }
  } finally {
    loading.value = false
  }
}

async function onResend() {
  error.value = ''
  try {
    await authApi.requestRegisterOtp(email.value)
    otp.value = ''
    otpState.value = 'default'
    otpError.value = ''
    issuedAt.value = Date.now()
  } catch (err) {
    error.value = toUserError(err).message
  }
}
</script>

<template>
  <div class="auth-page">
    <PrismAmbientBackground />
    <form v-if="step === 'form'" class="auth-card p-glass" @submit.prevent="onSendOtp">
      <h1 class="auth-title">Create Account</h1>
      <p class="auth-subtitle">Register to access AI Skill Management</p>

      <p v-if="error" class="p-field-error" role="alert">{{ error }}</p>

      <PrismInput
        v-model="name"
        label="User Name"
        placeholder="Your full name"
        maxlength="100"
        required
      />
      <PrismInput
        v-model="email"
        label="Email"
        type="email"
        placeholder="you@company.com"
        autocomplete="email"
        required
      />
      <PrismPasswordInput
        v-model="password"
        label="Password"
        placeholder="At least 8 characters"
        :strength="strength"
        autocomplete="new-password"
        required
      />
      <PasswordPolicy :password="password" />
      <PrismPasswordInput
        v-model="confirmPassword"
        label="Confirm Password"
        placeholder="Repeat your password"
        autocomplete="new-password"
        required
      />

      <PrismButton type="submit" :loading="loading">Send OTP</PrismButton>

      <p class="auth-register">
        Already have an account? <RouterLink to="/login">Log In</RouterLink>
      </p>
    </form>

    <form v-else class="auth-card p-glass" @submit.prevent="onVerify">
      <h1 class="auth-title">Verify Your Email</h1>
      <p class="auth-subtitle">
        Enter the 6-digit verification code sent to <strong>{{ email }}</strong>
      </p>

      <p v-if="error" class="p-field-error" role="alert">{{ error }}</p>

      <OtpStep
        v-model="otp"
        :otp-state="otpState"
        :otp-error="otpError"
        :loading="loading"
        :issued-at="issuedAt"
        @verify="onVerify"
        @resend="onResend"
      />

      <p class="auth-register">
        <button type="button" class="linklike" @click="step = 'form'">Back</button>
      </p>
    </form>
  </div>
</template>

<style scoped>
.auth-page {
  min-height: 100vh;
  display: grid;
  place-items: center;
  padding: var(--ds-space-6);
}
.auth-card {
  width: min(440px, 100%);
  padding: var(--ds-space-8);
  display: grid;
  gap: var(--ds-space-4);
  position: relative;
  z-index: 1;
}
.auth-title {
  margin: 0;
  font-size: 24px;
  font-weight: 700;
}
.auth-subtitle {
  margin: -8px 0 0;
  color: var(--ds-color-text-muted);
  font-size: 13px;
}
.auth-register {
  margin: 0;
  text-align: center;
  font-size: 13px;
  color: var(--ds-color-text-secondary);
}
.linklike {
  border: none;
  background: none;
  color: var(--ds-color-accent);
  cursor: pointer;
  font: inherit;
  padding: 0;
}
.linklike:hover {
  text-decoration: underline;
}
</style>
