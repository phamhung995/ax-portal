export interface TranslationKeyItem {
  id: number
  key: string
  description: string | null
  values: Record<string, string>
  updatedAt: string
  updatedBy: string | null
}

export interface CreateTranslationRequest {
  key: string
  description?: string
  values?: Record<string, string>
}

export interface UpdateTranslationRequest {
  description?: string
  values?: Record<string, string>
}
