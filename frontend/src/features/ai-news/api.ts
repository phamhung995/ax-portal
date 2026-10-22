import { api, request } from '@/lib/api'
import type { AiNewsCommentItem, AiNewsDetail, AiNewsItem } from './types'

/**
 * AI News endpoints (AI_News_Screen_SRS.md §5.6).
 * All under /api/v1/home/ai-news.
 */
export const aiNewsApi = {
  list: (limit = 8) => request<AiNewsItem[]>(api.get('/home/ai-news', { params: { limit } })),
  detail: (newsId: number, signal?: AbortSignal) =>
    request<AiNewsDetail>(api.get(`/home/ai-news/${newsId}`, { signal })),
  comments: (newsId: number) =>
    request<AiNewsCommentItem[]>(api.get(`/home/ai-news/${newsId}/comments`)),
  addComment: (newsId: number, content: string, parentId?: number) =>
    request<AiNewsCommentItem>(api.post(`/home/ai-news/${newsId}/comments`, { content, parentId: parentId ?? null })),
  deleteComment: (newsId: number, commentId: number) =>
    request<void>(api.delete(`/home/ai-news/${newsId}/comments/${commentId}`)),
  toggleHelpful: (newsId: number, commentId: number) =>
    request<{ helpfulCount: number }>(api.post(`/home/ai-news/${newsId}/comments/${commentId}/helpful`)),
  toggleLike: (newsId: number) =>
    request<{ likeCount: number }>(api.post(`/home/ai-news/${newsId}/like`)),
}
