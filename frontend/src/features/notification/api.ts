import { api, request } from '@/lib/api'
import type { NotificationItem } from './types'

export const notificationApi = {
  unreadCount: () => request<{ unreadCount: number }>(api.get('/notifications/unread-count')),
  list: (limit = 10) => request<NotificationItem[]>(api.get('/notifications', { params: { limit } })),
  markRead: (notificationId: number) =>
    request<void>(api.post(`/notifications/${notificationId}/read`)),
  markAllRead: () => request<void>(api.post('/notifications/read-all')),
}
