-- Fix app.mcp.* i18n values that still say "skill" (cloned from app.skills.*
-- in V103). The MCP module manages MCPs, not skills — relabel to MCP.
-- Keys about the Skill side (depBanner, mcpDepsWarn*, pinBlocked*, newMcpDraftHint,
-- skillsTab, noSkills, depDeletedTip, mcpDeletedTip) intentionally keep "skill".
--
-- NOTE (Flyway): V104 - checked flyway_schema_history: repo max = 103, dev DB max = 103.

UPDATE translations SET value = 'Add MCP'
WHERE key_id = (SELECT id FROM translation_keys WHERE key = 'app.mcp.addBtn') AND language = 'en';
UPDATE translations SET value = 'Thêm MCP'
WHERE key_id = (SELECT id FROM translation_keys WHERE key = 'app.mcp.addBtn') AND language = 'vi';
UPDATE translations SET value = 'MCP 추가'
WHERE key_id = (SELECT id FROM translation_keys WHERE key = 'app.mcp.addBtn') AND language = 'ko';

UPDATE translations SET value = 'Register new MCP'
WHERE key_id = (SELECT id FROM translation_keys WHERE key = 'app.mcp.addTitle') AND language = 'en';
UPDATE translations SET value = 'Đăng ký MCP mới'
WHERE key_id = (SELECT id FROM translation_keys WHERE key = 'app.mcp.addTitle') AND language = 'vi';
UPDATE translations SET value = '새 MCP 등록'
WHERE key_id = (SELECT id FROM translation_keys WHERE key = 'app.mcp.addTitle') AND language = 'ko';

UPDATE translations SET value = 'MCP Name'
WHERE key_id = (SELECT id FROM translation_keys WHERE key = 'app.mcp.colName') AND language = 'en';
UPDATE translations SET value = 'Tên MCP'
WHERE key_id = (SELECT id FROM translation_keys WHERE key = 'app.mcp.colName') AND language = 'vi';
UPDATE translations SET value = 'MCP 이름'
WHERE key_id = (SELECT id FROM translation_keys WHERE key = 'app.mcp.colName') AND language = 'ko';

UPDATE translations SET value = 'MCP Detail'
WHERE key_id = (SELECT id FROM translation_keys WHERE key = 'app.mcp.detailTitle') AND language = 'en';
UPDATE translations SET value = 'Chi tiết MCP'
WHERE key_id = (SELECT id FROM translation_keys WHERE key = 'app.mcp.detailTitle') AND language = 'vi';
UPDATE translations SET value = 'MCP 상세'
WHERE key_id = (SELECT id FROM translation_keys WHERE key = 'app.mcp.detailTitle') AND language = 'ko';

UPDATE translations SET value = 'Delete MCPs'
WHERE key_id = (SELECT id FROM translation_keys WHERE key = 'app.mcp.deleteTitle') AND language = 'en';
UPDATE translations SET value = 'Xóa MCP'
WHERE key_id = (SELECT id FROM translation_keys WHERE key = 'app.mcp.deleteTitle') AND language = 'vi';
UPDATE translations SET value = 'MCP 삭제'
WHERE key_id = (SELECT id FROM translation_keys WHERE key = 'app.mcp.deleteTitle') AND language = 'ko';

UPDATE translations SET value = 'Are you sure you want to delete the selected {n} MCP(s)?'
WHERE key_id = (SELECT id FROM translation_keys WHERE key = 'app.mcp.deleteDesc') AND language = 'en';
UPDATE translations SET value = 'Bạn có chắc muốn xóa {n} MCP đã chọn?'
WHERE key_id = (SELECT id FROM translation_keys WHERE key = 'app.mcp.deleteDesc') AND language = 'vi';
UPDATE translations SET value = '선택한 {n}개 MCP를 삭제할까요?'
WHERE key_id = (SELECT id FROM translation_keys WHERE key = 'app.mcp.deleteDesc') AND language = 'ko';

