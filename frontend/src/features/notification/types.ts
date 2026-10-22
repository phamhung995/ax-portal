export interface NotificationItem {
  notificationId: number
  title: string
  skillId: string | null
  feedbackId: number | null
  createdAt: string
  isRead: boolean
}
