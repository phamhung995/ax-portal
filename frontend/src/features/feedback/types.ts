export type FeedbackType = 'BUG' | 'SUGGESTION' | 'QUESTION'
export type FeedbackStatus = 'NEW' | 'IN_REVIEW' | 'RESOLVED' | 'IGNORED'

export interface FeedbackRequest {
  type: FeedbackType
  content: string
  screenshot?: string
}

export interface FeedbackItem {
  id: number
  userId: number
  userName: string | null
  userEmail: string | null
  type: FeedbackType
  content: string
  status: FeedbackStatus
  createdAt: string
  hasScreenshot: boolean
  resolvedByName: string | null
}

export interface FeedbackSearchResult {
  totalCount: number
  page: number
  pageSize: number
  items: FeedbackItem[]
}

export interface FeedbackDetailResponse {
  id: number
  userId: number
  userName: string | null
  userEmail: string | null
  type: FeedbackType
  content: string
  screenshot: string | null
  status: FeedbackStatus
  resolutionNote: string | null
  resolvedByName: string | null
  resolvedAt: string | null
  createdAt: string
}
