import { nextTick, onBeforeUnmount, onMounted, ref } from 'vue'

/**
 * Trạng thái chung cho panel nổi (dropdown) trong showcase:
 * - Teleport ra `body` + `position: fixed` (z-index 85) tính vị trí từ rect của control,
 *   nên không bị che bởi stacking context của `.p-glass`/`backdrop-filter` hay
 *   `overflow` của container (cùng cơ chế `useDatePopup` cho date picker).
 * - Lật lên phía trên control khi gần đáy màn hình, kẹp trong viewport khi gần mép phải.
 * - Đóng khi click ngoài hoặc Escape; click bên trong panel không đóng.
 *
 * options:
 *  - matchWidth (default false): true → panel rộng bằng control (min 200px); false → để CSS tự quyết định.
 *  - align (default 'left'): 'left' = canh trái với control; 'right' = canh phải với control.
 *
 * Khi control nằm trong một `<dialog>` đang mở, panel được teleport vào chính dialog đó
 * (cùng top layer) thay vì ra `body` — nếu teleport ra `body`, panel sẽ bị che bởi backdrop
 * của dialog. Vì `.p-glass` dùng `backdrop-filter`, dialog trở thành containing block của
 * panel: `position: fixed` bên trong dialog được tính theo padding box của dialog (không
 * phải viewport) — cùng cơ chế `useDatePopup` cho date picker.
 */
export function useFloatingPanel(controlRef, { matchWidth = false, align = 'left' } = {}) {
  const open = ref(false)
  const panelRef = ref(null)
  const panelPos = ref({ top: 0, left: 0 })
  const panelTo = ref(document.body)
  const inDialog = ref(false)
  let dialogToggleHandler = null
  let hostDialog = null

  function positionPanel() {
    const control = controlRef.value
    const panel = panelRef.value
    if (!control || !panel) return
    const rect = control.getBoundingClientRect()
    const panelWidth = matchWidth ? Math.max(rect.width, 200) : panel.offsetWidth
    const height = panel.offsetHeight
    const margin = 8
    // Trong dialog: toạ độ theo padding box của dialog (backdrop-filter khiến dialog thành
    // containing block của panel `position: fixed`), cộng thêm offset scroll của dialog.
    // Ngoài trang: toạ độ theo viewport (boxTop/boxLeft = 0, sT/sL = 0).
    let boxTop = 0, boxLeft = 0, visH = window.innerHeight, visW = window.innerWidth, sT = 0, sL = 0
    if (inDialog.value) {
      const d = panelTo.value
      const dr = d.getBoundingClientRect()
      const cs = getComputedStyle(d)
      const bT = parseFloat(cs.borderTopWidth) || 0, bL = parseFloat(cs.borderLeftWidth) || 0
      const bB = parseFloat(cs.borderBottomWidth) || 0, bR = parseFloat(cs.borderRightWidth) || 0
      boxTop = dr.top + bT
      boxLeft = dr.left + bL
      visH = dr.height - bT - bB
      visW = dr.width - bL - bR
      sT = d.scrollTop
      sL = d.scrollLeft
    }
    // Vị trí control trong toạ độ nội dung (content coords) của container.
    const ctrlTop = rect.top - boxTop + sT
    const ctrlBottom = rect.bottom - boxTop + sT
    const ctrlLeft = rect.left - boxLeft + sL
    const ctrlRight = rect.right - boxLeft + sL
    // Khung nhìn thấy được trong toạ độ nội dung.
    const visTop = sT, visBottom = sT + visH, visLeft = sL, visRight = sL + visW
    let top = ctrlBottom + margin
    if (top + height > visBottom - 8 && ctrlTop - height - margin >= visTop + 8) top = ctrlTop - height - margin
    if (top + height > visBottom - 8) top = Math.max(visTop + 8, visBottom - height - 8)
    let left = align === 'right' ? ctrlRight - panelWidth : ctrlLeft
    if (left + panelWidth > visRight - 8) left = Math.max(visLeft + 8, visRight - panelWidth - 8)
    if (left < visLeft + 8) left = visLeft + 8
    panelPos.value = { top, left }
  }
  function onViewportChange() { if (open.value) positionPanel() }
  function detachDialog() {
    if (hostDialog && dialogToggleHandler) hostDialog.removeEventListener('toggle', dialogToggleHandler)
    hostDialog = null
    dialogToggleHandler = null
  }
  function openPanel() {
    open.value = true
    nextTick(() => {
      detachDialog()
      const dialog = controlRef.value?.closest?.('dialog')
      if (dialog && dialog.open) {
        hostDialog = dialog
        panelTo.value = dialog
        inDialog.value = true
        dialogToggleHandler = () => { if (!dialog.open) closePanel() }
        dialog.addEventListener('toggle', dialogToggleHandler)
      } else {
        panelTo.value = document.body
        inDialog.value = false
      }
      positionPanel()
    })
  }
  function closePanel() {
    open.value = false
    detachDialog()
    panelTo.value = document.body
    inDialog.value = false
  }
  function togglePanel() { open.value ? closePanel() : openPanel() }
  function onDocClick(e) {
    if (open.value && !panelRef.value?.contains(e.target) && !controlRef.value?.contains(e.target)) closePanel()
  }
  function onDocKeydown(e) { if (e.key === 'Escape' && open.value) closePanel() }
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
  return { open, panelRef, panelPos, panelTo, inDialog, openPanel, closePanel, togglePanel }
}
