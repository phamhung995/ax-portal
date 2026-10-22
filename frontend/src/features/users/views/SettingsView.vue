<script setup lang="ts">
import { computed, onBeforeUnmount, onMounted, reactive, ref } from 'vue'
import PrismButton from '@/design-system/components/PrismButton.vue'
import PrismInput from '@/design-system/components/PrismInput.vue'
import PrismCombobox from '@/design-system/components/PrismCombobox.vue'
import PrismSwitch from '@/design-system/components/PrismSwitch.vue'
import PrismPasswordInput from '@/design-system/components/PrismPasswordInput.vue'
import PrismFileUpload from '@/design-system/components/PrismFileUpload.vue'
import PrismDialog from '@/design-system/components/PrismDialog.vue'
import PrismAlert from '@/design-system/components/PrismAlert.vue'
import PrismEmptyState from '@/design-system/components/PrismEmptyState.vue'
import PrismAvatar from '@/design-system/components/PrismAvatar.vue'
import PasswordPolicy from '@/features/auth/components/PasswordPolicy.vue'
import { settingsApi } from '@/features/users/api'
import { toUserError } from '@/lib/api'
import { toastSuccess, toastError } from '@/lib/toast'
import { useAuthStore } from '@/stores/auth'
import type { MeResponse } from '@/features/users/types'

const auth = useAuthStore()

const SECTIONS = [
  { id: 'profile', label: 'Profile' },
  { id: 'preferences', label: 'Preferences' },
  { id: 'notifications', label: 'Notifications' },
  { id: 'password', label: 'Change Password' },
] as const
type SectionId = (typeof SECTIONS)[number]['id']

const loading = ref(true)
const loadError = ref('')
const me = ref<MeResponse | null>(null)

const departmentLabel = computed(() => {
  const orgs = me.value?.orgs ?? []
  if (orgs.length === 0) return '—'
  return orgs.map((o) => `${o.part} > ${o.group}`).join('; ')
})

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

const nameCounter = computed(() => `${profile.displayName.length}/50`)
const nameCounterWarn = computed(() => profile.displayName.length >= 45)

// ── Preferences ─────────────────────────────────────────────────────────────
const preferences = reactive({ language: 'en', timezone: 'Asia/Ho_Chi_Minh', dateFormat: 'DD/MM/YYYY' })
const prefsSaving = ref(false)
const prefsError = ref('')

const LANGUAGE_OPTIONS = [
  { label: 'English', value: 'en' },
  { label: 'Tiếng Việt', value: 'vi' },
]
const TIMEZONE_OPTIONS = [
  { label: 'Asia/Ho_Chi_Minh', value: 'Asia/Ho_Chi_Minh' },
  { label: 'Asia/Bangkok', value: 'Asia/Bangkok' },
  { label: 'Asia/Tokyo', value: 'Asia/Tokyo' },
  { label: 'UTC', value: 'UTC' },
]
const DATE_FORMAT_OPTIONS = ['DD/MM/YYYY', 'MM/DD/YYYY', 'YYYY-MM-DD'].map((v) => ({ label: v, value: v }))

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
const strengthLabel = computed(() => (strength.value <= 1 ? 'Weak' : strength.value <= 3 ? 'Medium' : 'Strong'))

// ── Dirty state per section ─────────────────────────────────────────────────
function isDirty(section: SectionId): boolean {
  if (!me.value) return false
  const m = me.value
  switch (section) {
    case 'profile':
      return profile.displayName !== m.displayName || profile.avatar != null || profile.removeAvatar
    case 'preferences':
      return (
        preferences.language !== m.settings.language ||
        preferences.timezone !== m.settings.timezone ||
        preferences.dateFormat !== m.settings.dateFormat
      )
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
    case 'preferences':
      preferences.language = m.settings.language
      preferences.timezone = m.settings.timezone
      preferences.dateFormat = m.settings.dateFormat
      prefsError.value = ''
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
  resetSection('preferences')
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
    profileError.value = 'Display name cannot be empty'
    return
  }
  if (profile.displayName.length > 50) {
    profileError.value = 'Display name is at most 50 characters'
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
    toastSuccess('Settings saved successfully')
  } catch (err) {
    profileError.value = toUserError(err).message
  } finally {
    profileSaving.value = false
  }
}

async function savePreferences() {
  prefsError.value = ''
  prefsSaving.value = true
  try {
    applyMe(await settingsApi.updatePreferences({ ...preferences }))
    toastSuccess('Settings saved successfully')
  } catch (err) {
    prefsError.value = toUserError(err).message
  } finally {
    prefsSaving.value = false
  }
}

async function saveNotifications() {
  notifError.value = ''
  notifSaving.value = true
  try {
    applyMe(await settingsApi.updateNotifications({ ...notifications }))
    toastSuccess('Settings saved successfully')
  } catch (err) {
    notifError.value = toUserError(err).message
  } finally {
    notifSaving.value = false
  }
}

