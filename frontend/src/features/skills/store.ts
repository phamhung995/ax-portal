import { defineStore } from 'pinia'
import { reactive, watch } from 'vue'
import type { SkillListItem, SkillSearchResult } from './types'
import { useAuthStore } from '@/stores/auth'

/**
 * Holds the Skill Management list state so it survives navigation away and
 * back (grid state, search criteria, selection).
 * Reset on logout so the next session starts with clean search criteria.
 */
export const useSkillListStore = defineStore('skillList', () => {
  const state = reactive({
    skillName: '',
    description: '',
    tags: [] as string[],
    creatorId: null as number | null,
    creatorName: '',
    createdDateFrom: '',
    createdDateTo: '',
    statuses: [] as string[],
    scopes: [] as string[],
    categoryIds: [] as number[],
    /** System / Sub System markers (search filters). */
    systemKey: '',
    subSystemCode: '',
    /** Stage selected in the Process Map pipeline (null = no stage filter). */
    stageId: null as number | null,
    /** Subscription filter from Home KPI deep-link or the search form (ALL | Y | N). */
    subscribed: 'ALL' as 'ALL' | 'Y' | 'N',
    /** Active view tab (grid | map) — survives navigation away and back. */
    view: 'grid' as 'grid' | 'map',
    page: 1,
    pageSize: 20,
    sortBy: 'createdDate',
    sortDirection: 'DESC',
    totalCount: 0,
    items: [] as SkillListItem[],
    /** Total skills per process stage (stage id → count) from the last search. */
    stageCounts: {} as Record<string, number>,
    hasSearched: false,
    /** userId of the viewer whose results are currently cached (null = never searched). */
    searchedBy: null as number | null,
    loading: false,
    error: '',
    /** Set when a skill was created/edited/deleted; the view re-runs its search once. */
    needsRefresh: false,
  })

  function clear() {
    state.skillName = ''
    state.description = ''
    state.tags = []
    state.creatorId = null
    state.creatorName = ''
    state.createdDateFrom = ''
    state.createdDateTo = ''
    state.statuses = []
    state.scopes = []
    state.categoryIds = []
    state.systemKey = ''
    state.subSystemCode = ''
    state.stageId = null
    state.subscribed = 'ALL'
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

  function applyResult(result: SkillSearchResult, viewerId: number, stageCounts?: Record<string, number>) {
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
