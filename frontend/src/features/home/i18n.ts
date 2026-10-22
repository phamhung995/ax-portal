import { computed, ref } from 'vue'
import { settingsApi } from '@/features/users/api'

/**
 * Feature-scoped i18n for Home (3 languages: vi/en/ko — Home SRS §11).
 * Language + date format come from the user's Settings preference
 * (Settings SRS SH-03-04), cached in localStorage.
 */

export type HomeLang = 'vi' | 'en' | 'ko'

const LANG_KEY = 'home.lang'
const DATE_FORMAT_KEY = 'home.dateFormat'

const language = ref<HomeLang>((localStorage.getItem(LANG_KEY) as HomeLang) ?? 'en')
const dateFormat = ref<string>(localStorage.getItem(DATE_FORMAT_KEY) ?? 'DD/MM/YYYY')
let loaded = false
let loading: Promise<void> | null = null

export async function loadHomePrefs(): Promise<void> {
  if (loaded) return
  if (!loading) {
    loading = (async () => {
      try {
        const me = await settingsApi.me()
        const lang = me.settings.language
        if (lang === 'vi' || lang === 'en' || lang === 'ko') {
          language.value = lang
          localStorage.setItem(LANG_KEY, lang)
        }
        if (me.settings.dateFormat) {
          dateFormat.value = me.settings.dateFormat
          localStorage.setItem(DATE_FORMAT_KEY, me.settings.dateFormat)
        }
        loaded = true
      } catch {
        /* non-critical — keep the cached/default language */
      } finally {
        loading = null
      }
    })()
  }
  await loading
}

export function t(key: string, vars?: Record<string, string | number>): string {
  const table = MESSAGES[language.value]
  let text = table[key] ?? MESSAGES.en[key] ?? key
  if (vars) {
    for (const [k, v] of Object.entries(vars)) {
      text = text.replaceAll(`{${k}}`, String(v))
    }
  }
  return text
}

export function formatDate(iso: string | null): string {
  if (!iso) return ''
  const d = new Date(iso)
  if (Number.isNaN(d.getTime())) return ''
  const dd = String(d.getDate()).padStart(2, '0')
  const mm = String(d.getMonth() + 1).padStart(2, '0')
  const yyyy = String(d.getFullYear())
  switch (dateFormat.value) {
    case 'MM/DD/YYYY':
      return `${mm}/${dd}/${yyyy}`
    case 'YYYY-MM-DD':
      return `${yyyy}-${mm}-${dd}`
    case 'DD/MM/YYYY':
    default:
      return `${dd}/${mm}/${yyyy}`
  }
}

export const activeLang = computed(() => language.value)

