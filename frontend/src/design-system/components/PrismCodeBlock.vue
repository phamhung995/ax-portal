<script setup>
import { ref } from 'vue'
import { Copy, Check } from 'lucide-vue-next'
import { useI18nStore } from '@/stores/i18n'
defineProps({ code: { type: String, required: true }, filename: { type: String, default: 'example.vue' } })
const { t } = useI18nStore()
const status = ref('')
async function copy(code) { try { await navigator.clipboard.writeText(code); status.value = t('app.codeBlock.copied') } catch { status.value = t('app.codeBlock.copyFailed') } }
</script>
<template><div class="p-code"><div class="p-code-header"><span>{{ filename }}</span><button type="button" class="p-icon-button" :aria-label="t('app.codeBlock.copyAria')" @click="copy(code)"><Check v-if="status === t('app.codeBlock.copied')" :size="16" /><Copy v-else :size="16" /></button></div><pre tabindex="0"><code>{{ code }}</code></pre><span role="status" class="p-code-status">{{ status }}</span></div></template>
