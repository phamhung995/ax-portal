<script setup lang="ts">
import { ref } from 'vue'
import { useRouter } from 'vue-router'
import PrismButton from '@/design-system/components/PrismButton.vue'
import PrismInput from '@/design-system/components/PrismInput.vue'
import PrismAmbientBackground from '@/design-system/components/PrismAmbientBackground.vue'
import OtpStep from '@/features/auth/components/OtpStep.vue'
import { authApi } from '@/features/auth/api'
import { toUserError } from '@/lib/api'
import { useI18nStore } from '@/stores/i18n'

const i18n = useI18nStore()
const { t } = i18n
const router = useRouter()
const step = ref<'email' | 'otp'>('email')
const email = ref('')
const otp = ref('')
const otpState = ref<'default' | 'error' | 'expired'>('default')
const otpError = ref('')
const issuedAt = ref(0)
const loading = ref(false)
const error = ref('')

async function onSendOtp() {
  error.value = ''
  if (!email.value) {
    error.value = t('app.auth.emailRequired')
    return
  }
  loading.value = true
  try {
    // Anti-enumeration: backend responds the same for unknown emails.
    await authApi.requestForgotOtp(email.value)
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
    await authApi.verifyForgotOtp(email.value, otp.value)
    router.push({ name: 'reset-password', query: { email: email.value } })
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
    await authApi.requestForgotOtp(email.value)
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
    <form v-if="step === 'email'" class="auth-card p-glass" @submit.prevent="onSendOtp">
      <h1 class="auth-title">{{ t('app.auth.forgotTitle') }}</h1>
      <p class="auth-subtitle">{{ t('app.auth.forgotSubtitle') }}</p>

      <p v-if="error" class="p-field-error" role="alert">{{ error }}</p>

      <PrismInput
        v-model="email"
        :label="t('app.common.email')"
        type="email"
        :placeholder="t('app.common.emailPh')"
        autocomplete="email"
        required
      />

      <PrismButton type="submit" :loading="loading">{{ t('app.auth.sendOtp') }}</PrismButton>

      <p class="auth-register">
        <RouterLink to="/login">{{ t('app.auth.backToLogin') }}</RouterLink>
      </p>
    </form>

    <form v-else class="auth-card p-glass" @submit.prevent="onVerify">
      <h1 class="auth-title">{{ t('app.auth.verifyTitle') }}</h1>
      <p class="auth-subtitle">
        {{ t('app.auth.forgotVerifyDesc', { email }) }}
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
        <button type="button" class="linklike" @click="step = 'email'">{{ t('app.auth.back') }}</button>
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
