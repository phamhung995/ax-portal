package com.skillmanagement.feedback.model;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.Instant;

/**
 * User feedback (VOC): bug report, suggestion, or question.
 * Screenshot is an optional base64 data URI (same approach as skill
 * description images). Admins move it through NEW → IN_REVIEW → RESOLVED/IGNORED.
 */
@Data
@Builder
@AllArgsConstructor
@NoArgsConstructor
@Entity
@Table(name = "feedback")
public class Feedback {

    public static final String TYPE_BUG = "BUG";
    public static final String TYPE_SUGGESTION = "SUGGESTION";
    public static final String TYPE_QUESTION = "QUESTION";

    public static final String STATUS_NEW = "NEW";
    public static final String STATUS_IN_REVIEW = "IN_REVIEW";
    public static final String STATUS_RESOLVED = "RESOLVED";
    public static final String STATUS_IGNORED = "IGNORED";

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "user_id", nullable = false)
    private Long userId;

    @Column(nullable = false, length = 20)
    private String type;

    @Column(nullable = false, columnDefinition = "TEXT")
    private String content;

    /** Optional screenshot as a base64 data URI (data:image/...). */
    @Column(columnDefinition = "TEXT")
    private String screenshot;

    @Builder.Default
    @Column(nullable = false, length = 20)
    private String status = STATUS_NEW;

    @Column(name = "resolution_note", length = 500)
    private String resolutionNote;

    @Column(name = "resolved_by")
    private Long resolvedBy;

    @Column(name = "resolved_at")
    private Instant resolvedAt;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @PrePersist
    protected void onCreate() {
        if (createdAt == null) {
            createdAt = Instant.now();
        }
    }
}
