import { api, request } from '@/lib/api'

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

export interface ContributorReputation {
  userId: number
  userName: string | null
  avatarUrl: string | null
  tier: string
  itemCount: number
  avgScore: number | null
  fulfilledRequests: number
  reportCount: number
}

export const reputationApi = {
  get: (targetType: string, targetId: number) =>
    request<Reputation>(api.get(`/reputation/${targetType}/${targetId}`)),
  explain: (targetType: string, targetId: number) =>
    request<ReputationExplain>(api.get(`/reputation/${targetType}/${targetId}/explain`)),
  /** Contributor (author) reputation (GĐ5, BA §8.5). */
  contributor: (userId: number) =>
    request<ContributorReputation>(api.get(`/reputation/CONTRIBUTOR/${userId}`)),
}
