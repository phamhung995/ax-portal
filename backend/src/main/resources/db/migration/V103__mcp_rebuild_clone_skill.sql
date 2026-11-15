-- Rebuild MCP module: clone Skill model (2026-11-05, PO).
-- Drop old mcp_* tables + skill_mcp_dependency, recreate with the
-- Skill version model (master row = current version + pending metadata,
-- mcp_version = snapshot history). Visibility: PUBLIC only (no scope).
-- New features vs old MCP: comments, own taxonomy (mcp_category/mcp_stage).
--
-- NOTE (Flyway): V103 - checked flyway_schema_history: dev DB max version = 102.

DROP TABLE IF EXISTS mcp_master, mcp_version, mcp_version_file,
  mcp_files, mcp_comments, mcp_category, mcp_stage,
  mcp_category_mapping, mcp_stage_mapping,
  skill_mcp_dependency, mcp_subscriptions, mcp_status_history,
  mcp_ratings, mcp_download_log, mcp_view_log CASCADE;

-- ── Master (clone skills_master: no public_scope/system/share/cowork) ──
CREATE TABLE mcp_master (
  id BIGSERIAL PRIMARY KEY,
  mcp_code VARCHAR(50) NOT NULL,
  mcp_name VARCHAR(200) NOT NULL,
  server_key VARCHAR(100) NOT NULL,
  description TEXT,
  status VARCHAR(20) NOT NULL DEFAULT 'DRAFT',
  rejection_reason TEXT,
  hashtags JSONB,
  view_count INTEGER NOT NULL DEFAULT 0,
  version INTEGER NOT NULL DEFAULT 1,
  pending_metadata JSONB,
  pending_remove_file_ids JSONB,
  deleted_yn VARCHAR(1) NOT NULL DEFAULT 'N',
  created_by BIGINT NOT NULL,
  created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
  updated_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP
);
CREATE UNIQUE INDEX uq_mcp_master_code ON mcp_master (mcp_code);
CREATE UNIQUE INDEX uq_mcp_master_server_key ON mcp_master (server_key);
CREATE INDEX idx_mcp_master_status ON mcp_master (status);
CREATE INDEX idx_mcp_master_created_by ON mcp_master (created_by);

-- ── Version snapshot (clone skill_version) ──
CREATE TABLE mcp_version (
  id BIGSERIAL PRIMARY KEY,
  mcp_id BIGINT NOT NULL REFERENCES mcp_master (id),
  version INTEGER NOT NULL,
  name VARCHAR(200) NOT NULL,
  description TEXT,
  hashtags JSONB,
  created_by BIGINT NOT NULL,
  created_at TIMESTAMPTZ NOT NULL,
  UNIQUE (mcp_id, version)
);
CREATE INDEX idx_mcp_version_mcp ON mcp_version (mcp_id);

-- ── Version snapshot files (clone skill_version_file) ──
CREATE TABLE mcp_version_file (
  id BIGSERIAL PRIMARY KEY,
  mcp_version_id BIGINT NOT NULL REFERENCES mcp_version (id),
  file_name VARCHAR(255) NOT NULL,
  file_path VARCHAR(500) NOT NULL,
  file_size BIGINT
);

-- ── Files: current + pending staged (clone skill_files) ──
CREATE TABLE mcp_files (
  id BIGSERIAL PRIMARY KEY,
  mcp_id BIGINT NOT NULL REFERENCES mcp_master (id),
  file_name VARCHAR(255) NOT NULL,
  file_path VARCHAR(500) NOT NULL,
  file_size BIGINT,
  created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
  pending_yn VARCHAR(1) NOT NULL DEFAULT 'N'
);
CREATE INDEX idx_mcp_files_mcp ON mcp_files (mcp_id);

-- ── Comments (clone skill_comments) ──
CREATE TABLE mcp_comments (
  id BIGSERIAL PRIMARY KEY,
  mcp_id BIGINT NOT NULL REFERENCES mcp_master (id),
  user_id BIGINT NOT NULL,
  content TEXT NOT NULL,
  created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
  deleted_at TIMESTAMPTZ
);
CREATE INDEX idx_mcp_comments_mcp ON mcp_comments (mcp_id);

-- ── Ratings (clone skill_ratings) ──
CREATE TABLE mcp_ratings (
  id BIGSERIAL PRIMARY KEY,
  mcp_id BIGINT NOT NULL REFERENCES mcp_master (id),
  user_id BIGINT NOT NULL,
  rating NUMERIC(3,1) NOT NULL,
  created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
  UNIQUE (mcp_id, user_id)
);

-- ── Own taxonomy (clone skill_category/process_stage) ──
CREATE TABLE mcp_category (
  id BIGSERIAL PRIMARY KEY,
  name VARCHAR(100) NOT NULL,
  created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP
);
CREATE TABLE mcp_stage (
  id BIGSERIAL PRIMARY KEY,
  name VARCHAR(100) NOT NULL,
  sort_order INTEGER NOT NULL DEFAULT 0,
  description VARCHAR(255),
  created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP
);
CREATE TABLE mcp_category_mapping (
  mcp_id BIGINT NOT NULL REFERENCES mcp_master (id),
  category_id BIGINT NOT NULL REFERENCES mcp_category (id),
  PRIMARY KEY (mcp_id, category_id)
);
CREATE TABLE mcp_stage_mapping (
  mcp_id BIGINT NOT NULL REFERENCES mcp_master (id),
  stage_id BIGINT NOT NULL REFERENCES mcp_stage (id),
  PRIMARY KEY (mcp_id, stage_id)
);

-- ── Subscriptions / history / logs (clone skill equivalents) ──
CREATE TABLE mcp_subscriptions (
  mcp_id BIGINT NOT NULL REFERENCES mcp_master (id),
  user_id BIGINT NOT NULL,
  created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
  PRIMARY KEY (mcp_id, user_id)
);
CREATE TABLE mcp_status_history (
  id BIGSERIAL PRIMARY KEY,
  mcp_id BIGINT NOT NULL,
  from_status VARCHAR(20),
  to_status VARCHAR(20) NOT NULL,
  changed_by BIGINT NOT NULL,
  rejection_reason TEXT,
  changed_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP
);
CREATE INDEX idx_mcp_status_history_mcp ON mcp_status_history (mcp_id);
CREATE TABLE mcp_download_log (
  id BIGSERIAL PRIMARY KEY,
  mcp_id BIGINT NOT NULL,
  user_id BIGINT NOT NULL,
  downloaded_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
  download_all BOOLEAN NOT NULL DEFAULT FALSE
);
CREATE TABLE mcp_view_log (
  id BIGSERIAL PRIMARY KEY,
  mcp_id BIGINT NOT NULL,
  user_id BIGINT NOT NULL,
  viewed_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP
);

-- ── Skill -> MCP dependency (recreated, same structure) ──
CREATE TABLE skill_mcp_dependency (
  id BIGSERIAL PRIMARY KEY,
  skill_id BIGINT NOT NULL,
  mcp_id BIGINT NOT NULL REFERENCES mcp_master (id),
  mcp_code VARCHAR(50) NOT NULL,
  mcp_server_key VARCHAR(100) NOT NULL,
  mcp_version_number INTEGER NOT NULL,
  required BOOLEAN NOT NULL DEFAULT FALSE,
  source VARCHAR(20) NOT NULL,
  UNIQUE (skill_id, mcp_id)
);
CREATE INDEX idx_skill_mcp_dep_mcp ON skill_mcp_dependency (mcp_id);

-- ── i18n: drop old app.mcp.* keys, recreate (cloned from app.skill(s).*;
-- keys without a skill source keep their previous values) ──
DELETE FROM translations WHERE key_id IN (SELECT id FROM translation_keys WHERE key LIKE 'app.mcp.%');
DELETE FROM translation_keys WHERE key LIKE 'app.mcp.%';

INSERT INTO translation_keys (key, description) VALUES
    ('app.mcp.addBtn', NULL),
    ('app.mcp.addTitle', NULL),
    ('app.mcp.adminBadge', NULL),
    ('app.mcp.adminOnlyCat', NULL),
    ('app.mcp.adminOnlyStage', NULL),
    ('app.mcp.allStatuses', NULL),
    ('app.mcp.approve', NULL),
    ('app.mcp.approveVersion', NULL),
    ('app.mcp.approved', NULL),
    ('app.mcp.approvedFilesHint', NULL),
    ('app.mcp.approvedMcpTab', NULL),
    ('app.mcp.approvedToast', NULL),
    ('app.mcp.browseTitle', NULL),
    ('app.mcp.cancel', NULL),
    ('app.mcp.cancelUpdateBtn', NULL),
    ('app.mcp.cancelUpdateDesc', NULL),
    ('app.mcp.cancelUpdateTitle', NULL),
    ('app.mcp.cancelVersionUpdate', NULL),
    ('app.mcp.cannotDeleteItem', NULL),
    ('app.mcp.cannotDeleteList', NULL),
    ('app.mcp.cannotDeleteTitle', NULL),
    ('app.mcp.cannotDeleteTotal', NULL),
    ('app.mcp.cardsAria', NULL),
    ('app.mcp.catAdded', NULL),
    ('app.mcp.catDeleteDesc', NULL),
    ('app.mcp.catDeleteDescSimple', NULL),
    ('app.mcp.catDeleted', NULL),
    ('app.mcp.catUpdated', NULL),
    ('app.mcp.categories', NULL),
    ('app.mcp.categoryName', NULL),
    ('app.mcp.changelog', NULL),
    ('app.mcp.changelogPh', NULL),
    ('app.mcp.changelogRequired', NULL),
    ('app.mcp.changelogTooLong', NULL),
    ('app.mcp.clearCreator', NULL),
    ('app.mcp.close', NULL),
    ('app.mcp.colCreated', NULL),
    ('app.mcp.colCreator', NULL),
    ('app.mcp.colDescription', NULL),
    ('app.mcp.colDownloads', NULL),
    ('app.mcp.colName', NULL),
    ('app.mcp.colRating', NULL),
    ('app.mcp.colStatus', NULL),
    ('app.mcp.colSubscribe', NULL),
    ('app.mcp.colViews', NULL),
    ('app.mcp.confirm', NULL),
    ('app.mcp.copyLink', NULL),
    ('app.mcp.copyLinkCopied', NULL),
    ('app.mcp.copyLinkFailed', NULL),
    ('app.mcp.copyLinkTooltip', NULL),
    ('app.mcp.createVersion', NULL),
    ('app.mcp.createdToast', NULL),
    ('app.mcp.createdUpdated', NULL),
    ('app.mcp.creator', NULL),
    ('app.mcp.current', NULL),
    ('app.mcp.currentFile', NULL),
    ('app.mcp.deleteBtn', NULL),
    ('app.mcp.deleteCategoryAria', NULL),
    ('app.mcp.deleteCommentAria', NULL),
    ('app.mcp.deleteDeniedDesc', NULL),
    ('app.mcp.deleteDeniedList', NULL),
    ('app.mcp.deleteDeniedTitle', NULL),
    ('app.mcp.deleteDeniedTotal', NULL),
    ('app.mcp.deleteDesc', NULL),
    ('app.mcp.deleteStageAria', NULL),
    ('app.mcp.deleteTitle', NULL),
    ('app.mcp.deletedToast', NULL),
    ('app.mcp.deletedToastDetail', NULL),
    ('app.mcp.depAlready', NULL),
    ('app.mcp.depBanner', NULL),
    ('app.mcp.depDeleted', NULL),
    ('app.mcp.depDeletedTip', NULL),
    ('app.mcp.depLabel', NULL),
    ('app.mcp.depLoading', NULL),
    ('app.mcp.depPh', NULL),
    ('app.mcp.dependentSkills', NULL),
    ('app.mcp.descRequired', NULL),
    ('app.mcp.description', NULL),
    ('app.mcp.descriptionPh', NULL),
    ('app.mcp.detailTitle', NULL),
    ('app.mcp.download', NULL),
    ('app.mcp.downloadAll', NULL),
    ('app.mcp.downloadCount', NULL),
    ('app.mcp.downloadExcel', NULL),
    ('app.mcp.draft', NULL),
    ('app.mcp.draftHint', NULL),
    ('app.mcp.draftNote', NULL),
    ('app.mcp.dragDropNewOr', NULL),
    ('app.mcp.editBtn', NULL),
    ('app.mcp.editCategoryAria', NULL),
    ('app.mcp.editCategoryTitle', NULL),
    ('app.mcp.editNoteApproved', NULL),
    ('app.mcp.editNoteStatus', NULL),
    ('app.mcp.editStageAria', NULL),
    ('app.mcp.editStageTitle', NULL),
    ('app.mcp.editTitle', NULL),
    ('app.mcp.emptyDesc', NULL),
    ('app.mcp.emptyTitle', NULL),
    ('app.mcp.exportedToast', NULL),
    ('app.mcp.file', NULL),
    ('app.mcp.fileDropOr', NULL),
    ('app.mcp.fileErrorFormat', NULL),
    ('app.mcp.fileErrorSize', NULL),
    ('app.mcp.fileKeepHint', NULL),
    ('app.mcp.fileRemoved', NULL),
    ('app.mcp.fileRequired', NULL),
    ('app.mcp.fileRequiredUpload', NULL),
    ('app.mcp.fileSupported', NULL),
    ('app.mcp.filesToRemove', NULL),
    ('app.mcp.hashtag', NULL),
    ('app.mcp.hashtagExists', NULL),
    ('app.mcp.hashtagPh', NULL),
    ('app.mcp.hashtagRemoveAria', NULL),
    ('app.mcp.lastRejected', NULL),
    ('app.mcp.listAria', NULL),
    ('app.mcp.loadError', NULL),
    ('app.mcp.loadErrorDetail', NULL),
    ('app.mcp.manageTaxonomy', NULL),
    ('app.mcp.manageTaxonomyTitle', NULL),
    ('app.mcp.mcpCode', NULL),
    ('app.mcp.mcpCount', NULL),
    ('app.mcp.mcpDeleted', NULL),
    ('app.mcp.mcpDeletedTip', NULL),
    ('app.mcp.mcpDepsWarnDesc', NULL),
    ('app.mcp.mcpDepsWarnNote', NULL),
    ('app.mcp.mcpDepsWarnTitle', NULL),
    ('app.mcp.mcpUnavailableGone', NULL),
    ('app.mcp.mcpUnavailableGoneWarn', NULL),
    ('app.mcp.mcpUnavailableNotApproved', NULL),
    ('app.mcp.mcpUnavailableNotApprovedWarn', NULL),
    ('app.mcp.myMcpTab', NULL),
    ('app.mcp.myMcpsEmpty', NULL),
    ('app.mcp.name', NULL),
    ('app.mcp.nameEmpty', NULL),
    ('app.mcp.nameLocked', NULL),
    ('app.mcp.namePh', NULL),
    ('app.mcp.nameTooLong', NULL),
    ('app.mcp.newCategoryPh', NULL),
    ('app.mcp.newFiles', NULL),
    ('app.mcp.newMcpDraftHint', NULL),
    ('app.mcp.newStagePh', NULL),
    ('app.mcp.noCategoriesYet', NULL),
    ('app.mcp.noComments', NULL),
    ('app.mcp.noDeps', NULL),
    ('app.mcp.noDescription', NULL),
    ('app.mcp.noMatchDesc', NULL),
    ('app.mcp.noOptions', NULL),
    ('app.mcp.noRating', NULL),
    ('app.mcp.noSkills', NULL),
    ('app.mcp.noStagesYet', NULL),
    ('app.mcp.noSuggestions', NULL),
    ('app.mcp.noUsersSelected', NULL),
    ('app.mcp.noVersions', NULL),
    ('app.mcp.notFoundOrNoAccess', NULL),
    ('app.mcp.onlyApprovedComment', NULL),
    ('app.mcp.onlyApprovedRate', NULL),
    ('app.mcp.openListAria', NULL),
    ('app.mcp.optional', NULL),
    ('app.mcp.pending', NULL),
    ('app.mcp.pendingBanner', NULL),
    ('app.mcp.pendingVersion', NULL),
    ('app.mcp.pendingVersionBanner', NULL),
    ('app.mcp.pinBlockedArchived', NULL),
    ('app.mcp.pinBlockedDraft', NULL),
    ('app.mcp.pinBlockedPending', NULL),
    ('app.mcp.pinBlockedRejected', NULL),
    ('app.mcp.processStages', NULL),
    ('app.mcp.rateOnlyApproved', NULL),
    ('app.mcp.rateStars', NULL),
    ('app.mcp.reject', NULL),
    ('app.mcp.rejectReasonPh', NULL),
    ('app.mcp.rejectReasonRequired', NULL),
    ('app.mcp.rejectTitle', NULL),
    ('app.mcp.rejectVersion', NULL),
    ('app.mcp.rejected', NULL),
    ('app.mcp.rejectedToast', NULL),
    ('app.mcp.rejectionReason', NULL),
    ('app.mcp.removeDepAria', NULL),
    ('app.mcp.reuseFile', NULL),
    ('app.mcp.reviewSubmitted', NULL),
    ('app.mcp.save', NULL),
    ('app.mcp.saveDraft', NULL),
    ('app.mcp.search', NULL),
    ('app.mcp.searchByName', NULL),
    ('app.mcp.searchTitle', NULL),
    ('app.mcp.searchUserDesc', NULL),
    ('app.mcp.selectCreator', NULL),
    ('app.mcp.selected', NULL),
    ('app.mcp.selectedCount', NULL),
    ('app.mcp.send', NULL),
    ('app.mcp.serverKey', NULL),
    ('app.mcp.showMore', NULL),
    ('app.mcp.skillsTab', NULL),
    ('app.mcp.stageAdded', NULL),
    ('app.mcp.stageDeleteDesc', NULL),
    ('app.mcp.stageDeleteDescSimple', NULL),
    ('app.mcp.stageDeleted', NULL),
    ('app.mcp.stageDescLabel', NULL),
    ('app.mcp.stageDescPh', NULL),
    ('app.mcp.stageName', NULL),
    ('app.mcp.stageUpdated', NULL),
    ('app.mcp.status', NULL),
    ('app.mcp.submit', NULL),
    ('app.mcp.submitForReview', NULL),
    ('app.mcp.submittedToast', NULL),
    ('app.mcp.subscribe', NULL),
    ('app.mcp.subscribeAll', NULL),
    ('app.mcp.subscribeN', NULL),
    ('app.mcp.subscribeToast', NULL),
    ('app.mcp.subscribeY', NULL),
    ('app.mcp.subscribed', NULL),
    ('app.mcp.subscribedToast', NULL),
    ('app.mcp.tabComments', NULL),
    ('app.mcp.tabInfo', NULL),
    ('app.mcp.tabSkills', NULL),
    ('app.mcp.tabVersions', NULL),
    ('app.mcp.title', NULL),
    ('app.mcp.underReview', NULL),
    ('app.mcp.unsubscribedToast', NULL),
    ('app.mcp.updateFileRequired', NULL),
    ('app.mcp.updateFilesHint', NULL),
    ('app.mcp.updateHint', NULL),
    ('app.mcp.updateInfo', NULL),
    ('app.mcp.updateInfoPh', NULL),
    ('app.mcp.updateInfoRequired', NULL),
    ('app.mcp.updateInfoTooLong', NULL),
    ('app.mcp.updateSubmitted', NULL),
    ('app.mcp.updateTitle', NULL),
    ('app.mcp.updateVersion', NULL),
    ('app.mcp.updated', NULL),
    ('app.mcp.updatedToast', NULL),
    ('app.mcp.uploadNewFile', NULL),
    ('app.mcp.version', NULL),
    ('app.mcp.versionApproved', NULL),
    ('app.mcp.versionCancelled', NULL),
    ('app.mcp.versionConfirmDesc', NULL),
    ('app.mcp.versionConfirmTitle', NULL),
    ('app.mcp.versionCreatedToast', NULL),
    ('app.mcp.versionInfo', NULL),
    ('app.mcp.versionNote', NULL),
    ('app.mcp.versionNum', NULL),
    ('app.mcp.versionRejected', NULL),
    ('app.mcp.viewCount', NULL),
    ('app.mcp.withdraw', NULL),
    ('app.mcp.withdrawDesc', NULL),
    ('app.mcp.withdrawTitle', NULL),
    ('app.mcp.withdrawn', NULL),
    ('app.mcp.withdrawnToast', NULL),
    ('app.mcp.writeComment', NULL),
    ('app.mcp.youRate', NULL);

