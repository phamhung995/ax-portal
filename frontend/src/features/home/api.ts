import { api, request } from '@/lib/api'
import type { CommunitySkills, HomeActivity, HomeSkillItem, HomeSummary, NewRequestItem } from './types'

export const homeApi = {
  summary: () => request<HomeSummary>(api.get('/home/summary')),
  myRecentSkills: () => request<HomeSkillItem[]>(api.get('/home/my-recent-skills')),
  communitySkills: () => request<CommunitySkills>(api.get('/home/community-skills')),
  newRequests: (limit = 8) =>
    request<NewRequestItem[]>(api.get('/home/new-requests', { params: { limit } })),
  activity: (weeks = 6) =>
    request<HomeActivity>(api.get('/home/activity', { params: { weeks } })),
}
