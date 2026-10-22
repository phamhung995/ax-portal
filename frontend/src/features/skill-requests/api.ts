import { api, request } from '@/lib/api'
import type {
  SkillRequestFeedItem,
  SkillRequestFeedResult,
  SkillRequestCommentPage,
  SkillRequestSuggestions,
  SkillRequestStatsResult,
} from './types'

export const skillRequestApi = {
  /** Feed page ranked by (vote*2 + comment) * time decay. */
  feed: (cursor: string | null, limit = 10, mine = false, title?: string) =>
    request<SkillRequestFeedResult>(
      api.get('/skill-requests/feed', {
        params: {
          cursor: cursor || undefined,
          limit,
          mine: mine || undefined,
          title: title || undefined,
        },
      })
    ),
  detail: (id: number) =>
    request<SkillRequestFeedItem>(api.get(`/skill-requests/${id}`)),
  create: (payload: { title: string; description?: string; hashtags: string[]; files?: File[] }) => {
    const form = new FormData()
    form.append('title', payload.title)
    if (payload.description) form.append('description', payload.description)
    form.append('hashtags', JSON.stringify(payload.hashtags))
    for (const file of payload.files ?? []) form.append('file', file)
    return request<number>(api.post('/skill-requests', form))
  },
  downloadFile: async (id: number, fileId: number): Promise<void> => {
    const response = await api.get(`/skill-requests/${id}/files/${fileId}/download`, {
      responseType: 'blob',
      transformResponse: (data) => data,
    })
    const disposition = response.headers['content-disposition'] as string | undefined
    const match = disposition?.match(/filename="?([^";]+)"?/)
    const filename = match?.[1] ?? 'skill-request-file'
    const url = URL.createObjectURL(response.data as Blob)
    const anchor = document.createElement('a')
    anchor.href = url
    anchor.download = filename
    anchor.click()
    URL.revokeObjectURL(url)
  },
  vote: (id: number) => request<void>(api.post(`/skill-requests/${id}/vote`)),
  unvote: (id: number) => request<void>(api.delete(`/skill-requests/${id}/vote`)),
  // ── Comments ──────────────────────────────────────────────────────────────
  comments: (id: number, page = 1, size = 10) =>
    request<SkillRequestCommentPage>(api.get(`/skill-requests/${id}/comments`, { params: { page, size } })),
  addComment: (id: number, content: string, linkedSkillId?: number, parentId?: number) =>
    request<void>(api.post(`/skill-requests/${id}/comments`, { content, linkedSkillId, parentId })),
  deleteComment: (id: number, commentId: number) =>
    request<void>(api.delete(`/skill-requests/${id}/comments/${commentId}`)),
  resolveComment: (id: number, commentId: number) =>
    request<void>(api.put(`/skill-requests/${id}/comments/${commentId}/resolve`)),
  likeComment: (id: number, commentId: number) =>
    request<void>(api.post(`/skill-requests/${id}/comments/${commentId}/like`)),
  unlikeComment: (id: number, commentId: number) =>
    request<void>(api.delete(`/skill-requests/${id}/comments/${commentId}/like`)),
  // ── Duplicate suggestions (option A) ──────────────────────────────────────
  suggestions: (keyword: string) =>
    request<SkillRequestSuggestions>(api.get('/skill-requests/suggestions', { params: { keyword } })),
  // ── Sidebar stats (top-10 cards) ──────────────────────────────────────────
  stats: () => request<SkillRequestStatsResult>(api.get('/skill-requests/stats')),
}
