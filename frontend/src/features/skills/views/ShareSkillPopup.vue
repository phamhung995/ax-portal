<script setup lang="ts">
import { computed, ref, watch } from 'vue'
import { User as UserIcon, FolderKanban, X } from 'lucide-vue-next'
import PrismDialog from '@/design-system/components/PrismDialog.vue'
import PrismButton from '@/design-system/components/PrismButton.vue'
import PrismInput from '@/design-system/components/PrismInput.vue'
import PrismCombobox from '@/design-system/components/PrismCombobox.vue'
import PrismTable from '@/design-system/components/PrismTable.vue'
import PrismBadge from '@/design-system/components/PrismBadge.vue'
import PrismEmptyState from '@/design-system/components/PrismEmptyState.vue'
import { skillApi } from '@/features/skills/api'
import { toUserError } from '@/lib/api'
import { toast, toastError, toastSuccess } from '@/lib/toast'
import { useI18nStore } from '@/stores/i18n'
import type { SearchUserItem, SkillShareItem } from '@/features/skills/types'

const { t } = useI18nStore()

const props = withDefaults(defineProps<{ open: boolean; skillId: number | null; skillStatus?: string | null; adminReviewed?: boolean }>(), {
  skillStatus: null,
  adminReviewed: false,
})
const emit = defineEmits(['update:open'])

const shareTab = ref<'user' | 'project'>('user')
const shares = ref<SkillShareItem[]>([])
const loading = ref(false)

// ── Share user (multi-select) ────────────────────────────────────────────────
const userKeyword = ref('')
const userResults = ref<SearchUserItem[]>([])
const userSearching = ref(false)
const selectedUsers = ref<SearchUserItem[]>([])
let userSearchTimer: ReturnType<typeof setTimeout> | null = null

// ── Share project (multi-select) ─────────────────────────────────────────────
const projectOptions = ref<{ label: string; value: string }[]>([])
const selectedProjects = ref<string[]>([])

const shareableProjects = computed(() =>
  projectOptions.value.filter((o) => !shares.value.some((s) => s.shareType === 'PROJECT' && s.projectKey === o.value))
)

async function loadAll() {
  if (!props.skillId) return
  loading.value = true
  try {
    const [shareList, projects] = await Promise.all([
      skillApi.shares(props.skillId),
      skillApi.shareProjectOptions(props.skillId),
    ])
    shares.value = shareList
    projectOptions.value = projects.map((p) => ({ label: p, value: p }))
  } catch (err) {
    toastError(toUserError(err).message)
  } finally {
    loading.value = false
  }
}

watch(() => props.open, (open) => {
  if (open) {
    shareTab.value = 'user'
    userKeyword.value = ''
    userResults.value = []
    selectedUsers.value = []
    selectedProjects.value = []
    checkedShareIds.value = []
    shareConfirmOpen.value = false
    pendingShareAction = null
    loadAll()
  }
})

function onUserKeyword() {
  if (userSearchTimer) clearTimeout(userSearchTimer)
  userSearchTimer = setTimeout(() => searchUsers(), 300)
}

async function searchUsers() {
  const kw = userKeyword.value.trim()
  if (!kw) {
    userResults.value = []
    return
  }
  userSearching.value = true
  try {
    const r = await skillApi.searchUser(kw, 1, 20)
    const selectedIds = new Set(selectedUsers.value.map((u) => u.id))
    userResults.value = r.items.filter(
      (u) => !shares.value.some((s) => s.shareType === 'USER' && s.userId === u.id)
        && !selectedIds.has(u.id)
    )
  } catch {
    userResults.value = []
  } finally {
    userSearching.value = false
  }
}

function toggleUser(user: SearchUserItem) {
  const exists = selectedUsers.value.some((u) => u.id === user.id)
  selectedUsers.value = exists
    ? selectedUsers.value.filter((u) => u.id !== user.id)
    : [...selectedUsers.value, user]
}

function removeSelectedUser(id: number) {
  selectedUsers.value = selectedUsers.value.filter((u) => u.id !== id)
}

const busy = ref(false)

// ── Confirm before a share that moves the skill to PENDING_APPROVAL ─────────
// External shares (users/projects outside the creator's projects) put an
// APPROVED skill into PENDING_APPROVAL — the user must confirm first.
// A reviewed skill (admin approved it at least once) never goes pending,
// so no confirm is needed (V91).
const shareConfirmOpen = ref(false)
let pendingShareAction: (() => Promise<void>) | null = null

