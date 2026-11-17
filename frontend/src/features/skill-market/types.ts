export interface TopCommentItem {
  id: number
  userId: number
  userName: string | null
  userAvatarUrl: string | null
  content: string
  createdAt: string
  linkedSkillId: number | null
  linkedSkillName: string | null
  linkedSkillStatus: string | null
  resolvedYn: string
  likeCount: number
  likedByMe: boolean
}

export interface SkillRequestFileItem {
  id: number
  fileName: string
  fileSize: number | null
  isImage: boolean
  /** Public URL of an image attachment (null for non-image files). */
  url: string | null
}

export interface ResolvedSkillItem {
  skillId: number
  skillName: string | null
  skillStatus: string | null
  /** Name of the user who wrote the resolved comment. */
  commenterName: string | null
  /** Knox ID of the user who wrote the resolved comment. */
  commenterKnoxId: string | null
}

export interface SkillRequestFeedItem {
  id: number
  title: string
  description: string | null
  hashtags: string[]
  voteCount: number
  commentCount: number
  creatorId: number
  creatorName: string | null
  creatorAvatarUrl: string | null
  createdAt: string
  hasVoted: boolean
  isRequestCreator: boolean
  hasResolvedComment: boolean
  /** Skills linked in resolved comments (earliest first, max 3). */
  resolvedSkills: ResolvedSkillItem[]
  /** Total number of resolved skills (may exceed resolvedSkills.length). */
  resolvedSkillCount: number
  topComments: TopCommentItem[]
  files: SkillRequestFileItem[]
  /** Cached reputation score (null = not enough verified votes yet, NEW). */
  reputationScore: number | null
  /** NEW | TRUSTED | POPULAR. */
  reputationTier: string
  /** Fulfillment (GĐ5, Q8): OPEN | PENDING_CONFIRM | FULFILLED. */
  fulfillmentStatus: string
  /** Skill proposed/accepted as the answer (null unless pending/fulfilled). */
  fulfilledSkillId: number | null
  /** Name of the fulfilling skill (null unless pending/fulfilled). */
  fulfilledSkillName: string | null
  /** Requester decision deadline, ISO string (null unless PENDING_CONFIRM). */
  fulfillmentDeadline: string | null
}

/** Contributor (author) reputation (GĐ5, BA §8.5). */
export interface ContributorReputation {
  userId: number
  userName: string | null
  avatarUrl: string | null
  /** NEW_MEMBER | VERIFIED | TRUSTED_AUTHOR | EXPERT. */
  tier: string
  itemCount: number
  avgScore: number | null
  fulfilledRequests: number
  reportCount: number
}

export interface SkillRequestFeedResult {
  items: SkillRequestFeedItem[]
  nextCursor: string | null
  hasMore: boolean
}

export interface SkillRequestCommentItem {
  id: number
  userId: number
  userName: string | null
  userAvatarUrl: string | null
  content: string
  createdAt: string
  canDelete: boolean
  canEdit: boolean
  parentId: number | null
  linkedSkillId: number | null
  linkedSkillName: string | null
  linkedSkillStatus: string | null
  resolvedYn: string
  resolvedAt: string | null
  likeCount: number
  likedByMe: boolean
  replies: SkillRequestCommentItem[]
}

export interface SkillRequestCommentPage {
  totalCount: number
  page: number
  size: number
  items: SkillRequestCommentItem[]
}

export interface SkillSuggestion {
  id: number
  name: string
  status: string
  creatorName: string | null
}

export interface RequestSuggestion {
  id: number
  title: string
  voteCount: number
}

export interface SkillRequestSuggestions {
  skills: SkillSuggestion[]
  requests: RequestSuggestion[]
}

export interface TopVotedItem {
  id: number
  title: string
  voteCount: number
  commentCount: number
}

export interface TopContributorItem {
  userId: number
  userName: string | null
  userAvatarUrl: string | null
  skillLinkCount: number
}

export interface PopularHashtagItem {
  tag: string
  count: number
}

export interface SkillRequestStatsResult {
  topVoted: TopVotedItem[]
  topContributors: TopContributorItem[]
  popularHashtags: PopularHashtagItem[]
}
