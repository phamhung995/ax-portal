<script setup lang="ts">
import { computed, ref } from 'vue'
import { Search } from 'lucide-vue-next'
import PrismDialog from '@/design-system/components/PrismDialog.vue'
import PrismButton from '@/design-system/components/PrismButton.vue'
import PrismInput from '@/design-system/components/PrismInput.vue'
import PrismTable from '@/design-system/components/PrismTable.vue'
import PrismPagination from '@/design-system/components/PrismPagination.vue'
import PrismEmptyState from '@/design-system/components/PrismEmptyState.vue'
import { skillApi } from '@/features/skills/api'
import { toUserError } from '@/lib/api'
import { toastError } from '@/lib/toast'
import { useI18nStore } from '@/stores/i18n'
import type { SearchUserItem } from '@/features/skills/types'
import { orgsLabel, type OrgRef } from '@/lib/types'

const props = defineProps<{ open: boolean }>()
const emit = defineEmits(['update:open', 'selected'])
const { t } = useI18nStore()

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
    items.value = result.items
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

function onRowClick(row: SearchUserItem) {
  emit('selected', row)
  emit('update:open', false)
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

function onPage(p: number) {
  page.value = p
  void runSearch(false)
}
</script>

<template>
  <PrismDialog
    :model-value="open"
    :title="t('app.skills.searchTitle')"
    size="lg"
    :close-on-overlay="true"
    :close-on-escape="true"
    @update:model-value="(v) => !v && emit('update:open', false)"
    @close="emit('update:open', false)"
  >
    <div class="su">
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
        <p class="su-error">{{ error }}</p>
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
    </div>

    <template #footer>
      <PrismButton variant="secondary" @click="emit('update:open', false)">{{ t('app.common.close') }}</PrismButton>
    </template>
  </PrismDialog>
</template>

<style scoped>
.su {
  display: grid;
  gap: var(--ds-space-3);
  font-size: 13px;
}
.su-error {
  color: var(--ds-color-danger);
  font-size: 13px;
}
/* Compact popup controls — match the compact search panels (34px / 13px). */
.su :deep(.p-field) {
  gap: var(--ds-space-1);
  font-size: 13px;
}
.su :deep(.p-input-wrap),
.su :deep(.p-input-wrap) input {
  min-height: 34px;
  font-size: 13px;
}
.su :deep(.p-button) {
  min-height: 34px;
  padding: 0 var(--ds-space-3);
  font-size: 13px;
}
.su :deep(.p-table) {
  white-space: normal;
  font-size: 13px;
}
.su :deep(.p-table th) {
  font-size: 12px;
}
.su :deep(.p-table td),
.su :deep(.p-table th) {
  padding: var(--ds-space-2) var(--ds-space-2);
}
.su :deep(.p-table td:nth-child(-n + 2)),
.su :deep(.p-table th:nth-child(-n + 2)) {
  white-space: nowrap;
}
.su :deep(.p-table td) {
  word-break: break-word;
}
.su :deep(.p-pagination) {
  font-size: 12px;
}
</style>