-- en
INSERT INTO translations (key_id, language, value)
SELECT k.id, 'en',
  CASE
    WHEN k.key = 'app.mcp.addBtn' THEN (SELECT value FROM translations t2 JOIN translation_keys k2 ON k2.id = t2.key_id WHERE k2.key = 'app.skills.addBtn' AND t2.language = 'en')
    WHEN k.key = 'app.mcp.addTitle' THEN (SELECT value FROM translations t2 JOIN translation_keys k2 ON k2.id = t2.key_id WHERE k2.key = 'app.skills.addTitle' AND t2.language = 'en')
    WHEN k.key = 'app.mcp.adminBadge' THEN (SELECT value FROM translations t2 JOIN translation_keys k2 ON k2.id = t2.key_id WHERE k2.key = 'app.skills.adminBadge' AND t2.language = 'en')
    WHEN k.key = 'app.mcp.adminOnlyCat' THEN (SELECT value FROM translations t2 JOIN translation_keys k2 ON k2.id = t2.key_id WHERE k2.key = 'app.skills.adminOnlyCat' AND t2.language = 'en')
    WHEN k.key = 'app.mcp.adminOnlyStage' THEN (SELECT value FROM translations t2 JOIN translation_keys k2 ON k2.id = t2.key_id WHERE k2.key = 'app.skills.adminOnlyStage' AND t2.language = 'en')
    WHEN k.key = 'app.mcp.allStatuses' THEN 'All Statuses'
    WHEN k.key = 'app.mcp.approve' THEN (SELECT value FROM translations t2 JOIN translation_keys k2 ON k2.id = t2.key_id WHERE k2.key = 'app.skills.approve' AND t2.language = 'en')
    WHEN k.key = 'app.mcp.approveVersion' THEN (SELECT value FROM translations t2 JOIN translation_keys k2 ON k2.id = t2.key_id WHERE k2.key = 'app.skills.approveVersion' AND t2.language = 'en')
    WHEN k.key = 'app.mcp.approved' THEN 'Approved'
    WHEN k.key = 'app.mcp.approvedFilesHint' THEN (SELECT value FROM translations t2 JOIN translation_keys k2 ON k2.id = t2.key_id WHERE k2.key = 'app.skills.approvedFilesHint' AND t2.language = 'en')
    WHEN k.key = 'app.mcp.approvedMcpTab' THEN 'Approved MCPs ({n})'
    WHEN k.key = 'app.mcp.approvedToast' THEN 'MCP approved.'
    WHEN k.key = 'app.mcp.browseTitle' THEN 'Browse All MCPs'
    WHEN k.key = 'app.mcp.cancel' THEN 'Cancel'
    WHEN k.key = 'app.mcp.cancelUpdateBtn' THEN (SELECT value FROM translations t2 JOIN translation_keys k2 ON k2.id = t2.key_id WHERE k2.key = 'app.skills.cancelUpdateBtn' AND t2.language = 'en')
    WHEN k.key = 'app.mcp.cancelUpdateDesc' THEN (SELECT value FROM translations t2 JOIN translation_keys k2 ON k2.id = t2.key_id WHERE k2.key = 'app.skills.cancelUpdateDesc' AND t2.language = 'en')
    WHEN k.key = 'app.mcp.cancelUpdateTitle' THEN (SELECT value FROM translations t2 JOIN translation_keys k2 ON k2.id = t2.key_id WHERE k2.key = 'app.skills.cancelUpdateTitle' AND t2.language = 'en')
    WHEN k.key = 'app.mcp.cancelVersionUpdate' THEN (SELECT value FROM translations t2 JOIN translation_keys k2 ON k2.id = t2.key_id WHERE k2.key = 'app.skills.cancelVersionUpdate' AND t2.language = 'en')
    WHEN k.key = 'app.mcp.cannotDeleteItem' THEN (SELECT value FROM translations t2 JOIN translation_keys k2 ON k2.id = t2.key_id WHERE k2.key = 'app.skills.cannotDeleteItem' AND t2.language = 'en')
    WHEN k.key = 'app.mcp.cannotDeleteList' THEN (SELECT value FROM translations t2 JOIN translation_keys k2 ON k2.id = t2.key_id WHERE k2.key = 'app.skills.cannotDeleteList' AND t2.language = 'en')
    WHEN k.key = 'app.mcp.cannotDeleteTitle' THEN (SELECT value FROM translations t2 JOIN translation_keys k2 ON k2.id = t2.key_id WHERE k2.key = 'app.skills.cannotDeleteTitle' AND t2.language = 'en')
    WHEN k.key = 'app.mcp.cannotDeleteTotal' THEN (SELECT value FROM translations t2 JOIN translation_keys k2 ON k2.id = t2.key_id WHERE k2.key = 'app.skills.cannotDeleteTotal' AND t2.language = 'en')
    WHEN k.key = 'app.mcp.cardsAria' THEN (SELECT value FROM translations t2 JOIN translation_keys k2 ON k2.id = t2.key_id WHERE k2.key = 'app.skills.cardsAria' AND t2.language = 'en')
    WHEN k.key = 'app.mcp.catAdded' THEN (SELECT value FROM translations t2 JOIN translation_keys k2 ON k2.id = t2.key_id WHERE k2.key = 'app.skills.catAdded' AND t2.language = 'en')
    WHEN k.key = 'app.mcp.catDeleteDesc' THEN (SELECT value FROM translations t2 JOIN translation_keys k2 ON k2.id = t2.key_id WHERE k2.key = 'app.skills.catDeleteDesc' AND t2.language = 'en')
    WHEN k.key = 'app.mcp.catDeleteDescSimple' THEN (SELECT value FROM translations t2 JOIN translation_keys k2 ON k2.id = t2.key_id WHERE k2.key = 'app.skills.catDeleteDescSimple' AND t2.language = 'en')
    WHEN k.key = 'app.mcp.catDeleted' THEN (SELECT value FROM translations t2 JOIN translation_keys k2 ON k2.id = t2.key_id WHERE k2.key = 'app.skills.catDeleted' AND t2.language = 'en')
    WHEN k.key = 'app.mcp.catUpdated' THEN (SELECT value FROM translations t2 JOIN translation_keys k2 ON k2.id = t2.key_id WHERE k2.key = 'app.skills.catUpdated' AND t2.language = 'en')
    WHEN k.key = 'app.mcp.categories' THEN (SELECT value FROM translations t2 JOIN translation_keys k2 ON k2.id = t2.key_id WHERE k2.key = 'app.skills.categories' AND t2.language = 'en')
    WHEN k.key = 'app.mcp.categoryName' THEN (SELECT value FROM translations t2 JOIN translation_keys k2 ON k2.id = t2.key_id WHERE k2.key = 'app.skills.categoryName' AND t2.language = 'en')
    WHEN k.key = 'app.mcp.changelog' THEN 'Changelog'
    WHEN k.key = 'app.mcp.changelogPh' THEN 'Describe what changed in this version...'
    WHEN k.key = 'app.mcp.changelogRequired' THEN 'Changelog is required for a new version.'
    WHEN k.key = 'app.mcp.changelogTooLong' THEN 'Changelog is at most {n} characters.'
    WHEN k.key = 'app.mcp.clearCreator' THEN (SELECT value FROM translations t2 JOIN translation_keys k2 ON k2.id = t2.key_id WHERE k2.key = 'app.skills.clearCreator' AND t2.language = 'en')
    WHEN k.key = 'app.mcp.close' THEN 'Close'
    WHEN k.key = 'app.mcp.colCreated' THEN 'Created Date'
    WHEN k.key = 'app.mcp.colCreator' THEN (SELECT value FROM translations t2 JOIN translation_keys k2 ON k2.id = t2.key_id WHERE k2.key = 'app.skills.colCreator' AND t2.language = 'en')
    WHEN k.key = 'app.mcp.colDescription' THEN 'Description'
    WHEN k.key = 'app.mcp.colDownloads' THEN (SELECT value FROM translations t2 JOIN translation_keys k2 ON k2.id = t2.key_id WHERE k2.key = 'app.skills.colDownloads' AND t2.language = 'en')
    WHEN k.key = 'app.mcp.colName' THEN (SELECT value FROM translations t2 JOIN translation_keys k2 ON k2.id = t2.key_id WHERE k2.key = 'app.skills.colName' AND t2.language = 'en')
    WHEN k.key = 'app.mcp.colRating' THEN (SELECT value FROM translations t2 JOIN translation_keys k2 ON k2.id = t2.key_id WHERE k2.key = 'app.skills.colRating' AND t2.language = 'en')
    WHEN k.key = 'app.mcp.colStatus' THEN 'Status'
    WHEN k.key = 'app.mcp.colSubscribe' THEN (SELECT value FROM translations t2 JOIN translation_keys k2 ON k2.id = t2.key_id WHERE k2.key = 'app.skills.colSubscribe' AND t2.language = 'en')
    WHEN k.key = 'app.mcp.colViews' THEN (SELECT value FROM translations t2 JOIN translation_keys k2 ON k2.id = t2.key_id WHERE k2.key = 'app.skills.colViews' AND t2.language = 'en')
    WHEN k.key = 'app.mcp.confirm' THEN 'Confirm'
    WHEN k.key = 'app.mcp.copyLink' THEN (SELECT value FROM translations t2 JOIN translation_keys k2 ON k2.id = t2.key_id WHERE k2.key = 'app.skill.copyLink' AND t2.language = 'en')
    WHEN k.key = 'app.mcp.copyLinkCopied' THEN (SELECT value FROM translations t2 JOIN translation_keys k2 ON k2.id = t2.key_id WHERE k2.key = 'app.skill.copyLinkCopied' AND t2.language = 'en')
    WHEN k.key = 'app.mcp.copyLinkFailed' THEN (SELECT value FROM translations t2 JOIN translation_keys k2 ON k2.id = t2.key_id WHERE k2.key = 'app.skill.copyLinkFailed' AND t2.language = 'en')
    WHEN k.key = 'app.mcp.copyLinkTooltip' THEN (SELECT value FROM translations t2 JOIN translation_keys k2 ON k2.id = t2.key_id WHERE k2.key = 'app.skill.copyLinkTooltip' AND t2.language = 'en')
    WHEN k.key = 'app.mcp.createVersion' THEN 'Create Version'
    WHEN k.key = 'app.mcp.createdToast' THEN (SELECT value FROM translations t2 JOIN translation_keys k2 ON k2.id = t2.key_id WHERE k2.key = 'app.skills.createdToast' AND t2.language = 'en')
    WHEN k.key = 'app.mcp.createdUpdated' THEN (SELECT value FROM translations t2 JOIN translation_keys k2 ON k2.id = t2.key_id WHERE k2.key = 'app.skills.createdUpdated' AND t2.language = 'en')
    WHEN k.key = 'app.mcp.creator' THEN (SELECT value FROM translations t2 JOIN translation_keys k2 ON k2.id = t2.key_id WHERE k2.key = 'app.skills.creator' AND t2.language = 'en')
    WHEN k.key = 'app.mcp.current' THEN (SELECT value FROM translations t2 JOIN translation_keys k2 ON k2.id = t2.key_id WHERE k2.key = 'app.skills.current' AND t2.language = 'en')
    WHEN k.key = 'app.mcp.currentFile' THEN 'Current file'
    WHEN k.key = 'app.mcp.deleteBtn' THEN (SELECT value FROM translations t2 JOIN translation_keys k2 ON k2.id = t2.key_id WHERE k2.key = 'app.skills.deleteBtn' AND t2.language = 'en')
    WHEN k.key = 'app.mcp.deleteCategoryAria' THEN (SELECT value FROM translations t2 JOIN translation_keys k2 ON k2.id = t2.key_id WHERE k2.key = 'app.skills.deleteCategoryAria' AND t2.language = 'en')
    WHEN k.key = 'app.mcp.deleteCommentAria' THEN (SELECT value FROM translations t2 JOIN translation_keys k2 ON k2.id = t2.key_id WHERE k2.key = 'app.skills.deleteCommentAria' AND t2.language = 'en')
    WHEN k.key = 'app.mcp.deleteDeniedDesc' THEN 'You do not have permission to delete one or more selected MCPs.'
    WHEN k.key = 'app.mcp.deleteDeniedList' THEN 'MCPs you cannot delete:'
    WHEN k.key = 'app.mcp.deleteDeniedTitle' THEN 'Cannot delete all selected MCPs'
    WHEN k.key = 'app.mcp.deleteDeniedTotal' THEN 'Total: {n} MCP(s) selected, {m} cannot be deleted.'
    WHEN k.key = 'app.mcp.deleteDesc' THEN (SELECT value FROM translations t2 JOIN translation_keys k2 ON k2.id = t2.key_id WHERE k2.key = 'app.skills.deleteDesc' AND t2.language = 'en')
    WHEN k.key = 'app.mcp.deleteStageAria' THEN (SELECT value FROM translations t2 JOIN translation_keys k2 ON k2.id = t2.key_id WHERE k2.key = 'app.skills.deleteStageAria' AND t2.language = 'en')
    WHEN k.key = 'app.mcp.deleteTitle' THEN (SELECT value FROM translations t2 JOIN translation_keys k2 ON k2.id = t2.key_id WHERE k2.key = 'app.skills.deleteTitle' AND t2.language = 'en')
    WHEN k.key = 'app.mcp.deletedToast' THEN (SELECT value FROM translations t2 JOIN translation_keys k2 ON k2.id = t2.key_id WHERE k2.key = 'app.skills.deletedToast' AND t2.language = 'en')
    WHEN k.key = 'app.mcp.deletedToastDetail' THEN (SELECT value FROM translations t2 JOIN translation_keys k2 ON k2.id = t2.key_id WHERE k2.key = 'app.skills.deletedToastDetail' AND t2.language = 'en')
    WHEN k.key = 'app.mcp.depAlready' THEN 'This MCP is already in the dependency list.'
    WHEN k.key = 'app.mcp.depBanner' THEN 'This skill cannot be submitted until the following MCPs are approved:'
    WHEN k.key = 'app.mcp.depDeleted' THEN 'Deleted'
    WHEN k.key = 'app.mcp.depDeletedTip' THEN 'This MCP has been deleted. The skill keeps working — you can remove this dependency.'
    WHEN k.key = 'app.mcp.depLabel' THEN 'MCP Dependencies'
    WHEN k.key = 'app.mcp.depLoading' THEN 'Loading suggestions...'
    WHEN k.key = 'app.mcp.depPh' THEN 'Select MCP dependencies...'
    WHEN k.key = 'app.mcp.dependentSkills' THEN NULL
    WHEN k.key = 'app.mcp.descRequired' THEN 'Description is required.'
    WHEN k.key = 'app.mcp.description' THEN 'Description'
    WHEN k.key = 'app.mcp.descriptionPh' THEN 'Enter MCP description and usage guide...'
    WHEN k.key = 'app.mcp.detailTitle' THEN (SELECT value FROM translations t2 JOIN translation_keys k2 ON k2.id = t2.key_id WHERE k2.key = 'app.skills.detailTitle' AND t2.language = 'en')
    WHEN k.key = 'app.mcp.download' THEN 'Download'
    WHEN k.key = 'app.mcp.downloadAll' THEN (SELECT value FROM translations t2 JOIN translation_keys k2 ON k2.id = t2.key_id WHERE k2.key = 'app.skills.downloadAll' AND t2.language = 'en')
    WHEN k.key = 'app.mcp.downloadCount' THEN 'Downloads: {n}'
    WHEN k.key = 'app.mcp.downloadExcel' THEN (SELECT value FROM translations t2 JOIN translation_keys k2 ON k2.id = t2.key_id WHERE k2.key = 'app.skills.downloadExcel' AND t2.language = 'en')
    WHEN k.key = 'app.mcp.draft' THEN 'Draft'
    WHEN k.key = 'app.mcp.draftHint' THEN (SELECT value FROM translations t2 JOIN translation_keys k2 ON k2.id = t2.key_id WHERE k2.key = 'app.skills.draftHint' AND t2.language = 'en')
    WHEN k.key = 'app.mcp.draftNote' THEN 'The MCP is created in Draft status. You can edit it and submit for approval when ready.'
    WHEN k.key = 'app.mcp.dragDropNewOr' THEN (SELECT value FROM translations t2 JOIN translation_keys k2 ON k2.id = t2.key_id WHERE k2.key = 'app.skills.dragDropNewOr' AND t2.language = 'en')
    WHEN k.key = 'app.mcp.editBtn' THEN (SELECT value FROM translations t2 JOIN translation_keys k2 ON k2.id = t2.key_id WHERE k2.key = 'app.skills.editBtn' AND t2.language = 'en')
    WHEN k.key = 'app.mcp.editCategoryAria' THEN (SELECT value FROM translations t2 JOIN translation_keys k2 ON k2.id = t2.key_id WHERE k2.key = 'app.skills.editCategoryAria' AND t2.language = 'en')
    WHEN k.key = 'app.mcp.editCategoryTitle' THEN (SELECT value FROM translations t2 JOIN translation_keys k2 ON k2.id = t2.key_id WHERE k2.key = 'app.skills.editCategoryTitle' AND t2.language = 'en')
    WHEN k.key = 'app.mcp.editNoteApproved' THEN 'This MCP is approved. Name, description and hashtags are updated immediately without re-approval.'
    WHEN k.key = 'app.mcp.editNoteStatus' THEN 'You are editing an MCP in {status} status. Changes stay in the same status.'
    WHEN k.key = 'app.mcp.editStageAria' THEN (SELECT value FROM translations t2 JOIN translation_keys k2 ON k2.id = t2.key_id WHERE k2.key = 'app.skills.editStageAria' AND t2.language = 'en')
    WHEN k.key = 'app.mcp.editStageTitle' THEN (SELECT value FROM translations t2 JOIN translation_keys k2 ON k2.id = t2.key_id WHERE k2.key = 'app.skills.editStageTitle' AND t2.language = 'en')
    WHEN k.key = 'app.mcp.editTitle' THEN (SELECT value FROM translations t2 JOIN translation_keys k2 ON k2.id = t2.key_id WHERE k2.key = 'app.skills.editTitle' AND t2.language = 'en')
    WHEN k.key = 'app.mcp.emptyDesc' THEN (SELECT value FROM translations t2 JOIN translation_keys k2 ON k2.id = t2.key_id WHERE k2.key = 'app.skills.emptyDesc' AND t2.language = 'en')
    WHEN k.key = 'app.mcp.emptyTitle' THEN 'No data found.'
    WHEN k.key = 'app.mcp.exportedToast' THEN (SELECT value FROM translations t2 JOIN translation_keys k2 ON k2.id = t2.key_id WHERE k2.key = 'app.skills.exportedToast' AND t2.language = 'en')
    WHEN k.key = 'app.mcp.file' THEN 'File'
    WHEN k.key = 'app.mcp.fileDropOr' THEN 'Drag & drop file here or'
    WHEN k.key = 'app.mcp.fileErrorFormat' THEN '"{name}": file format is not supported. Allowed: {exts}.'
    WHEN k.key = 'app.mcp.fileErrorSize' THEN '"{name}": file size is at most 50MB.'
    WHEN k.key = 'app.mcp.fileKeepHint' THEN 'Keep the current file or upload a new one.'
    WHEN k.key = 'app.mcp.fileRemoved' THEN (SELECT value FROM translations t2 JOIN translation_keys k2 ON k2.id = t2.key_id WHERE k2.key = 'app.skills.fileRemoved' AND t2.language = 'en')
    WHEN k.key = 'app.mcp.fileRequired' THEN (SELECT value FROM translations t2 JOIN translation_keys k2 ON k2.id = t2.key_id WHERE k2.key = 'app.skills.fileRequired' AND t2.language = 'en')
    WHEN k.key = 'app.mcp.fileRequiredUpload' THEN 'File is required when uploading a new file.'
    WHEN k.key = 'app.mcp.fileSupported' THEN 'Supported: zip, 7z, json, yaml, yml, txt, md · max 50MB'
    WHEN k.key = 'app.mcp.filesToRemove' THEN (SELECT value FROM translations t2 JOIN translation_keys k2 ON k2.id = t2.key_id WHERE k2.key = 'app.skills.filesToRemove' AND t2.language = 'en')
    WHEN k.key = 'app.mcp.hashtag' THEN 'Hashtag'
    WHEN k.key = 'app.mcp.hashtagExists' THEN 'Hashtag already exists.'
    WHEN k.key = 'app.mcp.hashtagPh' THEN 'Type a hashtag and press Enter...'
    WHEN k.key = 'app.mcp.hashtagRemoveAria' THEN 'Remove hashtag'
    WHEN k.key = 'app.mcp.lastRejected' THEN (SELECT value FROM translations t2 JOIN translation_keys k2 ON k2.id = t2.key_id WHERE k2.key = 'app.skills.lastRejected' AND t2.language = 'en')
    WHEN k.key = 'app.mcp.listAria' THEN (SELECT value FROM translations t2 JOIN translation_keys k2 ON k2.id = t2.key_id WHERE k2.key = 'app.skills.listAria' AND t2.language = 'en')
    WHEN k.key = 'app.mcp.loadError' THEN (SELECT value FROM translations t2 JOIN translation_keys k2 ON k2.id = t2.key_id WHERE k2.key = 'app.skills.loadError' AND t2.language = 'en')
    WHEN k.key = 'app.mcp.loadErrorDetail' THEN (SELECT value FROM translations t2 JOIN translation_keys k2 ON k2.id = t2.key_id WHERE k2.key = 'app.skills.loadErrorDetail' AND t2.language = 'en')
    WHEN k.key = 'app.mcp.manageTaxonomy' THEN (SELECT value FROM translations t2 JOIN translation_keys k2 ON k2.id = t2.key_id WHERE k2.key = 'app.skills.manageTaxonomy' AND t2.language = 'en')
    WHEN k.key = 'app.mcp.manageTaxonomyTitle' THEN (SELECT value FROM translations t2 JOIN translation_keys k2 ON k2.id = t2.key_id WHERE k2.key = 'app.skills.manageTaxonomyTitle' AND t2.language = 'en')
    WHEN k.key = 'app.mcp.mcpCode' THEN NULL
    WHEN k.key = 'app.mcp.mcpCount' THEN (SELECT value FROM translations t2 JOIN translation_keys k2 ON k2.id = t2.key_id WHERE k2.key = 'app.skills.skillCount' AND t2.language = 'en')
    WHEN k.key = 'app.mcp.mcpDeleted' THEN (SELECT value FROM translations t2 JOIN translation_keys k2 ON k2.id = t2.key_id WHERE k2.key = 'app.skills.mcpDeleted' AND t2.language = 'en')
    WHEN k.key = 'app.mcp.mcpDeletedTip' THEN (SELECT value FROM translations t2 JOIN translation_keys k2 ON k2.id = t2.key_id WHERE k2.key = 'app.skills.mcpDeletedTip' AND t2.language = 'en')
    WHEN k.key = 'app.mcp.mcpDepsWarnDesc' THEN (SELECT value FROM translations t2 JOIN translation_keys k2 ON k2.id = t2.key_id WHERE k2.key = 'app.skills.mcpDepsWarnDesc' AND t2.language = 'en')
    WHEN k.key = 'app.mcp.mcpDepsWarnNote' THEN (SELECT value FROM translations t2 JOIN translation_keys k2 ON k2.id = t2.key_id WHERE k2.key = 'app.skills.mcpDepsWarnNote' AND t2.language = 'en')
    WHEN k.key = 'app.mcp.mcpDepsWarnTitle' THEN (SELECT value FROM translations t2 JOIN translation_keys k2 ON k2.id = t2.key_id WHERE k2.key = 'app.skills.mcpDepsWarnTitle' AND t2.language = 'en')
    WHEN k.key = 'app.mcp.mcpUnavailableGone' THEN (SELECT value FROM translations t2 JOIN translation_keys k2 ON k2.id = t2.key_id WHERE k2.key = 'app.skills.mcpUnavailableGone' AND t2.language = 'en')
    WHEN k.key = 'app.mcp.mcpUnavailableGoneWarn' THEN (SELECT value FROM translations t2 JOIN translation_keys k2 ON k2.id = t2.key_id WHERE k2.key = 'app.skills.mcpUnavailableGoneWarn' AND t2.language = 'en')
    WHEN k.key = 'app.mcp.mcpUnavailableNotApproved' THEN (SELECT value FROM translations t2 JOIN translation_keys k2 ON k2.id = t2.key_id WHERE k2.key = 'app.skills.mcpUnavailableNotApproved' AND t2.language = 'en')
    WHEN k.key = 'app.mcp.mcpUnavailableNotApprovedWarn' THEN (SELECT value FROM translations t2 JOIN translation_keys k2 ON k2.id = t2.key_id WHERE k2.key = 'app.skills.mcpUnavailableNotApprovedWarn' AND t2.language = 'en')
    WHEN k.key = 'app.mcp.myMcpTab' THEN 'My MCPs ({n})'
    WHEN k.key = 'app.mcp.myMcpsEmpty' THEN 'No MCPs of yours in Draft / Pending / Rejected.'
    WHEN k.key = 'app.mcp.name' THEN 'MCP name'
    WHEN k.key = 'app.mcp.nameEmpty' THEN 'MCP name cannot be empty.'
    WHEN k.key = 'app.mcp.nameLocked' THEN (SELECT value FROM translations t2 JOIN translation_keys k2 ON k2.id = t2.key_id WHERE k2.key = 'app.skills.nameLocked' AND t2.language = 'en')
    WHEN k.key = 'app.mcp.namePh' THEN 'Enter MCP name...'
    WHEN k.key = 'app.mcp.nameTooLong' THEN 'MCP name is at most {n} characters.'
    WHEN k.key = 'app.mcp.newCategoryPh' THEN (SELECT value FROM translations t2 JOIN translation_keys k2 ON k2.id = t2.key_id WHERE k2.key = 'app.skills.newCategoryPh' AND t2.language = 'en')
    WHEN k.key = 'app.mcp.newFiles' THEN (SELECT value FROM translations t2 JOIN translation_keys k2 ON k2.id = t2.key_id WHERE k2.key = 'app.skills.newFiles' AND t2.language = 'en')
    WHEN k.key = 'app.mcp.newMcpDraftHint' THEN 'MCP created in Draft status. Submit it for approval in MCP Management, then come back to Edit the skill and add this MCP.'
    WHEN k.key = 'app.mcp.newStagePh' THEN (SELECT value FROM translations t2 JOIN translation_keys k2 ON k2.id = t2.key_id WHERE k2.key = 'app.skills.newStagePh' AND t2.language = 'en')
    WHEN k.key = 'app.mcp.noCategoriesYet' THEN (SELECT value FROM translations t2 JOIN translation_keys k2 ON k2.id = t2.key_id WHERE k2.key = 'app.skills.noCategoriesYet' AND t2.language = 'en')
    WHEN k.key = 'app.mcp.noComments' THEN (SELECT value FROM translations t2 JOIN translation_keys k2 ON k2.id = t2.key_id WHERE k2.key = 'app.skills.noComments' AND t2.language = 'en')
    WHEN k.key = 'app.mcp.noDeps' THEN 'No MCP dependencies'
    WHEN k.key = 'app.mcp.noDescription' THEN (SELECT value FROM translations t2 JOIN translation_keys k2 ON k2.id = t2.key_id WHERE k2.key = 'app.skills.noDescription' AND t2.language = 'en')
    WHEN k.key = 'app.mcp.noMatchDesc' THEN (SELECT value FROM translations t2 JOIN translation_keys k2 ON k2.id = t2.key_id WHERE k2.key = 'app.skills.noMatchDesc' AND t2.language = 'en')
    WHEN k.key = 'app.mcp.noOptions' THEN (SELECT value FROM translations t2 JOIN translation_keys k2 ON k2.id = t2.key_id WHERE k2.key = 'app.skills.noOptions' AND t2.language = 'en')
    WHEN k.key = 'app.mcp.noRating' THEN 'No rating'
    WHEN k.key = 'app.mcp.noSkills' THEN 'No skills are using this MCP.'
    WHEN k.key = 'app.mcp.noStagesYet' THEN (SELECT value FROM translations t2 JOIN translation_keys k2 ON k2.id = t2.key_id WHERE k2.key = 'app.skills.noStagesYet' AND t2.language = 'en')
    WHEN k.key = 'app.mcp.noSuggestions' THEN 'No suggestions'
    WHEN k.key = 'app.mcp.noUsersSelected' THEN (SELECT value FROM translations t2 JOIN translation_keys k2 ON k2.id = t2.key_id WHERE k2.key = 'app.skills.noUsersSelected' AND t2.language = 'en')
    WHEN k.key = 'app.mcp.noVersions' THEN (SELECT value FROM translations t2 JOIN translation_keys k2 ON k2.id = t2.key_id WHERE k2.key = 'app.skills.noVersions' AND t2.language = 'en')
    WHEN k.key = 'app.mcp.notFoundOrNoAccess' THEN (SELECT value FROM translations t2 JOIN translation_keys k2 ON k2.id = t2.key_id WHERE k2.key = 'app.skill.notFoundOrNoAccess' AND t2.language = 'en')
    WHEN k.key = 'app.mcp.onlyApprovedComment' THEN (SELECT value FROM translations t2 JOIN translation_keys k2 ON k2.id = t2.key_id WHERE k2.key = 'app.skills.onlyApprovedComment' AND t2.language = 'en')
    WHEN k.key = 'app.mcp.onlyApprovedRate' THEN (SELECT value FROM translations t2 JOIN translation_keys k2 ON k2.id = t2.key_id WHERE k2.key = 'app.skills.onlyApprovedRate' AND t2.language = 'en')
    WHEN k.key = 'app.mcp.openListAria' THEN 'Open MCP list'
    WHEN k.key = 'app.mcp.optional' THEN (SELECT value FROM translations t2 JOIN translation_keys k2 ON k2.id = t2.key_id WHERE k2.key = 'app.skills.optional' AND t2.language = 'en')
    WHEN k.key = 'app.mcp.pending' THEN 'Pending Approval'
    WHEN k.key = 'app.mcp.pendingBanner' THEN (SELECT value FROM translations t2 JOIN translation_keys k2 ON k2.id = t2.key_id WHERE k2.key = 'app.skills.pendingBanner' AND t2.language = 'en')
    WHEN k.key = 'app.mcp.pendingVersion' THEN (SELECT value FROM translations t2 JOIN translation_keys k2 ON k2.id = t2.key_id WHERE k2.key = 'app.skills.pendingVersion' AND t2.language = 'en')
    WHEN k.key = 'app.mcp.pendingVersionBanner' THEN (SELECT value FROM translations t2 JOIN translation_keys k2 ON k2.id = t2.key_id WHERE k2.key = 'app.skills.pendingVersionBanner' AND t2.language = 'en')
    WHEN k.key = 'app.mcp.pinBlockedArchived' THEN 'This MCP is archived. Unarchive it first, then add it to the skill.'
    WHEN k.key = 'app.mcp.pinBlockedDraft' THEN 'This MCP is still in Draft. Submit it for approval first, then add it to the skill.'
    WHEN k.key = 'app.mcp.pinBlockedPending' THEN 'This MCP is already awaiting admin approval. Add it to the skill once it is approved.'
    WHEN k.key = 'app.mcp.pinBlockedRejected' THEN 'This MCP was rejected. Resubmit it for approval first, then add it to the skill.'
    WHEN k.key = 'app.mcp.processStages' THEN (SELECT value FROM translations t2 JOIN translation_keys k2 ON k2.id = t2.key_id WHERE k2.key = 'app.skills.processStages' AND t2.language = 'en')
    WHEN k.key = 'app.mcp.rateOnlyApproved' THEN 'Only approved MCPs can be rated.'
    WHEN k.key = 'app.mcp.rateStars' THEN (SELECT value FROM translations t2 JOIN translation_keys k2 ON k2.id = t2.key_id WHERE k2.key = 'app.skills.rateStars' AND t2.language = 'en')
    WHEN k.key = 'app.mcp.reject' THEN (SELECT value FROM translations t2 JOIN translation_keys k2 ON k2.id = t2.key_id WHERE k2.key = 'app.skills.reject' AND t2.language = 'en')
    WHEN k.key = 'app.mcp.rejectReasonPh' THEN (SELECT value FROM translations t2 JOIN translation_keys k2 ON k2.id = t2.key_id WHERE k2.key = 'app.skills.rejectReasonPh' AND t2.language = 'en')
    WHEN k.key = 'app.mcp.rejectReasonRequired' THEN (SELECT value FROM translations t2 JOIN translation_keys k2 ON k2.id = t2.key_id WHERE k2.key = 'app.skills.rejectReasonRequired' AND t2.language = 'en')
    WHEN k.key = 'app.mcp.rejectTitle' THEN (SELECT value FROM translations t2 JOIN translation_keys k2 ON k2.id = t2.key_id WHERE k2.key = 'app.skills.rejectTitle' AND t2.language = 'en')
    WHEN k.key = 'app.mcp.rejectVersion' THEN (SELECT value FROM translations t2 JOIN translation_keys k2 ON k2.id = t2.key_id WHERE k2.key = 'app.skills.rejectVersion' AND t2.language = 'en')
    WHEN k.key = 'app.mcp.rejected' THEN 'Rejected'
    WHEN k.key = 'app.mcp.rejectedToast' THEN 'MCP rejected.'
    WHEN k.key = 'app.mcp.rejectionReason' THEN (SELECT value FROM translations t2 JOIN translation_keys k2 ON k2.id = t2.key_id WHERE k2.key = 'app.skills.rejectionReason' AND t2.language = 'en')
    WHEN k.key = 'app.mcp.removeDepAria' THEN 'Remove {name}'
    WHEN k.key = 'app.mcp.reuseFile' THEN 'Reuse current file'
    WHEN k.key = 'app.mcp.reviewSubmitted' THEN (SELECT value FROM translations t2 JOIN translation_keys k2 ON k2.id = t2.key_id WHERE k2.key = 'app.skills.reviewSubmitted' AND t2.language = 'en')
    WHEN k.key = 'app.mcp.save' THEN 'Save'
    WHEN k.key = 'app.mcp.saveDraft' THEN 'Save Draft'
    WHEN k.key = 'app.mcp.search' THEN 'Search'
    WHEN k.key = 'app.mcp.searchByName' THEN 'Search MCP by name...'
    WHEN k.key = 'app.mcp.searchTitle' THEN (SELECT value FROM translations t2 JOIN translation_keys k2 ON k2.id = t2.key_id WHERE k2.key = 'app.skills.searchTitle' AND t2.language = 'en')
    WHEN k.key = 'app.mcp.searchUserDesc' THEN (SELECT value FROM translations t2 JOIN translation_keys k2 ON k2.id = t2.key_id WHERE k2.key = 'app.skills.searchUserDesc' AND t2.language = 'en')
    WHEN k.key = 'app.mcp.selectCreator' THEN (SELECT value FROM translations t2 JOIN translation_keys k2 ON k2.id = t2.key_id WHERE k2.key = 'app.skills.selectCreator' AND t2.language = 'en')
    WHEN k.key = 'app.mcp.selected' THEN (SELECT value FROM translations t2 JOIN translation_keys k2 ON k2.id = t2.key_id WHERE k2.key = 'app.skills.selected' AND t2.language = 'en')
    WHEN k.key = 'app.mcp.selectedCount' THEN '{n} MCP(s) selected'
    WHEN k.key = 'app.mcp.send' THEN (SELECT value FROM translations t2 JOIN translation_keys k2 ON k2.id = t2.key_id WHERE k2.key = 'app.skills.send' AND t2.language = 'en')
    WHEN k.key = 'app.mcp.serverKey' THEN NULL
    WHEN k.key = 'app.mcp.showMore' THEN 'Show more ({n} left)'
    WHEN k.key = 'app.mcp.skillsTab' THEN 'Skills using this MCP'
    WHEN k.key = 'app.mcp.stageAdded' THEN (SELECT value FROM translations t2 JOIN translation_keys k2 ON k2.id = t2.key_id WHERE k2.key = 'app.skills.stageAdded' AND t2.language = 'en')
    WHEN k.key = 'app.mcp.stageDeleteDesc' THEN (SELECT value FROM translations t2 JOIN translation_keys k2 ON k2.id = t2.key_id WHERE k2.key = 'app.skills.stageDeleteDesc' AND t2.language = 'en')
    WHEN k.key = 'app.mcp.stageDeleteDescSimple' THEN (SELECT value FROM translations t2 JOIN translation_keys k2 ON k2.id = t2.key_id WHERE k2.key = 'app.skills.stageDeleteDescSimple' AND t2.language = 'en')
    WHEN k.key = 'app.mcp.stageDeleted' THEN (SELECT value FROM translations t2 JOIN translation_keys k2 ON k2.id = t2.key_id WHERE k2.key = 'app.skills.stageDeleted' AND t2.language = 'en')
    WHEN k.key = 'app.mcp.stageDescLabel' THEN (SELECT value FROM translations t2 JOIN translation_keys k2 ON k2.id = t2.key_id WHERE k2.key = 'app.skills.stageDescLabel' AND t2.language = 'en')
    WHEN k.key = 'app.mcp.stageDescPh' THEN (SELECT value FROM translations t2 JOIN translation_keys k2 ON k2.id = t2.key_id WHERE k2.key = 'app.skills.stageDescPh' AND t2.language = 'en')
    WHEN k.key = 'app.mcp.stageName' THEN (SELECT value FROM translations t2 JOIN translation_keys k2 ON k2.id = t2.key_id WHERE k2.key = 'app.skills.stageName' AND t2.language = 'en')
    WHEN k.key = 'app.mcp.stageUpdated' THEN (SELECT value FROM translations t2 JOIN translation_keys k2 ON k2.id = t2.key_id WHERE k2.key = 'app.skills.stageUpdated' AND t2.language = 'en')
    WHEN k.key = 'app.mcp.status' THEN 'Status'
    WHEN k.key = 'app.mcp.submit' THEN 'Submit for Approval'
    WHEN k.key = 'app.mcp.submitForReview' THEN (SELECT value FROM translations t2 JOIN translation_keys k2 ON k2.id = t2.key_id WHERE k2.key = 'app.skills.submitForReview' AND t2.language = 'en')
    WHEN k.key = 'app.mcp.submittedToast' THEN 'Submitted for approval.'
    WHEN k.key = 'app.mcp.subscribe' THEN 'Subscribe'
    WHEN k.key = 'app.mcp.subscribeAll' THEN (SELECT value FROM translations t2 JOIN translation_keys k2 ON k2.id = t2.key_id WHERE k2.key = 'app.skills.subscribeAll' AND t2.language = 'en')
    WHEN k.key = 'app.mcp.subscribeN' THEN (SELECT value FROM translations t2 JOIN translation_keys k2 ON k2.id = t2.key_id WHERE k2.key = 'app.skills.subscribeN' AND t2.language = 'en')
    WHEN k.key = 'app.mcp.subscribeToast' THEN 'Subscribed successfully.'
    WHEN k.key = 'app.mcp.subscribeY' THEN (SELECT value FROM translations t2 JOIN translation_keys k2 ON k2.id = t2.key_id WHERE k2.key = 'app.skills.subscribeY' AND t2.language = 'en')
    WHEN k.key = 'app.mcp.subscribed' THEN (SELECT value FROM translations t2 JOIN translation_keys k2 ON k2.id = t2.key_id WHERE k2.key = 'app.skills.subscribed' AND t2.language = 'en')
    WHEN k.key = 'app.mcp.subscribedToast' THEN (SELECT value FROM translations t2 JOIN translation_keys k2 ON k2.id = t2.key_id WHERE k2.key = 'app.skills.subscribedToast' AND t2.language = 'en')
    WHEN k.key = 'app.mcp.tabComments' THEN (SELECT value FROM translations t2 JOIN translation_keys k2 ON k2.id = t2.key_id WHERE k2.key = 'app.skills.tabComments' AND t2.language = 'en')
    WHEN k.key = 'app.mcp.tabInfo' THEN (SELECT value FROM translations t2 JOIN translation_keys k2 ON k2.id = t2.key_id WHERE k2.key = 'app.skills.tabInfo' AND t2.language = 'en')
    WHEN k.key = 'app.mcp.tabSkills' THEN 'Skills using this MCP'
    WHEN k.key = 'app.mcp.tabVersions' THEN (SELECT value FROM translations t2 JOIN translation_keys k2 ON k2.id = t2.key_id WHERE k2.key = 'app.skills.tabVersions' AND t2.language = 'en')
    WHEN k.key = 'app.mcp.title' THEN 'MCP Detail'
    WHEN k.key = 'app.mcp.underReview' THEN (SELECT value FROM translations t2 JOIN translation_keys k2 ON k2.id = t2.key_id WHERE k2.key = 'app.skills.underReview' AND t2.language = 'en')
    WHEN k.key = 'app.mcp.unsubscribedToast' THEN (SELECT value FROM translations t2 JOIN translation_keys k2 ON k2.id = t2.key_id WHERE k2.key = 'app.skills.unsubscribedToast' AND t2.language = 'en')
    WHEN k.key = 'app.mcp.updateFileRequired' THEN (SELECT value FROM translations t2 JOIN translation_keys k2 ON k2.id = t2.key_id WHERE k2.key = 'app.skills.updateFileRequired' AND t2.language = 'en')
    WHEN k.key = 'app.mcp.updateFilesHint' THEN (SELECT value FROM translations t2 JOIN translation_keys k2 ON k2.id = t2.key_id WHERE k2.key = 'app.skills.updateFilesHint' AND t2.language = 'en')
    WHEN k.key = 'app.mcp.updateHint' THEN (SELECT value FROM translations t2 JOIN translation_keys k2 ON k2.id = t2.key_id WHERE k2.key = 'app.skills.updateHint' AND t2.language = 'en')
    WHEN k.key = 'app.mcp.updateInfo' THEN 'Update Info'
    WHEN k.key = 'app.mcp.updateInfoPh' THEN 'Describe what changed in this version...'
    WHEN k.key = 'app.mcp.updateInfoRequired' THEN 'Update Info is required for a new version.'
    WHEN k.key = 'app.mcp.updateInfoTooLong' THEN 'Update Info is at most {n} characters.'
    WHEN k.key = 'app.mcp.updateSubmitted' THEN (SELECT value FROM translations t2 JOIN translation_keys k2 ON k2.id = t2.key_id WHERE k2.key = 'app.skills.updateSubmitted' AND t2.language = 'en')
    WHEN k.key = 'app.mcp.updateTitle' THEN (SELECT value FROM translations t2 JOIN translation_keys k2 ON k2.id = t2.key_id WHERE k2.key = 'app.skills.updateTitle' AND t2.language = 'en')
    WHEN k.key = 'app.mcp.updateVersion' THEN (SELECT value FROM translations t2 JOIN translation_keys k2 ON k2.id = t2.key_id WHERE k2.key = 'app.skills.updateVersion' AND t2.language = 'en')
    WHEN k.key = 'app.mcp.updated' THEN (SELECT value FROM translations t2 JOIN translation_keys k2 ON k2.id = t2.key_id WHERE k2.key = 'app.skills.updated' AND t2.language = 'en')
    WHEN k.key = 'app.mcp.updatedToast' THEN (SELECT value FROM translations t2 JOIN translation_keys k2 ON k2.id = t2.key_id WHERE k2.key = 'app.skills.updatedToast' AND t2.language = 'en')
    WHEN k.key = 'app.mcp.uploadNewFile' THEN 'Upload new file'
    WHEN k.key = 'app.mcp.version' THEN (SELECT value FROM translations t2 JOIN translation_keys k2 ON k2.id = t2.key_id WHERE k2.key = 'app.skills.version' AND t2.language = 'en')
    WHEN k.key = 'app.mcp.versionApproved' THEN (SELECT value FROM translations t2 JOIN translation_keys k2 ON k2.id = t2.key_id WHERE k2.key = 'app.skills.versionApproved' AND t2.language = 'en')
    WHEN k.key = 'app.mcp.versionCancelled' THEN (SELECT value FROM translations t2 JOIN translation_keys k2 ON k2.id = t2.key_id WHERE k2.key = 'app.skills.versionCancelled' AND t2.language = 'en')
    WHEN k.key = 'app.mcp.versionConfirmDesc' THEN (SELECT value FROM translations t2 JOIN translation_keys k2 ON k2.id = t2.key_id WHERE k2.key = 'app.skill.versionConfirmDesc' AND t2.language = 'en')
    WHEN k.key = 'app.mcp.versionConfirmTitle' THEN (SELECT value FROM translations t2 JOIN translation_keys k2 ON k2.id = t2.key_id WHERE k2.key = 'app.skill.versionConfirmTitle' AND t2.language = 'en')
    WHEN k.key = 'app.mcp.versionCreatedToast' THEN 'Version {n} created as DRAFT — submit for approval when ready.'
    WHEN k.key = 'app.mcp.versionInfo' THEN 'A new version {new} will be created from the current version (v{current}).'
    WHEN k.key = 'app.mcp.versionNote' THEN 'Version {n} is created in Draft status and the MCP returns to Draft (same as a newly created MCP). The current version keeps serving until the new version is approved. Submit it for approval when ready.'
    WHEN k.key = 'app.mcp.versionNum' THEN 'Version {n}'
    WHEN k.key = 'app.mcp.versionRejected' THEN (SELECT value FROM translations t2 JOIN translation_keys k2 ON k2.id = t2.key_id WHERE k2.key = 'app.skills.versionRejected' AND t2.language = 'en')
    WHEN k.key = 'app.mcp.viewCount' THEN 'Views: {n}'
    WHEN k.key = 'app.mcp.withdraw' THEN (SELECT value FROM translations t2 JOIN translation_keys k2 ON k2.id = t2.key_id WHERE k2.key = 'app.skills.withdraw' AND t2.language = 'en')
    WHEN k.key = 'app.mcp.withdrawDesc' THEN (SELECT value FROM translations t2 JOIN translation_keys k2 ON k2.id = t2.key_id WHERE k2.key = 'app.skills.withdrawDesc' AND t2.language = 'en')
    WHEN k.key = 'app.mcp.withdrawTitle' THEN (SELECT value FROM translations t2 JOIN translation_keys k2 ON k2.id = t2.key_id WHERE k2.key = 'app.skills.withdrawTitle' AND t2.language = 'en')
    WHEN k.key = 'app.mcp.withdrawn' THEN (SELECT value FROM translations t2 JOIN translation_keys k2 ON k2.id = t2.key_id WHERE k2.key = 'app.skills.withdrawn' AND t2.language = 'en')
    WHEN k.key = 'app.mcp.withdrawnToast' THEN 'Withdrawn to draft.'
    WHEN k.key = 'app.mcp.writeComment' THEN (SELECT value FROM translations t2 JOIN translation_keys k2 ON k2.id = t2.key_id WHERE k2.key = 'app.skills.writeComment' AND t2.language = 'en')
    WHEN k.key = 'app.mcp.youRate' THEN 'Rate {n} stars'
  END
