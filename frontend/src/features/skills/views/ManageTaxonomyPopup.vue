<script setup lang="ts">
import { computed, ref, watch } from 'vue'
import PrismDialog from '@/design-system/components/PrismDialog.vue'
import PrismButton from '@/design-system/components/PrismButton.vue'
import PrismInput from '@/design-system/components/PrismInput.vue'
import { skillTaxonomyApi } from '@/features/skills/api'
import { toUserError } from '@/lib/api'
import { toastError, toastSuccess } from '@/lib/toast'
import type { SkillTaxonomyItem } from '@/features/skills/types'

const props = defineProps<{ open: boolean }>()
const emit = defineEmits(['update:open', 'changed'])

const categories = ref<SkillTaxonomyItem[]>([])
const stages = ref<SkillTaxonomyItem[]>([])
const loading = ref(false)
const newCategory = ref('')
const newStage = ref('')
const newStageDesc = ref('')
const addingCategory = ref(false)
const addingStage = ref(false)

// ── Edit stage ──────────────────────────────────────────────────────────────
const editStageId = ref<number | null>(null)
const editStageName = ref('')
const editStageDesc = ref('')
const savingStage = ref(false)
const editOpen = computed(() => editStageId.value !== null)

function openEditStage(item: SkillTaxonomyItem) {
  editStageId.value = item.id
  editStageName.value = item.name
  editStageDesc.value = item.description ?? ''
}

function closeEditStage() {
  editStageId.value = null
}

async function saveEditStage() {
  const name = editStageName.value.trim()
  if (editStageId.value == null || !name) return
  savingStage.value = true
  try {
    await skillTaxonomyApi.updateStage(editStageId.value, name, editStageDesc.value.trim() || undefined)
    toastSuccess(`Stage "${name}" updated.`)
    closeEditStage()
    await load()
    emit('changed')
  } catch (err) {
    toastError(toUserError(err).message)
  } finally {
    savingStage.value = false
  }
}

// ── Edit category ───────────────────────────────────────────────────────────
const editCategoryId = ref<number | null>(null)
const editCategoryName = ref('')
const savingCategory = ref(false)
const editCategoryOpen = computed(() => editCategoryId.value !== null)

function openEditCategory(item: SkillTaxonomyItem) {
  editCategoryId.value = item.id
  editCategoryName.value = item.name
}

function closeEditCategory() {
  editCategoryId.value = null
}

async function saveEditCategory() {
  const name = editCategoryName.value.trim()
  if (editCategoryId.value == null || !name) return
  savingCategory.value = true
  try {
    await skillTaxonomyApi.updateCategory(editCategoryId.value, name)
    toastSuccess(`Category "${name}" updated.`)
    closeEditCategory()
    await load()
    emit('changed')
  } catch (err) {
    toastError(toUserError(err).message)
  } finally {
    savingCategory.value = false
  }
}

async function load() {
  loading.value = true
  try {
    const [cats, sts] = await Promise.all([
      skillTaxonomyApi.listCategories(),
      skillTaxonomyApi.listStages(),
    ])
    categories.value = cats
    stages.value = sts
  } catch (err) {
    toastError(toUserError(err).message)
  } finally {
    loading.value = false
  }
}

watch(
  () => props.open,
  (open) => {
    if (open) {
      newCategory.value = ''
      newStage.value = ''
      newStageDesc.value = ''
      load()
    }
  }
)

async function addCategory() {
  const name = newCategory.value.trim()
  if (!name) return
  addingCategory.value = true
  try {
    await skillTaxonomyApi.createCategory(name)
    newCategory.value = ''
    toastSuccess(`Category "${name}" added.`)
    await load()
    emit('changed')
  } catch (err) {
    toastError(toUserError(err).message)
  } finally {
    addingCategory.value = false
  }
}

async function deleteCategory(item: SkillTaxonomyItem) {
  const message = item.skillCount > 0
    ? `Delete category "${item.name}"? It will be removed from ${item.skillCount} skill(s).`
    : `Delete category "${item.name}"?`
  if (!confirm(message)) return
  try {
    await skillTaxonomyApi.deleteCategory(item.id)
    toastSuccess(`Category "${item.name}" deleted.`)
    await load()
    emit('changed')
  } catch (err) {
    toastError(toUserError(err).message)
  }
}

async function addStage() {
  const name = newStage.value.trim()
  if (!name) return
  addingStage.value = true
  try {
    await skillTaxonomyApi.createStage(name, newStageDesc.value.trim() || undefined)
    newStage.value = ''
    newStageDesc.value = ''
    toastSuccess(`Stage "${name}" added.`)
    await load()
    emit('changed')
  } catch (err) {
    toastError(toUserError(err).message)
  } finally {
    addingStage.value = false
  }
}

