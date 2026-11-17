/**
 * MCP Management types (MCP Management SRS v2.0 — rebuilt on the Skill model,
 * PUBLIC only).
 */

export interface McpListItem {
  id: number
  name: string
  description: string | null
  mcpCode: string
  serverKey: string
  creatorId: number
  creatorName: string | null
  createdAt: string
  status: string
  categories: string[]
  stages: string[]
  viewCount: number
  avgRating: number | null
  ratingCount: number
  subscribed: boolean
  hasPendingVersion: boolean
  downloadCount: number
  /** Cached reputation score (0-100, null = not enough ratings yet). */
  reputationScore: number | null
  /** NEW | TRUSTED | POPULAR. */
  reputationTier: string
}

export interface McpSearchResult {
  totalCount: number
  page: number
  pageSize: number
  items: McpListItem[]
  /** Total MCPs per process stage (stage id → count) under the current criteria. */
  stageCounts: Record<string, number>
}

export interface McpFileItem {
  id: number
  fileName: string
  fileSize: number
  createdAt: string
}

export interface McpPendingVersionItem {
  name: string
  description: string | null
  hashtags: string[]
  categories: string[]
  stages: string[]
  newFiles: McpFileItem[]
  removedFiles: McpFileItem[]
}

export interface McpDetailResponse {
  id: number
  name: string
  description: string | null
  mcpCode: string
  serverKey: string
  status: string
  rejectionReason: string | null
  hashtags: string[]
  files: McpFileItem[]
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
  pendingVersion: McpPendingVersionItem | null
  categories: string[]
  stages: string[]
  /** FR-MCP-019: skills that pin this MCP (reverse reference). */
  dependentSkills: McpDependentSkillItem[]
}

/** FR-MCP-019: a skill that references an MCP (reverse reference). */
export interface McpDependentSkillItem {
  skillId: number
  skillName: string
  skillVersion: number
  creatorName: string | null
}

export interface McpVersionFileItem {
  id: number
  fileName: string
  fileSize: number
}

export interface McpVersionItem {
  version: number
  name: string
  description: string | null
  hashtags: string[]
  creatorName: string | null
  createdAt: string
  files: McpVersionFileItem[]
}

export interface McpDeleteCheckResponse {
  canDelete: boolean
  deniedMcps: { id: number; name: string; creatorName: string | null }[]
}

export interface McpSearchQuery {
  mcpName?: string
  description?: string
  tags?: string[]
  creatorId?: number
  createdDateFrom?: string
  createdDateTo?: string
  statuses?: string[]
  categoryIds?: number[]
  stageId?: number
  /** Subscription filter: "Y" = subscribed, "N" = not subscribed, undefined = all. */
  subscribed?: 'Y' | 'N'
  page?: number
  pageSize?: number
  sortBy?: string
  sortDirection?: string
}

export interface McpTaxonomy {
  categories: TaxonomyOption[]
  stages: TaxonomyOption[]
}

export interface TaxonomyOption {
  id: number
  name: string
  /** Short description (process stages only). */
  description?: string
}

export interface McpTaxonomyItem {
  id: number
  name: string
  sortOrder: number
  description: string | null
  mcpCount: number
}

export interface McpSuggestionResponse {
  suggestions: Array<{
    mcpId: number
    mcpCode: string
    mcpName: string
    serverKey: string
    currentVersion: number
    score: number
  }>
}
