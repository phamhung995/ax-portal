import { api, request } from '@/lib/api'
import type {
  TranslationKeyItem,
  CreateTranslationRequest,
  UpdateTranslationRequest,
} from './types'

export const translationApi = {
  list: (search?: string) =>
    request<TranslationKeyItem[]>(api.get('/admin/translations', { params: { search } })),
  create: (payload: CreateTranslationRequest) =>
    request<TranslationKeyItem>(api.post('/admin/translations', payload)),
  update: (id: number, payload: UpdateTranslationRequest) =>
    request<TranslationKeyItem>(api.put(`/admin/translations/${id}`, payload)),
  remove: (id: number) => request<void>(api.delete(`/admin/translations/${id}`)),
}
