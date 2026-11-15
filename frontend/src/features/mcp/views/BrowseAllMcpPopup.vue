<script setup lang="ts">
/**
 * Browse All MCPs Popup (FR-MCP-021 — reactivated v1.27, 2-zone v1.33,
 * 2-tab layout v1.40).
 *
 * Entry point: link "Browse all MCPs" in the MCP Dependencies dropdown
 * (FR-MCP-014). Lets the owner search existing MCPs and pick one to pin as a
 * skill dependency.
 *
 * v1.33 — two zones (logic unchanged):
 *  - "Approved MCPs": APPROVED MCPs in scope (search by name, debounce
 *    300ms + pagination 20/page). Row click → pin.
 *  - "Your MCPs (N)": the viewer's OWN MCPs (ALL statuses — DRAFT/
 *    PENDING_APPROVAL/REJECTED/APPROVED, 2026-11), cap 10 + "Show more",
 *    sort updated DESC, no separate search. DRAFT/PENDING row → pin (Q-18
 *    exception); REJECTED row → NOT pinnable, click the name → MCP Detail
 *    Popup to resubmit (FR-MCP-006).
 *
 * v1.40 — layout 2 tabs (PO confirmed, SUPERSEDED v1.38):
 *  - Tab "Approved MCPs (N)" (default): search box + grid + pagination.
 *  - Tab "Your MCPs (N)": the owner's own MCPs in the SAME grid as the
 *    Approved tab (7 columns — PO: consistent UI across tabs). REJECTED
 *    rows: the name is a link → Detail Popup to resubmit.
 *    The search box lives inside the Approved tab — it only filters that grid.
 *
 * - Header button "New MCP" (footer) → closes this popup, opens the Register
 *   New MCP Popup (FR-MCP-004 — logic unchanged).
 * - 2026-11-05: MULTI-SELECT (Cowork pattern) — row click toggles a draft
 *   selection (tag "Selected"), footer OK fetches the details of every
 *   selected MCP and emits them as an array; Cancel/Close discards.
 * - Already-pinned MCP → row disabled + "Selected".
 * - Pinning own unapproved MCP: version = current, required = false
 *   (Q-18 exception — publish validation FR-MCP-016 is the net at submit).
 */
import { computed, onMounted, ref, watch } from 'vue'
import { PlusCircle } from 'lucide-vue-next'
import PrismDialog from '@/design-system/components/PrismDialog.vue'
import PrismButton from '@/design-system/components/PrismButton.vue'
import PrismInput from '@/design-system/components/PrismInput.vue'
import PrismTable from '@/design-system/components/PrismTable.vue'
import PrismPagination from '@/design-system/components/PrismPagination.vue'
import PrismBadge from '@/design-system/components/PrismBadge.vue'
import PrismTabs from '@/design-system/components/PrismTabs.vue'
import PrismEmptyState from '@/design-system/components/PrismEmptyState.vue'
import McpDetailPopup from './McpDetailPopup.vue'
import { mcpApi } from '../api'
import { useMcpListStore } from '../store'
import { useAuthStore } from '@/stores/auth'
import { useI18nStore } from '@/stores/i18n'
import { toUserError } from '@/lib/api'
import { stripHtml } from '@/lib/sanitize'
import { toastError } from '@/lib/toast'
import type { McpListItem, McpDetailResponse } from '../types'

const { t } = useI18nStore()

const props = defineProps<{
  /** MCPs already in the dependency list — those rows are disabled. */
  pinnedMcpIds: number[]
}>()

const emit = defineEmits<{
  close: []
  /** "New MCP" footer button → parent closes this popup + opens Register New MCP. */
  'add-new-mcp': []
  /** OK → parent adds the selected MCPs as dependency chips (multi-select). */
  selected: [details: McpDetailResponse[]]
}>()

const auth = useAuthStore()
const mcpList = useMcpListStore()
const selfId = computed(() => auth.user?.userId)

