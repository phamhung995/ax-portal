<script setup lang="ts">
import { computed, nextTick, onMounted, ref, watch } from 'vue'
import { Plus, Search, Trash2 } from 'lucide-vue-next'
import PrismButton from '@/design-system/components/PrismButton.vue'
import PrismInput from '@/design-system/components/PrismInput.vue'
import PrismTable from '@/design-system/components/PrismTable.vue'
import PrismDialog from '@/design-system/components/PrismDialog.vue'
import PrismEmptyState from '@/design-system/components/PrismEmptyState.vue'
import PrismPagination from '@/design-system/components/PrismPagination.vue'
import PrismCombobox from '@/design-system/components/PrismCombobox.vue'
import { translationApi } from '@/features/translations/api'
import type { TranslationKeyItem } from '@/features/translations/types'
import { toUserError } from '@/lib/api'
import { toastError, toastSuccess } from '@/lib/toast'
import { useI18nStore } from '@/stores/i18n'

const i18n = useI18nStore()
const { t, formatDateTime } = i18n

const LANGS = [
  { code: 'en', label: 'English' },
  { code: 'vi', label: 'Tiếng Việt' },
  { code: 'ko', label: '한국어' },
] as const

// ── List ─────────────────────────────────────────────────────────────────────
const items = ref<TranslationKeyItem[]>([])
const loading = ref(true)
const error = ref('')
const search = ref('')

// Client-side pagination (the API returns the full filtered list).
const page = ref(1)
const pageSize = ref(20)
const PAGE_SIZES = [10, 20, 50, 100]
const pagedItems = computed(() => {
  const start = (page.value - 1) * pageSize.value
  return items.value.slice(start, start + pageSize.value)
})

async function load() {
  loading.value = true
  error.value = ''
  try {
    items.value = await translationApi.list(search.value.trim() || undefined)
  } catch (err) {
    error.value = toUserError(err).message
    items.value = []
  } finally {
    loading.value = false
  }
}

// Debounced search: refetch when the query settles and reset to page 1.
let searchTimer: number | undefined
watch(search, () => {
  window.clearTimeout(searchTimer)
  searchTimer = window.setTimeout(() => {
    page.value = 1
    load()
  }, 300)
})

function onPageSize(size: number) {
  pageSize.value = size
  page.value = 1
}

// ── Row selection (bulk delete, current page) ────────────────────────────────
const selectedIds = ref<Set<number>>(new Set())
const allSelected = computed(
  () => pagedItems.value.length > 0 && pagedItems.value.every((r) => selectedIds.value.has(r.id)),
)
function toggleAll() {
  selectedIds.value = allSelected.value ? new Set() : new Set(pagedItems.value.map((r) => r.id))
}
function toggleRow(id: number) {
  const next = new Set(selectedIds.value)
  if (next.has(id)) next.delete(id)
  else next.add(id)
  selectedIds.value = next
}

// ── Inline edit ──────────────────────────────────────────────────────────────
const editing = ref<{ id: number; lang: string } | null>(null)
const editDraft = ref('')
const editSaving = ref(false)
const editInput = ref<HTMLInputElement | null>(null)

// Focus the input as soon as it renders, with the caret at the end of the value.
// The input lives inside a v-for slot, so a plain template ref would become an
// array — use a function ref to keep a single element.
watch(editing, async (val) => {
  if (!val) return
  await nextTick()
  const el = editInput.value
  if (!el) return
  el.focus()
  el.setSelectionRange(el.value.length, el.value.length)
})

function onEditInputEl(el: HTMLElement | null) {
  if (el instanceof HTMLInputElement) editInput.value = el
}

// Vue 3.5 function refs pass (element | component instance, refs) —
// accept the wide type and narrow to the actual input element.
function onEditInputRef(el: unknown) {
  onEditInputEl(el as HTMLElement | null)
}

function startEdit(row: TranslationKeyItem, lang: string) {
  if (editing.value) return
  editing.value = { id: row.id, lang }
  editDraft.value = row.values[lang] ?? ''
}

function cancelEdit() {
  editing.value = null
  editDraft.value = ''
}

