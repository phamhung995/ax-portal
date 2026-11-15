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
import { useI18nStore } from '@/stores/i18n'
import { landingPath } from '@/router'

const auth = useAuthStore()
const i18n = useI18nStore()
const { t } = i18n
const route = useRoute()
const router = useRouter()

const email = ref('')
const password = ref('')
const loading = ref(false)
const error = ref('')

async function onSubmit() {
  error.value = ''
  if (!email.value || !password.value) {
    error.value = t('app.auth.loginError')
    return
  }
  loading.value = true
  try {
    const data = await authApi.login(email.value, password.value)
    auth.setSession(data)
    router.push((route.query.redirect as string) || landingPath())
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
      <h1 class="auth-brand">{{ t('app.auth.loginTitle') }}</h1>
      <p class="auth-subtitle">{{ t('app.auth.loginSubtitle') }}</p>

      <p v-if="error" class="p-field-error" role="alert">{{ error }}</p>

      <PrismInput
        v-model="email"
        :label="t('app.common.email')"
        type="email"
        :placeholder="t('app.common.emailPh')"
        autocomplete="email"
        required
      />
      <PrismPasswordInput
        v-model="password"
        :label="t('app.auth.password')"
        :placeholder="t('app.auth.passwordPh')"
        autocomplete="current-password"
        required
      />

      <div class="auth-links">
        <RouterLink to="/forgot-password">{{ t('app.auth.forgotPassword') }}</RouterLink>
      </div>

      <PrismButton type="submit" :loading="loading">{{ t('app.auth.loginBtn') }}</PrismButton>

      <p class="auth-register">
        {{ t('app.auth.noAccount') }} <RouterLink to="/register">{{ t('app.auth.register') }}</RouterLink>
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
