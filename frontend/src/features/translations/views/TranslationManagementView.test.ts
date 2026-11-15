import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest'
import { mount } from '@vue/test-utils'
import { setActivePinia, createPinia } from 'pinia'
import TranslationManagementView from './TranslationManagementView.vue'

vi.mock('@/features/translations/api', () => ({
  translationApi: {
    list: vi.fn(),
    create: vi.fn(),
    update: vi.fn(),
    remove: vi.fn(),
  },
}))

vi.mock('@/stores/i18n', () => ({
  useI18nStore: () => ({
    t: (key: string) => key,
    formatDateTime: (iso: string) => iso,
  }),
}))

vi.mock('@/lib/toast', () => ({
  toastError: vi.fn(),
  toastSuccess: vi.fn(),
}))

import { translationApi } from '@/features/translations/api'
import type { TranslationKeyItem } from '@/features/translations/types'

const mockedList = translationApi.list as unknown as ReturnType<typeof vi.fn>
const mockedUpdate = translationApi.update as unknown as ReturnType<typeof vi.fn>

function makeRow(overrides: Partial<TranslationKeyItem> = {}): TranslationKeyItem {
  return {
    id: 1,
    key: 'home.title',
    description: null,
    values: { en: 'Home', vi: 'Trang chủ', ko: '홈' },
    updatedAt: '2026-10-31T00:00:00Z',
    updatedBy: null,
    ...overrides,
  }
}

describe('TranslationManagementView inline edit', () => {
  afterEach(() => {
    ;(document.activeElement as HTMLElement | null)?.blur?.()
    document.body.innerHTML = ''
  })

  beforeEach(() => {
    vi.clearAllMocks()
    setActivePinia(createPinia())
    mockedList.mockResolvedValue([makeRow()])
    mockedUpdate.mockImplementation(async (id: number, payload: Record<string, unknown>) => ({
      ...makeRow(),
      ...payload,
      values: { ...makeRow().values, ...(payload.values as Record<string, string>) },
    }))
  })

  async function mountView() {
    // attachTo document so el.focus() updates document.activeElement
    const wrapper = mount(TranslationManagementView, { attachTo: document.body })
    await vi.waitFor(() => expect(wrapper.find('.tm-edit-input, .tm-value-cell').exists()).toBe(true))
    return wrapper
  }

  it('focuses the edit input with the cursor at the end of the value on click', async () => {
    const wrapper = await mountView()
    const cell = wrapper.findAll('.tm-value-cell')[0]! // English column of row 1
    await cell.trigger('click')

    const input = wrapper.find('input.tm-edit-input')
    expect(input.exists()).toBe(true)
    // focus happens on the next tick after the input renders
    await vi.waitFor(() => expect(document.activeElement).toBe(input.element))
    const el = input.element as HTMLInputElement
    expect(el.selectionStart).toBe(el.value.length)
    expect(el.selectionEnd).toBe(el.value.length)
  })

  it('Escape cancels the edit without saving', async () => {
    const wrapper = await mountView()
    await wrapper.findAll('.tm-value-cell')[0]!.trigger('click')
    const input = wrapper.find('input.tm-edit-input')
    expect(input.exists()).toBe(true)

    await input.trigger('keydown', { key: 'Escape' })

    expect(wrapper.find('input.tm-edit-input').exists()).toBe(false)
    expect(mockedUpdate).not.toHaveBeenCalled()
    // the original value is restored in the cell
    expect(wrapper.findAll('.tm-value-cell')[0]!.text()).toBe('Home')
  })

  it('Enter saves the edited value', async () => {
    const wrapper = await mountView()
    await wrapper.findAll('.tm-value-cell')[0]!.trigger('click')
    const input = wrapper.find('input.tm-edit-input')
    await input.setValue('Homepage')
    await input.trigger('keydown', { key: 'Enter' })

    await vi.waitFor(() => expect(mockedUpdate).toHaveBeenCalledTimes(1))
    expect(mockedUpdate).toHaveBeenCalledWith(1, { values: { en: 'Homepage' } })
    expect(wrapper.find('input.tm-edit-input').exists()).toBe(false)
  })
})