UPDATE translations SET value = 'Deleted {n} MCP(s).'
WHERE key_id = (SELECT id FROM translation_keys WHERE key = 'app.mcp.deletedToast') AND language = 'en';
UPDATE translations SET value = 'Đã xóa {n} MCP.'
WHERE key_id = (SELECT id FROM translation_keys WHERE key = 'app.mcp.deletedToast') AND language = 'vi';
UPDATE translations SET value = '{n}개 MCP를 삭제했습니다.'
WHERE key_id = (SELECT id FROM translation_keys WHERE key = 'app.mcp.deletedToast') AND language = 'ko';

UPDATE translations SET value = 'MCP has been deleted.'
WHERE key_id = (SELECT id FROM translation_keys WHERE key = 'app.mcp.deletedToastDetail') AND language = 'en';
UPDATE translations SET value = 'MCP đã bị xóa.'
WHERE key_id = (SELECT id FROM translation_keys WHERE key = 'app.mcp.deletedToastDetail') AND language = 'vi';
UPDATE translations SET value = 'MCP가 삭제되었습니다.'
WHERE key_id = (SELECT id FROM translation_keys WHERE key = 'app.mcp.deletedToastDetail') AND language = 'ko';

UPDATE translations SET value = 'MCP created successfully.'
WHERE key_id = (SELECT id FROM translation_keys WHERE key = 'app.mcp.createdToast') AND language = 'en';
UPDATE translations SET value = 'Đã tạo MCP.'
WHERE key_id = (SELECT id FROM translation_keys WHERE key = 'app.mcp.createdToast') AND language = 'vi';
UPDATE translations SET value = 'MCP가 생성되었습니다.'
WHERE key_id = (SELECT id FROM translation_keys WHERE key = 'app.mcp.createdToast') AND language = 'ko';

UPDATE translations SET value = 'MCP list exported successfully.'
WHERE key_id = (SELECT id FROM translation_keys WHERE key = 'app.mcp.exportedToast') AND language = 'en';
UPDATE translations SET value = 'Đã xuất danh sách MCP.'
WHERE key_id = (SELECT id FROM translation_keys WHERE key = 'app.mcp.exportedToast') AND language = 'vi';
UPDATE translations SET value = 'MCP 목록을 내보냈습니다.'
WHERE key_id = (SELECT id FROM translation_keys WHERE key = 'app.mcp.exportedToast') AND language = 'ko';

UPDATE translations SET value = 'MCP list'
WHERE key_id = (SELECT id FROM translation_keys WHERE key = 'app.mcp.listAria') AND language = 'en';
UPDATE translations SET value = 'Danh sách MCP'
WHERE key_id = (SELECT id FROM translation_keys WHERE key = 'app.mcp.listAria') AND language = 'vi';
UPDATE translations SET value = 'MCP 목록'
WHERE key_id = (SELECT id FROM translation_keys WHERE key = 'app.mcp.listAria') AND language = 'ko';

UPDATE translations SET value = 'MCP cards'
WHERE key_id = (SELECT id FROM translation_keys WHERE key = 'app.mcp.cardsAria') AND language = 'en';
UPDATE translations SET value = 'Thẻ MCP'
WHERE key_id = (SELECT id FROM translation_keys WHERE key = 'app.mcp.cardsAria') AND language = 'vi';
UPDATE translations SET value = 'MCP 카드'
WHERE key_id = (SELECT id FROM translation_keys WHERE key = 'app.mcp.cardsAria') AND language = 'ko';

UPDATE translations SET value = 'Unable to load MCP list.'
WHERE key_id = (SELECT id FROM translation_keys WHERE key = 'app.mcp.loadError') AND language = 'en';
UPDATE translations SET value = 'Không tải được danh sách MCP.'
WHERE key_id = (SELECT id FROM translation_keys WHERE key = 'app.mcp.loadError') AND language = 'vi';
UPDATE translations SET value = 'MCP 목록을 불러오지 못했습니다.'
WHERE key_id = (SELECT id FROM translation_keys WHERE key = 'app.mcp.loadError') AND language = 'ko';

