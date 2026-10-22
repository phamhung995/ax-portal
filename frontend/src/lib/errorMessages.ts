/**
 * Maps backend error codes (BusinessErrorCode) to user-facing messages.
 * The frontend never branches on English message text from the server.
 */
const CATALOG: Record<string, string> = {
  // Login
  INVALID_EMAIL: 'Please enter a valid email address.',
  USER_NOT_FOUND: 'Invalid email or password.',
  INVALID_PASSWORD: 'Invalid email or password.',
  USER_DEACTIVE: 'Invalid Email or Password.',
  ORGANIZATION_NOT_SYNCED:
    'Your organization has not been synced yet. Please contact your administrator.',
  ORGANIZATION_UNAVAILABLE:
    'Your organization is no longer available. Please contact your administrator.',
  // Registration
  EMAIL_REQUIRED: 'Email is required.',
  EMAIL_TOO_LONG: 'Email must be 254 characters or fewer.',
  EMAIL_ALREADY_USED: 'This email is already registered.',
  NAME_REQUIRED: 'Full name is required.',
  NAME_TOO_LONG: 'Full name must be 50 characters or fewer.',
  PASSWORD_TOO_SHORT: 'Password must be at least 8 characters.',
  PASSWORD_WEAK: 'Password does not meet the complexity requirements.',
  PASSWORD_MISMATCH: 'Passwords do not match.',
  OTP_NOT_VERIFIED: 'Please verify the OTP before continuing.',
  OTP_INVALID: 'The OTP is incorrect. Please check and try again.',
  OTP_EXPIRED: 'The OTP has expired. Please request a new one.',
  REGISTRATION_FAILED: 'Registration failed. Please try again.',
  // Forgot / reset password
  PASSWORD_RESET_FAILED: 'Password reset failed. Please try again.',
  PASSWORD_CURRENT_WRONG: 'Current password is incorrect.',
  PASSWORD_SAME_AS_CURRENT: 'New password must be different from the current password.',
  TOO_MANY_ATTEMPTS: 'Too many attempts. Please try again in 15 minutes.',
  // User management
  EMAIL_IMMUTABLE: 'Email cannot be changed.',
  SELF_ROLE_CHANGE_FORBIDDEN: 'You cannot change your own role.',
  SELF_STATUS_CHANGE_FORBIDDEN: 'You cannot change your own status.',
  INVALID_ROLE: 'Invalid role.',
  INVALID_STATUS: 'Invalid status.',
  USER_NOT_FOUND_404: 'User not found.',
  CONCURRENT_UPDATE_CONFLICT:
    'This record was changed by another user. Please reload and try again.',
  KNOX_ID_CONFLICT:
    'A user with this Knox ID (email local part) already exists.',
  // BULK_FILE_INVALID intentionally not in the catalog: the backend message
  // names the missing columns and is shown as-is.
  BULK_FILE_TOO_LARGE: 'File must be 10 MB or smaller.',
  BULK_FILE_ENCRYPTED:
    'File is encrypted by Samsung NASC document protection. Please open the file in Excel, save a copy, then upload the copy.',
  BULK_TOO_MANY_ROWS: 'File must contain 500 rows or fewer.',
  DATA_INTEGRITY_CONFLICT:
    'This record already exists in the database. Please reload and try again.',
  // Settings
  AVATAR_TOO_LARGE: 'Avatar must be 2 MB or smaller.',
  AVATAR_INVALID_TYPE: 'Avatar must be a PNG or JPG image.',
  INVALID_LANGUAGE: 'Invalid language.',
  INVALID_TIMEZONE: 'Invalid timezone.',
  INVALID_DATE_FORMAT: 'Invalid date format.',
  // Skill management
  SKILL_NOT_FOUND: 'Skill not found.',
  SKILL_NAME_REQUIRED: 'Skill name cannot be empty.',
  SKILL_NAME_TOO_LONG: 'Skill name is at most 100 characters.',
  SKILL_NAME_DUPLICATE: 'You already have a skill with this name.',
  SKILL_INVALID_SCOPE: 'Invalid visibility scope.',
  SKILL_DESC_TOO_LONG: 'Description is at most 2000 characters.',
  SKILL_HASHTAGS_TOO_MANY: 'Maximum 10 hashtags.',
  SKILL_HASHTAG_TOO_LONG: 'Hashtag is at most 30 characters.',
  SKILL_INVALID_HASHTAG: 'Hashtag contains only letters, digits, "-" and "_".',
  SKILL_FILE_INVALID_TYPE: 'File format is not supported.',
  SKILL_FILE_TOO_LARGE: 'File size is at most 10MB.',
  SKILL_NOT_EDITABLE: 'You do not have permission to edit this skill.',
  SKILL_FIELD_READ_ONLY: 'This field cannot be changed.',
  SKILL_NOT_APPROVED: 'This skill is not approved yet.',
  SKILL_INVALID_TRANSITION: 'This status change is not allowed.',
  SKILL_OPTIMISTIC_LOCK: 'Skill has been changed by another user.',
  SKILL_DELETE_FORBIDDEN: 'You do not have permission to delete one or more selected Skills.',
  SKILL_INVALID_RATING: 'Rating must be between 1 and 5.',
  SKILL_COMMENT_EMPTY: 'Comment cannot be empty.',
  SKILL_COMMENT_TOO_LONG: 'Comment is at most 1000 characters.',
  SKILL_COMMENT_NOT_FOUND: 'Comment not found.',
  SKILL_FILE_NOT_FOUND: 'Skill file does not exist.',
  SKILL_SUBSCRIBE_FORBIDDEN: 'You cannot subscribe to this skill.',
  SKILL_RATE_LIMITED: 'Too many requests. Please try again later.',
  // AI News
  NEWS_NOT_FOUND: 'News not found or archived.',
  SKILL_CATEGORY_REQUIRED: 'Select at least one category.',
  SKILL_STAGE_REQUIRED: 'Select at least one process stage.',
  SKILL_INVALID_CATEGORY_ID: 'One or more categories do not exist.',
  SKILL_INVALID_STAGE_ID: 'One or more process stages do not exist.',
  // Feedback (VOC)
  FEEDBACK_NOT_FOUND: 'Feedback not found.',
  FEEDBACK_INVALID_TYPE: 'Invalid feedback type.',
  FEEDBACK_INVALID_STATUS: 'Invalid feedback status.',
  FEEDBACK_CONTENT_EMPTY: 'Feedback content cannot be empty.',
  FEEDBACK_CONTENT_TOO_LONG: 'Feedback content is at most 1000 characters.',
  FEEDBACK_RESOLUTION_NOTE_REQUIRED: 'A resolution note is required.',
  FEEDBACK_RESOLUTION_NOTE_TOO_LONG: 'Resolution note is at most 500 characters.',
  FEEDBACK_RATE_LIMITED: 'Too many requests. Please try again later.',
  // Skill request management
  SKILL_REQUEST_NOT_FOUND: 'Skill request not found.',
  SKILL_REQUEST_TITLE_REQUIRED: 'Request title cannot be empty.',
  SKILL_REQUEST_TITLE_TOO_LONG: 'Request title is at most 100 characters.',
  SKILL_REQUEST_DESC_TOO_LONG: 'Description is at most 2000 characters.',
  SKILL_REQUEST_ALREADY_VOTED: 'You have already voted for this request.',
  SKILL_REQUEST_NOT_EDITABLE: 'You do not have permission to perform this action on the request.',
  SKILL_REQUEST_COMMENT_EMPTY: 'Comment cannot be empty.',
  SKILL_REQUEST_COMMENT_TOO_LONG: 'Comment is at most 1000 characters.',
  SKILL_REQUEST_COMMENT_NOT_FOUND: 'Comment not found.',
  SKILL_REQUEST_LINK_INVALID: 'The linked skill does not exist or is not approved yet.',
  SKILL_REQUEST_HASHTAG_TOO_LONG: 'Hashtag is at most 30 characters.',
  SKILL_REQUEST_HASHTAG_INVALID: 'Hashtag contains only letters, digits, "-" and "_".',
  SKILL_REQUEST_HASHTAGS_TOO_MANY: 'Maximum 10 hashtags.',
  SKILL_REQUEST_RATE_LIMITED: 'Too many requests. Please try again later.',
  SKILL_REQUEST_FILE_INVALID_TYPE: 'File format is not supported.',
  SKILL_REQUEST_FILE_TOO_LARGE: 'File size is at most 10MB.',
  SKILL_REQUEST_FILE_NOT_FOUND: 'Skill request file does not exist.',
  SKILL_REQUEST_FILES_TOO_MANY: 'Maximum 10 attachments.',
  // Generic
  AUTH_401: 'Your session has expired. Please log in again.',
  FORBIDDEN: 'You do not have permission to perform this action.',
  NETWORK_ERROR: 'Unable to connect to the server. Please try again.',
  INTERNAL_ERROR: 'System error. Please try again later.',
}

export function errorMessage(code: string | null | undefined, fallback?: string): string {
  if (code && CATALOG[code]) return CATALOG[code]
  return fallback ?? 'Something went wrong. Please try again.'
}
