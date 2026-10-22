<script setup lang="ts">
import { ref, watch } from 'vue'
import { Link2, Plus } from 'lucide-vue-next'
import PrismDialog from '@/design-system/components/PrismDialog.vue'
import PrismButton from '@/design-system/components/PrismButton.vue'
import PrismInput from '@/design-system/components/PrismInput.vue'
import PrismTable from '@/design-system/components/PrismTable.vue'
import { skillApi } from '@/features/skills/api'
import { toUserError } from '@/lib/api'
import { toastError } from '@/lib/toast'
import RegisterSkillPopup from './RegisterSkillPopup.vue'
import type { SkillListItem } from '@/features/skills/types'

const props = defineProps<{ open: boolean }>()
const emit = defineEmits(['update:open', 'selected'])

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

const columns = [
  { key: 'name', label: 'Name' },
  { key: 'status', label: 'Status' },
  { key: 'creatorName', label: 'Creator' },
]
</script>

<template>
  <PrismDialog :model-value="open" title="Link an existing skill" size="lg" @update:model-value="emit('update:open', $event)">
    <div class="link">
      <PrismInput v-model="search" placeholder="Search skill name…" @keydown.enter="loadSkills()" />
      <p v-if="error" class="p-field-error" role="alert">{{ error }}</p>
      <PrismTable
        v-else
        :columns="columns"
        :rows="skills"
        row-key="id"
        :loading="loading"
        caption="Approved skills"
        empty-text="No approved skills found."
      >
        <template #cell-name="{ row }">
          <button type="button" class="link-name" :title="(row as SkillListItem).name" @click="onRowClick(row as SkillListItem)">
            <Link2 :size="14" aria-hidden="true" />
            {{ (row as SkillListItem).name }}
          </button>
        </template>
      </PrismTable>
    </div>

    <template #footer>
      <PrismButton variant="secondary" @click="registerOpen = true">
        <Plus :size="16" aria-hidden="true" /> Add Skill
      </PrismButton>
      <span class="link-spacer" aria-hidden="true"></span>
      <PrismButton variant="ghost" @click="emit('update:open', false)">Cancel</PrismButton>
    </template>
  </PrismDialog>

  <RegisterSkillPopup v-model:open="registerOpen" @saved="onRegistered" />
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
}
.link-name:hover {
  text-decoration: underline;
}
</style>
