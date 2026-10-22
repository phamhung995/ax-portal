<script>
import { h, nextTick, ref } from 'vue'

export default {
  name: 'PrismTree',
  props: {
    items: { type: Array, default: () => [] }, // [{ id, label, children?, icon?, meta? }]
    label: { type: String, default: 'Cây phân cấp' },
    defaultExpanded: { type: Array, default: () => [] },
  },
  emits: ['select'],
  setup(props, { emit }) {
    const expanded = ref(new Set(props.defaultExpanded))
    const selectedId = ref(null)
    function toggle(id) {
      const next = new Set(expanded.value)
      next.has(id) ? next.delete(id) : next.add(id)
      expanded.value = next
    }
    // Duyệt cây theo thứ tự hiển thị (chỉ node đang mở)
    function visibleNodes(nodes, out = []) {
      for (const node of nodes) {
        out.push(node)
        const hasChildren = Array.isArray(node.children) && node.children.length
        if (hasChildren && expanded.value.has(node.id)) visibleNodes(node.children, out)
      }
      return out
    }
    function focusNode(id) {
      const safe = String(id).replace(/["\\]/g, '\\$&')
      nextTick(() => document.querySelector(`.p-tree [data-node-id="${safe}"]`)?.focus())
    }
    function onKeydown(event, node) {
      const list = visibleNodes(props.items)
      const idx = list.findIndex(n => n.id === node.id)
      const hasChildren = Array.isArray(node.children) && node.children.length
      const isOpen = expanded.value.has(node.id)
      let target = null
      switch (event.key) {
        case 'ArrowDown': target = list[idx + 1]; break
        case 'ArrowUp': target = list[idx - 1]; break
        case 'ArrowRight':
          if (hasChildren && !isOpen) toggle(node.id)
          else if (hasChildren) target = list[idx + 1]
          break
        case 'ArrowLeft':
          if (hasChildren && isOpen) toggle(node.id)
          else {
            // lên node cha gần nhất
            target = findParent(props.items, node.id)
          }
          break
        case 'Home': target = list[0]; break
        case 'End': target = list[list.length - 1]; break
        case 'Enter':
        case ' ':
          event.preventDefault()
          selectedId.value = node.id
          emit('select', node)
          return
        default: return
      }
      if (event.key !== 'Enter' && event.key !== ' ') event.preventDefault()
      if (target && target.id != null) { selectedId.value = target.id; focusNode(target.id) }
    }
    function findParent(nodes, id, parent = null) {
      for (const node of nodes) {
        if (node.id === id) return parent
        const found = findParent(node.children ?? [], id, node)
        if (found !== undefined) return found
      }
      return undefined
    }
    function renderNodes(nodes, depth) {
      return nodes.map((node, i) => {
        const hasChildren = Array.isArray(node.children) && node.children.length
        const isOpen = expanded.value.has(node.id)
        // Một nút treeitem duy nhất cho mỗi node (chuẩn APG): chevron là phần trang trí bên trong,
        // aria-expanded nằm trên treeitem — không có button thứ hai làm con của role=tree.
        return h('li', { key: node.id ?? i, role: 'none' }, [
          h('div', { class: 'p-tree-row', style: { paddingLeft: `${depth * 18 + 8}px` } }, [
            h('span', {
              class: hasChildren ? 'p-tree-toggle' : 'p-tree-toggle p-tree-toggle--leaf',
              'aria-hidden': 'true',
              onClick: () => { if (hasChildren) toggle(node.id) },
            }, [
              h('svg', { width: 12, height: 12, viewBox: '0 0 12 12', fill: 'none' }, [
                h('path', { d: hasChildren && isOpen ? 'm3 4.5 3 3 3-3' : 'm4.5 3 3 3-3 3', stroke: 'currentColor', 'stroke-width': '1.5', 'stroke-linecap': 'round', 'stroke-linejoin': 'round' }),
              ]),
            ]),
            h('button', {
              type: 'button',
              class: 'p-tree-node',
              role: 'treeitem',
              'data-node-id': node.id,
              'aria-expanded': hasChildren ? isOpen : undefined,
              'aria-level': depth + 1,
              'aria-selected': selectedId.value === node.id,
              onClick: () => { selectedId.value = node.id; if (hasChildren) toggle(node.id); emit('select', node) },
              onKeydown: e => onKeydown(e, node),
            }, [
              h('span', { class: 'p-tree-label' }, node.label),
              node.meta ? h('span', { class: 'p-tree-meta' }, node.meta) : null,
            ]),
          ]),
          hasChildren && isOpen
            ? h('ul', { role: 'group' }, renderNodes(node.children, depth + 1))
            : null,
        ])
      })
    }
    return () => h('div', { class: 'p-tree' }, [
      h('ul', { role: 'tree', 'aria-label': props.label }, renderNodes(props.items, 0)),
    ])
  },
}
</script>
