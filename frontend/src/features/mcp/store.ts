import { defineStore } from 'pinia'
import { reactive, watch } from 'vue'
import type { McpListItem, McpSearchResult } from './types'
import { useAuthStore } from '@/stores/auth'

/**
 * Holds the MCP Management list state so it survives navigation away and
 * back (search criteria, pagination, results). Mirrors useSkillListStore.
 * Reset on logout so the next session starts with clean search criteria.
 */
export const useMcpListStore = defineStore('mcpList', () => {
  const state = reactive({
    mcpName: '',
    description: '',
    tags: [] as string[],
    creatorId: null as number | null,
    creatorName: '',
    createdDateFrom: '',
    createdDateTo: '',
    statuses: [] as string[],
    categoryIds: [] as number[],
    /** Stage selected in the Process Map pipeline (null = no stage filter). */
    stageId: null as number | null,
    /** Active view tab (grid | map) — survives navigation away and back. */
    view: 'grid' as 'grid' | 'map',
    /** Subscription filter from the search form (ALL | Y | N). */
    subscribed: 'ALL' as 'ALL' | 'Y' | 'N',
    page: 1,
    pageSize: 20,
    sortBy: 'createdDate',
    sortDirection: 'DESC',
    totalCount: 0,
    items: [] as McpListItem[],
    /** Total MCPs per process stage (stage id → count) from the last search. */
    stageCounts: {} as Record<string, number>,
    hasSearched: false,
    /** userId of the viewer whose results are currently cached (null = never searched). */
    searchedBy: null as number | null,
    loading: false,
    error: '',
    /** Set when an MCP was created/edited/deleted; the view re-runs its search once. */
    needsRefresh: false,
  })

  function clear() {
    state.mcpName = ''
    state.description = ''
    state.tags = []
    state.creatorId = null
    state.creatorName = ''
    state.createdDateFrom = ''
    state.createdDateTo = ''
    state.statuses = []
    state.categoryIds = []
    state.stageId = null
    state.view = 'grid'
    state.subscribed = 'ALL'
    state.page = 1
    state.totalCount = 0
    state.items = []
    state.stageCounts = {}
    state.hasSearched = false
    state.searchedBy = null
    state.error = ''
    state.needsRefresh = false
    state.sortBy = 'createdDate'
    state.sortDirection = 'DESC'
  }

  function markDirty() {
    state.needsRefresh = true
  }

  function applyResult(result: McpSearchResult, viewerId: number, stageCounts?: Record<string, number>) {
    state.totalCount = result.totalCount
    state.items = result.items
    if (stageCounts) state.stageCounts = stageCounts
    state.hasSearched = true
    state.searchedBy = viewerId
    state.error = ''
    state.needsRefresh = false
  }

  const auth = useAuthStore()
  watch(
    () => auth.user,
    (user) => {
      if (!user) clear()
    }
  )

  return { state, clear, markDirty, applyResult }
})