async function saveEdit() {
  const target = editing.value
  if (!target || editSaving.value) return
  const row = items.value.find((r) => r.id === target.id)
  if (!row) return cancelEdit()
  const original = row.values[target.lang] ?? ''
  if (editDraft.value === original) return cancelEdit()
  editSaving.value = true
  try {
    const updated = await translationApi.update(row.id, { values: { [target.lang]: editDraft.value } })
    items.value = items.value.map((r) => (r.id === row.id ? updated : r))
    toastSuccess(t('app.translations.saved'))
    // Apply the change to the running app immediately (the backend message
    // cache is already cleared on write) — e.g. the Home banner robot picks
    // up the new phrase on its next action.
    void i18n.loadMessages()
  } catch (err) {
    toastError(toUserError(err).message)
  } finally {
    editSaving.value = false
    cancelEdit()
  }
}

// ── Add key dialog ───────────────────────────────────────────────────────────
const addOpen = ref(false)
const addForm = ref({ key: '', description: '', en: '', vi: '', ko: '' })
const addError = ref('')
const addSaving = ref(false)

function openAdd() {
  addForm.value = { key: '', description: '', en: '', vi: '', ko: '' }
  addError.value = ''
  addOpen.value = true
}

async function submitAdd() {
  const key = addForm.value.key.trim()
  if (!/^[a-zA-Z][a-zA-Z0-9._-]{0,119}$/.test(key)) {
    addError.value = t('app.translations.keyInvalid')
    return
  }
  if (items.value.some((r) => r.key === key)) {
    addError.value = t('app.translations.keyExists')
    return
  }
  addSaving.value = true
  addError.value = ''
  try {
    const values: Record<string, string> = {}
    for (const lang of LANGS) {
      const v = addForm.value[lang.code].trim()
      if (v) values[lang.code] = v
    }
    await translationApi.create({
      key,
      description: addForm.value.description.trim() || undefined,
      values,
    })
    addOpen.value = false
    toastSuccess(t('app.translations.added'))
    await load()
    void i18n.loadMessages()
  } catch (err) {
    addError.value = toUserError(err).message
  } finally {
    addSaving.value = false
  }
}

// ── Bulk delete ──────────────────────────────────────────────────────────────
const bulkDeleteOpen = ref(false)
const deleteSaving = ref(false)

async function confirmBulkDelete() {
  const ids = [...selectedIds.value]
  if (!ids.length || deleteSaving.value) return
  deleteSaving.value = true
  try {
    await Promise.all(ids.map((id) => translationApi.remove(id)))
    toastSuccess(t('app.translations.deleted'))
    bulkDeleteOpen.value = false
    selectedIds.value = new Set()
    await load()
    void i18n.loadMessages()
  } catch (err) {
    toastError(toUserError(err).message)
  } finally {
    deleteSaving.value = false
  }
}

const columns = computed(() => [
  { key: 'select', label: '' },
  { key: 'key', label: t('app.translations.colKey') },
  ...LANGS.map((l) => ({ key: l.code, label: l.label })),
  { key: 'updatedAt', label: t('app.translations.colUpdated') },
])

onMounted(load)
</script>

