import { api, request } from '@/lib/api'
import type { SkillTaxonomy } from '@/features/skills/types'
import type {
  AiToolDetailResponse,
  AiToolSearchQuery,
  AiToolSearchResult,
} from './types'

function toCsv(values?: string[]): string | undefined {
  return values && values.length ? values.join(',') : undefined
}

export const aiToolApi = {
  search: (query: AiToolSearchQuery) =>
    request<AiToolSearchResult>(
      api.get('/ai-tools', {
        params: {
          name: query.name || undefined,
          tags: toCsv(query.tags),
          uploaderId: query.uploaderId,
          createdDateFrom: query.createdDateFrom || undefined,
          createdDateTo: query.createdDateTo || undefined,
          categoryIds: query.categoryIds && query.categoryIds.length ? query.categoryIds.join(',') : undefined,
          stageId: query.stageId,
          page: query.page ?? 1,
          pageSize: query.pageSize ?? 20,
          sortBy: query.sortBy ?? 'createdDate',
          sortDirection: query.sortDirection ?? 'DESC',
        },
      })
    ),
  taxonomy: () => request<SkillTaxonomy>(api.get('/ai-tools/taxonomy')),
  detail: (id: number) => request<AiToolDetailResponse>(api.get(`/ai-tools/${id}`)),
  create: (payload: {
    name: string
    description?: string
    tags: string[]
    categoryIds: number[]
    stageIds: number[]
    file: File
  }) => {
    const form = new FormData()
    form.append('name', payload.name)
    if (payload.description) form.append('description', payload.description)
    form.append('tags', JSON.stringify(payload.tags))
    for (const id of payload.categoryIds) form.append('categoryIds', String(id))
    for (const id of payload.stageIds) form.append('stageIds', String(id))
    form.append('file', payload.file)
    return request<number>(api.post('/ai-tools', form))
  },
  update: (
    id: number,
    payload: {
      name: string
      description?: string
      tags: string[]
      categoryIds: number[]
      stageIds: number[]
      file?: File
      removeFile?: boolean
    }
  ) => {
    const form = new FormData()
    form.append('name', payload.name)
    if (payload.description) form.append('description', payload.description)
    form.append('tags', JSON.stringify(payload.tags))
    for (const id of payload.categoryIds) form.append('categoryIds', String(id))
    for (const id of payload.stageIds) form.append('stageIds', String(id))
    if (payload.removeFile) form.append('removeFile', 'true')
    if (payload.file) form.append('file', payload.file)
    return request<void>(api.put(`/ai-tools/${id}`, form))
  },
  deleteBatch: (ids: number[]) =>
    request<{ deleted: number; skippedNames: string[] }>(api.delete('/ai-tools', { data: { ids } })),
  download: async (id: number, fileId: number): Promise<void> => {
    const response = await api.get(`/ai-tools/${id}/files/${fileId}/download`, {
      responseType: 'blob',
      transformResponse: (data) => data,
    })
    const disposition = response.headers['content-disposition'] as string | undefined
    const match = disposition?.match(/filename="?([^";]+)"?/)
    const filename = match?.[1] ?? 'ai-tool-file'
    const url = URL.createObjectURL(response.data as Blob)
    const anchor = document.createElement('a')
    anchor.href = url
    anchor.download = filename
    anchor.click()
    URL.revokeObjectURL(url)
  },
}
