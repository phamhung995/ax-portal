// AI News feature types (AI_News_Screen_SRS.md §5).

/** List row — NO body (Home SRS §7.4: list API never returns body). */
export interface AiNewsItem {
  newsId: number
  title: string
  sender: string | null
  source: string
  createdAt: string
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