const MESSAGES: Record<HomeLang, Record<string, string>> = {
  vi: {
    'home.title': 'Trang chủ',
    'home.tabChart': 'Biểu đồ',
    'home.tabCommunity': 'Cộng đồng',
    'home.hello': 'Xin chào, {name}',
    'home.role.normal': 'User thường',
    'home.role.admin': 'Quản trị viên',
    'home.addSkill': '+ Thêm Skill',
    'home.addSkillBtn': 'Thêm Skill',
    'home.skillMgmt': 'Quản lý Skill',
    'home.kpi.mySkills': 'Skill của tôi',
    'home.kpi.published': 'Đã duyệt',
    'home.kpi.pending': 'Chờ duyệt',
    'home.kpi.totalViews': 'Tổng lượt xem',
    'home.kpi.avgRating': 'Đánh giá TB',
    'home.kpi.subscribed': 'Đang đăng ký',
    'home.noRating': 'Chưa có đánh giá',
    'home.banner.review': 'Bạn có {n} skill chờ xem xét.',
    'home.banner.requests': 'Yêu cầu tính năng: {n}',
    'home.banner.reviewNow': 'Xem ngay',
    'home.card.hot': 'Skill Hot',
    'home.card.hotSub': 'Xem nhiều nhất',
    'home.card.trending': 'Skill mới nổi',
    'home.card.trendingSub': 'Xem nhiều nhất 2 tuần',
    'home.card.newSkills': 'Skill mới',
    'home.card.newSkillsSub': '10 skill gần nhất',
    'home.card.newRequest': 'Yêu cầu mới',
    'home.card.viewAll': 'Xem tất cả',
    'home.card.aiNews': 'AI News',
    'home.card.aiNewsSub': 'Tin AI từ email nội bộ · hiển thị người gửi (tên + email)',
    'home.noNewRequests': 'Chưa có yêu cầu nào',
    'home.noNewRequestsDesc': 'Chưa có yêu cầu bug hoặc cải tiến nào.',
    'home.noAiNews': 'Chưa có tin AI',
    'home.noAiNewsDesc': 'Chưa có tin AI nào.',
    'home.emptyList': 'Chưa có skill nào',
    'home.retry': 'Thử lại',
    'home.error.summary': 'Không tải được thông tin trang chủ. Vui lòng thử lại.',
    'home.error.community': 'Không tải được skill cộng đồng. Vui lòng thử lại.',
    'home.error.newRequests': 'Không tải được yêu cầu mới. Vui lòng thử lại.',
    'home.error.aiNews': 'Không tải được tin AI. Vui lòng thử lại.',
    'home.chart.status': 'Skill theo trạng thái',
    'home.chart.personal': 'Chỉ số cá nhân',
    'home.chart.byCreator': 'Skill theo creator',
    'home.chart.viewsByCreator': 'Views theo creator',
    'home.chart.activity6w': 'Activity 6 tuần',
    'home.chart.coverage': 'Activity coverage',
    'home.chart.publishedRatio': 'Tỷ lệ published',
    'home.chart.viewsSpark': 'Views sparkline',
    'home.chart.updatedByWeek': 'Skill update/tuần',
    'home.chart.info.status': 'Phân bố skill của bạn theo trạng thái (DRAFT, PENDING_APPROVAL, APPROVED, REJECTED).',
    'home.chart.info.personal': 'Chỉ số cá nhân: số skill, lượt xem, đánh giá trung bình, số skill đã đăng ký.',
    'home.chart.info.byCreator': 'Số skill theo từng creator (phân theo trạng thái).',
    'home.chart.info.viewsByCreator': 'Tổng lượt xem skill theo từng creator.',
    'home.chart.info.activity6w': 'Hoạt động (view + update) trong 6 tuần gần nhất.',
    'home.chart.info.coverage': 'Bản đồ nhiệt hoạt động theo ngày trong 6 tuần.',
    'home.chart.info.publishedRatio': 'Tỷ lệ skill đã duyệt so với tổng số skill của bạn.',
    'home.chart.info.viewsSpark': 'Xu hướng lượt xem 6 tuần gần nhất.',
    'home.chart.info.updatedByWeek': 'Số skill được cập nhật theo tuần.',
    'home.status.DRAFT': 'DRAFT',
    'home.status.PENDING_APPROVAL': 'PENDING',
    'home.status.APPROVED': 'APPROVED',
    'home.status.REJECTED': 'REJECTED',
  },
  en: {
    'home.title': 'Home',
    'home.tabChart': 'Chart',
    'home.tabCommunity': 'Community',
    'home.hello': 'Hello, {name}',
    'home.role.normal': 'Normal User',
    'home.role.admin': 'Admin User',
    'home.addSkill': '+ Add Skill',
    'home.addSkillBtn': 'Add Skill',
    'home.skillMgmt': 'Skill Management',
    'home.kpi.mySkills': 'My Skills',
    'home.kpi.published': 'Published',
    'home.kpi.pending': 'Pending Approval',
    'home.kpi.totalViews': 'Total Views',
    'home.kpi.avgRating': 'Average Rating',
    'home.kpi.subscribed': 'Subscribed',
    'home.noRating': 'No rating',
    'home.banner.review': 'You have {n} skills awaiting review.',
    'home.banner.requests': 'Feature Requests: {n}',
    'home.banner.reviewNow': 'Review Now',
    'home.card.hot': 'Skill Hot',
    'home.card.hotSub': 'Most viewed',
    'home.card.trending': 'Trending Skills',
    'home.card.trendingSub': 'Most viewed in 2 weeks',
    'home.card.newSkills': 'New Skills',
    'home.card.newSkillsSub': '10 latest skills',
    'home.card.newRequest': 'New Request',
    'home.card.viewAll': 'View All',
    'home.card.aiNews': 'AI News',
    'home.card.aiNewsSub': 'AI news from internal emails · shows sender (name + email)',
    'home.noNewRequests': 'No requests yet',
    'home.noNewRequestsDesc': 'No bug or enhancement requests yet.',
    'home.noAiNews': 'No AI news',
    'home.noAiNewsDesc': 'No AI news yet.',
    'home.emptyList': 'No skills yet',
    'home.retry': 'Retry',
    'home.error.summary': 'Unable to load home summary. Please try again.',
    'home.error.community': 'Unable to load community skills. Please try again.',
    'home.error.newRequests': 'Unable to load new requests. Please try again.',
    'home.error.aiNews': 'Unable to load AI news. Please try again.',
    'home.chart.status': 'Skill by status',
    'home.chart.personal': 'Personal metrics',
    'home.chart.byCreator': 'Skill by creator',
    'home.chart.viewsByCreator': 'Views by creator',
    'home.chart.activity6w': 'Activity 6 weeks',
    'home.chart.coverage': 'Activity coverage',
    'home.chart.publishedRatio': 'Published ratio',
    'home.chart.viewsSpark': 'Views sparkline',
    'home.chart.updatedByWeek': 'Skill updated/week',
    'home.chart.info.status': 'Distribution of your skills by status (DRAFT, PENDING_APPROVAL, APPROVED, REJECTED).',
    'home.chart.info.personal': 'Personal metrics: skill count, views, average rating, subscribed skills.',
    'home.chart.info.byCreator': 'Skill count per creator (split by status).',
    'home.chart.info.viewsByCreator': 'Total skill views per creator.',
    'home.chart.info.activity6w': 'Activity (views + updates) over the last 6 weeks.',
    'home.chart.info.coverage': 'Daily activity heatmap over the last 6 weeks.',
    'home.chart.info.publishedRatio': 'Ratio of approved skills over your total skills.',
    'home.chart.info.viewsSpark': 'View trend over the last 6 weeks.',
    'home.chart.info.updatedByWeek': 'Skills updated per week.',
    'home.status.DRAFT': 'DRAFT',
    'home.status.PENDING_APPROVAL': 'PENDING',
    'home.status.APPROVED': 'APPROVED',
    'home.status.REJECTED': 'REJECTED',
  },
  ko: {
    'home.title': '홈',
    'home.tabChart': '차트',
    'home.tabCommunity': '커뮤니티',
    'home.hello': '안녕하세요, {name}',
    'home.role.normal': '일반 사용자',
    'home.role.admin': '관리자',
    'home.addSkill': '+ 스킬 추가',
    'home.addSkillBtn': '스킬 추가',
    'home.skillMgmt': '스킬 관리',
    'home.kpi.mySkills': '내 스킬',
    'home.kpi.published': '승인됨',
    'home.kpi.pending': '승인 대기',
    'home.kpi.totalViews': '총 조회수',
    'home.kpi.avgRating': '평균 평가',
    'home.kpi.subscribed': '구독 중',
    'home.noRating': '평가 없음',
    'home.banner.review': '검토 대기 중인 스킬이 {n}개 있습니다.',
    'home.banner.requests': '기능 요청: {n}',
    'home.banner.reviewNow': '지금 검토',
    'home.card.hot': '인기 스킬',
    'home.card.hotSub': '조회수 최다',
    'home.card.trending': '상승 중인 스킬',
    'home.card.trendingSub': '2주간 조회수 최다',
    'home.card.newSkills': '새로운 스킬',
    'home.card.newSkillsSub': '최근 10개 스킬',
    'home.card.newRequest': '새 요청',
    'home.card.viewAll': '전체 보기',
    'home.card.aiNews': 'AI 뉴스',
    'home.card.aiNewsSub': '내부 이메일 AI 뉴스 · 발신자(이름 + 이메일) 표시',
    'home.noNewRequests': '요청 없음',
    'home.noNewRequestsDesc': '버그 또는 개선 요청이 없습니다.',
    'home.noAiNews': 'AI 뉴스 없음',
    'home.noAiNewsDesc': '아직 AI 뉴스가 없습니다.',
    'home.emptyList': '스킬 없음',
    'home.retry': '다시 시도',
    'home.error.summary': '홈 정보를 불러오지 못했습니다. 다시 시도해 주세요.',
    'home.error.community': '커뮤니티 스킬을 불러오지 못했습니다. 다시 시도해 주세요.',
    'home.error.newRequests': '새 요청을 불러오지 못했습니다. 다시 시도해 주세요.',
    'home.error.aiNews': 'AI 뉴스를 불러오지 못했습니다. 다시 시도해 주세요.',
    'home.chart.status': '상태별 스킬',
    'home.chart.personal': '개인 지표',
    'home.chart.byCreator': '생성자별 스킬',
    'home.chart.viewsByCreator': '생성자별 조회수',
    'home.chart.activity6w': '6주 활동',
    'home.chart.coverage': '활동 커버리지',
    'home.chart.publishedRatio': '승인 비율',
    'home.chart.viewsSpark': '조회수 스파크라인',
    'home.chart.updatedByWeek': '주별 업데이트',
    'home.chart.info.status': '상태별 스킬 분포(DRAFT, PENDING_APPROVAL, APPROVED, REJECTED).',
    'home.chart.info.personal': '개인 지표: 스킬 수, 조회수, 평균 평가, 구독 스킬.',
    'home.chart.info.byCreator': '생성자별 스킬 수(상태별 구분).',
    'home.chart.info.viewsByCreator': '생성자별 스킬 총 조회수.',
    'home.chart.info.activity6w': '최근 6주간 활동(조회수 + 업데이트).',
    'home.chart.info.coverage': '최근 6주간 일별 활동 히트맵.',
    'home.chart.info.publishedRatio': '승인된 스킬의 비율(전체 스킬 대비).',
    'home.chart.info.viewsSpark': '최근 6주간 조회수 추세.',
    'home.chart.info.updatedByWeek': '주별 업데이트된 스킬 수.',
    'home.status.DRAFT': 'DRAFT',
    'home.status.PENDING_APPROVAL': 'PENDING',
    'home.status.APPROVED': 'APPROVED',
    'home.status.REJECTED': 'REJECTED',
  },
}
