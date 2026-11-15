<script setup lang="ts">
import { nextTick, onBeforeUnmount, ref, watch } from 'vue'
import { Download } from 'lucide-vue-next'
import { useI18nStore } from '@/stores/i18n'
import { AX_DESKTOP_DOWNLOAD_URL } from '@/config/appPromo'

const props = defineProps<{ open: boolean }>()
const emit = defineEmits(['update:open'])
const { t } = useI18nStore()

const element = ref<HTMLDialogElement | null>(null)
const noAgainToday = ref(false)

// Same-origin anchor with the `download` attribute — triggers a download in
// the current tab instead of opening a new one.
function onDownload() {
  const a = document.createElement('a')
  a.href = AX_DESKTOP_DOWNLOAD_URL
  a.download = AX_DESKTOP_DOWNLOAD_URL.split('/').pop() ?? ''
  document.body.appendChild(a)
  a.click()
  a.remove()
}

watch(
  () => props.open,
  async (open) => {
    await nextTick()
    const el = element.value
    if (!el) return
    if (open && !el.open) {
      noAgainToday.value = false
      el.showModal()
    } else if (!open && el.open) {
      el.close()
    }
  },
  { immediate: true },
)

function onClose() {
  if (noAgainToday.value) {
    const today = new Date().toISOString().slice(0, 10) // date YYYY-MM-DD (UTC)
    localStorage.setItem('ax-desktop-promo-dismissed', today)
  }
  emit('update:open', false)
}

// Clicking the dimmed area around the dialog (backdrop) closes it.
function onDialogClick(event: MouseEvent) {
  if (event.target === element.value) element.value?.close()
}

onBeforeUnmount(() => element.value?.close())
</script>

<template>
  <Teleport to="body">
    <dialog
      ref="element"
      class="p-glass app-promo"
      :aria-label="t('app.promo.ariaLabel')"
      @close="onClose"
      @click="onDialogClick"
    >
      <button type="button" class="app-promo-close" :aria-label="t('app.promo.closeAria')" @click="element?.close()">✕</button>
      <div class="app-promo-body">
        <div class="app-promo-badges">
          <span class="app-promo-badge-new">{{ t('app.promo.newBadge') }}</span>
          <span class="app-promo-badge-free">{{ t('app.promo.freeBadge') }}</span>
        </div>
        <div class="app-promo-title">
          {{ t('app.promo.title') }}
        </div>
        <div class="app-promo-sub">
          {{ t('app.promo.subtitle') }}
        </div>
        <button
          type="button"
          class="app-promo-cta"
          :title="t('app.promo.cta')"
          @click="onDownload"
        >
          <Download :size="17" aria-hidden="true" />
          {{ t('app.promo.cta') }}
        </button>
        <div class="app-promo-meta">{{ t('app.promo.meta') }}</div>
      </div>
      <div class="app-promo-foot">
        <label class="app-promo-noagain">
          <input v-model="noAgainToday" type="checkbox" />
          {{ t('app.promo.dontShow') }}
        </label>
      </div>
    </dialog>
  </Teleport>
</template>

<style scoped>
.app-promo {
  width: 440px;
  max-width: calc(100vw - 32px);
  padding: 0;
  border: 1px solid var(--ds-color-border-strong);
  border-radius: var(--ds-radius-lg);
  box-shadow: var(--ds-shadow-glass), 0 24px 64px rgb(3 5 23 / 0.5);
  background:
    radial-gradient(300px 140px at 50% -20%, rgb(103 232 249 / 0.22), transparent 70%),
    radial-gradient(260px 160px at 90% 110%, rgb(165 138 239 / 0.18), transparent 70%),
    var(--ds-dialog-background, rgb(15 32 62 / 0.96));
}
.app-promo::backdrop {
  background: rgb(5 8 26 / 0.55);
  backdrop-filter: blur(2px);
}
.app-promo-close {
  position: absolute;
  top: 10px;
  right: 10px;
  z-index: 5;
  width: 28px;
  height: 28px;
  display: grid;
  place-items: center;
  border: none;
  border-radius: 50%;
  background: rgb(255 255 255 / 0.06);
  color: var(--ds-color-text-muted);
  cursor: pointer;
  font-size: 14px;
  line-height: 1;
}
.app-promo-close:hover {
  background: rgb(255 255 255 / 0.12);
  color: var(--ds-color-text-primary);
}
.app-promo-body {
  padding: 28px 28px 20px;
  display: grid;
  gap: 14px;
  text-align: center;
  justify-items: center;
}
.app-promo-badges {
  display: flex;
  align-items: center;
  gap: 8px;
}
.app-promo-badge-new,
.app-promo-badge-free {
  font: 700 10.5px var(--ds-font-mono);
  letter-spacing: 0.12em;
  padding: 3px 10px;
  border-radius: 10px;
}
.app-promo-badge-new {
  color: var(--ds-color-space-950);
  background: linear-gradient(90deg, var(--ds-color-accent), var(--ds-color-success));
}
.app-promo-badge-free {
  color: var(--ds-color-space-950);
  background: linear-gradient(90deg, var(--ds-color-warning), var(--ds-color-success));
}
.app-promo-title {
  font-size: 21px;
  font-weight: 700;
  line-height: 1.3;
}
.app-promo-sub {
  font-size: 13.5px;
  color: var(--ds-color-text-secondary);
  line-height: 1.55;
  max-width: 320px;
}
.app-promo-cta {
  width: 100%;
  max-width: 300px;
  display: flex;
  align-items: center;
  justify-content: center;
  gap: 9px;
  border: 1px solid rgb(103 232 249 / 0.55);
  background: linear-gradient(180deg, rgb(103 232 249 / 0.3), rgb(103 232 249 / 0.14));
  color: var(--ds-color-text-primary);
  font: 700 15px var(--ds-font-sans);
  padding: 13px 24px;
  border-radius: 28px;
  cursor: pointer;
  box-shadow: var(--ds-shadow-glow);
  transition: transform 0.16s ease, box-shadow 0.16s ease;
}
.app-promo-cta:hover {
  transform: translateY(-1px);
}
.app-promo-meta {
  font: 11px var(--ds-font-mono);
  color: var(--ds-color-text-muted);
}
.app-promo-foot {
  display: flex;
  align-items: center;
  justify-content: center;
  padding: 12px 0;
  border-top: 1px solid var(--ds-color-border);
  background: rgb(9 20 45 / 0.35);
}
.app-promo-noagain {
  display: flex;
  align-items: center;
  gap: 8px;
  font-size: 12.5px;
  color: var(--ds-color-text-muted);
  cursor: pointer;
  user-select: none;
}
.app-promo-noagain input {
  accent-color: var(--ds-color-accent);
  width: 14px;
  height: 14px;
  cursor: pointer;
}
</style>