async function deleteStage(item: SkillTaxonomyItem) {
  const message = item.skillCount > 0
    ? `Delete stage "${item.name}"? It will be removed from ${item.skillCount} skill(s).`
    : `Delete stage "${item.name}"?`
  if (!confirm(message)) return
  try {
    await skillTaxonomyApi.deleteStage(item.id)
    toastSuccess(`Stage "${item.name}" deleted.`)
    await load()
    emit('changed')
  } catch (err) {
    toastError(toUserError(err).message)
  }
}

function close() {
  emit('update:open', false)
}
</script>

<template>
  <PrismDialog
    :model-value="open"
    title="Manage Categories & Process Stages"
    size="lg"
    :close-on-overlay="true"
    :close-on-escape="true"
    @update:model-value="(v) => !v && close()"
    @close="close"
  >
    <div v-if="loading" class="mgt-loading">Loading…</div>
    <div v-else class="mgt">
      <div class="mgt-cols">
        <!-- Categories -->
        <div class="mgt-col">
          <h3 class="mgt-title">
            <span class="mgt-dot mgt-dot--cat" aria-hidden="true"></span>
            Categories
            <span class="mgt-count">({{ categories.length }})</span>
          </h3>
          <ul class="mgt-list">
            <li v-for="c in categories" :key="c.id" class="mgt-item">
              <span class="mgt-name" :title="c.name">{{ c.name }}</span>
              <span class="mgt-use">{{ c.skillCount }} skill{{ c.skillCount === 1 ? '' : 's' }}</span>
              <button
                type="button"
                class="mgt-edit"
                :aria-label="`Edit category ${c.name}`"
                @click="openEditCategory(c)"
              >✎</button>
              <button
                type="button"
                class="mgt-del"
                :aria-label="`Delete category ${c.name}`"
                @click="deleteCategory(c)"
              >✕</button>
            </li>
            <li v-if="!categories.length" class="mgt-empty">No categories yet.</li>
          </ul>
          <div class="mgt-add">
            <PrismInput
              v-model="newCategory"
              placeholder="New category name"
              :maxlength="50"
              @keydown.enter="addCategory"
            />
            <PrismButton :loading="addingCategory" :disabled="!newCategory.trim()" @click="addCategory">Add</PrismButton>
          </div>
          <p class="mgt-locked">🔒 Admin only. Deleting a category removes it from all skills using it.</p>
        </div>

        <!-- Process stages -->
        <div class="mgt-col">
          <h3 class="mgt-title">
            <span class="mgt-dot mgt-dot--stage" aria-hidden="true"></span>
            Process Stages
            <span class="mgt-count">({{ stages.length }})</span>
          </h3>
          <ul class="mgt-list">
            <li v-for="(st, i) in stages" :key="st.id" class="mgt-item mgt-item--stage">
              <span class="mgt-order" aria-hidden="true">{{ i + 1 }}</span>
              <span class="mgt-stage-info">
                <span class="mgt-name" :title="st.name">{{ st.name }}</span>
                <span v-if="st.description" class="mgt-desc" :title="st.description">{{ st.description }}</span>
              </span>
              <span class="mgt-use">{{ st.skillCount }} skill{{ st.skillCount === 1 ? '' : 's' }}</span>
              <button
                type="button"
                class="mgt-edit"
                :aria-label="`Edit stage ${st.name}`"
                @click="openEditStage(st)"
              >✎</button>
              <button
                type="button"
                class="mgt-del"
                :aria-label="`Delete stage ${st.name}`"
                @click="deleteStage(st)"
              >✕</button>
            </li>
            <li v-if="!stages.length" class="mgt-empty">No stages yet.</li>
          </ul>
          <div class="mgt-add">
            <PrismInput
              v-model="newStage"
              placeholder="New stage name"
              :maxlength="50"
              @keydown.enter="addStage"
            />
          </div>
          <div class="mgt-add">
            <PrismInput
              v-model="newStageDesc"
              placeholder="Short description (optional)"
              :maxlength="200"
              @keydown.enter="addStage"
            />
            <PrismButton :loading="addingStage" :disabled="!newStage.trim()" @click="addStage">Add</PrismButton>
          </div>
          <p class="mgt-locked">🔒 Admin only. Stages keep their order in the pipeline.</p>
        </div>
      </div>
    </div>

    <template #footer>
      <PrismButton variant="secondary" @click="close">Close</PrismButton>
    </template>
  </PrismDialog>

  <PrismDialog
    :model-value="editCategoryOpen"
    title="Edit Category"
    size="sm"
    @update:model-value="(v) => !v && closeEditCategory()"
    @close="closeEditCategory"
  >
    <div class="mgt-edit-form">
      <PrismInput
        v-model="editCategoryName"
        label="Category name"
        required
        :maxlength="50"
      />
    </div>
    <template #footer>
      <PrismButton variant="secondary" @click="closeEditCategory">Cancel</PrismButton>
      <PrismButton :loading="savingCategory" :disabled="!editCategoryName.trim()" @click="saveEditCategory">Save</PrismButton>
    </template>
  </PrismDialog>

  <PrismDialog
    :model-value="editOpen"
    title="Edit Process Stage"
    size="sm"
    @update:model-value="(v) => !v && closeEditStage()"
    @close="closeEditStage"
  >
    <div class="mgt-edit-form">
      <PrismInput
        v-model="editStageName"
        label="Stage name"
        required
        :maxlength="50"
      />
      <PrismInput
        v-model="editStageDesc"
        label="Short description"
        placeholder="Optional"
        :maxlength="200"
      />
    </div>
    <template #footer>
      <PrismButton variant="secondary" @click="closeEditStage">Cancel</PrismButton>
      <PrismButton :loading="savingStage" :disabled="!editStageName.trim()" @click="saveEditStage">Save</PrismButton>
    </template>
  </PrismDialog>
