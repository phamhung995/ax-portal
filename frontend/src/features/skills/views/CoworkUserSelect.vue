<script setup lang="ts">
import { computed, ref, watch } from 'vue'
import { Search, X } from 'lucide-vue-next'
import PrismDialog from '@/design-system/components/PrismDialog.vue'
import PrismButton from '@/design-system/components/PrismButton.vue'
import PrismInput from '@/design-system/components/PrismInput.vue'
import PrismTable from '@/design-system/components/PrismTable.vue'
import PrismPagination from '@/design-system/components/PrismPagination.vue'
import PrismEmptyState from '@/design-system/components/PrismEmptyState.vue'
import { skillApi } from '@/features/skills/api'
import { toUserError } from '@/lib/api'
import { useAuthStore } from '@/stores/auth'
import { useI18nStore } from '@/stores/i18n'
import type { SearchUserItem } from '@/features/skills/types'
import { orgsLabel, type OrgRef } from '@/lib/types'

const props = defineProps<{
  modelValue: { id: number; name: string }[]
  label?: string
  error?: string
  disabled?: boolean
  hint?: string
}>()
const emit = defineEmits(['update:modelValue'])

const auth = useAuthStore()
const { t } = useI18nStore()

const open = ref(false)

// Popup-local draft (committed to the input only on OK).
const draft = ref<{ id: number; name: string }[]>([])

// ── Search ──────────────────────────────────────────────────────────────────
const keyword = ref('')
const items = ref<SearchUserItem[]>([])
const totalCount = ref(0)
const loading = ref(false)
const searched = ref(false)
const error = ref('')
const page = ref(1)
const pageSize = 10

const columns = computed(() => [
  { key: 'name', label: t('app.common.colName') },
  { key: 'knoxId', label: t('app.common.colId') },
  { key: 'orgs', label: t('app.common.organization') },
])

function orgLabel(orgs: OrgRef[]): string {
  return orgsLabel(orgs)
}

async function runSearch(resetPage = true) {
  if (resetPage) page.value = 1
  loading.value = true
  error.value = ''
  try {
    const result = await skillApi.searchUser(keyword.value.trim(), page.value, pageSize)
    // Exclude self and already-selected users from the results.
    const selectedIds = new Set(draft.value.map((u) => u.id))
    items.value = result.items.filter(
      (u) => u.id !== auth.user?.userId && !selectedIds.has(u.id)
    )
    totalCount.value = result.totalCount
    searched.value = true
  } catch (err) {
    error.value = toUserError(err).message
    items.value = []
  } finally {
    loading.value = false
  }
}

function onSearch() {
  void runSearch(true)
}

function onKeydown(event: KeyboardEvent) {
  if (event.key === 'Enter') {
    event.preventDefault()
    onSearch()
  }
}

function onPage(p: number) {
  page.value = p
  void runSearch(false)
}

// ── Selection (row click toggles) ───────────────────────────────────────────
function onRowClick(row: SearchUserItem) {
  toggleUser(row)
}

function onGridClick(event: MouseEvent) {
  const target = event.target as HTMLElement
  const rowEl = target.closest('tr')
  if (!rowEl) return
  const tbody = rowEl.parentElement
  if (!tbody) return
  const index = Array.prototype.indexOf.call(tbody.children, rowEl)
  const item = items.value[index]
  if (item) onRowClick(item)
}

function toggleUser(user: SearchUserItem) {
  const exists = draft.value.some((u) => u.id === user.id)
  draft.value = exists
    ? draft.value.filter((u) => u.id !== user.id)
    : [...draft.value, { id: user.id, name: user.name }]
}

function removeUser(id: number) {
  draft.value = draft.value.filter((u) => u.id !== id)
}

// ── Open / OK / Cancel ──────────────────────────────────────────────────────
function onInputClick() {
  if (props.disabled) return
  draft.value = [...props.modelValue]
  keyword.value = ''
  items.value = []
  totalCount.value = 0
  searched.value = false
  error.value = ''
  page.value = 1
  open.value = true
}

