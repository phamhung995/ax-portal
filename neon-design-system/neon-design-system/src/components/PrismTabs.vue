<script setup>
import { useId, ref } from 'vue'
const props = defineProps({ modelValue: String, items: { type: Array, default: () => [] }, label: { type: String, default: 'Nội dung' } })
const emit = defineEmits(['update:modelValue'])
const id = useId()
const list = ref(null)
function move(event) {
  if (!['ArrowLeft', 'ArrowRight', 'Home', 'End'].includes(event.key)) return
  event.preventDefault()
  const enabled = props.items.filter(item => !item.disabled)
  let index = enabled.findIndex(item => item.value === props.modelValue)
  index = event.key === 'Home' ? 0 : event.key === 'End' ? enabled.length - 1 : (index + (event.key === 'ArrowRight' ? 1 : -1) + enabled.length) % enabled.length
  if (!enabled[index]) return
  emit('update:modelValue', enabled[index].value)
  list.value.querySelectorAll('button:not(:disabled)')[index]?.focus()
}
</script>
<template>
  <div class="p-tabs">
    <div ref="list" class="p-tablist" role="tablist" :aria-label="label" @keydown="move"><button v-for="item in items" :id="`${id}-tab-${item.value}`" :key="item.value" type="button" role="tab" :disabled="item.disabled" :aria-selected="modelValue === item.value" :aria-controls="item.panelId || `${id}-panel-${item.value}`" :tabindex="modelValue === item.value ? 0 : -1" @click="$emit('update:modelValue', item.value)">{{ item.label }}<span v-if="item.count" class="p-tab-count">{{ item.count }}</span></button></div>
    <div v-for="item in items" v-show="modelValue === item.value" :id="item.panelId || `${id}-panel-${item.value}`" :key="item.value" role="tabpanel" :aria-labelledby="`${id}-tab-${item.value}`" tabindex="0" class="p-tabpanel"><slot :name="item.value" /></div>
  </div>
</template>
