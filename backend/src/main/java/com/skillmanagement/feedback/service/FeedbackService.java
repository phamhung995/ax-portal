package com.skillmanagement.feedback.service;

import com.skillmanagement.feedback.dto.*;
import com.skillmanagement.feedback.dto.*;
import com.skillmanagement.user.model.User;

public interface FeedbackService {

    /** Submit feedback (any authenticated user). Notifies all admins. */
    void submit(User user, FeedbackRequest request);

    /** Admin: paged search with optional type/status filters. */
    FeedbackSearchResult search(FeedbackSearchRequest request);

    /** Admin: full detail including screenshot. */
    FeedbackDetailResponse detail(Long id);

    /** The user's own feedback (bell deep-link). 404 for other users' feedback. */
    FeedbackDetailResponse detailForOwner(Long id, User user);

    /** The user's own feedback list (VOC popup). */
    FeedbackSearchResult listMine(User user, int page, int pageSize);

    /** Admin: move to IN_REVIEW / RESOLVED / IGNORED. */
    void changeStatus(Long id, User admin, FeedbackStatusRequest request);

    /** Admin: export the full search result (no paging) to .xlsx. */
    byte[] exportExcel(FeedbackSearchRequest request);
}
