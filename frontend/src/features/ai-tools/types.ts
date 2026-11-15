export interface AiToolListItem {
  id: number
  name: string
  description: string | null
  uploaderId: number
  uploaderName: string | null
  createdAt: string
  categories: string[]
  stages: string[]
  tags: string[]
  downloadCount: number
  fileName: string | null
  fileId: number | null
  /** True when the current user may edit/delete this tool (creator or admin). */
  canEdit: boolean
}

export interface AiToolSearchResult {
  totalCount: number
  page: number
  pageSize: number
  items: AiToolListItem[]
  /** Total tools per process stage (stage id → count) under the current criteria. */
  stageCounts: Record<string, number>
}

export interface AiToolFileItem {
  id: number
  fileName: string
  fileSize: number
  createdAt: string
}

export interface AiToolDetailResponse {
  id: number
  name: string
  description: string | null
  uploaderId: number
  uploaderName: string | null
  createdAt: string
  updatedAt: string
  categories: string[]
  stages: string[]
  tags: string[]
  downloadCount: number
  file: AiToolFileItem | null
  /** True when the current user may edit/delete this tool (creator or admin). */
  canEdit: boolean
}

export interface AiToolSearchQuery {
  name?: string
  tags?: string[]
  uploaderId?: number
  createdDateFrom?: string
  createdDateTo?: string
  categoryIds?: number[]
  stageId?: number
  page?: number
  pageSize?: number
  sortBy?: string
  sortDirection?: string
}
