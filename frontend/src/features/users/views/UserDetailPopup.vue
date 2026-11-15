<script setup lang="ts">
import { computed, ref, watch } from 'vue'
import PrismDialog from '@/design-system/components/PrismDialog.vue'
import PrismButton from '@/design-system/components/PrismButton.vue'
import PrismInput from '@/design-system/components/PrismInput.vue'
import PrismCombobox from '@/design-system/components/PrismCombobox.vue'
import PrismEmptyState from '@/design-system/components/PrismEmptyState.vue'
import { adminUserApi } from '@/features/users/api'
import { toUserError } from '@/lib/api'
import { orgRowLabel } from '@/lib/types'
import { toastSuccess } from '@/lib/toast'
import { useAuthStore } from '@/stores/auth'
import { useI18nStore } from '@/stores/i18n'
import { useUserListStore } from '@/features/users/store'
import type { UserDetailResponse } from '@/features/users/types'

const props = defineProps<{ open: boolean; userId: number | null }>()
const emit = defineEmits(['update:open', 'saved'])

const auth = useAuthStore()
const { t } = useI18nStore()
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

const ROLE_OPTIONS = computed(() => [
  { label: t('app.common.normalUser'), value: 'NORMAL_USER' },
  { label: t('app.common.adminUser'), value: 'ADMIN_USER' },
])
const STATUS_OPTIONS = computed(() => [
  { label: t('app.common.active'), value: 'ACTIVE' },
  { label: t('app.common.deactive'), value: 'DEACTIVE' },
])

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
    saveError.value = t('app.users.nameRequired')
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
    toastSuccess(t('app.users.updatedToast'))
    emit('saved')
    emit('update:open', false)
  } catch (err) {
    const apiError = toUserError(err)
    if (apiError.code === 'CONCURRENT_UPDATE_CONFLICT') {
      saveError.value = t('app.users.versionConflict')
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
    :title="t('app.users.detailTitle')"
    size="lg"
    :close-on-overlay="false"
    :close-on-escape="false"
    @update:model-value="requestClose"
    @close="requestClose"
  >
    <div v-if="loading" class="ud-state">
      <p>{{ t('app.users.detailLoading') }}</p>
    </div>

    <PrismEmptyState v-else-if="loadError" :title="t('app.users.detailLoadError')">
      <p class="ud-error-text">{{ loadError }}</p>
      <PrismButton variant="secondary" @click="load">{{ t('app.common.retry') }}</PrismButton>
    </PrismEmptyState>

    <form v-else class="ud" @submit.prevent="onSave">
      <p v-if="saveError" class="p-field-error" role="alert">{{ saveError }}</p>

      <PrismInput v-model="userName" :label="t('app.users.colUserName')" required maxlength="100" :disabled="saving" />
      <PrismInput :model-value="detail?.email ?? ''" :label="t('app.users.colEmail')" readonly :hint="t('app.users.emailImmutable')" />

      <div class="ud-orgs">
        <span class="ud-orgs-label">{{ t('app.common.organization') }}</span>
        <p v-if="detail && detail.orgs.length === 0" class="ud-orgs-empty">—</p>
        <ul v-else class="ud-orgs-list">
          <li v-for="(o, i) in detail?.orgs ?? []" :key="i">
            {{ orgRowLabel(o) }}
          </li>
        </ul>
      </div>

      <PrismCombobox
        v-model="role"
        :label="t('app.common.role')"
        :options="ROLE_OPTIONS"
        :disabled="isSelf || saving"
        :hint="isSelf ? t('app.users.cannotChangeOwnRole') : undefined"
      />
      <PrismCombobox
        v-model="status"
        :label="t('app.common.status')"
        :options="STATUS_OPTIONS"
        :disabled="isSelf || saving"
        :hint="isSelf ? t('app.users.cannotChangeOwnStatus') : undefined"
      />
    </form>

    <template #footer>
      <PrismButton variant="secondary" :disabled="saving" @click="requestClose">{{ t('app.common.cancel') }}</PrismButton>
      <PrismButton :loading="saving" :disabled="!hasChanges" @click="onSave">{{ t('app.common.save') }}</PrismButton>
    </template>
  </PrismDialog>

  <PrismDialog
    :model-value="confirmCancel"
    :title="t('app.users.unsavedTitle')"
    size="sm"
    :close-on-overlay="false"
    @update:model-value="confirmCancel = $event"
  >
    <p class="ud-confirm-text">{{ t('app.users.unsavedDesc') }}</p>
    <template #footer>
      <PrismButton variant="secondary" @click="confirmCancel = false">{{ t('app.common.stay') }}</PrismButton>
      <PrismButton @click="confirmDiscard">{{ t('app.common.leave') }}</PrismButton>
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