<template>
  <div class="tm">
    <!-- Search + actions -->
    <section class="tm-toolbar p-glass" :aria-label="t('app.translations.searchAria')">
      <div class="tm-search">
        <Search :size="16" aria-hidden="true" />
        <input
          v-model="search"
          type="search"
          class="tm-search-input"
          :placeholder="t('app.translations.searchPh')"
          :aria-label="t('app.translations.searchAria')"
        />
      </div>
      <PrismButton
        variant="danger"
        type="button"
        :disabled="selectedIds.size === 0"
        @click="bulkDeleteOpen = true"
      >
        <Trash2 :size="16" aria-hidden="true" /> {{ t('app.translations.delete') }} ({{ selectedIds.size }})
      </PrismButton>
      <PrismButton type="button" @click="openAdd">
        <Plus :size="16" aria-hidden="true" /> {{ t('app.translations.addKey') }}
      </PrismButton>
    </section>

    <!-- Grid -->
    <section class="tm-grid p-glass" aria-label="Translation list">
      <PrismEmptyState v-if="error" :title="t('app.translations.loadError')">
        <p class="tm-error-text">{{ error }}</p>
        <PrismButton variant="secondary" @click="load">{{ t('app.translations.retry') }}</PrismButton>
      </PrismEmptyState>
      <template v-else>
        <PrismTable
          :columns="columns"
          :rows="pagedItems"
          :loading="loading"
          :caption="`Total: ${items.length}`"
          :empty-text="t('app.translations.noData')"
        >
          <template #header-select>
            <input
              type="checkbox"
              :checked="allSelected"
              aria-label="Select all rows"
              @change="toggleAll()"
            />
          </template>
          <template #cell-select="{ row }">
            <input
              type="checkbox"
              :checked="selectedIds.has((row as TranslationKeyItem).id)"
              :aria-label="`Select ${(row as TranslationKeyItem).key}`"
              @change="toggleRow((row as TranslationKeyItem).id)"
            />
          </template>
          <template #cell-key="{ row }">
            <code class="tm-key" :title="(row as TranslationKeyItem).key">{{ (row as TranslationKeyItem).key }}</code>
          </template>
          <template v-for="lang in LANGS" #[`cell-${lang.code}`]="{ row }">
            <input
              v-if="editing?.id === (row as TranslationKeyItem).id && editing.lang === lang.code"
              v-model="editDraft"
              :ref="onEditInputRef"
              class="tm-edit-input"
              :aria-label="`Edit ${lang.label} value`"
              @keydown.enter="saveEdit"
              @keydown.esc="cancelEdit"
              @blur="saveEdit"
              @click.stop
            />
            <button
              v-else
              type="button"
              class="tm-value-cell"
              :class="{ 'is-empty': !(row as TranslationKeyItem).values[lang.code] }"
              :title="`Edit ${lang.label} value`"
              @click="startEdit(row as TranslationKeyItem, lang.code)"
            >
              {{ (row as TranslationKeyItem).values[lang.code] || '—' }}
            </button>
          </template>
          <template #cell-updatedAt="{ row }">
            <span class="tm-updated">{{ formatDateTime((row as TranslationKeyItem).updatedAt) }}</span>
          </template>
        </PrismTable>

        <div class="tm-pager">
          <div class="tm-pagesize">
            <span class="tm-pagesize-label">{{ t('app.translations.rows') }}</span>
            <PrismCombobox
              :model-value="pageSize"
              readonly
              :aria-label="t('app.translations.rows')"
              :options="PAGE_SIZES.map((size) => ({ value: size, label: String(size) }))"
              @update:model-value="onPageSize(Number($event))"
            />
          </div>
          <PrismPagination
            :page="page"
            :page-size="pageSize"
            :total="items.length"
            @update:page="page = $event"
          />
        </div>
      </template>
    </section>

    <!-- Add key dialog -->
    <PrismDialog v-model="addOpen" :title="t('app.translations.addTitle')" size="lg">
      <p v-if="addError" class="p-field-error" role="alert">{{ addError }}</p>
      <div class="tm-form">
        <PrismInput v-model="addForm.key" :label="t('app.translations.keyLabel')" required :placeholder="t('app.translations.keyPh')" />
        <PrismInput v-model="addForm.description" :label="t('app.translations.descLabel')" :placeholder="t('app.translations.descPh')" />
        <div class="tm-form-values">
          <PrismInput v-for="lang in LANGS" :key="lang.code" v-model="addForm[lang.code]" :label="lang.label" />
        </div>
      </div>
      <template #footer>
        <PrismButton variant="secondary" type="button" @click="addOpen = false">{{ t('app.translations.cancel') }}</PrismButton>
        <PrismButton type="button" :loading="addSaving" @click="submitAdd">{{ t('app.translations.add') }}</PrismButton>
      </template>
    </PrismDialog>

    <!-- Delete confirm (bulk) -->
    <PrismDialog
      v-model="bulkDeleteOpen"
      :title="t('app.translations.deleteTitle')"
      :description="t('app.translations.deleteSelected', { n: selectedIds.size })"
    >
      <template #footer>
        <PrismButton variant="secondary" type="button" @click="bulkDeleteOpen = false">{{ t('app.translations.cancel') }}</PrismButton>
        <PrismButton type="button" :loading="deleteSaving" @click="confirmBulkDelete">{{ t('app.translations.delete') }}</PrismButton>
      </template>
    </PrismDialog>
  </div>