const PAGE_SIZE = 20
const SEARCH_DEBOUNCE_MS = 300
const OWN_PAGE_SIZE = 100 // own unapproved MCPs are few — fetch once, slice client-side
const OWN_CAP = 10

// ── Tab "Approved MCPs" (default): search + grid + pagination ───────────────
const activeTab = ref('approved')
const searchName = ref('')
const page = ref(1)
const results = ref<McpListItem[]>([])
const total = ref(0)
const loading = ref(false)
const error = ref('')
const focusedRow = ref(-1)

let searchDebounce: ReturnType<typeof setTimeout> | null = null

async function runSearch(resetPage = false) {
  if (resetPage) page.value = 1
  loading.value = true
  error.value = ''
  focusedRow.value = -1
  try {
    const data = await mcpApi.search({
      mcpName: searchName.value.trim() || undefined,
      statuses: ['APPROVED'], // v1.33: Approved tab = APPROVED in scope only
      page: page.value,
      pageSize: PAGE_SIZE,
      sortBy: 'createdDate',
      sortDirection: 'DESC',
    })
    results.value = data.items
    total.value = data.totalCount
  } catch (e) {
    error.value = toUserError(e).message
    results.value = []
    total.value = 0
  } finally {
    loading.value = false
  }
}

// Search by name, debounce 300ms (spec FR-MCP-021 main flow 5).
watch(searchName, () => {
  if (searchDebounce) clearTimeout(searchDebounce)
  searchDebounce = setTimeout(() => runSearch(true), SEARCH_DEBOUNCE_MS)
})

function onPage(p: number) {
  page.value = p
  runSearch(false)
}

// ── Tab "Your MCPs" (own MCPs — all statuses) ───────────────────────────────
const ownMcps = ref<McpListItem[]>([])
const ownLoading = ref(false)
const ownVisible = ref(OWN_CAP) // cap 10 + "Show more"

async function loadOwnMcps() {
  if (selfId.value == null) {
    ownMcps.value = []
    return
  }
  ownLoading.value = true
  try {
    const data = await mcpApi.search({
      creatorId: selfId.value,
      // 2026-11: ALL own MCPs (incl. APPROVED) — not just
      // DRAFT/PENDING/REJECTED.
      statuses: ['DRAFT', 'PENDING_APPROVAL', 'REJECTED', 'APPROVED'],
      page: 1,
      pageSize: OWN_PAGE_SIZE,
      sortBy: 'createdDate',
      sortDirection: 'DESC',
    })
    ownMcps.value = data.items
  } catch {
    ownMcps.value = []
  } finally {
    ownLoading.value = false
  }
}

const ownMcpsVisible = computed(() => ownMcps.value.slice(0, ownVisible.value))
const hasMoreOwn = computed(() => ownMcps.value.length > ownVisible.value)

onMounted(() => {
  runSearch(true)
  loadOwnMcps()
})

function isPinned(row: McpListItem): boolean {
  return props.pinnedMcpIds.includes(row.id)
}

// ── Tab "Approved MCPs" row click = TOGGLE the draft selection (2026-11-05:
//    multi-select — Cowork pattern). Pinned rows are disabled. ──
function onGridClick(event: MouseEvent) {
  const rowEl = (event.target as HTMLElement).closest('tr')
  const tbody = rowEl?.parentElement
  if (!rowEl || !tbody) return
  const index = Array.prototype.indexOf.call(tbody.children, rowEl)
  const item = results.value[index]
  if (item) toggleRow(item)
}

function onGridKeydown(event: KeyboardEvent) {
  if (event.key === 'ArrowDown' || event.key === 'ArrowUp') {
    event.preventDefault()
    const delta = event.key === 'ArrowDown' ? 1 : -1
    focusedRow.value = Math.min(Math.max(focusedRow.value + delta, 0), results.value.length - 1)
  } else if (event.key === 'Enter' && focusedRow.value >= 0) {
    event.preventDefault()
    const row = results.value[focusedRow.value]
    focusedRow.value = -1
    if (row) toggleRow(row)
  }
}

