<script setup lang="ts">
// Introduction page (all environments) — the standalone static
// public/introduction.html embedded below the GNB. The GNB language
// switcher is the single source of truth: the iframe src carries the
// app language as ?lang=, so switching language in the header reloads
// the page in the new language.
import { computed, onMounted } from 'vue'
import { useI18nStore } from '@/stores/i18n'
import { INTRODUCTION_PATH } from '@/config/introduction'

const i18n = useI18nStore()
// Cache-bust on every mount so a fresh build is always picked up (the
// static file is otherwise cached by the browser across app reloads).
const stamp = Date.now()
const src = computed(() => `${INTRODUCTION_PATH}?lang=${i18n.language}&v=${stamp}`)

// The intro page is the landing page ONCE per session (see landingPath in
// router/index.ts) — mark it seen so the next login lands on Home.
onMounted(() => {
  sessionStorage.setItem('ax-intro-seen', '1')
})
</script>

<template>
  <iframe :src="src" class="intro-frame" title="Introduction" />
</template>

<style scoped>
.intro-frame {
  width: 100%;
  height: calc(100vh - var(--nx-app-header-height));
  border: none;
  display: block;
  background: var(--ds-color-canvas);
}
</style>