</template>

<style scoped>
.tm {
  display: grid;
  gap: var(--ds-space-4);
  width: 100%;
}

.tm-toolbar {
  display: flex;
  align-items: center;
  gap: var(--ds-space-3);
  padding: var(--ds-space-3) var(--ds-space-4);
}
.tm-search {
  flex: 1;
  max-width: 420px;
  display: flex;
  align-items: center;
  gap: var(--ds-space-2);
  padding: 0 var(--ds-space-3);
  border: 1px solid var(--ds-color-border);
  border-radius: var(--ds-radius-sm);
  color: var(--ds-color-text-muted);
}
.tm-search-input {
  flex: 1;
  border: none;
  background: none;
  outline: none;
  font: 13px var(--ds-font-sans);
  color: var(--ds-color-text-primary);
  padding: 8px 0;
}
.tm-search-input::placeholder {
  color: var(--ds-color-text-muted);
}

.tm-grid {
  padding: var(--ds-space-3);
  min-width: 0;
}
.tm-grid :deep(.p-table-scroll) {
  overflow-x: hidden;
}
.tm-grid :deep(table) {
  table-layout: fixed;
  width: 100%;
}
.tm-grid :deep(th:nth-child(1)),
.tm-grid :deep(td:nth-child(1)) {
  width: 36px;
}
.tm-grid :deep(th:nth-child(2)),
.tm-grid :deep(td:nth-child(2)) {
  width: 18%;
}
.tm-grid :deep(th:nth-child(6)),
.tm-grid :deep(td:nth-child(6)) {
  width: 130px;
}
.tm-error-text {
  color: var(--ds-color-danger);
  font-size: 13px;
}

.tm-key {
  display: block;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
  font: 12px var(--ds-font-mono);
  color: var(--ds-color-text-primary);
}

.tm-value-cell {
  display: block;
  width: 100%;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
  text-align: left;
  border: 1px dashed transparent;
  background: none;
  border-radius: var(--ds-radius-sm);
  padding: 4px 6px;
  font: 13px var(--ds-font-sans);
  color: var(--ds-color-text-primary);
  cursor: text;
}
.tm-value-cell:hover {
  border-color: var(--ds-color-border-strong);
  background: var(--ds-list-item-hover-accent);
}
.tm-value-cell.is-empty {
  color: var(--ds-color-text-muted);
}
.tm-edit-input {
  width: 100%;
  border: 1px solid var(--ds-color-accent);
  border-radius: var(--ds-radius-sm);
  background: var(--ds-surface-base, transparent);
  font: 13px var(--ds-font-sans);
  color: var(--ds-color-text-primary);
  padding: 4px 6px;
  outline: none;
}

.tm-updated {
  display: block;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
  font: 12px var(--ds-font-mono);
  color: var(--ds-color-text-secondary);
}

.tm-form {
  display: grid;
  gap: var(--ds-space-4);
}
.tm-form-values {
  display: grid;
  grid-template-columns: repeat(3, 1fr);
  gap: var(--ds-space-3);
}
@media (max-width: 899px) {
  .tm-form-values {
    grid-template-columns: 1fr;
  }
}

.tm-pager {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: var(--ds-space-3);
  margin-top: var(--ds-space-4);
  flex-wrap: wrap;
}
.tm-pagesize {
  display: flex;
  align-items: center;
  gap: var(--ds-space-2);
  flex-shrink: 0;
}
.tm-pagesize-label {
  font-size: 12px;
  font-weight: 500;
  color: var(--ds-color-text-secondary);
}
.tm-pagesize :deep(.p-field) {
  gap: 0;
}
.tm-pagesize :deep(.p-field > label:empty) {
  display: none;
}
.tm-pagesize :deep(.p-combobox) {
  width: 84px;
  font-size: 12px;
}
.tm-pagesize :deep(.p-input-wrap),
.tm-pagesize :deep(.p-input-wrap) input {
  min-height: 30px;
  font-size: 12px;
  padding: 0 var(--ds-space-2);
}
</style>