UPDATE translations SET value = 'Unable to load MCP information.'
WHERE key_id = (SELECT id FROM translation_keys WHERE key = 'app.mcp.loadErrorDetail') AND language = 'en';
UPDATE translations SET value = 'Không tải được thông tin MCP.'
WHERE key_id = (SELECT id FROM translation_keys WHERE key = 'app.mcp.loadErrorDetail') AND language = 'vi';
UPDATE translations SET value = 'MCP 정보를 불러오지 못했습니다.'
WHERE key_id = (SELECT id FROM translation_keys WHERE key = 'app.mcp.loadErrorDetail') AND language = 'ko';

UPDATE translations SET value = 'Enter search criteria and click Search to load MCPs.'
WHERE key_id = (SELECT id FROM translation_keys WHERE key = 'app.mcp.emptyDesc') AND language = 'en';
UPDATE translations SET value = 'Nhập tiêu chí tìm kiếm và bấm Tìm kiếm để tải MCP.'
WHERE key_id = (SELECT id FROM translation_keys WHERE key = 'app.mcp.emptyDesc') AND language = 'vi';
UPDATE translations SET value = '검색 조건을 입력하고 검색을 클릭하여 MCP를 불러오세요.'
WHERE key_id = (SELECT id FROM translation_keys WHERE key = 'app.mcp.emptyDesc') AND language = 'ko';

UPDATE translations SET value = 'No MCPs match the current filters.'
WHERE key_id = (SELECT id FROM translation_keys WHERE key = 'app.mcp.noMatchDesc') AND language = 'en';
UPDATE translations SET value = 'Không có MCP nào khớp bộ lọc.'
WHERE key_id = (SELECT id FROM translation_keys WHERE key = 'app.mcp.noMatchDesc') AND language = 'vi';
UPDATE translations SET value = '현재 필터에 맞는 MCP가 없습니다.'
WHERE key_id = (SELECT id FROM translation_keys WHERE key = 'app.mcp.noMatchDesc') AND language = 'ko';

UPDATE translations SET value = 'MCP not found or you do not have permission to view it.'
WHERE key_id = (SELECT id FROM translation_keys WHERE key = 'app.mcp.notFoundOrNoAccess') AND language = 'en';
UPDATE translations SET value = 'Không tìm thấy MCP hoặc bạn không có quyền xem.'
WHERE key_id = (SELECT id FROM translation_keys WHERE key = 'app.mcp.notFoundOrNoAccess') AND language = 'vi';
UPDATE translations SET value = 'MCP를 찾을 수 없거나 볼 권한이 없습니다.'
WHERE key_id = (SELECT id FROM translation_keys WHERE key = 'app.mcp.notFoundOrNoAccess') AND language = 'ko';

UPDATE translations SET value = 'Only approved MCPs can be commented.'
WHERE key_id = (SELECT id FROM translation_keys WHERE key = 'app.mcp.onlyApprovedComment') AND language = 'en';
UPDATE translations SET value = 'Chỉ MCP đã duyệt mới được bình luận.'
WHERE key_id = (SELECT id FROM translation_keys WHERE key = 'app.mcp.onlyApprovedComment') AND language = 'vi';
UPDATE translations SET value = '승인된 MCP만 댓글을 달 수 있습니다.'
WHERE key_id = (SELECT id FROM translation_keys WHERE key = 'app.mcp.onlyApprovedComment') AND language = 'ko';

UPDATE translations SET value = 'Only approved MCPs can be rated.'
WHERE key_id = (SELECT id FROM translation_keys WHERE key = 'app.mcp.onlyApprovedRate') AND language = 'en';
UPDATE translations SET value = 'Chỉ MCP đã duyệt mới được đánh giá.'
WHERE key_id = (SELECT id FROM translation_keys WHERE key = 'app.mcp.onlyApprovedRate') AND language = 'vi';
UPDATE translations SET value = '승인된 MCP만 평가할 수 있습니다.'
WHERE key_id = (SELECT id FROM translation_keys WHERE key = 'app.mcp.onlyApprovedRate') AND language = 'ko';

