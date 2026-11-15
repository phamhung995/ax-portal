import { api, request } from '@/lib/api'
import type {
  SkillSearchResult,
  SkillDetailResponse,
  SkillDeleteCheckResponse,
  SearchUserResult,
  SkillTaxonomy,
  SkillTaxonomyItem,
  SkillVersionItem,
  SkillCommentDTO,
  SystemOption,
  SubSystemOption,
  SkillFilterOptions,
  SkillShareItem,
  SkillShareResult,
} from './types'

export interface SkillSearchQuery {
  skillName?: string
  description?: string
  tags?: string[]
  creatorId?: number
  createdDateFrom?: string
  createdDateTo?: string
  statuses?: string[]
  scopes?: string[]
  categoryIds?: number[]
  stageId?: number
  /** Subscription filter: "Y" = subscribed, "N" = not subscribed, undefined = all. */
  subscribed?: 'Y' | 'N'
  systemKey?: string
  subSystemCode?: string
  page?: number
  pageSize?: number
  sortBy?: string
  sortDirection?: string
}

function toCsv(values?: string[]): string | undefined {
  return values && values.length ? values.join(',') : undefined
}

export const skillApi = {
  search: (query: SkillSearchQuery) =>
    request<SkillSearchResult>(
      api.get('/skills', {
        params: {
          skillName: query.skillName || undefined,
          description: query.description || undefined,
          tags: toCsv(query.tags),
          creatorId: query.creatorId,
          createdDateFrom: query.createdDateFrom || undefined,
          createdDateTo: query.createdDateTo || undefined,
          statuses: toCsv(query.statuses),
          scopes: toCsv(query.scopes),
          categoryIds: query.categoryIds && query.categoryIds.length ? query.categoryIds.join(',') : undefined,
          stageId: query.stageId,
          subscribed: query.subscribed || undefined,
          systemKey: query.systemKey || undefined,
          subSystemCode: query.subSystemCode || undefined,
          page: query.page ?? 1,
          pageSize: query.pageSize ?? 20,
          sortBy: query.sortBy ?? 'createdDate',
          sortDirection: query.sortDirection ?? 'DESC',
        },
      })
    ),
  taxonomy: () => request<SkillTaxonomy>(api.get('/skills/taxonomy')),
  /** The caller's PIC systems (PROJECT scope System combobox). */
  mySystems: () => request<SystemOption[]>(api.get('/skills/my-systems')),
  /** Sub systems of one system (PROJECT scope Sub System combobox). */
  subSystems: (systemKey: string) =>
    request<SubSystemOption[]>(api.get('/skills/sub-systems', { params: { systemKey } })),
  /** Systems of the skill's creator (PROJECT scope System combobox in edit/update). */
  formSystems: (skillId: number) =>
    request<SystemOption[]>(api.get(`/skills/${skillId}/form-systems`)),
  /** All systems + sub systems (search filter comboboxes). */
  filterOptions: () => request<SkillFilterOptions>(api.get('/skills/filter-options')),
  // ── Share (PROJECT scope) ──────────────────────────────────────────────
  shares: (id: number) => request<SkillShareItem[]>(api.get(`/skills/${id}/shares`)),
  shareProjectOptions: (id: number) => request<string[]>(api.get(`/skills/${id}/shares/project-options`)),
  shareUser: (id: number, userIds: number[]) =>
    request<SkillShareResult>(api.post(`/skills/${id}/shares/user`, userIds)),
  shareProject: (id: number, projectKeys: string[]) =>
    request<SkillShareResult>(api.post(`/skills/${id}/shares/project`, projectKeys)),
  removeShare: (id: number, shareId: number) =>
    request<void>(api.delete(`/skills/${id}/shares/${shareId}`)),
  removeShares: (id: number, shareIds: number[]) =>
    request<number>(api.delete(`/skills/${id}/shares`, { data: shareIds })),
  detail: (id: number) => request<SkillDetailResponse>(api.get(`/skills/${id}`)),
  create: (payload: {
    name: string
    description?: string
    publicScope: string
    systemKey?: string
    subSystemCode?: string
    hashtags: string[]
    categoryIds: number[]
    stageIds?: number[]
    coworkUserIds?: number[]
    files?: File[]
    mcpDependencies?: Array<{ mcpId: number; mcpVersion: number }>
  }) => {
    const form = new FormData()
    form.append('name', payload.name)
    if (payload.description) form.append('description', payload.description)
    form.append('publicScope', payload.publicScope)
    if (payload.systemKey) form.append('systemKey', payload.systemKey)
    if (payload.subSystemCode) form.append('subSystemCode', payload.subSystemCode)
    form.append('hashtags', JSON.stringify(payload.hashtags))
    for (const id of payload.categoryIds) form.append('categoryIds', String(id))
    for (const id of payload.stageIds ?? []) form.append('stageIds', String(id))
    for (const id of payload.coworkUserIds ?? []) form.append('coworkUserIds', String(id))
    for (const file of payload.files ?? []) form.append('file', file)
    if (payload.mcpDependencies?.length) form.append('mcpDependencies', JSON.stringify(payload.mcpDependencies))
    return request<number>(api.post('/skills', form))
  },
  checkDelete: (ids: number[]) =>
    request<SkillDeleteCheckResponse>(api.get('/skills/check-delete', { params: { ids: ids.join(',') } })),
  deleteBatch: (ids: number[]) =>
    request<void>(api.delete('/skills', { data: { ids } })),
  recordView: (id: number) => request<{ viewCount: number }>(api.post(`/skills/${id}/view`)),
  exportExcel: async (query: SkillSearchQuery, selectedIds?: number[]): Promise<void> => {
    const response = await api.get('/skills/export', {
      params: {
        skillName: query.skillName || undefined,
        description: query.description || undefined,
        tags: toCsv(query.tags),
        creatorId: query.creatorId,
        createdDateFrom: query.createdDateFrom || undefined,
        createdDateTo: query.createdDateTo || undefined,
        statuses: toCsv(query.statuses),
        scopes: toCsv(query.scopes),
        categoryIds: query.categoryIds && query.categoryIds.length ? query.categoryIds.join(',') : undefined,
        stageId: query.stageId,
        systemKey: query.systemKey || undefined,
        subSystemCode: query.subSystemCode || undefined,
        ids: selectedIds && selectedIds.length ? selectedIds.join(',') : undefined,
      },
      responseType: 'blob',
      transformResponse: (data) => data,
    })
    const disposition = response.headers['content-disposition'] as string | undefined
    const match = disposition?.match(/filename="?([^";]+)"?/)
    const filename = match?.[1] ?? 'Skill_Management.xlsx'
    const url = URL.createObjectURL(response.data as Blob)
    const anchor = document.createElement('a')
    anchor.href = url
    anchor.download = filename
    anchor.click()
    URL.revokeObjectURL(url)
  },
  searchUser: (keyword: string, page = 1, pageSize = 20) =>
    request<SearchUserResult>(
      api.get('/users/search-for-skill', { params: { keyword, page, pageSize } })
    ),
  // ── Detail popup ──────────────────────────────────────────────────────────
  rating: (id: number) =>
    request<{ averageRating: number; totalRatings: number }>(api.get(`/skills/${id}/rating`)),
  myRating: (id: number) => request<number | null>(api.get(`/skills/${id}/rating/me`)),
  upsertRating: (id: number, rating: number) =>
    request<{ averageRating: number; totalRatings: number }>(
      api.put(`/skills/${id}/rating`, { rating })
    ),
  comments: (id: number, page = 1, size = 10) =>
    request<{
      totalCount: number
      page: number
      size: number
      items: SkillCommentDTO[]
    }>(api.get(`/skills/${id}/comments`, { params: { page, size } })),
  addComment: (id: number, content: string, parentId?: number | null) =>
    request<void>(api.post(`/skills/${id}/comments`, { content, parentId: parentId ?? null })),
  deleteComment: (id: number, commentId: number) =>
    request<void>(api.delete(`/skills/${id}/comments/${commentId}`)),
  toggleCommentLike: (id: number, commentId: number) =>
    request<{ liked: boolean; likeCount: number }>(
      api.post(`/skills/${id}/comments/${commentId}/like`)
    ),
  editComment: (id: number, commentId: number, content: string) =>
    request<void>(api.put(`/skills/${id}/comments/${commentId}`, { content })),
  changeStatus: (id: number, approvalStatus: string, rejectionReason?: string) =>
    request<void>(api.put(`/skills/${id}/status`, { approvalStatus, rejectionReason })),
  update: (
    id: number,
    payload: {
      name: string
      description?: string
      publicScope: string
      systemKey?: string
      subSystemCode?: string
      hashtags: string[]
      categoryIds: number[]
      stageIds?: number[]
      coworkUserIds?: number[]
      files?: File[]
      removeFileIds?: number[]
      /** Full MCP dependency list — always sent (even []) to replace/clear. */
      mcpDependencies?: Array<{ mcpId: number; mcpVersion: number }>
      updatedAt: string
    }
  ) => {
    const form = new FormData()
    form.append('name', payload.name)
    if (payload.description) form.append('description', payload.description)
    form.append('publicScope', payload.publicScope)
    if (payload.systemKey) form.append('systemKey', payload.systemKey)
    if (payload.subSystemCode) form.append('subSystemCode', payload.subSystemCode)
    form.append('hashtags', JSON.stringify(payload.hashtags))
    for (const cid of payload.categoryIds) form.append('categoryIds', String(cid))
    for (const sid of payload.stageIds ?? []) form.append('stageIds', String(sid))
    for (const cid of payload.coworkUserIds ?? []) form.append('coworkUserIds', String(cid))
    for (const file of payload.files ?? []) form.append('file', file)
    for (const fid of payload.removeFileIds ?? []) form.append('removeFileIds', String(fid))
    if (payload.mcpDependencies) form.append('mcpDependencies', JSON.stringify(payload.mcpDependencies))
    form.append('updatedAt', payload.updatedAt)
    return request<{ version: number }>(api.put(`/skills/${id}`, form))
  },
  deleteFile: (id: number, fileId: number) => request<void>(api.delete(`/skills/${id}/files/${fileId}`)),
  // ── Version update (APPROVED skills) ───────────────────────────────────────
  updateVersion: (
    id: number,
    payload: {
      name: string
      description?: string
      publicScope: string
      systemKey?: string
      subSystemCode?: string
      hashtags: string[]
      categoryIds: number[]
      stageIds?: number[]
      files?: File[]
      removeFileIds?: number[]
      /** Full MCP dependency list — always sent (even []) to replace/clear. */
      mcpDependencies?: Array<{ mcpId: number; mcpVersion: number }>
    }
  ) => {
    const form = new FormData()
    form.append('name', payload.name)
    if (payload.description) form.append('description', payload.description)
    form.append('publicScope', payload.publicScope)
    if (payload.systemKey) form.append('systemKey', payload.systemKey)
    if (payload.subSystemCode) form.append('subSystemCode', payload.subSystemCode)
    form.append('hashtags', JSON.stringify(payload.hashtags))
    for (const cid of payload.categoryIds) form.append('categoryIds', String(cid))
    for (const sid of payload.stageIds ?? []) form.append('stageIds', String(sid))
    for (const file of payload.files ?? []) form.append('file', file)
    for (const fid of payload.removeFileIds ?? []) form.append('removeFileIds', String(fid))
    if (payload.mcpDependencies) form.append('mcpDependencies', JSON.stringify(payload.mcpDependencies))
    return request<void>(api.post(`/skills/${id}/versions`, form))
  },
  cancelVersionUpdate: (id: number) => request<void>(api.post(`/skills/${id}/versions/cancel`)),
  listVersions: (id: number) =>
    request<SkillVersionItem[]>(api.get(`/skills/${id}/versions`)),
  downloadVersionFile: async (id: number, version: number, fileId: number): Promise<void> => {
    const response = await api.get(`/skills/${id}/versions/${version}/files/${fileId}/download`, {
      responseType: 'blob',
      transformResponse: (data) => data,
    })
    const disposition = response.headers['content-disposition'] as string | undefined
    const match = disposition?.match(/filename="?([^";]+)"?/)
    const filename = match?.[1] ?? 'skill-version-file'
    const url = URL.createObjectURL(response.data as Blob)
    const anchor = document.createElement('a')
    anchor.href = url
    anchor.download = filename
    anchor.click()
    URL.revokeObjectURL(url)
  },
  downloadVersionAll: async (id: number, version: number): Promise<void> => {
    const response = await api.get(`/skills/${id}/versions/${version}/files/download-all`, {
      responseType: 'blob',
      transformResponse: (data) => data,
    })
    const disposition = response.headers['content-disposition'] as string | undefined
    const match = disposition?.match(/filename="?([^";]+)"?/)
    const filename = match?.[1] ?? 'skill-version-files.zip'
    const url = URL.createObjectURL(response.data as Blob)
    const anchor = document.createElement('a')
    anchor.href = url
    anchor.download = filename
    anchor.click()
    URL.revokeObjectURL(url)
  },
  downloadPendingFile: async (id: number, fileId: number): Promise<void> => {
    const response = await api.get(`/skills/${id}/pending-versions/files/${fileId}/download`, {
      responseType: 'blob',
      transformResponse: (data) => data,
    })
    const disposition = response.headers['content-disposition'] as string | undefined
    const match = disposition?.match(/filename="?([^";]+)"?/)
    const filename = match?.[1] ?? 'skill-pending-file'
    const url = URL.createObjectURL(response.data as Blob)
    const anchor = document.createElement('a')
    anchor.href = url
    anchor.download = filename
    anchor.click()
    URL.revokeObjectURL(url)
  },
  downloadPendingAll: async (id: number): Promise<void> => {
    const response = await api.get(`/skills/${id}/pending-versions/files/download-all`, {
      responseType: 'blob',
      transformResponse: (data) => data,
    })
    const disposition = response.headers['content-disposition'] as string | undefined
    const match = disposition?.match(/filename="?([^";]+)"?/)
    const filename = match?.[1] ?? 'skill-pending-files.zip'
    const url = URL.createObjectURL(response.data as Blob)
    const anchor = document.createElement('a')
    anchor.href = url
    anchor.download = filename
    anchor.click()
    URL.revokeObjectURL(url)
  },
  subscribe: (id: number) => request<void>(api.post(`/skills/${id}/subscription`)),
  unsubscribe: (id: number) => request<void>(api.delete(`/skills/${id}/subscription`)),
  download: async (id: number, fileId: number): Promise<void> => {
    const response = await api.get(`/skills/${id}/files/${fileId}/download`, {
      responseType: 'blob',
      transformResponse: (data) => data,
    })
    const disposition = response.headers['content-disposition'] as string | undefined
    const match = disposition?.match(/filename="?([^";]+)"?/)
    const filename = match?.[1] ?? 'skill-file'
    const url = URL.createObjectURL(response.data as Blob)
    const anchor = document.createElement('a')
    anchor.href = url
    anchor.download = filename
    anchor.click()
    URL.revokeObjectURL(url)
  },
  downloadAll: async (id: number): Promise<void> => {
    const response = await api.get(`/skills/${id}/files/download-all`, {
      responseType: 'blob',
      transformResponse: (data) => data,
    })
    const disposition = response.headers['content-disposition'] as string | undefined
    const match = disposition?.match(/filename="?([^";]+)"?/)
    const filename = match?.[1] ?? 'skill-files.zip'
    const url = URL.createObjectURL(response.data as Blob)
    const anchor = document.createElement('a')
    anchor.href = url
    anchor.download = filename
    anchor.click()
    URL.revokeObjectURL(url)
  },
  /** Download All (with MCPs): skill files + every available MCP dependency
   *  (each MCP in its own subfolder) as ONE zip. */
  downloadAllWithMcps: async (id: number): Promise<void> => {
    const response = await api.get(`/skills/${id}/files/download-all-with-mcps`, {
      responseType: 'blob',
      transformResponse: (data) => data,
    })
    const disposition = response.headers['content-disposition'] as string | undefined
    const match = disposition?.match(/filename="?([^";]+)"?/)
    const filename = match?.[1] ?? 'skill-files.zip'
    const url = URL.createObjectURL(response.data as Blob)
    const anchor = document.createElement('a')
    anchor.href = url
    anchor.download = filename
    anchor.click()
    URL.revokeObjectURL(url)
  },
  /** Download one pinned MCP dependency's version (zip) via the skill
   *  context — works for unapproved MCPs on PRIVATE/PROJECT skills. */
  downloadMcpDepVersionAll: async (skillId: number, mcpId: number, version: number): Promise<void> => {
    const response = await api.get(
      `/skills/${skillId}/mcp-deps/${mcpId}/versions/${version}/files/download-all`,
      { responseType: 'blob', transformResponse: (data) => data }
    )
    const disposition = response.headers['content-disposition'] as string | undefined
    const match = disposition?.match(/filename="?([^";]+)"?/)
    const filename = match?.[1] ?? 'mcp-version-files.zip'
    const url = URL.createObjectURL(response.data as Blob)
    const anchor = document.createElement('a')
    anchor.href = url
    anchor.download = filename
    anchor.click()
    URL.revokeObjectURL(url)
  },
}

/** Admin management of skill categories & process stages. */
export const skillTaxonomyApi = {
  listCategories: () => request<SkillTaxonomyItem[]>(api.get('/admin/skill-taxonomy/categories')),
  createCategory: (name: string) =>
    request<number>(api.post('/admin/skill-taxonomy/categories', { name })),
  updateCategory: (id: number, name: string) =>
    request<void>(api.put(`/admin/skill-taxonomy/categories/${id}`, { name })),
  deleteCategory: (id: number) => request<void>(api.delete(`/admin/skill-taxonomy/categories/${id}`)),
  listStages: () => request<SkillTaxonomyItem[]>(api.get('/admin/skill-taxonomy/stages')),
  createStage: (name: string, description?: string) =>
    request<number>(api.post('/admin/skill-taxonomy/stages', { name, description })),
  updateStage: (id: number, name: string, description?: string) =>
    request<void>(api.put(`/admin/skill-taxonomy/stages/${id}`, { name, description })),
  deleteStage: (id: number) => request<void>(api.delete(`/admin/skill-taxonomy/stages/${id}`)),
}
