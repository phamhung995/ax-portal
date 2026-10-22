<script setup lang="ts">
import { ref } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import PrismButton from '@/design-system/components/PrismButton.vue'
import PrismInput from '@/design-system/components/PrismInput.vue'
import PrismPasswordInput from '@/design-system/components/PrismPasswordInput.vue'
import PrismAmbientBackground from '@/design-system/components/PrismAmbientBackground.vue'
import { authApi } from '@/features/auth/api'
import { toUserError } from '@/lib/api'
import { useAuthStore } from '@/stores/auth'

const auth = useAuthStore()
const route = useRoute()
const router = useRouter()

const email = ref('')
const password = ref('')
const loading = ref(false)
const error = ref('')

async function onSubmit() {
  error.value = ''
  if (!email.value || !password.value) {
    error.value = 'Please enter your email and password.'
    return
  }
  loading.value = true
  try {
    const data = await authApi.login(email.value, password.value)
    auth.setSession(data)
    router.push((route.query.redirect as string) || '/home')
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
      <h1 class="auth-brand">AI SKILL MANAGEMENT</h1>
      <p class="auth-subtitle">Sign in with your email and password</p>

      <p v-if="error" class="p-field-error" role="alert">{{ error }}</p>

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
        placeholder="Your password"
        autocomplete="current-password"
        required
      />

      <div class="auth-links">
        <RouterLink to="/forgot-password">Forgot password?</RouterLink>
      </div>

      <PrismButton type="submit" :loading="loading">Log In</PrismButton>

      <p class="auth-register">
        Don't have an account? <RouterLink to="/register">Register</RouterLink>
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
  position: relative;
}
.auth-card {
  width: min(420px, 100%);
  padding: var(--ds-space-8);
  display: grid;
  gap: var(--ds-space-4);
  position: relative;
  z-index: 1;
}
.auth-brand {
  margin: 0;
  font-size: 18px;
  font-weight: 700;
  letter-spacing: 0.12em;
  text-align: center;
}
.auth-subtitle {
  margin: -8px 0 0;
  color: var(--ds-color-text-muted);
  font-size: 13px;
}
.auth-links {
  display: flex;
  justify-content: flex-end;
  margin-top: -8px;
}
.auth-register {
  margin: 0;
  text-align: center;
  font-size: 13px;
  color: var(--ds-color-text-secondary);
}
</style>
