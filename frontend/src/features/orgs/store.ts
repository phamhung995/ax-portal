import { defineStore } from 'pinia'
import { reactive, watch } from 'vue'
import type { OrgListItem, OrgSearchResult } from './types'
import { useAuthStore } from '@/stores/auth'

/**
 * Holds the Organization Management list state so it survives navigation
 * away from and back to the grid (pattern: userList.ts).
 * Reset on logout so the next session starts with clean search criteria.
 */
export const useOrgListStore = defineStore('orgList', () => {
  const state = reactive({
    knoxId: '',
    userName: '',
    company: '',
    part: '',
    group: '',
    employed: '',
    page: 1,
    pageSize: 20,
    totalCount: 0,
    items: [] as OrgListItem[],
    hasSearched: false,
    loading: false,
    error: '',
  })

  function clear() {
    state.knoxId = ''
    state.userName = ''
    state.company = ''
    state.part = ''
    state.group = ''
    state.employed = ''
    state.page = 1
    state.totalCount = 0
    state.items = []
    state.hasSearched = false
    state.error = ''
  }

  function applyResult(result: OrgSearchResult) {
    state.totalCount = result.totalCount
    state.items = result.items
    state.hasSearched = true
    state.error = ''
  }

  const auth = useAuthStore()
  watch(
    () => auth.user,
    (user) => {
      if (!user) clear()
    }
  )

  return { state, clear, applyResult }
})