// ── Multi-select draft (Cowork pattern): row click toggles, OK commits ──────
const draft = ref<McpListItem[]>([])
const okLoading = ref(false)

function isDrafted(row: McpListItem): boolean {
  return draft.value.some((d) => d.id === row.id)
}

function toggleRow(row: McpListItem) {
  if (isPinned(row)) return
  draft.value = isDrafted(row)
    ? draft.value.filter((d) => d.id !== row.id)
    : [...draft.value, row]
}

function removeDraft(row: McpListItem) {
  draft.value = draft.value.filter((d) => d.id !== row.id)
}

/** OK: fetch the full detail of every selected MCP → emit the array. */
async function onOk() {
  if (!draft.value.length || okLoading.value) return
  okLoading.value = true
  try {
    const details = await Promise.all(draft.value.map((row) => mcpApi.detail(row.id)))
    emit('selected', details)
  } catch (e) {
    toastError(toUserError(e).message)
  } finally {
    okLoading.value = false
  }
}

// ── Tab "Your MCPs" row click = toggle the draft selection (same pattern as
//    the Approved tab). REJECTED rows are not pinnable — the name link opens
//    the Detail Popup to resubmit. ──
function onOwnGridClick(event: MouseEvent) {
  const rowEl = (event.target as HTMLElement).closest('tr')
  const tbody = rowEl?.parentElement
  if (!rowEl || !tbody) return
  const index = Array.prototype.indexOf.call(tbody.children, rowEl)
  const item = ownMcpsVisible.value[index]
  if (item) toggleOwnRow(item)
}

function toggleOwnRow(row: McpListItem) {
  if (isPinned(row)) return
  if (row.status === 'REJECTED') return // not pinnable — use the name link
  toggleRow(row)
}

// MCP Name link → MCP Detail Popup (both tabs — PO 2026-11). Row click still
// pins (selects) the MCP; the name link stops propagation.
const detailMcpId = ref<number | null>(null)
function openDetail(mcpId: number) {
  detailMcpId.value = mcpId
}
// After resubmit the MCP status changes (REJECTED → PENDING) → refresh the list.
function onDetailUpdated() {
  mcpList.markDirty()
  runSearch(false)
  loadOwnMcps()
}

// ── Display helpers (same labels/tones as the MCP Management grid) ─────────
const statusLabel = (code: string) =>
  code === 'PENDING_APPROVAL' ? t('app.common.statusPending') : code === 'DRAFT' ? t('app.common.statusDraft') : code === 'APPROVED' ? t('app.common.statusApproved') : code === 'REJECTED' ? t('app.common.statusRejected') : code
const statusTone = (code: string) =>
  code === 'APPROVED' ? 'success' : code === 'PENDING_APPROVAL' ? 'warning' : code === 'REJECTED' ? 'danger' : 'neutral'

const columns = computed(() => [
  { key: 'name', label: t('app.mcp.colName') },
  { key: 'description', label: t('app.mcp.colDescription') },
  { key: 'status', label: t('app.mcp.colStatus'), width: 140 },
  { key: 'viewCount', label: t('app.mcp.colViews'), width: 70 },
  // v1.28: Rating = fit-content — 90px vừa đủ cho "No rating" / "★ 5.0 (1)"
  // (table-layout: fixed không hỗ trợ fit-content thật → width vừa đủ).
  { key: 'rating', label: t('app.mcp.colRating'), width: 90 },
])
</script>

