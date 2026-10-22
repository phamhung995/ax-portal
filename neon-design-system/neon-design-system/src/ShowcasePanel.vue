<script setup>
import { ref } from 'vue'
import { Code2, Eye } from 'lucide-vue-next'
import { PrismCodeBlock } from './components'
defineProps({ specimen: Object, index: Number })
const code = ref(false)
</script>
<template><article class="specimen p-glass" :id="specimen.id"><header class="specimen-head"><div><span class="specimen-index">{{ String(index + 1).padStart(2, '0') }} /</span><h3>{{ specimen.name }}</h3></div><button class="p-icon-button" type="button" :aria-label="`${code ? 'Xem giao diện' : 'Xem mã'} ${specimen.name}`" :aria-pressed="code" @click="code = !code"><Eye v-if="code" :size="16" /><Code2 v-else :size="16" /></button></header><p class="specimen-description">{{ specimen.description }}</p><div class="specimen-body"><PrismCodeBlock v-if="code" :code="specimen.code" /><slot v-else /></div><footer class="specimen-footer"><span>{{ specimen.category.toUpperCase() }}</span><span><i></i> Vue component</span></footer></article></template>
