import type { OrgRef } from '@/lib/types'

export interface LoginResponse {
  userId: number
  userName: string
  email: string
  role: string
  orgs: OrgRef[]
  avatarUrl: string | null
  accessToken: string
  refreshToken: string
}
