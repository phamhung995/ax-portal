import { defineStore } from 'pinia'
import { reactive, watch } from 'vue'
import type { SkillRequestFeedItem, SkillRequestFeedResult } from './types'
import { useAuthStore } from '@/stores/auth'

/**
 * Holds the skill request feed state so it survives navigation away and back.
 * Cursor-based infinite scroll: items are appended, nextCursor tracks the
 * position of the last loaded item.
 * Reset on logout so the next session starts clean.
 */
export const useSkillRequestFeedStore = defineStore('skillRequestFeed', () => {
  const state = reactive({
    items: [] as SkillRequestFeedItem[],
    nextCursor: null as string | null,
    hasMore: true,
    loading: false,
    error: '',
    mine: false,
    title: '',
    /** userId of the viewer whose feed is currently cached (null = never loaded). */
    loadedBy: null as number | null,
  })

  function reset() {
    state.items = []
    state.nextCursor = null
    state.hasMore = true
    state.error = ''
  }

  function appendResult(result: SkillRequestFeedResult, viewerId: number) {
    state.items.push(...result.items)
    state.nextCursor = result.nextCursor
    state.hasMore = result.hasMore
    state.error = ''
    state.loadedBy = viewerId
  }

  const auth = useAuthStore()
  watch(
    () => auth.user,
    (user) => {
      if (!user) {
        reset()
        state.mine = false
        state.title = ''
      }
    }
  )

  return { state, reset, appendResult }
})
