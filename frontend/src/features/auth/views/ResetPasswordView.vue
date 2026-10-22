<script setup lang="ts">
import { computed, ref } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import PrismButton from '@/design-system/components/PrismButton.vue'
import PrismPasswordInput from '@/design-system/components/PrismPasswordInput.vue'
import PrismAmbientBackground from '@/design-system/components/PrismAmbientBackground.vue'
import PasswordPolicy from '@/features/auth/components/PasswordPolicy.vue'
import { authApi } from '@/features/auth/api'
import { toUserError } from '@/lib/api'
import { toastSuccess } from '@/lib/toast'

const route = useRoute()
const router = useRouter()

const email = computed(() => (route.query.email as string) ?? '')
const newPassword = ref('')
const confirmPassword = ref('')
const loading = ref(false)
const error = ref('')

const strength = computed(() => {
  const p = newPassword.value
  if (!p) return 0
  let score = 0
  if (p.length >= 8) score++
  if (/[A-Z]/.test(p)) score++
  if (/[0-9]/.test(p)) score++
  if (/[^A-Za-z0-9]/.test(p)) score++
  return score
})

async function onSubmit() {
  error.value = ''
  if (!email.value) {
    error.value = 'Missing email. Please restart the reset flow.'
    return
  }
  if (newPassword.value !== confirmPassword.value) {
    error.value = 'Passwords do not match.'
    return
  }
  loading.value = true
  try {
    await authApi.resetPassword({
      email: email.value,
      newPassword: newPassword.value,
      confirmPassword: confirmPassword.value,
    })
    toastSuccess('Password has been reset. Please sign in.')
    router.push('/login')
  } catch (err) {
    error.value = toUserError(err).message
  } finally {
    loading.value = false
  }
}
</script>

<template>
  <div class="auth-page">
    <PrismAmbientBackground />
    <form class="auth-card p-glass" @submit.prevent="onSubmit">
      <h1 class="auth-title">Choose a new password</h1>
      <p v-if="email" class="auth-subtitle">For {{ email }}</p>

      <p v-if="error" class="p-field-error" role="alert">{{ error }}</p>

      <PrismPasswordInput
        v-model="newPassword"
        label="New password"
        placeholder="At least 8 characters"
        :strength="strength"
        autocomplete="new-password"
        required
      />
      <PasswordPolicy :password="newPassword" />
      <PrismPasswordInput
        v-model="confirmPassword"
        label="Confirm new password"
        placeholder="Repeat your new password"
        autocomplete="new-password"
        required
      />

      <PrismButton type="submit" :loading="loading">Reset password</PrismButton>

      <p class="auth-register">
        <RouterLink to="/login">Back to sign in</RouterLink>
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
  width: min(420px, 100%);
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
</style>
