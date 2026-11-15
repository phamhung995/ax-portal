<script setup lang="ts">
/**
 * MCP Dependencies section (FR-MCP-014/015).
 * Placed right under Skill Name in Add/Edit Skill Popup (Q-12).
 *
 * 2026-11-05: MULTI-SELECT (Cowork pattern) — the control is an input button
 * ("Selected (n)") that opens the Browse All MCPs Popup (FR-MCP-021), where
 * the owner toggles multiple MCPs and commits them with OK. Replaces the old
 * single-select combobox with 3 fuzzy suggestions (FR-MCP-013 — the Browse
 * popup's search covers the discovery need).
 *
 * - v1.18: each selected MCP = 1 chip (name = link → MCP Detail Popup,
 *   version pin shown, × to remove)
 * - Multiple chips allowed (1 skill → many MCP — Q-12)
 *
 * Exposed API (for the parent popups):
 * - addMcpsFromDetails(details) — batch-add MCPs from the Browse popup (single emit)
 * - refresh(force) — re-fetch detail of each pinned row (status sync)
 * - initFromExisting(items) — restore from a skill's existing dependencies
 */
import { computed, ref } from 'vue'
import { mcpApi } from '@/features/mcp/api'
import type { McpDetailResponse } from '@/features/mcp/types'
import { toastError } from '@/lib/toast'
import McpDetailPopup from './McpDetailPopup.vue'
import { useI18nStore } from '@/stores/i18n'
import { Link2 } from 'lucide-vue-next'

const { t } = useI18nStore()

export interface McpDependencyRow {
  mcpId: number
  mcpName: string
  mcpCode: string
  serverKey: string
  mcpVersion: number
  source: string
  availableVersions: number[] // APPROVED versions of this MCP
  /** v1.25 (Q-18 exception): master status — 'APPROVED' | 'DRAFT' | 'PENDING_APPROVAL' | 'REJECTED' */
  status: string
  /** 2026-11-05: MCP soft-deleted — chip shows a "deleted" badge, row stays. */
  deleted?: boolean
}

const props = defineProps<{
  modelValue: McpDependencyRow[]
  /** When true, section is read-only (skill APPROVED — FR-MCP-015 E1) */
  readonly?: boolean
  /** v1.31: validation error (section is optional — no required error) */
  error?: string
}>()

const emit = defineEmits<{
  'update:modelValue': [rows: McpDependencyRow[]]
  /** Input button clicked (FR-MCP-021) → parent opens the Browse All MCPs Popup. */
  'open-browse': []
}>()

/**
 * Add dependencies from full MCP details (Browse All MCPs Popup — FR-MCP-021).
 * Pinning: APPROVED → current_version; own unapproved MCP → current version
 * (Q-18 exception). Batch-add in a SINGLE emit — a per-item emit in a tight
 * loop reads the stale props.modelValue and overwrites the previous row.
 */
function addMcpsFromDetails(details: McpDetailResponse[]) {
  const existingIds = new Set(props.modelValue.map(r => r.mcpId))
  const newRows: McpDependencyRow[] = []
  for (const detail of details) {
    if (existingIds.has(detail.id)) {
      toastError(t('app.mcp.depAlready'))
      continue
    }
    existingIds.add(detail.id)
    const current = detail.version ?? 1
    newRows.push({
      mcpId: detail.id,
      mcpName: detail.name,
      mcpCode: detail.mcpCode,
      serverKey: detail.serverKey,
      mcpVersion: current,
      source: 'MANUAL',
      availableVersions: [current],
      status: detail.status,
    })
  }
  if (newRows.length) {
    emit('update:modelValue', [...props.modelValue, ...newRows])
  }
}

/**
 * 2026-11: initialize the section from a skill's existing dependencies
 * (Edit Skill / Update Version popups). Each row is re-fetched via the MCP
 * detail so availableVersions + name/code/serverKey are populated (the
 * skill detail only carries mcpId/mcpVersion/status).
 *
 * 2026-11-05: a soft-deleted MCP (detail 404) is KEPT as a row with
 * `deleted: true` (badge "MCP đã bị xóa") instead of being skipped —
 * the creator must see the dependency is gone and can remove it.
 */
