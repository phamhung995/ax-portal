package com.furelise.skillmanagement.translation.repository;

import com.furelise.skillmanagement.translation.model.TranslationKey;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

public interface TranslationKeyRepository extends JpaRepository<TranslationKey, Long> {

    Optional<TranslationKey> findByKey(String key);

    boolean existsByKey(String key);

    @Query("SELECT DISTINCT k FROM TranslationKey k LEFT JOIN Translation t ON t.keyId = k.id WHERE "
            + "(:search IS NULL OR k.key LIKE %:search% OR k.description LIKE %:search% OR t.value LIKE %:search%) "
            + "ORDER BY k.key")
    List<TranslationKey> search(@Param("search") String search);
}