<template>
  <PrismDialog
    :model-value="true"
    :title="t('app.mcp.browseTitle')"
    size="lg"
    dialog-class="mcp-browse-dialog"
    :close-on-overlay="false"
    :close-on-escape="false"
    @update:model-value="emit('close')"
    @close="emit('close')"
  >
    <div class="bm">
      <!-- v1.40 (phương án 2 tabs): 2 tabs — "Approved MCPs (N)" (mặc định)
           + "Your MCPs (N)". Search box nằm trong tab Approved. -->
      <PrismTabs v-model="activeTab" :items="[
        { value: 'approved', label: t('app.mcp.approvedMcpTab', { n: total }) },
        { value: 'yours', label: t('app.mcp.myMcpTab', { n: ownMcps.length }) },
      ]">
        <!-- ── Tab Approved MCPs (default): search + grid + pagination ────── -->
        <template #approved>
          <div class="bm-zone">
            <PrismInput
              v-model="searchName"
              :label="t('app.mcp.colName')"
              :placeholder="t('app.mcp.searchByName')"
            />

            <PrismEmptyState v-if="error" :title="t('app.mcp.loadError')">
              <p class="bm-error-text">{{ error }}</p>
              <PrismButton variant="secondary" @click="runSearch(true)">{{ t('app.common.retry') }}</PrismButton>
            </PrismEmptyState>
            <template v-else>
              <div class="bm-grid table-compact" @click="onGridClick" @keydown="onGridKeydown">
                <PrismTable
                  :columns="columns"
                  :rows="results"
                  row-key="id"
                  :loading="loading"
                  :caption="t('app.common.total', { n: total })"
                  :empty-text="t('app.mcp.emptyTitle')"
                >
                  <template #cell-name="{ row }">
                    <span class="bm-name" :class="{ 'is-pinned': isPinned(row as McpListItem) || isDrafted(row as McpListItem) }">
                      <button
                        type="button"
                        class="bm-your-mcp-name-link"
                        :title="(row as McpListItem).name"
                        @click.stop="openDetail((row as McpListItem).id)"
                      >
                        {{ (row as McpListItem).name }}
                      </button>
                      <span v-if="isPinned(row as McpListItem) || isDrafted(row as McpListItem)" class="bm-selected-tag">{{ t('app.mcp.selectedTag') }}</span>
                    </span>
                  </template>
                  <template #cell-description="{ row }">
                    <span
                      class="bm-cell-text"
                      :title="stripHtml((row as McpListItem).description ?? '') || undefined"
                    >
                      {{ stripHtml((row as McpListItem).description ?? '') || '—' }}
                    </span>
                  </template>
                  <template #cell-status="{ row }">
                    <PrismBadge :tone="statusTone((row as McpListItem).status)">
                      {{ statusLabel((row as McpListItem).status) }}
                    </PrismBadge>
                  </template>
                  <template #cell-rating="{ row }">
                    <span v-if="(row as McpListItem).avgRating != null" class="bm-rating">
                      ★ {{ (row as McpListItem).avgRating!.toFixed(1) }} ({{ (row as McpListItem).ratingCount }})
                    </span>
                    <span v-else class="bm-norating">{{ t('app.mcp.noRating') }}</span>
                  </template>
                </PrismTable>
              </div>

              <div class="bm-pager">
                <PrismPagination
                  :page="page"
                  :page-size="PAGE_SIZE"
                  :total="total"
                  @update:page="onPage"
                />
              </div>
            </template>
          </div>
        </template>

        <!-- ── Tab Your MCPs (own MCPs, all statuses) — v1.40: same grid
           as the Approved tab (7 columns, row click → pin) ───────────────── -->
        <template #yours>
          <div class="bm-zone bm-your-mcps">
            <div class="bm-grid table-compact" @click="onOwnGridClick">
              <PrismTable
                :columns="columns"
                :rows="ownMcpsVisible"
                row-key="id"
                :loading="ownLoading"
                :caption="t('app.common.total', { n: ownMcps.length })"
                :empty-text="t('app.mcp.myMcpsEmpty')"
              >
                <template #cell-name="{ row }">
                  <span class="bm-name" :class="{ 'is-pinned': isPinned(row as McpListItem) || isDrafted(row as McpListItem) }">
                    <button
                      type="button"
                      class="bm-your-mcp-name-link"
                      :title="(row as McpListItem).name"
                      @click.stop="openDetail((row as McpListItem).id)"
                    >
                      {{ (row as McpListItem).name }}
                    </button>
                    <span v-if="isPinned(row as McpListItem) || isDrafted(row as McpListItem)" class="bm-selected-tag">{{ t('app.mcp.selectedTag') }}</span>
                  </span>
                </template>
                <template #cell-description="{ row }">
                  <span
                    class="bm-cell-text"
                    :title="stripHtml((row as McpListItem).description ?? '') || undefined"
                  >
                    {{ stripHtml((row as McpListItem).description ?? '') || '—' }}
                  </span>
                </template>
                <template #cell-status="{ row }">
                  <PrismBadge :tone="statusTone((row as McpListItem).status)">
                    {{ statusLabel((row as McpListItem).status) }}
                  </PrismBadge>
                </template>
                <template #cell-rating="{ row }">
                  <span v-if="(row as McpListItem).avgRating != null" class="bm-rating">
                    ★ {{ (row as McpListItem).avgRating!.toFixed(1) }} ({{ (row as McpListItem).ratingCount }})
                  </span>
                  <span v-else class="bm-norating">{{ t('app.mcp.noRating') }}</span>
                </template>
              </PrismTable>
            </div>

            <button v-if="hasMoreOwn" type="button" class="bm-show-more" @click="ownVisible += OWN_CAP">
              {{ t('app.mcp.showMore', { n: ownMcps.length - ownVisible }) }}
            </button>
          </div>
        </template>
      </PrismTabs>

      <!-- 2026-11-05: multi-select draft (Cowork pattern) — chips of the MCPs
           selected in this popup; committed to the skill on OK. -->
      <div v-if="draft.length" class="bm-selected">
        <span class="bm-selected-label">{{ t('app.mcp.selectedCount', { n: draft.length }) }}</span>
        <div class="bm-selected-chips">
          <span v-for="d in draft" :key="d.id" class="bm-chip">
            {{ d.name }}
            <button
              type="button"
              class="bm-chip-remove"
              :aria-label="t('app.mcp.removeDepAria', { name: d.name })"
              @click="removeDraft(d)"
            >×</button>
          </span>
        </div>
      </div>
    </div>

    <template #footer>
      <!-- v1.28: "New MCP" moved from header to the footer (left of Close —
           same layout as the Add Skill Popup footer).
           2026-11-05: OK commits the multi-select draft (Cowork pattern). -->
      <PrismButton type="button" @click="emit('add-new-mcp')">
        <PlusCircle :size="16" aria-hidden="true" /> {{ t('app.mcp.addBtn') }}
      </PrismButton>
      <PrismButton variant="secondary" @click="emit('close')">{{ t('app.mcp.close') }}</PrismButton>
      <PrismButton :disabled="!draft.length" :loading="okLoading" @click="onOk">{{ t('app.common.ok') }}</PrismButton>
    </template>

    <!-- MCP Name link → MCP Detail Popup (both tabs) -->
    <McpDetailPopup
      v-if="detailMcpId !== null"
      :mcpId="detailMcpId"
      @close="detailMcpId = null"
      @updated="onDetailUpdated"
    />
  </PrismDialog>
