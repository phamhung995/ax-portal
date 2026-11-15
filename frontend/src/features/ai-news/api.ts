import { api, request } from '@/lib/api'
import type { AiNewsCommentItem, AiNewsDetail, AiNewsItem, MhtImportResult, MentionUser } from './types'

/** Optional advanced-search filters (all blank/undefined = no filter). */
export interface AiNewsSearchFilters {
  title?: string
  source?: string
  sender?: string
  /** ISO date YYYY-MM-DD (inclusive). */
  from?: string
  /** ISO date YYYY-MM-DD (inclusive). */
  to?: string
}

/**
 * AI News endpoints (AI_News_Screen_SRS.md §5.6).
 * All under /api/v1/home/ai-news.
 */
export const aiNewsApi = {
  list: (limit = 8, filters?: AiNewsSearchFilters) =>
    request<AiNewsItem[]>(api.get('/home/ai-news', {
      params: {
        limit,
        ...(filters?.title ? { title: filters.title } : {}),
        ...(filters?.source ? { source: filters.source } : {}),
        ...(filters?.sender ? { sender: filters.sender } : {}),
        ...(filters?.from ? { from: filters.from } : {}),
        ...(filters?.to ? { to: filters.to } : {}),
      },
    })),
  /** @mention autocomplete: up to 10 users matching the query (name/email). */
  mentionUsers: (q: string, signal?: AbortSignal) =>
    request<MentionUser[]>(api.get('/home/ai-news/mention-users', { params: { q }, signal })),
  detail: (newsId: number, signal?: AbortSignal) =>
    request<AiNewsDetail>(api.get(`/home/ai-news/${newsId}`, { signal })),
  comments: (newsId: number, signal?: AbortSignal) =>
    request<AiNewsCommentItem[]>(api.get(`/home/ai-news/${newsId}/comments`, { signal })),
  addComment: (newsId: number, content: string, parentId?: number) =>
    request<AiNewsCommentItem>(api.post(`/home/ai-news/${newsId}/comments`, { content, parentId: parentId ?? null })),
  deleteComment: (newsId: number, commentId: number) =>
    request<void>(api.delete(`/home/ai-news/${newsId}/comments/${commentId}`)),
  toggleHelpful: (newsId: number, commentId: number) =>
    request<{ helpfulCount: number }>(api.post(`/home/ai-news/${newsId}/comments/${commentId}/helpful`)),
  toggleLike: (newsId: number) =>
    request<{ likeCount: number }>(api.post(`/home/ai-news/${newsId}/like`)),
  /** Record a news view (idempotent per user — fired after 30s of reading). */
  recordView: (newsId: number) =>
    request<{ viewCount: number }>(api.post(`/home/ai-news/${newsId}/view`)),
  /** Manual .mht import (admin only — AI_News_Screen_SRS.md v1.14 §5.7). */
  adminImport: (files: File[]) => {
    const form = new FormData()
    for (const file of files) form.append('files', file)
    // Long timeout: the response embeds base64 data-URIs for oversized CID
    // images (a 4MB .mht can produce a ~4MB JSON response), which exceeds
    // the 30s client timeout on slow connections.
    return request<MhtImportResult[]>(
      api.post('/admin/ai-news/import', form, { timeout: 120000 }),
    )
  },
}
