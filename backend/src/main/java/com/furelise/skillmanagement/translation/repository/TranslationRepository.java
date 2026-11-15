package com.furelise.skillmanagement.translation.repository;

import com.furelise.skillmanagement.translation.model.Translation;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface TranslationRepository extends JpaRepository<Translation, com.furelise.skillmanagement.translation.model.TranslationId> {

    List<Translation> findByKeyId(Long keyId);

    List<Translation> findByLanguage(String language);

    void deleteByKeyId(Long keyId);
}
