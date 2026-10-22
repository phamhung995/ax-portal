<script setup lang="ts">
import { computed, nextTick, ref, watch } from 'vue'
import { X, ChevronLeft, ChevronRight } from 'lucide-vue-next'

export interface LightboxImage {
  url: string
  name: string
}

const props = defineProps<{
  images: LightboxImage[]
  index: number
  open: boolean
}>()
const emit = defineEmits(['close', 'update:index'])

const nextBtn = ref<HTMLButtonElement | null>(null)

const current = computed(() => props.images[props.index] ?? null)

function step(delta: number) {
  if (props.images.length <= 1) return
  const next = (props.index + delta + props.images.length) % props.images.length
  emit('update:index', next)
}

function onKeydown(event: KeyboardEvent) {
  if (!props.open) return
  if (event.key === 'Escape') {
    emit('close')
  } else if (event.key === 'ArrowLeft') {
    step(-1)
  } else if (event.key === 'ArrowRight') {
    step(1)
  }
}

watch(
  () => props.open,
  (open) => {
    if (open) {
      document.addEventListener('keydown', onKeydown)
      document.body.style.overflow = 'hidden'
      void nextTick(() => nextBtn.value?.focus())
    } else {
      document.removeEventListener('keydown', onKeydown)
      document.body.style.overflow = ''
    }
  }
)
</script>

<template>
  <Teleport to="body">
    <div v-if="open && current" class="lightbox" role="dialog" aria-modal="true" aria-label="Image viewer">
      <div class="lightbox-scrim" @click="emit('close')"></div>
      <button
        type="button"
        class="lightbox-close"
        aria-label="Close image viewer"
        @click="emit('close')"
      >
        <X :size="20" aria-hidden="true" />
      </button>
      <button
        v-if="images.length > 1"
        type="button"
        class="lightbox-nav lightbox-prev"
        aria-label="Previous image"
        @click="step(-1)"
      >
        <ChevronLeft :size="24" aria-hidden="true" />
      </button>
      <figure class="lightbox-stage">
        <img :src="current.url" :alt="current.name" />
        <figcaption class="lightbox-caption">
          <span class="lightbox-name" :title="current.name">{{ current.name }}</span>
          <span class="lightbox-counter">{{ index + 1 }} / {{ images.length }}</span>
        </figcaption>
      </figure>
      <button
        v-if="images.length > 1"
        ref="nextBtn"
        type="button"
        class="lightbox-nav lightbox-next"
        aria-label="Next image"
        @click="step(1)"
      >
        <ChevronRight :size="24" aria-hidden="true" />
      </button>
    </div>
  </Teleport>
</template>

<style scoped>
.lightbox {
  position: fixed;
  inset: 0;
  z-index: 100;
  display: grid;
  place-items: center;
}
.lightbox-scrim {
  position: absolute;
  inset: 0;
  background: rgb(3 5 23 / .88);
  backdrop-filter: blur(8px);
}
.lightbox-stage {
  position: relative;
  z-index: 1;
  margin: 0;
  max-width: min(1100px, 92vw);
  max-height: 86vh;
  display: grid;
  gap: var(--ds-space-2);
  justify-items: center;
}
.lightbox-stage img {
  max-width: 100%;
  max-height: calc(86vh - 44px);
  object-fit: contain;
  border-radius: var(--ds-radius-md);
  border: 1px solid var(--ds-color-border-strong);
  box-shadow: 0 24px 80px rgb(3 5 23 / .6);
  background: #05081a;
}
.lightbox-caption {
  display: flex;
  align-items: center;
  gap: var(--ds-space-3);
  font-size: 13px;
  color: var(--ds-color-text-secondary);
  max-width: 100%;
}
.lightbox-name {
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}
.lightbox-counter {
  font-family: var(--ds-font-mono);
  font-size: 12px;
  color: var(--ds-color-text-muted);
  flex-shrink: 0;
}
.lightbox-close {
  position: absolute;
  top: var(--ds-space-4);
  right: var(--ds-space-4);
  z-index: 2;
  width: 40px;
  height: 40px;
  display: grid;
  place-items: center;
  border: 1px solid var(--ds-color-border);
  border-radius: var(--ds-radius-sm);
  background: rgb(5 8 26 / .6);
  color: var(--ds-color-text-primary);
  cursor: pointer;
}
@media (hover: hover) {
  .lightbox-close:hover {
    background: rgb(103 232 249 / .16);
    border-color: var(--ds-color-border-strong);
  }
}
.lightbox-nav {
  position: absolute;
  z-index: 2;
  top: 50%;
  transform: translateY(-50%);
  width: 44px;
  height: 44px;
  display: grid;
  place-items: center;
  border: 1px solid var(--ds-color-border);
  border-radius: 50%;
  background: rgb(5 8 26 / .6);
  color: var(--ds-color-text-primary);
  cursor: pointer;
  transition: all var(--ds-duration-fast) var(--ds-ease);
}
@media (hover: hover) {
  .lightbox-nav:hover {
    background: rgb(103 232 249 / .16);
    border-color: var(--ds-color-border-strong);
  }
}
.lightbox-prev {
  left: var(--ds-space-4);
}
.lightbox-next {
  right: var(--ds-space-4);
}
</style>
