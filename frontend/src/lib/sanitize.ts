// HTML sanitizer for skill descriptions (rich text from Tiptap).
// Whitelist approach: only allowed tags/attributes survive; everything else
// is dropped. Used before v-html rendering (Detail Popup) and for plain-text
// extraction (grid column, char counting).

const ALLOWED_TAGS = new Set([
  'P', 'BR', 'STRONG', 'B', 'EM', 'I', 'U', 'S', 'DEL', 'SUB', 'SUP', 'MARK',
  'UL', 'OL', 'LI', 'H1', 'H2', 'H3', 'H4', 'H5', 'H6',
  'A', 'BLOCKQUOTE', 'CODE', 'PRE', 'DIV', 'SPAN', 'HR',
  'TABLE', 'THEAD', 'TBODY', 'TR', 'TH', 'TD', 'COLGROUP', 'COL',
  'IMG', 'INPUT', 'LABEL',
])

const ALIGN_RE = /^text-align:\s*(left|center|right|justify);?$/i

export function sanitizeHtml(html: string): string {
  if (!html) return ''
  const doc = new DOMParser().parseFromString(html, 'text/html')
  sanitizeNode(doc.body)
  return doc.body.innerHTML
}

function sanitizeNode(node: Node): void {
  const children = Array.from(node.childNodes)
  for (const child of children) {
    if (child.nodeType === Node.ELEMENT_NODE) {
      const el = child as HTMLElement
      const tag = el.tagName
      if (!ALLOWED_TAGS.has(tag)) {
        // unwrap: keep children, drop the element itself
        sanitizeNode(el)
        el.replaceWith(...el.childNodes)
        continue
      }
      sanitizeNode(el)
      // capture needed values BEFORE stripping, then re-apply safe ones
      const attrs: Record<string, string> = {}
      for (const a of ['href', 'src', 'alt', 'width', 'height', 'type', 'data-type', 'data-checked', 'colspan', 'rowspan', 'style']) {
        const v = el.getAttribute(a)
        if (v != null) attrs[a] = v
      }
      for (const attr of Array.from(el.attributes)) el.removeAttribute(attr.name)
      if (tag === 'A') {
        const href = attrs.href ?? ''
        if (/^https?:\/\//i.test(href)) {
          el.setAttribute('href', href)
          el.setAttribute('target', '_blank')
          el.setAttribute('rel', 'noopener')
        }
      } else if (tag === 'IMG') {
        const src = attrs.src ?? ''
        if (/^https?:\/\//i.test(src) || /^data:image\/(jpeg|png|webp);base64,/i.test(src)) {
          el.setAttribute('src', src)
          el.setAttribute('alt', attrs.alt ?? '')
          for (const a of ['width', 'height']) {
            const v = attrs[a]
            if (v && /^\d+$/.test(v)) el.setAttribute(a, v)
          }
        }
      } else if (tag === 'INPUT') {
        // task list checkbox: only type="checkbox" survives; checked state
        // is carried by the parent li[data-checked] (input.checked is stripped)
        if (attrs.type === 'checkbox') {
          el.setAttribute('type', 'checkbox')
          const li = el.closest('li')
          if (li && li.getAttribute('data-checked') === 'true') el.setAttribute('checked', 'checked')
          el.setAttribute('disabled', 'disabled')
        } else {
          el.remove()
          continue
        }
      } else if (tag === 'LABEL') {
        // task list label: keep as-is (no attributes needed)
      } else if (tag === 'LI') {
        // task items: keep data-type + data-checked so checked state renders correctly
        if (attrs['data-type'] === 'taskItem') {
          el.setAttribute('data-type', 'taskItem')
          if (attrs['data-checked'] === 'true') el.setAttribute('data-checked', 'true')
        }
        const style = attrs.style ?? ''
        if (ALIGN_RE.test(style.trim())) el.setAttribute('style', style.trim())
      } else if (tag === 'COL') {
        const w = attrs.width
        if (w && /^\d+$/.test(w)) el.setAttribute('width', w)
      } else if (tag === 'TD' || tag === 'TH') {
        for (const a of ['colspan', 'rowspan']) {
          const v = attrs[a]
          if (v && /^\d+$/.test(v)) el.setAttribute(a, v)
        }
      } else if (tag === 'P' || tag === 'DIV' || tag === 'H1' || tag === 'H2' || tag === 'H3' || tag === 'H4' || tag === 'H5' || tag === 'H6') {
        const style = attrs.style ?? ''
        if (ALIGN_RE.test(style.trim())) el.setAttribute('style', style.trim())
      }
    } else if (child.nodeType === Node.COMMENT_NODE) {
      child.remove()
    }
  }
}

export function stripHtml(html: string): string {
  if (!html) return ''
  const doc = new DOMParser().parseFromString(html, 'text/html')
  // drop images (incl. base64 data URIs) so they don't count toward the char limit
  for (const img of Array.from(doc.body.querySelectorAll('img'))) img.remove()
  return doc.body.textContent ?? ''
}
