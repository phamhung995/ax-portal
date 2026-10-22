<script setup lang="ts">
import { ref } from 'vue'
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
import type { SearchUserItem } from '@/features/skills/types'
import type { OrgRef } from '@/lib/types'

const props = defineProps<{ open: boolean }>()
const emit = defineEmits(['update:open', 'selected'])

const keyword = ref('')
const items = ref<SearchUserItem[]>([])
const totalCount = ref(0)
const loading = ref(false)
const searched = ref(false)
const error = ref('')
const page = ref(1)
const pageSize = 10

const columns = [
  { key: 'name', label: 'Name' },
  { key: 'knoxId', label: 'ID' },
  { key: 'orgs', label: 'Part > Group' },
]

function orgLabel(orgs: OrgRef[]): string {
  if (!orgs?.length) return '—'
  return orgs
    .map((o) => `${o.part ?? ''} > ${o.group ?? ''}`)
    .filter((s) => s !== ' > ')
    .join(', ') || '—'
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
    title="Search User"
    size="lg"
    :close-on-overlay="true"
    :close-on-escape="true"
    @update:model-value="(v) => !v && emit('update:open', false)"
    @close="emit('update:open', false)"
  >
    <div class="su">
      <PrismInput
        v-model="keyword"
        label="Name or ID"
        placeholder="Search by name or Knox ID..."
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
        title="No data found."
        description="Enter a name or ID and click Search."
      />
      <PrismEmptyState v-else-if="error" title="Unable to search users.">
        <p class="su-error">{{ error }}</p>
        <PrismButton variant="secondary" @click="onSearch">Retry</PrismButton>
      </PrismEmptyState>
      <template v-else>
        <div @click="onGridClick">
          <PrismTable
            :columns="columns"
            :rows="items"
            row-key="id"
            :loading="loading"
            caption="User search results"
            empty-text="No data found."
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
      <PrismButton variant="secondary" @click="emit('update:open', false)">Close</PrismButton>
    </template>
  </PrismDialog>
</template>

<style scoped>
.su {
  display: grid;
  gap: var(--ds-space-4);
}
.su-error {
  color: var(--ds-color-danger);
  font-size: 13px;
}
:deep(.p-table) {
  white-space: normal;
}
:deep(.p-table td:nth-child(-n + 2)),
:deep(.p-table th:nth-child(-n + 2)) {
  white-space: nowrap;
}
:deep(.p-table td) {
  word-break: break-word;
}
</style>
