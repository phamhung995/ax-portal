<script setup>
import { useId } from 'vue'
import { ChevronDown } from 'lucide-vue-next'
const props = defineProps({ items: { type: Array, default: () => [] }, modelValue: { type: Array, default: () => [] }, multiple: Boolean, collapsible: { type: Boolean, default: true } })
const emit = defineEmits(['update:modelValue'])
const id = useId()
function toggle(value) { const open = props.modelValue.includes(value); if (open && !props.collapsible && props.modelValue.length === 1) return; emit('update:modelValue', open ? props.modelValue.filter(v => v !== value) : props.multiple ? [...props.modelValue, value] : [value]) }
</script>
<template><div class="p-accordion"><section v-for="item in items" :key="item.value"><h3><button :id="`${id}-button-${item.value}`" type="button" :aria-expanded="modelValue.includes(item.value)" :aria-controls="`${id}-panel-${item.value}`" @click="toggle(item.value)">{{ item.label }}<ChevronDown :size="17" /></button></h3><div v-show="modelValue.includes(item.value)" :id="`${id}-panel-${item.value}`" role="region" :aria-labelledby="`${id}-button-${item.value}`"><slot :name="item.value">{{ item.content }}</slot></div></section></div></template>