async function initFromExisting(items: Array<{ mcpId: number; mcpVersion: number; status: string | null; name?: string | null }>) {
  const rows: McpDependencyRow[] = []
  for (const item of items) {
    try {
      const detail = await mcpApi.detail(item.mcpId)
      // Skill model: only the master's current version is pinnable.
      const current = detail.version ?? 1
      const availableVersions = [current]
      if (!availableVersions.includes(item.mcpVersion)) {
        availableVersions.push(item.mcpVersion)
      }
      rows.push({
        mcpId: detail.id,
        mcpName: detail.name,
        mcpCode: detail.mcpCode,
        serverKey: detail.serverKey,
        mcpVersion: item.mcpVersion,
        source: 'MANUAL',
        availableVersions,
        status: detail.status,
      })
    } catch {
      // MCP deleted (or not visible) — keep the row flagged as deleted so
      // the creator sees the dependency is gone.
      rows.push({
        mcpId: item.mcpId,
        mcpName: item.name || `MCP #${item.mcpId}`,
        mcpCode: '',
        serverKey: '',
        mcpVersion: item.mcpVersion,
        source: 'MANUAL',
        availableVersions: [item.mcpVersion],
        status: item.status || 'REJECTED',
        deleted: true,
      })
    }
  }
  emit('update:modelValue', rows)
}

function removeRow(index: number) {
  const rows = [...props.modelValue]
  rows.splice(index, 1)
  emit('update:modelValue', rows)
}

const selectedLabel = computed(() =>
  props.modelValue.length
    ? t('app.mcp.selectedCount', { n: props.modelValue.length })
    : ''
)

// v1.25: chip status color (DRAFT=neutral, PENDING_APPROVAL=warning, REJECTED=danger)
function statusTone(status: string): 'neutral' | 'warning' | 'danger' | 'success' {
  if (status === 'PENDING_APPROVAL') return 'warning'
  if (status === 'REJECTED') return 'danger'
  if (status === 'APPROVED') return 'success'
  return 'neutral'
}

function statusLabel(status: string): string {
  if (status === 'PENDING_APPROVAL') return t('app.common.statusPending')
  if (status === 'DRAFT') return t('app.common.statusDraft')
  if (status === 'REJECTED') return t('app.common.statusRejected')
  return t('app.common.statusApproved')
}

// v1.33 (FR-MCP-014 §14.1 item 13): banner warning — list the pinned MCPs
// that are NOT yet APPROVED (DRAFT/PENDING_APPROVAL). The skill cannot be
// submitted until every pinned MCP is APPROVED (FR-MCP-016). Derived from the
// chip `status` (kept in sync by the 30s refresh + detail-popup `updated`).
const notApprovedMcps = computed(() =>
  props.modelValue.filter(r => r.status !== 'APPROVED')
)

// v1.25: refresh — re-fetch detail of each pinned row so chip status colors
// follow MCP status changes (e.g. after Submit for Approval).
// Throttled to 30s (Q-30 pattern); force=true bypasses the throttle.
let lastRefreshAt = 0
const REFRESH_THROTTLE_MS = 30000

async function refresh(force = false) {
  if (!force && Date.now() - lastRefreshAt < REFRESH_THROTTLE_MS) return
  lastRefreshAt = Date.now()
  // Re-fetch detail of each pinned row to sync status
  const rows = props.modelValue
  if (rows.length) {
    await Promise.all(rows.map(async (row) => {
      try {
        const detail = await mcpApi.detail(row.mcpId)
        updateRowStatus(row.mcpId, detail.status)
      } catch {
        // non-fatal — keep stale status
      }
    }))
  }
}

function updateRowStatus(mcpId: number, status: string) {
  const rows = props.modelValue.map(r => r.mcpId === mcpId ? { ...r, status } : r)
  emit('update:modelValue', rows)
}

// v1.18: MCP name in a row is a link — opens the MCP Detail Popup.
const detailMcpId = ref<number | null>(null)
function openMcpDetail(mcpId: number) {
  detailMcpId.value = mcpId
}

// v1.25: MCP Detail Popup emitted 'updated' (e.g. after Submit for Approval)
// → re-fetch detail and update the chip status immediately.
async function onDetailUpdated() {
  if (detailMcpId.value === null) return
  try {
    const detail = await mcpApi.detail(detailMcpId.value)
    updateRowStatus(detail.id, detail.status)
  } catch {
    // non-fatal
  }
}

defineExpose({ addMcpsFromDetails, refresh, initFromExisting })
</script>

