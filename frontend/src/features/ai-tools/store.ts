import { defineStore } from 'pinia'
import { reactive, watch } from 'vue'
import type { AiToolListItem, AiToolSearchResult } from './types'
import { useAuthStore } from '@/stores/auth'

/**
 * Holds the AI Tools list state so it survives navigation away and back
 * (grid state, search criteria, selection).
 * Reset on logout so the next session starts with clean search criteria.
 */
export const useAiToolListStore = defineStore('aiToolList', () => {
  const state = reactive({
    name: '',
    tags: [] as string[],
    uploaderId: null as number | null,
    uploaderName: '',
    createdDateFrom: '',
    createdDateTo: '',
    categoryIds: [] as number[],
    /** Stage selected in the Process Map pipeline (null = no stage filter). */
    stageId: null as number | null,
    /** Active view tab (grid | map) — survives navigation away and back. */
    view: 'grid' as 'grid' | 'map',
    page: 1,
    pageSize: 20,
    sortBy: 'createdDate',
    sortDirection: 'DESC',
    totalCount: 0,
    items: [] as AiToolListItem[],
    /** Total tools per process stage (stage id → count) from the last search. */
    stageCounts: {} as Record<string, number>,
    hasSearched: false,
    /** userId of the viewer whose results are currently cached (null = never searched). */
    searchedBy: null as number | null,
    loading: false,
    error: '',
    /** Set when a tool was created/edited/deleted; the view re-runs its search once. */
    needsRefresh: false,
  })

  function clear() {
    state.name = ''
    state.tags = []
    state.uploaderId = null
    state.uploaderName = ''
    state.createdDateFrom = ''
    state.createdDateTo = ''
    state.categoryIds = []
    state.stageId = null
    state.view = 'grid'
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

  function applyResult(result: AiToolSearchResult, viewerId: number, stageCounts?: Record<string, number>) {
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
