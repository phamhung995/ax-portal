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
    /** Stage selected in the Process Map pipeline (null = no stage filter). */
    stageId: null as number | null,
    /** Active view tab (grid | map) — survives navigation away and back. */
    view: 'grid' as 'grid' | 'map',
    page: 1,
    pageSize: 20,
    sortBy: 'createdDate',
    sortDirection: 'DESC',
    totalCount: 0,
    items: [] as SkillListItem[],
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
    state.stageId = null
    state.view = 'grid'
    state.page = 1
    state.totalCount = 0
    state.items = []
    state.hasSearched = false
    state.searchedBy = null
    state.error = ''
    state.needsRefresh = false
  }

  function markDirty() {
    state.needsRefresh = true
  }

  function applyResult(result: SkillSearchResult, viewerId: number) {
    state.totalCount = result.totalCount
    state.items = result.items
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
