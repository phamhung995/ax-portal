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

const props = defineProps<{ open: boolean }>()
const emit = defineEmits(['update:open', 'saved'])

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
  if (!userName.value.trim()) return 'User name cannot be empty.'
  if (!email.value.trim()) return 'Email cannot be empty.'
  if (!/^[A-Za-z0-9._%+-]+@[A-Za-z0-9.-]+\.[A-Za-z]{2,}$/.test(email.value.trim())) {
    return 'Invalid Email format.'
  }
  if (email.value.trim().length > 254) return 'Email is too long (maximum 254 characters).'
  if (!password.value) return 'Password cannot be empty.'
  if (password.value !== confirmPassword.value) return 'Password confirmation mismatch.'
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
    toastSuccess('User created successfully.')
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
    title="Create User"
    size="lg"
    :close-on-overlay="false"
    :close-on-escape="false"
    @update:model-value="requestClose"
    @close="requestClose"
  >
    <form class="cu" @submit.prevent="onSave">
      <p v-if="error" class="p-field-error" role="alert">{{ error }}</p>

      <PrismInput v-model="userName" label="User Name" required maxlength="100" />
      <PrismInput
        v-model="email"
        label="Email"
        type="email"
        required
        maxlength="254"
        hint="Email address can not be changed after creating user"
      />
      <PrismPasswordInput
        v-model="password"
        label="Password"
        required
        :strength="strength"
        autocomplete="new-password"
      />
      <PasswordPolicy :password="password" />
      <PrismPasswordInput
        v-model="confirmPassword"
        label="Confirm Password"
        required
        autocomplete="new-password"
      />

      <PrismInput model-value="" label="Organization" readonly hint="Assigned by a batch process" />
      <PrismInput model-value="Normal User" label="Role" readonly />
      <PrismInput model-value="Active" label="Status" readonly />
    </form>

    <template #footer>
      <PrismButton variant="secondary" :disabled="loading" @click="requestClose">Cancel</PrismButton>
      <PrismButton :loading="loading" @click="onSave">Save</PrismButton>
    </template>
  </PrismDialog>

  <PrismDialog
    :model-value="confirmCancel"
    title="Discard changes?"
    size="sm"
    :close-on-overlay="false"
    @update:model-value="confirmCancel = $event"
  >
    <p class="cu-confirm-text">Are you sure you want to cancel? The information you entered will not be saved.</p>
    <template #footer>
      <PrismButton variant="secondary" @click="confirmCancel = false">Keep editing</PrismButton>
      <PrismButton @click="confirmDiscard">Discard</PrismButton>
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