function onOk() {
  emit('update:modelValue', [...draft.value])
  open.value = false
}

function onCancel() {
  open.value = false
}

const selectedLabel = computed(() =>
  props.modelValue.length ? props.modelValue.map((u) => u.name).join(', ') : ''
)

watch(open, (v) => {
  if (!v) {
    // Reset search state so the next open starts clean.
    items.value = []
    totalCount.value = 0
    searched.value = false
  }
})
</script>

<template>
  <div class="cws-field">
    <label class="p-label">{{ label ?? t('app.common.cowork') }}</label>
    <button
      type="button"
      class="cws-input-btn"
      :class="{ 'is-disabled': disabled }"
      :disabled="disabled"
      @click="onInputClick"
    >
      <span v-if="selectedLabel" class="cws-input-value">{{ selectedLabel }}</span>
      <span v-else class="cws-input-placeholder">{{ t('app.skills.coworkClick') }}</span>
    </button>
    <p v-if="error" class="p-field-error" role="alert">{{ error }}</p>
    <p v-else-if="hint" class="cws-hint">{{ hint }}</p>
  </div>

  <PrismDialog
    :model-value="open"
    :title="t('app.skills.coworkTitle')"
    size="lg"
    :close-on-overlay="true"
    :close-on-escape="true"
    @update:model-value="(v) => !v && onCancel()"
    @close="onCancel"
  >
    <div class="cws-p">
      <!-- Top: search + results -->
      <PrismInput
        v-model="keyword"
        :label="t('app.common.nameOrId')"
        :placeholder="t('app.common.searchByNameIdPh')"
        @keydown="onKeydown"
      >
        <template #suffix>
          <PrismButton type="button" variant="ghost" :loading="loading" @click="onSearch">
            <Search :size="16" aria-hidden="true" />
          </PrismButton>
        </template>
      </PrismInput>

      <PrismEmptyState
        v-if="!searched && !loading && !error"
        :title="t('app.common.noDataFound')"
        :description="t('app.skills.searchUserDesc')"
      />
      <PrismEmptyState v-else-if="error" :title="t('app.common.userSearchError')">
        <p class="cws-error">{{ error }}</p>
        <PrismButton variant="secondary" @click="onSearch">{{ t('app.common.retry') }}</PrismButton>
      </PrismEmptyState>
      <template v-else>
        <div @click="onGridClick">
          <PrismTable
            :columns="columns"
            :rows="items"
            row-key="id"
            :loading="loading"
            :caption="t('app.common.userSearchResults')"
            :empty-text="t('app.common.noDataFound')"
          >
            <template #cell-orgs="{ row }">
              {{ orgLabel((row as SearchUserItem).orgs) }}
            </template>
          </PrismTable>
        </div>
        <PrismPagination
          v-if="items.length"
          :page="page"
          :page-size="pageSize"
          :total="totalCount"
          @update:page="onPage"
        />
      </template>

      <!-- Bottom: selected list -->
      <div class="cws-selected">
        <span class="cws-selected-label">{{ t('app.skills.selected', { n: draft.length }) }}</span>
        <div v-if="draft.length" class="cws-selected-chips">
          <span v-for="u in draft" :key="u.id" class="cws-chip">
            {{ u.name }}
            <button
              type="button"
              class="cws-chip-remove"
              :aria-label="t('app.common.removeUserAria', { name: u.name })"
              @click="removeUser(u.id)"
            >
              <X :size="12" aria-hidden="true" />
            </button>
          </span>
        </div>
        <p v-else class="cws-selected-empty">{{ t('app.skills.noUsersSelected') }}</p>
      </div>
    </div>

    <template #footer>
      <PrismButton variant="secondary" @click="onCancel">{{ t('app.common.cancel') }}</PrismButton>
      <PrismButton @click="onOk">{{ t('app.common.ok') }}</PrismButton>
    </template>
  </PrismDialog>
</template>