</template>

<style scoped>
.bm {
  display: grid;
  gap: var(--ds-space-4);
}
.bm-zone {
  display: grid;
  gap: var(--ds-space-3);
}
/* v1.40: compact tabs (13px/34px) — same compact scale as the search input
   and the grid (.table-compact), instead of the 16px/44px design-system
   default. :deep() required — .p-tablist is inside PrismTabs. */
.bm :deep(.p-tablist) {
  gap: var(--ds-space-4);
}
.bm :deep(.p-tablist > button) {
  font-size: 13px;
  min-height: 34px;
  padding: 0 0 var(--ds-space-2);
}
.bm :deep(.p-tabpanel) {
  padding-top: var(--ds-space-3);
}
/* v1.28: compact search input (34px/13px) — same scale as the Register
   new MCP popup (.mcp-form-dialog), instead of the 44px/16px design-system
   default. :deep() required — PrismInput's .p-input-wrap is inside a child
   component, out of reach of plain scoped selectors.
   v1.40: search box lives inside the Approved tab panel. */
.bm .p-field {
  font-size: 13px;
}
.bm .p-field :deep(.p-input-wrap),
.bm .p-field :deep(.p-input-wrap input) {
  min-height: 34px;
  font-size: 13px;
}
.bm-grid {
  /* Row click = select (whole row is a target). */
  cursor: pointer;
  /* Cap the grid height so the dialog stays usable; the dialog itself
     scrolls (p-dialog overflow auto). */
  max-height: 420px;
  overflow-y: auto;
}
/* v1.28: fixed layout — the fixed-width columns (status/scope/type/views)
   take their declared width; Rating is fit-content; Name + Description are
   the flexible columns. Every text cell truncates to one line with "…" +
   tooltip so nothing overflows the popup. */
