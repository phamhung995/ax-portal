<script setup lang="ts">
import { computed, onBeforeUnmount, onMounted, reactive, ref } from 'vue'
import PrismButton from '@/design-system/components/PrismButton.vue'
import PrismInput from '@/design-system/components/PrismInput.vue'
import PrismSwitch from '@/design-system/components/PrismSwitch.vue'
import PrismPasswordInput from '@/design-system/components/PrismPasswordInput.vue'
import PrismFileUpload from '@/design-system/components/PrismFileUpload.vue'
import PrismDialog from '@/design-system/components/PrismDialog.vue'
import PrismEmptyState from '@/design-system/components/PrismEmptyState.vue'
import PrismAvatar from '@/design-system/components/PrismAvatar.vue'
import PasswordPolicy from '@/features/auth/components/PasswordPolicy.vue'
import { settingsApi } from '@/features/users/api'
import { toUserError } from '@/lib/api'
import { orgsLabel } from '@/lib/types'
import { toastSuccess, toastError } from '@/lib/toast'
import { useAuthStore } from '@/stores/auth'
import { useI18nStore } from '@/stores/i18n'
import type { MeResponse } from '@/features/users/types'

const auth = useAuthStore()
const i18n = useI18nStore()
const { t } = i18n

const SECTIONS = computed(() => [
  { id: 'profile', label: t('app.settings.profile') },
  { id: 'notifications', label: t('app.settings.notifications') },
  { id: 'password', label: t('app.settings.changePassword') },
] as const)
type SectionId = (typeof SECTIONS.value)[number]['id']

const loading = ref(true)
const loadError = ref('')
const me = ref<MeResponse | null>(null)

const departmentLabel = computed(() => orgsLabel(me.value?.orgs))

const active = ref<SectionId>('profile')
const confirmSwitch = ref(false)
const pendingSection = ref<SectionId>('profile')

// ── Profile ─────────────────────────────────────────────────────────────────
const profile = reactive({ displayName: '', avatar: null as File | null, removeAvatar: false })
const profileSaving = ref(false)
const profileError = ref('')

// Local preview URL for the newly selected avatar file (revoked on change/unmount).
const avatarPreviewUrl = ref('')
function setAvatarFile(file: File | null) {
  profile.avatar = file
  if (avatarPreviewUrl.value) URL.revokeObjectURL(avatarPreviewUrl.value)
  avatarPreviewUrl.value = file ? URL.createObjectURL(file) : ''
}
onBeforeUnmount(() => {
  if (avatarPreviewUrl.value) URL.revokeObjectURL(avatarPreviewUrl.value)
})

const nameCounter = computed(() => t('app.common.charCount', { count: profile.displayName.length, max: 50 }))
const nameCounterWarn = computed(() => profile.displayName.length >= 45)

// ── Notifications ───────────────────────────────────────────────────────────
const notifications = reactive({ emailNotify: true, inappNotify: true })
const notifSaving = ref(false)
const notifError = ref('')

// ── Change Password ─────────────────────────────────────────────────────────
const passwordForm = reactive({ current: '', next: '', confirm: '' })
const passwordSaving = ref(false)
const passwordError = ref('')

const strength = computed(() => {
  const p = passwordForm.next
  if (!p) return 0
  let score = 0
  if (p.length >= 8) score++
  if (/[A-Z]/.test(p)) score++
  if (/[0-9]/.test(p)) score++
  if (/[^A-Za-z0-9]/.test(p)) score++
  return score
})
const strengthLabel = computed(() => (strength.value <= 1 ? t('app.settings.weak') : strength.value <= 3 ? t('app.settings.medium') : t('app.settings.strong')))

// ── Dirty state per section ─────────────────────────────────────────────────
function isDirty(section: SectionId): boolean {
  if (!me.value) return false
  const m = me.value
  switch (section) {
    case 'profile':
      return profile.displayName !== m.displayName || profile.avatar != null || profile.removeAvatar
    case 'notifications':
      return notifications.emailNotify !== m.settings.emailNotify || notifications.inappNotify !== m.settings.inappNotify
    case 'password':
      return !!(passwordForm.current || passwordForm.next || passwordForm.confirm)
  }
}

