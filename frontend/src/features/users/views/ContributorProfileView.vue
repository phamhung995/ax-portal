<script setup lang="ts">
import { computed, onMounted, ref } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { ArrowLeft } from 'lucide-vue-next'
import PrismAvatar from '@/design-system/components/PrismAvatar.vue'
import PrismBadge from '@/design-system/components/PrismBadge.vue'
import PrismButton from '@/design-system/components/PrismButton.vue'
import PrismEmptyState from '@/design-system/components/PrismEmptyState.vue'
import { reputationApi, type ContributorReputation } from '@/features/reputation/api'
import { toUserError } from '@/lib/api'
import { useI18nStore } from '@/stores/i18n'

/**
 * Contributor (author) profile (GĐ5, BA §8.5): tier + item count + average
 * item score + fulfilled requests. The author tier is kept SEPARATE from
 * item scores — it never adds points to their items (BA P3).
 */
const route = useRoute()
const router = useRouter()
const { t } = useI18nStore()

const userId = computed(() => Number(route.params.userId))
const validId = computed(() => Number.isInteger(userId.value) && userId.value > 0)

const profile = ref<ContributorReputation | null>(null)
const loading = ref(false)
const loadError = ref('')

const tierLabel = (tier: string) =>
  tier === 'VERIFIED' ? 'Đã kiểm chứng'
    : tier === 'TRUSTED_AUTHOR' ? 'Tác giả đáng tin'
    : tier === 'EXPERT' ? 'Chuyên gia'
    : 'Thành viên mới'
const tierTone = (tier: string) =>
  tier === 'EXPERT' ? 'success' : tier === 'TRUSTED_AUTHOR' ? 'success' : tier === 'VERIFIED' ? 'info' : 'neutral'

async function loadProfile() {
  if (!validId.value) return
  loading.value = true
  loadError.value = ''
  profile.value = null
  try {
    profile.value = await reputationApi.contributor(userId.value)
  } catch (err) {
    loadError.value = toUserError(err).message
  } finally {
    loading.value = false
  }
}

function goBack() {
  router.back()
}

onMounted(() => {
  void loadProfile()
})
</script>

<template>
  <div class="contrib">
    <PrismButton variant="ghost" @click="goBack">
      <ArrowLeft :size="16" aria-hidden="true" /> {{ t('app.common.back') }}
    </PrismButton>

    <PrismEmptyState v-if="loadError" :title="t('app.skillMarket.loadError')" :description="loadError">
      <PrismButton variant="secondary" @click="loadProfile">{{ t('app.common.retry') }}</PrismButton>
    </PrismEmptyState>

    <section v-else-if="profile" class="contrib-card p-glass">
      <PrismAvatar :name="profile.userName ?? ''" :src="profile.avatarUrl ?? undefined" :size="64" />
      <div class="contrib-head">
        <h1 class="contrib-name">{{ profile.userName ?? t('app.common.unknown') }}</h1>
        <PrismBadge :tone="tierTone(profile.tier)">{{ tierLabel(profile.tier) }}</PrismBadge>
      </div>
      <dl class="contrib-stats">
        <div class="contrib-stat">
          <dt>Số skill / MCP đã đăng</dt>
          <dd>{{ profile.itemCount }}</dd>
        </div>
        <div class="contrib-stat">
          <dt>Điểm trung bình các item</dt>
          <dd>{{ profile.avgScore != null ? profile.avgScore.toFixed(2) : '—' }}</dd>
        </div>
        <div class="contrib-stat">
          <dt>Request đã đáp ứng</dt>
          <dd>{{ profile.fulfilledRequests }}</dd>
        </div>
        <div class="contrib-stat">
          <dt>Lượt báo cáo</dt>
          <dd>{{ profile.reportCount }}</dd>
        </div>
      </dl>
      <p class="contrib-note">
        Hạng tác giả được tính từ điểm các skill / MCP của tác giả và số request đã đáp ứng — không cộng điểm vào skill.
      </p>
    </section>
  </div>
</template>

<style scoped>
.contrib {
  display: grid;
  gap: var(--ds-space-4);
  width: 100%;
  max-width: 720px;
  justify-items: start;
}
.contrib-card {
  display: grid;
  gap: var(--ds-space-4);
  padding: var(--ds-space-5);
  width: 100%;
}
.contrib-head {
  display: flex;
  align-items: center;
  gap: var(--ds-space-3);
  flex-wrap: wrap;
}
.contrib-name {
  margin: 0;
  font-size: 20px;
  font-weight: 700;
  color: var(--ds-color-text-primary);
}
.contrib-stats {
  display: grid;
  grid-template-columns: repeat(auto-fit, minmax(140px, 1fr));
  gap: var(--ds-space-3);
  margin: 0;
}
.contrib-stat {
  display: grid;
  gap: 2px;
  padding: var(--ds-space-3);
  border: 1px solid var(--ds-color-border);
  border-radius: 8px;
  background: var(--ds-input-background);
}
.contrib-stat dt {
  font-size: 12.5px;
  color: var(--ds-color-text-muted);
}
.contrib-stat dd {
  margin: 0;
  font: 700 20px var(--ds-font-mono);
  color: var(--ds-color-text-primary);
}
.contrib-note {
  margin: 0;
  font-size: 13px;
  color: var(--ds-color-text-muted);
}
</style>