<template>
  <div class="mcp-dep-section">
    <!-- 2026-11-05: input button (Cowork pattern) → opens the Browse All
         MCPs Popup (multi-select). Same look as the Cowork field. -->
    <div class="p-field">
      <label>{{ t('app.mcp.depLabel') }}</label>
      <button
        v-if="!readonly"
        type="button"
        class="mcp-dep-input-btn"
        :aria-invalid="!!error || undefined"
        @click="emit('open-browse')"
      >
        <span v-if="modelValue.length" class="mcp-dep-input-value">{{ selectedLabel }}</span>
        <span v-else class="mcp-dep-input-placeholder">{{ t('app.mcp.depPh') }}</span>
      </button>
      <div v-else class="mcp-dep-input-btn is-readonly">
        <span v-if="modelValue.length" class="mcp-dep-input-value">{{ selectedLabel }}</span>
        <span v-else class="mcp-dep-input-placeholder">{{ t('app.mcp.noDeps') }}</span>
      </div>
      <p v-if="error" class="p-field-error">{{ error }}</p>
    </div>

    <!-- v1.18: dependency chips (replace the row table) — name is a link to
         the MCP Detail Popup, × removes (edit mode only).
         v1.25: chip border + status dot follow the MCP status (Q-18 exception) -->
    <div v-if="modelValue.length > 0" class="mcp-chips">
      <span
        v-for="(row, i) in modelValue"
        :key="row.mcpId"
        class="mcp-chip"
        :class="`is-${statusTone(row.status || 'APPROVED')}`"
      >
        <Link2 :size="14" class="mcp-chip-icon" aria-hidden="true" />
        <button
          v-if="!row.deleted"
          type="button"
          class="mcp-name-link"
          :title="row.mcpName"
          @click="openMcpDetail(row.mcpId)"
        >{{ row.mcpName }}</button>
        <span v-else class="mcp-name-link" :title="t('app.mcp.depDeletedTip')">{{ row.mcpName }}</span>
        <span class="mcp-chip-version">v{{ row.mcpVersion }}</span>
        <!-- 2026-11-05: deleted MCP badge (MCP soft-deleted — skill keeps the link) -->
        <span
          v-if="row.deleted"
          class="mcp-chip-deleted"
          :title="t('app.mcp.depDeletedTip')"
        >{{ t('app.mcp.depDeleted') }}</span>
        <!-- v1.30: status dot cho MỌI chip (v1.25: chỉ chip chưa duyệt) -->
        <span
          v-else
          class="mcp-chip-dot"
          :class="`is-${statusTone(row.status || 'APPROVED')}`"
          :title="statusLabel(row.status || 'APPROVED')"
          aria-hidden="true"
        ></span>
        <button
          v-if="!readonly"
          type="button"
          class="mcp-chip-remove"
          :aria-label="t('app.mcp.removeDepAria', { name: row.mcpName })"
          @click="removeRow(i)"
        >×</button>
      </span>
    </div>

    <!-- v1.33 (FR-MCP-014 §14.1 item 13): banner warning — list pinned MCPs
         not yet APPROVED. Auto-dismisses (derived from chip status) once every
         MCP is APPROVED. No banner when the skill has 0 MCPs.
         2026-11 rewrite (PO): clearer text + MCP name is a link → Detail. -->
    <div
      v-if="!readonly && notApprovedMcps.length > 0"
      class="mcp-pending-banner"
      role="alert"
    >
      <span class="mcp-pending-banner-label">
        {{ t('app.mcp.depBanner') }}
      </span>
      <span class="mcp-pending-banner-list">
        <template v-for="(row, i) in notApprovedMcps" :key="row.mcpId">
          <span v-if="i > 0">, </span>
          <button
            type="button"
            class="mcp-pending-banner-link"
            :title="row.mcpName"
            @click="openMcpDetail(row.mcpId)"
          >{{ row.mcpName }}</button>
          <span class="mcp-pending-banner-status">({{ statusLabel(row.status || 'DRAFT') }})</span>
        </template>
      </span>
    </div>

    <!-- v1.18: MCP Detail Popup (opened from the MCP name link in a chip) -->
    <McpDetailPopup
      v-if="detailMcpId !== null"
      :mcpId="detailMcpId"
      @close="detailMcpId = null"
      @updated="onDetailUpdated"
    />
  </div>
</template>

<style scoped>
.mcp-dep-section { margin-bottom: 0; }
/* 2026-11-05: input button (Cowork pattern) — same look/size as the normal
   form fields (44px/16px). */