function switchSection(target: SectionId) {
  if (target === active.value) return
  if (isDirty(active.value)) {
    pendingSection.value = target
    confirmSwitch.value = true
  } else {
    active.value = target
  }
}
function confirmSwitchSection() {
  resetSection(pendingSection.value)
  active.value = pendingSection.value
  confirmSwitch.value = false
}

function resetSection(section: SectionId) {
  const m = me.value
  if (!m) return
  switch (section) {
    case 'profile':
      profile.displayName = m.displayName
      setAvatarFile(null)
      profile.removeAvatar = false
      profileError.value = ''
      break
    case 'notifications':
      notifications.emailNotify = m.settings.emailNotify
      notifications.inappNotify = m.settings.inappNotify
      notifError.value = ''
      break
    case 'password':
      passwordForm.current = ''
      passwordForm.next = ''
      passwordForm.confirm = ''
      passwordError.value = ''
      break
  }
}

function applyMe(m: MeResponse) {
  me.value = m
  // Keep the global user (top-right header) in sync with the latest profile.
  auth.setUserName(m.displayName)
  auth.setAvatarUrl(m.avatarPath)
  resetSection('profile')
  resetSection('notifications')
  resetSection('password')
}

async function load() {
  loading.value = true
  loadError.value = ''
  try {
    applyMe(await settingsApi.me())
  } catch (err) {
    loadError.value = toUserError(err).message
  } finally {
    loading.value = false
  }
}

// ── Save handlers ───────────────────────────────────────────────────────────
async function saveProfile() {
  profileError.value = ''
  if (!profile.displayName.trim()) {
    profileError.value = t('app.settings.displayNameEmpty')
    return
  }
  if (profile.displayName.length > 50) {
    profileError.value = t('app.settings.displayNameTooLong')
    return
  }
  profileSaving.value = true
  try {
    applyMe(
      await settingsApi.updateProfile(
        { displayName: profile.displayName.trim() },
        profile.avatar ?? undefined,
        profile.removeAvatar
      )
    )
    toastSuccess(t('app.settings.savedToast'))
  } catch (err) {
    profileError.value = toUserError(err).message
  } finally {
    profileSaving.value = false
  }
}

async function saveNotifications() {
  notifError.value = ''
  notifSaving.value = true
  try {
    applyMe(await settingsApi.updateNotifications({ ...notifications }))
    toastSuccess(t('app.settings.savedToast'))
  } catch (err) {
    notifError.value = toUserError(err).message
  } finally {
    notifSaving.value = false
  }
}

async function savePassword() {
  passwordError.value = ''
  if (!passwordForm.current || !passwordForm.next || !passwordForm.confirm) {
    passwordError.value = t('app.settings.passwordFieldsRequired')
    return
  }
  if (passwordForm.next !== passwordForm.confirm) {
    passwordError.value = t('app.settings.passwordMismatch')
    return
  }
  passwordSaving.value = true
  try {
    await settingsApi.changePassword({
      currentPassword: passwordForm.current,
      newPassword: passwordForm.next,
      confirmPassword: passwordForm.confirm,
    })
    toastSuccess(t('app.settings.passwordUpdatedToast'))
    resetSection('password')
  } catch (err) {
    passwordError.value = toUserError(err).message
  } finally {
    passwordSaving.value = false
  }
}

onMounted(load)
</script>

