<script setup lang="ts">
import { computed, onBeforeUnmount, onMounted, ref, watch } from 'vue'
import { useEditor, EditorContent } from '@tiptap/vue-3'
import { BubbleMenuPlugin } from '@tiptap/extension-bubble-menu'
import StarterKit from '@tiptap/starter-kit'
import Link from '@tiptap/extension-link'
import Placeholder from '@tiptap/extension-placeholder'
import { Table, TableCell, TableHeader, TableRow } from '@tiptap/extension-table'
import TaskList from '@tiptap/extension-task-list'
import TaskItem from '@tiptap/extension-task-item'
import Highlight from '@tiptap/extension-highlight'
import TextAlign from '@tiptap/extension-text-align'
import Subscript from '@tiptap/extension-subscript'
import Superscript from '@tiptap/extension-superscript'
import Image from '@tiptap/extension-image'
import PrismFormField from '@/design-system/components/PrismFormField.vue'
import { useId } from 'vue'

const props = defineProps<{
  modelValue: string
  label?: string
  placeholder?: string
  rows?: number
  maxLength?: number
  disabled?: boolean
  required?: boolean
}>()
const emit = defineEmits<{ 'update:modelValue': [value: string] }>()

const generatedId = useId()

const editor = useEditor({
  content: props.modelValue || '',
  extensions: [
    StarterKit,
    Link.configure({ openOnClick: false, autolink: true }),
    Placeholder.configure({ placeholder: () => props.placeholder || '' }),
    Table.configure({ resizable: false }),
    TableRow,
    TableHeader,
    TableCell,
    TaskList,
    TaskItem.configure({ nested: true }),
    Highlight.configure({ multicolor: false }),
    TextAlign.configure({ types: ['heading', 'paragraph'] }),
    Subscript,
    Superscript,
    Image.configure({ allowBase64: true, resize: { enabled: true, minWidth: 40, minHeight: 20 } }),
  ],
  editorProps: {
    attributes: {
      id: generatedId,
      class: 'rich-text-area',
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
  },
)

onBeforeUnmount(() => {
  editor.value?.destroy()
})

function active(cmd: 'bold' | 'italic' | 'underline' | 'strike' | 'bulletList' | 'orderedList' | 'taskList' | 'blockquote' | 'codeBlock' | 'subscript' | 'superscript' | 'highlight') {
  return editor.value?.isActive(cmd) ?? false
}

function run(cmd: 'bold' | 'italic' | 'underline' | 'strike' | 'bulletList' | 'orderedList' | 'taskList' | 'blockquote' | 'codeBlock' | 'subscript' | 'superscript' | 'highlight') {
  const e = editor.value
  if (!e) return
  switch (cmd) {
    case 'bold': e.chain().focus().toggleBold().run(); break
    case 'italic': e.chain().focus().toggleItalic().run(); break
    case 'underline': e.chain().focus().toggleUnderline().run(); break
    case 'strike': e.chain().focus().toggleStrike().run(); break
    case 'bulletList': e.chain().focus().toggleBulletList().run(); break
    case 'orderedList': e.chain().focus().toggleOrderedList().run(); break
    case 'taskList': e.chain().focus().toggleList('taskList', 'taskItem').run(); break
    case 'blockquote': e.chain().focus().toggleBlockquote().run(); break
    case 'codeBlock': e.chain().focus().toggleCodeBlock().run(); break
    case 'subscript': e.chain().focus().toggleSubscript().run(); break
    case 'superscript': e.chain().focus().toggleSuperscript().run(); break
    case 'highlight': e.chain().focus().toggleHighlight().run(); break
  }
}

function setHeading(level: 1 | 2 | 3 | 0) {
  const e = editor.value
  if (!e) return
  if (level) e.chain().focus().setHeading({ level }).run()
  else e.chain().focus().setParagraph().run()
}

function setAlign(align: 'left' | 'center' | 'right' | 'justify') {
  editor.value?.chain().focus().setTextAlign(align).run()
}

function isAlign(align: 'left' | 'center' | 'right' | 'justify') {
  return editor.value?.isActive({ textAlign: align }) ?? false
}

function setLink() {
  const e = editor.value
  if (!e) return
  if (e.isActive('link')) {
    e.chain().focus().unsetLink().run()
    return
  }
  const url = window.prompt('Link URL (https://...)', 'https://')
  if (url && /^https?:\/\//i.test(url)) {
    e.chain().focus().extendMarkRange('link').setLink({ href: url }).run()
  }
}

const IMAGE_ALLOWED = ['jpeg', 'jpg', 'png', 'webp']
const IMAGE_MAX_SIZE = 5 * 1024 * 1024 // 5MB (same as avatar policy)
const IMAGE_WARN_SIZE = 500 * 1024 // above this → auto-compress before storing
const IMAGE_MAX_DIM = 1280 // longest side after compression (plenty for a description)
const IMAGE_JPEG_QUALITY = 0.85
const imageInput = ref<HTMLInputElement | null>(null)

function setImage() {
  if (props.disabled) return
  imageInput.value?.click()
}

/**
 * Compress an image via canvas: scale the longest side down to IMAGE_MAX_DIM
 * and re-encode as JPEG (white background for transparent PNGs).
 * Resolves with the smaller of the compressed and original data URIs.
 */
function compressImage(file: File): Promise<string> {
  return new Promise((resolve, reject) => {
    const url = URL.createObjectURL(file)
    const img = document.createElement('img')
    img.onload = () => {
      const scale = Math.min(1, IMAGE_MAX_DIM / Math.max(img.width, img.height))
      const w = Math.max(1, Math.round(img.width * scale))
      const h = Math.max(1, Math.round(img.height * scale))
      const canvas = document.createElement('canvas')
      canvas.width = w
      canvas.height = h
      const ctx = canvas.getContext('2d')
      if (!ctx) {
        URL.revokeObjectURL(url)
        reject(new Error('Canvas not available'))
        return
      }
      ctx.fillStyle = '#fff'
      ctx.fillRect(0, 0, w, h)
      ctx.drawImage(img, 0, 0, w, h)
      URL.revokeObjectURL(url)
      const compressed = canvas.toDataURL('image/jpeg', IMAGE_JPEG_QUALITY)
      // keep whichever is smaller (rarely, re-encoding can be larger)
      if (compressed.length < file.size * 1.34) resolve(compressed)
      else void readAsDataUrl(file).then(resolve)
    }
    img.onerror = () => {
      URL.revokeObjectURL(url)
      reject(new Error('Could not read image'))
    }
    img.src = url
  })
}

function readAsDataUrl(file: File): Promise<string> {
  return new Promise((resolve, reject) => {
    const reader = new FileReader()
    reader.onload = () => resolve(typeof reader.result === 'string' ? reader.result : '')
    reader.onerror = () => reject(new Error('Could not read file'))
    reader.readAsDataURL(file)
  })
}

async function onImagePicked(ev: Event) {
  const input = ev.target as HTMLInputElement
  const file = input.files?.[0]
  input.value = '' // allow re-selecting the same file
  if (!file) return
  const ext = file.name.split('.').pop()?.toLowerCase() ?? ''
  if (!IMAGE_ALLOWED.includes(ext)) {
    window.alert('Unsupported image type. Allowed: .jpg, .jpeg, .png, .webp')
    return
  }
  if (file.size > IMAGE_MAX_SIZE) {
    window.alert('Image too large. Maximum size is 5MB.')
    return
  }
  let dataUri: string
  try {
    // small images: keep original bytes/format; larger ones: auto-compress
    dataUri = file.size <= IMAGE_WARN_SIZE
      ? await readAsDataUrl(file)
      : await compressImage(file)
  } catch {
    window.alert('Could not process the image. Please try a different file.')
    return
  }
  if (dataUri) editor.value?.chain().focus().setImage({ src: dataUri, alt: file.name }).run()
}

function insertTable() {
  editor.value?.chain().focus().insertTable({ rows: 3, cols: 3, withHeaderRow: true }).run()
}

function deleteTable() {
  editor.value?.chain().focus().deleteTable().run()
}

function addRowAfter() {
  editor.value?.chain().focus().addRowAfter().run()
}

function deleteRow() {
  editor.value?.chain().focus().deleteRow().run()
}

function addColumnAfter() {
  editor.value?.chain().focus().addColumnAfter().run()
}

function deleteColumn() {
  editor.value?.chain().focus().deleteColumn().run()
}

const isH1 = computed(() => editor.value?.isActive('heading', { level: 1 }) ?? false)
const isH2 = computed(() => editor.value?.isActive('heading', { level: 2 }) ?? false)
const isH3 = computed(() => editor.value?.isActive('heading', { level: 3 }) ?? false)
const isLink = computed(() => editor.value?.isActive('link') ?? false)
const isTable = computed(() => editor.value?.isActive('table') ?? false)
const bubbleVisible = ref(false)
const bubbleEl = ref<HTMLElement | null>(null)

onMounted(() => {
  const e = editor.value
  if (!e || !bubbleEl.value) return
  e.registerPlugin(
    BubbleMenuPlugin({
      editor: e,
      element: bubbleEl.value,
      pluginKey: 'bubbleMenu',
      shouldShow: ({ editor: ed }) =>
        ed.isActive('bold') ||
        ed.isActive('italic') ||
        ed.isActive('underline') ||
        ed.isActive('strike') ||
        ed.isActive('link') ||
        ed.isActive('highlight'),
      options: {
        onShow: () => { bubbleVisible.value = true },
        onHide: () => { bubbleVisible.value = false },
      },
    }),
  )
})
</script>

<template>
  <PrismFormField :id="generatedId" :label="label" :required="required">
    <div class="rte">
      <div class="rte-toolbar" role="toolbar" aria-label="Text formatting">
        <button type="button" class="rte-btn" :class="{ 'is-active': active('bold') }" :disabled="disabled" @click="run('bold')" title="Bold"><strong>B</strong></button>
        <button type="button" class="rte-btn" :class="{ 'is-active': active('italic') }" :disabled="disabled" @click="run('italic')" title="Italic"><em>I</em></button>
        <button type="button" class="rte-btn" :class="{ 'is-active': active('underline') }" :disabled="disabled" @click="run('underline')" title="Underline"><u>U</u></button>
        <button type="button" class="rte-btn" :class="{ 'is-active': active('strike') }" :disabled="disabled" @click="run('strike')" title="Strikethrough"><s>S</s></button>
        <button type="button" class="rte-btn" :class="{ 'is-active': active('highlight') }" :disabled="disabled" @click="run('highlight')" title="Highlight">🖍</button>
        <button type="button" class="rte-btn" :class="{ 'is-active': active('subscript') }" :disabled="disabled" @click="run('subscript')" title="Subscript">X<sub>2</sub></button>
        <button type="button" class="rte-btn" :class="{ 'is-active': active('superscript') }" :disabled="disabled" @click="run('superscript')" title="Superscript">X<sup>2</sup></button>
        <span class="rte-sep" aria-hidden="true"></span>
        <button type="button" class="rte-btn" :class="{ 'is-active': isH1 }" :disabled="disabled" @click="setHeading(1)" title="Heading 1">H1</button>
        <button type="button" class="rte-btn" :class="{ 'is-active': isH2 }" :disabled="disabled" @click="setHeading(2)" title="Heading 2">H2</button>
        <button type="button" class="rte-btn" :class="{ 'is-active': isH3 }" :disabled="disabled" @click="setHeading(3)" title="Heading 3">H3</button>
        <button type="button" class="rte-btn" :disabled="disabled" @click="setHeading(0)" title="Paragraph">¶</button>
        <span class="rte-sep" aria-hidden="true"></span>
        <button type="button" class="rte-btn" :class="{ 'is-active': isAlign('left') }" :disabled="disabled" @click="setAlign('left')" title="Align left">⇤</button>
        <button type="button" class="rte-btn" :class="{ 'is-active': isAlign('center') }" :disabled="disabled" @click="setAlign('center')" title="Align center">⇹</button>
        <button type="button" class="rte-btn" :class="{ 'is-active': isAlign('right') }" :disabled="disabled" @click="setAlign('right')" title="Align right">⇥</button>
        <span class="rte-sep" aria-hidden="true"></span>
        <button type="button" class="rte-btn" :class="{ 'is-active': active('bulletList') }" :disabled="disabled" @click="run('bulletList')" title="Bullet list">•≡</button>
        <button type="button" class="rte-btn" :class="{ 'is-active': active('orderedList') }" :disabled="disabled" @click="run('orderedList')" title="Numbered list">1≡</button>
        <button type="button" class="rte-btn" :class="{ 'is-active': active('taskList') }" :disabled="disabled" @click="run('taskList')" title="Task list">☑</button>
        <button type="button" class="rte-btn" :class="{ 'is-active': active('blockquote') }" :disabled="disabled" @click="run('blockquote')" title="Quote">❝</button>
        <button type="button" class="rte-btn" :class="{ 'is-active': active('codeBlock') }" :disabled="disabled" @click="run('codeBlock')" title="Code block">&lt;/&gt;</button>
        <span class="rte-sep" aria-hidden="true"></span>
        <button type="button" class="rte-btn" :class="{ 'is-active': isTable }" :disabled="disabled" @click="insertTable()" title="Insert table">▦</button>
        <button type="button" class="rte-btn" :disabled="disabled || !isTable" @click="addRowAfter()" title="Add row below">+☰</button>
        <button type="button" class="rte-btn" :disabled="disabled || !isTable" @click="deleteRow()" title="Delete row">☰✕</button>
        <button type="button" class="rte-btn" :disabled="disabled || !isTable" @click="addColumnAfter()" title="Add column right">+▥</button>
        <button type="button" class="rte-btn" :disabled="disabled || !isTable" @click="deleteColumn()" title="Delete column">▥✕</button>
        <button type="button" class="rte-btn" :disabled="disabled || !isTable" @click="deleteTable()" title="Delete table">▦✕</button>
        <span class="rte-sep" aria-hidden="true"></span>
        <button type="button" class="rte-btn" :class="{ 'is-active': isLink }" :disabled="disabled" @click="setLink()" title="Link">🔗</button>
        <button type="button" class="rte-btn" :disabled="disabled" @click="setImage()" title="Image">🖼</button>
      </div>
      <input ref="imageInput" type="file" accept=".jpg,.jpeg,.png,.webp" class="rte-file-input" @change="onImagePicked" tabindex="-1" aria-hidden="true" />
      <div class="rte-content-wrap">
        <EditorContent :editor="editor" class="rte-content" :style="{ minHeight: `${(rows || 4) * 1.6}em` }" />
        <div ref="bubbleEl" class="rte-bubble" v-show="bubbleVisible">
          <button type="button" class="rte-btn" :class="{ 'is-active': active('bold') }" @click="run('bold')" title="Bold"><strong>B</strong></button>
          <button type="button" class="rte-btn" :class="{ 'is-active': active('italic') }" @click="run('italic')" title="Italic"><em>I</em></button>
          <button type="button" class="rte-btn" :class="{ 'is-active': active('underline') }" @click="run('underline')" title="Underline"><u>U</u></button>
          <button type="button" class="rte-btn" :class="{ 'is-active': active('highlight') }" @click="run('highlight')" title="Highlight">🖍</button>
          <button type="button" class="rte-btn" :class="{ 'is-active': isLink }" @click="setLink()" title="Link">🔗</button>
        </div>
      </div>
    </div>
  </PrismFormField>
</template>

<style scoped>
.rte {
  border: 1px solid var(--ds-color-border);
  border-radius: var(--ds-radius-sm);
  background: var(--ds-input-background);
  overflow: hidden;
}
.rte-toolbar {
  display: flex;
  align-items: center;
  flex-wrap: wrap;
  gap: 2px;
  padding: var(--ds-space-1) var(--ds-space-2);
  border-bottom: 1px solid var(--ds-color-border);
  background: var(--ds-color-bg-secondary, rgba(255, 255, 255, 0.02));
}
.rte-btn {
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
}
.rte-btn:hover:not(:disabled) {
  background: var(--ds-color-bg-hover, rgba(255, 255, 255, 0.06));
  color: var(--ds-color-text-primary);
}
.rte-btn.is-active {
  color: var(--ds-color-accent);
  background: var(--ds-color-accent-soft, rgba(56, 189, 248, 0.12));
}
.rte-btn:disabled {
  opacity: 0.4;
  cursor: not-allowed;
}
.rte-sep {
  width: 1px;
  height: 18px;
  margin: 0 4px;
  background: var(--ds-color-border);
}
.rte-file-input {
  display: none;
}
.rte-content-wrap {
  position: relative;
}
.rte-content {
  padding: var(--ds-space-2) var(--ds-space-3);
}
.rte-bubble {
  display: flex;
  align-items: center;
  gap: 2px;
  padding: 3px;
  border: 1px solid var(--ds-color-border);
  border-radius: var(--ds-radius-sm);
  background: var(--ds-color-bg-primary, #12121a);
  box-shadow: 0 4px 16px rgba(0, 0, 0, 0.35);
  z-index: 10;
}
.rte-content :deep(.ProseMirror) {
  outline: none;
  min-height: inherit;
  color: var(--ds-color-text-primary);
  line-height: 1.6;
}
.rte-content :deep(.ProseMirror p) {
  margin: 0 0 var(--ds-space-2);
}
.rte-content :deep(.ProseMirror h1),
.rte-content :deep(.ProseMirror h2),
.rte-content :deep(.ProseMirror h3) {
  margin: var(--ds-space-3) 0 var(--ds-space-2);
}
.rte-content :deep(.ProseMirror h1) {
  font-size: 20px;
}
.rte-content :deep(.ProseMirror h2) {
  font-size: 16px;
}
.rte-content :deep(.ProseMirror h3) {
  font-size: 14px;
}
.rte-content :deep(.ProseMirror ul),
.rte-content :deep(.ProseMirror ol) {
  margin: 0 0 var(--ds-space-2);
  padding-left: 22px;
}
.rte-content :deep(.ProseMirror ul[data-type='taskList']) {
  list-style: none;
  padding-left: 4px;
}
.rte-content :deep(.ProseMirror li[data-type='taskItem']) {
  display: flex;
  align-items: baseline;
  gap: 6px;
}
.rte-content :deep(.ProseMirror li[data-type='taskItem'] > label) {
  flex-shrink: 0;
}
.rte-content :deep(.ProseMirror li[data-type='taskItem'] input[type='checkbox']) {
  accent-color: var(--ds-color-accent);
}
.rte-content :deep(.ProseMirror li[data-type='taskItem'][data-checked='true'] > div) {
  text-decoration: line-through;
  color: var(--ds-color-text-muted, var(--ds-color-text-secondary));
}
.rte-content :deep(.ProseMirror blockquote) {
  margin: 0 0 var(--ds-space-2);
  padding-left: var(--ds-space-3);
  border-left: 3px solid var(--ds-color-border-strong);
  color: var(--ds-color-text-secondary);
}
.rte-content :deep(.ProseMirror pre) {
  margin: 0 0 var(--ds-space-2);
  padding: var(--ds-space-2) var(--ds-space-3);
  background: var(--ds-input-background);
  border: 1px solid var(--ds-color-border);
  border-radius: var(--ds-radius-sm);
  font-family: ui-monospace, monospace;
  font-size: 13px;
  overflow-x: auto;
}
.rte-content :deep(.ProseMirror a) {
  color: var(--ds-color-accent);
  text-decoration: underline;
}
.rte-content :deep(.ProseMirror mark) {
  background: rgba(250, 204, 21, 0.35);
  color: inherit;
  border-radius: 2px;
}
.rte-content :deep(.ProseMirror img) {
  max-width: 100%;
  height: auto;
  border-radius: var(--ds-radius-sm);
}
/* Tiptap ResizableNodeView: default handles are unstyled 0x0 divs —
   give them size + cursor so they are visible and draggable. */
.rte-content :deep([data-resize-container]) {
  display: inline-flex;
  max-width: 100%;
}
.rte-content :deep([data-resize-wrapper]) {
  display: inline-block;
  max-width: 100%;
}
.rte-content :deep([data-resize-handle]) {
  width: 12px;
  height: 12px;
  background: var(--ds-color-accent);
  border: 2px solid #fff;
  border-radius: 50%;
  box-shadow: 0 1px 4px rgba(0, 0, 0, 0.4);
  cursor: nwse-resize;
  z-index: 5;
}
.rte-content :deep([data-resize-handle='top-left']) {
  top: -6px;
  left: -6px;
  cursor: nwse-resize;
}
.rte-content :deep([data-resize-handle='top-right']) {
  top: -6px;
  right: -6px;
  cursor: nesw-resize;
}
.rte-content :deep([data-resize-handle='bottom-left']) {
  bottom: -6px;
  left: -6px;
  cursor: nesw-resize;
}
.rte-content :deep([data-resize-handle='bottom-right']) {
  bottom: -6px;
  right: -6px;
  cursor: nwse-resize;
}
.rte-content :deep(.ProseMirror table) {
  border-collapse: collapse;
  width: 100%;
  margin: 0 0 var(--ds-space-2);
  table-layout: fixed;
  overflow: hidden;
}
.rte-content :deep(.ProseMirror .tableWrapper) {
  overflow-x: auto;
  margin: 0 0 var(--ds-space-2);
}
.rte-content :deep(.ProseMirror .selectedCell:after) {
  content: '';
  position: absolute;
  inset: 0;
  background: rgb(103 232 249 / .14);
  pointer-events: none;
  border: 1px solid var(--ds-color-accent);
}
.rte-content :deep(.ProseMirror th),
.rte-content :deep(.ProseMirror td) {
  border: 1px solid var(--ds-color-border);
  padding: 4px 8px;
  vertical-align: top;
  min-width: 24px;
}
.rte-content :deep(.ProseMirror th) {
  background: var(--ds-color-bg-secondary, rgba(255, 255, 255, 0.04));
  font-weight: 600;
}
.rte-content :deep(.ProseMirror .is-editor-empty:first-child::before) {
  content: attr(data-placeholder);
  float: left;
  color: var(--ds-color-text-muted, var(--ds-color-text-secondary));
  pointer-events: none;
  height: 0;
}
</style>
