<script setup>
defineProps({ items: { type: Array, default: () => [] }, label: { type: String, default: 'Danh sách' }, selectable: Boolean, selected: { type: Number, default: -1 } })
defineEmits(['select'])
</script>
<template>
  <ul class="p-list" :aria-label="label" :role="selectable ? 'listbox' : undefined">
    <li v-for="(item, i) in items" :key="i" class="p-list-item" :class="{ 'is-selectable': selectable, 'is-selected': i === selected }" :role="selectable ? 'option' : undefined" :aria-selected="selectable ? i === selected : undefined" @click="selectable && $emit('select', i, item)">
      <slot :name="`icon-${i}`"><slot name="icon" /></slot>
      <span class="p-list-item-main">
        <span class="p-list-item-title">{{ item.title ?? item }}</span>
        <span v-if="item.subtitle" class="p-list-item-sub">{{ item.subtitle }}</span>
      </span>
      <span v-if="item.trailing" class="p-list-item-trailing">{{ item.trailing }}</span>
      <slot :name="`trailing-${i}`" :item="item" />
    </li>
  </ul>
</template>