function requestShare(action: () => Promise<void>) {
  if (props.skillStatus === 'APPROVED' && !props.adminReviewed) {
    pendingShareAction = action
    shareConfirmOpen.value = true
  } else {
    void action()
  }
}

async function confirmShare() {
  shareConfirmOpen.value = false
  const action = pendingShareAction
  pendingShareAction = null
  if (action) await action()
}

async function doShareUser() {
  if (!props.skillId || !selectedUsers.value.length) return
  busy.value = true
  try {
    const result = await skillApi.shareUser(props.skillId, selectedUsers.value.map((u) => u.id))
    toastSuccess(t('app.skill.shareSuccessUser', { count: selectedUsers.value.length }))
    if (result.pendingCount > 0) {
      toast({ title: '', message: t('app.skill.sharePendingToast', { count: result.pendingCount }), tone: 'warning' })
    }
    selectedUsers.value = []
    userKeyword.value = ''
    userResults.value = []
    await loadAll()
  } catch (err) {
    toastError(toUserError(err).message)
  } finally {
    busy.value = false
  }
}

async function doShareProject() {
  if (!props.skillId || !selectedProjects.value.length) return
  busy.value = true
  try {
    const result = await skillApi.shareProject(props.skillId, selectedProjects.value)
    toastSuccess(t('app.skill.shareSuccessProject', { count: selectedProjects.value.length }))
    if (result.pendingCount > 0) {
      toast({ title: '', message: t('app.skill.sharePendingToast', { count: result.pendingCount }), tone: 'warning' })
    }
    selectedProjects.value = []
    await loadAll()
  } catch (err) {
    toastError(toUserError(err).message)
  } finally {
    busy.value = false
  }
}

// ── Batch delete (checkbox selection) ───────────────────────────────────────
const checkedShareIds = ref<number[]>([])

watch(shares, () => {
  // Drop checks for shares that no longer exist (after a delete).
  const ids = new Set(shares.value.map((s) => s.id))
  checkedShareIds.value = checkedShareIds.value.filter((id) => ids.has(id))
})

function toggleCheck(id: number) {
  const i = checkedShareIds.value.indexOf(id)
  checkedShareIds.value = i >= 0
    ? checkedShareIds.value.filter((x) => x !== id)
    : [...checkedShareIds.value, id]
}

const allChecked = computed(() =>
  shares.value.length > 0 && shares.value.every((s) => checkedShareIds.value.includes(s.id))
)

function toggleCheckAll() {
  checkedShareIds.value = allChecked.value ? [] : shares.value.map((s) => s.id)
}

async function doDeleteChecked() {
  if (!props.skillId || !checkedShareIds.value.length) return
  busy.value = true
  try {
    await skillApi.removeShares(props.skillId, checkedShareIds.value)
    toastSuccess(t('app.skill.shareRemoved'))
    checkedShareIds.value = []
    await loadAll()
  } catch (err) {
    toastError(toUserError(err).message)
  } finally {
    busy.value = false
  }
}

function close() {
  emit('update:open', false)
}

const sharedColumns = computed(() => [
  { key: 'check', label: '' },
  { key: 'type', label: t('app.skill.shareTypeCol') },
  { key: 'target', label: t('app.skill.shareSharedWith') },
  { key: 'status', label: t('app.common.status') },
])

const sharedRows = computed(() =>
  shares.value.map((s) => ({
    id: s.id,
    checked: checkedShareIds.value.includes(s.id),
    type: s.shareType === 'USER' ? t('app.skill.shareTypeUser') : t('app.skill.shareTypeProject'),
    target: s.shareType === 'USER' ? (s.userName ?? `User #${s.userId}`) : (s.projectKey ?? '—'),
    share: s,
  }))
)

function shareStatusBadge(s: SkillShareItem): { label: string; tone: 'warning' | 'danger' | 'success' } | null {
  if (s.approvalStatus === 'PENDING') return { label: t('app.skill.sharePendingBadge'), tone: 'warning' }
  if (s.approvalStatus === 'REJECTED') return { label: t('app.skill.shareRejectedBadge'), tone: 'danger' }
  return { label: t('app.common.statusApproved'), tone: 'success' }
}
</script>

