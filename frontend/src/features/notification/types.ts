export interface NotificationItem {
  notificationId: number
  title: string
  skillId: string | null
  feedbackId: number | null
  requestId: number | null
  newsId: number | null
  commentId: number | null
  mcpId: number | null
  createdAt: string
  isRead: boolean
}