UPDATE translations SET value = 'This MCP is pending approval.'
WHERE key_id = (SELECT id FROM translation_keys WHERE key = 'app.mcp.pendingBanner') AND language = 'en';
UPDATE translations SET value = 'MCP này đang chờ duyệt.'
WHERE key_id = (SELECT id FROM translation_keys WHERE key = 'app.mcp.pendingBanner') AND language = 'vi';
UPDATE translations SET value = '이 MCP는 승인 대기 중입니다.'
WHERE key_id = (SELECT id FROM translation_keys WHERE key = 'app.mcp.pendingBanner') AND language = 'ko';

UPDATE translations SET value = 'MCP updated successfully.'
WHERE key_id = (SELECT id FROM translation_keys WHERE key = 'app.mcp.updatedToast') AND language = 'en';
UPDATE translations SET value = 'Đã cập nhật MCP.'
WHERE key_id = (SELECT id FROM translation_keys WHERE key = 'app.mcp.updatedToast') AND language = 'vi';
UPDATE translations SET value = 'MCP가 업데이트되었습니다.'
WHERE key_id = (SELECT id FROM translation_keys WHERE key = 'app.mcp.updatedToast') AND language = 'ko';

UPDATE translations SET value = 'Edit MCP'
WHERE key_id = (SELECT id FROM translation_keys WHERE key = 'app.mcp.editTitle') AND language = 'en';
UPDATE translations SET value = 'Sửa MCP'
WHERE key_id = (SELECT id FROM translation_keys WHERE key = 'app.mcp.editTitle') AND language = 'vi';
UPDATE translations SET value = 'MCP 수정'
WHERE key_id = (SELECT id FROM translation_keys WHERE key = 'app.mcp.editTitle') AND language = 'ko';

UPDATE translations SET value = 'MCP withdrawn successfully.'
WHERE key_id = (SELECT id FROM translation_keys WHERE key = 'app.mcp.withdrawn') AND language = 'en';
UPDATE translations SET value = 'Đã rút MCP.'
WHERE key_id = (SELECT id FROM translation_keys WHERE key = 'app.mcp.withdrawn') AND language = 'vi';
UPDATE translations SET value = 'MCP가 철회되었습니다.'
WHERE key_id = (SELECT id FROM translation_keys WHERE key = 'app.mcp.withdrawn') AND language = 'ko';

UPDATE translations SET value = 'Withdraw MCP'
WHERE key_id = (SELECT id FROM translation_keys WHERE key = 'app.mcp.withdrawTitle') AND language = 'en';
UPDATE translations SET value = 'Rút MCP'
WHERE key_id = (SELECT id FROM translation_keys WHERE key = 'app.mcp.withdrawTitle') AND language = 'vi';
UPDATE translations SET value = 'MCP 철회'
WHERE key_id = (SELECT id FROM translation_keys WHERE key = 'app.mcp.withdrawTitle') AND language = 'ko';

UPDATE translations SET value = 'Are you sure you want to withdraw this MCP? The MCP will return to Draft status.'
WHERE key_id = (SELECT id FROM translation_keys WHERE key = 'app.mcp.withdrawDesc') AND language = 'en';
UPDATE translations SET value = 'Bạn có chắc muốn rút MCP này? MCP sẽ quay về trạng thái Bản nháp.'
WHERE key_id = (SELECT id FROM translation_keys WHERE key = 'app.mcp.withdrawDesc') AND language = 'vi';
UPDATE translations SET value = '해당 MCP를 정말 철회하고 싶습니까? 철회되면 초안 상태로 변경이 됩니다.'
WHERE key_id = (SELECT id FROM translation_keys WHERE key = 'app.mcp.withdrawDesc') AND language = 'ko';

UPDATE translations SET value = 'The MCP is created in Draft status. You can edit it and submit for review when ready.'
WHERE key_id = (SELECT id FROM translation_keys WHERE key = 'app.mcp.draftHint') AND language = 'en';
UPDATE translations SET value = 'MCP được tạo ở trạng thái Bản nháp. Bạn có thể sửa và gửi xem xét khi sẵn sàng.'
WHERE key_id = (SELECT id FROM translation_keys WHERE key = 'app.mcp.draftHint') AND language = 'vi';
UPDATE translations SET value = 'MCP는 초안 상태로 생성됩니다. 작성된 내용을 확인하고 상신하시길 바랍니다.'
WHERE key_id = (SELECT id FROM translation_keys WHERE key = 'app.mcp.draftHint') AND language = 'ko';