<template>
  <div class="settings">
    <div v-if="loading" class="settings-state p-glass">
      <p>{{ t('app.settings.loading') }}</p>
    </div>
    <PrismEmptyState v-else-if="loadError" :title="t('app.settings.loadError')">
      <p class="settings-error-text">{{ loadError }}</p>
      <PrismButton variant="secondary" @click="load">{{ t('app.common.retry') }}</PrismButton>
    </PrismEmptyState>

    <div v-else class="settings-body">
      <nav class="settings-nav p-glass" :aria-label="t('app.settings.sectionsAria')">
        <button
          v-for="section in SECTIONS"
          :key="section.id"
          type="button"
          class="settings-nav-item"
          :class="{ 'is-active': active === section.id }"
          :aria-current="active === section.id ? 'page' : undefined"
          @click="switchSection(section.id)"
        >
          {{ section.label }}
        </button>
      </nav>

      <div class="settings-content p-glass">
        <!-- Profile -->
        <section v-if="active === 'profile'" :aria-label="t('app.settings.profile')">
          <h2>{{ t('app.settings.profile') }}</h2>
          <p v-if="profileError" class="p-field-error" role="alert">{{ profileError }}</p>

          <div class="profile-avatar">
            <div class="profile-avatar-side">
              <PrismAvatar
                :name="profile.displayName || me?.displayName || ''"
                :src="!profile.removeAvatar ? (profile.avatar ? avatarPreviewUrl : me?.avatarPath) ?? undefined : undefined"
                :size="250"
              />
            </div>
            <div class="profile-avatar-actions">
              <PrismFileUpload
                :model-value="profile.avatar ? [profile.avatar] : []"
                :label="t('app.settings.changePhoto')"
                accept=".jpg,.jpeg,.png,.webp"
                :max-size="2 * 1024 * 1024"
                :hint="t('app.settings.photoHint')"
                @update:model-value="(files: File[]) => setAvatarFile(files[0] ?? null)"
              />
              <PrismButton
                v-if="me?.avatarPath"
                variant="secondary"
                type="button"
                @click="profile.removeAvatar = !profile.removeAvatar"
              >
                {{ profile.removeAvatar ? t('app.settings.keepPhoto') : t('app.common.remove') }}
              </PrismButton>
            </div>
          </div>

          <div class="profile-name-field">
            <PrismInput
              v-model="profile.displayName"
              :label="t('app.settings.displayName')"
              required
              maxlength="50"
            />
            <span class="name-counter" :class="{ 'is-warn': nameCounterWarn }" aria-live="polite">
              {{ nameCounter }}
            </span>
          </div>
          <PrismInput :model-value="me?.email ?? ''" :label="t('app.common.email')" readonly :hint="t('app.settings.emailHint')" />
          <PrismInput :model-value="departmentLabel" :label="t('app.settings.department')" readonly />

          <div class="settings-actions">
            <PrismButton variant="secondary" type="button" @click="resetSection('profile')">{{ t('app.common.cancel') }}</PrismButton>
            <PrismButton type="button" :loading="profileSaving" :disabled="!isDirty('profile')" @click="saveProfile">
              {{ t('app.common.save') }}
            </PrismButton>
          </div>
        </section>

        <!-- Notifications -->
        <section v-else-if="active === 'notifications'" :aria-label="t('app.settings.notifications')">
          <h2>{{ t('app.settings.notifications') }}</h2>
          <p v-if="notifError" class="p-field-error" role="alert">{{ notifError }}</p>

          <div class="notif-row">
            <PrismSwitch v-model="notifications.emailNotify" :label="t('app.settings.emailNotify')" />
            <p class="notif-desc">
              {{ t('app.settings.emailNotifyDesc') }}
            </p>
          </div>
          <div class="notif-row">
            <PrismSwitch v-model="notifications.inappNotify" :label="t('app.settings.inappNotify')" />
            <p class="notif-desc">{{ t('app.settings.inappNotifyDesc') }}</p>
          </div>

          <div class="settings-actions">
            <PrismButton variant="secondary" type="button" @click="resetSection('notifications')">{{ t('app.common.cancel') }}</PrismButton>
            <PrismButton type="button" :loading="notifSaving" :disabled="!isDirty('notifications')" @click="saveNotifications">
              {{ t('app.common.save') }}
            </PrismButton>
          </div>
        </section>

        <!-- Change Password -->
        <section v-else :aria-label="t('app.settings.changePassword')">
          <h2>{{ t('app.settings.changePassword') }}</h2>
          <p v-if="passwordError" class="p-field-error" role="alert">{{ passwordError }}</p>

          <PrismPasswordInput
            v-model="passwordForm.current"
            :label="t('app.settings.currentPassword')"
            required
            autocomplete="current-password"
          />
          <div class="password-group">
            <PrismPasswordInput
              v-model="passwordForm.next"
              :label="t('app.auth.newPassword')"
              required
              :strength="strength"
              autocomplete="new-password"
            />
            <p class="strength-label" aria-live="polite">{{ t('app.settings.strength', { level: strengthLabel }) }}</p>
            <PasswordPolicy :password="passwordForm.next" />
          </div>
          <PrismPasswordInput
            v-model="passwordForm.confirm"
            :label="t('app.auth.confirmNewPassword')"
            required
            autocomplete="new-password"
          />

          <div class="settings-actions">
            <PrismButton variant="secondary" type="button" @click="resetSection('password')">{{ t('app.common.cancel') }}</PrismButton>
            <PrismButton type="button" :loading="passwordSaving" @click="savePassword">{{ t('app.common.save') }}</PrismButton>
          </div>
        </section>
      </div>
    </div>

    <PrismDialog
      v-model="confirmSwitch"
      :title="t('app.settings.unsavedTitle')"
      :description="t('app.settings.unsavedDesc')"
    >
      <template #footer>
        <PrismButton variant="secondary" type="button" @click="confirmSwitch = false">{{ t('app.common.stay') }}</PrismButton>
        <PrismButton type="button" @click="confirmSwitchSection">{{ t('app.common.leave') }}</PrismButton>
      </template>
    </PrismDialog>
  </div>
