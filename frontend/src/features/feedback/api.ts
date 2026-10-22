import { api, request } from '@/lib/api'
import type {
  FeedbackDetailResponse,
  FeedbackRequest,
  FeedbackSearchResult,
  FeedbackStatus,
} from './types'
import type { UserListItem } from '@/features/users/types'

export const feedbackApi = {
  submit: (payload: FeedbackRequest) =>
    request<void>(api.post('/feedback', payload)),
  /** The user's own feedback (opened from the notification bell). */
  detail: (id: number) =>
    request<FeedbackDetailResponse>(api.get(`/feedback/${id}`)),
  /** The user's own feedback list (VOC popup — My VOCs tab). */
  mine: (page: number, pageSize: number) =>
    request<FeedbackSearchResult>(
      api.get('/feedback/mine', { params: { page, pageSize } }),
    ),
}

export interface FeedbackSearchQuery {
  type?: string
  status?: string
  content?: string
  requesterId?: number
  resolvedById?: number
  page: number
  pageSize: number
}

export const adminFeedbackApi = {
  search: (query: FeedbackSearchQuery) =>
    request<FeedbackSearchResult>(api.post('/admin/feedback/search', query)),
  detail: (id: number) =>
    request<FeedbackDetailResponse>(api.get(`/admin/feedback/${id}`)),
  changeStatus: (id: number, payload: { status: FeedbackStatus; resolutionNote?: string }) =>
    request<void>(api.patch(`/admin/feedback/${id}/status`, payload)),
  /** User list for the Requester/Handler filter dropdowns. */
  userOptions: (keyword?: string, role?: string) =>
    request<UserListItem[]>(
      api.get('/admin/users/search-users', {
        params: { userName: keyword || undefined, role: role || undefined },
      }),
    ),
  /** Export the current search result (all pages) to .xlsx. */
  exportExcel: async (query: Omit<FeedbackSearchQuery, 'page' | 'pageSize'>): Promise<void> => {
    const response = await api.post('/admin/feedback/export', query, {
      responseType: 'blob',
      transformResponse: (data) => data,
    })
    const url = URL.createObjectURL(response.data as Blob)
    const anchor = document.createElement('a')
    anchor.href = url
    anchor.download = 'Feedback.xlsx'
    anchor.click()
    URL.revokeObjectURL(url)
  },
}
