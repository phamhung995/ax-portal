export interface OrgListItem {
  id: string
  knoxId: string
  userName: string
  employed: string
  regularPartner: string
  company: string | null
  division: string | null
  center: string | null
  group: string | null
  part: string | null
  employeeNumber: string | null
  phone: string | null
  email: string | null
  position: string | null
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
  division?: string
  center?: string
  group?: string
  part?: string
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

export interface OrgImportJobInfo {
  id: number
  fileName: string
  uploadedBy: string
  uploadedAt: string
  status: 'PENDING' | 'RUNNING' | 'SUCCESS' | 'FAILED' | 'SUPERSEDED'
  rowsCount: number | null
  successCount: number | null
  errorCount: number | null
  matchedCount: number | null
  deactivatedCount: number | null
  errorRows: OrgImportRowResult[] | null
  errorMessage: string | null
  startedAt: string | null
  finishedAt: string | null
}

export interface OrgImportStatus {
  running: boolean
  lastImport: OrgImportJobInfo | null
  pendingFile: OrgImportJobInfo | null
}

// ── Systems tab (System / Sub System master data) ────────────────────────────

export interface SystemListItem {
  subSystemCode: string | null
  subSystemName: string | null
  systemKey: string
  systemName: string
  currentPhase: string | null
  clientOfSds: string | null
  gdcGroup: string | null
  gdcPart: string | null
  gdcTpmMain: string | null
  projectKey: string | null
  projectName: string | null
  subProjectKey: string | null
}

export interface SystemSearchResult {
  totalCount: number
  page: number
  pageSize: number
  items: SystemListItem[]
}

export interface SystemSearchRequest {
  systemKey?: string
  systemName?: string
  part?: string
  phase?: string
  page?: number
  pageSize?: number
}

export interface SystemImportRowResult {
  rowNumber: number
  sheet: string
  systemKey: string
  errors: string[]
}

export interface SystemImportResponse {
  systemsCount: number
  subSystemsCount: number
  errorCount: number
  errorRows: SystemImportRowResult[]
}

export interface SystemImportJobInfo {
  id: number
  fileName: string
  uploadedBy: string
  uploadedAt: string
  status: 'PENDING' | 'RUNNING' | 'SUCCESS' | 'FAILED' | 'SUPERSEDED'
  systemsCount: number | null
  subSystemsCount: number | null
  errorCount: number | null
  errorRows: SystemImportRowResult[] | null
  errorMessage: string | null
  startedAt: string | null
  finishedAt: string | null
}

export interface SystemImportStatus {
  running: boolean
  lastImport: SystemImportJobInfo | null
  pendingFile: SystemImportJobInfo | null
}

// ── Human Resource System List (Human Resource tab) ─────────────────────────

export interface HrListItem {
  knoxId: string
  userName: string
  employed: string | null
  regularPartner: string | null
  company: string | null
  part: string | null
  employeeNumber: string | null
  systemKey: string
  systemCode: string | null
  role: string | null
  jan: number | null
  feb: number | null
  mar: number | null
  apr: number | null
  may: number | null
  jun: number | null
  jul: number | null
  aug: number | null
  sep: number | null
  oct: number | null
  nov: number | null
  dec: number | null
  projectKey: string | null
  projectName: string | null
}

export interface HrSearchRequest {
  knoxId?: string
  userName?: string
  part?: string
  systemKey?: string
  role?: string
  page: number
  pageSize: number
}

export interface HrSearchResult {
  totalCount: number
  page: number
  pageSize: number
  items: HrListItem[]
}

export interface HrImportJobInfo {
  id: number
  fileName: string
  uploadedBy: string
  uploadedAt: string
  status: 'PENDING' | 'RUNNING' | 'SUCCESS' | 'FAILED' | 'SUPERSEDED'
  rowsCount: number | null
  errorCount: number | null
  errorRows: SystemImportRowResult[] | null
  errorMessage: string | null
  startedAt: string | null
  finishedAt: string | null
}

export interface HrImportStatus {
  running: boolean
  lastImport: HrImportJobInfo | null
  pendingFile: HrImportJobInfo | null
}
