import { computed, ref } from 'vue'
import { settingsApi } from '@/features/users/api'

/**
 * Feature-scoped i18n for AI News (3 languages: vi/en/ko — NFR-04).
 * The language is read from the user's Settings preference (Settings SRS
 * SH-03-04) and cached in localStorage so every screen can use it without
 * re-fetching. Falls back to 'en' until the first successful fetch.
 */

export type AiNewsLang = 'vi' | 'en' | 'ko'

const LANG_KEY = 'aiNews.lang'
const DATE_FORMAT_KEY = 'aiNews.dateFormat'

const language = ref<AiNewsLang>((localStorage.getItem(LANG_KEY) as AiNewsLang) ?? 'en')
const dateFormat = ref<string>(localStorage.getItem(DATE_FORMAT_KEY) ?? 'DD/MM/YYYY')
let loaded = false
let loading: Promise<void> | null = null

export async function loadAiNewsPrefs(): Promise<void> {
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

/** Translate a key (e.g. `aiNews.loading`) in the active language. */
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

/** Format an ISO date per the user's date format setting (default DD/MM/YYYY). */
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

const MESSAGES: Record<AiNewsLang, Record<string, string>> = {
  vi: {
    'app.aiNews': 'AI News',
    'aiNews.subtitle': 'Tin AI từ email nội bộ',
    'aiNews.loading': 'Đang tải nội dung email…',
    'aiNews.loadFail': 'Không tải được nội dung email.',
    'aiNews.noBody': 'Email không có nội dung.',
    'aiNews.notFound': 'Tin không tồn tại hoặc đã được lưu trữ.',
    'aiNews.selectNews': 'Chọn một tin',
    'aiNews.selectNewsDesc': 'Chọn tin trong danh sách để xem nội dung email.',
    'aiNews.comments': 'Bình luận',
    'aiNews.commentCount': 'Bình luận ({n})',
    'aiNews.writeComment': 'Viết bình luận',
    'aiNews.writeCommentPh': 'Thảo luận về bản tin này…',
    'aiNews.send': 'Gửi',
    'aiNews.reply': 'Trả lời',
    'aiNews.writeReplyPh': 'Viết trả lời…',
    'aiNews.delComment': 'Xóa',
    'aiNews.delCommentTitle': 'Xóa bình luận',
    'aiNews.delCommentDesc': 'Bạn có chắc muốn xóa bình luận này?',
    'aiNews.helpful': 'Hữu ích',
    'aiNews.cannotVoteOwn': 'Không thể vote bình luận của chính mình',
    'aiNews.noComments': 'Chưa có bình luận nào',
    'aiNews.noCommentsDesc': 'Hãy là người đầu tiên thảo luận về bản tin này.',
    'aiNews.commentSent': 'Đã gửi bình luận',
    'aiNews.replySent': 'Đã gửi trả lời',
    'aiNews.replyFlattened': 'Trả lời đã gộp vào chủ đề chính',
    'aiNews.commentDeleted': 'Đã xóa bình luận',
    'aiNews.voteRegistered': 'Đã đánh dấu hữu ích',
    'aiNews.voteRemoved': 'Đã bỏ đánh dấu hữu ích',
    'aiNews.deletedUser': 'User đã xóa',
    'aiNews.deletedComment': '[đã xóa]',
    'aiNews.like': 'Thích',
    'aiNews.liked': 'Đã thích',
    'aiNews.listEmpty': 'Chưa có tin nào',
    'aiNews.retry': 'Thử lại',
  },
  en: {
    'app.aiNews': 'AI News',
    'aiNews.subtitle': 'AI news from internal emails',
    'aiNews.loading': 'Loading email content…',
    'aiNews.loadFail': 'Failed to load email content.',
    'aiNews.noBody': 'This email has no content.',
    'aiNews.notFound': 'News not found or archived.',
    'aiNews.selectNews': 'Select a news item',
    'aiNews.selectNewsDesc': 'Select an item from the list to view the email content.',
    'aiNews.comments': 'Comments',
    'aiNews.commentCount': 'Comments ({n})',
    'aiNews.writeComment': 'Write a comment',
    'aiNews.writeCommentPh': 'Discuss this news…',
    'aiNews.send': 'Send',
    'aiNews.reply': 'Reply',
    'aiNews.writeReplyPh': 'Write a reply…',
    'aiNews.delComment': 'Delete',
    'aiNews.delCommentTitle': 'Delete comment',
    'aiNews.delCommentDesc': 'Are you sure you want to delete this comment?',
    'aiNews.helpful': 'Helpful',
    'aiNews.cannotVoteOwn': 'Cannot vote on your own comment',
    'aiNews.noComments': 'No comments yet',
    'aiNews.noCommentsDesc': 'Be the first to discuss this news.',
    'aiNews.commentSent': 'Comment sent',
    'aiNews.replySent': 'Reply sent',
    'aiNews.replyFlattened': 'Reply flattened to main thread',
    'aiNews.commentDeleted': 'Comment deleted',
    'aiNews.voteRegistered': 'Marked as helpful',
    'aiNews.voteRemoved': 'Removed helpful mark',
    'aiNews.deletedUser': 'Deleted user',
    'aiNews.deletedComment': '[deleted]',
    'aiNews.like': 'Like',
    'aiNews.liked': 'Liked',
    'aiNews.listEmpty': 'No news yet',
    'aiNews.retry': 'Retry',
  },
  ko: {
    'app.aiNews': 'AI 뉴스',
    'aiNews.subtitle': '내부 이메일 AI 뉴스',
    'aiNews.loading': '이메일 내용 불러오는 중…',
    'aiNews.loadFail': '이메일 내용을 불러오지 못했습니다.',
    'aiNews.noBody': '이메일에 내용이 없습니다.',
    'aiNews.notFound': '뉴스가 없거나 보관되었습니다.',
    'aiNews.selectNews': '뉴스 선택',
    'aiNews.selectNewsDesc': '목록에서 뉴스를 선택하여 이메일 내용을 확인하세요.',
    'aiNews.comments': '댓글',
    'aiNews.commentCount': '댓글 ({n})',
    'aiNews.writeComment': '댓글 작성',
    'aiNews.writeCommentPh': '이 뉴스에 대해 토론하세요…',
    'aiNews.send': '전송',
    'aiNews.reply': '답장',
    'aiNews.writeReplyPh': '답장 작성…',
    'aiNews.delComment': '삭제',
    'aiNews.delCommentTitle': '댓글 삭제',
    'aiNews.delCommentDesc': '이 댓글을 정말 삭제할까요?',
    'aiNews.helpful': '유용해요',
    'aiNews.cannotVoteOwn': '자신의 댓글에는 투표할 수 없습니다',
    'aiNews.noComments': '아직 댓글이 없습니다',
    'aiNews.noCommentsDesc': '이 뉴스에 대해 첫 번째 토론을 시작하세요.',
    'aiNews.commentSent': '댓글 전송됨',
    'aiNews.replySent': '답장 전송됨',
    'aiNews.replyFlattened': '답장이 메인 스레드에 병합됨',
    'aiNews.commentDeleted': '댓글 삭제됨',
    'aiNews.voteRegistered': '유용함 표시됨',
    'aiNews.voteRemoved': '유용함 표시 취소',
    'aiNews.deletedUser': '삭제된 사용자',
    'aiNews.deletedComment': '[삭제됨]',
    'aiNews.like': '좋아요',
    'aiNews.liked': '좋아요',
    'aiNews.listEmpty': '아직 뉴스가 없습니다',
    'aiNews.retry': '다시 시도',
  },
}
