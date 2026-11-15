package com.furelise.skillmanagement.translation.model;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serializable;

/**
 * Composite PK for {@link Translation} (key_id + language).
 */
@Data
@Builder
@AllArgsConstructor
@NoArgsConstructor
public class TranslationId implements Serializable {

    private Long keyId;

    private String language;
}