<style scoped>
.cws-field {
  display: grid;
  gap: var(--ds-space-2);
  align-content: start;
}
.cws-input-btn {
  display: flex;
  align-items: center;
  width: 100%;
  min-height: var(--ds-control-height);
  padding: 0 var(--ds-space-3);
  border: 1px solid var(--ds-color-border);
  border-radius: var(--ds-radius-sm);
  background: var(--ds-input-background);
  color: var(--ds-color-text-primary);
  font: inherit;
  font-size: 13px;
  text-align: left;
  cursor: pointer;
  overflow: hidden;
  transition: border-color var(--ds-duration-fast), background var(--ds-duration-fast), box-shadow var(--ds-duration-fast);
}
.cws-input-btn:hover:not(:disabled) {
  border-color: var(--ds-color-border-strong, var(--ds-color-border));
}
.cws-input-btn:focus-visible {
  outline: none;
  border-color: var(--ds-color-accent);
  background: var(--ds-input-focus-background);
  box-shadow: 0 0 0 3px rgb(94 106 210 / .10);
}
.cws-input-btn.is-disabled {
  opacity: .6;
  cursor: not-allowed;
}
.cws-input-value {
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}
.cws-input-placeholder {
  color: var(--ds-color-text-muted);
}
.cws-hint {
  margin: 0;
  color: var(--ds-color-text-muted);
  font-size: 12px;
}
.cws-p {
  display: grid;
  gap: var(--ds-space-3);
  font-size: 13px;
}
.cws-error {
  color: var(--ds-color-danger);
  font-size: 13px;
}
/* Compact popup controls — match the compact search panels (34px / 13px). */
.cws-p :deep(.p-field) {
  gap: var(--ds-space-1);
  font-size: 13px;
}
.cws-p :deep(.p-input-wrap),
.cws-p :deep(.p-input-wrap) input {
  min-height: 34px;
  font-size: 13px;
}
.cws-p :deep(.p-button) {
  min-height: 34px;
  padding: 0 var(--ds-space-3);
  font-size: 13px;
}
.cws-p :deep(.p-table) {
  white-space: normal;
  font-size: 13px;
}
.cws-p :deep(.p-table th) {
  font-size: 12px;
}
.cws-p :deep(.p-table td),
.cws-p :deep(.p-table th) {
  padding: var(--ds-space-2) var(--ds-space-2);
}
.cws-p :deep(.p-table td:nth-child(-n + 2)),
.cws-p :deep(.p-table th:nth-child(-n + 2)) {
  white-space: nowrap;
}
.cws-p :deep(.p-table td) {
  word-break: break-word;
}
.cws-p :deep(.p-pagination) {
  font-size: 12px;
}
/* Selected list */
.cws-selected {
  display: grid;
  gap: var(--ds-space-2);
  padding-top: var(--ds-space-3);
  border-top: 1px solid var(--ds-color-border);
}
.cws-selected-label {
  font-size: 11px;
  text-transform: uppercase;
  letter-spacing: .08em;
  color: var(--ds-color-text-muted);
}
.cws-selected-chips {
  display: flex;
  flex-wrap: wrap;
  gap: var(--ds-space-2);
  max-height: 120px;
  overflow-y: auto;
}
.cws-chip {
  display: inline-flex;
  align-items: center;
  gap: var(--ds-space-1);
  padding: 2px var(--ds-space-2);
  border: 1px solid var(--ds-color-border);
  border-radius: 20px;
  background: rgb(94 106 210 / .06);
  color: var(--ds-color-text-secondary);
  font-size: 13px;
}
.cws-chip-remove {
  display: inline-flex;
  border: none;
  background: none;
  cursor: pointer;
  color: var(--ds-color-text-muted);
  padding: 0;
}
@media (hover: hover) { .cws-chip-remove:hover { color: var(--ds-color-danger); } }
.cws-selected-empty {
  margin: 0;
  color: var(--ds-color-text-muted);
  font-size: 13px;
}
</style>
