export interface TopCommentItem {
  id: number
  userId: number
  userName: string | null
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

export interface SkillRequestFeedItem {
  id: number
  title: string
  description: string | null
  hashtags: string[]
  voteCount: number
  commentCount: number
  creatorId: number
  creatorName: string | null
  createdAt: string
  hasVoted: boolean
  isRequestCreator: boolean
  hasResolvedComment: boolean
  topComments: TopCommentItem[]
  files: SkillRequestFileItem[]
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
  content: string
  createdAt: string
  canDelete: boolean
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

export interface TopRequesterItem {
  userId: number
  userName: string | null
  requestCount: number
  resolvedCount: number
}

export interface PopularHashtagItem {
  tag: string
  count: number
}

export interface SkillRequestStatsResult {
  topVoted: TopVotedItem[]
  topRequesters: TopRequesterItem[]
  popularHashtags: PopularHashtagItem[]
}