FROM translation_keys k
WHERE k.key LIKE 'app.mcp.%'
  AND CASE
    WHEN k.key = 'app.mcp.addBtn' THEN EXISTS (SELECT 1 FROM translations t2 JOIN translation_keys k2 ON k2.id = t2.key_id WHERE k2.key = 'app.skills.addBtn' AND t2.language = 'en')
    WHEN k.key = 'app.mcp.addTitle' THEN EXISTS (SELECT 1 FROM translations t2 JOIN translation_keys k2 ON k2.id = t2.key_id WHERE k2.key = 'app.skills.addTitle' AND t2.language = 'en')
    WHEN k.key = 'app.mcp.adminBadge' THEN EXISTS (SELECT 1 FROM translations t2 JOIN translation_keys k2 ON k2.id = t2.key_id WHERE k2.key = 'app.skills.adminBadge' AND t2.language = 'en')
    WHEN k.key = 'app.mcp.adminOnlyCat' THEN EXISTS (SELECT 1 FROM translations t2 JOIN translation_keys k2 ON k2.id = t2.key_id WHERE k2.key = 'app.skills.adminOnlyCat' AND t2.language = 'en')
    WHEN k.key = 'app.mcp.adminOnlyStage' THEN EXISTS (SELECT 1 FROM translations t2 JOIN translation_keys k2 ON k2.id = t2.key_id WHERE k2.key = 'app.skills.adminOnlyStage' AND t2.language = 'en')
    WHEN k.key = 'app.mcp.allStatuses' THEN TRUE
    WHEN k.key = 'app.mcp.approve' THEN EXISTS (SELECT 1 FROM translations t2 JOIN translation_keys k2 ON k2.id = t2.key_id WHERE k2.key = 'app.skills.approve' AND t2.language = 'en')
    WHEN k.key = 'app.mcp.approveVersion' THEN EXISTS (SELECT 1 FROM translations t2 JOIN translation_keys k2 ON k2.id = t2.key_id WHERE k2.key = 'app.skills.approveVersion' AND t2.language = 'en')
    WHEN k.key = 'app.mcp.approved' THEN TRUE
    WHEN k.key = 'app.mcp.approvedFilesHint' THEN EXISTS (SELECT 1 FROM translations t2 JOIN translation_keys k2 ON k2.id = t2.key_id WHERE k2.key = 'app.skills.approvedFilesHint' AND t2.language = 'en')
    WHEN k.key = 'app.mcp.approvedMcpTab' THEN TRUE
    WHEN k.key = 'app.mcp.approvedToast' THEN TRUE
    WHEN k.key = 'app.mcp.browseTitle' THEN TRUE
    WHEN k.key = 'app.mcp.cancel' THEN TRUE
    WHEN k.key = 'app.mcp.cancelUpdateBtn' THEN EXISTS (SELECT 1 FROM translations t2 JOIN translation_keys k2 ON k2.id = t2.key_id WHERE k2.key = 'app.skills.cancelUpdateBtn' AND t2.language = 'en')
    WHEN k.key = 'app.mcp.cancelUpdateDesc' THEN EXISTS (SELECT 1 FROM translations t2 JOIN translation_keys k2 ON k2.id = t2.key_id WHERE k2.key = 'app.skills.cancelUpdateDesc' AND t2.language = 'en')
    WHEN k.key = 'app.mcp.cancelUpdateTitle' THEN EXISTS (SELECT 1 FROM translations t2 JOIN translation_keys k2 ON k2.id = t2.key_id WHERE k2.key = 'app.skills.cancelUpdateTitle' AND t2.language = 'en')
    WHEN k.key = 'app.mcp.cancelVersionUpdate' THEN EXISTS (SELECT 1 FROM translations t2 JOIN translation_keys k2 ON k2.id = t2.key_id WHERE k2.key = 'app.skills.cancelVersionUpdate' AND t2.language = 'en')
    WHEN k.key = 'app.mcp.cannotDeleteItem' THEN EXISTS (SELECT 1 FROM translations t2 JOIN translation_keys k2 ON k2.id = t2.key_id WHERE k2.key = 'app.skills.cannotDeleteItem' AND t2.language = 'en')
    WHEN k.key = 'app.mcp.cannotDeleteList' THEN EXISTS (SELECT 1 FROM translations t2 JOIN translation_keys k2 ON k2.id = t2.key_id WHERE k2.key = 'app.skills.cannotDeleteList' AND t2.language = 'en')
    WHEN k.key = 'app.mcp.cannotDeleteTitle' THEN EXISTS (SELECT 1 FROM translations t2 JOIN translation_keys k2 ON k2.id = t2.key_id WHERE k2.key = 'app.skills.cannotDeleteTitle' AND t2.language = 'en')
    WHEN k.key = 'app.mcp.cannotDeleteTotal' THEN EXISTS (SELECT 1 FROM translations t2 JOIN translation_keys k2 ON k2.id = t2.key_id WHERE k2.key = 'app.skills.cannotDeleteTotal' AND t2.language = 'en')
    WHEN k.key = 'app.mcp.cardsAria' THEN EXISTS (SELECT 1 FROM translations t2 JOIN translation_keys k2 ON k2.id = t2.key_id WHERE k2.key = 'app.skills.cardsAria' AND t2.language = 'en')
    WHEN k.key = 'app.mcp.catAdded' THEN EXISTS (SELECT 1 FROM translations t2 JOIN translation_keys k2 ON k2.id = t2.key_id WHERE k2.key = 'app.skills.catAdded' AND t2.language = 'en')
    WHEN k.key = 'app.mcp.catDeleteDesc' THEN EXISTS (SELECT 1 FROM translations t2 JOIN translation_keys k2 ON k2.id = t2.key_id WHERE k2.key = 'app.skills.catDeleteDesc' AND t2.language = 'en')
    WHEN k.key = 'app.mcp.catDeleteDescSimple' THEN EXISTS (SELECT 1 FROM translations t2 JOIN translation_keys k2 ON k2.id = t2.key_id WHERE k2.key = 'app.skills.catDeleteDescSimple' AND t2.language = 'en')
    WHEN k.key = 'app.mcp.catDeleted' THEN EXISTS (SELECT 1 FROM translations t2 JOIN translation_keys k2 ON k2.id = t2.key_id WHERE k2.key = 'app.skills.catDeleted' AND t2.language = 'en')
    WHEN k.key = 'app.mcp.catUpdated' THEN EXISTS (SELECT 1 FROM translations t2 JOIN translation_keys k2 ON k2.id = t2.key_id WHERE k2.key = 'app.skills.catUpdated' AND t2.language = 'en')
    WHEN k.key = 'app.mcp.categories' THEN EXISTS (SELECT 1 FROM translations t2 JOIN translation_keys k2 ON k2.id = t2.key_id WHERE k2.key = 'app.skills.categories' AND t2.language = 'en')
    WHEN k.key = 'app.mcp.categoryName' THEN EXISTS (SELECT 1 FROM translations t2 JOIN translation_keys k2 ON k2.id = t2.key_id WHERE k2.key = 'app.skills.categoryName' AND t2.language = 'en')
    WHEN k.key = 'app.mcp.changelog' THEN TRUE
    WHEN k.key = 'app.mcp.changelogPh' THEN TRUE
    WHEN k.key = 'app.mcp.changelogRequired' THEN TRUE
    WHEN k.key = 'app.mcp.changelogTooLong' THEN TRUE
    WHEN k.key = 'app.mcp.clearCreator' THEN EXISTS (SELECT 1 FROM translations t2 JOIN translation_keys k2 ON k2.id = t2.key_id WHERE k2.key = 'app.skills.clearCreator' AND t2.language = 'en')
    WHEN k.key = 'app.mcp.close' THEN TRUE
    WHEN k.key = 'app.mcp.colCreated' THEN TRUE
    WHEN k.key = 'app.mcp.colCreator' THEN EXISTS (SELECT 1 FROM translations t2 JOIN translation_keys k2 ON k2.id = t2.key_id WHERE k2.key = 'app.skills.colCreator' AND t2.language = 'en')
    WHEN k.key = 'app.mcp.colDescription' THEN TRUE
    WHEN k.key = 'app.mcp.colDownloads' THEN EXISTS (SELECT 1 FROM translations t2 JOIN translation_keys k2 ON k2.id = t2.key_id WHERE k2.key = 'app.skills.colDownloads' AND t2.language = 'en')
    WHEN k.key = 'app.mcp.colName' THEN EXISTS (SELECT 1 FROM translations t2 JOIN translation_keys k2 ON k2.id = t2.key_id WHERE k2.key = 'app.skills.colName' AND t2.language = 'en')
    WHEN k.key = 'app.mcp.colRating' THEN EXISTS (SELECT 1 FROM translations t2 JOIN translation_keys k2 ON k2.id = t2.key_id WHERE k2.key = 'app.skills.colRating' AND t2.language = 'en')
    WHEN k.key = 'app.mcp.colStatus' THEN TRUE
    WHEN k.key = 'app.mcp.colSubscribe' THEN EXISTS (SELECT 1 FROM translations t2 JOIN translation_keys k2 ON k2.id = t2.key_id WHERE k2.key = 'app.skills.colSubscribe' AND t2.language = 'en')
    WHEN k.key = 'app.mcp.colViews' THEN EXISTS (SELECT 1 FROM translations t2 JOIN translation_keys k2 ON k2.id = t2.key_id WHERE k2.key = 'app.skills.colViews' AND t2.language = 'en')
    WHEN k.key = 'app.mcp.confirm' THEN TRUE
    WHEN k.key = 'app.mcp.copyLink' THEN EXISTS (SELECT 1 FROM translations t2 JOIN translation_keys k2 ON k2.id = t2.key_id WHERE k2.key = 'app.skill.copyLink' AND t2.language = 'en')
    WHEN k.key = 'app.mcp.copyLinkCopied' THEN EXISTS (SELECT 1 FROM translations t2 JOIN translation_keys k2 ON k2.id = t2.key_id WHERE k2.key = 'app.skill.copyLinkCopied' AND t2.language = 'en')
    WHEN k.key = 'app.mcp.copyLinkFailed' THEN EXISTS (SELECT 1 FROM translations t2 JOIN translation_keys k2 ON k2.id = t2.key_id WHERE k2.key = 'app.skill.copyLinkFailed' AND t2.language = 'en')
    WHEN k.key = 'app.mcp.copyLinkTooltip' THEN EXISTS (SELECT 1 FROM translations t2 JOIN translation_keys k2 ON k2.id = t2.key_id WHERE k2.key = 'app.skill.copyLinkTooltip' AND t2.language = 'en')
    WHEN k.key = 'app.mcp.createVersion' THEN TRUE
    WHEN k.key = 'app.mcp.createdToast' THEN EXISTS (SELECT 1 FROM translations t2 JOIN translation_keys k2 ON k2.id = t2.key_id WHERE k2.key = 'app.skills.createdToast' AND t2.language = 'en')
    WHEN k.key = 'app.mcp.createdUpdated' THEN EXISTS (SELECT 1 FROM translations t2 JOIN translation_keys k2 ON k2.id = t2.key_id WHERE k2.key = 'app.skills.createdUpdated' AND t2.language = 'en')
    WHEN k.key = 'app.mcp.creator' THEN EXISTS (SELECT 1 FROM translations t2 JOIN translation_keys k2 ON k2.id = t2.key_id WHERE k2.key = 'app.skills.creator' AND t2.language = 'en')
    WHEN k.key = 'app.mcp.current' THEN EXISTS (SELECT 1 FROM translations t2 JOIN translation_keys k2 ON k2.id = t2.key_id WHERE k2.key = 'app.skills.current' AND t2.language = 'en')
    WHEN k.key = 'app.mcp.currentFile' THEN TRUE
    WHEN k.key = 'app.mcp.deleteBtn' THEN EXISTS (SELECT 1 FROM translations t2 JOIN translation_keys k2 ON k2.id = t2.key_id WHERE k2.key = 'app.skills.deleteBtn' AND t2.language = 'en')
    WHEN k.key = 'app.mcp.deleteCategoryAria' THEN EXISTS (SELECT 1 FROM translations t2 JOIN translation_keys k2 ON k2.id = t2.key_id WHERE k2.key = 'app.skills.deleteCategoryAria' AND t2.language = 'en')
    WHEN k.key = 'app.mcp.deleteCommentAria' THEN EXISTS (SELECT 1 FROM translations t2 JOIN translation_keys k2 ON k2.id = t2.key_id WHERE k2.key = 'app.skills.deleteCommentAria' AND t2.language = 'en')
    WHEN k.key = 'app.mcp.deleteDeniedDesc' THEN TRUE
    WHEN k.key = 'app.mcp.deleteDeniedList' THEN TRUE
    WHEN k.key = 'app.mcp.deleteDeniedTitle' THEN TRUE
    WHEN k.key = 'app.mcp.deleteDeniedTotal' THEN TRUE
    WHEN k.key = 'app.mcp.deleteDesc' THEN EXISTS (SELECT 1 FROM translations t2 JOIN translation_keys k2 ON k2.id = t2.key_id WHERE k2.key = 'app.skills.deleteDesc' AND t2.language = 'en')
    WHEN k.key = 'app.mcp.deleteStageAria' THEN EXISTS (SELECT 1 FROM translations t2 JOIN translation_keys k2 ON k2.id = t2.key_id WHERE k2.key = 'app.skills.deleteStageAria' AND t2.language = 'en')
    WHEN k.key = 'app.mcp.deleteTitle' THEN EXISTS (SELECT 1 FROM translations t2 JOIN translation_keys k2 ON k2.id = t2.key_id WHERE k2.key = 'app.skills.deleteTitle' AND t2.language = 'en')
    WHEN k.key = 'app.mcp.deletedToast' THEN EXISTS (SELECT 1 FROM translations t2 JOIN translation_keys k2 ON k2.id = t2.key_id WHERE k2.key = 'app.skills.deletedToast' AND t2.language = 'en')
    WHEN k.key = 'app.mcp.deletedToastDetail' THEN EXISTS (SELECT 1 FROM translations t2 JOIN translation_keys k2 ON k2.id = t2.key_id WHERE k2.key = 'app.skills.deletedToastDetail' AND t2.language = 'en')
    WHEN k.key = 'app.mcp.depAlready' THEN TRUE
    WHEN k.key = 'app.mcp.depBanner' THEN TRUE
    WHEN k.key = 'app.mcp.depDeleted' THEN TRUE
    WHEN k.key = 'app.mcp.depDeletedTip' THEN TRUE
    WHEN k.key = 'app.mcp.depLabel' THEN TRUE
    WHEN k.key = 'app.mcp.depLoading' THEN TRUE
    WHEN k.key = 'app.mcp.depPh' THEN TRUE
    WHEN k.key = 'app.mcp.dependentSkills' THEN FALSE
    WHEN k.key = 'app.mcp.descRequired' THEN TRUE
    WHEN k.key = 'app.mcp.description' THEN TRUE
    WHEN k.key = 'app.mcp.descriptionPh' THEN TRUE
    WHEN k.key = 'app.mcp.detailTitle' THEN EXISTS (SELECT 1 FROM translations t2 JOIN translation_keys k2 ON k2.id = t2.key_id WHERE k2.key = 'app.skills.detailTitle' AND t2.language = 'en')
    WHEN k.key = 'app.mcp.download' THEN TRUE
    WHEN k.key = 'app.mcp.downloadAll' THEN EXISTS (SELECT 1 FROM translations t2 JOIN translation_keys k2 ON k2.id = t2.key_id WHERE k2.key = 'app.skills.downloadAll' AND t2.language = 'en')
    WHEN k.key = 'app.mcp.downloadCount' THEN TRUE
    WHEN k.key = 'app.mcp.downloadExcel' THEN EXISTS (SELECT 1 FROM translations t2 JOIN translation_keys k2 ON k2.id = t2.key_id WHERE k2.key = 'app.skills.downloadExcel' AND t2.language = 'en')
    WHEN k.key = 'app.mcp.draft' THEN TRUE
    WHEN k.key = 'app.mcp.draftHint' THEN EXISTS (SELECT 1 FROM translations t2 JOIN translation_keys k2 ON k2.id = t2.key_id WHERE k2.key = 'app.skills.draftHint' AND t2.language = 'en')
    WHEN k.key = 'app.mcp.draftNote' THEN TRUE
    WHEN k.key = 'app.mcp.dragDropNewOr' THEN EXISTS (SELECT 1 FROM translations t2 JOIN translation_keys k2 ON k2.id = t2.key_id WHERE k2.key = 'app.skills.dragDropNewOr' AND t2.language = 'en')
    WHEN k.key = 'app.mcp.editBtn' THEN EXISTS (SELECT 1 FROM translations t2 JOIN translation_keys k2 ON k2.id = t2.key_id WHERE k2.key = 'app.skills.editBtn' AND t2.language = 'en')
    WHEN k.key = 'app.mcp.editCategoryAria' THEN EXISTS (SELECT 1 FROM translations t2 JOIN translation_keys k2 ON k2.id = t2.key_id WHERE k2.key = 'app.skills.editCategoryAria' AND t2.language = 'en')
    WHEN k.key = 'app.mcp.editCategoryTitle' THEN EXISTS (SELECT 1 FROM translations t2 JOIN translation_keys k2 ON k2.id = t2.key_id WHERE k2.key = 'app.skills.editCategoryTitle' AND t2.language = 'en')
    WHEN k.key = 'app.mcp.editNoteApproved' THEN TRUE
    WHEN k.key = 'app.mcp.editNoteStatus' THEN TRUE
    WHEN k.key = 'app.mcp.editStageAria' THEN EXISTS (SELECT 1 FROM translations t2 JOIN translation_keys k2 ON k2.id = t2.key_id WHERE k2.key = 'app.skills.editStageAria' AND t2.language = 'en')
    WHEN k.key = 'app.mcp.editStageTitle' THEN EXISTS (SELECT 1 FROM translations t2 JOIN translation_keys k2 ON k2.id = t2.key_id WHERE k2.key = 'app.skills.editStageTitle' AND t2.language = 'en')
    WHEN k.key = 'app.mcp.editTitle' THEN EXISTS (SELECT 1 FROM translations t2 JOIN translation_keys k2 ON k2.id = t2.key_id WHERE k2.key = 'app.skills.editTitle' AND t2.language = 'en')
    WHEN k.key = 'app.mcp.emptyDesc' THEN EXISTS (SELECT 1 FROM translations t2 JOIN translation_keys k2 ON k2.id = t2.key_id WHERE k2.key = 'app.skills.emptyDesc' AND t2.language = 'en')
    WHEN k.key = 'app.mcp.emptyTitle' THEN TRUE
    WHEN k.key = 'app.mcp.exportedToast' THEN EXISTS (SELECT 1 FROM translations t2 JOIN translation_keys k2 ON k2.id = t2.key_id WHERE k2.key = 'app.skills.exportedToast' AND t2.language = 'en')
    WHEN k.key = 'app.mcp.file' THEN TRUE
    WHEN k.key = 'app.mcp.fileDropOr' THEN TRUE
    WHEN k.key = 'app.mcp.fileErrorFormat' THEN TRUE
    WHEN k.key = 'app.mcp.fileErrorSize' THEN TRUE
    WHEN k.key = 'app.mcp.fileKeepHint' THEN TRUE
    WHEN k.key = 'app.mcp.fileRemoved' THEN EXISTS (SELECT 1 FROM translations t2 JOIN translation_keys k2 ON k2.id = t2.key_id WHERE k2.key = 'app.skills.fileRemoved' AND t2.language = 'en')
    WHEN k.key = 'app.mcp.fileRequired' THEN EXISTS (SELECT 1 FROM translations t2 JOIN translation_keys k2 ON k2.id = t2.key_id WHERE k2.key = 'app.skills.fileRequired' AND t2.language = 'en')
    WHEN k.key = 'app.mcp.fileRequiredUpload' THEN TRUE
    WHEN k.key = 'app.mcp.fileSupported' THEN TRUE
    WHEN k.key = 'app.mcp.filesToRemove' THEN EXISTS (SELECT 1 FROM translations t2 JOIN translation_keys k2 ON k2.id = t2.key_id WHERE k2.key = 'app.skills.filesToRemove' AND t2.language = 'en')
    WHEN k.key = 'app.mcp.hashtag' THEN TRUE
    WHEN k.key = 'app.mcp.hashtagExists' THEN TRUE
    WHEN k.key = 'app.mcp.hashtagPh' THEN TRUE
    WHEN k.key = 'app.mcp.hashtagRemoveAria' THEN TRUE
    WHEN k.key = 'app.mcp.lastRejected' THEN EXISTS (SELECT 1 FROM translations t2 JOIN translation_keys k2 ON k2.id = t2.key_id WHERE k2.key = 'app.skills.lastRejected' AND t2.language = 'en')
    WHEN k.key = 'app.mcp.listAria' THEN EXISTS (SELECT 1 FROM translations t2 JOIN translation_keys k2 ON k2.id = t2.key_id WHERE k2.key = 'app.skills.listAria' AND t2.language = 'en')
    WHEN k.key = 'app.mcp.loadError' THEN EXISTS (SELECT 1 FROM translations t2 JOIN translation_keys k2 ON k2.id = t2.key_id WHERE k2.key = 'app.skills.loadError' AND t2.language = 'en')
    WHEN k.key = 'app.mcp.loadErrorDetail' THEN EXISTS (SELECT 1 FROM translations t2 JOIN translation_keys k2 ON k2.id = t2.key_id WHERE k2.key = 'app.skills.loadErrorDetail' AND t2.language = 'en')
    WHEN k.key = 'app.mcp.manageTaxonomy' THEN EXISTS (SELECT 1 FROM translations t2 JOIN translation_keys k2 ON k2.id = t2.key_id WHERE k2.key = 'app.skills.manageTaxonomy' AND t2.language = 'en')
    WHEN k.key = 'app.mcp.manageTaxonomyTitle' THEN EXISTS (SELECT 1 FROM translations t2 JOIN translation_keys k2 ON k2.id = t2.key_id WHERE k2.key = 'app.skills.manageTaxonomyTitle' AND t2.language = 'en')
    WHEN k.key = 'app.mcp.mcpCode' THEN FALSE
    WHEN k.key = 'app.mcp.mcpCount' THEN EXISTS (SELECT 1 FROM translations t2 JOIN translation_keys k2 ON k2.id = t2.key_id WHERE k2.key = 'app.skills.skillCount' AND t2.language = 'en')
    WHEN k.key = 'app.mcp.mcpDeleted' THEN EXISTS (SELECT 1 FROM translations t2 JOIN translation_keys k2 ON k2.id = t2.key_id WHERE k2.key = 'app.skills.mcpDeleted' AND t2.language = 'en')
    WHEN k.key = 'app.mcp.mcpDeletedTip' THEN EXISTS (SELECT 1 FROM translations t2 JOIN translation_keys k2 ON k2.id = t2.key_id WHERE k2.key = 'app.skills.mcpDeletedTip' AND t2.language = 'en')
    WHEN k.key = 'app.mcp.mcpDepsWarnDesc' THEN EXISTS (SELECT 1 FROM translations t2 JOIN translation_keys k2 ON k2.id = t2.key_id WHERE k2.key = 'app.skills.mcpDepsWarnDesc' AND t2.language = 'en')
    WHEN k.key = 'app.mcp.mcpDepsWarnNote' THEN EXISTS (SELECT 1 FROM translations t2 JOIN translation_keys k2 ON k2.id = t2.key_id WHERE k2.key = 'app.skills.mcpDepsWarnNote' AND t2.language = 'en')
    WHEN k.key = 'app.mcp.mcpDepsWarnTitle' THEN EXISTS (SELECT 1 FROM translations t2 JOIN translation_keys k2 ON k2.id = t2.key_id WHERE k2.key = 'app.skills.mcpDepsWarnTitle' AND t2.language = 'en')
    WHEN k.key = 'app.mcp.mcpUnavailableGone' THEN EXISTS (SELECT 1 FROM translations t2 JOIN translation_keys k2 ON k2.id = t2.key_id WHERE k2.key = 'app.skills.mcpUnavailableGone' AND t2.language = 'en')
    WHEN k.key = 'app.mcp.mcpUnavailableGoneWarn' THEN EXISTS (SELECT 1 FROM translations t2 JOIN translation_keys k2 ON k2.id = t2.key_id WHERE k2.key = 'app.skills.mcpUnavailableGoneWarn' AND t2.language = 'en')
    WHEN k.key = 'app.mcp.mcpUnavailableNotApproved' THEN EXISTS (SELECT 1 FROM translations t2 JOIN translation_keys k2 ON k2.id = t2.key_id WHERE k2.key = 'app.skills.mcpUnavailableNotApproved' AND t2.language = 'en')
    WHEN k.key = 'app.mcp.mcpUnavailableNotApprovedWarn' THEN EXISTS (SELECT 1 FROM translations t2 JOIN translation_keys k2 ON k2.id = t2.key_id WHERE k2.key = 'app.skills.mcpUnavailableNotApprovedWarn' AND t2.language = 'en')
    WHEN k.key = 'app.mcp.myMcpTab' THEN TRUE
    WHEN k.key = 'app.mcp.myMcpsEmpty' THEN TRUE
    WHEN k.key = 'app.mcp.name' THEN TRUE
    WHEN k.key = 'app.mcp.nameEmpty' THEN TRUE
    WHEN k.key = 'app.mcp.nameLocked' THEN EXISTS (SELECT 1 FROM translations t2 JOIN translation_keys k2 ON k2.id = t2.key_id WHERE k2.key = 'app.skills.nameLocked' AND t2.language = 'en')
    WHEN k.key = 'app.mcp.namePh' THEN TRUE
    WHEN k.key = 'app.mcp.nameTooLong' THEN TRUE
    WHEN k.key = 'app.mcp.newCategoryPh' THEN EXISTS (SELECT 1 FROM translations t2 JOIN translation_keys k2 ON k2.id = t2.key_id WHERE k2.key = 'app.skills.newCategoryPh' AND t2.language = 'en')
    WHEN k.key = 'app.mcp.newFiles' THEN EXISTS (SELECT 1 FROM translations t2 JOIN translation_keys k2 ON k2.id = t2.key_id WHERE k2.key = 'app.skills.newFiles' AND t2.language = 'en')
    WHEN k.key = 'app.mcp.newMcpDraftHint' THEN TRUE
    WHEN k.key = 'app.mcp.newStagePh' THEN EXISTS (SELECT 1 FROM translations t2 JOIN translation_keys k2 ON k2.id = t2.key_id WHERE k2.key = 'app.skills.newStagePh' AND t2.language = 'en')
    WHEN k.key = 'app.mcp.noCategoriesYet' THEN EXISTS (SELECT 1 FROM translations t2 JOIN translation_keys k2 ON k2.id = t2.key_id WHERE k2.key = 'app.skills.noCategoriesYet' AND t2.language = 'en')
    WHEN k.key = 'app.mcp.noComments' THEN EXISTS (SELECT 1 FROM translations t2 JOIN translation_keys k2 ON k2.id = t2.key_id WHERE k2.key = 'app.skills.noComments' AND t2.language = 'en')
    WHEN k.key = 'app.mcp.noDeps' THEN TRUE
    WHEN k.key = 'app.mcp.noDescription' THEN EXISTS (SELECT 1 FROM translations t2 JOIN translation_keys k2 ON k2.id = t2.key_id WHERE k2.key = 'app.skills.noDescription' AND t2.language = 'en')
    WHEN k.key = 'app.mcp.noMatchDesc' THEN EXISTS (SELECT 1 FROM translations t2 JOIN translation_keys k2 ON k2.id = t2.key_id WHERE k2.key = 'app.skills.noMatchDesc' AND t2.language = 'en')
    WHEN k.key = 'app.mcp.noOptions' THEN EXISTS (SELECT 1 FROM translations t2 JOIN translation_keys k2 ON k2.id = t2.key_id WHERE k2.key = 'app.skills.noOptions' AND t2.language = 'en')
    WHEN k.key = 'app.mcp.noRating' THEN TRUE
    WHEN k.key = 'app.mcp.noSkills' THEN TRUE
    WHEN k.key = 'app.mcp.noStagesYet' THEN EXISTS (SELECT 1 FROM translations t2 JOIN translation_keys k2 ON k2.id = t2.key_id WHERE k2.key = 'app.skills.noStagesYet' AND t2.language = 'en')
    WHEN k.key = 'app.mcp.noSuggestions' THEN TRUE
    WHEN k.key = 'app.mcp.noUsersSelected' THEN EXISTS (SELECT 1 FROM translations t2 JOIN translation_keys k2 ON k2.id = t2.key_id WHERE k2.key = 'app.skills.noUsersSelected' AND t2.language = 'en')
    WHEN k.key = 'app.mcp.noVersions' THEN EXISTS (SELECT 1 FROM translations t2 JOIN translation_keys k2 ON k2.id = t2.key_id WHERE k2.key = 'app.skills.noVersions' AND t2.language = 'en')
    WHEN k.key = 'app.mcp.notFoundOrNoAccess' THEN EXISTS (SELECT 1 FROM translations t2 JOIN translation_keys k2 ON k2.id = t2.key_id WHERE k2.key = 'app.skill.notFoundOrNoAccess' AND t2.language = 'en')
    WHEN k.key = 'app.mcp.onlyApprovedComment' THEN EXISTS (SELECT 1 FROM translations t2 JOIN translation_keys k2 ON k2.id = t2.key_id WHERE k2.key = 'app.skills.onlyApprovedComment' AND t2.language = 'en')
    WHEN k.key = 'app.mcp.onlyApprovedRate' THEN EXISTS (SELECT 1 FROM translations t2 JOIN translation_keys k2 ON k2.id = t2.key_id WHERE k2.key = 'app.skills.onlyApprovedRate' AND t2.language = 'en')
    WHEN k.key = 'app.mcp.openListAria' THEN TRUE
    WHEN k.key = 'app.mcp.optional' THEN EXISTS (SELECT 1 FROM translations t2 JOIN translation_keys k2 ON k2.id = t2.key_id WHERE k2.key = 'app.skills.optional' AND t2.language = 'en')
    WHEN k.key = 'app.mcp.pending' THEN TRUE
    WHEN k.key = 'app.mcp.pendingBanner' THEN EXISTS (SELECT 1 FROM translations t2 JOIN translation_keys k2 ON k2.id = t2.key_id WHERE k2.key = 'app.skills.pendingBanner' AND t2.language = 'en')
    WHEN k.key = 'app.mcp.pendingVersion' THEN EXISTS (SELECT 1 FROM translations t2 JOIN translation_keys k2 ON k2.id = t2.key_id WHERE k2.key = 'app.skills.pendingVersion' AND t2.language = 'en')
    WHEN k.key = 'app.mcp.pendingVersionBanner' THEN EXISTS (SELECT 1 FROM translations t2 JOIN translation_keys k2 ON k2.id = t2.key_id WHERE k2.key = 'app.skills.pendingVersionBanner' AND t2.language = 'en')
    WHEN k.key = 'app.mcp.pinBlockedArchived' THEN TRUE
    WHEN k.key = 'app.mcp.pinBlockedDraft' THEN TRUE
    WHEN k.key = 'app.mcp.pinBlockedPending' THEN TRUE
    WHEN k.key = 'app.mcp.pinBlockedRejected' THEN TRUE
    WHEN k.key = 'app.mcp.processStages' THEN EXISTS (SELECT 1 FROM translations t2 JOIN translation_keys k2 ON k2.id = t2.key_id WHERE k2.key = 'app.skills.processStages' AND t2.language = 'en')
    WHEN k.key = 'app.mcp.rateOnlyApproved' THEN TRUE
    WHEN k.key = 'app.mcp.rateStars' THEN EXISTS (SELECT 1 FROM translations t2 JOIN translation_keys k2 ON k2.id = t2.key_id WHERE k2.key = 'app.skills.rateStars' AND t2.language = 'en')
    WHEN k.key = 'app.mcp.reject' THEN EXISTS (SELECT 1 FROM translations t2 JOIN translation_keys k2 ON k2.id = t2.key_id WHERE k2.key = 'app.skills.reject' AND t2.language = 'en')
    WHEN k.key = 'app.mcp.rejectReasonPh' THEN EXISTS (SELECT 1 FROM translations t2 JOIN translation_keys k2 ON k2.id = t2.key_id WHERE k2.key = 'app.skills.rejectReasonPh' AND t2.language = 'en')
    WHEN k.key = 'app.mcp.rejectReasonRequired' THEN EXISTS (SELECT 1 FROM translations t2 JOIN translation_keys k2 ON k2.id = t2.key_id WHERE k2.key = 'app.skills.rejectReasonRequired' AND t2.language = 'en')
    WHEN k.key = 'app.mcp.rejectTitle' THEN EXISTS (SELECT 1 FROM translations t2 JOIN translation_keys k2 ON k2.id = t2.key_id WHERE k2.key = 'app.skills.rejectTitle' AND t2.language = 'en')
    WHEN k.key = 'app.mcp.rejectVersion' THEN EXISTS (SELECT 1 FROM translations t2 JOIN translation_keys k2 ON k2.id = t2.key_id WHERE k2.key = 'app.skills.rejectVersion' AND t2.language = 'en')
    WHEN k.key = 'app.mcp.rejected' THEN TRUE
    WHEN k.key = 'app.mcp.rejectedToast' THEN TRUE
    WHEN k.key = 'app.mcp.rejectionReason' THEN EXISTS (SELECT 1 FROM translations t2 JOIN translation_keys k2 ON k2.id = t2.key_id WHERE k2.key = 'app.skills.rejectionReason' AND t2.language = 'en')
    WHEN k.key = 'app.mcp.removeDepAria' THEN TRUE
    WHEN k.key = 'app.mcp.reuseFile' THEN TRUE
    WHEN k.key = 'app.mcp.reviewSubmitted' THEN EXISTS (SELECT 1 FROM translations t2 JOIN translation_keys k2 ON k2.id = t2.key_id WHERE k2.key = 'app.skills.reviewSubmitted' AND t2.language = 'en')
    WHEN k.key = 'app.mcp.save' THEN TRUE
    WHEN k.key = 'app.mcp.saveDraft' THEN TRUE
    WHEN k.key = 'app.mcp.search' THEN TRUE
    WHEN k.key = 'app.mcp.searchByName' THEN TRUE
    WHEN k.key = 'app.mcp.searchTitle' THEN EXISTS (SELECT 1 FROM translations t2 JOIN translation_keys k2 ON k2.id = t2.key_id WHERE k2.key = 'app.skills.searchTitle' AND t2.language = 'en')
    WHEN k.key = 'app.mcp.searchUserDesc' THEN EXISTS (SELECT 1 FROM translations t2 JOIN translation_keys k2 ON k2.id = t2.key_id WHERE k2.key = 'app.skills.searchUserDesc' AND t2.language = 'en')
    WHEN k.key = 'app.mcp.selectCreator' THEN EXISTS (SELECT 1 FROM translations t2 JOIN translation_keys k2 ON k2.id = t2.key_id WHERE k2.key = 'app.skills.selectCreator' AND t2.language = 'en')
    WHEN k.key = 'app.mcp.selected' THEN EXISTS (SELECT 1 FROM translations t2 JOIN translation_keys k2 ON k2.id = t2.key_id WHERE k2.key = 'app.skills.selected' AND t2.language = 'en')
    WHEN k.key = 'app.mcp.selectedCount' THEN TRUE
    WHEN k.key = 'app.mcp.send' THEN EXISTS (SELECT 1 FROM translations t2 JOIN translation_keys k2 ON k2.id = t2.key_id WHERE k2.key = 'app.skills.send' AND t2.language = 'en')
    WHEN k.key = 'app.mcp.serverKey' THEN FALSE
    WHEN k.key = 'app.mcp.showMore' THEN TRUE
    WHEN k.key = 'app.mcp.skillsTab' THEN TRUE
    WHEN k.key = 'app.mcp.stageAdded' THEN EXISTS (SELECT 1 FROM translations t2 JOIN translation_keys k2 ON k2.id = t2.key_id WHERE k2.key = 'app.skills.stageAdded' AND t2.language = 'en')
    WHEN k.key = 'app.mcp.stageDeleteDesc' THEN EXISTS (SELECT 1 FROM translations t2 JOIN translation_keys k2 ON k2.id = t2.key_id WHERE k2.key = 'app.skills.stageDeleteDesc' AND t2.language = 'en')
    WHEN k.key = 'app.mcp.stageDeleteDescSimple' THEN EXISTS (SELECT 1 FROM translations t2 JOIN translation_keys k2 ON k2.id = t2.key_id WHERE k2.key = 'app.skills.stageDeleteDescSimple' AND t2.language = 'en')
    WHEN k.key = 'app.mcp.stageDeleted' THEN EXISTS (SELECT 1 FROM translations t2 JOIN translation_keys k2 ON k2.id = t2.key_id WHERE k2.key = 'app.skills.stageDeleted' AND t2.language = 'en')
    WHEN k.key = 'app.mcp.stageDescLabel' THEN EXISTS (SELECT 1 FROM translations t2 JOIN translation_keys k2 ON k2.id = t2.key_id WHERE k2.key = 'app.skills.stageDescLabel' AND t2.language = 'en')
    WHEN k.key = 'app.mcp.stageDescPh' THEN EXISTS (SELECT 1 FROM translations t2 JOIN translation_keys k2 ON k2.id = t2.key_id WHERE k2.key = 'app.skills.stageDescPh' AND t2.language = 'en')
    WHEN k.key = 'app.mcp.stageName' THEN EXISTS (SELECT 1 FROM translations t2 JOIN translation_keys k2 ON k2.id = t2.key_id WHERE k2.key = 'app.skills.stageName' AND t2.language = 'en')
    WHEN k.key = 'app.mcp.stageUpdated' THEN EXISTS (SELECT 1 FROM translations t2 JOIN translation_keys k2 ON k2.id = t2.key_id WHERE k2.key = 'app.skills.stageUpdated' AND t2.language = 'en')
    WHEN k.key = 'app.mcp.status' THEN TRUE
    WHEN k.key = 'app.mcp.submit' THEN TRUE
    WHEN k.key = 'app.mcp.submitForReview' THEN EXISTS (SELECT 1 FROM translations t2 JOIN translation_keys k2 ON k2.id = t2.key_id WHERE k2.key = 'app.skills.submitForReview' AND t2.language = 'en')
    WHEN k.key = 'app.mcp.submittedToast' THEN TRUE
    WHEN k.key = 'app.mcp.subscribe' THEN TRUE
    WHEN k.key = 'app.mcp.subscribeAll' THEN EXISTS (SELECT 1 FROM translations t2 JOIN translation_keys k2 ON k2.id = t2.key_id WHERE k2.key = 'app.skills.subscribeAll' AND t2.language = 'en')
    WHEN k.key = 'app.mcp.subscribeN' THEN EXISTS (SELECT 1 FROM translations t2 JOIN translation_keys k2 ON k2.id = t2.key_id WHERE k2.key = 'app.skills.subscribeN' AND t2.language = 'en')
    WHEN k.key = 'app.mcp.subscribeToast' THEN TRUE
    WHEN k.key = 'app.mcp.subscribeY' THEN EXISTS (SELECT 1 FROM translations t2 JOIN translation_keys k2 ON k2.id = t2.key_id WHERE k2.key = 'app.skills.subscribeY' AND t2.language = 'en')
    WHEN k.key = 'app.mcp.subscribed' THEN EXISTS (SELECT 1 FROM translations t2 JOIN translation_keys k2 ON k2.id = t2.key_id WHERE k2.key = 'app.skills.subscribed' AND t2.language = 'en')
    WHEN k.key = 'app.mcp.subscribedToast' THEN EXISTS (SELECT 1 FROM translations t2 JOIN translation_keys k2 ON k2.id = t2.key_id WHERE k2.key = 'app.skills.subscribedToast' AND t2.language = 'en')
    WHEN k.key = 'app.mcp.tabComments' THEN EXISTS (SELECT 1 FROM translations t2 JOIN translation_keys k2 ON k2.id = t2.key_id WHERE k2.key = 'app.skills.tabComments' AND t2.language = 'en')
    WHEN k.key = 'app.mcp.tabInfo' THEN EXISTS (SELECT 1 FROM translations t2 JOIN translation_keys k2 ON k2.id = t2.key_id WHERE k2.key = 'app.skills.tabInfo' AND t2.language = 'en')
    WHEN k.key = 'app.mcp.tabSkills' THEN TRUE
    WHEN k.key = 'app.mcp.tabVersions' THEN EXISTS (SELECT 1 FROM translations t2 JOIN translation_keys k2 ON k2.id = t2.key_id WHERE k2.key = 'app.skills.tabVersions' AND t2.language = 'en')
    WHEN k.key = 'app.mcp.title' THEN TRUE
    WHEN k.key = 'app.mcp.underReview' THEN EXISTS (SELECT 1 FROM translations t2 JOIN translation_keys k2 ON k2.id = t2.key_id WHERE k2.key = 'app.skills.underReview' AND t2.language = 'en')
    WHEN k.key = 'app.mcp.unsubscribedToast' THEN EXISTS (SELECT 1 FROM translations t2 JOIN translation_keys k2 ON k2.id = t2.key_id WHERE k2.key = 'app.skills.unsubscribedToast' AND t2.language = 'en')
    WHEN k.key = 'app.mcp.updateFileRequired' THEN EXISTS (SELECT 1 FROM translations t2 JOIN translation_keys k2 ON k2.id = t2.key_id WHERE k2.key = 'app.skills.updateFileRequired' AND t2.language = 'en')
    WHEN k.key = 'app.mcp.updateFilesHint' THEN EXISTS (SELECT 1 FROM translations t2 JOIN translation_keys k2 ON k2.id = t2.key_id WHERE k2.key = 'app.skills.updateFilesHint' AND t2.language = 'en')
    WHEN k.key = 'app.mcp.updateHint' THEN EXISTS (SELECT 1 FROM translations t2 JOIN translation_keys k2 ON k2.id = t2.key_id WHERE k2.key = 'app.skills.updateHint' AND t2.language = 'en')
    WHEN k.key = 'app.mcp.updateInfo' THEN TRUE
    WHEN k.key = 'app.mcp.updateInfoPh' THEN TRUE
    WHEN k.key = 'app.mcp.updateInfoRequired' THEN TRUE
    WHEN k.key = 'app.mcp.updateInfoTooLong' THEN TRUE
    WHEN k.key = 'app.mcp.updateSubmitted' THEN EXISTS (SELECT 1 FROM translations t2 JOIN translation_keys k2 ON k2.id = t2.key_id WHERE k2.key = 'app.skills.updateSubmitted' AND t2.language = 'en')
    WHEN k.key = 'app.mcp.updateTitle' THEN EXISTS (SELECT 1 FROM translations t2 JOIN translation_keys k2 ON k2.id = t2.key_id WHERE k2.key = 'app.skills.updateTitle' AND t2.language = 'en')
    WHEN k.key = 'app.mcp.updateVersion' THEN EXISTS (SELECT 1 FROM translations t2 JOIN translation_keys k2 ON k2.id = t2.key_id WHERE k2.key = 'app.skills.updateVersion' AND t2.language = 'en')
    WHEN k.key = 'app.mcp.updated' THEN EXISTS (SELECT 1 FROM translations t2 JOIN translation_keys k2 ON k2.id = t2.key_id WHERE k2.key = 'app.skills.updated' AND t2.language = 'en')
    WHEN k.key = 'app.mcp.updatedToast' THEN EXISTS (SELECT 1 FROM translations t2 JOIN translation_keys k2 ON k2.id = t2.key_id WHERE k2.key = 'app.skills.updatedToast' AND t2.language = 'en')
    WHEN k.key = 'app.mcp.uploadNewFile' THEN TRUE
    WHEN k.key = 'app.mcp.version' THEN EXISTS (SELECT 1 FROM translations t2 JOIN translation_keys k2 ON k2.id = t2.key_id WHERE k2.key = 'app.skills.version' AND t2.language = 'en')
    WHEN k.key = 'app.mcp.versionApproved' THEN EXISTS (SELECT 1 FROM translations t2 JOIN translation_keys k2 ON k2.id = t2.key_id WHERE k2.key = 'app.skills.versionApproved' AND t2.language = 'en')
    WHEN k.key = 'app.mcp.versionCancelled' THEN EXISTS (SELECT 1 FROM translations t2 JOIN translation_keys k2 ON k2.id = t2.key_id WHERE k2.key = 'app.skills.versionCancelled' AND t2.language = 'en')
    WHEN k.key = 'app.mcp.versionConfirmDesc' THEN EXISTS (SELECT 1 FROM translations t2 JOIN translation_keys k2 ON k2.id = t2.key_id WHERE k2.key = 'app.skill.versionConfirmDesc' AND t2.language = 'en')
    WHEN k.key = 'app.mcp.versionConfirmTitle' THEN EXISTS (SELECT 1 FROM translations t2 JOIN translation_keys k2 ON k2.id = t2.key_id WHERE k2.key = 'app.skill.versionConfirmTitle' AND t2.language = 'en')
    WHEN k.key = 'app.mcp.versionCreatedToast' THEN TRUE
    WHEN k.key = 'app.mcp.versionInfo' THEN TRUE
    WHEN k.key = 'app.mcp.versionNote' THEN TRUE
    WHEN k.key = 'app.mcp.versionNum' THEN TRUE
    WHEN k.key = 'app.mcp.versionRejected' THEN EXISTS (SELECT 1 FROM translations t2 JOIN translation_keys k2 ON k2.id = t2.key_id WHERE k2.key = 'app.skills.versionRejected' AND t2.language = 'en')
    WHEN k.key = 'app.mcp.viewCount' THEN TRUE
    WHEN k.key = 'app.mcp.withdraw' THEN EXISTS (SELECT 1 FROM translations t2 JOIN translation_keys k2 ON k2.id = t2.key_id WHERE k2.key = 'app.skills.withdraw' AND t2.language = 'en')
    WHEN k.key = 'app.mcp.withdrawDesc' THEN EXISTS (SELECT 1 FROM translations t2 JOIN translation_keys k2 ON k2.id = t2.key_id WHERE k2.key = 'app.skills.withdrawDesc' AND t2.language = 'en')
    WHEN k.key = 'app.mcp.withdrawTitle' THEN EXISTS (SELECT 1 FROM translations t2 JOIN translation_keys k2 ON k2.id = t2.key_id WHERE k2.key = 'app.skills.withdrawTitle' AND t2.language = 'en')
    WHEN k.key = 'app.mcp.withdrawn' THEN EXISTS (SELECT 1 FROM translations t2 JOIN translation_keys k2 ON k2.id = t2.key_id WHERE k2.key = 'app.skills.withdrawn' AND t2.language = 'en')
    WHEN k.key = 'app.mcp.withdrawnToast' THEN TRUE
    WHEN k.key = 'app.mcp.writeComment' THEN EXISTS (SELECT 1 FROM translations t2 JOIN translation_keys k2 ON k2.id = t2.key_id WHERE k2.key = 'app.skills.writeComment' AND t2.language = 'en')
    WHEN k.key = 'app.mcp.youRate' THEN TRUE
  END
ON CONFLICT (key_id, language) DO NOTHING;