</template>

<style scoped>
.settings {
  display: grid;
  gap: var(--ds-space-4);
  width: 100%;
}
.settings-state {
  padding: var(--ds-space-6);
  color: var(--ds-color-text-muted);
}
.settings-error-text {
  color: var(--ds-color-danger);
  font-size: 13px;
}

.settings-body {
  display: grid;
  grid-template-columns: 220px 1fr;
  gap: var(--ds-space-4);
  align-items: start;
}
@media (max-width: 899px) {
  .settings-body {
    grid-template-columns: 1fr;
  }
}

.settings-nav {
  display: grid;
  gap: var(--ds-space-1);
  padding: var(--ds-space-3);
}
.settings-nav-item {
  position: relative;
  text-align: left;
  border: none;
  background: none;
  color: var(--ds-color-text-secondary);
  font: 14px var(--ds-font-sans);
  padding: 10px var(--ds-space-3);
  border-radius: var(--ds-radius-sm);
  cursor: pointer;
}
.settings-nav-item:hover {
  background: var(--ds-list-item-hover-accent);
  color: var(--ds-color-text-primary);
}
.settings-nav-item.is-active {
  background: var(--ds-list-item-hover-accent);
  color: var(--ds-color-text-primary);
}
.settings-nav-item.is-active::before {
  content: '';
  position: absolute;
  left: 0;
  top: 8px;
  bottom: 8px;
  width: 3px;
  border-radius: 2px;
  background: var(--ds-color-accent);
}

.settings-content {
  padding: var(--ds-space-6);
  display: grid;
  gap: var(--ds-space-4);
}
.settings-content section {
  max-width: 720px;
  display: grid;
  gap: var(--ds-space-6);
}
.settings-content h2 {
  margin: 0;
  font-size: 18px;
}

.profile-avatar {
  display: grid;
  grid-template-columns: 1fr 1fr;
  gap: var(--ds-space-4);
  align-items: center;
}
@media (max-width: 599px) {
  .profile-avatar {
    grid-template-columns: 1fr;
  }
}
.profile-avatar-side {
  display: grid;
  place-items: center;
}
.profile-avatar-actions {
  display: grid;
  gap: var(--ds-space-2);
}
.profile-name-field {
  display: grid;
  gap: var(--ds-space-1);
}
.name-counter {
  font: 12px var(--ds-font-mono);
  color: var(--ds-color-text-muted);
}
.name-counter.is-warn {
  color: var(--ds-color-danger);
}

.notif-row {
  display: grid;
  gap: var(--ds-space-1);
}
.notif-desc {
  margin: 0 0 0 var(--ds-space-4);
  font-size: 12px;
  color: var(--ds-color-text-muted);
}

.password-group {
  display: grid;
  gap: var(--ds-space-2);
}
.strength-label {
  margin: 0;
  font-size: 12px;
  color: var(--ds-color-text-muted);
}

.settings-actions {
  display: flex;
  justify-content: flex-end;
  gap: var(--ds-space-3);
  margin-top: var(--ds-space-2);
}
</style>
