import { api, request } from '@/lib/api'
import type {
  ChartDimensions,
  ChartStats,
  CommunitySkills,
  HomeSkillItem,
  HomeSummary,
} from './types'

export const homeApi = {
  summary: () => request<HomeSummary>(api.get('/home/summary')),
  myRecentSkills: () => request<HomeSkillItem[]>(api.get('/home/my-recent-skills')),
  communitySkills: () => request<CommunitySkills>(api.get('/home/community-skills')),
  /** Chart tab (admin) — selector data: groups + parts per group. */
  chartDimensions: () => request<ChartDimensions>(api.get('/home/chart-dimensions')),
  /** Chart tab (admin) — metrics per Group/Part for the selected window. */
  chartStats: (period: string, dimension: string, group?: string) =>
    request<ChartStats>(api.get('/home/chart-stats', { params: { period, dimension, group } })),
}