-- vi
INSERT INTO translations (key_id, language, value)
SELECT k.id, 'vi',
  CASE
    WHEN k.key = 'app.mcp.addBtn' THEN (SELECT value FROM translations t2 JOIN translation_keys k2 ON k2.id = t2.key_id WHERE k2.key = 'app.skills.addBtn' AND t2.language = 'vi')
    WHEN k.key = 'app.mcp.addTitle' THEN (SELECT value FROM translations t2 JOIN translation_keys k2 ON k2.id = t2.key_id WHERE k2.key = 'app.skills.addTitle' AND t2.language = 'vi')
    WHEN k.key = 'app.mcp.adminBadge' THEN (SELECT value FROM translations t2 JOIN translation_keys k2 ON k2.id = t2.key_id WHERE k2.key = 'app.skills.adminBadge' AND t2.language = 'vi')
    WHEN k.key = 'app.mcp.adminOnlyCat' THEN (SELECT value FROM translations t2 JOIN translation_keys k2 ON k2.id = t2.key_id WHERE k2.key = 'app.skills.adminOnlyCat' AND t2.language = 'vi')
    WHEN k.key = 'app.mcp.adminOnlyStage' THEN (SELECT value FROM translations t2 JOIN translation_keys k2 ON k2.id = t2.key_id WHERE k2.key = 'app.skills.adminOnlyStage' AND t2.language = 'vi')
    WHEN k.key = 'app.mcp.allStatuses' THEN 'Tất cả trạng thái'
    WHEN k.key = 'app.mcp.approve' THEN (SELECT value FROM translations t2 JOIN translation_keys k2 ON k2.id = t2.key_id WHERE k2.key = 'app.skills.approve' AND t2.language = 'vi')
    WHEN k.key = 'app.mcp.approveVersion' THEN (SELECT value FROM translations t2 JOIN translation_keys k2 ON k2.id = t2.key_id WHERE k2.key = 'app.skills.approveVersion' AND t2.language = 'vi')
    WHEN k.key = 'app.mcp.approved' THEN 'Đã phê duyệt'
    WHEN k.key = 'app.mcp.approvedFilesHint' THEN (SELECT value FROM translations t2 JOIN translation_keys k2 ON k2.id = t2.key_id WHERE k2.key = 'app.skills.approvedFilesHint' AND t2.language = 'vi')
    WHEN k.key = 'app.mcp.approvedMcpTab' THEN 'MCP đã phê duyệt ({n})'
    WHEN k.key = 'app.mcp.approvedToast' THEN 'MCP đã được phê duyệt.'
    WHEN k.key = 'app.mcp.browseTitle' THEN 'Xem tất cả MCP'
    WHEN k.key = 'app.mcp.cancel' THEN 'Hủy'
    WHEN k.key = 'app.mcp.cancelUpdateBtn' THEN (SELECT value FROM translations t2 JOIN translation_keys k2 ON k2.id = t2.key_id WHERE k2.key = 'app.skills.cancelUpdateBtn' AND t2.language = 'vi')
    WHEN k.key = 'app.mcp.cancelUpdateDesc' THEN (SELECT value FROM translations t2 JOIN translation_keys k2 ON k2.id = t2.key_id WHERE k2.key = 'app.skills.cancelUpdateDesc' AND t2.language = 'vi')
    WHEN k.key = 'app.mcp.cancelUpdateTitle' THEN (SELECT value FROM translations t2 JOIN translation_keys k2 ON k2.id = t2.key_id WHERE k2.key = 'app.skills.cancelUpdateTitle' AND t2.language = 'vi')
    WHEN k.key = 'app.mcp.cancelVersionUpdate' THEN (SELECT value FROM translations t2 JOIN translation_keys k2 ON k2.id = t2.key_id WHERE k2.key = 'app.skills.cancelVersionUpdate' AND t2.language = 'vi')
    WHEN k.key = 'app.mcp.cannotDeleteItem' THEN (SELECT value FROM translations t2 JOIN translation_keys k2 ON k2.id = t2.key_id WHERE k2.key = 'app.skills.cannotDeleteItem' AND t2.language = 'vi')
    WHEN k.key = 'app.mcp.cannotDeleteList' THEN (SELECT value FROM translations t2 JOIN translation_keys k2 ON k2.id = t2.key_id WHERE k2.key = 'app.skills.cannotDeleteList' AND t2.language = 'vi')
    WHEN k.key = 'app.mcp.cannotDeleteTitle' THEN (SELECT value FROM translations t2 JOIN translation_keys k2 ON k2.id = t2.key_id WHERE k2.key = 'app.skills.cannotDeleteTitle' AND t2.language = 'vi')
    WHEN k.key = 'app.mcp.cannotDeleteTotal' THEN (SELECT value FROM translations t2 JOIN translation_keys k2 ON k2.id = t2.key_id WHERE k2.key = 'app.skills.cannotDeleteTotal' AND t2.language = 'vi')
    WHEN k.key = 'app.mcp.cardsAria' THEN (SELECT value FROM translations t2 JOIN translation_keys k2 ON k2.id = t2.key_id WHERE k2.key = 'app.skills.cardsAria' AND t2.language = 'vi')
    WHEN k.key = 'app.mcp.catAdded' THEN (SELECT value FROM translations t2 JOIN translation_keys k2 ON k2.id = t2.key_id WHERE k2.key = 'app.skills.catAdded' AND t2.language = 'vi')
    WHEN k.key = 'app.mcp.catDeleteDesc' THEN (SELECT value FROM translations t2 JOIN translation_keys k2 ON k2.id = t2.key_id WHERE k2.key = 'app.skills.catDeleteDesc' AND t2.language = 'vi')
    WHEN k.key = 'app.mcp.catDeleteDescSimple' THEN (SELECT value FROM translations t2 JOIN translation_keys k2 ON k2.id = t2.key_id WHERE k2.key = 'app.skills.catDeleteDescSimple' AND t2.language = 'vi')
    WHEN k.key = 'app.mcp.catDeleted' THEN (SELECT value FROM translations t2 JOIN translation_keys k2 ON k2.id = t2.key_id WHERE k2.key = 'app.skills.catDeleted' AND t2.language = 'vi')
    WHEN k.key = 'app.mcp.catUpdated' THEN (SELECT value FROM translations t2 JOIN translation_keys k2 ON k2.id = t2.key_id WHERE k2.key = 'app.skills.catUpdated' AND t2.language = 'vi')
    WHEN k.key = 'app.mcp.categories' THEN (SELECT value FROM translations t2 JOIN translation_keys k2 ON k2.id = t2.key_id WHERE k2.key = 'app.skills.categories' AND t2.language = 'vi')
    WHEN k.key = 'app.mcp.categoryName' THEN (SELECT value FROM translations t2 JOIN translation_keys k2 ON k2.id = t2.key_id WHERE k2.key = 'app.skills.categoryName' AND t2.language = 'vi')
    WHEN k.key = 'app.mcp.changelog' THEN 'Nhật ký thay đổi'
    WHEN k.key = 'app.mcp.changelogPh' THEN 'Mô tả những gì đã thay đổi trong phiên bản này...'
    WHEN k.key = 'app.mcp.changelogRequired' THEN 'Nhật ký thay đổi là bắt buộc cho phiên bản mới.'
    WHEN k.key = 'app.mcp.changelogTooLong' THEN 'Nhật ký thay đổi tối đa {n} ký tự.'
    WHEN k.key = 'app.mcp.clearCreator' THEN (SELECT value FROM translations t2 JOIN translation_keys k2 ON k2.id = t2.key_id WHERE k2.key = 'app.skills.clearCreator' AND t2.language = 'vi')
    WHEN k.key = 'app.mcp.close' THEN 'Đóng'
    WHEN k.key = 'app.mcp.colCreated' THEN 'Ngày tạo'
    WHEN k.key = 'app.mcp.colCreator' THEN (SELECT value FROM translations t2 JOIN translation_keys k2 ON k2.id = t2.key_id WHERE k2.key = 'app.skills.colCreator' AND t2.language = 'vi')
    WHEN k.key = 'app.mcp.colDescription' THEN 'Mô tả'
    WHEN k.key = 'app.mcp.colDownloads' THEN (SELECT value FROM translations t2 JOIN translation_keys k2 ON k2.id = t2.key_id WHERE k2.key = 'app.skills.colDownloads' AND t2.language = 'vi')
    WHEN k.key = 'app.mcp.colName' THEN (SELECT value FROM translations t2 JOIN translation_keys k2 ON k2.id = t2.key_id WHERE k2.key = 'app.skills.colName' AND t2.language = 'vi')
    WHEN k.key = 'app.mcp.colRating' THEN (SELECT value FROM translations t2 JOIN translation_keys k2 ON k2.id = t2.key_id WHERE k2.key = 'app.skills.colRating' AND t2.language = 'vi')
    WHEN k.key = 'app.mcp.colStatus' THEN 'Trạng thái'
    WHEN k.key = 'app.mcp.colSubscribe' THEN (SELECT value FROM translations t2 JOIN translation_keys k2 ON k2.id = t2.key_id WHERE k2.key = 'app.skills.colSubscribe' AND t2.language = 'vi')
    WHEN k.key = 'app.mcp.colViews' THEN (SELECT value FROM translations t2 JOIN translation_keys k2 ON k2.id = t2.key_id WHERE k2.key = 'app.skills.colViews' AND t2.language = 'vi')
    WHEN k.key = 'app.mcp.confirm' THEN 'Xác nhận'
    WHEN k.key = 'app.mcp.copyLink' THEN (SELECT value FROM translations t2 JOIN translation_keys k2 ON k2.id = t2.key_id WHERE k2.key = 'app.skill.copyLink' AND t2.language = 'vi')
    WHEN k.key = 'app.mcp.copyLinkCopied' THEN (SELECT value FROM translations t2 JOIN translation_keys k2 ON k2.id = t2.key_id WHERE k2.key = 'app.skill.copyLinkCopied' AND t2.language = 'vi')
    WHEN k.key = 'app.mcp.copyLinkFailed' THEN (SELECT value FROM translations t2 JOIN translation_keys k2 ON k2.id = t2.key_id WHERE k2.key = 'app.skill.copyLinkFailed' AND t2.language = 'vi')
    WHEN k.key = 'app.mcp.copyLinkTooltip' THEN (SELECT value FROM translations t2 JOIN translation_keys k2 ON k2.id = t2.key_id WHERE k2.key = 'app.skill.copyLinkTooltip' AND t2.language = 'vi')
    WHEN k.key = 'app.mcp.createVersion' THEN 'Tạo phiên bản'
    WHEN k.key = 'app.mcp.createdToast' THEN (SELECT value FROM translations t2 JOIN translation_keys k2 ON k2.id = t2.key_id WHERE k2.key = 'app.skills.createdToast' AND t2.language = 'vi')
    WHEN k.key = 'app.mcp.createdUpdated' THEN (SELECT value FROM translations t2 JOIN translation_keys k2 ON k2.id = t2.key_id WHERE k2.key = 'app.skills.createdUpdated' AND t2.language = 'vi')
    WHEN k.key = 'app.mcp.creator' THEN (SELECT value FROM translations t2 JOIN translation_keys k2 ON k2.id = t2.key_id WHERE k2.key = 'app.skills.creator' AND t2.language = 'vi')
    WHEN k.key = 'app.mcp.current' THEN (SELECT value FROM translations t2 JOIN translation_keys k2 ON k2.id = t2.key_id WHERE k2.key = 'app.skills.current' AND t2.language = 'vi')
    WHEN k.key = 'app.mcp.currentFile' THEN 'Tệp hiện tại'
    WHEN k.key = 'app.mcp.deleteBtn' THEN (SELECT value FROM translations t2 JOIN translation_keys k2 ON k2.id = t2.key_id WHERE k2.key = 'app.skills.deleteBtn' AND t2.language = 'vi')
    WHEN k.key = 'app.mcp.deleteCategoryAria' THEN (SELECT value FROM translations t2 JOIN translation_keys k2 ON k2.id = t2.key_id WHERE k2.key = 'app.skills.deleteCategoryAria' AND t2.language = 'vi')
    WHEN k.key = 'app.mcp.deleteCommentAria' THEN (SELECT value FROM translations t2 JOIN translation_keys k2 ON k2.id = t2.key_id WHERE k2.key = 'app.skills.deleteCommentAria' AND t2.language = 'vi')
    WHEN k.key = 'app.mcp.deleteDeniedDesc' THEN 'Bạn không có quyền xóa một hoặc nhiều MCP đã chọn.'
    WHEN k.key = 'app.mcp.deleteDeniedList' THEN 'Các MCP bạn không thể xóa:'
    WHEN k.key = 'app.mcp.deleteDeniedTitle' THEN 'Không thể xóa tất cả MCP đã chọn'
    WHEN k.key = 'app.mcp.deleteDeniedTotal' THEN 'Tổng: {n} MCP đã chọn, {m} không thể xóa.'
    WHEN k.key = 'app.mcp.deleteDesc' THEN (SELECT value FROM translations t2 JOIN translation_keys k2 ON k2.id = t2.key_id WHERE k2.key = 'app.skills.deleteDesc' AND t2.language = 'vi')
    WHEN k.key = 'app.mcp.deleteStageAria' THEN (SELECT value FROM translations t2 JOIN translation_keys k2 ON k2.id = t2.key_id WHERE k2.key = 'app.skills.deleteStageAria' AND t2.language = 'vi')
    WHEN k.key = 'app.mcp.deleteTitle' THEN (SELECT value FROM translations t2 JOIN translation_keys k2 ON k2.id = t2.key_id WHERE k2.key = 'app.skills.deleteTitle' AND t2.language = 'vi')
    WHEN k.key = 'app.mcp.deletedToast' THEN (SELECT value FROM translations t2 JOIN translation_keys k2 ON k2.id = t2.key_id WHERE k2.key = 'app.skills.deletedToast' AND t2.language = 'vi')
    WHEN k.key = 'app.mcp.deletedToastDetail' THEN (SELECT value FROM translations t2 JOIN translation_keys k2 ON k2.id = t2.key_id WHERE k2.key = 'app.skills.deletedToastDetail' AND t2.language = 'vi')
    WHEN k.key = 'app.mcp.depAlready' THEN 'MCP này đã có trong danh sách phụ thuộc.'
    WHEN k.key = 'app.mcp.depBanner' THEN 'Skill này không thể được gửi cho đến khi các MCP sau được phê duyệt:'
    WHEN k.key = 'app.mcp.depDeleted' THEN 'Đã bị xóa'
    WHEN k.key = 'app.mcp.depDeletedTip' THEN 'MCP này đã bị xóa. Skill vẫn hoạt động — bạn có thể gỡ liên kết này.'
    WHEN k.key = 'app.mcp.depLabel' THEN 'Phụ thuộc MCP'
    WHEN k.key = 'app.mcp.depLoading' THEN 'Đang tải gợi ý...'
    WHEN k.key = 'app.mcp.depPh' THEN 'Chọn phụ thuộc MCP...'
    WHEN k.key = 'app.mcp.dependentSkills' THEN NULL
    WHEN k.key = 'app.mcp.descRequired' THEN 'Mô tả không được để trống.'
    WHEN k.key = 'app.mcp.description' THEN 'Mô tả'
    WHEN k.key = 'app.mcp.descriptionPh' THEN 'Nhập mô tả và hướng dẫn sử dụng MCP...'
    WHEN k.key = 'app.mcp.detailTitle' THEN (SELECT value FROM translations t2 JOIN translation_keys k2 ON k2.id = t2.key_id WHERE k2.key = 'app.skills.detailTitle' AND t2.language = 'vi')
    WHEN k.key = 'app.mcp.download' THEN 'Tải xuống'
    WHEN k.key = 'app.mcp.downloadAll' THEN (SELECT value FROM translations t2 JOIN translation_keys k2 ON k2.id = t2.key_id WHERE k2.key = 'app.skills.downloadAll' AND t2.language = 'vi')
    WHEN k.key = 'app.mcp.downloadCount' THEN 'Lượt tải: {n}'
    WHEN k.key = 'app.mcp.downloadExcel' THEN (SELECT value FROM translations t2 JOIN translation_keys k2 ON k2.id = t2.key_id WHERE k2.key = 'app.skills.downloadExcel' AND t2.language = 'vi')
    WHEN k.key = 'app.mcp.draft' THEN 'Bản nháp'
    WHEN k.key = 'app.mcp.draftHint' THEN (SELECT value FROM translations t2 JOIN translation_keys k2 ON k2.id = t2.key_id WHERE k2.key = 'app.skills.draftHint' AND t2.language = 'vi')
    WHEN k.key = 'app.mcp.draftNote' THEN 'MCP được tạo ở trạng thái Bản nháp. Bạn có thể chỉnh sửa và gửi phê duyệt khi sẵn sàng.'
    WHEN k.key = 'app.mcp.dragDropNewOr' THEN (SELECT value FROM translations t2 JOIN translation_keys k2 ON k2.id = t2.key_id WHERE k2.key = 'app.skills.dragDropNewOr' AND t2.language = 'vi')
    WHEN k.key = 'app.mcp.editBtn' THEN (SELECT value FROM translations t2 JOIN translation_keys k2 ON k2.id = t2.key_id WHERE k2.key = 'app.skills.editBtn' AND t2.language = 'vi')
    WHEN k.key = 'app.mcp.editCategoryAria' THEN (SELECT value FROM translations t2 JOIN translation_keys k2 ON k2.id = t2.key_id WHERE k2.key = 'app.skills.editCategoryAria' AND t2.language = 'vi')
    WHEN k.key = 'app.mcp.editCategoryTitle' THEN (SELECT value FROM translations t2 JOIN translation_keys k2 ON k2.id = t2.key_id WHERE k2.key = 'app.skills.editCategoryTitle' AND t2.language = 'vi')
    WHEN k.key = 'app.mcp.editNoteApproved' THEN 'MCP này đã được phê duyệt. Tên, mô tả và hashtag được cập nhật ngay lập tức mà không cần phê duyệt lại.'
    WHEN k.key = 'app.mcp.editNoteStatus' THEN 'Bạn đang chỉnh sửa MCP ở trạng thái {status}. Thay đổi giữ nguyên trạng thái.'
    WHEN k.key = 'app.mcp.editStageAria' THEN (SELECT value FROM translations t2 JOIN translation_keys k2 ON k2.id = t2.key_id WHERE k2.key = 'app.skills.editStageAria' AND t2.language = 'vi')
    WHEN k.key = 'app.mcp.editStageTitle' THEN (SELECT value FROM translations t2 JOIN translation_keys k2 ON k2.id = t2.key_id WHERE k2.key = 'app.skills.editStageTitle' AND t2.language = 'vi')
    WHEN k.key = 'app.mcp.editTitle' THEN (SELECT value FROM translations t2 JOIN translation_keys k2 ON k2.id = t2.key_id WHERE k2.key = 'app.skills.editTitle' AND t2.language = 'vi')
    WHEN k.key = 'app.mcp.emptyDesc' THEN (SELECT value FROM translations t2 JOIN translation_keys k2 ON k2.id = t2.key_id WHERE k2.key = 'app.skills.emptyDesc' AND t2.language = 'vi')
    WHEN k.key = 'app.mcp.emptyTitle' THEN 'Không tìm thấy dữ liệu.'
    WHEN k.key = 'app.mcp.exportedToast' THEN (SELECT value FROM translations t2 JOIN translation_keys k2 ON k2.id = t2.key_id WHERE k2.key = 'app.skills.exportedToast' AND t2.language = 'vi')
    WHEN k.key = 'app.mcp.file' THEN 'Tệp'
    WHEN k.key = 'app.mcp.fileDropOr' THEN 'Kéo & thả tệp vào đây hoặc'
    WHEN k.key = 'app.mcp.fileErrorFormat' THEN '"{name}": định dạng tệp không được hỗ trợ. Cho phép: {exts}.'
    WHEN k.key = 'app.mcp.fileErrorSize' THEN '"{name}": dung lượng tệp tối đa 50MB.'
    WHEN k.key = 'app.mcp.fileKeepHint' THEN 'Giữ tệp hiện tại hoặc tải lên tệp mới.'
    WHEN k.key = 'app.mcp.fileRemoved' THEN (SELECT value FROM translations t2 JOIN translation_keys k2 ON k2.id = t2.key_id WHERE k2.key = 'app.skills.fileRemoved' AND t2.language = 'vi')
    WHEN k.key = 'app.mcp.fileRequired' THEN (SELECT value FROM translations t2 JOIN translation_keys k2 ON k2.id = t2.key_id WHERE k2.key = 'app.skills.fileRequired' AND t2.language = 'vi')
    WHEN k.key = 'app.mcp.fileRequiredUpload' THEN 'Tệp là bắt buộc khi tải lên tệp mới.'
    WHEN k.key = 'app.mcp.fileSupported' THEN 'Hỗ trợ: zip, 7z, json, yaml, yml, txt, md · tối đa 50MB'
    WHEN k.key = 'app.mcp.filesToRemove' THEN (SELECT value FROM translations t2 JOIN translation_keys k2 ON k2.id = t2.key_id WHERE k2.key = 'app.skills.filesToRemove' AND t2.language = 'vi')
    WHEN k.key = 'app.mcp.hashtag' THEN 'Hashtag'
    WHEN k.key = 'app.mcp.hashtagExists' THEN 'Hashtag đã tồn tại.'
    WHEN k.key = 'app.mcp.hashtagPh' THEN 'Nhập hashtag và nhấn Enter...'
    WHEN k.key = 'app.mcp.hashtagRemoveAria' THEN 'Xóa hashtag'
    WHEN k.key = 'app.mcp.lastRejected' THEN (SELECT value FROM translations t2 JOIN translation_keys k2 ON k2.id = t2.key_id WHERE k2.key = 'app.skills.lastRejected' AND t2.language = 'vi')
    WHEN k.key = 'app.mcp.listAria' THEN (SELECT value FROM translations t2 JOIN translation_keys k2 ON k2.id = t2.key_id WHERE k2.key = 'app.skills.listAria' AND t2.language = 'vi')
    WHEN k.key = 'app.mcp.loadError' THEN (SELECT value FROM translations t2 JOIN translation_keys k2 ON k2.id = t2.key_id WHERE k2.key = 'app.skills.loadError' AND t2.language = 'vi')
    WHEN k.key = 'app.mcp.loadErrorDetail' THEN (SELECT value FROM translations t2 JOIN translation_keys k2 ON k2.id = t2.key_id WHERE k2.key = 'app.skills.loadErrorDetail' AND t2.language = 'vi')
    WHEN k.key = 'app.mcp.manageTaxonomy' THEN (SELECT value FROM translations t2 JOIN translation_keys k2 ON k2.id = t2.key_id WHERE k2.key = 'app.skills.manageTaxonomy' AND t2.language = 'vi')
    WHEN k.key = 'app.mcp.manageTaxonomyTitle' THEN (SELECT value FROM translations t2 JOIN translation_keys k2 ON k2.id = t2.key_id WHERE k2.key = 'app.skills.manageTaxonomyTitle' AND t2.language = 'vi')
    WHEN k.key = 'app.mcp.mcpCode' THEN NULL
    WHEN k.key = 'app.mcp.mcpCount' THEN (SELECT value FROM translations t2 JOIN translation_keys k2 ON k2.id = t2.key_id WHERE k2.key = 'app.skills.skillCount' AND t2.language = 'vi')
    WHEN k.key = 'app.mcp.mcpDeleted' THEN (SELECT value FROM translations t2 JOIN translation_keys k2 ON k2.id = t2.key_id WHERE k2.key = 'app.skills.mcpDeleted' AND t2.language = 'vi')
    WHEN k.key = 'app.mcp.mcpDeletedTip' THEN (SELECT value FROM translations t2 JOIN translation_keys k2 ON k2.id = t2.key_id WHERE k2.key = 'app.skills.mcpDeletedTip' AND t2.language = 'vi')
    WHEN k.key = 'app.mcp.mcpDepsWarnDesc' THEN (SELECT value FROM translations t2 JOIN translation_keys k2 ON k2.id = t2.key_id WHERE k2.key = 'app.skills.mcpDepsWarnDesc' AND t2.language = 'vi')
    WHEN k.key = 'app.mcp.mcpDepsWarnNote' THEN (SELECT value FROM translations t2 JOIN translation_keys k2 ON k2.id = t2.key_id WHERE k2.key = 'app.skills.mcpDepsWarnNote' AND t2.language = 'vi')
    WHEN k.key = 'app.mcp.mcpDepsWarnTitle' THEN (SELECT value FROM translations t2 JOIN translation_keys k2 ON k2.id = t2.key_id WHERE k2.key = 'app.skills.mcpDepsWarnTitle' AND t2.language = 'vi')
    WHEN k.key = 'app.mcp.mcpUnavailableGone' THEN (SELECT value FROM translations t2 JOIN translation_keys k2 ON k2.id = t2.key_id WHERE k2.key = 'app.skills.mcpUnavailableGone' AND t2.language = 'vi')
    WHEN k.key = 'app.mcp.mcpUnavailableGoneWarn' THEN (SELECT value FROM translations t2 JOIN translation_keys k2 ON k2.id = t2.key_id WHERE k2.key = 'app.skills.mcpUnavailableGoneWarn' AND t2.language = 'vi')
    WHEN k.key = 'app.mcp.mcpUnavailableNotApproved' THEN (SELECT value FROM translations t2 JOIN translation_keys k2 ON k2.id = t2.key_id WHERE k2.key = 'app.skills.mcpUnavailableNotApproved' AND t2.language = 'vi')
    WHEN k.key = 'app.mcp.mcpUnavailableNotApprovedWarn' THEN (SELECT value FROM translations t2 JOIN translation_keys k2 ON k2.id = t2.key_id WHERE k2.key = 'app.skills.mcpUnavailableNotApprovedWarn' AND t2.language = 'vi')
    WHEN k.key = 'app.mcp.myMcpTab' THEN 'MCP của tôi ({n})'
    WHEN k.key = 'app.mcp.myMcpsEmpty' THEN 'Không có MCP nào của bạn ở trạng thái Bản nháp / Chờ duyệt / Bị từ chối.'
    WHEN k.key = 'app.mcp.name' THEN 'Tên MCP'
    WHEN k.key = 'app.mcp.nameEmpty' THEN 'Tên MCP không được để trống.'
    WHEN k.key = 'app.mcp.nameLocked' THEN (SELECT value FROM translations t2 JOIN translation_keys k2 ON k2.id = t2.key_id WHERE k2.key = 'app.skills.nameLocked' AND t2.language = 'vi')
    WHEN k.key = 'app.mcp.namePh' THEN 'Nhập tên MCP...'
    WHEN k.key = 'app.mcp.nameTooLong' THEN 'Tên MCP tối đa {n} ký tự.'
    WHEN k.key = 'app.mcp.newCategoryPh' THEN (SELECT value FROM translations t2 JOIN translation_keys k2 ON k2.id = t2.key_id WHERE k2.key = 'app.skills.newCategoryPh' AND t2.language = 'vi')
    WHEN k.key = 'app.mcp.newFiles' THEN (SELECT value FROM translations t2 JOIN translation_keys k2 ON k2.id = t2.key_id WHERE k2.key = 'app.skills.newFiles' AND t2.language = 'vi')
    WHEN k.key = 'app.mcp.newMcpDraftHint' THEN 'MCP đã được tạo ở trạng thái Draft. Hãy submit để duyệt trong MCP Management, sau đó quay lại Edit skill để thêm MCP này.'
    WHEN k.key = 'app.mcp.newStagePh' THEN (SELECT value FROM translations t2 JOIN translation_keys k2 ON k2.id = t2.key_id WHERE k2.key = 'app.skills.newStagePh' AND t2.language = 'vi')
    WHEN k.key = 'app.mcp.noCategoriesYet' THEN (SELECT value FROM translations t2 JOIN translation_keys k2 ON k2.id = t2.key_id WHERE k2.key = 'app.skills.noCategoriesYet' AND t2.language = 'vi')
    WHEN k.key = 'app.mcp.noComments' THEN (SELECT value FROM translations t2 JOIN translation_keys k2 ON k2.id = t2.key_id WHERE k2.key = 'app.skills.noComments' AND t2.language = 'vi')
    WHEN k.key = 'app.mcp.noDeps' THEN 'Không có phụ thuộc MCP'
    WHEN k.key = 'app.mcp.noDescription' THEN (SELECT value FROM translations t2 JOIN translation_keys k2 ON k2.id = t2.key_id WHERE k2.key = 'app.skills.noDescription' AND t2.language = 'vi')
    WHEN k.key = 'app.mcp.noMatchDesc' THEN (SELECT value FROM translations t2 JOIN translation_keys k2 ON k2.id = t2.key_id WHERE k2.key = 'app.skills.noMatchDesc' AND t2.language = 'vi')
    WHEN k.key = 'app.mcp.noOptions' THEN (SELECT value FROM translations t2 JOIN translation_keys k2 ON k2.id = t2.key_id WHERE k2.key = 'app.skills.noOptions' AND t2.language = 'vi')
    WHEN k.key = 'app.mcp.noRating' THEN 'Chưa có đánh giá'
    WHEN k.key = 'app.mcp.noSkills' THEN 'Không có skill nào đang sử dụng MCP này.'
    WHEN k.key = 'app.mcp.noStagesYet' THEN (SELECT value FROM translations t2 JOIN translation_keys k2 ON k2.id = t2.key_id WHERE k2.key = 'app.skills.noStagesYet' AND t2.language = 'vi')
    WHEN k.key = 'app.mcp.noSuggestions' THEN 'Không có gợi ý'
    WHEN k.key = 'app.mcp.noUsersSelected' THEN (SELECT value FROM translations t2 JOIN translation_keys k2 ON k2.id = t2.key_id WHERE k2.key = 'app.skills.noUsersSelected' AND t2.language = 'vi')
    WHEN k.key = 'app.mcp.noVersions' THEN (SELECT value FROM translations t2 JOIN translation_keys k2 ON k2.id = t2.key_id WHERE k2.key = 'app.skills.noVersions' AND t2.language = 'vi')
    WHEN k.key = 'app.mcp.notFoundOrNoAccess' THEN (SELECT value FROM translations t2 JOIN translation_keys k2 ON k2.id = t2.key_id WHERE k2.key = 'app.skill.notFoundOrNoAccess' AND t2.language = 'vi')
    WHEN k.key = 'app.mcp.onlyApprovedComment' THEN (SELECT value FROM translations t2 JOIN translation_keys k2 ON k2.id = t2.key_id WHERE k2.key = 'app.skills.onlyApprovedComment' AND t2.language = 'vi')
    WHEN k.key = 'app.mcp.onlyApprovedRate' THEN (SELECT value FROM translations t2 JOIN translation_keys k2 ON k2.id = t2.key_id WHERE k2.key = 'app.skills.onlyApprovedRate' AND t2.language = 'vi')
    WHEN k.key = 'app.mcp.openListAria' THEN 'Mở danh sách MCP'
    WHEN k.key = 'app.mcp.optional' THEN (SELECT value FROM translations t2 JOIN translation_keys k2 ON k2.id = t2.key_id WHERE k2.key = 'app.skills.optional' AND t2.language = 'vi')
    WHEN k.key = 'app.mcp.pending' THEN 'Chờ phê duyệt'
    WHEN k.key = 'app.mcp.pendingBanner' THEN (SELECT value FROM translations t2 JOIN translation_keys k2 ON k2.id = t2.key_id WHERE k2.key = 'app.skills.pendingBanner' AND t2.language = 'vi')
    WHEN k.key = 'app.mcp.pendingVersion' THEN (SELECT value FROM translations t2 JOIN translation_keys k2 ON k2.id = t2.key_id WHERE k2.key = 'app.skills.pendingVersion' AND t2.language = 'vi')
    WHEN k.key = 'app.mcp.pendingVersionBanner' THEN (SELECT value FROM translations t2 JOIN translation_keys k2 ON k2.id = t2.key_id WHERE k2.key = 'app.skills.pendingVersionBanner' AND t2.language = 'vi')
    WHEN k.key = 'app.mcp.pinBlockedArchived' THEN 'MCP này đã bị archive. Hãy unarchive trước, sau đó thêm vào skill.'
    WHEN k.key = 'app.mcp.pinBlockedDraft' THEN 'MCP này đang ở trạng thái Draft. Hãy submit để duyệt trước, sau đó thêm vào skill.'
    WHEN k.key = 'app.mcp.pinBlockedPending' THEN 'MCP này đang chờ admin duyệt. Hãy thêm vào skill sau khi được duyệt.'
    WHEN k.key = 'app.mcp.pinBlockedRejected' THEN 'MCP này đã bị reject. Hãy submit lại để duyệt trước, sau đó thêm vào skill.'
    WHEN k.key = 'app.mcp.processStages' THEN (SELECT value FROM translations t2 JOIN translation_keys k2 ON k2.id = t2.key_id WHERE k2.key = 'app.skills.processStages' AND t2.language = 'vi')
    WHEN k.key = 'app.mcp.rateOnlyApproved' THEN 'Chỉ MCP đã phê duyệt mới có thể được đánh giá.'
    WHEN k.key = 'app.mcp.rateStars' THEN (SELECT value FROM translations t2 JOIN translation_keys k2 ON k2.id = t2.key_id WHERE k2.key = 'app.skills.rateStars' AND t2.language = 'vi')
    WHEN k.key = 'app.mcp.reject' THEN (SELECT value FROM translations t2 JOIN translation_keys k2 ON k2.id = t2.key_id WHERE k2.key = 'app.skills.reject' AND t2.language = 'vi')
    WHEN k.key = 'app.mcp.rejectReasonPh' THEN (SELECT value FROM translations t2 JOIN translation_keys k2 ON k2.id = t2.key_id WHERE k2.key = 'app.skills.rejectReasonPh' AND t2.language = 'vi')
    WHEN k.key = 'app.mcp.rejectReasonRequired' THEN (SELECT value FROM translations t2 JOIN translation_keys k2 ON k2.id = t2.key_id WHERE k2.key = 'app.skills.rejectReasonRequired' AND t2.language = 'vi')
    WHEN k.key = 'app.mcp.rejectTitle' THEN (SELECT value FROM translations t2 JOIN translation_keys k2 ON k2.id = t2.key_id WHERE k2.key = 'app.skills.rejectTitle' AND t2.language = 'vi')
    WHEN k.key = 'app.mcp.rejectVersion' THEN (SELECT value FROM translations t2 JOIN translation_keys k2 ON k2.id = t2.key_id WHERE k2.key = 'app.skills.rejectVersion' AND t2.language = 'vi')
    WHEN k.key = 'app.mcp.rejected' THEN 'Bị từ chối'
    WHEN k.key = 'app.mcp.rejectedToast' THEN 'MCP đã bị từ chối.'
    WHEN k.key = 'app.mcp.rejectionReason' THEN (SELECT value FROM translations t2 JOIN translation_keys k2 ON k2.id = t2.key_id WHERE k2.key = 'app.skills.rejectionReason' AND t2.language = 'vi')
    WHEN k.key = 'app.mcp.removeDepAria' THEN 'Xóa {name}'
    WHEN k.key = 'app.mcp.reuseFile' THEN 'Dùng lại tệp hiện tại'
    WHEN k.key = 'app.mcp.reviewSubmitted' THEN (SELECT value FROM translations t2 JOIN translation_keys k2 ON k2.id = t2.key_id WHERE k2.key = 'app.skills.reviewSubmitted' AND t2.language = 'vi')
    WHEN k.key = 'app.mcp.save' THEN 'Lưu'
    WHEN k.key = 'app.mcp.saveDraft' THEN 'Lưu bản nháp'
    WHEN k.key = 'app.mcp.search' THEN 'Tìm kiếm'
    WHEN k.key = 'app.mcp.searchByName' THEN 'Tìm MCP theo tên...'
    WHEN k.key = 'app.mcp.searchTitle' THEN (SELECT value FROM translations t2 JOIN translation_keys k2 ON k2.id = t2.key_id WHERE k2.key = 'app.skills.searchTitle' AND t2.language = 'vi')
    WHEN k.key = 'app.mcp.searchUserDesc' THEN (SELECT value FROM translations t2 JOIN translation_keys k2 ON k2.id = t2.key_id WHERE k2.key = 'app.skills.searchUserDesc' AND t2.language = 'vi')
    WHEN k.key = 'app.mcp.selectCreator' THEN (SELECT value FROM translations t2 JOIN translation_keys k2 ON k2.id = t2.key_id WHERE k2.key = 'app.skills.selectCreator' AND t2.language = 'vi')
    WHEN k.key = 'app.mcp.selected' THEN (SELECT value FROM translations t2 JOIN translation_keys k2 ON k2.id = t2.key_id WHERE k2.key = 'app.skills.selected' AND t2.language = 'vi')
    WHEN k.key = 'app.mcp.selectedCount' THEN 'Đã chọn {n} MCP'
    WHEN k.key = 'app.mcp.send' THEN (SELECT value FROM translations t2 JOIN translation_keys k2 ON k2.id = t2.key_id WHERE k2.key = 'app.skills.send' AND t2.language = 'vi')
    WHEN k.key = 'app.mcp.serverKey' THEN NULL
    WHEN k.key = 'app.mcp.showMore' THEN 'Xem thêm ({n} còn lại)'
    WHEN k.key = 'app.mcp.skillsTab' THEN 'Skill đang dùng MCP này'
    WHEN k.key = 'app.mcp.stageAdded' THEN (SELECT value FROM translations t2 JOIN translation_keys k2 ON k2.id = t2.key_id WHERE k2.key = 'app.skills.stageAdded' AND t2.language = 'vi')
    WHEN k.key = 'app.mcp.stageDeleteDesc' THEN (SELECT value FROM translations t2 JOIN translation_keys k2 ON k2.id = t2.key_id WHERE k2.key = 'app.skills.stageDeleteDesc' AND t2.language = 'vi')
    WHEN k.key = 'app.mcp.stageDeleteDescSimple' THEN (SELECT value FROM translations t2 JOIN translation_keys k2 ON k2.id = t2.key_id WHERE k2.key = 'app.skills.stageDeleteDescSimple' AND t2.language = 'vi')
    WHEN k.key = 'app.mcp.stageDeleted' THEN (SELECT value FROM translations t2 JOIN translation_keys k2 ON k2.id = t2.key_id WHERE k2.key = 'app.skills.stageDeleted' AND t2.language = 'vi')
    WHEN k.key = 'app.mcp.stageDescLabel' THEN (SELECT value FROM translations t2 JOIN translation_keys k2 ON k2.id = t2.key_id WHERE k2.key = 'app.skills.stageDescLabel' AND t2.language = 'vi')
    WHEN k.key = 'app.mcp.stageDescPh' THEN (SELECT value FROM translations t2 JOIN translation_keys k2 ON k2.id = t2.key_id WHERE k2.key = 'app.skills.stageDescPh' AND t2.language = 'vi')
    WHEN k.key = 'app.mcp.stageName' THEN (SELECT value FROM translations t2 JOIN translation_keys k2 ON k2.id = t2.key_id WHERE k2.key = 'app.skills.stageName' AND t2.language = 'vi')
    WHEN k.key = 'app.mcp.stageUpdated' THEN (SELECT value FROM translations t2 JOIN translation_keys k2 ON k2.id = t2.key_id WHERE k2.key = 'app.skills.stageUpdated' AND t2.language = 'vi')
    WHEN k.key = 'app.mcp.status' THEN 'Trạng thái'
    WHEN k.key = 'app.mcp.submit' THEN 'Gửi phê duyệt'
    WHEN k.key = 'app.mcp.submitForReview' THEN (SELECT value FROM translations t2 JOIN translation_keys k2 ON k2.id = t2.key_id WHERE k2.key = 'app.skills.submitForReview' AND t2.language = 'vi')
    WHEN k.key = 'app.mcp.submittedToast' THEN 'Đã gửi để phê duyệt.'
    WHEN k.key = 'app.mcp.subscribe' THEN 'Đăng ký'
    WHEN k.key = 'app.mcp.subscribeAll' THEN (SELECT value FROM translations t2 JOIN translation_keys k2 ON k2.id = t2.key_id WHERE k2.key = 'app.skills.subscribeAll' AND t2.language = 'vi')
    WHEN k.key = 'app.mcp.subscribeN' THEN (SELECT value FROM translations t2 JOIN translation_keys k2 ON k2.id = t2.key_id WHERE k2.key = 'app.skills.subscribeN' AND t2.language = 'vi')
    WHEN k.key = 'app.mcp.subscribeToast' THEN 'Đã đăng ký thành công.'
    WHEN k.key = 'app.mcp.subscribeY' THEN (SELECT value FROM translations t2 JOIN translation_keys k2 ON k2.id = t2.key_id WHERE k2.key = 'app.skills.subscribeY' AND t2.language = 'vi')
    WHEN k.key = 'app.mcp.subscribed' THEN (SELECT value FROM translations t2 JOIN translation_keys k2 ON k2.id = t2.key_id WHERE k2.key = 'app.skills.subscribed' AND t2.language = 'vi')
    WHEN k.key = 'app.mcp.subscribedToast' THEN (SELECT value FROM translations t2 JOIN translation_keys k2 ON k2.id = t2.key_id WHERE k2.key = 'app.skills.subscribedToast' AND t2.language = 'vi')
    WHEN k.key = 'app.mcp.tabComments' THEN (SELECT value FROM translations t2 JOIN translation_keys k2 ON k2.id = t2.key_id WHERE k2.key = 'app.skills.tabComments' AND t2.language = 'vi')
    WHEN k.key = 'app.mcp.tabInfo' THEN (SELECT value FROM translations t2 JOIN translation_keys k2 ON k2.id = t2.key_id WHERE k2.key = 'app.skills.tabInfo' AND t2.language = 'vi')
    WHEN k.key = 'app.mcp.tabSkills' THEN 'Skill đang dùng MCP này'
    WHEN k.key = 'app.mcp.tabVersions' THEN (SELECT value FROM translations t2 JOIN translation_keys k2 ON k2.id = t2.key_id WHERE k2.key = 'app.skills.tabVersions' AND t2.language = 'vi')
    WHEN k.key = 'app.mcp.title' THEN 'Chi tiết MCP'
    WHEN k.key = 'app.mcp.underReview' THEN (SELECT value FROM translations t2 JOIN translation_keys k2 ON k2.id = t2.key_id WHERE k2.key = 'app.skills.underReview' AND t2.language = 'vi')
    WHEN k.key = 'app.mcp.unsubscribedToast' THEN (SELECT value FROM translations t2 JOIN translation_keys k2 ON k2.id = t2.key_id WHERE k2.key = 'app.skills.unsubscribedToast' AND t2.language = 'vi')
    WHEN k.key = 'app.mcp.updateFileRequired' THEN (SELECT value FROM translations t2 JOIN translation_keys k2 ON k2.id = t2.key_id WHERE k2.key = 'app.skills.updateFileRequired' AND t2.language = 'vi')
    WHEN k.key = 'app.mcp.updateFilesHint' THEN (SELECT value FROM translations t2 JOIN translation_keys k2 ON k2.id = t2.key_id WHERE k2.key = 'app.skills.updateFilesHint' AND t2.language = 'vi')
    WHEN k.key = 'app.mcp.updateHint' THEN (SELECT value FROM translations t2 JOIN translation_keys k2 ON k2.id = t2.key_id WHERE k2.key = 'app.skills.updateHint' AND t2.language = 'vi')
    WHEN k.key = 'app.mcp.updateInfo' THEN 'Thông tin cập nhật'
    WHEN k.key = 'app.mcp.updateInfoPh' THEN 'Mô tả những gì đã thay đổi trong phiên bản này...'
    WHEN k.key = 'app.mcp.updateInfoRequired' THEN 'Thông tin cập nhật là bắt buộc cho phiên bản mới.'
    WHEN k.key = 'app.mcp.updateInfoTooLong' THEN 'Thông tin cập nhật tối đa {n} ký tự.'
    WHEN k.key = 'app.mcp.updateSubmitted' THEN (SELECT value FROM translations t2 JOIN translation_keys k2 ON k2.id = t2.key_id WHERE k2.key = 'app.skills.updateSubmitted' AND t2.language = 'vi')
    WHEN k.key = 'app.mcp.updateTitle' THEN (SELECT value FROM translations t2 JOIN translation_keys k2 ON k2.id = t2.key_id WHERE k2.key = 'app.skills.updateTitle' AND t2.language = 'vi')
    WHEN k.key = 'app.mcp.updateVersion' THEN (SELECT value FROM translations t2 JOIN translation_keys k2 ON k2.id = t2.key_id WHERE k2.key = 'app.skills.updateVersion' AND t2.language = 'vi')
    WHEN k.key = 'app.mcp.updated' THEN (SELECT value FROM translations t2 JOIN translation_keys k2 ON k2.id = t2.key_id WHERE k2.key = 'app.skills.updated' AND t2.language = 'vi')
    WHEN k.key = 'app.mcp.updatedToast' THEN (SELECT value FROM translations t2 JOIN translation_keys k2 ON k2.id = t2.key_id WHERE k2.key = 'app.skills.updatedToast' AND t2.language = 'vi')
    WHEN k.key = 'app.mcp.uploadNewFile' THEN 'Tải lên tệp mới'
    WHEN k.key = 'app.mcp.version' THEN (SELECT value FROM translations t2 JOIN translation_keys k2 ON k2.id = t2.key_id WHERE k2.key = 'app.skills.version' AND t2.language = 'vi')
    WHEN k.key = 'app.mcp.versionApproved' THEN (SELECT value FROM translations t2 JOIN translation_keys k2 ON k2.id = t2.key_id WHERE k2.key = 'app.skills.versionApproved' AND t2.language = 'vi')
    WHEN k.key = 'app.mcp.versionCancelled' THEN (SELECT value FROM translations t2 JOIN translation_keys k2 ON k2.id = t2.key_id WHERE k2.key = 'app.skills.versionCancelled' AND t2.language = 'vi')
    WHEN k.key = 'app.mcp.versionConfirmDesc' THEN (SELECT value FROM translations t2 JOIN translation_keys k2 ON k2.id = t2.key_id WHERE k2.key = 'app.skill.versionConfirmDesc' AND t2.language = 'vi')
    WHEN k.key = 'app.mcp.versionConfirmTitle' THEN (SELECT value FROM translations t2 JOIN translation_keys k2 ON k2.id = t2.key_id WHERE k2.key = 'app.skill.versionConfirmTitle' AND t2.language = 'vi')
    WHEN k.key = 'app.mcp.versionCreatedToast' THEN 'Phiên bản {n} đã được tạo ở trạng thái Bản nháp — gửi phê duyệt khi sẵn sàng.'
    WHEN k.key = 'app.mcp.versionInfo' THEN 'Một phiên bản mới {new} sẽ được tạo từ phiên bản hiện tại (v{current}).'
    WHEN k.key = 'app.mcp.versionNote' THEN 'Phiên bản {n} được tạo ở trạng thái Bản nháp và MCP trở lại Bản nháp (giống như MCP mới tạo). Phiên bản hiện tại tiếp tục phục vụ cho đến khi phiên bản mới được phê duyệt. Gửi phê duyệt khi sẵn sàng.'
    WHEN k.key = 'app.mcp.versionNum' THEN 'Phiên bản {n}'
    WHEN k.key = 'app.mcp.versionRejected' THEN (SELECT value FROM translations t2 JOIN translation_keys k2 ON k2.id = t2.key_id WHERE k2.key = 'app.skills.versionRejected' AND t2.language = 'vi')
    WHEN k.key = 'app.mcp.viewCount' THEN 'Lượt xem: {n}'
    WHEN k.key = 'app.mcp.withdraw' THEN (SELECT value FROM translations t2 JOIN translation_keys k2 ON k2.id = t2.key_id WHERE k2.key = 'app.skills.withdraw' AND t2.language = 'vi')
    WHEN k.key = 'app.mcp.withdrawDesc' THEN (SELECT value FROM translations t2 JOIN translation_keys k2 ON k2.id = t2.key_id WHERE k2.key = 'app.skills.withdrawDesc' AND t2.language = 'vi')
    WHEN k.key = 'app.mcp.withdrawTitle' THEN (SELECT value FROM translations t2 JOIN translation_keys k2 ON k2.id = t2.key_id WHERE k2.key = 'app.skills.withdrawTitle' AND t2.language = 'vi')
    WHEN k.key = 'app.mcp.withdrawn' THEN (SELECT value FROM translations t2 JOIN translation_keys k2 ON k2.id = t2.key_id WHERE k2.key = 'app.skills.withdrawn' AND t2.language = 'vi')
    WHEN k.key = 'app.mcp.withdrawnToast' THEN 'Đã rút lại về bản nháp.'
    WHEN k.key = 'app.mcp.writeComment' THEN (SELECT value FROM translations t2 JOIN translation_keys k2 ON k2.id = t2.key_id WHERE k2.key = 'app.skills.writeComment' AND t2.language = 'vi')
    WHEN k.key = 'app.mcp.youRate' THEN 'Đánh giá {n} sao'
  END
