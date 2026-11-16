<script setup lang="ts">
import { computed, onBeforeUnmount, onMounted, ref, useId, watch } from 'vue'
import { useEditor, EditorContent, VueRenderer } from '@tiptap/vue-3'
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
import Mention from '@tiptap/extension-mention'
import MentionSuggestionList from '@/components/MentionSuggestionList.vue'
import PrismFormField from '@/design-system/components/PrismFormField.vue'
import { useI18nStore } from '@/stores/i18n'

const { t } = useI18nStore()

export interface MentionSuggestionItem {
  id: number
  name: string
}

const props = defineProps<{
  modelValue: string
  label?: string
  placeholder?: string
  rows?: number
  maxLength?: number
  disabled?: boolean
  required?: boolean
  mentionEnabled?: boolean
  mentionSuggestion?: (query: string, signal?: AbortSignal) => Promise<MentionSuggestionItem[]>
}>()
const emit = defineEmits<{ 'update:modelValue': [value: string] }>()

const generatedId = useId()

// Mention autocomplete: true while a suggestion query is in flight (drives
// the "Searching…" row in the dropdown). Monotonic request id so a stale
// response (superseded by a newer query) does not clear the flag while the
// newer query is still in flight.
const mentionLoading = ref(false)
let mentionReqId = 0

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
    // @mention autocomplete — only active where the parent opts in
    // (mention-enabled + mention-suggestion, e.g. AI News comments).
    // The mention renders as <span data-type="mention" data-user-id
    // data-user-name> which the backend parses for notifications.
    ...(props.mentionEnabled
      ? [
          // Extra `userId` attribute (via extend) so data-user-id survives
          // Tiptap's re-parse of stored comment HTML — the default node only
          // parses data-id / data-label, so without this, editing a comment
          // that contains a mention would drop the id and the backend could
          // not notify on resubmit.
          Mention.extend({
            addAttributes() {
              return {
                ...this.parent?.(),
                userId: {
                  default: null,
                  parseHTML: (element: HTMLElement) => element.getAttribute('data-user-id'),
                  renderHTML: (attributes: Record<string, unknown>) =>
                    attributes.userId ? { 'data-user-id': String(attributes.userId) } : {},
                },
              }
            },
          }).configure({
            HTMLAttributes: { class: 'mention' },
            // Keep data-type="mention" — the backend parses
            // span[data-type=mention][data-user-id] for notifications.
            renderHTML: ({ options, node }) => [
              'span',
              {
                'data-type': 'mention',
                ...options.HTMLAttributes,
                'data-user-id': (node.attrs as { userId?: string | null }).userId ?? node.attrs.id ?? '',
                'data-user-name': node.attrs.label ?? '',
              },
              `@${node.attrs.label ?? node.attrs.id ?? ''}`,
            ],
            suggestion: {
              char: '@',
              allowSpaces: true,
              debounce: 250,
              // The dropdown mounts into document.body by default — but a
              // modal <dialog> sits above body content in the top layer,
              // so when the editor renders inside one (AI News comment
              // sidebar) a body-mounted popup is hidden behind the drawer.
              // The popup is instead pre-attached to the <dialog> in
              // onStart (see below). The dialog is positioned (top layer),
              // so it is the popup's offsetParent and the default 'absolute'
              // strategy resolves correctly against it. Do NOT use
              // 'fixed' here: the dialog's backdrop-filter makes it the
              // containing block for fixed descendants, so fixed
              // (viewport-relative) coordinates would be offset by the
              // dialog's position. The popup is a sibling of
              // .p-dialog-body, so the body's overflow-y:auto doesn't
              // clip it.
              items: ({ query, signal }) => {
                const reqId = ++mentionReqId
                mentionLoading.value = true
                return (props.mentionSuggestion
                  ? props.mentionSuggestion(query, signal)
                  : Promise.resolve([])
                )
                  .then((users) => users.map((u) => ({ id: String(u.id), label: u.name })))
                  .finally(() => {
                    // Only clear the flag if this is still the latest query —
                    // an aborted/superseded response must not hide the
                    // "Searching…" state of the newer one.
                    if (reqId === mentionReqId) mentionLoading.value = false
                  })
              },
              render: () => {
                let component: VueRenderer | null = null
                let unmount: (() => void) | null = null
                // The dropdown's item shape (MentionSuggestionList): the
                // suggestion's command/items are MentionNodeAttrs-shaped
                // (id/label as strings), not the API's MentionUser (id: number).
                type MentionListItem = { id: string; label: string }
                let items: MentionListItem[] = []
                let command: (item: MentionListItem) => void = () => {}
                // Keyboard selection index (renderer-level — the dropdown is
                // mounted OUTSIDE the editor DOM, so keydown never reaches
                // it; the Suggestion plugin routes keys here via the
                // renderer's onKeyDown hook).
                let selected = 0
                const setSelected = (i: number) => {
                  selected = i
                  component?.updateProps({ selected: i })
                }
                return {
                  onStart: (props) => {
                    items = props.items
                    command = props.command
                    selected = 0
                    component = new VueRenderer(MentionSuggestionList, {
                      props: {
                        items: props.items,
                        command: props.command,
                        loading: mentionLoading.value,
                        selected: 0,
                        onHover: setSelected,
                      },
                      editor: props.editor,
                    })
                    if (component.element instanceof HTMLElement) {
                      // Pre-attach the element to the nearest <dialog> when
                      // the editor renders inside one (AI News comment
                      // sidebar) — the plugin's mount() skips re-appending
                      // already-connected elements, so it keeps positioning
                      // this element here instead of document.body (which
                      // a modal <dialog> covers in the top layer).
                      const dialog = props.editor.view.dom.closest('dialog')
                      if (dialog) dialog.appendChild(component.element)
                      unmount = props.mount(component.element)
                    }
                  },
                  onUpdate: (props) => {
                    items = props.items
                    command = props.command
                    if (selected >= items.length) selected = 0
                    component?.updateProps({
                      items: props.items,
                      command: props.command,
                      loading: mentionLoading.value,
                      selected,
                      onHover: setSelected,
                    })
                  },
                  // ArrowUp/Down move the selection, Enter/Tab insert the
                  // selected user. Without this hook the dropdown was
                  // click-only: Enter inserted a paragraph and the arrow
                  // keys did nothing (the plugin only forwards keys to the
                  // renderer's onKeyDown).
                  onKeyDown: ({ event }) => {
                    if (items.length === 0) return false
                    if (event.key === 'ArrowDown') {
                      event.preventDefault()
                      setSelected((selected + 1) % items.length)
                      return true
                    }
                    if (event.key === 'ArrowUp') {
                      event.preventDefault()
                      setSelected((selected - 1 + items.length) % items.length)
                      return true
                    }
                    if (event.key === 'Enter' || event.key === 'Tab') {
                      const item = items[selected]
                      if (item) {
                        event.preventDefault()
                        command(item)
                        return true
                      }
                    }
                    return false
                  },
                  onExit: () => {
                    unmount?.()
                    component?.destroy()
                    component = null
                    unmount = null
                    items = []
                    selected = 0
                  },
                }
              },
            },
          }),
        ]
      : []),
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

