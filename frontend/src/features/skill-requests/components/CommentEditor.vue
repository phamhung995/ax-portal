<script setup lang="ts">
import { computed, onBeforeUnmount, ref, watch } from 'vue'
import { useEditor, EditorContent } from '@tiptap/vue-3'
import StarterKit from '@tiptap/starter-kit'
import Link from '@tiptap/extension-link'
import Placeholder from '@tiptap/extension-placeholder'
import TaskList from '@tiptap/extension-task-list'
import TaskItem from '@tiptap/extension-task-item'

const props = withDefaults(
  defineProps<{
    modelValue: string
    placeholder?: string
    maxLength?: number
    disabled?: boolean
  }>(),
  { modelValue: '', placeholder: 'Share your thoughts…', maxLength: 1000, disabled: false }
)
const emit = defineEmits<{ 'update:modelValue': [value: string] }>()

const editor = useEditor({
  content: props.modelValue || '',
  extensions: [
    StarterKit,
    Link.configure({ openOnClick: false, autolink: true }),
    Placeholder.configure({ placeholder: () => props.placeholder || '' }),
    TaskList,
    TaskItem.configure({ nested: true }),
  ],
  editorProps: {
    attributes: {
      class: 'cmt-rte-content',
      'data-placeholder': props.placeholder || '',
    },
  },
  onUpdate: ({ editor: e }) => {
    emit('update:modelValue', e.isEmpty ? '' : e.getHTML())
  },
})

// external value changes (e.g. form reset) → sync into editor
watch(
  () => props.modelValue,
  (val) => {
    const e = editor.value
    if (!e) return
    const current = e.isEmpty ? '' : e.getHTML()
    if (val !== current) e.commands.setContent(val || '', { emitUpdate: false })
  }
)

onBeforeUnmount(() => {
  editor.value?.destroy()
})

const charCount = computed(() => {
  const e = editor.value
  if (!e) return 0
  return e.getText().replace(/\n$/, '').length
})
const overLimit = computed(() => charCount.value > (props.maxLength ?? 1000))

function active(cmd: 'bold' | 'italic' | 'underline' | 'strike' | 'bulletList' | 'orderedList' | 'taskList' | 'link') {
  return editor.value?.isActive(cmd) ?? false
}

function run(cmd: 'bold' | 'italic' | 'underline' | 'strike' | 'bulletList' | 'orderedList' | 'taskList') {
  const e = editor.value
  if (!e || props.disabled) return
  switch (cmd) {
    case 'bold': e.chain().focus().toggleBold().run(); break
    case 'italic': e.chain().focus().toggleItalic().run(); break
    case 'underline': e.chain().focus().toggleUnderline().run(); break
    case 'strike': e.chain().focus().toggleStrike().run(); break
    case 'bulletList': e.chain().focus().toggleBulletList().run(); break
    case 'orderedList': e.chain().focus().toggleOrderedList().run(); break
    case 'taskList': e.chain().focus().toggleList('taskList', 'taskItem').run(); break
  }
}

function setLink() {
  const e = editor.value
  if (!e || props.disabled) return
  if (e.isActive('link')) {
    e.chain().focus().unsetLink().run()
    return
  }
  const url = window.prompt('Link URL (https://...)', 'https://')
  if (url && /^https?:\/\//i.test(url)) {
    e.chain().focus().extendMarkRange('link').setLink({ href: url }).run()
  }
}
</script>

<template>
  <div class="cmt-rte" :class="{ 'is-disabled': disabled }">
    <div class="cmt-rte-toolbar" role="toolbar" aria-label="Comment formatting">
      <button type="button" class="cmt-rte-btn" :class="{ 'is-active': active('bold') }" :disabled="disabled" title="Bold" @click="run('bold')"><strong>B</strong></button>
      <button type="button" class="cmt-rte-btn" :class="{ 'is-active': active('italic') }" :disabled="disabled" title="Italic" @click="run('italic')"><em>I</em></button>
      <button type="button" class="cmt-rte-btn" :class="{ 'is-active': active('underline') }" :disabled="disabled" title="Underline" @click="run('underline')"><u>U</u></button>
      <button type="button" class="cmt-rte-btn" :class="{ 'is-active': active('strike') }" :disabled="disabled" title="Strikethrough" @click="run('strike')"><s>S</s></button>
      <span class="cmt-rte-sep" aria-hidden="true"></span>
      <button type="button" class="cmt-rte-btn" :class="{ 'is-active': active('bulletList') }" :disabled="disabled" title="Bullet list" @click="run('bulletList')">•≡</button>
      <button type="button" class="cmt-rte-btn" :class="{ 'is-active': active('orderedList') }" :disabled="disabled" title="Numbered list" @click="run('orderedList')">1≡</button>
      <button type="button" class="cmt-rte-btn" :class="{ 'is-active': active('taskList') }" :disabled="disabled" title="Task list" @click="run('taskList')">☑</button>
      <button type="button" class="cmt-rte-btn" :class="{ 'is-active': editor?.isActive('codeBlock') }" :disabled="disabled" title="Code block" @click="editor?.chain().focus().toggleCodeBlock().run()">&lt;/&gt;</button>
      <span class="cmt-rte-sep" aria-hidden="true"></span>
      <button type="button" class="cmt-rte-btn" :class="{ 'is-active': active('link') }" :disabled="disabled" title="Link" @click="setLink">🔗</button>
    </div>
    <EditorContent :editor="editor" />
  </div>
  <div class="cmt-rte-count" :class="{ 'is-over': overLimit }">{{ charCount }}/{{ maxLength }}</div>
