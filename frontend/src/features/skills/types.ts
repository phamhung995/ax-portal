import type { OrgRef } from '@/lib/types'

export interface SkillListItem {
  id: number
  name: string
  description: string | null
  creatorId: number
  creatorName: string | null
  createdAt: string
  status: string
  scope: string
  systemKey: string | null
  subSystemCode: string | null
  categories: string[]
  stages: string[]
  viewCount: number
  avgRating: number | null
  ratingCount: number
  subscribed: boolean
  hasPendingVersion: boolean
  /** 'SHARE' | 'SCOPE' | null — why the skill is pending (null = legacy submit or version update). */
  pendingReason: string | null
  /** Number of shares awaiting admin approval (only when pendingReason = 'SHARE'). */
  pendingShareCount: number
  /** True when the current viewer is a cowork user of this skill. */
  isCowork: boolean
  /** Total download events recorded for this skill. */
  downloadCount: number
  /** Cached reputation score (0-100, null = not enough ratings yet). */
  reputationScore: number | null
  /** NEW | TRUSTED | POPULAR. */
  reputationTier: string
}

export interface SkillSearchResult {
  totalCount: number
  page: number
  pageSize: number
  items: SkillListItem[]
  /** Total skills per process stage (stage id → count) under the current criteria. */
  stageCounts: Record<string, number>
}

export interface SkillFileItem {
  id: number
  fileName: string
  fileSize: number
  createdAt: string
}

export interface SkillPendingVersionItem {
  name: string
  description: string | null
  publicScope: string
  systemKey: string | null
  subSystemCode: string | null
  hashtags: string[]
  categories: string[]
  stages: string[]
  newFiles: SkillFileItem[]
  removedFiles: SkillFileItem[]
}

export interface SkillDetailResponse {
  id: number
  name: string
  description: string | null
  status: string
  scope: string
  systemKey: string | null
  subSystemCode: string | null
  /** 2026-11-07: resolved display names (null when the master row is gone). */
  systemName: string | null
  subSystemName: string | null
  rejectionReason: string | null
  hashtags: string[]
  files: SkillFileItem[]
  viewCount: number
  downloadCount: number
  version: number
  creatorId: number
  creatorName: string | null
  createdAt: string
  updatedAt: string
  avgRating: number | null
  ratingCount: number
  isCreator: boolean
  isAdmin: boolean
  canEdit: boolean
  subscribed: boolean
  hasPendingVersion: boolean
  /** 'Y' when an admin has approved this skill at least once (V91). */
  adminReviewed: boolean
  /** 'SHARE' | 'SCOPE' | null — why the skill is pending (null = legacy submit or version update). */
  pendingReason: string | null
  /** The new scope awaiting approval (only when pendingReason = 'SCOPE'). */
  pendingScope: string | null
  /** Shares awaiting admin approval (only when pendingReason = 'SHARE'). */
  pendingShares: SkillShareItem[]
  pendingVersion: SkillPendingVersionItem | null
  categories: string[]
  stages: string[]
  /** Cowork users of the skill (empty when none). */
  coworkUsers: CoworkUserItem[]
  /** v1.31 (FR-MCP-015): MCP dependencies for the "MCPs (n)" tab. */
  mcpDependencies: SkillMcpDependencyItem[]
}

/** v1.31 (FR-MCP-015): one MCP dependency row on the Skill Detail "MCPs" tab. */
export interface SkillMcpDependencyItem {
  mcpId: number
  mcpCode: string
  mcpName: string | null
  serverKey: string
  mcpVersion: number
  source: string
  mcpStatus: string | null
  mcpDeleted: boolean
  availableForViewer: boolean
  /** The MCP detail page is visible to the viewer (name link clickable). */
  mcpVisibleToViewer: boolean
  files: SkillMcpDependencyFileItem[] | null
  /** The skill's public scope (PRIVATE/PROJECT = internal MCP access). */
  skillScope: string
}

export interface SkillMcpDependencyFileItem {
  fileId: number
  fileRole: string
  fileName: string
  fileSize: number
}

export interface CoworkUserItem {
  id: number
  name: string | null
}

export interface SkillVersionFileItem {
  id: number
  fileName: string
  fileSize: number
}

/** Skill comment (2026-11-08 v2): threaded depth 2 + likes + edit. */
export interface SkillCommentDTO {
  id: number
  userId: number
  userName: string | null
  avatarUrl: string | null
  parentId: number | null
  content: string
  likeCount: number
  replyCount: number
  createdAt: string
  updatedAt: string | null
  own: boolean
  liked: boolean
  canDelete: boolean
  canEdit: boolean
  replies: SkillCommentDTO[]
}

export interface SkillVersionItem {
  version: number
  name: string
  description: string | null
  publicScope: string
  systemKey: string | null
  subSystemCode: string | null
  hashtags: string[]
  creatorName: string | null
  createdAt: string
  files: SkillVersionFileItem[]
}

export interface SystemOption {
  systemKey: string
  systemName: string
}

export interface SubSystemOption {
  subSystemCode: string
  subSystemName: string
}

export interface SubSystemFilterOption {
  systemKey: string
  subSystemCode: string
  subSystemName: string
}

export interface SkillFilterOptions {
  systems: SystemOption[]
  subSystems: SubSystemFilterOption[]
}

export interface SkillShareItem {
  id: number
  shareType: 'USER' | 'PROJECT'
  userId: number | null
  userName: string | null
  projectKey: string | null
  /** PENDING | APPROVED | REJECTED (external shares await admin approval). */
  approvalStatus: 'PENDING' | 'APPROVED' | 'REJECTED'
}

/** Response of a share action (share users / share projects). */
export interface SkillShareResult {
  items: SkillShareItem[]
  /** Number of new shares that are PENDING (external). > 0 → skill went pending. */
  pendingCount: number
}

export interface TaxonomyOption {
  id: number
  name: string
  /** Short description (process stages only). */
  description?: string
}

export interface SkillTaxonomy {
  categories: TaxonomyOption[]
  stages: TaxonomyOption[]
}

export interface SkillTaxonomyItem {
  id: number
  name: string
  sortOrder: number
  description: string | null
  skillCount: number
}

export interface SkillDeleteCheckResponse {
  canDelete: boolean
  deniedSkills: { id: number; name: string; creatorName: string | null }[]
}

export interface SearchUserItem {
  id: number
  name: string
  knoxId: string
  orgs: OrgRef[]
}

export interface SearchUserResult {
  totalCount: number
  page: number
  pageSize: number
  items: SearchUserItem[]
}
