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
  /** Staked bounty points (0 = no bounty). */
  bountyPoints: number
  /** NONE | ACTIVE | FULFILLED | EXPIRED | REFUNDED */
  bountyStatus: string
  bountyExpiresAt: string | null
  fulfilledSkillId: number | null
}

export interface BountyClaim {
  id: number
  requestId: number
  skillId: number
  skillName: string | null
  claimantId: number
  claimantName: string | null
  claimType: 'BUILD' | 'MATCH'
  status: 'PENDING' | 'ACCEPTED' | 'REJECTED' | 'DISPUTED'
  decideBy: string
  createdAt: string
}

export interface BountyInfo {
  requestId: number
  bountyPoints: number
  bountyStatus: string
  bountyExpiresAt: string | null
  fulfilledSkillId: number | null
  fulfilledBy: number | null
  bountyAwardedAt: string | null
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
