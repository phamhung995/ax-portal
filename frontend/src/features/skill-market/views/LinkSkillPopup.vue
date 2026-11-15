<script setup lang="ts">
import { computed, ref, watch } from 'vue'
import { Link2, Plus } from 'lucide-vue-next'
import PrismDialog from '@/design-system/components/PrismDialog.vue'
import PrismButton from '@/design-system/components/PrismButton.vue'
import PrismInput from '@/design-system/components/PrismInput.vue'
import PrismTable from '@/design-system/components/PrismTable.vue'
import { skillApi } from '@/features/skills/api'
import { toUserError } from '@/lib/api'
import { toastError } from '@/lib/toast'
import { useI18nStore } from '@/stores/i18n'
import AddSkillPopup from '@/features/skills/views/AddSkillPopup.vue'
import type { SkillListItem } from '@/features/skills/types'

const props = defineProps<{ open: boolean }>()
const emit = defineEmits(['update:open', 'selected'])
const { t } = useI18nStore()

const search = ref('')
const loading = ref(false)
const error = ref('')
const skills = ref<SkillListItem[]>([])
const registerOpen = ref(false)
let searchTimer: ReturnType<typeof setTimeout> | null = null
let lastQuery = ''

// Only APPROVED skills can be linked from a comment.
async function loadSkills() {
  const kw = search.value.trim()
  loading.value = true
  error.value = ''
  try {
    const result = await skillApi.search({ skillName: kw || undefined, pageSize: 50 })
    skills.value = result.items.filter((s) => s.status === 'APPROVED')
    lastQuery = kw
  } catch (err) {
    error.value = toUserError(err).message
    skills.value = []
  } finally {
    loading.value = false
  }
}

watch(search, () => {
  if (searchTimer) clearTimeout(searchTimer)
  searchTimer = setTimeout(loadSkills, 400)
})

watch(
  () => props.open,
  (open) => {
    if (open) {
      search.value = ''
      skills.value = []
      error.value = ''
      loadSkills()
    }
  }
)

function onRowClick(skill: SkillListItem) {
  emit('selected', skill)
  emit('update:open', false)
}

function onRegistered(skillId: number, skillName: string) {
  // Auto-link the freshly registered skill to the request.
  const skill = skills.value.find((s) => s.id === skillId)
  if (skill) {
    emit('selected', skill)
    emit('update:open', false)
    return
  }
  // New skills are DRAFT (not in the APPROVED list) — build the item so the
  // pending skill still gets attached with its status tag.
  emit('selected', {
    id: skillId,
    name: skillName,
    description: null,
    creatorId: 0,
    creatorName: null,
    createdAt: new Date().toISOString(),
    status: 'DRAFT',
    scope: 'PRIVATE',
    categories: [],
    stages: [],
    viewCount: 0,
    avgRating: null,
    ratingCount: 0,
    subscribed: false,
  })
  emit('update:open', false)
}

const columns = computed(() => [
  { key: 'name', label: t('app.common.colName') },
  { key: 'description', label: t('app.common.description') },
  { key: 'creatorName', label: t('app.common.creator') },
])

// Strip rich-text HTML so the cell can show a single truncated line.
function plainText(html: string | null): string {
  if (!html) return ''
  const div = document.createElement('div')
  div.innerHTML = html
  return (div.textContent ?? '').replace(/\s+/g, ' ').trim()
}
</script>

<template>
  <PrismDialog :model-value="open" :title="t('app.skillMarket.linkTitle')" size="lg" @update:model-value="emit('update:open', $event)">
    <div class="link">
      <PrismInput v-model="search" :placeholder="t('app.skillMarket.linkSearchPh')" @keydown.enter="loadSkills()" />
      <p v-if="error" class="p-field-error" role="alert">{{ error }}</p>
      <PrismTable
        v-else
        :columns="columns"
        :rows="skills"
        row-key="id"
        :loading="loading"
        :caption="t('app.skillMarket.approvedSkillsCaption')"
        :empty-text="t('app.skillMarket.noApprovedSkills')"
      >
        <template #cell-name="{ row }">
          <button type="button" class="link-name" :title="(row as SkillListItem).name" @click="onRowClick(row as SkillListItem)">
            <Link2 :size="14" aria-hidden="true" />
            <span class="link-name-text">{{ (row as SkillListItem).name }}</span>
          </button>
        </template>
        <template #cell-description="{ row }">
          <span class="link-desc" :title="plainText((row as SkillListItem).description)">
            {{ plainText((row as SkillListItem).description) || '—' }}
          </span>
        </template>
      </PrismTable>
    </div>

    <template #footer>
      <PrismButton variant="secondary" @click="registerOpen = true">
        <Plus :size="16" aria-hidden="true" /> {{ t('app.skills.addBtn') }}
      </PrismButton>
      <span class="link-spacer" aria-hidden="true"></span>
      <PrismButton variant="ghost" @click="emit('update:open', false)">{{ t('app.common.cancel') }}</PrismButton>
    </template>
  </PrismDialog>

  <AddSkillPopup
    v-model:open="registerOpen"
    :show-cowork="false"
    :files-required="false"
    title-key="app.skillMarket.registerSkillTitle"
    save-label-key="app.skillMarket.registerBtn"
    hint-key="app.skillMarket.registerSkillHint"
    success-key="app.skillMarket.registeredToast"
    @saved="onRegistered"
  />
</template>

<style scoped>
.link {
  display: grid;
  gap: var(--ds-space-4);
}
.link-spacer {
  flex: 1;
}
.link-name {
  display: inline-flex;
  align-items: center;
  gap: var(--ds-space-2);
  border: none;
  background: none;
  padding: 0;
  color: var(--ds-color-accent);
  font: inherit;
  cursor: pointer;
  text-align: left;
  max-width: 100%;
}
.link-name :deep(svg) {
  flex: none;
}
.link-name-text {
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}
.link-name:hover {
  text-decoration: underline;
}
.link-desc {
  display: block;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
  color: var(--ds-color-text-secondary);
}
/* Widen the Description column, keep Name/Creator compact */
.link :deep(.p-table) {
  table-layout: fixed;
}
.link :deep(th:nth-child(1)),
.link :deep(td:nth-child(1)) {
  width: 240px;
}
.link :deep(th:nth-child(2)),
.link :deep(td:nth-child(2)) {
  min-width: 320px;
}
.link :deep(th:nth-child(3)),
.link :deep(td:nth-child(3)) {
  width: 160px;
}
</style>

<!-- The dialog is teleported to <body>, so scoped styles can't reach it.
     Target only the dialog that hosts this table via :has(). -->
<style>
.p-dialog:has(.p-dialog-body > .link) {
  width: min(1100px, calc(100vw - 32px));
}
</style>