.mcp-dep-input-btn {
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
  font-size: 16px;
  text-align: left;
  cursor: pointer;
  overflow: hidden;
  transition: border-color var(--ds-duration-fast), background var(--ds-duration-fast), box-shadow var(--ds-duration-fast);
}
.mcp-dep-input-btn:hover:not(:disabled) {
  border-color: var(--ds-color-border-strong, var(--ds-color-border));
}
.mcp-dep-input-btn:focus-visible {
  outline: none;
  border-color: var(--ds-color-accent);
  background: var(--ds-input-focus-background);
  box-shadow: 0 0 0 3px rgb(103 232 249 / .10), 0 0 14px rgb(103 232 249 / .05);
}
.mcp-dep-input-btn.is-readonly {
  cursor: default;
}
.mcp-dep-input-value {
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}
.mcp-dep-input-placeholder {
  color: var(--ds-color-text-muted);
}
/* v1.18 — dependency chips (replace the row table) */
/* v1.30: dòng chip cách ô input combobox 8px (v1.29: dính sát control) */
.mcp-chips {
  display: flex;
  flex-wrap: wrap;
  gap: var(--ds-space-2);
  margin-top: var(--ds-space-2);
}
.mcp-chip {
  display: inline-flex;
  align-items: center;
  gap: var(--ds-space-2);
  padding: 2px var(--ds-space-2);
  border: 1px solid var(--ds-color-border);
  border-radius: 20px;
  background: rgb(103 232 249 / .06);
}
/* v1.25 (Q-18 exception) → v1.30: chip border theo status cho MỌI chip
   (APPROVED = success/mint, PENDING = warning, REJECTED = danger, DRAFT = neutral) */
.mcp-chip.is-success { border-color: var(--ds-color-success); }
.mcp-chip.is-warning { border-color: var(--ds-color-warning); }
.mcp-chip.is-danger { border-color: var(--ds-color-danger); }
.mcp-chip.is-neutral { border-color: var(--ds-color-text-secondary); }
.mcp-chip-dot {
  width: 8px;
  height: 8px;
  border-radius: 50%;
  background: var(--ds-color-text-muted);
  flex-shrink: 0;
}
.mcp-chip-dot.is-success { background: var(--ds-color-success); }
.mcp-chip-dot.is-warning { background: var(--ds-color-warning); }
.mcp-chip-dot.is-danger { background: var(--ds-color-danger); }
.mcp-chip-dot.is-neutral { background: var(--ds-color-text-secondary); }
.mcp-chip-icon {
  color: var(--ds-color-text-muted);
  flex-shrink: 0;
}
.mcp-chip-version {
  color: var(--ds-color-text-muted);
  font-size: 12px;
}
/* 2026-11-05: deleted MCP badge on the chip (replaces the status dot) */
.mcp-chip-deleted {
  font-size: 10.5px;
  font-weight: 600;
  color: var(--ds-color-danger);
  border: 1px solid rgb(255 143 163 / .4);
  background: rgb(255 143 163 / .08);
  padding: 0 7px;
  border-radius: 999px;
  white-space: nowrap;
  flex-shrink: 0;
}
.mcp-chip-remove {
  border: none;
  background: none;
  cursor: pointer;
  color: var(--ds-color-text-muted);
  font-size: 14px;
  line-height: 1;
  padding: 0;
}
@media (hover: hover) { .mcp-chip-remove:hover { color: var(--ds-color-danger); } }
/* v1.18 — MCP name in a chip = link to the MCP Detail Popup */
.mcp-name-link {
  border: none;
  background: none;
  padding: 0;
  cursor: pointer;
  font: inherit;
  color: var(--ds-color-accent);
  text-align: left;
}
@media (hover: hover) { .mcp-name-link:hover { text-decoration: underline; } }
/* v1.33 (FR-MCP-014 §14.1 item 13): banner warning — pinned MCPs not yet
   APPROVED. Compact, sits under the chip row; auto-dismisses (derived from
   chip status) once every MCP is APPROVED. */
.mcp-pending-banner {
  display: flex;
  flex-wrap: wrap;
  align-items: center;
  gap: var(--ds-space-2);
  margin-top: var(--ds-space-2);
  padding: var(--ds-space-2) var(--ds-space-3);
  border: 1px solid var(--ds-color-warning);
  border-left: 3px solid var(--ds-color-warning);
  border-radius: var(--ds-radius-sm);
  background: rgb(251 191 36 / .08);
  font-size: 13px;
  color: var(--ds-color-text-secondary);
  line-height: 1.5;
}
.mcp-pending-banner-label {
  font-weight: 600;
  color: var(--ds-color-text-primary);
}
.mcp-pending-banner-list {
  color: var(--ds-color-text-secondary);
}
.mcp-pending-banner-link {
  border: none;
  background: none;
  padding: 0;
  cursor: pointer;
  font: inherit;
  font-weight: 600;
  color: var(--ds-color-accent);
}
@media (hover: hover) { .mcp-pending-banner-link:hover { text-decoration: underline; } }
.mcp-pending-banner-status {
  color: var(--ds-color-text-muted);
}
</style>