async function savePassword() {
  passwordError.value = ''
  if (!passwordForm.current || !passwordForm.next || !passwordForm.confirm) {
    passwordError.value = 'Please fill in all password fields.'
    return
  }
  if (passwordForm.next !== passwordForm.confirm) {
    passwordError.value = 'Password confirmation mismatch'
    return
  }
  passwordSaving.value = true
  try {
    await settingsApi.changePassword({
      currentPassword: passwordForm.current,
      newPassword: passwordForm.next,
      confirmPassword: passwordForm.confirm,
    })
    toastSuccess('Password updated successfully')
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
      <p>Loading settings…</p>
    </div>
    <PrismEmptyState v-else-if="loadError" title="Unable to load settings">
      <p class="settings-error-text">{{ loadError }}</p>
      <PrismButton variant="secondary" @click="load">Retry</PrismButton>
    </PrismEmptyState>

    <div v-else class="settings-body">
      <nav class="settings-nav p-glass" aria-label="Settings sections">
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
        <section v-if="active === 'profile'" aria-label="Profile">
          <h2>Profile</h2>
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
                label="Change photo"
                accept=".jpg,.jpeg,.png,.webp"
                :max-size="2 * 1024 * 1024"
                hint="JPG, PNG or WebP, max 2MB"
                @update:model-value="(files: File[]) => setAvatarFile(files[0] ?? null)"
              />
              <PrismButton
                v-if="me?.avatarPath"
                variant="secondary"
                type="button"
                @click="profile.removeAvatar = !profile.removeAvatar"
              >
                {{ profile.removeAvatar ? 'Keep photo' : 'Remove' }}
              </PrismButton>
            </div>
          </div>

          <div class="profile-name-field">
            <PrismInput
              v-model="profile.displayName"
              label="Display name"
              required
              maxlength="50"
            />
            <span class="name-counter" :class="{ 'is-warn': nameCounterWarn }" aria-live="polite">
              {{ nameCounter }}
            </span>
          </div>
          <PrismInput :model-value="me?.email ?? ''" label="Email" readonly hint="Email cannot be changed" />
          <PrismInput :model-value="departmentLabel" label="Department" readonly />

          <div class="settings-actions">
            <PrismButton variant="secondary" type="button" @click="resetSection('profile')">Cancel</PrismButton>
            <PrismButton type="button" :loading="profileSaving" :disabled="!isDirty('profile')" @click="saveProfile">
              Save
            </PrismButton>
          </div>
        </section>

        <!-- Preferences -->
        <section v-else-if="active === 'preferences'" aria-label="Preferences">
          <h2>Preferences</h2>
          <p v-if="prefsError" class="p-field-error" role="alert">{{ prefsError }}</p>

          <PrismCombobox v-model="preferences.language" label="System language" :options="LANGUAGE_OPTIONS" required />
          <PrismCombobox v-model="preferences.timezone" label="Timezone" :options="TIMEZONE_OPTIONS" required />
          <PrismCombobox v-model="preferences.dateFormat" label="Date format" :options="DATE_FORMAT_OPTIONS" required />

          <PrismAlert tone="info">Language &amp; date format apply to the entire system after saving.</PrismAlert>

          <div class="settings-actions">
            <PrismButton variant="secondary" type="button" @click="resetSection('preferences')">Cancel</PrismButton>
            <PrismButton type="button" :loading="prefsSaving" :disabled="!isDirty('preferences')" @click="savePreferences">
              Save
            </PrismButton>
          </div>
        </section>

        <!-- Notifications -->
        <section v-else-if="active === 'notifications'" aria-label="Notifications">
          <h2>Notifications</h2>
          <p v-if="notifError" class="p-field-error" role="alert">{{ notifError }}</p>

          <div class="notif-row">
            <PrismSwitch v-model="notifications.emailNotify" label="Email notifications" />
            <p class="notif-desc">
              Receive an email only when a subscribed skill has a new approved version.
            </p>
          </div>
          <div class="notif-row">
            <PrismSwitch v-model="notifications.inappNotify" label="In-app notifications" />
            <p class="notif-desc">Show notifications in the bell menu.</p>
          </div>

          <div class="settings-actions">
            <PrismButton variant="secondary" type="button" @click="resetSection('notifications')">Cancel</PrismButton>
            <PrismButton type="button" :loading="notifSaving" :disabled="!isDirty('notifications')" @click="saveNotifications">
              Save
            </PrismButton>
          </div>
        </section>

        <!-- Change Password -->
        <section v-else aria-label="Change Password">
          <h2>Change Password</h2>
          <p v-if="passwordError" class="p-field-error" role="alert">{{ passwordError }}</p>

          <PrismPasswordInput
            v-model="passwordForm.current"
            label="Current password"
            required
            autocomplete="current-password"
          />
          <div class="password-group">
            <PrismPasswordInput
              v-model="passwordForm.next"
              label="New password"
              required
              :strength="strength"
              autocomplete="new-password"
            />
            <p class="strength-label" aria-live="polite">Strength: {{ strengthLabel }}</p>
            <PasswordPolicy :password="passwordForm.next" />
          </div>
          <PrismPasswordInput
            v-model="passwordForm.confirm"
            label="Confirm new password"
            required
            autocomplete="new-password"
          />

          <div class="settings-actions">
            <PrismButton variant="secondary" type="button" @click="resetSection('password')">Cancel</PrismButton>
            <PrismButton type="button" :loading="passwordSaving" @click="savePassword">Save</PrismButton>
          </div>
        </section>
      </div>
    </div>

    <PrismDialog
      v-model="confirmSwitch"
      title="Unsaved changes"
      description="You have unsaved changes. Continue and discard changes?"
    >
      <template #footer>
        <PrismButton variant="secondary" type="button" @click="confirmSwitch = false">Stay</PrismButton>
        <PrismButton type="button" @click="confirmSwitchSection">Leave</PrismButton>
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
