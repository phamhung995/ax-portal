<script setup lang="ts">
import { computed, ref, watch } from 'vue'
import PrismDialog from '@/design-system/components/PrismDialog.vue'
import PrismButton from '@/design-system/components/PrismButton.vue'
import PrismInput from '@/design-system/components/PrismInput.vue'
import PrismCombobox from '@/design-system/components/PrismCombobox.vue'
import PrismEmptyState from '@/design-system/components/PrismEmptyState.vue'
import { adminUserApi } from '@/features/users/api'
import { toUserError } from '@/lib/api'
import { toastSuccess } from '@/lib/toast'
import { useAuthStore } from '@/stores/auth'
import { useUserListStore } from '@/features/users/store'
import type { UserDetailResponse } from '@/features/users/types'

const props = defineProps<{ open: boolean; userId: number | null }>()
const emit = defineEmits(['update:open', 'saved'])

const auth = useAuthStore()
const list = useUserListStore()

const loading = ref(false)
const loadError = ref('')
const detail = ref<UserDetailResponse | null>(null)

const userName = ref('')
const role = ref('')
const status = ref('')

const saving = ref(false)
const saveError = ref('')
const confirmCancel = ref(false)

const ROLE_OPTIONS = [
  { label: 'Normal User', value: 'NORMAL_USER' },
  { label: 'Admin User', value: 'ADMIN_USER' },
]
const STATUS_OPTIONS = [
  { label: 'Active', value: 'ACTIVE' },
  { label: 'Deactive', value: 'DEACTIVE' },
]

const isSelf = computed(() => detail.value != null && auth.user?.userId === detail.value.userId)

const hasChanges = computed(() => {
  const d = detail.value
  if (!d) return false
  return (
    userName.value.trim() !== d.userName ||
    role.value !== d.role ||
    status.value !== d.status
  )
})

async function load() {
  if (props.userId == null) return
  loading.value = true
  loadError.value = ''
  detail.value = null
  try {
    detail.value = await adminUserApi.detail(props.userId)
    userName.value = detail.value.userName
    role.value = detail.value.role
    status.value = detail.value.status
  } catch (err) {
    loadError.value = toUserError(err).message
  } finally {
    loading.value = false
  }
}

watch(
  () => props.open,
  (open) => {
    if (open) load()
  }
)

async function onSave() {
  if (!detail.value) return
  saveError.value = ''
  if (!userName.value.trim()) {
    saveError.value = 'User name cannot be empty.'
    return
  }
  saving.value = true
  try {
    await adminUserApi.update(props.userId!, {
      userName: userName.value.trim(),
      role: role.value,
      status: status.value,
      expectedVersion: detail.value.version,
    })
    list.markDirty()
    toastSuccess('User information has been updated successfully.')
    emit('saved')
    emit('update:open', false)
  } catch (err) {
    const apiError = toUserError(err)
    if (apiError.code === 'CONCURRENT_UPDATE_CONFLICT') {
      saveError.value =
        'Data has been changed by another process. Please reload the user information and try again.'
    } else {
      saveError.value = apiError.message
    }
  } finally {
    saving.value = false
  }
}

function requestClose() {
  if (saving.value) return
  if (hasChanges.value) {
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
    title="User Detail"
    size="lg"
    :close-on-overlay="false"
    :close-on-escape="false"
    @update:model-value="requestClose"
    @close="requestClose"
  >
    <div v-if="loading" class="ud-state">
      <p>Loading user information…</p>
    </div>

    <PrismEmptyState v-else-if="loadError" title="Unable to load user information.">
      <p class="ud-error-text">{{ loadError }}</p>
      <PrismButton variant="secondary" @click="load">Retry</PrismButton>
    </PrismEmptyState>

    <form v-else class="ud" @submit.prevent="onSave">
      <p v-if="saveError" class="p-field-error" role="alert">{{ saveError }}</p>

      <PrismInput v-model="userName" label="User Name" required maxlength="100" :disabled="saving" />
      <PrismInput :model-value="detail?.email ?? ''" label="Email" readonly hint="Email is immutable" />

      <div class="ud-orgs">
        <span class="ud-orgs-label">Organization</span>
        <p v-if="detail && detail.orgs.length === 0" class="ud-orgs-empty">—</p>
        <ul v-else class="ud-orgs-list">
          <li v-for="(o, i) in detail?.orgs ?? []" :key="i">
            {{ o.part }} &gt; {{ o.group }}
          </li>
        </ul>
      </div>

      <PrismCombobox
        v-model="role"
        label="Role"
        :options="ROLE_OPTIONS"
        :disabled="isSelf || saving"
        :hint="isSelf ? 'You cannot change your own role' : undefined"
      />
      <PrismCombobox
        v-model="status"
        label="Status"
        :options="STATUS_OPTIONS"
        :disabled="isSelf || saving"
        :hint="isSelf ? 'You cannot change your own status' : undefined"
      />
    </form>

    <template #footer>
      <PrismButton variant="secondary" :disabled="saving" @click="requestClose">Cancel</PrismButton>
      <PrismButton :loading="saving" :disabled="!hasChanges" @click="onSave">Save</PrismButton>
    </template>
  </PrismDialog>

  <PrismDialog
    :model-value="confirmCancel"
    title="Unsaved changes"
    size="sm"
    :close-on-overlay="false"
    @update:model-value="confirmCancel = $event"
  >
    <p class="ud-confirm-text">You have unsaved changes. Continue and discard changes?</p>
    <template #footer>
      <PrismButton variant="secondary" @click="confirmCancel = false">Stay</PrismButton>
      <PrismButton @click="confirmDiscard">Leave</PrismButton>
    </template>
  </PrismDialog>
</template>

<style scoped>
.ud {
  display: grid;
  gap: var(--ds-space-4);
}
.ud-state {
  padding: var(--ds-space-4) 0;
  color: var(--ds-color-text-muted);
}
.ud-error-text {
  color: var(--ds-color-danger);
  font-size: 13px;
}
.ud-confirm-text {
  color: var(--ds-color-text-primary);
}
.ud-orgs {
  display: grid;
  gap: var(--ds-space-2);
}
.ud-orgs-label {
  font-size: 14px;
  font-weight: 600;
  color: var(--ds-color-text-secondary);
}
.ud-orgs-empty {
  color: var(--ds-color-text-muted);
}
.ud-orgs-list {
  list-style: none;
  margin: 0;
  padding: 0;
  display: grid;
  gap: var(--ds-space-1);
}
</style>
