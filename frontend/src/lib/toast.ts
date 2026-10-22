export interface ToastOptions {
  title: string
  message?: string
  tone?: 'success' | 'info' | 'warning' | 'danger'
  duration?: number
}

let pushFn: ((options: ToastOptions) => void) | null = null

/** Called once from App.vue, wired to the PrismToast instance. */
export function registerToast(fn: (options: ToastOptions) => void): void {
  pushFn = fn
}

export function toast(options: ToastOptions): void {
  pushFn?.(options)
}

export function toastSuccess(message: string): void {
  toast({ title: 'Success', message, tone: 'success' })
}

export function toastError(message: string): void {
  toast({ title: 'Error', message, tone: 'danger' })
}
