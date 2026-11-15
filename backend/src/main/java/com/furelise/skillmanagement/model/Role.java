package com.furelise.skillmanagement.model;

import jakarta.persistence.*;
import lombok.*;

/**
 * JPA Entity representing dynamic roles in the system (e.g. ADMIN, EDITOR, USER).
 */
@Data
@Builder
@AllArgsConstructor
@NoArgsConstructor
@Entity
@Table(name = "role")
public class Role {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, unique = true, length = 50)
    private String name;

    @Column(length = 255)
    private String description;

    /**
     * Returns the Spring Security authority string with ROLE_ prefix.
     */
    public String toAuthority() {
        return name != null && name.startsWith("ROLE_") ? name : "ROLE_" + name;
    }
}
