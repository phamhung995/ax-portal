import { test, expect } from '@playwright/test'
import AxeBuilder from '@axe-core/playwright'

test('catalog interactions, dialog validation, focus and project creation', async ({ page }) => {
  const errors = []
  page.on('pageerror', error => errors.push(error.message))
  await page.goto('/')
  await expect(page.getByRole('heading', { name: /Build beyond/ })).toBeVisible()
  await page.getByRole('button', { name: 'Khám phá components' }).click()
  await expect(page.locator('#library')).toBeInViewport()
  const trigger = page.getByRole('button', { name: 'Mở dialog', exact: true })
  await trigger.click()
  const dialog = page.getByRole('dialog')
  await expect(dialog).toBeVisible()
  await expect(dialog.getByLabel('Tên không gian')).toBeFocused()
  await dialog.getByRole('button', { name: 'Tạo không gian', exact: true }).click()
  await expect(dialog.getByText('Vui lòng nhập tên không gian.')).toBeVisible()
  await dialog.getByLabel('Tên không gian').fill('Aurora test')
  await dialog.getByRole('button', { name: 'Tạo không gian', exact: true }).click()
  await expect(dialog).not.toBeVisible()
  await expect(trigger).toBeFocused()
  await expect(page.getByRole('cell', { name: 'Aurora test', exact: true })).toBeVisible()
  await trigger.click()
  await page.keyboard.press('Shift+Tab')
  expect(await page.evaluate(() => !!document.activeElement.closest('dialog'))).toBe(true)
  await page.keyboard.press('Escape')
  await expect(dialog).not.toBeVisible()
  await expect(trigger).toBeFocused()
  expect(errors).toEqual([])
})

test('search, filtering, snippets, tabs and pagination', async ({ page }) => {
  await page.goto('/')
  await page.getByRole('textbox', { name: 'Tìm component' }).fill('PrismInput')
  await expect(page.locator('.specimen')).toHaveCount(1)
  await page.getByRole('button', { name: 'Xem mã Inputs & select' }).click()
  await expect(page.locator('.specimen pre')).toContainText('PrismInput')
  await page.getByRole('textbox', { name: 'Tìm component' }).fill('nothing-matches')
  await expect(page.getByText('Không tìm thấy component')).toBeVisible()
  await page.getByRole('button', { name: 'Xóa bộ lọc' }).click()
  await expect(page.locator('.specimen')).toHaveCount(43)
  await page.getByRole('tab', { name: 'Tổng quan' }).focus()
  await page.keyboard.press('ArrowRight')
  await expect(page.getByRole('tab', { name: 'Chi tiết' })).toHaveAttribute('aria-selected', 'true')
  await page.getByRole('button', { name: 'Trang sau' }).click()
  await expect(page.getByRole('cell', { name: 'Lunar analytics' })).toBeVisible()
  await expect(page.getByRole('button', { name: 'Trang sau' })).toBeDisabled()
})

test('glass controls and CSS export work', async ({ page }) => {
  await page.goto('/#playground')
  const slider = page.getByRole('slider', { name: 'Độ che của mặt kính' })
  await slider.fill('30')
  expect(await page.evaluate(() => getComputedStyle(document.documentElement).getPropertyValue('--ds-surface-alpha').trim())).toBe('0.3')
  const downloadPromise = page.waitForEvent('download')
  await page.getByRole('button', { name: 'Xuất CSS tokens' }).click()
  expect((await downloadPromise).suggestedFilename()).toBe('prism-overrides.css')
  await page.getByRole('button', { name: 'Đặt lại mặc định' }).click()
  await expect(slider).toHaveValue('10')
  await page.screenshot({ path: 'test-results/glass-lab-desktop.png', fullPage: true })
  await page.getByRole('button', { name: 'Thử popup xuyên thấu' }).click()
  await page.screenshot({ path: 'test-results/glass-dialog.png' })
  expect(await page.getByRole('dialog').evaluate(el => getComputedStyle(el).backgroundImage)).toContain('gradient')
})

test('desktop accessibility and full-page visual', async ({ page }) => {
  test.setTimeout(120000)
  await page.goto('/')
  const results = await new AxeBuilder({ page }).withTags(['wcag2a', 'wcag2aa', 'wcag21aa']).analyze()
  expect(results.violations).toEqual([])
  await page.screenshot({ path: 'test-results/catalog-desktop.png', fullPage: true })
  await page.getByRole('button', { name: 'Mở dialog', exact: true }).click()
  const dialogResults = await new AxeBuilder({ page }).withTags(['wcag2a', 'wcag2aa', 'wcag21aa']).analyze()
  expect(dialogResults.violations).toEqual([])
})

