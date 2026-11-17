package com.furelise.skillmanagement.reputation.model;

import jakarta.persistence.Column;
import jakarta.persistence.Embeddable;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serializable;

/** Composite key of {@link ReputationScore}: one row per (target_type, target_id). */
@Data
@Embeddable
@AllArgsConstructor
@NoArgsConstructor
public class ReputationScoreId implements Serializable {

    @Column(name = "target_type", length = 20)
    private String targetType;

    @Column(name = "target_id")
    private Long targetId;
}