FROM translation_keys k
WHERE k.key LIKE 'app.mcp.%'
  AND CASE
    WHEN k.key = 'app.mcp.addBtn' THEN EXISTS (SELECT 1 FROM translations t2 JOIN translation_keys k2 ON k2.id = t2.key_id WHERE k2.key = 'app.skills.addBtn' AND t2.language = 'vi')
    WHEN k.key = 'app.mcp.addTitle' THEN EXISTS (SELECT 1 FROM translations t2 JOIN translation_keys k2 ON k2.id = t2.key_id WHERE k2.key = 'app.skills.addTitle' AND t2.language = 'vi')
    WHEN k.key = 'app.mcp.adminBadge' THEN EXISTS (SELECT 1 FROM translations t2 JOIN translation_keys k2 ON k2.id = t2.key_id WHERE k2.key = 'app.skills.adminBadge' AND t2.language = 'vi')
    WHEN k.key = 'app.mcp.adminOnlyCat' THEN EXISTS (SELECT 1 FROM translations t2 JOIN translation_keys k2 ON k2.id = t2.key_id WHERE k2.key = 'app.skills.adminOnlyCat' AND t2.language = 'vi')
    WHEN k.key = 'app.mcp.adminOnlyStage' THEN EXISTS (SELECT 1 FROM translations t2 JOIN translation_keys k2 ON k2.id = t2.key_id WHERE k2.key = 'app.skills.adminOnlyStage' AND t2.language = 'vi')
    WHEN k.key = 'app.mcp.allStatuses' THEN TRUE
    WHEN k.key = 'app.mcp.approve' THEN EXISTS (SELECT 1 FROM translations t2 JOIN translation_keys k2 ON k2.id = t2.key_id WHERE k2.key = 'app.skills.approve' AND t2.language = 'vi')
    WHEN k.key = 'app.mcp.approveVersion' THEN EXISTS (SELECT 1 FROM translations t2 JOIN translation_keys k2 ON k2.id = t2.key_id WHERE k2.key = 'app.skills.approveVersion' AND t2.language = 'vi')
    WHEN k.key = 'app.mcp.approved' THEN TRUE
    WHEN k.key = 'app.mcp.approvedFilesHint' THEN EXISTS (SELECT 1 FROM translations t2 JOIN translation_keys k2 ON k2.id = t2.key_id WHERE k2.key = 'app.skills.approvedFilesHint' AND t2.language = 'vi')
    WHEN k.key = 'app.mcp.approvedMcpTab' THEN TRUE
    WHEN k.key = 'app.mcp.approvedToast' THEN TRUE
    WHEN k.key = 'app.mcp.browseTitle' THEN TRUE
    WHEN k.key = 'app.mcp.cancel' THEN TRUE
    WHEN k.key = 'app.mcp.cancelUpdateBtn' THEN EXISTS (SELECT 1 FROM translations t2 JOIN translation_keys k2 ON k2.id = t2.key_id WHERE k2.key = 'app.skills.cancelUpdateBtn' AND t2.language = 'vi')
    WHEN k.key = 'app.mcp.cancelUpdateDesc' THEN EXISTS (SELECT 1 FROM translations t2 JOIN translation_keys k2 ON k2.id = t2.key_id WHERE k2.key = 'app.skills.cancelUpdateDesc' AND t2.language = 'vi')
    WHEN k.key = 'app.mcp.cancelUpdateTitle' THEN EXISTS (SELECT 1 FROM translations t2 JOIN translation_keys k2 ON k2.id = t2.key_id WHERE k2.key = 'app.skills.cancelUpdateTitle' AND t2.language = 'vi')
    WHEN k.key = 'app.mcp.cancelVersionUpdate' THEN EXISTS (SELECT 1 FROM translations t2 JOIN translation_keys k2 ON k2.id = t2.key_id WHERE k2.key = 'app.skills.cancelVersionUpdate' AND t2.language = 'vi')
    WHEN k.key = 'app.mcp.cannotDeleteItem' THEN EXISTS (SELECT 1 FROM translations t2 JOIN translation_keys k2 ON k2.id = t2.key_id WHERE k2.key = 'app.skills.cannotDeleteItem' AND t2.language = 'vi')
    WHEN k.key = 'app.mcp.cannotDeleteList' THEN EXISTS (SELECT 1 FROM translations t2 JOIN translation_keys k2 ON k2.id = t2.key_id WHERE k2.key = 'app.skills.cannotDeleteList' AND t2.language = 'vi')
    WHEN k.key = 'app.mcp.cannotDeleteTitle' THEN EXISTS (SELECT 1 FROM translations t2 JOIN translation_keys k2 ON k2.id = t2.key_id WHERE k2.key = 'app.skills.cannotDeleteTitle' AND t2.language = 'vi')
    WHEN k.key = 'app.mcp.cannotDeleteTotal' THEN EXISTS (SELECT 1 FROM translations t2 JOIN translation_keys k2 ON k2.id = t2.key_id WHERE k2.key = 'app.skills.cannotDeleteTotal' AND t2.language = 'vi')
    WHEN k.key = 'app.mcp.cardsAria' THEN EXISTS (SELECT 1 FROM translations t2 JOIN translation_keys k2 ON k2.id = t2.key_id WHERE k2.key = 'app.skills.cardsAria' AND t2.language = 'vi')
    WHEN k.key = 'app.mcp.catAdded' THEN EXISTS (SELECT 1 FROM translations t2 JOIN translation_keys k2 ON k2.id = t2.key_id WHERE k2.key = 'app.skills.catAdded' AND t2.language = 'vi')
    WHEN k.key = 'app.mcp.catDeleteDesc' THEN EXISTS (SELECT 1 FROM translations t2 JOIN translation_keys k2 ON k2.id = t2.key_id WHERE k2.key = 'app.skills.catDeleteDesc' AND t2.language = 'vi')
    WHEN k.key = 'app.mcp.catDeleteDescSimple' THEN EXISTS (SELECT 1 FROM translations t2 JOIN translation_keys k2 ON k2.id = t2.key_id WHERE k2.key = 'app.skills.catDeleteDescSimple' AND t2.language = 'vi')
    WHEN k.key = 'app.mcp.catDeleted' THEN EXISTS (SELECT 1 FROM translations t2 JOIN translation_keys k2 ON k2.id = t2.key_id WHERE k2.key = 'app.skills.catDeleted' AND t2.language = 'vi')
    WHEN k.key = 'app.mcp.catUpdated' THEN EXISTS (SELECT 1 FROM translations t2 JOIN translation_keys k2 ON k2.id = t2.key_id WHERE k2.key = 'app.skills.catUpdated' AND t2.language = 'vi')
    WHEN k.key = 'app.mcp.categories' THEN EXISTS (SELECT 1 FROM translations t2 JOIN translation_keys k2 ON k2.id = t2.key_id WHERE k2.key = 'app.skills.categories' AND t2.language = 'vi')
    WHEN k.key = 'app.mcp.categoryName' THEN EXISTS (SELECT 1 FROM translations t2 JOIN translation_keys k2 ON k2.id = t2.key_id WHERE k2.key = 'app.skills.categoryName' AND t2.language = 'vi')
    WHEN k.key = 'app.mcp.changelog' THEN TRUE
    WHEN k.key = 'app.mcp.changelogPh' THEN TRUE
    WHEN k.key = 'app.mcp.changelogRequired' THEN TRUE
    WHEN k.key = 'app.mcp.changelogTooLong' THEN TRUE
    WHEN k.key = 'app.mcp.clearCreator' THEN EXISTS (SELECT 1 FROM translations t2 JOIN translation_keys k2 ON k2.id = t2.key_id WHERE k2.key = 'app.skills.clearCreator' AND t2.language = 'vi')
    WHEN k.key = 'app.mcp.close' THEN TRUE
    WHEN k.key = 'app.mcp.colCreated' THEN TRUE
    WHEN k.key = 'app.mcp.colCreator' THEN EXISTS (SELECT 1 FROM translations t2 JOIN translation_keys k2 ON k2.id = t2.key_id WHERE k2.key = 'app.skills.colCreator' AND t2.language = 'vi')
    WHEN k.key = 'app.mcp.colDescription' THEN TRUE
    WHEN k.key = 'app.mcp.colDownloads' THEN EXISTS (SELECT 1 FROM translations t2 JOIN translation_keys k2 ON k2.id = t2.key_id WHERE k2.key = 'app.skills.colDownloads' AND t2.language = 'vi')
    WHEN k.key = 'app.mcp.colName' THEN EXISTS (SELECT 1 FROM translations t2 JOIN translation_keys k2 ON k2.id = t2.key_id WHERE k2.key = 'app.skills.colName' AND t2.language = 'vi')
    WHEN k.key = 'app.mcp.colRating' THEN EXISTS (SELECT 1 FROM translations t2 JOIN translation_keys k2 ON k2.id = t2.key_id WHERE k2.key = 'app.skills.colRating' AND t2.language = 'vi')
    WHEN k.key = 'app.mcp.colStatus' THEN TRUE
    WHEN k.key = 'app.mcp.colSubscribe' THEN EXISTS (SELECT 1 FROM translations t2 JOIN translation_keys k2 ON k2.id = t2.key_id WHERE k2.key = 'app.skills.colSubscribe' AND t2.language = 'vi')
    WHEN k.key = 'app.mcp.colViews' THEN EXISTS (SELECT 1 FROM translations t2 JOIN translation_keys k2 ON k2.id = t2.key_id WHERE k2.key = 'app.skills.colViews' AND t2.language = 'vi')
    WHEN k.key = 'app.mcp.confirm' THEN TRUE
    WHEN k.key = 'app.mcp.copyLink' THEN EXISTS (SELECT 1 FROM translations t2 JOIN translation_keys k2 ON k2.id = t2.key_id WHERE k2.key = 'app.skill.copyLink' AND t2.language = 'vi')
    WHEN k.key = 'app.mcp.copyLinkCopied' THEN EXISTS (SELECT 1 FROM translations t2 JOIN translation_keys k2 ON k2.id = t2.key_id WHERE k2.key = 'app.skill.copyLinkCopied' AND t2.language = 'vi')
    WHEN k.key = 'app.mcp.copyLinkFailed' THEN EXISTS (SELECT 1 FROM translations t2 JOIN translation_keys k2 ON k2.id = t2.key_id WHERE k2.key = 'app.skill.copyLinkFailed' AND t2.language = 'vi')
    WHEN k.key = 'app.mcp.copyLinkTooltip' THEN EXISTS (SELECT 1 FROM translations t2 JOIN translation_keys k2 ON k2.id = t2.key_id WHERE k2.key = 'app.skill.copyLinkTooltip' AND t2.language = 'vi')
    WHEN k.key = 'app.mcp.createVersion' THEN TRUE
    WHEN k.key = 'app.mcp.createdToast' THEN EXISTS (SELECT 1 FROM translations t2 JOIN translation_keys k2 ON k2.id = t2.key_id WHERE k2.key = 'app.skills.createdToast' AND t2.language = 'vi')
    WHEN k.key = 'app.mcp.createdUpdated' THEN EXISTS (SELECT 1 FROM translations t2 JOIN translation_keys k2 ON k2.id = t2.key_id WHERE k2.key = 'app.skills.createdUpdated' AND t2.language = 'vi')
    WHEN k.key = 'app.mcp.creator' THEN EXISTS (SELECT 1 FROM translations t2 JOIN translation_keys k2 ON k2.id = t2.key_id WHERE k2.key = 'app.skills.creator' AND t2.language = 'vi')
    WHEN k.key = 'app.mcp.current' THEN EXISTS (SELECT 1 FROM translations t2 JOIN translation_keys k2 ON k2.id = t2.key_id WHERE k2.key = 'app.skills.current' AND t2.language = 'vi')
    WHEN k.key = 'app.mcp.currentFile' THEN TRUE
    WHEN k.key = 'app.mcp.deleteBtn' THEN EXISTS (SELECT 1 FROM translations t2 JOIN translation_keys k2 ON k2.id = t2.key_id WHERE k2.key = 'app.skills.deleteBtn' AND t2.language = 'vi')
    WHEN k.key = 'app.mcp.deleteCategoryAria' THEN EXISTS (SELECT 1 FROM translations t2 JOIN translation_keys k2 ON k2.id = t2.key_id WHERE k2.key = 'app.skills.deleteCategoryAria' AND t2.language = 'vi')
    WHEN k.key = 'app.mcp.deleteCommentAria' THEN EXISTS (SELECT 1 FROM translations t2 JOIN translation_keys k2 ON k2.id = t2.key_id WHERE k2.key = 'app.skills.deleteCommentAria' AND t2.language = 'vi')
    WHEN k.key = 'app.mcp.deleteDeniedDesc' THEN TRUE
    WHEN k.key = 'app.mcp.deleteDeniedList' THEN TRUE
    WHEN k.key = 'app.mcp.deleteDeniedTitle' THEN TRUE
    WHEN k.key = 'app.mcp.deleteDeniedTotal' THEN TRUE
    WHEN k.key = 'app.mcp.deleteDesc' THEN EXISTS (SELECT 1 FROM translations t2 JOIN translation_keys k2 ON k2.id = t2.key_id WHERE k2.key = 'app.skills.deleteDesc' AND t2.language = 'vi')
    WHEN k.key = 'app.mcp.deleteStageAria' THEN EXISTS (SELECT 1 FROM translations t2 JOIN translation_keys k2 ON k2.id = t2.key_id WHERE k2.key = 'app.skills.deleteStageAria' AND t2.language = 'vi')
    WHEN k.key = 'app.mcp.deleteTitle' THEN EXISTS (SELECT 1 FROM translations t2 JOIN translation_keys k2 ON k2.id = t2.key_id WHERE k2.key = 'app.skills.deleteTitle' AND t2.language = 'vi')
    WHEN k.key = 'app.mcp.deletedToast' THEN EXISTS (SELECT 1 FROM translations t2 JOIN translation_keys k2 ON k2.id = t2.key_id WHERE k2.key = 'app.skills.deletedToast' AND t2.language = 'vi')
    WHEN k.key = 'app.mcp.deletedToastDetail' THEN EXISTS (SELECT 1 FROM translations t2 JOIN translation_keys k2 ON k2.id = t2.key_id WHERE k2.key = 'app.skills.deletedToastDetail' AND t2.language = 'vi')
    WHEN k.key = 'app.mcp.depAlready' THEN TRUE
    WHEN k.key = 'app.mcp.depBanner' THEN TRUE
    WHEN k.key = 'app.mcp.depDeleted' THEN TRUE
    WHEN k.key = 'app.mcp.depDeletedTip' THEN TRUE
    WHEN k.key = 'app.mcp.depLabel' THEN TRUE
    WHEN k.key = 'app.mcp.depLoading' THEN TRUE
    WHEN k.key = 'app.mcp.depPh' THEN TRUE
    WHEN k.key = 'app.mcp.dependentSkills' THEN FALSE
    WHEN k.key = 'app.mcp.descRequired' THEN TRUE
    WHEN k.key = 'app.mcp.description' THEN TRUE
    WHEN k.key = 'app.mcp.descriptionPh' THEN TRUE
    WHEN k.key = 'app.mcp.detailTitle' THEN EXISTS (SELECT 1 FROM translations t2 JOIN translation_keys k2 ON k2.id = t2.key_id WHERE k2.key = 'app.skills.detailTitle' AND t2.language = 'vi')
    WHEN k.key = 'app.mcp.download' THEN TRUE
    WHEN k.key = 'app.mcp.downloadAll' THEN EXISTS (SELECT 1 FROM translations t2 JOIN translation_keys k2 ON k2.id = t2.key_id WHERE k2.key = 'app.skills.downloadAll' AND t2.language = 'vi')
    WHEN k.key = 'app.mcp.downloadCount' THEN TRUE
    WHEN k.key = 'app.mcp.downloadExcel' THEN EXISTS (SELECT 1 FROM translations t2 JOIN translation_keys k2 ON k2.id = t2.key_id WHERE k2.key = 'app.skills.downloadExcel' AND t2.language = 'vi')
    WHEN k.key = 'app.mcp.draft' THEN TRUE
    WHEN k.key = 'app.mcp.draftHint' THEN EXISTS (SELECT 1 FROM translations t2 JOIN translation_keys k2 ON k2.id = t2.key_id WHERE k2.key = 'app.skills.draftHint' AND t2.language = 'vi')
    WHEN k.key = 'app.mcp.draftNote' THEN TRUE
    WHEN k.key = 'app.mcp.dragDropNewOr' THEN EXISTS (SELECT 1 FROM translations t2 JOIN translation_keys k2 ON k2.id = t2.key_id WHERE k2.key = 'app.skills.dragDropNewOr' AND t2.language = 'vi')
    WHEN k.key = 'app.mcp.editBtn' THEN EXISTS (SELECT 1 FROM translations t2 JOIN translation_keys k2 ON k2.id = t2.key_id WHERE k2.key = 'app.skills.editBtn' AND t2.language = 'vi')
    WHEN k.key = 'app.mcp.editCategoryAria' THEN EXISTS (SELECT 1 FROM translations t2 JOIN translation_keys k2 ON k2.id = t2.key_id WHERE k2.key = 'app.skills.editCategoryAria' AND t2.language = 'vi')
    WHEN k.key = 'app.mcp.editCategoryTitle' THEN EXISTS (SELECT 1 FROM translations t2 JOIN translation_keys k2 ON k2.id = t2.key_id WHERE k2.key = 'app.skills.editCategoryTitle' AND t2.language = 'vi')
    WHEN k.key = 'app.mcp.editNoteApproved' THEN TRUE
    WHEN k.key = 'app.mcp.editNoteStatus' THEN TRUE
    WHEN k.key = 'app.mcp.editStageAria' THEN EXISTS (SELECT 1 FROM translations t2 JOIN translation_keys k2 ON k2.id = t2.key_id WHERE k2.key = 'app.skills.editStageAria' AND t2.language = 'vi')
    WHEN k.key = 'app.mcp.editStageTitle' THEN EXISTS (SELECT 1 FROM translations t2 JOIN translation_keys k2 ON k2.id = t2.key_id WHERE k2.key = 'app.skills.editStageTitle' AND t2.language = 'vi')
    WHEN k.key = 'app.mcp.editTitle' THEN EXISTS (SELECT 1 FROM translations t2 JOIN translation_keys k2 ON k2.id = t2.key_id WHERE k2.key = 'app.skills.editTitle' AND t2.language = 'vi')
    WHEN k.key = 'app.mcp.emptyDesc' THEN EXISTS (SELECT 1 FROM translations t2 JOIN translation_keys k2 ON k2.id = t2.key_id WHERE k2.key = 'app.skills.emptyDesc' AND t2.language = 'vi')
    WHEN k.key = 'app.mcp.emptyTitle' THEN TRUE
    WHEN k.key = 'app.mcp.exportedToast' THEN EXISTS (SELECT 1 FROM translations t2 JOIN translation_keys k2 ON k2.id = t2.key_id WHERE k2.key = 'app.skills.exportedToast' AND t2.language = 'vi')
    WHEN k.key = 'app.mcp.file' THEN TRUE
    WHEN k.key = 'app.mcp.fileDropOr' THEN TRUE
    WHEN k.key = 'app.mcp.fileErrorFormat' THEN TRUE
    WHEN k.key = 'app.mcp.fileErrorSize' THEN TRUE
    WHEN k.key = 'app.mcp.fileKeepHint' THEN TRUE
    WHEN k.key = 'app.mcp.fileRemoved' THEN EXISTS (SELECT 1 FROM translations t2 JOIN translation_keys k2 ON k2.id = t2.key_id WHERE k2.key = 'app.skills.fileRemoved' AND t2.language = 'vi')
    WHEN k.key = 'app.mcp.fileRequired' THEN EXISTS (SELECT 1 FROM translations t2 JOIN translation_keys k2 ON k2.id = t2.key_id WHERE k2.key = 'app.skills.fileRequired' AND t2.language = 'vi')
    WHEN k.key = 'app.mcp.fileRequiredUpload' THEN TRUE
    WHEN k.key = 'app.mcp.fileSupported' THEN TRUE
    WHEN k.key = 'app.mcp.filesToRemove' THEN EXISTS (SELECT 1 FROM translations t2 JOIN translation_keys k2 ON k2.id = t2.key_id WHERE k2.key = 'app.skills.filesToRemove' AND t2.language = 'vi')
    WHEN k.key = 'app.mcp.hashtag' THEN TRUE
    WHEN k.key = 'app.mcp.hashtagExists' THEN TRUE
    WHEN k.key = 'app.mcp.hashtagPh' THEN TRUE
    WHEN k.key = 'app.mcp.hashtagRemoveAria' THEN TRUE
    WHEN k.key = 'app.mcp.lastRejected' THEN EXISTS (SELECT 1 FROM translations t2 JOIN translation_keys k2 ON k2.id = t2.key_id WHERE k2.key = 'app.skills.lastRejected' AND t2.language = 'vi')
    WHEN k.key = 'app.mcp.listAria' THEN EXISTS (SELECT 1 FROM translations t2 JOIN translation_keys k2 ON k2.id = t2.key_id WHERE k2.key = 'app.skills.listAria' AND t2.language = 'vi')
    WHEN k.key = 'app.mcp.loadError' THEN EXISTS (SELECT 1 FROM translations t2 JOIN translation_keys k2 ON k2.id = t2.key_id WHERE k2.key = 'app.skills.loadError' AND t2.language = 'vi')
    WHEN k.key = 'app.mcp.loadErrorDetail' THEN EXISTS (SELECT 1 FROM translations t2 JOIN translation_keys k2 ON k2.id = t2.key_id WHERE k2.key = 'app.skills.loadErrorDetail' AND t2.language = 'vi')
    WHEN k.key = 'app.mcp.manageTaxonomy' THEN EXISTS (SELECT 1 FROM translations t2 JOIN translation_keys k2 ON k2.id = t2.key_id WHERE k2.key = 'app.skills.manageTaxonomy' AND t2.language = 'vi')
    WHEN k.key = 'app.mcp.manageTaxonomyTitle' THEN EXISTS (SELECT 1 FROM translations t2 JOIN translation_keys k2 ON k2.id = t2.key_id WHERE k2.key = 'app.skills.manageTaxonomyTitle' AND t2.language = 'vi')
    WHEN k.key = 'app.mcp.mcpCode' THEN FALSE
    WHEN k.key = 'app.mcp.mcpCount' THEN EXISTS (SELECT 1 FROM translations t2 JOIN translation_keys k2 ON k2.id = t2.key_id WHERE k2.key = 'app.skills.skillCount' AND t2.language = 'vi')
    WHEN k.key = 'app.mcp.mcpDeleted' THEN EXISTS (SELECT 1 FROM translations t2 JOIN translation_keys k2 ON k2.id = t2.key_id WHERE k2.key = 'app.skills.mcpDeleted' AND t2.language = 'vi')
    WHEN k.key = 'app.mcp.mcpDeletedTip' THEN EXISTS (SELECT 1 FROM translations t2 JOIN translation_keys k2 ON k2.id = t2.key_id WHERE k2.key = 'app.skills.mcpDeletedTip' AND t2.language = 'vi')
    WHEN k.key = 'app.mcp.mcpDepsWarnDesc' THEN EXISTS (SELECT 1 FROM translations t2 JOIN translation_keys k2 ON k2.id = t2.key_id WHERE k2.key = 'app.skills.mcpDepsWarnDesc' AND t2.language = 'vi')
    WHEN k.key = 'app.mcp.mcpDepsWarnNote' THEN EXISTS (SELECT 1 FROM translations t2 JOIN translation_keys k2 ON k2.id = t2.key_id WHERE k2.key = 'app.skills.mcpDepsWarnNote' AND t2.language = 'vi')
    WHEN k.key = 'app.mcp.mcpDepsWarnTitle' THEN EXISTS (SELECT 1 FROM translations t2 JOIN translation_keys k2 ON k2.id = t2.key_id WHERE k2.key = 'app.skills.mcpDepsWarnTitle' AND t2.language = 'vi')
    WHEN k.key = 'app.mcp.mcpUnavailableGone' THEN EXISTS (SELECT 1 FROM translations t2 JOIN translation_keys k2 ON k2.id = t2.key_id WHERE k2.key = 'app.skills.mcpUnavailableGone' AND t2.language = 'vi')
    WHEN k.key = 'app.mcp.mcpUnavailableGoneWarn' THEN EXISTS (SELECT 1 FROM translations t2 JOIN translation_keys k2 ON k2.id = t2.key_id WHERE k2.key = 'app.skills.mcpUnavailableGoneWarn' AND t2.language = 'vi')
    WHEN k.key = 'app.mcp.mcpUnavailableNotApproved' THEN EXISTS (SELECT 1 FROM translations t2 JOIN translation_keys k2 ON k2.id = t2.key_id WHERE k2.key = 'app.skills.mcpUnavailableNotApproved' AND t2.language = 'vi')
    WHEN k.key = 'app.mcp.mcpUnavailableNotApprovedWarn' THEN EXISTS (SELECT 1 FROM translations t2 JOIN translation_keys k2 ON k2.id = t2.key_id WHERE k2.key = 'app.skills.mcpUnavailableNotApprovedWarn' AND t2.language = 'vi')
    WHEN k.key = 'app.mcp.myMcpTab' THEN TRUE
    WHEN k.key = 'app.mcp.myMcpsEmpty' THEN TRUE
    WHEN k.key = 'app.mcp.name' THEN TRUE
    WHEN k.key = 'app.mcp.nameEmpty' THEN TRUE
    WHEN k.key = 'app.mcp.nameLocked' THEN EXISTS (SELECT 1 FROM translations t2 JOIN translation_keys k2 ON k2.id = t2.key_id WHERE k2.key = 'app.skills.nameLocked' AND t2.language = 'vi')
    WHEN k.key = 'app.mcp.namePh' THEN TRUE
    WHEN k.key = 'app.mcp.nameTooLong' THEN TRUE
    WHEN k.key = 'app.mcp.newCategoryPh' THEN EXISTS (SELECT 1 FROM translations t2 JOIN translation_keys k2 ON k2.id = t2.key_id WHERE k2.key = 'app.skills.newCategoryPh' AND t2.language = 'vi')
    WHEN k.key = 'app.mcp.newFiles' THEN EXISTS (SELECT 1 FROM translations t2 JOIN translation_keys k2 ON k2.id = t2.key_id WHERE k2.key = 'app.skills.newFiles' AND t2.language = 'vi')
    WHEN k.key = 'app.mcp.newMcpDraftHint' THEN TRUE
    WHEN k.key = 'app.mcp.newStagePh' THEN EXISTS (SELECT 1 FROM translations t2 JOIN translation_keys k2 ON k2.id = t2.key_id WHERE k2.key = 'app.skills.newStagePh' AND t2.language = 'vi')
    WHEN k.key = 'app.mcp.noCategoriesYet' THEN EXISTS (SELECT 1 FROM translations t2 JOIN translation_keys k2 ON k2.id = t2.key_id WHERE k2.key = 'app.skills.noCategoriesYet' AND t2.language = 'vi')
    WHEN k.key = 'app.mcp.noComments' THEN EXISTS (SELECT 1 FROM translations t2 JOIN translation_keys k2 ON k2.id = t2.key_id WHERE k2.key = 'app.skills.noComments' AND t2.language = 'vi')
    WHEN k.key = 'app.mcp.noDeps' THEN TRUE
    WHEN k.key = 'app.mcp.noDescription' THEN EXISTS (SELECT 1 FROM translations t2 JOIN translation_keys k2 ON k2.id = t2.key_id WHERE k2.key = 'app.skills.noDescription' AND t2.language = 'vi')
    WHEN k.key = 'app.mcp.noMatchDesc' THEN EXISTS (SELECT 1 FROM translations t2 JOIN translation_keys k2 ON k2.id = t2.key_id WHERE k2.key = 'app.skills.noMatchDesc' AND t2.language = 'vi')
    WHEN k.key = 'app.mcp.noOptions' THEN EXISTS (SELECT 1 FROM translations t2 JOIN translation_keys k2 ON k2.id = t2.key_id WHERE k2.key = 'app.skills.noOptions' AND t2.language = 'vi')
    WHEN k.key = 'app.mcp.noRating' THEN TRUE
    WHEN k.key = 'app.mcp.noSkills' THEN TRUE
    WHEN k.key = 'app.mcp.noStagesYet' THEN EXISTS (SELECT 1 FROM translations t2 JOIN translation_keys k2 ON k2.id = t2.key_id WHERE k2.key = 'app.skills.noStagesYet' AND t2.language = 'vi')
    WHEN k.key = 'app.mcp.noSuggestions' THEN TRUE
    WHEN k.key = 'app.mcp.noUsersSelected' THEN EXISTS (SELECT 1 FROM translations t2 JOIN translation_keys k2 ON k2.id = t2.key_id WHERE k2.key = 'app.skills.noUsersSelected' AND t2.language = 'vi')
    WHEN k.key = 'app.mcp.noVersions' THEN EXISTS (SELECT 1 FROM translations t2 JOIN translation_keys k2 ON k2.id = t2.key_id WHERE k2.key = 'app.skills.noVersions' AND t2.language = 'vi')
    WHEN k.key = 'app.mcp.notFoundOrNoAccess' THEN EXISTS (SELECT 1 FROM translations t2 JOIN translation_keys k2 ON k2.id = t2.key_id WHERE k2.key = 'app.skill.notFoundOrNoAccess' AND t2.language = 'vi')
    WHEN k.key = 'app.mcp.onlyApprovedComment' THEN EXISTS (SELECT 1 FROM translations t2 JOIN translation_keys k2 ON k2.id = t2.key_id WHERE k2.key = 'app.skills.onlyApprovedComment' AND t2.language = 'vi')
    WHEN k.key = 'app.mcp.onlyApprovedRate' THEN EXISTS (SELECT 1 FROM translations t2 JOIN translation_keys k2 ON k2.id = t2.key_id WHERE k2.key = 'app.skills.onlyApprovedRate' AND t2.language = 'vi')
    WHEN k.key = 'app.mcp.openListAria' THEN TRUE
    WHEN k.key = 'app.mcp.optional' THEN EXISTS (SELECT 1 FROM translations t2 JOIN translation_keys k2 ON k2.id = t2.key_id WHERE k2.key = 'app.skills.optional' AND t2.language = 'vi')
    WHEN k.key = 'app.mcp.pending' THEN TRUE
    WHEN k.key = 'app.mcp.pendingBanner' THEN EXISTS (SELECT 1 FROM translations t2 JOIN translation_keys k2 ON k2.id = t2.key_id WHERE k2.key = 'app.skills.pendingBanner' AND t2.language = 'vi')
    WHEN k.key = 'app.mcp.pendingVersion' THEN EXISTS (SELECT 1 FROM translations t2 JOIN translation_keys k2 ON k2.id = t2.key_id WHERE k2.key = 'app.skills.pendingVersion' AND t2.language = 'vi')
    WHEN k.key = 'app.mcp.pendingVersionBanner' THEN EXISTS (SELECT 1 FROM translations t2 JOIN translation_keys k2 ON k2.id = t2.key_id WHERE k2.key = 'app.skills.pendingVersionBanner' AND t2.language = 'vi')
    WHEN k.key = 'app.mcp.pinBlockedArchived' THEN TRUE
    WHEN k.key = 'app.mcp.pinBlockedDraft' THEN TRUE
    WHEN k.key = 'app.mcp.pinBlockedPending' THEN TRUE
    WHEN k.key = 'app.mcp.pinBlockedRejected' THEN TRUE
    WHEN k.key = 'app.mcp.processStages' THEN EXISTS (SELECT 1 FROM translations t2 JOIN translation_keys k2 ON k2.id = t2.key_id WHERE k2.key = 'app.skills.processStages' AND t2.language = 'vi')
    WHEN k.key = 'app.mcp.rateOnlyApproved' THEN TRUE
    WHEN k.key = 'app.mcp.rateStars' THEN EXISTS (SELECT 1 FROM translations t2 JOIN translation_keys k2 ON k2.id = t2.key_id WHERE k2.key = 'app.skills.rateStars' AND t2.language = 'vi')
    WHEN k.key = 'app.mcp.reject' THEN EXISTS (SELECT 1 FROM translations t2 JOIN translation_keys k2 ON k2.id = t2.key_id WHERE k2.key = 'app.skills.reject' AND t2.language = 'vi')
    WHEN k.key = 'app.mcp.rejectReasonPh' THEN EXISTS (SELECT 1 FROM translations t2 JOIN translation_keys k2 ON k2.id = t2.key_id WHERE k2.key = 'app.skills.rejectReasonPh' AND t2.language = 'vi')
    WHEN k.key = 'app.mcp.rejectReasonRequired' THEN EXISTS (SELECT 1 FROM translations t2 JOIN translation_keys k2 ON k2.id = t2.key_id WHERE k2.key = 'app.skills.rejectReasonRequired' AND t2.language = 'vi')
    WHEN k.key = 'app.mcp.rejectTitle' THEN EXISTS (SELECT 1 FROM translations t2 JOIN translation_keys k2 ON k2.id = t2.key_id WHERE k2.key = 'app.skills.rejectTitle' AND t2.language = 'vi')
    WHEN k.key = 'app.mcp.rejectVersion' THEN EXISTS (SELECT 1 FROM translations t2 JOIN translation_keys k2 ON k2.id = t2.key_id WHERE k2.key = 'app.skills.rejectVersion' AND t2.language = 'vi')
    WHEN k.key = 'app.mcp.rejected' THEN TRUE
    WHEN k.key = 'app.mcp.rejectedToast' THEN TRUE
    WHEN k.key = 'app.mcp.rejectionReason' THEN EXISTS (SELECT 1 FROM translations t2 JOIN translation_keys k2 ON k2.id = t2.key_id WHERE k2.key = 'app.skills.rejectionReason' AND t2.language = 'vi')
    WHEN k.key = 'app.mcp.removeDepAria' THEN TRUE
    WHEN k.key = 'app.mcp.reuseFile' THEN TRUE
    WHEN k.key = 'app.mcp.reviewSubmitted' THEN EXISTS (SELECT 1 FROM translations t2 JOIN translation_keys k2 ON k2.id = t2.key_id WHERE k2.key = 'app.skills.reviewSubmitted' AND t2.language = 'vi')
    WHEN k.key = 'app.mcp.save' THEN TRUE
    WHEN k.key = 'app.mcp.saveDraft' THEN TRUE
    WHEN k.key = 'app.mcp.search' THEN TRUE
    WHEN k.key = 'app.mcp.searchByName' THEN TRUE
    WHEN k.key = 'app.mcp.searchTitle' THEN EXISTS (SELECT 1 FROM translations t2 JOIN translation_keys k2 ON k2.id = t2.key_id WHERE k2.key = 'app.skills.searchTitle' AND t2.language = 'vi')
    WHEN k.key = 'app.mcp.searchUserDesc' THEN EXISTS (SELECT 1 FROM translations t2 JOIN translation_keys k2 ON k2.id = t2.key_id WHERE k2.key = 'app.skills.searchUserDesc' AND t2.language = 'vi')
    WHEN k.key = 'app.mcp.selectCreator' THEN EXISTS (SELECT 1 FROM translations t2 JOIN translation_keys k2 ON k2.id = t2.key_id WHERE k2.key = 'app.skills.selectCreator' AND t2.language = 'vi')
    WHEN k.key = 'app.mcp.selected' THEN EXISTS (SELECT 1 FROM translations t2 JOIN translation_keys k2 ON k2.id = t2.key_id WHERE k2.key = 'app.skills.selected' AND t2.language = 'vi')
    WHEN k.key = 'app.mcp.selectedCount' THEN TRUE
    WHEN k.key = 'app.mcp.send' THEN EXISTS (SELECT 1 FROM translations t2 JOIN translation_keys k2 ON k2.id = t2.key_id WHERE k2.key = 'app.skills.send' AND t2.language = 'vi')
    WHEN k.key = 'app.mcp.serverKey' THEN FALSE
    WHEN k.key = 'app.mcp.showMore' THEN TRUE
    WHEN k.key = 'app.mcp.skillsTab' THEN TRUE
    WHEN k.key = 'app.mcp.stageAdded' THEN EXISTS (SELECT 1 FROM translations t2 JOIN translation_keys k2 ON k2.id = t2.key_id WHERE k2.key = 'app.skills.stageAdded' AND t2.language = 'vi')
    WHEN k.key = 'app.mcp.stageDeleteDesc' THEN EXISTS (SELECT 1 FROM translations t2 JOIN translation_keys k2 ON k2.id = t2.key_id WHERE k2.key = 'app.skills.stageDeleteDesc' AND t2.language = 'vi')
    WHEN k.key = 'app.mcp.stageDeleteDescSimple' THEN EXISTS (SELECT 1 FROM translations t2 JOIN translation_keys k2 ON k2.id = t2.key_id WHERE k2.key = 'app.skills.stageDeleteDescSimple' AND t2.language = 'vi')
    WHEN k.key = 'app.mcp.stageDeleted' THEN EXISTS (SELECT 1 FROM translations t2 JOIN translation_keys k2 ON k2.id = t2.key_id WHERE k2.key = 'app.skills.stageDeleted' AND t2.language = 'vi')
    WHEN k.key = 'app.mcp.stageDescLabel' THEN EXISTS (SELECT 1 FROM translations t2 JOIN translation_keys k2 ON k2.id = t2.key_id WHERE k2.key = 'app.skills.stageDescLabel' AND t2.language = 'vi')
    WHEN k.key = 'app.mcp.stageDescPh' THEN EXISTS (SELECT 1 FROM translations t2 JOIN translation_keys k2 ON k2.id = t2.key_id WHERE k2.key = 'app.skills.stageDescPh' AND t2.language = 'vi')
    WHEN k.key = 'app.mcp.stageName' THEN EXISTS (SELECT 1 FROM translations t2 JOIN translation_keys k2 ON k2.id = t2.key_id WHERE k2.key = 'app.skills.stageName' AND t2.language = 'vi')
    WHEN k.key = 'app.mcp.stageUpdated' THEN EXISTS (SELECT 1 FROM translations t2 JOIN translation_keys k2 ON k2.id = t2.key_id WHERE k2.key = 'app.skills.stageUpdated' AND t2.language = 'vi')
    WHEN k.key = 'app.mcp.status' THEN TRUE
    WHEN k.key = 'app.mcp.submit' THEN TRUE
    WHEN k.key = 'app.mcp.submitForReview' THEN EXISTS (SELECT 1 FROM translations t2 JOIN translation_keys k2 ON k2.id = t2.key_id WHERE k2.key = 'app.skills.submitForReview' AND t2.language = 'vi')
    WHEN k.key = 'app.mcp.submittedToast' THEN TRUE
    WHEN k.key = 'app.mcp.subscribe' THEN TRUE
    WHEN k.key = 'app.mcp.subscribeAll' THEN EXISTS (SELECT 1 FROM translations t2 JOIN translation_keys k2 ON k2.id = t2.key_id WHERE k2.key = 'app.skills.subscribeAll' AND t2.language = 'vi')
    WHEN k.key = 'app.mcp.subscribeN' THEN EXISTS (SELECT 1 FROM translations t2 JOIN translation_keys k2 ON k2.id = t2.key_id WHERE k2.key = 'app.skills.subscribeN' AND t2.language = 'vi')
    WHEN k.key = 'app.mcp.subscribeToast' THEN TRUE
    WHEN k.key = 'app.mcp.subscribeY' THEN EXISTS (SELECT 1 FROM translations t2 JOIN translation_keys k2 ON k2.id = t2.key_id WHERE k2.key = 'app.skills.subscribeY' AND t2.language = 'vi')
    WHEN k.key = 'app.mcp.subscribed' THEN EXISTS (SELECT 1 FROM translations t2 JOIN translation_keys k2 ON k2.id = t2.key_id WHERE k2.key = 'app.skills.subscribed' AND t2.language = 'vi')
    WHEN k.key = 'app.mcp.subscribedToast' THEN EXISTS (SELECT 1 FROM translations t2 JOIN translation_keys k2 ON k2.id = t2.key_id WHERE k2.key = 'app.skills.subscribedToast' AND t2.language = 'vi')
    WHEN k.key = 'app.mcp.tabComments' THEN EXISTS (SELECT 1 FROM translations t2 JOIN translation_keys k2 ON k2.id = t2.key_id WHERE k2.key = 'app.skills.tabComments' AND t2.language = 'vi')
    WHEN k.key = 'app.mcp.tabInfo' THEN EXISTS (SELECT 1 FROM translations t2 JOIN translation_keys k2 ON k2.id = t2.key_id WHERE k2.key = 'app.skills.tabInfo' AND t2.language = 'vi')
    WHEN k.key = 'app.mcp.tabSkills' THEN TRUE
    WHEN k.key = 'app.mcp.tabVersions' THEN EXISTS (SELECT 1 FROM translations t2 JOIN translation_keys k2 ON k2.id = t2.key_id WHERE k2.key = 'app.skills.tabVersions' AND t2.language = 'vi')
    WHEN k.key = 'app.mcp.title' THEN TRUE
    WHEN k.key = 'app.mcp.underReview' THEN EXISTS (SELECT 1 FROM translations t2 JOIN translation_keys k2 ON k2.id = t2.key_id WHERE k2.key = 'app.skills.underReview' AND t2.language = 'vi')
    WHEN k.key = 'app.mcp.unsubscribedToast' THEN EXISTS (SELECT 1 FROM translations t2 JOIN translation_keys k2 ON k2.id = t2.key_id WHERE k2.key = 'app.skills.unsubscribedToast' AND t2.language = 'vi')
    WHEN k.key = 'app.mcp.updateFileRequired' THEN EXISTS (SELECT 1 FROM translations t2 JOIN translation_keys k2 ON k2.id = t2.key_id WHERE k2.key = 'app.skills.updateFileRequired' AND t2.language = 'vi')
    WHEN k.key = 'app.mcp.updateFilesHint' THEN EXISTS (SELECT 1 FROM translations t2 JOIN translation_keys k2 ON k2.id = t2.key_id WHERE k2.key = 'app.skills.updateFilesHint' AND t2.language = 'vi')
    WHEN k.key = 'app.mcp.updateHint' THEN EXISTS (SELECT 1 FROM translations t2 JOIN translation_keys k2 ON k2.id = t2.key_id WHERE k2.key = 'app.skills.updateHint' AND t2.language = 'vi')
    WHEN k.key = 'app.mcp.updateInfo' THEN TRUE
    WHEN k.key = 'app.mcp.updateInfoPh' THEN TRUE
    WHEN k.key = 'app.mcp.updateInfoRequired' THEN TRUE
    WHEN k.key = 'app.mcp.updateInfoTooLong' THEN TRUE
    WHEN k.key = 'app.mcp.updateSubmitted' THEN EXISTS (SELECT 1 FROM translations t2 JOIN translation_keys k2 ON k2.id = t2.key_id WHERE k2.key = 'app.skills.updateSubmitted' AND t2.language = 'vi')
    WHEN k.key = 'app.mcp.updateTitle' THEN EXISTS (SELECT 1 FROM translations t2 JOIN translation_keys k2 ON k2.id = t2.key_id WHERE k2.key = 'app.skills.updateTitle' AND t2.language = 'vi')
    WHEN k.key = 'app.mcp.updateVersion' THEN EXISTS (SELECT 1 FROM translations t2 JOIN translation_keys k2 ON k2.id = t2.key_id WHERE k2.key = 'app.skills.updateVersion' AND t2.language = 'vi')
    WHEN k.key = 'app.mcp.updated' THEN EXISTS (SELECT 1 FROM translations t2 JOIN translation_keys k2 ON k2.id = t2.key_id WHERE k2.key = 'app.skills.updated' AND t2.language = 'vi')
    WHEN k.key = 'app.mcp.updatedToast' THEN EXISTS (SELECT 1 FROM translations t2 JOIN translation_keys k2 ON k2.id = t2.key_id WHERE k2.key = 'app.skills.updatedToast' AND t2.language = 'vi')
    WHEN k.key = 'app.mcp.uploadNewFile' THEN TRUE
    WHEN k.key = 'app.mcp.version' THEN EXISTS (SELECT 1 FROM translations t2 JOIN translation_keys k2 ON k2.id = t2.key_id WHERE k2.key = 'app.skills.version' AND t2.language = 'vi')
    WHEN k.key = 'app.mcp.versionApproved' THEN EXISTS (SELECT 1 FROM translations t2 JOIN translation_keys k2 ON k2.id = t2.key_id WHERE k2.key = 'app.skills.versionApproved' AND t2.language = 'vi')
    WHEN k.key = 'app.mcp.versionCancelled' THEN EXISTS (SELECT 1 FROM translations t2 JOIN translation_keys k2 ON k2.id = t2.key_id WHERE k2.key = 'app.skills.versionCancelled' AND t2.language = 'vi')
    WHEN k.key = 'app.mcp.versionConfirmDesc' THEN EXISTS (SELECT 1 FROM translations t2 JOIN translation_keys k2 ON k2.id = t2.key_id WHERE k2.key = 'app.skill.versionConfirmDesc' AND t2.language = 'vi')
    WHEN k.key = 'app.mcp.versionConfirmTitle' THEN EXISTS (SELECT 1 FROM translations t2 JOIN translation_keys k2 ON k2.id = t2.key_id WHERE k2.key = 'app.skill.versionConfirmTitle' AND t2.language = 'vi')
    WHEN k.key = 'app.mcp.versionCreatedToast' THEN TRUE
    WHEN k.key = 'app.mcp.versionInfo' THEN TRUE
    WHEN k.key = 'app.mcp.versionNote' THEN TRUE
    WHEN k.key = 'app.mcp.versionNum' THEN TRUE
    WHEN k.key = 'app.mcp.versionRejected' THEN EXISTS (SELECT 1 FROM translations t2 JOIN translation_keys k2 ON k2.id = t2.key_id WHERE k2.key = 'app.skills.versionRejected' AND t2.language = 'vi')
    WHEN k.key = 'app.mcp.viewCount' THEN TRUE
    WHEN k.key = 'app.mcp.withdraw' THEN EXISTS (SELECT 1 FROM translations t2 JOIN translation_keys k2 ON k2.id = t2.key_id WHERE k2.key = 'app.skills.withdraw' AND t2.language = 'vi')
    WHEN k.key = 'app.mcp.withdrawDesc' THEN EXISTS (SELECT 1 FROM translations t2 JOIN translation_keys k2 ON k2.id = t2.key_id WHERE k2.key = 'app.skills.withdrawDesc' AND t2.language = 'vi')
    WHEN k.key = 'app.mcp.withdrawTitle' THEN EXISTS (SELECT 1 FROM translations t2 JOIN translation_keys k2 ON k2.id = t2.key_id WHERE k2.key = 'app.skills.withdrawTitle' AND t2.language = 'vi')
    WHEN k.key = 'app.mcp.withdrawn' THEN EXISTS (SELECT 1 FROM translations t2 JOIN translation_keys k2 ON k2.id = t2.key_id WHERE k2.key = 'app.skills.withdrawn' AND t2.language = 'vi')
    WHEN k.key = 'app.mcp.withdrawnToast' THEN TRUE
    WHEN k.key = 'app.mcp.writeComment' THEN EXISTS (SELECT 1 FROM translations t2 JOIN translation_keys k2 ON k2.id = t2.key_id WHERE k2.key = 'app.skills.writeComment' AND t2.language = 'vi')
    WHEN k.key = 'app.mcp.youRate' THEN TRUE
  END