test('floating panels (combobox, dropdown, color picker, popover) open fixed and interactable', async ({ page }) => {
  await page.goto('/#components')
  const assertFixedPanel = async (panel) => {
    await expect(panel).toBeVisible()
    expect(await panel.evaluate(el => getComputedStyle(el).position)).toBe('fixed')
    const box = await panel.boundingBox()
    expect(box).not.toBeNull()
    expect(box.y).toBeGreaterThanOrEqual(0)
    expect(box.y + box.height).toBeLessThanOrEqual(page.viewportSize().height)
  }

  // Combobox: click input → panel fixed trong viewport, chọn option
  const combo = page.locator('#combobox .p-combobox').first()
  await combo.locator('input').click()
  const comboPanel = page.locator('.p-combobox-panel')
  await assertFixedPanel(comboPanel)
  await comboPanel.locator('li[role="option"]', { hasText: 'Growth' }).click()
  expect(await combo.locator('input').inputValue()).toBe('Growth')

  // Dropdown menu: click trigger → panel fixed, chọn item
  const dd = page.locator('#dropdown-menu .p-dropdown-trigger')
  await dd.click()
  const ddPanel = page.locator('.p-dropdown-panel')
  await assertFixedPanel(ddPanel)
  await ddPanel.locator('[role="menuitem"]', { hasText: 'Nhân bản' }).click()
  await expect(page.locator('#dropdown-menu code', { hasText: 'duplicate' })).toBeVisible()

  // Color picker: click trigger → panel fixed, chọn preset
  const cp = page.locator('#color-picker .p-colorpicker-trigger')
  await cp.click()
  const cpPanel = page.locator('.p-colorpicker-panel')
  await assertFixedPanel(cpPanel)
  await cpPanel.locator('.p-colorpicker-preset').nth(1).click()
  expect(await cp.locator('.p-colorpicker-hex').textContent()).toBe('#a58aef')

  // Popover: click trigger → panel fixed, đóng bằng Escape
  const pop = page.locator('#popover .p-popover')
  await pop.click()
  const popPanel = page.locator('.p-popover-panel')
  await assertFixedPanel(popPanel)
  await page.keyboard.press('Escape')
  await expect(popPanel).not.toBeVisible()
})

test('number input: typing, stepper and keyboard all work', async ({ page }) => {
  await page.goto('/#components')
  const num = page.locator('#number-input .p-number').first()
  const input = num.locator('input')
  const up = num.locator('.p-number-step').first()
  const down = num.locator('.p-number-step').last()

  // Stepper tăng/giảm (min 0, max 10, bắt đầu 3)
  await up.click()
  expect(await input.inputValue()).toBe('4')
  await down.click()
  expect(await input.inputValue()).toBe('3')

  // Gõ trực tiếp: giá trị trung gian giữ nguyên, blur mới clamp
  await input.fill('99')
  expect(await input.inputValue()).toBe('99')
  await input.blur()
  expect(await input.inputValue()).toBe('10')

  // Gõ số thập phân dở dang không bị phá giữa chừng
  await input.click()
  await input.fill('4.')
  expect(await input.inputValue()).toBe('4.')
  await input.blur()
  expect(await input.inputValue()).toBe('4')

  // Xóa hết rồi gõ lại được
  await input.click()
  await input.fill('')
  await input.type('7')
  expect(await input.inputValue()).toBe('7')
  await input.blur()
  expect(await input.inputValue()).toBe('7')

  // Phím ↑/↓ tăng giảm
  await input.click()
  await input.press('ArrowUp')
  expect(await input.inputValue()).toBe('8')
  await input.press('ArrowDown')
  await input.press('ArrowDown')
  expect(await input.inputValue()).toBe('6')
})

test('breadcrumbs navigate event works and divider label is centered', async ({ page }) => {
  await page.goto('/#components')
  const bc = page.locator('#breadcrumbs')
  await bc.scrollIntoViewIfNeeded()
  // Bấm link → emit navigate cập nhật note (trước đây lỗi $event trong inline handler)
  await bc.locator('.p-breadcrumb-link', { hasText: 'Dự án' }).click()
  await expect(bc.locator('.inline-note')).toContainText('Đã bấm: Dự án (index 1)')
  // Item cuối không phải link, có aria-current
  await expect(bc.locator('.p-breadcrumb-current')).toHaveAttribute('aria-current', 'page')
  // Divider có nhãn: label nằm gọn trong box divider (2 đoạn line ::before/::after 2 bên)
  const dv = page.locator('#divider .p-divider.has-label').first()
  await dv.scrollIntoViewIfNeeded()
  const geo = await dv.evaluate(el => {
    const label = el.querySelector('.p-divider-label').getBoundingClientRect()
    const r = el.getBoundingClientRect()
    return { labelX: label.x, labelRight: label.right, lineStart: r.x, lineEnd: r.right }
  })
  expect(geo.labelX).toBeGreaterThan(geo.lineStart)
  expect(geo.labelRight).toBeLessThan(geo.lineEnd)
})

test('mobile navigation, responsive layout and reduced motion', async ({ page }) => {
  test.setTimeout(120000)
  await page.setViewportSize({ width: 390, height: 844 })
  await page.emulateMedia({ reducedMotion: 'reduce' })
  await page.goto('/')
  expect(await page.evaluate(() => document.documentElement.scrollWidth <= innerWidth)).toBe(true)
  await page.screenshot({ path: 'test-results/catalog-mobile.png', fullPage: true })
  await page.getByRole('button', { name: 'Mở điều hướng' }).click()
  await page.getByRole('link', { name: 'Foundations' }).click()
  await expect(page.getByRole('heading', { name: 'Built on light.' })).toBeVisible()
  expect(await page.evaluate(() => document.documentElement.scrollWidth <= innerWidth)).toBe(true)
  await page.getByRole('button', { name: 'Tùy chỉnh giao diện' }).click()
  await expect(page.getByRole('dialog')).toBeVisible()
  await page.getByRole('button', { name: 'Hoàn tất' }).click()
  await expect(page.getByRole('dialog')).not.toBeVisible()
  await page.goto('/#components')
  expect(await page.locator('.p-spinner').first().evaluate(el => getComputedStyle(el).animationName)).toBe('none')
  const results = await new AxeBuilder({ page }).withTags(['wcag2a', 'wcag2aa', 'wcag21aa']).analyze()
  expect(results.violations).toEqual([])
})
