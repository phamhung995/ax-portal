package com.furelise.skillmanagement.translation.service;

import com.furelise.skillmanagement.translation.dto.CreateTranslationRequest;
import com.furelise.skillmanagement.translation.dto.TranslationKeyItem;
import com.furelise.skillmanagement.translation.dto.UpdateTranslationRequest;

import java.util.List;
import java.util.Map;

/**
 * DB-driven translations (en/vi/ko): admin CRUD + public message lookup.
 */
public interface TranslationService {

    /** All keys (optionally filtered by key/description substring), sorted by key. */
    List<TranslationKeyItem> list(String search);

    TranslationKeyItem create(CreateTranslationRequest request, Long actorUserId);

    TranslationKeyItem update(Long keyId, UpdateTranslationRequest request, Long actorUserId);

    void delete(Long keyId);

    /**
     * All messages for one language: keys missing in that language fall back
     * to their en value; keys with no en value are omitted.
     */
    Map<String, String> messages(String lang);
}
