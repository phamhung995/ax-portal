<script setup>
import { ref } from 'vue'
import { Copy, Check } from 'lucide-vue-next'
defineProps({ code: { type: String, required: true }, filename: { type: String, default: 'example.vue' } })
const status = ref('')
async function copy(code) { try { await navigator.clipboard.writeText(code); status.value = 'Đã sao chép' } catch { status.value = 'Không thể sao chép. Hãy chọn mã và sao chép thủ công.' } }
</script>
<template><div class="p-code"><div class="p-code-header"><span>{{ filename }}</span><button type="button" class="p-icon-button" aria-label="Sao chép mã" @click="copy(code)"><Check v-if="status === 'Đã sao chép'" :size="16" /><Copy v-else :size="16" /></button></div><pre tabindex="0"><code>{{ code }}</code></pre><span role="status" class="p-code-status">{{ status }}</span></div></template>
