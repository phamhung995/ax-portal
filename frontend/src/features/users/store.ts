import { defineStore } from 'pinia'
import { reactive, watch } from 'vue'
import type { UserListItem, UserSearchResult } from './types'
import { useAuthStore } from '@/stores/auth'

/**
 * Holds the User Management list state so it survives navigation to
 * Create User / User Detail / Bulk Upload and back (SRS §9.2, §6.11).
 * Reset on logout so the next session starts with clean search criteria.
 */
export const useUserListStore = defineStore('userList', () => {
  const state = reactive({
    userName: '',
    email: '',
    organization: '',
    roles: [] as string[],
    statuses: [] as string[],
    createdDateFrom: '',
    createdDateTo: '',
    page: 1,
    pageSize: 20,
    sortBy: 'createdDate',
    sortDirection: 'DESC',
    totalCount: 0,
    items: [] as UserListItem[],
    hasSearched: false,
    loading: false,
    error: '',
    /** Set when a user was modified (detail save); the list view re-runs its search once. */
    needsRefresh: false,
  })

  function clear() {
    state.userName = ''
    state.email = ''
    state.organization = ''
    state.roles = []
    state.statuses = []
    state.createdDateFrom = ''
    state.createdDateTo = ''
    state.page = 1
    state.totalCount = 0
    state.items = []
    state.hasSearched = false
    state.error = ''
    state.needsRefresh = false
  }

  function markDirty() {
    state.needsRefresh = true
  }

  function applyResult(result: UserSearchResult) {
    state.totalCount = result.totalCount
    state.items = result.items
    state.hasSearched = true
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
