import { api, request } from '@/lib/api'

export interface GamificationMe {
  balance: number
  checkedInToday: boolean
  streak: number
  activeRedemptions: number
}

export interface CheckInResult {
  balance: number
  streak: number
  awardedPoints: number
  bonusPoints: number
  alreadyCheckedIn: boolean
}

export interface PointsHistoryItem {
  id: number
  amount: number
  type: string
  reason: string | null
  createdAt: string
}

export interface Perk {
  id: number
  code: string
  name: string
  description: string | null
  cost: number
  active: string
}

export interface Reputation {
  targetType: string
  targetId: number
  score: number | null
  tier: string
  ratingCount: number
  ratingAvg: number | null
  downloadCount: number
  successRate: number | null
}

export interface ReputationExplain {
  targetType: string
  targetId: number
  score: number | null
  tier: string
  reasons: string[]
}

export const gamificationApi = {
  me: () => request<GamificationMe>(api.get('/gamification/me')),
  checkIn: () => request<CheckInResult>(api.post('/gamification/check-in')),
  history: (page = 1, pageSize = 20) =>
    request<PointsHistoryItem[]>(api.get('/gamification/history', { params: { page, pageSize } })),
  perks: () => request<Perk[]>(api.get('/gamification/perks')),
  redeemPerk: (perkId: number) => request<unknown>(api.post(`/gamification/perks/${perkId}/redeem`)),
}

export const reputationApi = {
  get: (targetType: string, targetId: number) =>
    request<Reputation>(api.get(`/reputation/${targetType}/${targetId}`)),
  explain: (targetType: string, targetId: number) =>
    request<ReputationExplain>(api.get(`/reputation/${targetType}/${targetId}/explain`)),
}