</template>

<style scoped>
.cmt-rte {
  border: 1px solid var(--ds-color-border);
  border-radius: var(--ds-radius-sm);
  background: var(--ds-input-background);
  overflow: hidden;
  transition: border-color var(--ds-duration-fast) ease, box-shadow var(--ds-duration-fast) ease;
}
.cmt-rte:focus-within {
  border-color: rgb(103 232 249 / .42);
  box-shadow: var(--ds-shadow-glow);
}
.cmt-rte.is-disabled {
  opacity: 0.55;
}
.cmt-rte-toolbar {
  display: flex;
  align-items: center;
  gap: 2px;
  padding: 4px var(--ds-space-2);
  border-bottom: 1px solid var(--ds-color-border);
  background: rgb(255 255 255 / .02);
}
.cmt-rte-btn {
  min-width: 28px;
  height: 28px;
  padding: 0 6px;
  display: inline-flex;
  align-items: center;
  justify-content: center;
  border: none;
  border-radius: var(--ds-radius-sm);
  background: transparent;
  color: var(--ds-color-text-secondary);
  font-size: 13px;
  cursor: pointer;
  transition: background var(--ds-duration-fast) ease, color var(--ds-duration-fast) ease;
}
.cmt-rte-btn:hover:not(:disabled) {
  background: rgb(255 255 255 / .06);
  color: var(--ds-color-text-primary);
}
.cmt-rte-btn.is-active {
  color: var(--ds-color-accent);
  background: rgb(103 232 249 / .12);
}
.cmt-rte-btn:disabled {
  opacity: 0.4;
  cursor: not-allowed;
}
.cmt-rte-sep {
  width: 1px;
  height: 18px;
  margin: 0 4px;
  background: var(--ds-color-border);
}
.cmt-rte-content {
  min-height: 44px;
  padding: var(--ds-space-2) var(--ds-space-3);
  font-size: 14px;
  line-height: 1.6;
  color: var(--ds-color-text-primary);
  outline: none;
}
.cmt-rte-count {
  justify-self: end;
  font: 11px var(--ds-font-mono);
  color: var(--ds-color-text-muted);
}
.cmt-rte-count.is-over {
  color: var(--ds-color-danger);
}
.cmt-rte-content :deep(.ProseMirror) {
  outline: none;
  min-height: inherit;
  color: inherit;
  line-height: inherit;
  font-size: inherit;
}
.cmt-rte-content :deep(.ProseMirror p) {
  margin: 0 0 var(--ds-space-2);
}
.cmt-rte-content :deep(.ProseMirror ul),
.cmt-rte-content :deep(.ProseMirror ol) {
  margin: 0 0 var(--ds-space-2);
  padding-left: 22px;
}
.cmt-rte-content :deep(.ProseMirror ul[data-type='taskList']) {
  list-style: none;
  padding-left: 4px;
}
.cmt-rte-content :deep(.ProseMirror li[data-type='taskItem']) {
  display: flex;
  align-items: baseline;
  gap: 6px;
}
.cmt-rte-content :deep(.ProseMirror li[data-type='taskItem'] > label) {
  flex-shrink: 0;
}
.cmt-rte-content :deep(.ProseMirror li[data-type='taskItem'] input[type='checkbox']) {
  accent-color: var(--ds-color-accent);
}
.cmt-rte-content :deep(.ProseMirror li[data-type='taskItem'][data-checked='true'] > div) {
  text-decoration: line-through;
  color: var(--ds-color-text-muted, var(--ds-color-text-secondary));
}
.cmt-rte-content :deep(.ProseMirror a) {
  color: var(--ds-color-accent);
  text-decoration: underline;
}
.cmt-rte-content :deep(.ProseMirror .is-editor-empty:first-child::before) {
  content: attr(data-placeholder);
  float: left;
  color: var(--ds-color-text-muted, var(--ds-color-text-secondary));
  pointer-events: none;
  height: 0;
}
</style>
