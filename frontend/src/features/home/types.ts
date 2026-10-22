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
}

export interface CommunitySkills {
  hot: CommunitySkillItem[]
  trending: CommunitySkillItem[]
  new: CommunitySkillItem[]
}

/** A Feature Request row in the New Request card (Home SRS §7.3). */
export interface NewRequestItem {
  requestId: string
  title: string
  category: string
  status: string
  requesterName: string
  createdAt: string
}

/** Real activity data for the Home chart tab (GET /home/activity). */
export interface HomeActivity {
  weeks: number
  /** Per-week view counts, oldest first (length = weeks). */
  weeklyViews: number[]
  /** Per-week skill-update counts, oldest first (length = weeks). */
  weeklyUpdates: number[]
  /** Per-day view counts, oldest first (length = weeks × 7). */
  dailyViews: number[]
}