ON CONFLICT (key_id, language) DO NOTHING;

-- ko
INSERT INTO translations (key_id, language, value)
SELECT k.id, 'ko',
  CASE
    WHEN k.key = 'app.mcp.addBtn' THEN (SELECT value FROM translations t2 JOIN translation_keys k2 ON k2.id = t2.key_id WHERE k2.key = 'app.skills.addBtn' AND t2.language = 'ko')
    WHEN k.key = 'app.mcp.addTitle' THEN (SELECT value FROM translations t2 JOIN translation_keys k2 ON k2.id = t2.key_id WHERE k2.key = 'app.skills.addTitle' AND t2.language = 'ko')
    WHEN k.key = 'app.mcp.adminBadge' THEN (SELECT value FROM translations t2 JOIN translation_keys k2 ON k2.id = t2.key_id WHERE k2.key = 'app.skills.adminBadge' AND t2.language = 'ko')
    WHEN k.key = 'app.mcp.adminOnlyCat' THEN (SELECT value FROM translations t2 JOIN translation_keys k2 ON k2.id = t2.key_id WHERE k2.key = 'app.skills.adminOnlyCat' AND t2.language = 'ko')
    WHEN k.key = 'app.mcp.adminOnlyStage' THEN (SELECT value FROM translations t2 JOIN translation_keys k2 ON k2.id = t2.key_id WHERE k2.key = 'app.skills.adminOnlyStage' AND t2.language = 'ko')
    WHEN k.key = 'app.mcp.allStatuses' THEN '모든 상태'
    WHEN k.key = 'app.mcp.approve' THEN (SELECT value FROM translations t2 JOIN translation_keys k2 ON k2.id = t2.key_id WHERE k2.key = 'app.skills.approve' AND t2.language = 'ko')
    WHEN k.key = 'app.mcp.approveVersion' THEN (SELECT value FROM translations t2 JOIN translation_keys k2 ON k2.id = t2.key_id WHERE k2.key = 'app.skills.approveVersion' AND t2.language = 'ko')
    WHEN k.key = 'app.mcp.approved' THEN '승인됨'
    WHEN k.key = 'app.mcp.approvedFilesHint' THEN (SELECT value FROM translations t2 JOIN translation_keys k2 ON k2.id = t2.key_id WHERE k2.key = 'app.skills.approvedFilesHint' AND t2.language = 'ko')
    WHEN k.key = 'app.mcp.approvedMcpTab' THEN '승인된 MCP ({n})'
    WHEN k.key = 'app.mcp.approvedToast' THEN 'MCP가 승인되었습니다.'
    WHEN k.key = 'app.mcp.browseTitle' THEN '모든 MCP 보기'
    WHEN k.key = 'app.mcp.cancel' THEN '취소'
    WHEN k.key = 'app.mcp.cancelUpdateBtn' THEN (SELECT value FROM translations t2 JOIN translation_keys k2 ON k2.id = t2.key_id WHERE k2.key = 'app.skills.cancelUpdateBtn' AND t2.language = 'ko')
    WHEN k.key = 'app.mcp.cancelUpdateDesc' THEN (SELECT value FROM translations t2 JOIN translation_keys k2 ON k2.id = t2.key_id WHERE k2.key = 'app.skills.cancelUpdateDesc' AND t2.language = 'ko')
    WHEN k.key = 'app.mcp.cancelUpdateTitle' THEN (SELECT value FROM translations t2 JOIN translation_keys k2 ON k2.id = t2.key_id WHERE k2.key = 'app.skills.cancelUpdateTitle' AND t2.language = 'ko')
    WHEN k.key = 'app.mcp.cancelVersionUpdate' THEN (SELECT value FROM translations t2 JOIN translation_keys k2 ON k2.id = t2.key_id WHERE k2.key = 'app.skills.cancelVersionUpdate' AND t2.language = 'ko')
    WHEN k.key = 'app.mcp.cannotDeleteItem' THEN (SELECT value FROM translations t2 JOIN translation_keys k2 ON k2.id = t2.key_id WHERE k2.key = 'app.skills.cannotDeleteItem' AND t2.language = 'ko')
    WHEN k.key = 'app.mcp.cannotDeleteList' THEN (SELECT value FROM translations t2 JOIN translation_keys k2 ON k2.id = t2.key_id WHERE k2.key = 'app.skills.cannotDeleteList' AND t2.language = 'ko')
    WHEN k.key = 'app.mcp.cannotDeleteTitle' THEN (SELECT value FROM translations t2 JOIN translation_keys k2 ON k2.id = t2.key_id WHERE k2.key = 'app.skills.cannotDeleteTitle' AND t2.language = 'ko')
    WHEN k.key = 'app.mcp.cannotDeleteTotal' THEN (SELECT value FROM translations t2 JOIN translation_keys k2 ON k2.id = t2.key_id WHERE k2.key = 'app.skills.cannotDeleteTotal' AND t2.language = 'ko')
    WHEN k.key = 'app.mcp.cardsAria' THEN (SELECT value FROM translations t2 JOIN translation_keys k2 ON k2.id = t2.key_id WHERE k2.key = 'app.skills.cardsAria' AND t2.language = 'ko')
    WHEN k.key = 'app.mcp.catAdded' THEN (SELECT value FROM translations t2 JOIN translation_keys k2 ON k2.id = t2.key_id WHERE k2.key = 'app.skills.catAdded' AND t2.language = 'ko')
    WHEN k.key = 'app.mcp.catDeleteDesc' THEN (SELECT value FROM translations t2 JOIN translation_keys k2 ON k2.id = t2.key_id WHERE k2.key = 'app.skills.catDeleteDesc' AND t2.language = 'ko')
    WHEN k.key = 'app.mcp.catDeleteDescSimple' THEN (SELECT value FROM translations t2 JOIN translation_keys k2 ON k2.id = t2.key_id WHERE k2.key = 'app.skills.catDeleteDescSimple' AND t2.language = 'ko')
    WHEN k.key = 'app.mcp.catDeleted' THEN (SELECT value FROM translations t2 JOIN translation_keys k2 ON k2.id = t2.key_id WHERE k2.key = 'app.skills.catDeleted' AND t2.language = 'ko')
    WHEN k.key = 'app.mcp.catUpdated' THEN (SELECT value FROM translations t2 JOIN translation_keys k2 ON k2.id = t2.key_id WHERE k2.key = 'app.skills.catUpdated' AND t2.language = 'ko')
    WHEN k.key = 'app.mcp.categories' THEN (SELECT value FROM translations t2 JOIN translation_keys k2 ON k2.id = t2.key_id WHERE k2.key = 'app.skills.categories' AND t2.language = 'ko')
    WHEN k.key = 'app.mcp.categoryName' THEN (SELECT value FROM translations t2 JOIN translation_keys k2 ON k2.id = t2.key_id WHERE k2.key = 'app.skills.categoryName' AND t2.language = 'ko')
    WHEN k.key = 'app.mcp.changelog' THEN '변경 내역'
    WHEN k.key = 'app.mcp.changelogPh' THEN '이 버전에서 변경된 내용을 설명하세요...'
    WHEN k.key = 'app.mcp.changelogRequired' THEN '새 버전에는 변경 내역이 필요합니다.'
    WHEN k.key = 'app.mcp.changelogTooLong' THEN '변경 내역은 최대 {n}자입니다.'
    WHEN k.key = 'app.mcp.clearCreator' THEN (SELECT value FROM translations t2 JOIN translation_keys k2 ON k2.id = t2.key_id WHERE k2.key = 'app.skills.clearCreator' AND t2.language = 'ko')
    WHEN k.key = 'app.mcp.close' THEN '닫기'
    WHEN k.key = 'app.mcp.colCreated' THEN '생성일'
    WHEN k.key = 'app.mcp.colCreator' THEN (SELECT value FROM translations t2 JOIN translation_keys k2 ON k2.id = t2.key_id WHERE k2.key = 'app.skills.colCreator' AND t2.language = 'ko')
    WHEN k.key = 'app.mcp.colDescription' THEN '설명'
    WHEN k.key = 'app.mcp.colDownloads' THEN (SELECT value FROM translations t2 JOIN translation_keys k2 ON k2.id = t2.key_id WHERE k2.key = 'app.skills.colDownloads' AND t2.language = 'ko')
    WHEN k.key = 'app.mcp.colName' THEN (SELECT value FROM translations t2 JOIN translation_keys k2 ON k2.id = t2.key_id WHERE k2.key = 'app.skills.colName' AND t2.language = 'ko')
    WHEN k.key = 'app.mcp.colRating' THEN (SELECT value FROM translations t2 JOIN translation_keys k2 ON k2.id = t2.key_id WHERE k2.key = 'app.skills.colRating' AND t2.language = 'ko')
    WHEN k.key = 'app.mcp.colStatus' THEN '상태'
    WHEN k.key = 'app.mcp.colSubscribe' THEN (SELECT value FROM translations t2 JOIN translation_keys k2 ON k2.id = t2.key_id WHERE k2.key = 'app.skills.colSubscribe' AND t2.language = 'ko')
    WHEN k.key = 'app.mcp.colViews' THEN (SELECT value FROM translations t2 JOIN translation_keys k2 ON k2.id = t2.key_id WHERE k2.key = 'app.skills.colViews' AND t2.language = 'ko')
    WHEN k.key = 'app.mcp.confirm' THEN '확인'
    WHEN k.key = 'app.mcp.copyLink' THEN (SELECT value FROM translations t2 JOIN translation_keys k2 ON k2.id = t2.key_id WHERE k2.key = 'app.skill.copyLink' AND t2.language = 'ko')
    WHEN k.key = 'app.mcp.copyLinkCopied' THEN (SELECT value FROM translations t2 JOIN translation_keys k2 ON k2.id = t2.key_id WHERE k2.key = 'app.skill.copyLinkCopied' AND t2.language = 'ko')
    WHEN k.key = 'app.mcp.copyLinkFailed' THEN (SELECT value FROM translations t2 JOIN translation_keys k2 ON k2.id = t2.key_id WHERE k2.key = 'app.skill.copyLinkFailed' AND t2.language = 'ko')
    WHEN k.key = 'app.mcp.copyLinkTooltip' THEN (SELECT value FROM translations t2 JOIN translation_keys k2 ON k2.id = t2.key_id WHERE k2.key = 'app.skill.copyLinkTooltip' AND t2.language = 'ko')
    WHEN k.key = 'app.mcp.createVersion' THEN '버전 생성'
    WHEN k.key = 'app.mcp.createdToast' THEN (SELECT value FROM translations t2 JOIN translation_keys k2 ON k2.id = t2.key_id WHERE k2.key = 'app.skills.createdToast' AND t2.language = 'ko')
    WHEN k.key = 'app.mcp.createdUpdated' THEN (SELECT value FROM translations t2 JOIN translation_keys k2 ON k2.id = t2.key_id WHERE k2.key = 'app.skills.createdUpdated' AND t2.language = 'ko')
    WHEN k.key = 'app.mcp.creator' THEN (SELECT value FROM translations t2 JOIN translation_keys k2 ON k2.id = t2.key_id WHERE k2.key = 'app.skills.creator' AND t2.language = 'ko')
    WHEN k.key = 'app.mcp.current' THEN (SELECT value FROM translations t2 JOIN translation_keys k2 ON k2.id = t2.key_id WHERE k2.key = 'app.skills.current' AND t2.language = 'ko')
    WHEN k.key = 'app.mcp.currentFile' THEN '현재 파일'
    WHEN k.key = 'app.mcp.deleteBtn' THEN (SELECT value FROM translations t2 JOIN translation_keys k2 ON k2.id = t2.key_id WHERE k2.key = 'app.skills.deleteBtn' AND t2.language = 'ko')
    WHEN k.key = 'app.mcp.deleteCategoryAria' THEN (SELECT value FROM translations t2 JOIN translation_keys k2 ON k2.id = t2.key_id WHERE k2.key = 'app.skills.deleteCategoryAria' AND t2.language = 'ko')
    WHEN k.key = 'app.mcp.deleteCommentAria' THEN (SELECT value FROM translations t2 JOIN translation_keys k2 ON k2.id = t2.key_id WHERE k2.key = 'app.skills.deleteCommentAria' AND t2.language = 'ko')
    WHEN k.key = 'app.mcp.deleteDeniedDesc' THEN '선택한 MCP 중 하나 이상을 삭제할 권한이 없습니다.'
    WHEN k.key = 'app.mcp.deleteDeniedList' THEN '삭제할 수 없는 MCP:'
    WHEN k.key = 'app.mcp.deleteDeniedTitle' THEN '선택한 MCP를 모두 삭제할 수 없습니다'
    WHEN k.key = 'app.mcp.deleteDeniedTotal' THEN '총 {n}개 선택, {m}개 삭제 불가.'
    WHEN k.key = 'app.mcp.deleteDesc' THEN (SELECT value FROM translations t2 JOIN translation_keys k2 ON k2.id = t2.key_id WHERE k2.key = 'app.skills.deleteDesc' AND t2.language = 'ko')
    WHEN k.key = 'app.mcp.deleteStageAria' THEN (SELECT value FROM translations t2 JOIN translation_keys k2 ON k2.id = t2.key_id WHERE k2.key = 'app.skills.deleteStageAria' AND t2.language = 'ko')
    WHEN k.key = 'app.mcp.deleteTitle' THEN (SELECT value FROM translations t2 JOIN translation_keys k2 ON k2.id = t2.key_id WHERE k2.key = 'app.skills.deleteTitle' AND t2.language = 'ko')
    WHEN k.key = 'app.mcp.deletedToast' THEN (SELECT value FROM translations t2 JOIN translation_keys k2 ON k2.id = t2.key_id WHERE k2.key = 'app.skills.deletedToast' AND t2.language = 'ko')
    WHEN k.key = 'app.mcp.deletedToastDetail' THEN (SELECT value FROM translations t2 JOIN translation_keys k2 ON k2.id = t2.key_id WHERE k2.key = 'app.skills.deletedToastDetail' AND t2.language = 'ko')
    WHEN k.key = 'app.mcp.depAlready' THEN '이 MCP는 이미 의존성 목록에 있습니다.'
    WHEN k.key = 'app.mcp.depBanner' THEN '다음 MCP가 승인되기 전까지 이 스킬을 제출할 수 없습니다:'
    WHEN k.key = 'app.mcp.depDeleted' THEN '삭제됨'
    WHEN k.key = 'app.mcp.depDeletedTip' THEN '이 MCP는 삭제되었습니다. Skill은 계속 작동하며 이 의존성을 제거할 수 있습니다.'
    WHEN k.key = 'app.mcp.depLabel' THEN 'MCP 의존성'
    WHEN k.key = 'app.mcp.depLoading' THEN '제안 불러오는 중...'
    WHEN k.key = 'app.mcp.depPh' THEN 'MCP 의존성 선택...'
    WHEN k.key = 'app.mcp.dependentSkills' THEN NULL
    WHEN k.key = 'app.mcp.descRequired' THEN '설명은 필수입니다.'
    WHEN k.key = 'app.mcp.description' THEN '설명'
    WHEN k.key = 'app.mcp.descriptionPh' THEN 'MCP 설명 및 사용 가이드를 입력하세요...'
    WHEN k.key = 'app.mcp.detailTitle' THEN (SELECT value FROM translations t2 JOIN translation_keys k2 ON k2.id = t2.key_id WHERE k2.key = 'app.skills.detailTitle' AND t2.language = 'ko')
    WHEN k.key = 'app.mcp.download' THEN '다운로드'
    WHEN k.key = 'app.mcp.downloadAll' THEN (SELECT value FROM translations t2 JOIN translation_keys k2 ON k2.id = t2.key_id WHERE k2.key = 'app.skills.downloadAll' AND t2.language = 'ko')
    WHEN k.key = 'app.mcp.downloadCount' THEN '다운로드: {n}'
    WHEN k.key = 'app.mcp.downloadExcel' THEN (SELECT value FROM translations t2 JOIN translation_keys k2 ON k2.id = t2.key_id WHERE k2.key = 'app.skills.downloadExcel' AND t2.language = 'ko')
    WHEN k.key = 'app.mcp.draft' THEN '임시 저장'
    WHEN k.key = 'app.mcp.draftHint' THEN (SELECT value FROM translations t2 JOIN translation_keys k2 ON k2.id = t2.key_id WHERE k2.key = 'app.skills.draftHint' AND t2.language = 'ko')
    WHEN k.key = 'app.mcp.draftNote' THEN 'MCP는 임시 저장 상태로 생성됩니다. 준비되면 승인 제출할 수 있습니다.'
    WHEN k.key = 'app.mcp.dragDropNewOr' THEN (SELECT value FROM translations t2 JOIN translation_keys k2 ON k2.id = t2.key_id WHERE k2.key = 'app.skills.dragDropNewOr' AND t2.language = 'ko')
    WHEN k.key = 'app.mcp.editBtn' THEN (SELECT value FROM translations t2 JOIN translation_keys k2 ON k2.id = t2.key_id WHERE k2.key = 'app.skills.editBtn' AND t2.language = 'ko')
    WHEN k.key = 'app.mcp.editCategoryAria' THEN (SELECT value FROM translations t2 JOIN translation_keys k2 ON k2.id = t2.key_id WHERE k2.key = 'app.skills.editCategoryAria' AND t2.language = 'ko')
    WHEN k.key = 'app.mcp.editCategoryTitle' THEN (SELECT value FROM translations t2 JOIN translation_keys k2 ON k2.id = t2.key_id WHERE k2.key = 'app.skills.editCategoryTitle' AND t2.language = 'ko')
    WHEN k.key = 'app.mcp.editNoteApproved' THEN '이 MCP는 승인되었습니다. 이름, 설명, 해시태그는 재승인 없이 즉시 업데이트됩니다.'
    WHEN k.key = 'app.mcp.editNoteStatus' THEN '{status} 상태의 MCP를 편집하고 있습니다. 변경 사항은 동일한 상태를 유지합니다.'
    WHEN k.key = 'app.mcp.editStageAria' THEN (SELECT value FROM translations t2 JOIN translation_keys k2 ON k2.id = t2.key_id WHERE k2.key = 'app.skills.editStageAria' AND t2.language = 'ko')
    WHEN k.key = 'app.mcp.editStageTitle' THEN (SELECT value FROM translations t2 JOIN translation_keys k2 ON k2.id = t2.key_id WHERE k2.key = 'app.skills.editStageTitle' AND t2.language = 'ko')
    WHEN k.key = 'app.mcp.editTitle' THEN (SELECT value FROM translations t2 JOIN translation_keys k2 ON k2.id = t2.key_id WHERE k2.key = 'app.skills.editTitle' AND t2.language = 'ko')
    WHEN k.key = 'app.mcp.emptyDesc' THEN (SELECT value FROM translations t2 JOIN translation_keys k2 ON k2.id = t2.key_id WHERE k2.key = 'app.skills.emptyDesc' AND t2.language = 'ko')
    WHEN k.key = 'app.mcp.emptyTitle' THEN '데이터를 찾을 수 없습니다.'
    WHEN k.key = 'app.mcp.exportedToast' THEN (SELECT value FROM translations t2 JOIN translation_keys k2 ON k2.id = t2.key_id WHERE k2.key = 'app.skills.exportedToast' AND t2.language = 'ko')
    WHEN k.key = 'app.mcp.file' THEN '파일'
    WHEN k.key = 'app.mcp.fileDropOr' THEN '파일을 여기에 드래그 & 드롭하거나'
    WHEN k.key = 'app.mcp.fileErrorFormat' THEN '"{name}": 지원하지 않는 파일 형식입니다. 허용: {exts}.'
    WHEN k.key = 'app.mcp.fileErrorSize' THEN '"{name}": 파일 크기는 최대 50MB입니다.'
    WHEN k.key = 'app.mcp.fileKeepHint' THEN '현재 파일을 유지하거나 새 파일을 업로드하세요.'
    WHEN k.key = 'app.mcp.fileRemoved' THEN (SELECT value FROM translations t2 JOIN translation_keys k2 ON k2.id = t2.key_id WHERE k2.key = 'app.skills.fileRemoved' AND t2.language = 'ko')
    WHEN k.key = 'app.mcp.fileRequired' THEN (SELECT value FROM translations t2 JOIN translation_keys k2 ON k2.id = t2.key_id WHERE k2.key = 'app.skills.fileRequired' AND t2.language = 'ko')
    WHEN k.key = 'app.mcp.fileRequiredUpload' THEN '새 파일을 업로드할 때 파일이 필요합니다.'
    WHEN k.key = 'app.mcp.fileSupported' THEN '지원: zip, 7z, json, yaml, yml, txt, md · 최대 50MB'
    WHEN k.key = 'app.mcp.filesToRemove' THEN (SELECT value FROM translations t2 JOIN translation_keys k2 ON k2.id = t2.key_id WHERE k2.key = 'app.skills.filesToRemove' AND t2.language = 'ko')
    WHEN k.key = 'app.mcp.hashtag' THEN '해시태그'
    WHEN k.key = 'app.mcp.hashtagExists' THEN '해시태그가 이미 존재합니다.'
    WHEN k.key = 'app.mcp.hashtagPh' THEN '해시태그를 입력하고 Enter를 누르세요...'
    WHEN k.key = 'app.mcp.hashtagRemoveAria' THEN '해시태그 제거'
    WHEN k.key = 'app.mcp.lastRejected' THEN (SELECT value FROM translations t2 JOIN translation_keys k2 ON k2.id = t2.key_id WHERE k2.key = 'app.skills.lastRejected' AND t2.language = 'ko')
    WHEN k.key = 'app.mcp.listAria' THEN (SELECT value FROM translations t2 JOIN translation_keys k2 ON k2.id = t2.key_id WHERE k2.key = 'app.skills.listAria' AND t2.language = 'ko')
    WHEN k.key = 'app.mcp.loadError' THEN (SELECT value FROM translations t2 JOIN translation_keys k2 ON k2.id = t2.key_id WHERE k2.key = 'app.skills.loadError' AND t2.language = 'ko')
    WHEN k.key = 'app.mcp.loadErrorDetail' THEN (SELECT value FROM translations t2 JOIN translation_keys k2 ON k2.id = t2.key_id WHERE k2.key = 'app.skills.loadErrorDetail' AND t2.language = 'ko')
    WHEN k.key = 'app.mcp.manageTaxonomy' THEN (SELECT value FROM translations t2 JOIN translation_keys k2 ON k2.id = t2.key_id WHERE k2.key = 'app.skills.manageTaxonomy' AND t2.language = 'ko')
    WHEN k.key = 'app.mcp.manageTaxonomyTitle' THEN (SELECT value FROM translations t2 JOIN translation_keys k2 ON k2.id = t2.key_id WHERE k2.key = 'app.skills.manageTaxonomyTitle' AND t2.language = 'ko')
    WHEN k.key = 'app.mcp.mcpCode' THEN NULL
    WHEN k.key = 'app.mcp.mcpCount' THEN (SELECT value FROM translations t2 JOIN translation_keys k2 ON k2.id = t2.key_id WHERE k2.key = 'app.skills.skillCount' AND t2.language = 'ko')
    WHEN k.key = 'app.mcp.mcpDeleted' THEN (SELECT value FROM translations t2 JOIN translation_keys k2 ON k2.id = t2.key_id WHERE k2.key = 'app.skills.mcpDeleted' AND t2.language = 'ko')
    WHEN k.key = 'app.mcp.mcpDeletedTip' THEN (SELECT value FROM translations t2 JOIN translation_keys k2 ON k2.id = t2.key_id WHERE k2.key = 'app.skills.mcpDeletedTip' AND t2.language = 'ko')
    WHEN k.key = 'app.mcp.mcpDepsWarnDesc' THEN (SELECT value FROM translations t2 JOIN translation_keys k2 ON k2.id = t2.key_id WHERE k2.key = 'app.skills.mcpDepsWarnDesc' AND t2.language = 'ko')
    WHEN k.key = 'app.mcp.mcpDepsWarnNote' THEN (SELECT value FROM translations t2 JOIN translation_keys k2 ON k2.id = t2.key_id WHERE k2.key = 'app.skills.mcpDepsWarnNote' AND t2.language = 'ko')
    WHEN k.key = 'app.mcp.mcpDepsWarnTitle' THEN (SELECT value FROM translations t2 JOIN translation_keys k2 ON k2.id = t2.key_id WHERE k2.key = 'app.skills.mcpDepsWarnTitle' AND t2.language = 'ko')
    WHEN k.key = 'app.mcp.mcpUnavailableGone' THEN (SELECT value FROM translations t2 JOIN translation_keys k2 ON k2.id = t2.key_id WHERE k2.key = 'app.skills.mcpUnavailableGone' AND t2.language = 'ko')
    WHEN k.key = 'app.mcp.mcpUnavailableGoneWarn' THEN (SELECT value FROM translations t2 JOIN translation_keys k2 ON k2.id = t2.key_id WHERE k2.key = 'app.skills.mcpUnavailableGoneWarn' AND t2.language = 'ko')
    WHEN k.key = 'app.mcp.mcpUnavailableNotApproved' THEN (SELECT value FROM translations t2 JOIN translation_keys k2 ON k2.id = t2.key_id WHERE k2.key = 'app.skills.mcpUnavailableNotApproved' AND t2.language = 'ko')
    WHEN k.key = 'app.mcp.mcpUnavailableNotApprovedWarn' THEN (SELECT value FROM translations t2 JOIN translation_keys k2 ON k2.id = t2.key_id WHERE k2.key = 'app.skills.mcpUnavailableNotApprovedWarn' AND t2.language = 'ko')
    WHEN k.key = 'app.mcp.myMcpTab' THEN '내 MCP ({n})'
    WHEN k.key = 'app.mcp.myMcpsEmpty' THEN '임시 저장 / 대기 / 거절 상태의 MCP가 없습니다.'
    WHEN k.key = 'app.mcp.name' THEN 'MCP 이름'
    WHEN k.key = 'app.mcp.nameEmpty' THEN 'MCP 이름을 입력해야 합니다.'
    WHEN k.key = 'app.mcp.nameLocked' THEN (SELECT value FROM translations t2 JOIN translation_keys k2 ON k2.id = t2.key_id WHERE k2.key = 'app.skills.nameLocked' AND t2.language = 'ko')
    WHEN k.key = 'app.mcp.namePh' THEN 'MCP 이름을 입력하세요...'
    WHEN k.key = 'app.mcp.nameTooLong' THEN 'MCP 이름은 최대 {n}자입니다.'
    WHEN k.key = 'app.mcp.newCategoryPh' THEN (SELECT value FROM translations t2 JOIN translation_keys k2 ON k2.id = t2.key_id WHERE k2.key = 'app.skills.newCategoryPh' AND t2.language = 'ko')
    WHEN k.key = 'app.mcp.newFiles' THEN (SELECT value FROM translations t2 JOIN translation_keys k2 ON k2.id = t2.key_id WHERE k2.key = 'app.skills.newFiles' AND t2.language = 'ko')
    WHEN k.key = 'app.mcp.newMcpDraftHint' THEN 'MCP가 Draft 상태로 생성되었습니다. MCP Management에서 승인 제출 후, 다시 스킬을 편집하여 이 MCP를 추가하세요.'
    WHEN k.key = 'app.mcp.newStagePh' THEN (SELECT value FROM translations t2 JOIN translation_keys k2 ON k2.id = t2.key_id WHERE k2.key = 'app.skills.newStagePh' AND t2.language = 'ko')
    WHEN k.key = 'app.mcp.noCategoriesYet' THEN (SELECT value FROM translations t2 JOIN translation_keys k2 ON k2.id = t2.key_id WHERE k2.key = 'app.skills.noCategoriesYet' AND t2.language = 'ko')
    WHEN k.key = 'app.mcp.noComments' THEN (SELECT value FROM translations t2 JOIN translation_keys k2 ON k2.id = t2.key_id WHERE k2.key = 'app.skills.noComments' AND t2.language = 'ko')
    WHEN k.key = 'app.mcp.noDeps' THEN 'MCP 의존성 없음'
    WHEN k.key = 'app.mcp.noDescription' THEN (SELECT value FROM translations t2 JOIN translation_keys k2 ON k2.id = t2.key_id WHERE k2.key = 'app.skills.noDescription' AND t2.language = 'ko')
    WHEN k.key = 'app.mcp.noMatchDesc' THEN (SELECT value FROM translations t2 JOIN translation_keys k2 ON k2.id = t2.key_id WHERE k2.key = 'app.skills.noMatchDesc' AND t2.language = 'ko')
    WHEN k.key = 'app.mcp.noOptions' THEN (SELECT value FROM translations t2 JOIN translation_keys k2 ON k2.id = t2.key_id WHERE k2.key = 'app.skills.noOptions' AND t2.language = 'ko')
    WHEN k.key = 'app.mcp.noRating' THEN '평점 없음'
    WHEN k.key = 'app.mcp.noSkills' THEN '이 MCP를 사용하는 스킬이 없습니다.'
    WHEN k.key = 'app.mcp.noStagesYet' THEN (SELECT value FROM translations t2 JOIN translation_keys k2 ON k2.id = t2.key_id WHERE k2.key = 'app.skills.noStagesYet' AND t2.language = 'ko')
    WHEN k.key = 'app.mcp.noSuggestions' THEN '제안 없음'
    WHEN k.key = 'app.mcp.noUsersSelected' THEN (SELECT value FROM translations t2 JOIN translation_keys k2 ON k2.id = t2.key_id WHERE k2.key = 'app.skills.noUsersSelected' AND t2.language = 'ko')
    WHEN k.key = 'app.mcp.noVersions' THEN (SELECT value FROM translations t2 JOIN translation_keys k2 ON k2.id = t2.key_id WHERE k2.key = 'app.skills.noVersions' AND t2.language = 'ko')
    WHEN k.key = 'app.mcp.notFoundOrNoAccess' THEN (SELECT value FROM translations t2 JOIN translation_keys k2 ON k2.id = t2.key_id WHERE k2.key = 'app.skill.notFoundOrNoAccess' AND t2.language = 'ko')
    WHEN k.key = 'app.mcp.onlyApprovedComment' THEN (SELECT value FROM translations t2 JOIN translation_keys k2 ON k2.id = t2.key_id WHERE k2.key = 'app.skills.onlyApprovedComment' AND t2.language = 'ko')
    WHEN k.key = 'app.mcp.onlyApprovedRate' THEN (SELECT value FROM translations t2 JOIN translation_keys k2 ON k2.id = t2.key_id WHERE k2.key = 'app.skills.onlyApprovedRate' AND t2.language = 'ko')
    WHEN k.key = 'app.mcp.openListAria' THEN 'MCP 목록 열기'
    WHEN k.key = 'app.mcp.optional' THEN (SELECT value FROM translations t2 JOIN translation_keys k2 ON k2.id = t2.key_id WHERE k2.key = 'app.skills.optional' AND t2.language = 'ko')
    WHEN k.key = 'app.mcp.pending' THEN '승인 대기'
    WHEN k.key = 'app.mcp.pendingBanner' THEN (SELECT value FROM translations t2 JOIN translation_keys k2 ON k2.id = t2.key_id WHERE k2.key = 'app.skills.pendingBanner' AND t2.language = 'ko')
    WHEN k.key = 'app.mcp.pendingVersion' THEN (SELECT value FROM translations t2 JOIN translation_keys k2 ON k2.id = t2.key_id WHERE k2.key = 'app.skills.pendingVersion' AND t2.language = 'ko')
    WHEN k.key = 'app.mcp.pendingVersionBanner' THEN (SELECT value FROM translations t2 JOIN translation_keys k2 ON k2.id = t2.key_id WHERE k2.key = 'app.skills.pendingVersionBanner' AND t2.language = 'ko')
    WHEN k.key = 'app.mcp.pinBlockedArchived' THEN '이 MCP는 아카이브되었습니다. 먼저 아카이브 해제 후 스킬에 추가하세요.'
    WHEN k.key = 'app.mcp.pinBlockedDraft' THEN '이 MCP는 아직 Draft 상태입니다. 먼저 승인 제출을 한 다음 스킬에 추가하세요.'
    WHEN k.key = 'app.mcp.pinBlockedPending' THEN '이 MCP는 관리자 승인을 기다리고 있습니다. 승인된 후 스킬에 추가하세요.'
    WHEN k.key = 'app.mcp.pinBlockedRejected' THEN '이 MCP는 거부되었습니다. 먼저 승인 재제출을 한 다음 스킬에 추가하세요.'
    WHEN k.key = 'app.mcp.processStages' THEN (SELECT value FROM translations t2 JOIN translation_keys k2 ON k2.id = t2.key_id WHERE k2.key = 'app.skills.processStages' AND t2.language = 'ko')
    WHEN k.key = 'app.mcp.rateOnlyApproved' THEN '승인된 MCP만 평가할 수 있습니다.'
    WHEN k.key = 'app.mcp.rateStars' THEN (SELECT value FROM translations t2 JOIN translation_keys k2 ON k2.id = t2.key_id WHERE k2.key = 'app.skills.rateStars' AND t2.language = 'ko')
    WHEN k.key = 'app.mcp.reject' THEN (SELECT value FROM translations t2 JOIN translation_keys k2 ON k2.id = t2.key_id WHERE k2.key = 'app.skills.reject' AND t2.language = 'ko')
    WHEN k.key = 'app.mcp.rejectReasonPh' THEN (SELECT value FROM translations t2 JOIN translation_keys k2 ON k2.id = t2.key_id WHERE k2.key = 'app.skills.rejectReasonPh' AND t2.language = 'ko')
    WHEN k.key = 'app.mcp.rejectReasonRequired' THEN (SELECT value FROM translations t2 JOIN translation_keys k2 ON k2.id = t2.key_id WHERE k2.key = 'app.skills.rejectReasonRequired' AND t2.language = 'ko')
    WHEN k.key = 'app.mcp.rejectTitle' THEN (SELECT value FROM translations t2 JOIN translation_keys k2 ON k2.id = t2.key_id WHERE k2.key = 'app.skills.rejectTitle' AND t2.language = 'ko')
    WHEN k.key = 'app.mcp.rejectVersion' THEN (SELECT value FROM translations t2 JOIN translation_keys k2 ON k2.id = t2.key_id WHERE k2.key = 'app.skills.rejectVersion' AND t2.language = 'ko')
    WHEN k.key = 'app.mcp.rejected' THEN '거절됨'
    WHEN k.key = 'app.mcp.rejectedToast' THEN 'MCP가 거절되었습니다.'
    WHEN k.key = 'app.mcp.rejectionReason' THEN (SELECT value FROM translations t2 JOIN translation_keys k2 ON k2.id = t2.key_id WHERE k2.key = 'app.skills.rejectionReason' AND t2.language = 'ko')
    WHEN k.key = 'app.mcp.removeDepAria' THEN '{name} 제거'
    WHEN k.key = 'app.mcp.reuseFile' THEN '현재 파일 재사용'
    WHEN k.key = 'app.mcp.reviewSubmitted' THEN (SELECT value FROM translations t2 JOIN translation_keys k2 ON k2.id = t2.key_id WHERE k2.key = 'app.skills.reviewSubmitted' AND t2.language = 'ko')
    WHEN k.key = 'app.mcp.save' THEN '저장'
    WHEN k.key = 'app.mcp.saveDraft' THEN '임시 저장'
    WHEN k.key = 'app.mcp.search' THEN '검색'
    WHEN k.key = 'app.mcp.searchByName' THEN '이름으로 MCP 검색...'
    WHEN k.key = 'app.mcp.searchTitle' THEN (SELECT value FROM translations t2 JOIN translation_keys k2 ON k2.id = t2.key_id WHERE k2.key = 'app.skills.searchTitle' AND t2.language = 'ko')
    WHEN k.key = 'app.mcp.searchUserDesc' THEN (SELECT value FROM translations t2 JOIN translation_keys k2 ON k2.id = t2.key_id WHERE k2.key = 'app.skills.searchUserDesc' AND t2.language = 'ko')
    WHEN k.key = 'app.mcp.selectCreator' THEN (SELECT value FROM translations t2 JOIN translation_keys k2 ON k2.id = t2.key_id WHERE k2.key = 'app.skills.selectCreator' AND t2.language = 'ko')
    WHEN k.key = 'app.mcp.selected' THEN (SELECT value FROM translations t2 JOIN translation_keys k2 ON k2.id = t2.key_id WHERE k2.key = 'app.skills.selected' AND t2.language = 'ko')
    WHEN k.key = 'app.mcp.selectedCount' THEN '{n}개 MCP 선택됨'
    WHEN k.key = 'app.mcp.send' THEN (SELECT value FROM translations t2 JOIN translation_keys k2 ON k2.id = t2.key_id WHERE k2.key = 'app.skills.send' AND t2.language = 'ko')
    WHEN k.key = 'app.mcp.serverKey' THEN NULL
    WHEN k.key = 'app.mcp.showMore' THEN '더 보기 ({n} 남음)'
    WHEN k.key = 'app.mcp.skillsTab' THEN '이 MCP를 사용하는 스킬'
    WHEN k.key = 'app.mcp.stageAdded' THEN (SELECT value FROM translations t2 JOIN translation_keys k2 ON k2.id = t2.key_id WHERE k2.key = 'app.skills.stageAdded' AND t2.language = 'ko')
    WHEN k.key = 'app.mcp.stageDeleteDesc' THEN (SELECT value FROM translations t2 JOIN translation_keys k2 ON k2.id = t2.key_id WHERE k2.key = 'app.skills.stageDeleteDesc' AND t2.language = 'ko')
    WHEN k.key = 'app.mcp.stageDeleteDescSimple' THEN (SELECT value FROM translations t2 JOIN translation_keys k2 ON k2.id = t2.key_id WHERE k2.key = 'app.skills.stageDeleteDescSimple' AND t2.language = 'ko')
    WHEN k.key = 'app.mcp.stageDeleted' THEN (SELECT value FROM translations t2 JOIN translation_keys k2 ON k2.id = t2.key_id WHERE k2.key = 'app.skills.stageDeleted' AND t2.language = 'ko')
    WHEN k.key = 'app.mcp.stageDescLabel' THEN (SELECT value FROM translations t2 JOIN translation_keys k2 ON k2.id = t2.key_id WHERE k2.key = 'app.skills.stageDescLabel' AND t2.language = 'ko')
    WHEN k.key = 'app.mcp.stageDescPh' THEN (SELECT value FROM translations t2 JOIN translation_keys k2 ON k2.id = t2.key_id WHERE k2.key = 'app.skills.stageDescPh' AND t2.language = 'ko')
    WHEN k.key = 'app.mcp.stageName' THEN (SELECT value FROM translations t2 JOIN translation_keys k2 ON k2.id = t2.key_id WHERE k2.key = 'app.skills.stageName' AND t2.language = 'ko')
    WHEN k.key = 'app.mcp.stageUpdated' THEN (SELECT value FROM translations t2 JOIN translation_keys k2 ON k2.id = t2.key_id WHERE k2.key = 'app.skills.stageUpdated' AND t2.language = 'ko')
    WHEN k.key = 'app.mcp.status' THEN '상태'
    WHEN k.key = 'app.mcp.submit' THEN '승인 제출'
    WHEN k.key = 'app.mcp.submitForReview' THEN (SELECT value FROM translations t2 JOIN translation_keys k2 ON k2.id = t2.key_id WHERE k2.key = 'app.skills.submitForReview' AND t2.language = 'ko')
    WHEN k.key = 'app.mcp.submittedToast' THEN '승인 제출이 완료되었습니다.'
    WHEN k.key = 'app.mcp.subscribe' THEN '구독'
    WHEN k.key = 'app.mcp.subscribeAll' THEN (SELECT value FROM translations t2 JOIN translation_keys k2 ON k2.id = t2.key_id WHERE k2.key = 'app.skills.subscribeAll' AND t2.language = 'ko')
    WHEN k.key = 'app.mcp.subscribeN' THEN (SELECT value FROM translations t2 JOIN translation_keys k2 ON k2.id = t2.key_id WHERE k2.key = 'app.skills.subscribeN' AND t2.language = 'ko')
    WHEN k.key = 'app.mcp.subscribeToast' THEN '구독에 성공했습니다.'
    WHEN k.key = 'app.mcp.subscribeY' THEN (SELECT value FROM translations t2 JOIN translation_keys k2 ON k2.id = t2.key_id WHERE k2.key = 'app.skills.subscribeY' AND t2.language = 'ko')
    WHEN k.key = 'app.mcp.subscribed' THEN (SELECT value FROM translations t2 JOIN translation_keys k2 ON k2.id = t2.key_id WHERE k2.key = 'app.skills.subscribed' AND t2.language = 'ko')
    WHEN k.key = 'app.mcp.subscribedToast' THEN (SELECT value FROM translations t2 JOIN translation_keys k2 ON k2.id = t2.key_id WHERE k2.key = 'app.skills.subscribedToast' AND t2.language = 'ko')
    WHEN k.key = 'app.mcp.tabComments' THEN (SELECT value FROM translations t2 JOIN translation_keys k2 ON k2.id = t2.key_id WHERE k2.key = 'app.skills.tabComments' AND t2.language = 'ko')
    WHEN k.key = 'app.mcp.tabInfo' THEN (SELECT value FROM translations t2 JOIN translation_keys k2 ON k2.id = t2.key_id WHERE k2.key = 'app.skills.tabInfo' AND t2.language = 'ko')
    WHEN k.key = 'app.mcp.tabSkills' THEN '이 MCP를 사용하는 스킬'
    WHEN k.key = 'app.mcp.tabVersions' THEN (SELECT value FROM translations t2 JOIN translation_keys k2 ON k2.id = t2.key_id WHERE k2.key = 'app.skills.tabVersions' AND t2.language = 'ko')
    WHEN k.key = 'app.mcp.title' THEN 'MCP 상세'
    WHEN k.key = 'app.mcp.underReview' THEN (SELECT value FROM translations t2 JOIN translation_keys k2 ON k2.id = t2.key_id WHERE k2.key = 'app.skills.underReview' AND t2.language = 'ko')
    WHEN k.key = 'app.mcp.unsubscribedToast' THEN (SELECT value FROM translations t2 JOIN translation_keys k2 ON k2.id = t2.key_id WHERE k2.key = 'app.skills.unsubscribedToast' AND t2.language = 'ko')
    WHEN k.key = 'app.mcp.updateFileRequired' THEN (SELECT value FROM translations t2 JOIN translation_keys k2 ON k2.id = t2.key_id WHERE k2.key = 'app.skills.updateFileRequired' AND t2.language = 'ko')
    WHEN k.key = 'app.mcp.updateFilesHint' THEN (SELECT value FROM translations t2 JOIN translation_keys k2 ON k2.id = t2.key_id WHERE k2.key = 'app.skills.updateFilesHint' AND t2.language = 'ko')
    WHEN k.key = 'app.mcp.updateHint' THEN (SELECT value FROM translations t2 JOIN translation_keys k2 ON k2.id = t2.key_id WHERE k2.key = 'app.skills.updateHint' AND t2.language = 'ko')
    WHEN k.key = 'app.mcp.updateInfo' THEN '업데이트 정보'
    WHEN k.key = 'app.mcp.updateInfoPh' THEN '이 버전에서 변경된 내용을 설명하세요...'
    WHEN k.key = 'app.mcp.updateInfoRequired' THEN '새 버전에는 업데이트 정보가 필요합니다.'
    WHEN k.key = 'app.mcp.updateInfoTooLong' THEN '업데이트 정보는 최대 {n}자입니다.'
    WHEN k.key = 'app.mcp.updateSubmitted' THEN (SELECT value FROM translations t2 JOIN translation_keys k2 ON k2.id = t2.key_id WHERE k2.key = 'app.skills.updateSubmitted' AND t2.language = 'ko')
    WHEN k.key = 'app.mcp.updateTitle' THEN (SELECT value FROM translations t2 JOIN translation_keys k2 ON k2.id = t2.key_id WHERE k2.key = 'app.skills.updateTitle' AND t2.language = 'ko')
    WHEN k.key = 'app.mcp.updateVersion' THEN (SELECT value FROM translations t2 JOIN translation_keys k2 ON k2.id = t2.key_id WHERE k2.key = 'app.skills.updateVersion' AND t2.language = 'ko')
    WHEN k.key = 'app.mcp.updated' THEN (SELECT value FROM translations t2 JOIN translation_keys k2 ON k2.id = t2.key_id WHERE k2.key = 'app.skills.updated' AND t2.language = 'ko')
    WHEN k.key = 'app.mcp.updatedToast' THEN (SELECT value FROM translations t2 JOIN translation_keys k2 ON k2.id = t2.key_id WHERE k2.key = 'app.skills.updatedToast' AND t2.language = 'ko')
    WHEN k.key = 'app.mcp.uploadNewFile' THEN '새 파일 업로드'
    WHEN k.key = 'app.mcp.version' THEN (SELECT value FROM translations t2 JOIN translation_keys k2 ON k2.id = t2.key_id WHERE k2.key = 'app.skills.version' AND t2.language = 'ko')
    WHEN k.key = 'app.mcp.versionApproved' THEN (SELECT value FROM translations t2 JOIN translation_keys k2 ON k2.id = t2.key_id WHERE k2.key = 'app.skills.versionApproved' AND t2.language = 'ko')
    WHEN k.key = 'app.mcp.versionCancelled' THEN (SELECT value FROM translations t2 JOIN translation_keys k2 ON k2.id = t2.key_id WHERE k2.key = 'app.skills.versionCancelled' AND t2.language = 'ko')
    WHEN k.key = 'app.mcp.versionConfirmDesc' THEN (SELECT value FROM translations t2 JOIN translation_keys k2 ON k2.id = t2.key_id WHERE k2.key = 'app.skill.versionConfirmDesc' AND t2.language = 'ko')
    WHEN k.key = 'app.mcp.versionConfirmTitle' THEN (SELECT value FROM translations t2 JOIN translation_keys k2 ON k2.id = t2.key_id WHERE k2.key = 'app.skill.versionConfirmTitle' AND t2.language = 'ko')
    WHEN k.key = 'app.mcp.versionCreatedToast' THEN '버전 {n}이(가) 임시 저장 상태로 생성되었습니다 — 준비되면 승인 제출하세요.'
    WHEN k.key = 'app.mcp.versionInfo' THEN '새 버전 {new}이(가) 현재 버전(v{current})에서 생성됩니다.'
    WHEN k.key = 'app.mcp.versionNote' THEN '버전 {n}은(는) 임시 저장 상태로 생성되며 MCP는 임시 저장 상태로 돌아갑니다 (새로 생성한 MCP와 동일). 새 버전이 승인될 때까지 현재 버전은 계속 제공됩니다. 준비되면 승인 제출하세요.'
    WHEN k.key = 'app.mcp.versionNum' THEN '버전 {n}'
    WHEN k.key = 'app.mcp.versionRejected' THEN (SELECT value FROM translations t2 JOIN translation_keys k2 ON k2.id = t2.key_id WHERE k2.key = 'app.skills.versionRejected' AND t2.language = 'ko')
    WHEN k.key = 'app.mcp.viewCount' THEN '조회수: {n}'
    WHEN k.key = 'app.mcp.withdraw' THEN (SELECT value FROM translations t2 JOIN translation_keys k2 ON k2.id = t2.key_id WHERE k2.key = 'app.skills.withdraw' AND t2.language = 'ko')
    WHEN k.key = 'app.mcp.withdrawDesc' THEN (SELECT value FROM translations t2 JOIN translation_keys k2 ON k2.id = t2.key_id WHERE k2.key = 'app.skills.withdrawDesc' AND t2.language = 'ko')
    WHEN k.key = 'app.mcp.withdrawTitle' THEN (SELECT value FROM translations t2 JOIN translation_keys k2 ON k2.id = t2.key_id WHERE k2.key = 'app.skills.withdrawTitle' AND t2.language = 'ko')
    WHEN k.key = 'app.mcp.withdrawn' THEN (SELECT value FROM translations t2 JOIN translation_keys k2 ON k2.id = t2.key_id WHERE k2.key = 'app.skills.withdrawn' AND t2.language = 'ko')
    WHEN k.key = 'app.mcp.withdrawnToast' THEN '임시 저장으로 취하되었습니다.'
    WHEN k.key = 'app.mcp.writeComment' THEN (SELECT value FROM translations t2 JOIN translation_keys k2 ON k2.id = t2.key_id WHERE k2.key = 'app.skills.writeComment' AND t2.language = 'ko')
    WHEN k.key = 'app.mcp.youRate' THEN '{n}개 별 평가'
  END