<template>
  <PrismDialog
    :model-value="open"
    :title="t('app.skill.shareTitle')"
    size="lg"
    dialog-class="skill-share-dialog"
    :close-on-overlay="true"
    :close-on-escape="true"
    @update:model-value="(v) => !v && close()"
    @close="close"
  >
    <div class="ss">
      <!-- Tabs -->
      <div class="ss-tabs" role="tablist">
        <button
          type="button"
          role="tab"
          class="ss-tab"
          :class="{ 'is-active': shareTab === 'user' }"
          :aria-selected="shareTab === 'user'"
          @click="shareTab = 'user'"
        >
          <UserIcon :size="14" aria-hidden="true" /> {{ t('app.skill.shareTabUser') }}
        </button>
        <button
          type="button"
          role="tab"
          class="ss-tab"
          :class="{ 'is-active': shareTab === 'project' }"
          :aria-selected="shareTab === 'project'"
          @click="shareTab = 'project'"
        >
          <FolderKanban :size="14" aria-hidden="true" /> {{ t('app.skill.shareTabProject') }}
        </button>
      </div>

      <!-- Share user tab -->
      <div v-if="shareTab === 'user'" class="ss-body">
        <p class="ss-hint">{{ t('app.skill.shareUserHint') }}</p>
        <PrismInput
          :model-value="userKeyword"
          :placeholder="t('app.skill.shareUserSearch')"
          @update:model-value="userKeyword = String($event); onUserKeyword()"
        />
        <div v-if="userSearching" class="ss-status">{{ t('app.skill.shareUserSearching') }}</div>
        <div v-else-if="userResults.length" class="ss-user-list">
          <button
            v-for="u in userResults"
            :key="u.id"
            type="button"
            class="ss-user"
            :class="{ 'is-selected': selectedUsers.some((s) => s.id === u.id) }"
            @click="toggleUser(u)"
          >
            <span class="ss-user-name">{{ u.name }}</span>
            <span class="ss-user-knox">{{ u.knoxId }}</span>
          </button>
        </div>
        <div v-else-if="userKeyword.trim()" class="ss-status">{{ t('app.skill.shareUserNotFound') }}</div>
        <div v-if="selectedUsers.length" class="ss-chips">
          <span v-for="u in selectedUsers" :key="u.id" class="ss-chip">
            {{ u.name }}
            <button
              type="button"
              class="ss-chip-remove"
              :aria-label="'Remove ' + u.name"
              @click="removeSelectedUser(u.id)"
            >
              <X :size="12" aria-hidden="true" />
            </button>
          </span>
        </div>
        <div class="ss-share-row">
          <PrismButton
            type="button"
            :loading="busy"
            :disabled="!selectedUsers.length"
            @click="requestShare(doShareUser)"
          >
            {{ t('app.skill.shareWithUsers', { count: selectedUsers.length || 1 }) }}
          </PrismButton>
        </div>
      </div>

      <!-- Share project tab -->
      <div v-else class="ss-body">
        <p class="ss-hint">
          {{ t('app.skill.shareProjectHint') }}
        </p>
        <PrismCombobox
          v-model="selectedProjects"
          :placeholder="t('app.skill.shareProjectPlaceholder')"
          clearable
          multiple
          :options="shareableProjects"
        />
        <div class="ss-share-row">
          <PrismButton
            type="button"
            :loading="busy"
            :disabled="!selectedProjects.length"
            @click="requestShare(doShareProject)"
          >
            {{ t('app.skill.shareWithProjects', { count: selectedProjects.length || 1 }) }}
          </PrismButton>
        </div>
      </div>

      <!-- Current shares -->
      <div class="ss-current">
        <h4 class="ss-current-title">{{ t('app.skill.shareCurrentShares') }}</h4>
        <PrismEmptyState
          v-if="!loading && !shares.length"
          :title="t('app.skill.shareNoShares')"
          :description="t('app.skill.shareNoSharesDesc')"
        />
        <PrismTable
          v-else
          :columns="sharedColumns"
          :rows="sharedRows"
          row-key="id"
          :loading="loading"
          :caption="`Total: ${shares.length}`"
          :empty-text="''"
        >
          <template #header-check>
            <input
              type="checkbox"
              :checked="allChecked"
              :aria-label="t('app.skill.shareSelectAll')"
              @change="toggleCheckAll()"
            />
          </template>
          <template #cell-check="{ row }">
            <input
              type="checkbox"
              :checked="(row as { checked: boolean }).checked"
              :aria-label="t('app.skill.shareSelectRow')"
              @change="toggleCheck((row as { id: number }).id)"
            />
          </template>
          <template #cell-status="{ row }">
            <PrismBadge
              v-if="(row as { share: SkillShareItem }).share.approvalStatus !== 'APPROVED'"
              :tone="shareStatusBadge((row as { share: SkillShareItem }).share)!.tone"
            >
              {{ shareStatusBadge((row as { share: SkillShareItem }).share)!.label }}
            </PrismBadge>
          </template>
        </PrismTable>
      </div>
    </div>
    <template #footer>
      <PrismButton
        v-if="shares.length"
        variant="danger"
        type="button"
        :loading="busy"
        :disabled="!checkedShareIds.length"
        @click="doDeleteChecked"
      >
        {{ t('app.skill.shareDeleteBtn', { count: checkedShareIds.length || 0 }) }}
      </PrismButton>
      <span class="ss-footer-spacer" aria-hidden="true"></span>
      <PrismButton variant="secondary" type="button" @click="close">{{ t('app.common.close') }}</PrismButton>
    </template>
  </PrismDialog>

  <!-- Confirm: external shares move the skill to PENDING_APPROVAL -->
  <PrismDialog
    :model-value="shareConfirmOpen"
    :title="t('app.skill.shareConfirmTitle')"
    size="sm"
    :close-on-overlay="false"
    @update:model-value="shareConfirmOpen = $event"
  >
    <p class="ss-confirm-text">{{ t('app.skill.shareConfirmDesc') }}</p>
    <template #footer>
      <PrismButton variant="secondary" type="button" @click="shareConfirmOpen = false">{{ t('app.common.cancel') }}</PrismButton>
      <PrismButton type="button" @click="confirmShare">{{ t('app.common.confirm') }}</PrismButton>
    </template>
  </PrismDialog>
