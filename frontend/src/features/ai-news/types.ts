// AI News feature types (AI_News_Screen_SRS.md §5).

/** List row — NO body (Home SRS §7.4: list API never returns body). */
export interface AiNewsItem {
  newsId: number
  title: string
  sender: string | null
  source: string
  createdAt: string
  likeCount: number
  commentCount: number
  /** Distinct-viewer count (users who read this news for 30s+). */
  viewCount: number
  /** Cached reputation score (0-100, null = not enough views yet). */
  reputationScore: number | null
  /** Cached reputation tier (NEW | TRUSTED | POPULAR). */
  reputationTier: string
}

/** Detail — includes the sanitized HTML body. */
export interface AiNewsDetail {
  newsId: number
  title: string
  sender: string | null
  source: string
  body: string | null
  createdAt: string
  likeCount: number
  liked: boolean
  /** Distinct-viewer count (users who read this news for 30s+). */
  viewCount: number
}

/** Comment row. deleted = true → tombstone "[deleted]" (FR-19). */
export interface AiNewsCommentItem {
  commentId: number
  newsId: number
  parentId: number | null
  userName: string
  content: string | null
  helpfulCount: number
  replyCount: number
  createdAt: string
  deleted: boolean
  own: boolean
  voted: boolean
}

/** @mention autocomplete candidate (id + display name only). */
export interface MentionUser {
  id: number
  name: string
}

/**
 * Per-file result of a manual .mht import (AI_News_Screen_SRS.md v1.14 §5.7).
 * status: INSERTED | SKIPPED_DUPLICATE | FAILED
 */
export interface MhtImportResult {
  index: number
  fileName: string
  status: 'INSERTED' | 'SKIPPED_DUPLICATE' | 'FAILED'
  newsId: number | null
  error: string | null
}
