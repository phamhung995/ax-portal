package com.furelise.skillmanagement.feedback.service.impl;

import com.furelise.skillmanagement.common.RateLimiter;
import com.furelise.skillmanagement.exception.BusinessErrorCode;
import com.furelise.skillmanagement.exception.BusinessException;
import com.furelise.skillmanagement.feedback.dto.*;
import com.furelise.skillmanagement.feedback.model.Feedback;
import com.furelise.skillmanagement.feedback.repository.FeedbackRepository;
import com.furelise.skillmanagement.model.Role;
import com.furelise.skillmanagement.notification.service.EventNotificationService;
import com.furelise.skillmanagement.feedback.service.FeedbackService;
import com.furelise.skillmanagement.repository.RoleRepository;
import com.furelise.skillmanagement.skill.service.ExcelExportStyle;
import com.furelise.skillmanagement.user.model.User;
import com.furelise.skillmanagement.user.model.UserSettings;
import com.furelise.skillmanagement.user.repository.UserRepository;
import com.furelise.skillmanagement.user.repository.UserSettingsRepository;
import jakarta.persistence.criteria.Expression;
import jakarta.persistence.criteria.Predicate;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.poi.ss.usermodel.CellStyle;
import org.springframework.beans.factory.annotation.Value;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.ss.usermodel.Workbook;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * Feedback (VOC) implementation.
 * Any authenticated user can submit; admins manage the queue.
 * New feedback notifies all admins (email + in-app, honoring their
 * notification settings, same pattern as skill workflow notifications).
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class FeedbackServiceImpl implements FeedbackService {

    private static final int MAX_RESOLUTION_NOTE_LENGTH = 500;
    private static final Set<String> VALID_TYPES = Set.of(
            Feedback.TYPE_BUG, Feedback.TYPE_SUGGESTION, Feedback.TYPE_QUESTION);
    private static final Set<String> VALID_STATUSES = Set.of(
            Feedback.STATUS_IN_REVIEW, Feedback.STATUS_RESOLVED, Feedback.STATUS_IGNORED);
    private static final int FEEDBACK_MAX_PER_MINUTE = 10;
    private static final int FEEDBACK_WINDOW_SECONDS = 60;

    private final FeedbackRepository feedbackRepository;
    private final UserRepository userRepository;
    private final UserSettingsRepository userSettingsRepository;
    private final RoleRepository roleRepository;
    private final EventNotificationService eventNotificationService;
    private final RateLimiter rateLimiter;
    private final com.furelise.skillmanagement.audit.service.AuditService auditService;

    /** Public frontend base URL — appended to VOC links in notification emails. */
    @Value("${app.base-url:}")
    private String appBaseUrl;

    @Override
    @Transactional
    public void submit(User user, FeedbackRequest request) {
        if (!rateLimiter.allow("feedback:" + user.getId(),
                FEEDBACK_MAX_PER_MINUTE, FEEDBACK_WINDOW_SECONDS)) {
            throw new BusinessException(BusinessErrorCode.FEEDBACK_RATE_LIMITED,
                    "Too many requests. Please try again later.");
        }
        String type = request.type() == null ? "" : request.type().trim();
        if (!VALID_TYPES.contains(type)) {
            throw new BusinessException(BusinessErrorCode.FEEDBACK_INVALID_TYPE,
                    "Feedback type must be BUG, SUGGESTION or QUESTION.");
        }
        String content = request.content() == null ? "" : request.content().trim();
        if (content.isEmpty()) {
            throw new BusinessException(BusinessErrorCode.FEEDBACK_CONTENT_EMPTY,
                    "Feedback content cannot be empty.");
        }
        String screenshot = request.screenshot();
        if (screenshot != null && !screenshot.startsWith("data:image/")) {
            screenshot = null; // only base64 data URIs are accepted
        }

        Feedback saved = feedbackRepository.save(Feedback.builder()
                .userId(user.getId())
                .type(type)
                .content(content)
                .screenshot(screenshot)
                .build());
        auditService.log(user, "CREATE", "FEEDBACK", String.valueOf(saved.getId()),
                "type=" + type);
        notifyAdmins(saved, user);
    }

    @Override
    @Transactional(readOnly = true)
    public FeedbackSearchResult search(FeedbackSearchRequest request) {
        int p = Math.max(1, request.page());
        int sz = request.pageSize() < 1 ? 20 : Math.min(100, request.pageSize());
        Pageable pageable = PageRequest.of(p - 1, sz,
                Sort.by(Sort.Direction.DESC, "createdAt").and(Sort.by(Sort.Direction.DESC, "id")));
        Page<Feedback> result = findFiltered(request, pageable);

        Map<Long, User> users = loadUsers(result.getContent());
        List<FeedbackItem> items = result.getContent().stream().map(f -> new FeedbackItem(
                f.getId(), f.getUserId(),
                users.get(f.getUserId()) != null ? users.get(f.getUserId()).getName() : null,
                users.get(f.getUserId()) != null ? users.get(f.getUserId()).getEmail() : null,
                f.getType(), f.getContent(), f.getStatus(),
                f.getCreatedAt(), f.getScreenshot() != null,
                resolvedUserName(f)))
                .toList();
        return new FeedbackSearchResult(result.getTotalElements(), p, sz, items);
    }

    @Override
    @Transactional(readOnly = true)
    public FeedbackDetailResponse detail(Long id) {
        Feedback feedback = feedbackRepository.findById(id)
                .orElseThrow(() -> new BusinessException(BusinessErrorCode.FEEDBACK_NOT_FOUND,
                        "Feedback not found."));
        User creator = userRepository.findById(feedback.getUserId()).orElse(null);
        String resolvedByName = null;
        if (feedback.getResolvedBy() != null) {
            resolvedByName = userRepository.findById(feedback.getResolvedBy())
                    .map(User::getName).orElse(null);
        }
        return new FeedbackDetailResponse(
                feedback.getId(),
                feedback.getUserId(),
                creator != null ? creator.getName() : null,
                creator != null ? creator.getEmail() : null,
                feedback.getType(), feedback.getContent(), feedback.getScreenshot(),
                feedback.getStatus(), feedback.getResolutionNote(),
                resolvedByName, feedback.getResolvedAt(), feedback.getCreatedAt());
    }

    @Override
    @Transactional(readOnly = true)
    public FeedbackDetailResponse detailForOwner(Long id, User user) {
        Feedback feedback = feedbackRepository.findById(id)
                .orElseThrow(() -> new BusinessException(BusinessErrorCode.FEEDBACK_NOT_FOUND,
                        "Feedback not found."));
        boolean isAdmin = user.getRole() != null && "ADMIN_USER".equals(user.getRole().getName());
        if (!isAdmin && !feedback.getUserId().equals(user.getId())) {
            // 404 (not 403): do not leak the existence of other users' feedback
            throw new BusinessException(BusinessErrorCode.FEEDBACK_NOT_FOUND,
                    "Feedback not found.");
        }
        return detail(id);
    }

    @Override
    @Transactional(readOnly = true)
    public FeedbackSearchResult listMine(User user, int page, int pageSize) {
        int p = Math.max(1, page);
        int sz = pageSize < 1 ? 20 : Math.min(100, pageSize);
        Pageable pageable = PageRequest.of(p - 1, sz,
                Sort.by(Sort.Direction.DESC, "createdAt").and(Sort.by(Sort.Direction.DESC, "id")));
        Page<Feedback> result = feedbackRepository.findByUserId(user.getId(), pageable);
        List<FeedbackItem> items = result.getContent().stream().map(f -> new FeedbackItem(
                f.getId(), f.getUserId(),
                user.getName(), user.getEmail(),
                f.getType(), f.getContent(), f.getStatus(),
                f.getCreatedAt(), f.getScreenshot() != null,
                resolvedUserName(f)))
                .toList();
        return new FeedbackSearchResult(result.getTotalElements(), p, sz, items);
    }

    // ── Export Excel (in-memory workbook — NASC-safe) ────────────────────────

    @Override
    @Transactional(readOnly = true)
    public byte[] exportExcel(FeedbackSearchRequest request) {
        Pageable all = PageRequest.of(0, Integer.MAX_VALUE,
                Sort.by(Sort.Direction.DESC, "createdAt"));
        List<Feedback> rows = findFiltered(request, all).getContent();
        Map<Long, User> users = loadUsers(rows);

        String[] headers = {
                "ID", "Type", "Content", "Requester", "Requester Email",
                "Handler", "Status", "Created"
        };
        try (Workbook workbook = new XSSFWorkbook();
             ByteArrayOutputStream out = new ByteArrayOutputStream()) {
            Sheet sheet = workbook.createSheet("Feedback");
            ExcelExportStyle.freezeHeader(sheet);
            CellStyle headerStyle = ExcelExportStyle.headerStyle(workbook);
            CellStyle dataStyle = ExcelExportStyle.dataStyle(workbook);
            CellStyle wrapStyle = ExcelExportStyle.wrapStyle(workbook);

            Row header = sheet.createRow(0);
            for (int i = 0; i < headers.length; i++) {
                ExcelExportStyle.setStyled(header, i, headers[i], headerStyle);
            }
            DateTimeFormatter dtf = DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm")
                    .withZone(ZoneId.systemDefault());
            int r = 1;
            for (Feedback f : rows) {
                User creator = users.get(f.getUserId());
                Row row = sheet.createRow(r++);
                ExcelExportStyle.setStyled(row, 0, f.getId(), dataStyle);
                ExcelExportStyle.setStyled(row, 1, f.getType(), dataStyle);
                ExcelExportStyle.setStyled(row, 2, stripHtml(f.getContent()), wrapStyle);
                ExcelExportStyle.setStyled(row, 3,
                        creator != null ? creator.getName() : "", dataStyle);
                ExcelExportStyle.setStyled(row, 4,
                        creator != null ? creator.getEmail() : "", dataStyle);
                ExcelExportStyle.setStyled(row, 5,
                        resolvedUserName(f) == null ? "" : resolvedUserName(f), dataStyle);
                ExcelExportStyle.setStyled(row, 6, f.getStatus(), dataStyle);
                ExcelExportStyle.setStyled(row, 7,
                        dtf.format(f.getCreatedAt()), dataStyle);
            }
            ExcelExportStyle.autoSizeColumns(sheet, headers.length - 1, 60);
            workbook.write(out);
            return out.toByteArray();
        } catch (IOException e) {
            throw new IllegalStateException("Failed to write feedback Excel export.", e);
        }
    }

    // ── Shared query helper ───────────────────────────────────────────────────

    /** Applies the 5 optional filters (type, status, content, requester, handler). */
    private Page<Feedback> findFiltered(FeedbackSearchRequest request, Pageable pageable) {
        String type = blankToNull(request.type());
        String status = blankToNull(request.status());
        String content = blankToNull(request.content());
        Long requester = request.requesterId();
        Long handler = request.resolvedById();

        Specification<Feedback> spec = (root, query, cb) -> {
            List<Predicate> predicates = new ArrayList<>();
            if (type != null) {
                predicates.add(cb.equal(root.get("type"), type));
            }
            if (status != null) {
                predicates.add(cb.equal(root.get("status"), status));
            }
            if (content != null) {
                // Content is rich-text HTML: strip tags first so keywords only
                // match visible text (not tag names like <strong>). Match each
                // whitespace-separated keyword against the plain text (AND).
                Expression<String> plainText = cb.function("regexp_replace", String.class,
                        root.get("content"), cb.literal("<[^>]+>"), cb.literal(""));
                for (String word : content.toLowerCase().split("\\s+")) {
                    predicates.add(cb.like(cb.lower(plainText), "%" + word + "%"));
                }
            }
            if (requester != null) {
                predicates.add(cb.equal(root.get("userId"), requester));
            }
            if (handler != null) {
                predicates.add(cb.equal(root.get("resolvedBy"), handler));
            }
            return predicates.isEmpty() ? null : cb.and(predicates.toArray(new Predicate[0]));
        };
        return feedbackRepository.findAll(spec, pageable);
    }

    /** Display name of the admin who handled the feedback (null if not handled). */
    private String resolvedUserName(Feedback f) {
        if (f.getResolvedBy() == null) {
            return null;
        }
        return userRepository.findById(f.getResolvedBy()).map(User::getName).orElse(null);
    }

    /** Plain-text version of the rich-text content for Excel cells. */
    private static String stripHtml(String html) {
        if (html == null) {
            return "";
        }
        return html.replaceAll("<[^>]+>", " ").replaceAll("\\s+", " ").trim();
    }

    @Override
    @Transactional
    public void changeStatus(Long id, User admin, FeedbackStatusRequest request) {
        Feedback feedback = feedbackRepository.findById(id)
                .orElseThrow(() -> new BusinessException(BusinessErrorCode.FEEDBACK_NOT_FOUND,
                        "Feedback not found."));
        String status = request.status() == null ? "" : request.status().trim();
        if (!VALID_STATUSES.contains(status)) {
            throw new BusinessException(BusinessErrorCode.FEEDBACK_INVALID_STATUS,
                    "Status must be IN_REVIEW, RESOLVED or IGNORED.");
        }
        String note = request.resolutionNote() == null ? "" : request.resolutionNote().trim();
        if (note.length() > MAX_RESOLUTION_NOTE_LENGTH) {
            throw new BusinessException(BusinessErrorCode.FEEDBACK_RESOLUTION_NOTE_TOO_LONG,
                    "Resolution note is at most 500 characters.");
        }
        if ((Feedback.STATUS_RESOLVED.equals(status) || Feedback.STATUS_IGNORED.equals(status))
                && note.isEmpty()) {
            throw new BusinessException(BusinessErrorCode.FEEDBACK_RESOLUTION_NOTE_REQUIRED,
                    "A resolution note is required.");
        }

        feedback.setStatus(status);
        feedback.setResolutionNote(note.isEmpty() ? null : note);
        feedback.setResolvedBy(admin.getId());
        feedback.setResolvedAt(Instant.now());
        feedbackRepository.save(feedback);

        auditService.log(admin, "UPDATE", "FEEDBACK", String.valueOf(id),
                "status=" + status + ", note=" + (note.isEmpty() ? null : note));
        notifyCreator(feedback, admin, note);
    }

    // ── Notifications ───────────────────────────────────────────────────────

    private void notifyAdmins(Feedback feedback, User creator) {
        List<User> admins = loadAdmins();
        if (admins.isEmpty()) {
            return;
        }
        String typeLabel = capitalize(feedback.getType());
        String title = "New " + typeLabel + " VOC from " + creator.getName() + ".";
        String vocUrl = vocDetailUrl(feedback.getId());
        String body = "New VOC (" + typeLabel + ") from " + creator.getName()
                + " (" + creator.getEmail() + "):\n\n" + feedback.getContent()
                + (vocUrl != null ? "\n\n" + vocUrl : "");
        for (User admin : admins) {
            UserSettings settings = userSettingsRepository.findById(admin.getId()).orElse(null);
            eventNotificationService.notify(creator, admin, settings,
                    new EventNotificationService.EventDetails(
                            title,
                            new EventNotificationService.EventContext(
                                    null, feedback.getId(), null, null, null),
                            "[AX Portal] " + title, body));
        }
    }

    private void notifyCreator(Feedback feedback, User admin, String note) {
        User creator = userRepository.findById(feedback.getUserId()).orElse(null);
        if (creator == null) {
            return;
        }
        boolean resolved = Feedback.STATUS_RESOLVED.equals(feedback.getStatus());
        String title = resolved
                ? "Your VOC has been resolved."
                : "Your VOC has been reviewed.";
        String vocUrl = vocDetailUrl(feedback.getId());
        String body = (resolved ? "Your VOC has been resolved. " : "Your VOC has been reviewed. ")
                + "Note: " + note
                + (vocUrl != null ? "\n\n" + vocUrl : "");
        UserSettings settings = userSettingsRepository.findById(creator.getId()).orElse(null);
        eventNotificationService.notify(admin, creator, settings,
                new EventNotificationService.EventDetails(
                        title,
                        new EventNotificationService.EventContext(
                                null, feedback.getId(), null, null, null),
                        "[AX Portal] " + title, body));
    }

    /** Uppercases the first character of the VOC type (e.g. BUG -> Bug). */
    private static String capitalize(String value) {
        if (value == null || value.isEmpty()) {
            return "";
        }
        return Character.toUpperCase(value.charAt(0)) + value.substring(1);
    }

    /** Frontend URL of the VOC Management page with the detail popup open. */
    private String vocDetailUrl(Long feedbackId) {
        if (appBaseUrl == null || appBaseUrl.isBlank()) {
            return null;
        }
        return appBaseUrl + "/feedback?feedback=" + feedbackId;
    }

    private List<User> loadAdmins() {
        Role adminRole = roleRepository.findByName("ADMIN_USER").orElse(null);
        if (adminRole == null) {
            return List.of();
        }
        return userRepository.findAll().stream()
                .filter(u -> u.getRole() != null && adminRole.getId().equals(u.getRole().getId()))
                .toList();
    }

    private Map<Long, User> loadUsers(List<Feedback> feedbacks) {
        Set<Long> ids = feedbacks.stream().map(Feedback::getUserId).collect(Collectors.toSet());
        if (ids.isEmpty()) {
            return Map.of();
        }
        return userRepository.findAllById(ids).stream()
                .collect(Collectors.toMap(User::getId, u -> u));
    }

    private static String blankToNull(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }
}
