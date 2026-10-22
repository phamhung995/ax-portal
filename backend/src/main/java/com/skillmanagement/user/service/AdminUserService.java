package com.skillmanagement.user.service;

import com.skillmanagement.user.dto.*;
import com.skillmanagement.skill.dto.*;
import com.skillmanagement.user.dto.*;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.util.List;

/**
 * User Management (admin only, SRS §4-§8).
 */
public interface AdminUserService {

    /** Search with AND between fields, IN/OR within a field (SRS §4). */
    UserSearchResult search(UserSearchRequest request);

    UserDetailResponse getDetail(Long userId);

    /** Create user: Role = NORMAL_USER, Status = ACTIVE, Part/Group = NULL (SRS §5). */
    UserDetailResponse createUser(CreateUserRequest request, String adminLoginId, String clientIp);

    /**
     * Update User Name / Role / Status (SRS §8).
     * Email immutable; self role/status changes rejected; optimistic lock.
     */
    UserDetailResponse updateUser(Long userId, UpdateUserRequest request, String adminLoginId, String clientIp);

    /**
     * Validate a bulk upload file (SRS §6). Returns per-row results in file order.
     * Nothing is persisted.
     */
    BulkUploadResponse validateBulkUpload(MultipartFile file);

    /**
     * Persist the SUCCESS rows (re-validated server-side). Partial success.
     * @param rows the SUCCESS rows from the validation step
     */
    BulkSaveResult saveBulkUpload(List<BulkRowResult> rows, String adminLoginId, String clientIp);

    /**
     * Export the full search result (all pages) to .xlsx (SRS §9).
     * Excludes password and audit information.
     */
    byte[] exportUsers(UserSearchRequest request) throws IOException;

    /**
     * Distinct non-null Part and Group values (SRS §3.2 Organization dropdown).
     */
    List<String> organizationOptions();

    /**
     * User list for filter dropdowns (e.g. Feedback Requester/Handler).
     * Name/email LIKE search, capped at 200 rows, no pagination.
     * @param role optional role-name filter (e.g. "ADMIN_USER"); null = any role
     */
    List<UserListItem> searchUsers(UserSearchRequest request, String role);
}
