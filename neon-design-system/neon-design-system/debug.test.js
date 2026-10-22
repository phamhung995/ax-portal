import { it } from 'vitest'
import { mount } from '@vue/test-utils'
import PrismDatePicker from './src/components/PrismDatePicker.vue'

it('debug', async () => {
  const wrapper = mount(PrismDatePicker, { attachTo: document.body, props: { modelValue: '2026-09-24', label: 'Ngày' } })
  await wrapper.get('input').trigger('click')
  const days = wrapper.findAll('.p-calendar-day')
  console.log('days found:', days.length)
  const target = days.find(d => d.attributes('aria-label') === '2026-09-15')
  console.log('target found:', !!target, 'disabled:', target?.attributes('disabled'))
  console.log('popup exists before:', wrapper.find('.p-date-popup').exists())
  await target.trigger('click')
  console.log('emitted:', JSON.stringify(wrapper.emitted()))
  console.log('popup exists after:', wrapper.find('.p-date-popup').exists())
})