UPDATE translations SET value = 'The MCP name cannot be changed because the MCP is approved.'
WHERE key_id = (SELECT id FROM translation_keys WHERE key = 'app.mcp.nameLocked') AND language = 'en';
UPDATE translations SET value = 'Tên MCP không thể thay đổi vì MCP đã được duyệt.'
WHERE key_id = (SELECT id FROM translation_keys WHERE key = 'app.mcp.nameLocked') AND language = 'vi';
UPDATE translations SET value = 'MCP가 승인되었으므로 이름을 변경할 수 없습니다.'
WHERE key_id = (SELECT id FROM translation_keys WHERE key = 'app.mcp.nameLocked') AND language = 'ko';

UPDATE translations SET value = '{n} MCP(s)'
WHERE key_id = (SELECT id FROM translation_keys WHERE key = 'app.mcp.mcpCount') AND language = 'en';
UPDATE translations SET value = '{n} MCP'
WHERE key_id = (SELECT id FROM translation_keys WHERE key = 'app.mcp.mcpCount') AND language = 'vi';
UPDATE translations SET value = '{n}개 MCP'
WHERE key_id = (SELECT id FROM translation_keys WHERE key = 'app.mcp.mcpCount') AND language = 'ko';

UPDATE translations SET value = '🔒 Admin only. Deleting a category removes it from all MCPs using it.'
WHERE key_id = (SELECT id FROM translation_keys WHERE key = 'app.mcp.adminOnlyCat') AND language = 'en';
UPDATE translations SET value = '🔒 Chỉ admin. Xóa danh mục sẽ gỡ khỏi tất cả MCP đang dùng.'
WHERE key_id = (SELECT id FROM translation_keys WHERE key = 'app.mcp.adminOnlyCat') AND language = 'vi';
UPDATE translations SET value = '🔒 관리자 전용. 카테고리를 삭제하면 사용하는 모든 MCP에서 제거됩니다.'
WHERE key_id = (SELECT id FROM translation_keys WHERE key = 'app.mcp.adminOnlyCat') AND language = 'ko';

UPDATE translations SET value = 'Delete category "{name}"? It will be removed from {n} MCP(s).'
WHERE key_id = (SELECT id FROM translation_keys WHERE key = 'app.mcp.catDeleteDesc') AND language = 'en';
UPDATE translations SET value = 'Xóa danh mục "{name}"? Nó sẽ bị gỡ khỏi {n} MCP.'
WHERE key_id = (SELECT id FROM translation_keys WHERE key = 'app.mcp.catDeleteDesc') AND language = 'vi';
UPDATE translations SET value = '카테고리 "{name}"을(를) 삭제할까요? {n}개 MCP에서 제거됩니다.'
WHERE key_id = (SELECT id FROM translation_keys WHERE key = 'app.mcp.catDeleteDesc') AND language = 'ko';

UPDATE translations SET value = 'Delete stage "{name}"? It will be removed from {n} MCP(s).'
WHERE key_id = (SELECT id FROM translation_keys WHERE key = 'app.mcp.stageDeleteDesc') AND language = 'en';
UPDATE translations SET value = 'Xóa giai đoạn "{name}"? Nó sẽ bị gỡ khỏi {n} MCP.'
WHERE key_id = (SELECT id FROM translation_keys WHERE key = 'app.mcp.stageDeleteDesc') AND language = 'vi';
UPDATE translations SET value = '단계 "{name}"을(를) 삭제할까요? {n}개 MCP에서 제거됩니다.'
WHERE key_id = (SELECT id FROM translation_keys WHERE key = 'app.mcp.stageDeleteDesc') AND language = 'ko';

