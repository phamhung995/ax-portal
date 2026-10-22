export interface OrgListItem {
  id: string
  knoxId: string
  userName: string
  employed: string
  regularPartner: string
  company: string | null
  part: string | null
  group: string | null
  employeeNumber: string | null
  importedAt: string
  matchedUserEmail: string | null
}

export interface OrgSearchResult {
  totalCount: number
  page: number
  pageSize: number
  items: OrgListItem[]
}

export interface OrgSearchRequest {
  knoxId?: string
  userName?: string
  company?: string
  part?: string
  group?: string
  employed?: string
  page?: number
  pageSize?: number
}

export interface OrgImportRowResult {
  rowNumber: number
  knoxId: string
  userName: string
  status: 'SUCCESS' | 'ERROR'
  errors: string[]
}

export interface OrgImportResponse {
  totalRows: number
  successCount: number
  errorCount: number
  matchedCount: number
  unmatchedCount: number
  deactivatedCount: number
  rows: OrgImportRowResult[]
}