// external value changes (e.g. form reset) → sync into editor.
// When the editor is focused, the change came from THIS editor's own
// onUpdate (the model round-trips back) — re-parsing would reset the caret
// on every keystroke. It also guards the twin-instance case (main pane +
// drawer bound to the same model): only the unfocused instance re-parses.
watch(
  () => props.modelValue,
  (val) => {
    const e = editor.value
    if (!e) return
    if (e.isFocused) return
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

/** Focus the editor (exposed for parents — e.g. auto-focus when a reply
    form opens). */
function focus() {
  editor.value?.chain().focus().run()
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
  const url = window.prompt(t('app.rte.linkPrompt'), 'https://')
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
    window.alert(t('app.rte.unsupportedImage'))
    return
  }
  if (file.size > IMAGE_MAX_SIZE) {
    window.alert(t('app.rte.imageTooLarge'))
    return
  }
  let dataUri: string
  try {
    // small images: keep original bytes/format; larger ones: auto-compress
    dataUri = file.size <= IMAGE_WARN_SIZE
      ? await readAsDataUrl(file)
      : await compressImage(file)
  } catch {
    window.alert(t('app.rte.imageProcessError'))
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

// Editor min-height in em. The two template branches used to default to
// different values (4 with a label, 3 without) — a silent drift; both now
// share this single default.
const effectiveRows = computed(() => props.rows ?? 4)

const charCount = computed(() => {
  const e = editor.value
  if (!e) return 0
  return e.getText().replace(/\n$/, '').length
})
const overLimit = computed(() => props.maxLength != null && charCount.value > props.maxLength)
/** Whether the plain-text length exceeds maxLength (exposed so parents can
    disable their Send button — the counter alone is display-only). */
const isOverLimit = computed(() => overLimit.value)

defineExpose({ focus, isOverLimit })

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
  <!-- Single editor body; the wrapper is PrismFormField when a label is
       given, a plain div otherwise. The body is defined ONCE (the two
       branches used to be ~40 lines of duplicated toolbar + bubble menu). -->
  <component
    :is="label ? PrismFormField : 'div'"
    v-bind="label ? { id: generatedId, label, required } : {}"
  >
    <div class="rte">
      <div class="rte-toolbar" role="toolbar" :aria-label="t('app.rte.toolbarAria')">
        <button type="button" class="rte-btn" :class="{ 'is-active': active('bold') }" :disabled="disabled" @click="run('bold')" :title="t('app.rte.bold')"><strong>B</strong></button>
        <button type="button" class="rte-btn" :class="{ 'is-active': active('italic') }" :disabled="disabled" @click="run('italic')" :title="t('app.rte.italic')"><em>I</em></button>
        <button type="button" class="rte-btn" :class="{ 'is-active': active('underline') }" :disabled="disabled" @click="run('underline')" :title="t('app.rte.underline')"><u>U</u></button>
        <button type="button" class="rte-btn" :class="{ 'is-active': active('strike') }" :disabled="disabled" @click="run('strike')" :title="t('app.rte.strikethrough')"><s>S</s></button>
        <button type="button" class="rte-btn" :class="{ 'is-active': active('highlight') }" :disabled="disabled" @click="run('highlight')" :title="t('app.rte.highlight')">🖍</button>
        <button type="button" class="rte-btn" :class="{ 'is-active': active('subscript') }" :disabled="disabled" @click="run('subscript')" :title="t('app.rte.subscript')">X<sub>2</sub></button>
        <button type="button" class="rte-btn" :class="{ 'is-active': active('superscript') }" :disabled="disabled" @click="run('superscript')" :title="t('app.rte.superscript')">X<sup>2</sup></button>
        <span class="rte-sep" aria-hidden="true"></span>
        <button type="button" class="rte-btn" :class="{ 'is-active': isH1 }" :disabled="disabled" @click="setHeading(1)" :title="t('app.rte.heading1')">H1</button>
        <button type="button" class="rte-btn" :class="{ 'is-active': isH2 }" :disabled="disabled" @click="setHeading(2)" :title="t('app.rte.heading2')">H2</button>
        <button type="button" class="rte-btn" :class="{ 'is-active': isH3 }" :disabled="disabled" @click="setHeading(3)" :title="t('app.rte.heading3')">H3</button>
        <button type="button" class="rte-btn" :disabled="disabled" @click="setHeading(0)" :title="t('app.rte.paragraph')">¶</button>
        <span class="rte-sep" aria-hidden="true"></span>
        <button type="button" class="rte-btn" :class="{ 'is-active': isAlign('left') }" :disabled="disabled" @click="setAlign('left')" :title="t('app.rte.alignLeft')">⇤</button>
        <button type="button" class="rte-btn" :class="{ 'is-active': isAlign('center') }" :disabled="disabled" @click="setAlign('center')" :title="t('app.rte.alignCenter')">⇹</button>
        <button type="button" class="rte-btn" :class="{ 'is-active': isAlign('right') }" :disabled="disabled" @click="setAlign('right')" :title="t('app.rte.alignRight')">⇥</button>
        <span class="rte-sep" aria-hidden="true"></span>
        <button type="button" class="rte-btn" :class="{ 'is-active': active('bulletList') }" :disabled="disabled" @click="run('bulletList')" :title="t('app.rte.bulletList')">•≡</button>
        <button type="button" class="rte-btn" :class="{ 'is-active': active('orderedList') }" :disabled="disabled" @click="run('orderedList')" :title="t('app.rte.numberedList')">1≡</button>
        <button type="button" class="rte-btn" :class="{ 'is-active': active('taskList') }" :disabled="disabled" @click="run('taskList')" :title="t('app.rte.taskList')">☑</button>
        <button type="button" class="rte-btn" :class="{ 'is-active': active('blockquote') }" :disabled="disabled" @click="run('blockquote')" :title="t('app.rte.quote')">❝</button>
        <button type="button" class="rte-btn" :class="{ 'is-active': active('codeBlock') }" :disabled="disabled" @click="run('codeBlock')" :title="t('app.rte.codeBlock')">&lt;/&gt;</button>
        <span class="rte-sep" aria-hidden="true"></span>
        <button type="button" class="rte-btn" :class="{ 'is-active': isTable }" :disabled="disabled" @click="insertTable()" :title="t('app.rte.insertTable')">▦</button>
        <button type="button" class="rte-btn" :disabled="disabled || !isTable" @click="addRowAfter()" :title="t('app.rte.addRowBelow')">+☰</button>
        <button type="button" class="rte-btn" :disabled="disabled || !isTable" @click="deleteRow()" :title="t('app.rte.deleteRow')">☰✕</button>
        <button type="button" class="rte-btn" :disabled="disabled || !isTable" @click="addColumnAfter()" :title="t('app.rte.addColumnRight')">+▥</button>
        <button type="button" class="rte-btn" :disabled="disabled || !isTable" @click="deleteColumn()" :title="t('app.rte.deleteColumn')">▥✕</button>
        <button type="button" class="rte-btn" :disabled="disabled || !isTable" @click="deleteTable()" :title="t('app.rte.deleteTable')">▦✕</button>
        <span class="rte-sep" aria-hidden="true"></span>
        <button type="button" class="rte-btn" :class="{ 'is-active': isLink }" :disabled="disabled" @click="setLink()" :title="t('app.rte.link')">🔗</button>
        <button type="button" class="rte-btn" :disabled="disabled" @click="setImage()" :title="t('app.rte.image')">🖼</button>
      </div>
      <input ref="imageInput" type="file" accept=".jpg,.jpeg,.png,.webp" class="rte-file-input" @change="onImagePicked" tabindex="-1" aria-hidden="true" />
      <div class="rte-content-wrap">
        <EditorContent :editor="editor" class="rte-content" :style="{ minHeight: `${effectiveRows * 1.6}em` }" />
        <div ref="bubbleEl" class="rte-bubble" v-show="bubbleVisible">
          <button type="button" class="rte-btn" :class="{ 'is-active': active('bold') }" @click="run('bold')" :title="t('app.rte.bold')"><strong>B</strong></button>
          <button type="button" class="rte-btn" :class="{ 'is-active': active('italic') }" @click="run('italic')" :title="t('app.rte.italic')"><em>I</em></button>
          <button type="button" class="rte-btn" :class="{ 'is-active': active('underline') }" @click="run('underline')" :title="t('app.rte.underline')"><u>U</u></button>
          <button type="button" class="rte-btn" :class="{ 'is-active': active('highlight') }" @click="run('highlight')" :title="t('app.rte.highlight')">🖍</button>
          <button type="button" class="rte-btn" :class="{ 'is-active': isLink }" @click="setLink()" :title="t('app.rte.link')">🔗</button>
        </div>
      </div>
      <div v-if="maxLength != null" class="rte-count" :class="{ 'is-over': overLimit }">{{ t('app.rte.charCounter', { count: charCount, max: maxLength }) }}</div>
    </div>
  </component>
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
.rte-count {
  padding: 2px var(--ds-space-3) 6px;
  text-align: right;
  font: 11px var(--ds-font-mono);
  color: var(--ds-color-text-muted);
}
.rte-count.is-over {
  color: var(--ds-color-danger);
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
.rte-content :deep(.ProseMirror .mention) {
  color: var(--ds-color-accent);
  background: rgb(94 106 210 / .12);
  border-radius: 3px;
  padding: 0 3px;
  font-weight: 500;
  white-space: nowrap;
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
  background: rgb(94 106 210 / .14);
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