UPDATE translations SET value = 'Files of an approved MCP can only be managed through Update Version.'
WHERE key_id = (SELECT id FROM translation_keys WHERE key = 'app.mcp.approvedFilesHint') AND language = 'en';
UPDATE translations SET value = 'File của MCP đã duyệt chỉ có thể quản lý qua Cập nhật phiên bản.'
WHERE key_id = (SELECT id FROM translation_keys WHERE key = 'app.mcp.approvedFilesHint') AND language = 'vi';
UPDATE translations SET value = '승인된 MCP의 파일은 버전 업데이트에서만 관리할 수 있습니다.'
WHERE key_id = (SELECT id FROM translation_keys WHERE key = 'app.mcp.approvedFilesHint') AND language = 'ko';

UPDATE translations SET value = 'MCPs you cannot delete:'
WHERE key_id = (SELECT id FROM translation_keys WHERE key = 'app.mcp.cannotDeleteList') AND language = 'en';
UPDATE translations SET value = 'MCP không thể xóa:'
WHERE key_id = (SELECT id FROM translation_keys WHERE key = 'app.mcp.cannotDeleteList') AND language = 'vi';
UPDATE translations SET value = '삭제할 수 없는 MCP:'
WHERE key_id = (SELECT id FROM translation_keys WHERE key = 'app.mcp.cannotDeleteList') AND language = 'ko';

UPDATE translations SET value = 'Cannot delete all selected MCPs'
WHERE key_id = (SELECT id FROM translation_keys WHERE key = 'app.mcp.cannotDeleteTitle') AND language = 'en';
UPDATE translations SET value = 'Không thể xóa tất cả MCP đã chọn'
WHERE key_id = (SELECT id FROM translation_keys WHERE key = 'app.mcp.cannotDeleteTitle') AND language = 'vi';
UPDATE translations SET value = '선택한 모든 MCP를 삭제할 수 없습니다'
WHERE key_id = (SELECT id FROM translation_keys WHERE key = 'app.mcp.cannotDeleteTitle') AND language = 'ko';

UPDATE translations SET value = 'Total: {n} MCP(s) selected, {m} cannot be deleted.'
WHERE key_id = (SELECT id FROM translation_keys WHERE key = 'app.mcp.cannotDeleteTotal') AND language = 'en';
UPDATE translations SET value = 'Tổng: {n} MCP đã chọn, {m} không thể xóa.'
WHERE key_id = (SELECT id FROM translation_keys WHERE key = 'app.mcp.cannotDeleteTotal') AND language = 'vi';

UPDATE translations SET value = 'Copy link to this MCP'
WHERE key_id = (SELECT id FROM translation_keys WHERE key = 'app.mcp.copyLinkTooltip') AND language = 'en';
UPDATE translations SET value = 'Sao chép link đến MCP này'
WHERE key_id = (SELECT id FROM translation_keys WHERE key = 'app.mcp.copyLinkTooltip') AND language = 'vi';
UPDATE translations SET value = '이 MCP의 링크를 복사'
WHERE key_id = (SELECT id FROM translation_keys WHERE key = 'app.mcp.copyLinkTooltip') AND language = 'ko';

UPDATE translations SET value = 'This version update requires admin approval. The current version stays available to everyone until it is approved, and the MCP will move to PENDING_APPROVAL. Do you want to continue?'
WHERE key_id = (SELECT id FROM translation_keys WHERE key = 'app.mcp.versionConfirmDesc') AND language = 'en';
UPDATE translations SET value = 'Cập nhật phiên bản này cần admin duyệt. Phiên bản hiện tại vẫn dùng được cho mọi người cho đến khi duyệt và MCP sẽ chuyển sang PENDING_APPROVAL. Bạn có muốn tiếp tục?'
WHERE key_id = (SELECT id FROM translation_keys WHERE key = 'app.mcp.versionConfirmDesc') AND language = 'vi';
UPDATE translations SET value = '이 버전 업데이트에는 관리자 승인이 필요합니다. 승인될 때까지 현재 버전이 계속 사용 가능하며 MCP는 승인 대기 상태로 전환됩니다. 계속하시겠습니까?'
WHERE key_id = (SELECT id FROM translation_keys WHERE key = 'app.mcp.versionConfirmDesc') AND language = 'ko';