.bm-grid :deep(.p-table) {
  table-layout: fixed;
}
.bm-grid :deep(th:nth-child(1)),
.bm-grid :deep(td:nth-child(1)) {
  max-width: 220px;
}
.bm-grid :deep(th:nth-child(2)),
.bm-grid :deep(td:nth-child(2)) {
  max-width: 340px;
}
.bm-name {
  display: flex;
  align-items: center;
  gap: var(--ds-space-2);
  min-width: 0;
}
.bm-name-text {
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}
.bm-name.is-pinned {
  color: var(--ds-color-text-muted);
}
.bm-cell-text {
  display: block;
  width: 100%;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}
.bm-rating {
  color: var(--ds-color-accent);
}
.bm-norating {
  color: var(--ds-color-text-secondary);
  font-size: 13px;
}
.bm-selected-tag {
  font-size: 11px;
  padding: 1px var(--ds-space-2);
  border: 1px solid var(--ds-color-border);
  border-radius: 20px;
  color: var(--ds-color-text-muted);
  white-space: nowrap;
}
.bm-error-text {
  color: var(--ds-color-danger);
  font-size: 13px;
}
/* 2026-11-05: multi-select draft chips (Cowork pattern) */
.bm-selected {
  display: grid;
  gap: var(--ds-space-2);
  padding-top: var(--ds-space-3);
  border-top: 1px solid var(--ds-color-border);
}
.bm-selected-label {
  font-size: 11px;
  text-transform: uppercase;
  letter-spacing: .08em;
  color: var(--ds-color-text-muted);
}
.bm-selected-chips {
  display: flex;
  flex-wrap: wrap;
  gap: var(--ds-space-2);
  max-height: 120px;
  overflow-y: auto;
}
.bm-chip {
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
.bm-chip-remove {
  display: inline-flex;
  border: none;
  background: none;
  cursor: pointer;
  color: var(--ds-color-text-muted);
  padding: 0;
}
@media (hover: hover) { .bm-chip-remove:hover { color: var(--ds-color-danger); } }
.bm-pager {
  display: flex;
  justify-content: flex-end;
}

/* ── v1.40: tab "Your MCPs" — grid đồng nhất với tab Approved ─────────────── */
/* Only the REJECTED name link is specific to this tab (accent link inside the
   Name cell); everything else reuses the shared .bm-grid / .bm-name styles. */
.bm-your-mcps {
  display: grid;
  gap: var(--ds-space-2);
}
.bm-your-mcp-name-link {
  min-width: 0;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
  text-align: left;
  background: none;
  border: none;
  padding: 0;
  cursor: pointer;
  font: inherit;
  font-size: inherit;
  color: var(--ds-color-accent);
}
@media (hover: hover) {
  .bm-your-mcp-name-link:hover {
    text-decoration: underline;
  }
}
.bm-show-more {
  justify-self: start;
  background: none;
  border: none;
  padding: var(--ds-space-1) var(--ds-space-2);
  cursor: pointer;
  font: inherit;
  font-size: 13px;
  color: var(--ds-color-accent);
  border-radius: var(--ds-radius-sm);
}
@media (hover: hover) {
  .bm-show-more:hover {
    background: rgb(103 232 249 / .12);
  }
}
</style>
