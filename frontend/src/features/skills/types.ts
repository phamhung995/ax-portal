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
  categories: string[]
  stages: string[]
  viewCount: number
  avgRating: number | null
  ratingCount: number
  subscribed: boolean
  hasPendingVersion: boolean
}

export interface SkillSearchResult {
  totalCount: number
  page: number
  pageSize: number
  items: SkillListItem[]
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
  rejectionReason: string | null
  hashtags: string[]
  files: SkillFileItem[]
  viewCount: number
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
  pendingVersion: SkillPendingVersionItem | null
  categories: string[]
  stages: string[]
}

export interface SkillVersionFileItem {
  id: number
  fileName: string
  fileSize: number
}

export interface SkillVersionItem {
  version: number
  name: string
  description: string | null
  publicScope: string
  hashtags: string[]
  creatorName: string | null
  createdAt: string
  files: SkillVersionFileItem[]
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
