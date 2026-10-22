import { api, request } from '@/lib/api'
import type { OrgSearchRequest, OrgSearchResult, OrgImportResponse } from './types'

export const organizationApi = {
  search: (query: OrgSearchRequest) =>
    request<OrgSearchResult>(api.post('/admin/organizations/search', query)),
  importFile: (file: File) => {
    const form = new FormData()
    form.append('file', file)
    return request<OrgImportResponse>(
      api.post('/admin/organizations/import', form, { timeout: 10 * 60 * 1000 })
    )
  },
  downloadTemplate: async (): Promise<void> => {
    const response = await api.get('/admin/organizations/template', {
      responseType: 'blob',
      transformResponse: (data) => data,
    })
    const url = URL.createObjectURL(response.data as Blob)
    const anchor = document.createElement('a')
    anchor.href = url
    anchor.download = 'organization-template.xlsx'
    anchor.click()
    URL.revokeObjectURL(url)
  },
  partOptions: () => request<string[]>(api.get('/admin/organizations/part-options')),
  groupOptions: () => request<string[]>(api.get('/admin/organizations/group-options')),
}
