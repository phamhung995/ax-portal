<script setup lang="ts">
import { computed, ref, watch } from 'vue'
import PrismDialog from '@/design-system/components/PrismDialog.vue'
import PrismButton from '@/design-system/components/PrismButton.vue'
import PrismInput from '@/design-system/components/PrismInput.vue'
import PrismPasswordInput from '@/design-system/components/PrismPasswordInput.vue'
import PasswordPolicy from '@/features/auth/components/PasswordPolicy.vue'
import { adminUserApi } from '@/features/users/api'
import { toUserError } from '@/lib/api'
import { toastError, toastSuccess } from '@/lib/toast'
import { useI18nStore } from '@/stores/i18n'

const props = defineProps<{ open: boolean }>()
const emit = defineEmits(['update:open', 'saved'])
const { t } = useI18nStore()

const userName = ref('')
const email = ref('')
const password = ref('')
const confirmPassword = ref('')
const loading = ref(false)
const error = ref('')
const confirmCancel = ref(false)

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

function formDirty() {
  return (
    userName.value.trim() !== '' ||
    email.value.trim() !== '' ||
    password.value !== '' ||
    confirmPassword.value !== ''
  )
}

function resetForm() {
  userName.value = ''
  email.value = ''
  password.value = ''
  confirmPassword.value = ''
  error.value = ''
}

watch(
  () => props.open,
  (open) => {
    if (open) resetForm()
  }
)

function validate(): string {
  if (!userName.value.trim()) return t('app.users.nameRequired')
  if (!email.value.trim()) return t('app.users.emailRequired')
  if (!/^[A-Za-z0-9._%+-]+@[A-Za-z0-9.-]+\.[A-Za-z]{2,}$/.test(email.value.trim())) {
    return t('app.users.emailInvalid')
  }
  if (email.value.trim().length > 254) return t('app.users.emailTooLong')
  if (!password.value) return t('app.users.passwordRequired')
  if (password.value !== confirmPassword.value) return t('app.users.passwordMismatch')
  return ''
}

async function onSave() {
  error.value = ''
  const validationError = validate()
  if (validationError) {
    error.value = validationError
    return
  }
  loading.value = true
  try {
    await adminUserApi.create({
      userName: userName.value.trim(),
      email: email.value.trim(),
      password: password.value,
      confirmPassword: confirmPassword.value,
    })
    toastSuccess(t('app.users.createdToast'))
    emit('saved')
    emit('update:open', false)
  } catch (err) {
    error.value = toUserError(err).message
  } finally {
    loading.value = false
  }
}

function requestClose() {
  if (loading.value) return
  if (formDirty()) {
    confirmCancel.value = true
  } else {
    emit('update:open', false)
  }
}

function confirmDiscard() {
  confirmCancel.value = false
  emit('update:open', false)
}
</script>

<template>
  <PrismDialog
    :model-value="open"
    :title="t('app.users.createTitle')"
    size="lg"
    :close-on-overlay="false"
    :close-on-escape="false"
    @update:model-value="requestClose"
    @close="requestClose"
  >
    <form class="cu" @submit.prevent="onSave">
      <p v-if="error" class="p-field-error" role="alert">{{ error }}</p>

      <PrismInput v-model="userName" :label="t('app.users.colUserName')" required maxlength="100" />
      <PrismInput
        v-model="email"
        :label="t('app.users.colEmail')"
        type="email"
        required
        maxlength="254"
        :hint="t('app.users.emailHint')"
      />
      <PrismPasswordInput
        v-model="password"
        :label="t('app.auth.password')"
        required
        :strength="strength"
        autocomplete="new-password"
      />
      <PasswordPolicy :password="password" />
      <PrismPasswordInput
        v-model="confirmPassword"
        :label="t('app.auth.confirmPassword')"
        required
        autocomplete="new-password"
      />

      <PrismInput :model-value="t('app.common.normalUser')" :label="t('app.common.role')" readonly />
      <PrismInput :model-value="t('app.common.active')" :label="t('app.common.status')" readonly />
    </form>

    <template #footer>
      <PrismButton variant="secondary" :disabled="loading" @click="requestClose">{{ t('app.common.cancel') }}</PrismButton>
      <PrismButton :loading="loading" @click="onSave">{{ t('app.common.save') }}</PrismButton>
    </template>
  </PrismDialog>

  <PrismDialog
    :model-value="confirmCancel"
    :title="t('app.common.discardTitle')"
    size="sm"
    :close-on-overlay="false"
    @update:model-value="confirmCancel = $event"
  >
    <p class="cu-confirm-text">{{ t('app.common.discardDescInfo') }}</p>
    <template #footer>
      <PrismButton variant="secondary" @click="confirmCancel = false">{{ t('app.common.keepEditing') }}</PrismButton>
      <PrismButton @click="confirmDiscard">{{ t('app.common.discard') }}</PrismButton>
    </template>
  </PrismDialog>
</template>

<style scoped>
.cu {
  display: grid;
  gap: var(--ds-space-4);
}
.cu-confirm-text {
  color: var(--ds-color-text-primary);
}
</style>