</template>

<style scoped>
.ss {
  display: grid;
  gap: var(--ds-space-4);
}
.ss-tabs {
  display: flex;
  gap: var(--ds-space-2);
  border-bottom: 1px solid var(--ds-color-border);
}
.ss-tab {
  display: inline-flex;
  align-items: center;
  gap: var(--ds-space-1);
  border: none;
  background: none;
  padding: var(--ds-space-2) var(--ds-space-3);
  cursor: pointer;
  font: inherit;
  font-size: 13px;
  color: var(--ds-color-text-secondary);
  border-bottom: 2px solid transparent;
}
.ss-tab.is-active {
  color: var(--ds-color-accent);
  border-bottom-color: var(--ds-color-accent);
}
.ss-body {
  display: grid;
  gap: var(--ds-space-3);
}
.ss-hint {
  margin: 0;
  font-size: 13px;
  color: var(--ds-color-text-secondary);
}
.ss-status {
  font-size: 13px;
  color: var(--ds-color-text-muted);
}
.ss-user-list {
  display: grid;
  gap: var(--ds-space-1);
  max-height: 220px;
  overflow-y: auto;
  border: 1px solid var(--ds-color-border);
  border-radius: var(--ds-radius-sm);
  padding: var(--ds-space-1);
}
.ss-user {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: var(--ds-space-2);
  border: 1px solid transparent;
  background: none;
  padding: 6px var(--ds-space-2);
  border-radius: var(--ds-radius-sm);
  cursor: pointer;
  font: inherit;
  font-size: 13px;
  text-align: left;
}
.ss-user:hover {
  background: var(--ds-color-bg-hover, rgba(128, 128, 128, 0.08));
}
.ss-user.is-selected {
  border-color: var(--ds-color-accent);
  color: var(--ds-color-accent);
}
.ss-user-knox {
  color: var(--ds-color-text-muted);
  font-size: 12px;
}
.ss-chips {
  display: flex;
  flex-wrap: wrap;
  gap: var(--ds-space-2);
  max-height: 120px;
  overflow-y: auto;
}
.ss-chip {
  display: inline-flex;
  align-items: center;
  gap: var(--ds-space-1);
  padding: 2px var(--ds-space-2);
  border: 1px solid var(--ds-color-border);
  border-radius: 20px;
  background: rgb(103 232 249 / .06);
  color: var(--ds-color-text-secondary);
  font-size: 13px;
}
.ss-chip-remove {
  display: inline-flex;
  border: none;
  background: none;
  cursor: pointer;
  color: var(--ds-color-text-muted);
  padding: 0;
}
@media (hover: hover) { .ss-chip-remove:hover { color: var(--ds-color-danger); } }
.ss-share-row {
  display: flex;
  justify-content: flex-end;
}
.ss-footer-spacer {
  flex: 1;
}
.ss-current {
  display: grid;
  gap: var(--ds-space-2);
  border-top: 1px solid var(--ds-color-border);
  padding-top: var(--ds-space-3);
}
.ss-current-title {
  margin: 0;
  font-size: 13px;
  font-weight: 600;
}
.ss-confirm-text {
  color: var(--ds-color-text-primary);
}
</style>

<!-- Non-scoped: the dialog is teleported to <body> via PrismDialog. -->
<style>
.skill-share-dialog {
  width: min(680px, calc(100vw - 32px));
}
</style>