</template>

<style scoped>
.mgt-loading {
  padding: var(--ds-space-6);
  text-align: center;
  color: var(--ds-color-text-secondary);
}
.mgt {
  display: grid;
  gap: var(--ds-space-4);
}
.mgt-cols {
  display: grid;
  grid-template-columns: 1fr 1fr;
  gap: 20px;
}
@media (max-width: 599px) {
  .mgt-cols {
    grid-template-columns: 1fr;
  }
}
.mgt-col {
  display: grid;
  gap: var(--ds-space-3);
  align-content: start;
}
.mgt-title {
  display: flex;
  align-items: center;
  gap: 8px;
  margin: 0;
  font-size: 13px;
  font-weight: 600;
  color: var(--ds-color-text-primary);
}
.mgt-dot {
  width: 8px;
  height: 8px;
  border-radius: 50%;
}
.mgt-dot--cat {
  background: var(--ds-color-violet-400, #a58aef);
}
.mgt-dot--stage {
  background: var(--ds-color-accent);
}
.mgt-count {
  font-size: 11px;
  font-weight: 400;
  color: var(--ds-color-text-muted);
}
.mgt-list {
  list-style: none;
  margin: 0;
  padding: 0;
  display: grid;
  gap: 6px;
  max-height: 320px;
  overflow-y: auto;
}
.mgt-item {
  display: flex;
  align-items: center;
  gap: 8px;
  border: 1px solid var(--ds-color-border);
  border-radius: var(--ds-radius-sm);
  padding: 7px 12px;
  font-size: 13px;
  background: var(--ds-input-background);
}
.mgt-name {
  flex: 1;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
  color: var(--ds-color-text-primary);
}
.mgt-stage-info {
  flex: 1;
  min-width: 0;
  display: grid;
  gap: 1px;
}
.mgt-stage-info .mgt-name {
  flex: none;
}
.mgt-desc {
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
  font-size: 11px;
  color: var(--ds-color-text-muted);
}
.mgt-order {
  width: 18px;
  text-align: center;
  font-size: 11px;
  color: var(--ds-color-text-muted);
}
.mgt-use {
  font-size: 11px;
  color: var(--ds-color-text-muted);
  white-space: nowrap;
}
.mgt-del {
  border: none;
  background: none;
  cursor: pointer;
  color: var(--ds-color-text-muted);
  font-size: 13px;
  padding: 2px 6px;
  border-radius: 4px;
}
@media (hover: hover) {
  .mgt-del:hover {
    color: var(--ds-color-danger);
    background: rgb(255 172 196 / .08);
  }
}
.mgt-edit {
  border: none;
  background: none;
  cursor: pointer;
  color: var(--ds-color-text-muted);
  font-size: 13px;
  padding: 2px 6px;
  border-radius: 4px;
}
@media (hover: hover) {
  .mgt-edit:hover {
    color: var(--ds-color-accent);
    background: rgb(103 232 249 / .08);
  }
}
.mgt-edit-form {
  display: grid;
  gap: var(--ds-space-4);
}
.mgt-empty {
  color: var(--ds-color-text-muted);
  font-size: 12px;
}
.mgt-add {
  display: flex;
  gap: var(--ds-space-2);
  align-items: center;
}
/* PrismFormField always renders a <label>; the add inputs have no label,
   so hide the empty one to keep the input and button on one line. */
.mgt-add :deep(.p-field > label) {
  display: none;
}
.mgt-add :deep(.p-field) {
  flex: 1;
  min-width: 0;
}
.mgt-add :deep(.p-input-wrap) {
  width: 100%;
  min-height: 38px;
}
.mgt-add :deep(.p-input-wrap input) {
  min-height: 38px;
}
.mgt-add :deep(.p-button) {
  min-height: 38px;
  padding: 6px 14px;
  font-size: 13px;
  font-weight: 600;
}
.mgt-locked {
  margin: 0;
  font-size: 11px;
  color: var(--ds-color-text-muted);
}
</style>
