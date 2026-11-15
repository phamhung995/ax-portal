import type { AxiosResponse } from 'axios'
import { api, request } from '@/lib/api'
import type {
  McpSearchResult,
  McpDetailResponse,
  McpDeleteCheckResponse,
  McpTaxonomy,
  McpTaxonomyItem,
  McpVersionItem,
  McpDependentSkillItem,
  McpSuggestionResponse,
} from './types'

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

function toCsv(values?: string[]): string | undefined {
  return values && values.length ? values.join(',') : undefined
}

/** Downloads a blob response and triggers a browser save. */
async function downloadBlob(
  response: AxiosResponse,
  fallbackName: string
): Promise<void> {
  const disposition = response.headers['content-disposition'] as string | undefined
  const match = disposition?.match(/filename="?([^";]+)"?/)
  const filename = match?.[1] ?? fallbackName
  const url = URL.createObjectURL(response.data as Blob)
  const anchor = document.createElement('a')
  anchor.href = url
  anchor.download = filename
  anchor.click()
  URL.revokeObjectURL(url)
}

async function blobGet(path: string, params?: Record<string, unknown>) {
  return api.get(path, {
    params,
    responseType: 'blob',
    transformResponse: (data) => data,
  })
}

export const mcpApi = {
  search: (query: McpSearchQuery) =>
    request<McpSearchResult>(
      api.get('/mcps', {
        params: {
          mcpName: query.mcpName || undefined,
          description: query.description || undefined,
          tags: toCsv(query.tags),
          creatorId: query.creatorId,
          createdDateFrom: query.createdDateFrom || undefined,
          createdDateTo: query.createdDateTo || undefined,
          statuses: toCsv(query.statuses),
          categoryIds: query.categoryIds && query.categoryIds.length ? query.categoryIds.join(',') : undefined,
          stageId: query.stageId,
          subscribed: query.subscribed || undefined,
          page: query.page ?? 1,
          pageSize: query.pageSize ?? 20,
          sortBy: query.sortBy ?? 'createdDate',
          sortDirection: query.sortDirection ?? 'DESC',
        },
      })
    ),
  taxonomy: () => request<McpTaxonomy>(api.get('/mcps/taxonomy')),
  /** Fuzzy MCP suggestions for the Add/Edit Skill MCP picker. */
  suggest: (skillName: string, skillDesc?: string, skillHashtags?: string[]) =>
    request<McpSuggestionResponse>(api.get('/mcps/suggest', {
      params: {
        skillName,
        skillDesc: skillDesc || undefined,
        skillHashtags: skillHashtags?.join(',') || undefined,
      },
    })),
  detail: (id: number) => request<McpDetailResponse>(api.get(`/mcps/${id}`)),
  create: (payload: {
    name: string
    description?: string
    hashtags: string[]
    categoryIds: number[]
    stageIds?: number[]
    files?: File[]
  }) => {
    const form = new FormData()
    form.append('name', payload.name)
    if (payload.description) form.append('description', payload.description)
    form.append('hashtags', JSON.stringify(payload.hashtags))
    for (const id of payload.categoryIds) form.append('categoryIds', String(id))
    for (const id of payload.stageIds ?? []) form.append('stageIds', String(id))
    for (const file of payload.files ?? []) form.append('file', file)
    return request<number>(api.post('/mcps', form))
  },
  checkDelete: (ids: number[]) =>
    request<McpDeleteCheckResponse>(api.get('/mcps/check-delete', { params: { ids: ids.join(',') } })),
  deleteBatch: (ids: number[]) =>
    request<void>(api.delete('/mcps', { data: { ids } })),
  recordView: (id: number) => request<{ viewCount: number }>(api.post(`/mcps/${id}/view`)),
  exportExcel: async (query: McpSearchQuery, selectedIds?: number[]): Promise<void> => {
    const response = await blobGet('/mcps/export', {
      mcpName: query.mcpName || undefined,
      description: query.description || undefined,
      tags: toCsv(query.tags),
      creatorId: query.creatorId,
      createdDateFrom: query.createdDateFrom || undefined,
      createdDateTo: query.createdDateTo || undefined,
      statuses: toCsv(query.statuses),
      categoryIds: query.categoryIds && query.categoryIds.length ? query.categoryIds.join(',') : undefined,
      stageId: query.stageId,
      ids: selectedIds && selectedIds.length ? selectedIds.join(',') : undefined,
    })
    await downloadBlob(response, 'MCP_Management.xlsx')
  },
  // ── Detail popup ──────────────────────────────────────────────────────────
  rating: (id: number) =>
    request<{ averageRating: number; totalRatings: number }>(api.get(`/mcps/${id}/rating`)),
  myRating: (id: number) => request<number | null>(api.get(`/mcps/${id}/rating/me`)),
  upsertRating: (id: number, rating: number) =>
    request<{ averageRating: number; totalRatings: number }>(
      api.put(`/mcps/${id}/rating`, { rating })
    ),
  comments: (id: number, page = 1, size = 10) =>
    request<{
      totalCount: number
      page: number
      size: number
      items: { id: number; userId: number; userName: string | null; content: string; createdAt: string; canDelete: boolean }[]
    }>(api.get(`/mcps/${id}/comments`, { params: { page, size } })),
  addComment: (id: number, content: string) =>
    request<void>(api.post(`/mcps/${id}/comments`, { content })),
  deleteComment: (id: number, commentId: number) =>
    request<void>(api.delete(`/mcps/${id}/comments/${commentId}`)),
  changeStatus: (id: number, approvalStatus: string, rejectionReason?: string) =>
    request<void>(api.put(`/mcps/${id}/status`, { approvalStatus, rejectionReason })),
  /** FR-MCP-019: skills that pin this MCP (reverse reference). */
  dependentSkills: (id: number) =>
    request<McpDependentSkillItem[]>(api.get(`/mcps/${id}/dependent-skills`)),
  update: (
    id: number,
    payload: {
      name: string
      description?: string
      hashtags: string[]
      categoryIds: number[]
      stageIds?: number[]
      files?: File[]
      removeFileIds?: number[]
      updatedAt: string
    }
  ) => {
    const form = new FormData()
    form.append('name', payload.name)
    if (payload.description) form.append('description', payload.description)
    form.append('hashtags', JSON.stringify(payload.hashtags))
    for (const cid of payload.categoryIds) form.append('categoryIds', String(cid))
    for (const sid of payload.stageIds ?? []) form.append('stageIds', String(sid))
    for (const file of payload.files ?? []) form.append('file', file)
    for (const fid of payload.removeFileIds ?? []) form.append('removeFileIds', String(fid))
    form.append('updatedAt', payload.updatedAt)
    return request<{ version: number }>(api.put(`/mcps/${id}`, form))
  },
  deleteFile: (id: number, fileId: number) => request<void>(api.delete(`/mcps/${id}/files/${fileId}`)),
  // ── Version update (APPROVED MCPs) ─────────────────────────────────────────
  updateVersion: (
    id: number,
    payload: {
      name: string
      description?: string
      hashtags: string[]
      categoryIds: number[]
      stageIds?: number[]
      files?: File[]
      removeFileIds?: number[]
    }
  ) => {
    const form = new FormData()
    form.append('name', payload.name)
    if (payload.description) form.append('description', payload.description)
    form.append('hashtags', JSON.stringify(payload.hashtags))
    for (const cid of payload.categoryIds) form.append('categoryIds', String(cid))
    for (const sid of payload.stageIds ?? []) form.append('stageIds', String(sid))
    for (const file of payload.files ?? []) form.append('file', file)
    for (const fid of payload.removeFileIds ?? []) form.append('removeFileIds', String(fid))
    return request<void>(api.post(`/mcps/${id}/versions`, form))
  },
  cancelVersionUpdate: (id: number) => request<void>(api.post(`/mcps/${id}/versions/cancel`)),
  listVersions: (id: number) =>
    request<McpVersionItem[]>(api.get(`/mcps/${id}/versions`)),
  downloadVersionFile: async (id: number, version: number, fileId: number): Promise<void> => {
    const response = await blobGet(`/mcps/${id}/versions/${version}/files/${fileId}/download`)
    await downloadBlob(response, 'mcp-version-file')
  },
  downloadVersionAll: async (id: number, version: number): Promise<void> => {
    const response = await blobGet(`/mcps/${id}/versions/${version}/files/download-all`)
    await downloadBlob(response, 'mcp-version-files.zip')
  },
  downloadPendingFile: async (id: number, fileId: number): Promise<void> => {
    const response = await blobGet(`/mcps/${id}/pending-versions/files/${fileId}/download`)
    await downloadBlob(response, 'mcp-pending-file')
  },
  downloadPendingAll: async (id: number): Promise<void> => {
    const response = await blobGet(`/mcps/${id}/pending-versions/files/download-all`)
    await downloadBlob(response, 'mcp-pending-files.zip')
  },
  subscribe: (id: number) => request<void>(api.post(`/mcps/${id}/subscription`)),
  unsubscribe: (id: number) => request<void>(api.delete(`/mcps/${id}/subscription`)),
  download: async (id: number, fileId: number): Promise<void> => {
    const response = await blobGet(`/mcps/${id}/files/${fileId}/download`)
    await downloadBlob(response, 'mcp-file')
  },
  downloadAll: async (id: number): Promise<void> => {
    const response = await blobGet(`/mcps/${id}/files/download-all`)
    await downloadBlob(response, 'mcp-files.zip')
  },
}

/** Admin management of MCP categories & process stages. */
export const mcpTaxonomyApi = {
  listCategories: () => request<McpTaxonomyItem[]>(api.get('/admin/mcp-taxonomy/categories')),
  createCategory: (name: string) =>
    request<number>(api.post('/admin/mcp-taxonomy/categories', { name })),
  updateCategory: (id: number, name: string) =>
    request<void>(api.put(`/admin/mcp-taxonomy/categories/${id}`, { name })),
  deleteCategory: (id: number) => request<void>(api.delete(`/admin/mcp-taxonomy/categories/${id}`)),
  listStages: () => request<McpTaxonomyItem[]>(api.get('/admin/mcp-taxonomy/stages')),
  createStage: (name: string, description?: string) =>
    request<number>(api.post('/admin/mcp-taxonomy/stages', { name, description })),
  updateStage: (id: number, name: string, description?: string) =>
    request<void>(api.put(`/admin/mcp-taxonomy/stages/${id}`, { name, description })),
  deleteStage: (id: number) => request<void>(api.delete(`/admin/mcp-taxonomy/stages/${id}`)),
}