FROM translation_keys k
WHERE k.key LIKE 'app.mcp.%'
  AND CASE
    WHEN k.key = 'app.mcp.addBtn' THEN EXISTS (SELECT 1 FROM translations t2 JOIN translation_keys k2 ON k2.id = t2.key_id WHERE k2.key = 'app.skills.addBtn' AND t2.language = 'ko')
    WHEN k.key = 'app.mcp.addTitle' THEN EXISTS (SELECT 1 FROM translations t2 JOIN translation_keys k2 ON k2.id = t2.key_id WHERE k2.key = 'app.skills.addTitle' AND t2.language = 'ko')
    WHEN k.key = 'app.mcp.adminBadge' THEN EXISTS (SELECT 1 FROM translations t2 JOIN translation_keys k2 ON k2.id = t2.key_id WHERE k2.key = 'app.skills.adminBadge' AND t2.language = 'ko')
    WHEN k.key = 'app.mcp.adminOnlyCat' THEN EXISTS (SELECT 1 FROM translations t2 JOIN translation_keys k2 ON k2.id = t2.key_id WHERE k2.key = 'app.skills.adminOnlyCat' AND t2.language = 'ko')
    WHEN k.key = 'app.mcp.adminOnlyStage' THEN EXISTS (SELECT 1 FROM translations t2 JOIN translation_keys k2 ON k2.id = t2.key_id WHERE k2.key = 'app.skills.adminOnlyStage' AND t2.language = 'ko')
    WHEN k.key = 'app.mcp.allStatuses' THEN TRUE
    WHEN k.key = 'app.mcp.approve' THEN EXISTS (SELECT 1 FROM translations t2 JOIN translation_keys k2 ON k2.id = t2.key_id WHERE k2.key = 'app.skills.approve' AND t2.language = 'ko')
    WHEN k.key = 'app.mcp.approveVersion' THEN EXISTS (SELECT 1 FROM translations t2 JOIN translation_keys k2 ON k2.id = t2.key_id WHERE k2.key = 'app.skills.approveVersion' AND t2.language = 'ko')
    WHEN k.key = 'app.mcp.approved' THEN TRUE
    WHEN k.key = 'app.mcp.approvedFilesHint' THEN EXISTS (SELECT 1 FROM translations t2 JOIN translation_keys k2 ON k2.id = t2.key_id WHERE k2.key = 'app.skills.approvedFilesHint' AND t2.language = 'ko')
    WHEN k.key = 'app.mcp.approvedMcpTab' THEN TRUE
    WHEN k.key = 'app.mcp.approvedToast' THEN TRUE
    WHEN k.key = 'app.mcp.browseTitle' THEN TRUE
    WHEN k.key = 'app.mcp.cancel' THEN TRUE
    WHEN k.key = 'app.mcp.cancelUpdateBtn' THEN EXISTS (SELECT 1 FROM translations t2 JOIN translation_keys k2 ON k2.id = t2.key_id WHERE k2.key = 'app.skills.cancelUpdateBtn' AND t2.language = 'ko')
    WHEN k.key = 'app.mcp.cancelUpdateDesc' THEN EXISTS (SELECT 1 FROM translations t2 JOIN translation_keys k2 ON k2.id = t2.key_id WHERE k2.key = 'app.skills.cancelUpdateDesc' AND t2.language = 'ko')
    WHEN k.key = 'app.mcp.cancelUpdateTitle' THEN EXISTS (SELECT 1 FROM translations t2 JOIN translation_keys k2 ON k2.id = t2.key_id WHERE k2.key = 'app.skills.cancelUpdateTitle' AND t2.language = 'ko')
    WHEN k.key = 'app.mcp.cancelVersionUpdate' THEN EXISTS (SELECT 1 FROM translations t2 JOIN translation_keys k2 ON k2.id = t2.key_id WHERE k2.key = 'app.skills.cancelVersionUpdate' AND t2.language = 'ko')
    WHEN k.key = 'app.mcp.cannotDeleteItem' THEN EXISTS (SELECT 1 FROM translations t2 JOIN translation_keys k2 ON k2.id = t2.key_id WHERE k2.key = 'app.skills.cannotDeleteItem' AND t2.language = 'ko')
    WHEN k.key = 'app.mcp.cannotDeleteList' THEN EXISTS (SELECT 1 FROM translations t2 JOIN translation_keys k2 ON k2.id = t2.key_id WHERE k2.key = 'app.skills.cannotDeleteList' AND t2.language = 'ko')
    WHEN k.key = 'app.mcp.cannotDeleteTitle' THEN EXISTS (SELECT 1 FROM translations t2 JOIN translation_keys k2 ON k2.id = t2.key_id WHERE k2.key = 'app.skills.cannotDeleteTitle' AND t2.language = 'ko')
    WHEN k.key = 'app.mcp.cannotDeleteTotal' THEN EXISTS (SELECT 1 FROM translations t2 JOIN translation_keys k2 ON k2.id = t2.key_id WHERE k2.key = 'app.skills.cannotDeleteTotal' AND t2.language = 'ko')
    WHEN k.key = 'app.mcp.cardsAria' THEN EXISTS (SELECT 1 FROM translations t2 JOIN translation_keys k2 ON k2.id = t2.key_id WHERE k2.key = 'app.skills.cardsAria' AND t2.language = 'ko')
    WHEN k.key = 'app.mcp.catAdded' THEN EXISTS (SELECT 1 FROM translations t2 JOIN translation_keys k2 ON k2.id = t2.key_id WHERE k2.key = 'app.skills.catAdded' AND t2.language = 'ko')
    WHEN k.key = 'app.mcp.catDeleteDesc' THEN EXISTS (SELECT 1 FROM translations t2 JOIN translation_keys k2 ON k2.id = t2.key_id WHERE k2.key = 'app.skills.catDeleteDesc' AND t2.language = 'ko')
    WHEN k.key = 'app.mcp.catDeleteDescSimple' THEN EXISTS (SELECT 1 FROM translations t2 JOIN translation_keys k2 ON k2.id = t2.key_id WHERE k2.key = 'app.skills.catDeleteDescSimple' AND t2.language = 'ko')
    WHEN k.key = 'app.mcp.catDeleted' THEN EXISTS (SELECT 1 FROM translations t2 JOIN translation_keys k2 ON k2.id = t2.key_id WHERE k2.key = 'app.skills.catDeleted' AND t2.language = 'ko')
    WHEN k.key = 'app.mcp.catUpdated' THEN EXISTS (SELECT 1 FROM translations t2 JOIN translation_keys k2 ON k2.id = t2.key_id WHERE k2.key = 'app.skills.catUpdated' AND t2.language = 'ko')
    WHEN k.key = 'app.mcp.categories' THEN EXISTS (SELECT 1 FROM translations t2 JOIN translation_keys k2 ON k2.id = t2.key_id WHERE k2.key = 'app.skills.categories' AND t2.language = 'ko')
    WHEN k.key = 'app.mcp.categoryName' THEN EXISTS (SELECT 1 FROM translations t2 JOIN translation_keys k2 ON k2.id = t2.key_id WHERE k2.key = 'app.skills.categoryName' AND t2.language = 'ko')
    WHEN k.key = 'app.mcp.changelog' THEN TRUE
    WHEN k.key = 'app.mcp.changelogPh' THEN TRUE
    WHEN k.key = 'app.mcp.changelogRequired' THEN TRUE
    WHEN k.key = 'app.mcp.changelogTooLong' THEN TRUE
    WHEN k.key = 'app.mcp.clearCreator' THEN EXISTS (SELECT 1 FROM translations t2 JOIN translation_keys k2 ON k2.id = t2.key_id WHERE k2.key = 'app.skills.clearCreator' AND t2.language = 'ko')
    WHEN k.key = 'app.mcp.close' THEN TRUE
    WHEN k.key = 'app.mcp.colCreated' THEN TRUE
    WHEN k.key = 'app.mcp.colCreator' THEN EXISTS (SELECT 1 FROM translations t2 JOIN translation_keys k2 ON k2.id = t2.key_id WHERE k2.key = 'app.skills.colCreator' AND t2.language = 'ko')
    WHEN k.key = 'app.mcp.colDescription' THEN TRUE
    WHEN k.key = 'app.mcp.colDownloads' THEN EXISTS (SELECT 1 FROM translations t2 JOIN translation_keys k2 ON k2.id = t2.key_id WHERE k2.key = 'app.skills.colDownloads' AND t2.language = 'ko')
    WHEN k.key = 'app.mcp.colName' THEN EXISTS (SELECT 1 FROM translations t2 JOIN translation_keys k2 ON k2.id = t2.key_id WHERE k2.key = 'app.skills.colName' AND t2.language = 'ko')
    WHEN k.key = 'app.mcp.colRating' THEN EXISTS (SELECT 1 FROM translations t2 JOIN translation_keys k2 ON k2.id = t2.key_id WHERE k2.key = 'app.skills.colRating' AND t2.language = 'ko')
    WHEN k.key = 'app.mcp.colStatus' THEN TRUE
    WHEN k.key = 'app.mcp.colSubscribe' THEN EXISTS (SELECT 1 FROM translations t2 JOIN translation_keys k2 ON k2.id = t2.key_id WHERE k2.key = 'app.skills.colSubscribe' AND t2.language = 'ko')
    WHEN k.key = 'app.mcp.colViews' THEN EXISTS (SELECT 1 FROM translations t2 JOIN translation_keys k2 ON k2.id = t2.key_id WHERE k2.key = 'app.skills.colViews' AND t2.language = 'ko')
    WHEN k.key = 'app.mcp.confirm' THEN TRUE
    WHEN k.key = 'app.mcp.copyLink' THEN EXISTS (SELECT 1 FROM translations t2 JOIN translation_keys k2 ON k2.id = t2.key_id WHERE k2.key = 'app.skill.copyLink' AND t2.language = 'ko')
    WHEN k.key = 'app.mcp.copyLinkCopied' THEN EXISTS (SELECT 1 FROM translations t2 JOIN translation_keys k2 ON k2.id = t2.key_id WHERE k2.key = 'app.skill.copyLinkCopied' AND t2.language = 'ko')
    WHEN k.key = 'app.mcp.copyLinkFailed' THEN EXISTS (SELECT 1 FROM translations t2 JOIN translation_keys k2 ON k2.id = t2.key_id WHERE k2.key = 'app.skill.copyLinkFailed' AND t2.language = 'ko')
    WHEN k.key = 'app.mcp.copyLinkTooltip' THEN EXISTS (SELECT 1 FROM translations t2 JOIN translation_keys k2 ON k2.id = t2.key_id WHERE k2.key = 'app.skill.copyLinkTooltip' AND t2.language = 'ko')
    WHEN k.key = 'app.mcp.createVersion' THEN TRUE
    WHEN k.key = 'app.mcp.createdToast' THEN EXISTS (SELECT 1 FROM translations t2 JOIN translation_keys k2 ON k2.id = t2.key_id WHERE k2.key = 'app.skills.createdToast' AND t2.language = 'ko')
    WHEN k.key = 'app.mcp.createdUpdated' THEN EXISTS (SELECT 1 FROM translations t2 JOIN translation_keys k2 ON k2.id = t2.key_id WHERE k2.key = 'app.skills.createdUpdated' AND t2.language = 'ko')
    WHEN k.key = 'app.mcp.creator' THEN EXISTS (SELECT 1 FROM translations t2 JOIN translation_keys k2 ON k2.id = t2.key_id WHERE k2.key = 'app.skills.creator' AND t2.language = 'ko')
    WHEN k.key = 'app.mcp.current' THEN EXISTS (SELECT 1 FROM translations t2 JOIN translation_keys k2 ON k2.id = t2.key_id WHERE k2.key = 'app.skills.current' AND t2.language = 'ko')
    WHEN k.key = 'app.mcp.currentFile' THEN TRUE
    WHEN k.key = 'app.mcp.deleteBtn' THEN EXISTS (SELECT 1 FROM translations t2 JOIN translation_keys k2 ON k2.id = t2.key_id WHERE k2.key = 'app.skills.deleteBtn' AND t2.language = 'ko')
    WHEN k.key = 'app.mcp.deleteCategoryAria' THEN EXISTS (SELECT 1 FROM translations t2 JOIN translation_keys k2 ON k2.id = t2.key_id WHERE k2.key = 'app.skills.deleteCategoryAria' AND t2.language = 'ko')
    WHEN k.key = 'app.mcp.deleteCommentAria' THEN EXISTS (SELECT 1 FROM translations t2 JOIN translation_keys k2 ON k2.id = t2.key_id WHERE k2.key = 'app.skills.deleteCommentAria' AND t2.language = 'ko')
    WHEN k.key = 'app.mcp.deleteDeniedDesc' THEN TRUE
    WHEN k.key = 'app.mcp.deleteDeniedList' THEN TRUE
    WHEN k.key = 'app.mcp.deleteDeniedTitle' THEN TRUE
    WHEN k.key = 'app.mcp.deleteDeniedTotal' THEN TRUE
    WHEN k.key = 'app.mcp.deleteDesc' THEN EXISTS (SELECT 1 FROM translations t2 JOIN translation_keys k2 ON k2.id = t2.key_id WHERE k2.key = 'app.skills.deleteDesc' AND t2.language = 'ko')
    WHEN k.key = 'app.mcp.deleteStageAria' THEN EXISTS (SELECT 1 FROM translations t2 JOIN translation_keys k2 ON k2.id = t2.key_id WHERE k2.key = 'app.skills.deleteStageAria' AND t2.language = 'ko')
    WHEN k.key = 'app.mcp.deleteTitle' THEN EXISTS (SELECT 1 FROM translations t2 JOIN translation_keys k2 ON k2.id = t2.key_id WHERE k2.key = 'app.skills.deleteTitle' AND t2.language = 'ko')
    WHEN k.key = 'app.mcp.deletedToast' THEN EXISTS (SELECT 1 FROM translations t2 JOIN translation_keys k2 ON k2.id = t2.key_id WHERE k2.key = 'app.skills.deletedToast' AND t2.language = 'ko')
    WHEN k.key = 'app.mcp.deletedToastDetail' THEN EXISTS (SELECT 1 FROM translations t2 JOIN translation_keys k2 ON k2.id = t2.key_id WHERE k2.key = 'app.skills.deletedToastDetail' AND t2.language = 'ko')
    WHEN k.key = 'app.mcp.depAlready' THEN TRUE
    WHEN k.key = 'app.mcp.depBanner' THEN TRUE
    WHEN k.key = 'app.mcp.depDeleted' THEN TRUE
    WHEN k.key = 'app.mcp.depDeletedTip' THEN TRUE
    WHEN k.key = 'app.mcp.depLabel' THEN TRUE
    WHEN k.key = 'app.mcp.depLoading' THEN TRUE
    WHEN k.key = 'app.mcp.depPh' THEN TRUE
    WHEN k.key = 'app.mcp.dependentSkills' THEN FALSE
    WHEN k.key = 'app.mcp.descRequired' THEN TRUE
    WHEN k.key = 'app.mcp.description' THEN TRUE
    WHEN k.key = 'app.mcp.descriptionPh' THEN TRUE
    WHEN k.key = 'app.mcp.detailTitle' THEN EXISTS (SELECT 1 FROM translations t2 JOIN translation_keys k2 ON k2.id = t2.key_id WHERE k2.key = 'app.skills.detailTitle' AND t2.language = 'ko')
    WHEN k.key = 'app.mcp.download' THEN TRUE
    WHEN k.key = 'app.mcp.downloadAll' THEN EXISTS (SELECT 1 FROM translations t2 JOIN translation_keys k2 ON k2.id = t2.key_id WHERE k2.key = 'app.skills.downloadAll' AND t2.language = 'ko')
    WHEN k.key = 'app.mcp.downloadCount' THEN TRUE
    WHEN k.key = 'app.mcp.downloadExcel' THEN EXISTS (SELECT 1 FROM translations t2 JOIN translation_keys k2 ON k2.id = t2.key_id WHERE k2.key = 'app.skills.downloadExcel' AND t2.language = 'ko')
    WHEN k.key = 'app.mcp.draft' THEN TRUE
    WHEN k.key = 'app.mcp.draftHint' THEN EXISTS (SELECT 1 FROM translations t2 JOIN translation_keys k2 ON k2.id = t2.key_id WHERE k2.key = 'app.skills.draftHint' AND t2.language = 'ko')
    WHEN k.key = 'app.mcp.draftNote' THEN TRUE
    WHEN k.key = 'app.mcp.dragDropNewOr' THEN EXISTS (SELECT 1 FROM translations t2 JOIN translation_keys k2 ON k2.id = t2.key_id WHERE k2.key = 'app.skills.dragDropNewOr' AND t2.language = 'ko')
    WHEN k.key = 'app.mcp.editBtn' THEN EXISTS (SELECT 1 FROM translations t2 JOIN translation_keys k2 ON k2.id = t2.key_id WHERE k2.key = 'app.skills.editBtn' AND t2.language = 'ko')
    WHEN k.key = 'app.mcp.editCategoryAria' THEN EXISTS (SELECT 1 FROM translations t2 JOIN translation_keys k2 ON k2.id = t2.key_id WHERE k2.key = 'app.skills.editCategoryAria' AND t2.language = 'ko')
    WHEN k.key = 'app.mcp.editCategoryTitle' THEN EXISTS (SELECT 1 FROM translations t2 JOIN translation_keys k2 ON k2.id = t2.key_id WHERE k2.key = 'app.skills.editCategoryTitle' AND t2.language = 'ko')
    WHEN k.key = 'app.mcp.editNoteApproved' THEN TRUE
    WHEN k.key = 'app.mcp.editNoteStatus' THEN TRUE
    WHEN k.key = 'app.mcp.editStageAria' THEN EXISTS (SELECT 1 FROM translations t2 JOIN translation_keys k2 ON k2.id = t2.key_id WHERE k2.key = 'app.skills.editStageAria' AND t2.language = 'ko')
    WHEN k.key = 'app.mcp.editStageTitle' THEN EXISTS (SELECT 1 FROM translations t2 JOIN translation_keys k2 ON k2.id = t2.key_id WHERE k2.key = 'app.skills.editStageTitle' AND t2.language = 'ko')
    WHEN k.key = 'app.mcp.editTitle' THEN EXISTS (SELECT 1 FROM translations t2 JOIN translation_keys k2 ON k2.id = t2.key_id WHERE k2.key = 'app.skills.editTitle' AND t2.language = 'ko')
    WHEN k.key = 'app.mcp.emptyDesc' THEN EXISTS (SELECT 1 FROM translations t2 JOIN translation_keys k2 ON k2.id = t2.key_id WHERE k2.key = 'app.skills.emptyDesc' AND t2.language = 'ko')
    WHEN k.key = 'app.mcp.emptyTitle' THEN TRUE
    WHEN k.key = 'app.mcp.exportedToast' THEN EXISTS (SELECT 1 FROM translations t2 JOIN translation_keys k2 ON k2.id = t2.key_id WHERE k2.key = 'app.skills.exportedToast' AND t2.language = 'ko')
    WHEN k.key = 'app.mcp.file' THEN TRUE
    WHEN k.key = 'app.mcp.fileDropOr' THEN TRUE
    WHEN k.key = 'app.mcp.fileErrorFormat' THEN TRUE
    WHEN k.key = 'app.mcp.fileErrorSize' THEN TRUE
    WHEN k.key = 'app.mcp.fileKeepHint' THEN TRUE
    WHEN k.key = 'app.mcp.fileRemoved' THEN EXISTS (SELECT 1 FROM translations t2 JOIN translation_keys k2 ON k2.id = t2.key_id WHERE k2.key = 'app.skills.fileRemoved' AND t2.language = 'ko')
    WHEN k.key = 'app.mcp.fileRequired' THEN EXISTS (SELECT 1 FROM translations t2 JOIN translation_keys k2 ON k2.id = t2.key_id WHERE k2.key = 'app.skills.fileRequired' AND t2.language = 'ko')
    WHEN k.key = 'app.mcp.fileRequiredUpload' THEN TRUE
    WHEN k.key = 'app.mcp.fileSupported' THEN TRUE
    WHEN k.key = 'app.mcp.filesToRemove' THEN EXISTS (SELECT 1 FROM translations t2 JOIN translation_keys k2 ON k2.id = t2.key_id WHERE k2.key = 'app.skills.filesToRemove' AND t2.language = 'ko')
    WHEN k.key = 'app.mcp.hashtag' THEN TRUE
    WHEN k.key = 'app.mcp.hashtagExists' THEN TRUE
    WHEN k.key = 'app.mcp.hashtagPh' THEN TRUE
    WHEN k.key = 'app.mcp.hashtagRemoveAria' THEN TRUE
    WHEN k.key = 'app.mcp.lastRejected' THEN EXISTS (SELECT 1 FROM translations t2 JOIN translation_keys k2 ON k2.id = t2.key_id WHERE k2.key = 'app.skills.lastRejected' AND t2.language = 'ko')
    WHEN k.key = 'app.mcp.listAria' THEN EXISTS (SELECT 1 FROM translations t2 JOIN translation_keys k2 ON k2.id = t2.key_id WHERE k2.key = 'app.skills.listAria' AND t2.language = 'ko')
    WHEN k.key = 'app.mcp.loadError' THEN EXISTS (SELECT 1 FROM translations t2 JOIN translation_keys k2 ON k2.id = t2.key_id WHERE k2.key = 'app.skills.loadError' AND t2.language = 'ko')
    WHEN k.key = 'app.mcp.loadErrorDetail' THEN EXISTS (SELECT 1 FROM translations t2 JOIN translation_keys k2 ON k2.id = t2.key_id WHERE k2.key = 'app.skills.loadErrorDetail' AND t2.language = 'ko')
    WHEN k.key = 'app.mcp.manageTaxonomy' THEN EXISTS (SELECT 1 FROM translations t2 JOIN translation_keys k2 ON k2.id = t2.key_id WHERE k2.key = 'app.skills.manageTaxonomy' AND t2.language = 'ko')
    WHEN k.key = 'app.mcp.manageTaxonomyTitle' THEN EXISTS (SELECT 1 FROM translations t2 JOIN translation_keys k2 ON k2.id = t2.key_id WHERE k2.key = 'app.skills.manageTaxonomyTitle' AND t2.language = 'ko')
    WHEN k.key = 'app.mcp.mcpCode' THEN FALSE
    WHEN k.key = 'app.mcp.mcpCount' THEN EXISTS (SELECT 1 FROM translations t2 JOIN translation_keys k2 ON k2.id = t2.key_id WHERE k2.key = 'app.skills.skillCount' AND t2.language = 'ko')
    WHEN k.key = 'app.mcp.mcpDeleted' THEN EXISTS (SELECT 1 FROM translations t2 JOIN translation_keys k2 ON k2.id = t2.key_id WHERE k2.key = 'app.skills.mcpDeleted' AND t2.language = 'ko')
    WHEN k.key = 'app.mcp.mcpDeletedTip' THEN EXISTS (SELECT 1 FROM translations t2 JOIN translation_keys k2 ON k2.id = t2.key_id WHERE k2.key = 'app.skills.mcpDeletedTip' AND t2.language = 'ko')
    WHEN k.key = 'app.mcp.mcpDepsWarnDesc' THEN EXISTS (SELECT 1 FROM translations t2 JOIN translation_keys k2 ON k2.id = t2.key_id WHERE k2.key = 'app.skills.mcpDepsWarnDesc' AND t2.language = 'ko')
    WHEN k.key = 'app.mcp.mcpDepsWarnNote' THEN EXISTS (SELECT 1 FROM translations t2 JOIN translation_keys k2 ON k2.id = t2.key_id WHERE k2.key = 'app.skills.mcpDepsWarnNote' AND t2.language = 'ko')
    WHEN k.key = 'app.mcp.mcpDepsWarnTitle' THEN EXISTS (SELECT 1 FROM translations t2 JOIN translation_keys k2 ON k2.id = t2.key_id WHERE k2.key = 'app.skills.mcpDepsWarnTitle' AND t2.language = 'ko')
    WHEN k.key = 'app.mcp.mcpUnavailableGone' THEN EXISTS (SELECT 1 FROM translations t2 JOIN translation_keys k2 ON k2.id = t2.key_id WHERE k2.key = 'app.skills.mcpUnavailableGone' AND t2.language = 'ko')
    WHEN k.key = 'app.mcp.mcpUnavailableGoneWarn' THEN EXISTS (SELECT 1 FROM translations t2 JOIN translation_keys k2 ON k2.id = t2.key_id WHERE k2.key = 'app.skills.mcpUnavailableGoneWarn' AND t2.language = 'ko')
    WHEN k.key = 'app.mcp.mcpUnavailableNotApproved' THEN EXISTS (SELECT 1 FROM translations t2 JOIN translation_keys k2 ON k2.id = t2.key_id WHERE k2.key = 'app.skills.mcpUnavailableNotApproved' AND t2.language = 'ko')
    WHEN k.key = 'app.mcp.mcpUnavailableNotApprovedWarn' THEN EXISTS (SELECT 1 FROM translations t2 JOIN translation_keys k2 ON k2.id = t2.key_id WHERE k2.key = 'app.skills.mcpUnavailableNotApprovedWarn' AND t2.language = 'ko')
    WHEN k.key = 'app.mcp.myMcpTab' THEN TRUE
    WHEN k.key = 'app.mcp.myMcpsEmpty' THEN TRUE
    WHEN k.key = 'app.mcp.name' THEN TRUE
    WHEN k.key = 'app.mcp.nameEmpty' THEN TRUE
    WHEN k.key = 'app.mcp.nameLocked' THEN EXISTS (SELECT 1 FROM translations t2 JOIN translation_keys k2 ON k2.id = t2.key_id WHERE k2.key = 'app.skills.nameLocked' AND t2.language = 'ko')
    WHEN k.key = 'app.mcp.namePh' THEN TRUE
    WHEN k.key = 'app.mcp.nameTooLong' THEN TRUE
    WHEN k.key = 'app.mcp.newCategoryPh' THEN EXISTS (SELECT 1 FROM translations t2 JOIN translation_keys k2 ON k2.id = t2.key_id WHERE k2.key = 'app.skills.newCategoryPh' AND t2.language = 'ko')
    WHEN k.key = 'app.mcp.newFiles' THEN EXISTS (SELECT 1 FROM translations t2 JOIN translation_keys k2 ON k2.id = t2.key_id WHERE k2.key = 'app.skills.newFiles' AND t2.language = 'ko')
    WHEN k.key = 'app.mcp.newMcpDraftHint' THEN TRUE
    WHEN k.key = 'app.mcp.newStagePh' THEN EXISTS (SELECT 1 FROM translations t2 JOIN translation_keys k2 ON k2.id = t2.key_id WHERE k2.key = 'app.skills.newStagePh' AND t2.language = 'ko')
    WHEN k.key = 'app.mcp.noCategoriesYet' THEN EXISTS (SELECT 1 FROM translations t2 JOIN translation_keys k2 ON k2.id = t2.key_id WHERE k2.key = 'app.skills.noCategoriesYet' AND t2.language = 'ko')
    WHEN k.key = 'app.mcp.noComments' THEN EXISTS (SELECT 1 FROM translations t2 JOIN translation_keys k2 ON k2.id = t2.key_id WHERE k2.key = 'app.skills.noComments' AND t2.language = 'ko')
    WHEN k.key = 'app.mcp.noDeps' THEN TRUE
    WHEN k.key = 'app.mcp.noDescription' THEN EXISTS (SELECT 1 FROM translations t2 JOIN translation_keys k2 ON k2.id = t2.key_id WHERE k2.key = 'app.skills.noDescription' AND t2.language = 'ko')
    WHEN k.key = 'app.mcp.noMatchDesc' THEN EXISTS (SELECT 1 FROM translations t2 JOIN translation_keys k2 ON k2.id = t2.key_id WHERE k2.key = 'app.skills.noMatchDesc' AND t2.language = 'ko')
    WHEN k.key = 'app.mcp.noOptions' THEN EXISTS (SELECT 1 FROM translations t2 JOIN translation_keys k2 ON k2.id = t2.key_id WHERE k2.key = 'app.skills.noOptions' AND t2.language = 'ko')
    WHEN k.key = 'app.mcp.noRating' THEN TRUE
    WHEN k.key = 'app.mcp.noSkills' THEN TRUE
    WHEN k.key = 'app.mcp.noStagesYet' THEN EXISTS (SELECT 1 FROM translations t2 JOIN translation_keys k2 ON k2.id = t2.key_id WHERE k2.key = 'app.skills.noStagesYet' AND t2.language = 'ko')
    WHEN k.key = 'app.mcp.noSuggestions' THEN TRUE
    WHEN k.key = 'app.mcp.noUsersSelected' THEN EXISTS (SELECT 1 FROM translations t2 JOIN translation_keys k2 ON k2.id = t2.key_id WHERE k2.key = 'app.skills.noUsersSelected' AND t2.language = 'ko')
    WHEN k.key = 'app.mcp.noVersions' THEN EXISTS (SELECT 1 FROM translations t2 JOIN translation_keys k2 ON k2.id = t2.key_id WHERE k2.key = 'app.skills.noVersions' AND t2.language = 'ko')
    WHEN k.key = 'app.mcp.notFoundOrNoAccess' THEN EXISTS (SELECT 1 FROM translations t2 JOIN translation_keys k2 ON k2.id = t2.key_id WHERE k2.key = 'app.skill.notFoundOrNoAccess' AND t2.language = 'ko')
    WHEN k.key = 'app.mcp.onlyApprovedComment' THEN EXISTS (SELECT 1 FROM translations t2 JOIN translation_keys k2 ON k2.id = t2.key_id WHERE k2.key = 'app.skills.onlyApprovedComment' AND t2.language = 'ko')
    WHEN k.key = 'app.mcp.onlyApprovedRate' THEN EXISTS (SELECT 1 FROM translations t2 JOIN translation_keys k2 ON k2.id = t2.key_id WHERE k2.key = 'app.skills.onlyApprovedRate' AND t2.language = 'ko')
    WHEN k.key = 'app.mcp.openListAria' THEN TRUE
    WHEN k.key = 'app.mcp.optional' THEN EXISTS (SELECT 1 FROM translations t2 JOIN translation_keys k2 ON k2.id = t2.key_id WHERE k2.key = 'app.skills.optional' AND t2.language = 'ko')
    WHEN k.key = 'app.mcp.pending' THEN TRUE
    WHEN k.key = 'app.mcp.pendingBanner' THEN EXISTS (SELECT 1 FROM translations t2 JOIN translation_keys k2 ON k2.id = t2.key_id WHERE k2.key = 'app.skills.pendingBanner' AND t2.language = 'ko')
    WHEN k.key = 'app.mcp.pendingVersion' THEN EXISTS (SELECT 1 FROM translations t2 JOIN translation_keys k2 ON k2.id = t2.key_id WHERE k2.key = 'app.skills.pendingVersion' AND t2.language = 'ko')
    WHEN k.key = 'app.mcp.pendingVersionBanner' THEN EXISTS (SELECT 1 FROM translations t2 JOIN translation_keys k2 ON k2.id = t2.key_id WHERE k2.key = 'app.skills.pendingVersionBanner' AND t2.language = 'ko')
    WHEN k.key = 'app.mcp.pinBlockedArchived' THEN TRUE
    WHEN k.key = 'app.mcp.pinBlockedDraft' THEN TRUE
    WHEN k.key = 'app.mcp.pinBlockedPending' THEN TRUE
    WHEN k.key = 'app.mcp.pinBlockedRejected' THEN TRUE
    WHEN k.key = 'app.mcp.processStages' THEN EXISTS (SELECT 1 FROM translations t2 JOIN translation_keys k2 ON k2.id = t2.key_id WHERE k2.key = 'app.skills.processStages' AND t2.language = 'ko')
    WHEN k.key = 'app.mcp.rateOnlyApproved' THEN TRUE
    WHEN k.key = 'app.mcp.rateStars' THEN EXISTS (SELECT 1 FROM translations t2 JOIN translation_keys k2 ON k2.id = t2.key_id WHERE k2.key = 'app.skills.rateStars' AND t2.language = 'ko')
    WHEN k.key = 'app.mcp.reject' THEN EXISTS (SELECT 1 FROM translations t2 JOIN translation_keys k2 ON k2.id = t2.key_id WHERE k2.key = 'app.skills.reject' AND t2.language = 'ko')
    WHEN k.key = 'app.mcp.rejectReasonPh' THEN EXISTS (SELECT 1 FROM translations t2 JOIN translation_keys k2 ON k2.id = t2.key_id WHERE k2.key = 'app.skills.rejectReasonPh' AND t2.language = 'ko')
    WHEN k.key = 'app.mcp.rejectReasonRequired' THEN EXISTS (SELECT 1 FROM translations t2 JOIN translation_keys k2 ON k2.id = t2.key_id WHERE k2.key = 'app.skills.rejectReasonRequired' AND t2.language = 'ko')
    WHEN k.key = 'app.mcp.rejectTitle' THEN EXISTS (SELECT 1 FROM translations t2 JOIN translation_keys k2 ON k2.id = t2.key_id WHERE k2.key = 'app.skills.rejectTitle' AND t2.language = 'ko')
    WHEN k.key = 'app.mcp.rejectVersion' THEN EXISTS (SELECT 1 FROM translations t2 JOIN translation_keys k2 ON k2.id = t2.key_id WHERE k2.key = 'app.skills.rejectVersion' AND t2.language = 'ko')
    WHEN k.key = 'app.mcp.rejected' THEN TRUE
    WHEN k.key = 'app.mcp.rejectedToast' THEN TRUE
    WHEN k.key = 'app.mcp.rejectionReason' THEN EXISTS (SELECT 1 FROM translations t2 JOIN translation_keys k2 ON k2.id = t2.key_id WHERE k2.key = 'app.skills.rejectionReason' AND t2.language = 'ko')
    WHEN k.key = 'app.mcp.removeDepAria' THEN TRUE
    WHEN k.key = 'app.mcp.reuseFile' THEN TRUE
    WHEN k.key = 'app.mcp.reviewSubmitted' THEN EXISTS (SELECT 1 FROM translations t2 JOIN translation_keys k2 ON k2.id = t2.key_id WHERE k2.key = 'app.skills.reviewSubmitted' AND t2.language = 'ko')
    WHEN k.key = 'app.mcp.save' THEN TRUE
    WHEN k.key = 'app.mcp.saveDraft' THEN TRUE
    WHEN k.key = 'app.mcp.search' THEN TRUE
    WHEN k.key = 'app.mcp.searchByName' THEN TRUE
    WHEN k.key = 'app.mcp.searchTitle' THEN EXISTS (SELECT 1 FROM translations t2 JOIN translation_keys k2 ON k2.id = t2.key_id WHERE k2.key = 'app.skills.searchTitle' AND t2.language = 'ko')
    WHEN k.key = 'app.mcp.searchUserDesc' THEN EXISTS (SELECT 1 FROM translations t2 JOIN translation_keys k2 ON k2.id = t2.key_id WHERE k2.key = 'app.skills.searchUserDesc' AND t2.language = 'ko')
    WHEN k.key = 'app.mcp.selectCreator' THEN EXISTS (SELECT 1 FROM translations t2 JOIN translation_keys k2 ON k2.id = t2.key_id WHERE k2.key = 'app.skills.selectCreator' AND t2.language = 'ko')
    WHEN k.key = 'app.mcp.selected' THEN EXISTS (SELECT 1 FROM translations t2 JOIN translation_keys k2 ON k2.id = t2.key_id WHERE k2.key = 'app.skills.selected' AND t2.language = 'ko')
    WHEN k.key = 'app.mcp.selectedCount' THEN TRUE
    WHEN k.key = 'app.mcp.send' THEN EXISTS (SELECT 1 FROM translations t2 JOIN translation_keys k2 ON k2.id = t2.key_id WHERE k2.key = 'app.skills.send' AND t2.language = 'ko')
    WHEN k.key = 'app.mcp.serverKey' THEN FALSE
    WHEN k.key = 'app.mcp.showMore' THEN TRUE
    WHEN k.key = 'app.mcp.skillsTab' THEN TRUE
    WHEN k.key = 'app.mcp.stageAdded' THEN EXISTS (SELECT 1 FROM translations t2 JOIN translation_keys k2 ON k2.id = t2.key_id WHERE k2.key = 'app.skills.stageAdded' AND t2.language = 'ko')
    WHEN k.key = 'app.mcp.stageDeleteDesc' THEN EXISTS (SELECT 1 FROM translations t2 JOIN translation_keys k2 ON k2.id = t2.key_id WHERE k2.key = 'app.skills.stageDeleteDesc' AND t2.language = 'ko')
    WHEN k.key = 'app.mcp.stageDeleteDescSimple' THEN EXISTS (SELECT 1 FROM translations t2 JOIN translation_keys k2 ON k2.id = t2.key_id WHERE k2.key = 'app.skills.stageDeleteDescSimple' AND t2.language = 'ko')
    WHEN k.key = 'app.mcp.stageDeleted' THEN EXISTS (SELECT 1 FROM translations t2 JOIN translation_keys k2 ON k2.id = t2.key_id WHERE k2.key = 'app.skills.stageDeleted' AND t2.language = 'ko')
    WHEN k.key = 'app.mcp.stageDescLabel' THEN EXISTS (SELECT 1 FROM translations t2 JOIN translation_keys k2 ON k2.id = t2.key_id WHERE k2.key = 'app.skills.stageDescLabel' AND t2.language = 'ko')
    WHEN k.key = 'app.mcp.stageDescPh' THEN EXISTS (SELECT 1 FROM translations t2 JOIN translation_keys k2 ON k2.id = t2.key_id WHERE k2.key = 'app.skills.stageDescPh' AND t2.language = 'ko')
    WHEN k.key = 'app.mcp.stageName' THEN EXISTS (SELECT 1 FROM translations t2 JOIN translation_keys k2 ON k2.id = t2.key_id WHERE k2.key = 'app.skills.stageName' AND t2.language = 'ko')
    WHEN k.key = 'app.mcp.stageUpdated' THEN EXISTS (SELECT 1 FROM translations t2 JOIN translation_keys k2 ON k2.id = t2.key_id WHERE k2.key = 'app.skills.stageUpdated' AND t2.language = 'ko')
    WHEN k.key = 'app.mcp.status' THEN TRUE
    WHEN k.key = 'app.mcp.submit' THEN TRUE
    WHEN k.key = 'app.mcp.submitForReview' THEN EXISTS (SELECT 1 FROM translations t2 JOIN translation_keys k2 ON k2.id = t2.key_id WHERE k2.key = 'app.skills.submitForReview' AND t2.language = 'ko')
    WHEN k.key = 'app.mcp.submittedToast' THEN TRUE
    WHEN k.key = 'app.mcp.subscribe' THEN TRUE
    WHEN k.key = 'app.mcp.subscribeAll' THEN EXISTS (SELECT 1 FROM translations t2 JOIN translation_keys k2 ON k2.id = t2.key_id WHERE k2.key = 'app.skills.subscribeAll' AND t2.language = 'ko')
    WHEN k.key = 'app.mcp.subscribeN' THEN EXISTS (SELECT 1 FROM translations t2 JOIN translation_keys k2 ON k2.id = t2.key_id WHERE k2.key = 'app.skills.subscribeN' AND t2.language = 'ko')
    WHEN k.key = 'app.mcp.subscribeToast' THEN TRUE
    WHEN k.key = 'app.mcp.subscribeY' THEN EXISTS (SELECT 1 FROM translations t2 JOIN translation_keys k2 ON k2.id = t2.key_id WHERE k2.key = 'app.skills.subscribeY' AND t2.language = 'ko')
    WHEN k.key = 'app.mcp.subscribed' THEN EXISTS (SELECT 1 FROM translations t2 JOIN translation_keys k2 ON k2.id = t2.key_id WHERE k2.key = 'app.skills.subscribed' AND t2.language = 'ko')
    WHEN k.key = 'app.mcp.subscribedToast' THEN EXISTS (SELECT 1 FROM translations t2 JOIN translation_keys k2 ON k2.id = t2.key_id WHERE k2.key = 'app.skills.subscribedToast' AND t2.language = 'ko')
    WHEN k.key = 'app.mcp.tabComments' THEN EXISTS (SELECT 1 FROM translations t2 JOIN translation_keys k2 ON k2.id = t2.key_id WHERE k2.key = 'app.skills.tabComments' AND t2.language = 'ko')
    WHEN k.key = 'app.mcp.tabInfo' THEN EXISTS (SELECT 1 FROM translations t2 JOIN translation_keys k2 ON k2.id = t2.key_id WHERE k2.key = 'app.skills.tabInfo' AND t2.language = 'ko')
    WHEN k.key = 'app.mcp.tabSkills' THEN TRUE
    WHEN k.key = 'app.mcp.tabVersions' THEN EXISTS (SELECT 1 FROM translations t2 JOIN translation_keys k2 ON k2.id = t2.key_id WHERE k2.key = 'app.skills.tabVersions' AND t2.language = 'ko')
    WHEN k.key = 'app.mcp.title' THEN TRUE
    WHEN k.key = 'app.mcp.underReview' THEN EXISTS (SELECT 1 FROM translations t2 JOIN translation_keys k2 ON k2.id = t2.key_id WHERE k2.key = 'app.skills.underReview' AND t2.language = 'ko')
    WHEN k.key = 'app.mcp.unsubscribedToast' THEN EXISTS (SELECT 1 FROM translations t2 JOIN translation_keys k2 ON k2.id = t2.key_id WHERE k2.key = 'app.skills.unsubscribedToast' AND t2.language = 'ko')
    WHEN k.key = 'app.mcp.updateFileRequired' THEN EXISTS (SELECT 1 FROM translations t2 JOIN translation_keys k2 ON k2.id = t2.key_id WHERE k2.key = 'app.skills.updateFileRequired' AND t2.language = 'ko')
    WHEN k.key = 'app.mcp.updateFilesHint' THEN EXISTS (SELECT 1 FROM translations t2 JOIN translation_keys k2 ON k2.id = t2.key_id WHERE k2.key = 'app.skills.updateFilesHint' AND t2.language = 'ko')
    WHEN k.key = 'app.mcp.updateHint' THEN EXISTS (SELECT 1 FROM translations t2 JOIN translation_keys k2 ON k2.id = t2.key_id WHERE k2.key = 'app.skills.updateHint' AND t2.language = 'ko')
    WHEN k.key = 'app.mcp.updateInfo' THEN TRUE
    WHEN k.key = 'app.mcp.updateInfoPh' THEN TRUE
    WHEN k.key = 'app.mcp.updateInfoRequired' THEN TRUE
    WHEN k.key = 'app.mcp.updateInfoTooLong' THEN TRUE
    WHEN k.key = 'app.mcp.updateSubmitted' THEN EXISTS (SELECT 1 FROM translations t2 JOIN translation_keys k2 ON k2.id = t2.key_id WHERE k2.key = 'app.skills.updateSubmitted' AND t2.language = 'ko')
    WHEN k.key = 'app.mcp.updateTitle' THEN EXISTS (SELECT 1 FROM translations t2 JOIN translation_keys k2 ON k2.id = t2.key_id WHERE k2.key = 'app.skills.updateTitle' AND t2.language = 'ko')
    WHEN k.key = 'app.mcp.updateVersion' THEN EXISTS (SELECT 1 FROM translations t2 JOIN translation_keys k2 ON k2.id = t2.key_id WHERE k2.key = 'app.skills.updateVersion' AND t2.language = 'ko')
    WHEN k.key = 'app.mcp.updated' THEN EXISTS (SELECT 1 FROM translations t2 JOIN translation_keys k2 ON k2.id = t2.key_id WHERE k2.key = 'app.skills.updated' AND t2.language = 'ko')
    WHEN k.key = 'app.mcp.updatedToast' THEN EXISTS (SELECT 1 FROM translations t2 JOIN translation_keys k2 ON k2.id = t2.key_id WHERE k2.key = 'app.skills.updatedToast' AND t2.language = 'ko')
    WHEN k.key = 'app.mcp.uploadNewFile' THEN TRUE
    WHEN k.key = 'app.mcp.version' THEN EXISTS (SELECT 1 FROM translations t2 JOIN translation_keys k2 ON k2.id = t2.key_id WHERE k2.key = 'app.skills.version' AND t2.language = 'ko')
    WHEN k.key = 'app.mcp.versionApproved' THEN EXISTS (SELECT 1 FROM translations t2 JOIN translation_keys k2 ON k2.id = t2.key_id WHERE k2.key = 'app.skills.versionApproved' AND t2.language = 'ko')
    WHEN k.key = 'app.mcp.versionCancelled' THEN EXISTS (SELECT 1 FROM translations t2 JOIN translation_keys k2 ON k2.id = t2.key_id WHERE k2.key = 'app.skills.versionCancelled' AND t2.language = 'ko')
    WHEN k.key = 'app.mcp.versionConfirmDesc' THEN EXISTS (SELECT 1 FROM translations t2 JOIN translation_keys k2 ON k2.id = t2.key_id WHERE k2.key = 'app.skill.versionConfirmDesc' AND t2.language = 'ko')
    WHEN k.key = 'app.mcp.versionConfirmTitle' THEN EXISTS (SELECT 1 FROM translations t2 JOIN translation_keys k2 ON k2.id = t2.key_id WHERE k2.key = 'app.skill.versionConfirmTitle' AND t2.language = 'ko')
    WHEN k.key = 'app.mcp.versionCreatedToast' THEN TRUE
    WHEN k.key = 'app.mcp.versionInfo' THEN TRUE
    WHEN k.key = 'app.mcp.versionNote' THEN TRUE
    WHEN k.key = 'app.mcp.versionNum' THEN TRUE
    WHEN k.key = 'app.mcp.versionRejected' THEN EXISTS (SELECT 1 FROM translations t2 JOIN translation_keys k2 ON k2.id = t2.key_id WHERE k2.key = 'app.skills.versionRejected' AND t2.language = 'ko')
    WHEN k.key = 'app.mcp.viewCount' THEN TRUE
    WHEN k.key = 'app.mcp.withdraw' THEN EXISTS (SELECT 1 FROM translations t2 JOIN translation_keys k2 ON k2.id = t2.key_id WHERE k2.key = 'app.skills.withdraw' AND t2.language = 'ko')
    WHEN k.key = 'app.mcp.withdrawDesc' THEN EXISTS (SELECT 1 FROM translations t2 JOIN translation_keys k2 ON k2.id = t2.key_id WHERE k2.key = 'app.skills.withdrawDesc' AND t2.language = 'ko')
    WHEN k.key = 'app.mcp.withdrawTitle' THEN EXISTS (SELECT 1 FROM translations t2 JOIN translation_keys k2 ON k2.id = t2.key_id WHERE k2.key = 'app.skills.withdrawTitle' AND t2.language = 'ko')
    WHEN k.key = 'app.mcp.withdrawn' THEN EXISTS (SELECT 1 FROM translations t2 JOIN translation_keys k2 ON k2.id = t2.key_id WHERE k2.key = 'app.skills.withdrawn' AND t2.language = 'ko')
    WHEN k.key = 'app.mcp.withdrawnToast' THEN TRUE
    WHEN k.key = 'app.mcp.writeComment' THEN EXISTS (SELECT 1 FROM translations t2 JOIN translation_keys k2 ON k2.id = t2.key_id WHERE k2.key = 'app.skills.writeComment' AND t2.language = 'ko')
    WHEN k.key = 'app.mcp.youRate' THEN TRUE
  END
ON CONFLICT (key_id, language) DO NOTHING;
