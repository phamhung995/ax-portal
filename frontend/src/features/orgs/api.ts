import { api, request } from '@/lib/api'
import type {
  OrgSearchRequest, OrgSearchResult, OrgImportResponse, OrgImportStatus,
  SystemSearchRequest, SystemSearchResult, SystemImportStatus,
  HrSearchRequest, HrSearchResult, HrImportStatus,
} from './types'

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
  /** Uploads a file for the on-demand import (no import yet). */
  uploadFile: (file: File) => {
    const form = new FormData()
    form.append('file', file)
    return request<OrgImportStatus>(api.post('/admin/organizations/upload', form))
  },
  /** Runs the newest pending upload right now (admin "Import Now"). */
  importNow: () =>
    request<OrgImportStatus>(api.post('/admin/organizations/import-now', undefined, {
      timeout: 10 * 60 * 1000,
    })),
  /** Import job state (last run + pending file + running flag). */
  status: () => request<OrgImportStatus>(api.get('/admin/organizations/status')),
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
  centerOptions: () => request<string[]>(api.get('/admin/organizations/center-options')),
  divisionOptions: () => request<string[]>(api.get('/admin/organizations/division-options')),
}

/** System / Sub System master data (Systems tab). */
export const systemApi = {
  search: (query: SystemSearchRequest) =>
    request<SystemSearchResult>(api.post('/admin/systems/search', query)),
  /** Uploads a file for the nightly import (02:00) — no import yet. */
  uploadFile: (file: File) => {
    const form = new FormData()
    form.append('file', file)
    return request<SystemImportStatus>(api.post('/admin/systems/upload', form))
  },
  /** Runs the newest pending upload right now (admin "Import Now"). */
  importNow: () =>
    request<SystemImportStatus>(api.post('/admin/systems/import-now', undefined, {
      timeout: 10 * 60 * 1000,
    })),
  /** Import job state (last run + pending file + running flag). */
  status: () => request<SystemImportStatus>(api.get('/admin/systems/status')),
  partOptions: () => request<string[]>(api.get('/admin/systems/part-options')),
}

/** Human Resource System List master data (Human Resource tab). */
export const hrSystemApi = {
  search: (query: HrSearchRequest) =>
    request<HrSearchResult>(api.post('/admin/hr-systems/search', query)),
  /** Uploads a file for the scheduled import — no import yet. */
  uploadFile: (file: File) => {
    const form = new FormData()
    form.append('file', file)
    return request<HrImportStatus>(api.post('/admin/hr-systems/upload', form))
  },
  /** Runs the newest pending upload right now (admin "Import Now"). */
  importNow: () =>
    request<HrImportStatus>(api.post('/admin/hr-systems/import-now', undefined, {
      timeout: 10 * 60 * 1000,
    })),
  /** Import job state (last run + pending file + running flag). */
  status: () => request<HrImportStatus>(api.get('/admin/hr-systems/status')),
  partOptions: () => request<string[]>(api.get('/admin/hr-systems/part-options')),
}
