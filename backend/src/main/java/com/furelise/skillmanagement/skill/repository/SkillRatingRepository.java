package com.furelise.skillmanagement.skill.repository;

import com.furelise.skillmanagement.skill.model.SkillRating;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;

public interface SkillRatingRepository extends JpaRepository<SkillRating, Long> {

    Optional<SkillRating> findBySkillIdAndUserId(Long skillId, Long userId);

    List<SkillRating> findBySkillId(Long skillId);

    @Query("SELECT AVG(r.rating) FROM SkillRating r WHERE r.skillId = :skillId")
    BigDecimal averageRatingBySkillId(@org.springframework.data.repository.query.Param("skillId") Long skillId);

    /**
     * Q2 (BA §14, chốt 17/11): self-ratings do NOT count toward the score.
     * Item average / count excluding the skill creator's own rating.
     */
    @Query("SELECT AVG(r.rating) FROM SkillRating r WHERE r.skillId = :skillId AND r.userId <> :userId")
    BigDecimal averageRatingBySkillIdExcludingUser(
            @org.springframework.data.repository.query.Param("skillId") Long skillId,
            @org.springframework.data.repository.query.Param("userId") Long userId);

    long countBySkillIdAndUserIdNot(Long skillId, Long userId);

    /** Global mean rating across all skills — the Bayesian prior mean. */
    @Query("SELECT AVG(r.rating) FROM SkillRating r")
    BigDecimal averageRating();

    @Query("SELECT COUNT(r) FROM SkillRating r WHERE r.skillId = :skillId")
    long countBySkillId(@org.springframework.data.repository.query.Param("skillId") Long skillId);

    @Query("SELECT r FROM SkillRating r WHERE r.skillId IN :skillIds")
    List<SkillRating> findBySkillIdIn(@org.springframework.data.repository.query.Param("skillIds") List<Long> skillIds);

    /** Per-skill rating aggregate (avg + count) for a set of skills — one GROUP BY instead of loading all rows. */
    @Query("SELECT r.skillId, AVG(r.rating), COUNT(r) FROM SkillRating r WHERE r.skillId IN :skillIds GROUP BY r.skillId")
    List<Object[]> ratingAggBySkillIdIn(@org.springframework.data.repository.query.Param("skillIds") List<Long> skillIds);
}
