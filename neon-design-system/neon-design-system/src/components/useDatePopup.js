import { nextTick, onBeforeUnmount, onMounted, ref } from 'vue'

/**
 * Trạng thái chung cho popup của các date picker:
 * - Teleport ra `body` (hoặc vào `<dialog>` native đang mở nếu picker nằm trong dialog —
 *   vì dialog nằm ở top-layer nên popup teleport ra body sẽ bị che).
 * - Ngoài trang: `position: fixed` tính từ rect của input; lật lên khi gần đáy màn hình,
 *   kẹp trong viewport khi gần mép phải; recalibrate khi resize/scroll.
 * - Trong dialog: `position: absolute` theo rect của dialog (dialog là containing block
 *   nhờ backdrop-filter của .p-glass), vẫn lật lên khi gần đáy dialog.
 * - Đóng khi click ngoài hoặc Escape.
 */
export function useDatePopup(inputRef, rootRef) {
  const open = ref(false)
  const popupRef = ref(null)
  const popupPos = ref({ top: 0, left: 0 })
  const teleportTarget = ref(document.body)
  const inDialog = ref(false)
  let dialogToggleHandler = null

  function positionPopup() {
    const input = inputRef.value
    const popup = popupRef.value
    if (!input || !popup) return
    const rect = input.getBoundingClientRect()
    const width = popup.offsetWidth
    const height = popup.offsetHeight
    const margin = 6
    // Trong dialog: toạ độ theo padding box của dialog; ngoài trang: theo viewport.
    const box = inDialog.value ? teleportTarget.value.getBoundingClientRect() : { top: 0, left: 0, bottom: window.innerHeight, right: window.innerWidth }
    let top = rect.bottom - box.top + margin
    if (top + height > box.bottom - 8 && rect.top - box.top - height - margin >= 8) top = rect.top - box.top - height - margin
    if (top + height > box.bottom - 8) top = Math.max(8, box.bottom - height - 8)
    let left = rect.left - box.left
    if (left + width > box.right - 8) left = Math.max(8, box.right - width - 8)
    popupPos.value = { top, left }
  }
  function onViewportChange() { if (open.value) positionPopup() }
  function detachDialog() {
    if (dialogToggleHandler && teleportTarget.value !== document.body) teleportTarget.value.removeEventListener('toggle', dialogToggleHandler)
    dialogToggleHandler = null
  }
  function openPopup() {
    open.value = true
    nextTick(() => {
      detachDialog()
      const dialog = inputRef.value?.closest?.('dialog')
      if (dialog && dialog.open) {
        teleportTarget.value = dialog
        inDialog.value = true
        dialogToggleHandler = () => { if (!dialog.open) closePopup(false) }
        dialog.addEventListener('toggle', dialogToggleHandler)
      } else {
        teleportTarget.value = document.body
        inDialog.value = false
      }
      positionPopup()
      inputRef.value?.focus()
    })
  }
  function closePopup(refocus = true) {
    open.value = false
    detachDialog()
    if (refocus) inputRef.value?.focus()
  }
  function onDocClick(e) {
    if (open.value && rootRef.value && !rootRef.value.contains(e.target) && (!popupRef.value || !popupRef.value.contains(e.target))) closePopup(false)
  }
  function onDocKeydown(e) { if (e.key === 'Escape' && open.value) closePopup(false) }
  onMounted(() => {
    document.addEventListener('click', onDocClick)
    document.addEventListener('keydown', onDocKeydown)
    window.addEventListener('resize', onViewportChange)
    window.addEventListener('scroll', onViewportChange, true)
  })
  onBeforeUnmount(() => {
    document.removeEventListener('click', onDocClick)
    document.removeEventListener('keydown', onDocKeydown)
    window.removeEventListener('resize', onViewportChange)
    window.removeEventListener('scroll', onViewportChange, true)
    detachDialog()
  })
  return { open, popupRef, popupPos, teleportTarget, inDialog, openPopup, closePopup }
}
