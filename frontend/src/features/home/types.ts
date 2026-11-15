export interface HomeSummary {
  mySkills: number
  myPublished: number
  myPendingApproval: number
  myDraft: number
  myTotalViews: number
  myAverageRating: number | null
  myRatedSkillCount: number
  subscribedSkills: number
  myOpenRequests: number
  /** ADMIN_USER only — system-wide PENDING_APPROVAL count. */
  pendingReviews: number | null
  /** ADMIN_USER only — system-wide open Feature Request count. */
  pendingRequestReviews: number | null
}

export interface HomeSkillItem {
  skillId: string
  skillName: string
  status: string
  creatorName: string | null
  updatedAt: string
  viewCount: number | null
  ratingScore: number | null
  ratingCount: number
}

/** A skill row in the community cards (Home SRS §7.2). */
export interface CommunitySkillItem {
  skillId: number
  skillName: string
  creatorName: string | null
  status: string
  createdAt: string
  viewCount: number | null
  ratingScore: number | null
  ratingCount: number
  /** Category names (empty when the skill has no category). */
  categories: string[]
  /** Process stage names in pipeline order (empty when unmapped). */
  stages: string[]
}

export interface CommunitySkills {
  hot: CommunitySkillItem[]
  trending: CommunitySkillItem[]
  new: CommunitySkillItem[]
}

/** Chart tab selector data (Home_Chart_SRS §6.1). */
export interface ChartDimensions {
  groups: string[]
  partsByGroup: Record<string, string[]>
}

/** One Group/Part row in the Chart tab (Home_Chart_SRS §6.2). */
export interface ChartEntity {
  name: string
  approvedSkills: number
  /** null when no approved-in-window skill of the entity has a rating. */
  avgRating: number | null
  totalViews: number
  totalDownloads: number
  activeUsers: number
  newUsers: number
}

/** Chart tab data (Home_Chart_SRS §6.2). */
export interface ChartStats {
  period: string
  dimension: string
  group: string | null
  entities: ChartEntity[]
}
