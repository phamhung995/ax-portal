package com.furelise.skillmanagement.translation.service.impl;

import com.furelise.skillmanagement.audit.service.AuditService;
import com.furelise.skillmanagement.exception.BusinessErrorCode;
import com.furelise.skillmanagement.exception.BusinessException;
import com.furelise.skillmanagement.translation.dto.CreateTranslationRequest;
import com.furelise.skillmanagement.translation.dto.TranslationKeyItem;
import com.furelise.skillmanagement.translation.dto.UpdateTranslationRequest;
import com.furelise.skillmanagement.translation.model.Translation;
import com.furelise.skillmanagement.translation.model.TranslationId;
import com.furelise.skillmanagement.translation.model.TranslationKey;
import com.furelise.skillmanagement.translation.repository.TranslationKeyRepository;
import com.furelise.skillmanagement.translation.repository.TranslationRepository;
import com.furelise.skillmanagement.translation.service.TranslationService;
import com.furelise.skillmanagement.user.model.User;
import com.furelise.skillmanagement.user.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

@Service
@RequiredArgsConstructor
@Slf4j
public class TranslationServiceImpl implements TranslationService {

    private static final Set<String> LANGUAGES = Set.of("en", "vi", "ko");
    private static final String KEY_PATTERN = "^[a-zA-Z][a-zA-Z0-9._-]{0,119}$";
    private static final long CACHE_TTL_MINUTES = 5;

    /** In-memory message cache, invalidated on any admin write. */
    private final Map<String, CacheEntry> cache = new ConcurrentHashMap<>();

    private record CacheEntry(Map<String, String> messages, Instant expiresAt) {
    }

    private final TranslationKeyRepository keyRepository;
    private final TranslationRepository translationRepository;
    private final UserRepository userRepository;
    private final AuditService auditService;

    @Override
    @Transactional(readOnly = true)
    public List<TranslationKeyItem> list(String search) {
        String trimmed = (search == null || search.isBlank()) ? null : search.trim();
        Map<Long, List<Translation>> byKey = new HashMap<>();
        for (Translation t : translationRepository.findAll()) {
            byKey.computeIfAbsent(t.getKeyId(), k -> new ArrayList<>()).add(t);
        }
        Map<Long, String> names = userDisplayNames(byKey);
        return keyRepository.search(trimmed).stream()
                .map(k -> toItem(k, byKey.getOrDefault(k.getId(), List.of()), names))
                .toList();
    }

    @Override
    @Transactional
    public TranslationKeyItem create(CreateTranslationRequest request, Long actorUserId) {
        String key = request.key() == null ? "" : request.key().trim();
        if (!key.matches(KEY_PATTERN)) {
            throw new BusinessException(BusinessErrorCode.TRANSLATION_KEY_INVALID,
                    "Invalid translation key: " + key);
        }
        if (keyRepository.existsByKey(key)) {
            throw new BusinessException(BusinessErrorCode.TRANSLATION_KEY_EXISTS,
                    "Translation key already exists: " + key);
        }
        TranslationKey entity = TranslationKey.builder()
                .key(key)
                .description(trimToNull(request.description()))
                .build();
        keyRepository.save(entity);
        saveValues(entity.getId(), request.values(), actorUserId);

        auditService.log("CREATE", "TRANSLATION", String.valueOf(entity.getId()), "key=" + key);
        cache.clear();
        return toItem(entity, translationRepository.findByKeyId(entity.getId()), Map.of());
    }

    @Override
    @Transactional
    public TranslationKeyItem update(Long keyId, UpdateTranslationRequest request, Long actorUserId) {
        TranslationKey entity = keyRepository.findById(keyId)
                .orElseThrow(() -> new BusinessException(BusinessErrorCode.TRANSLATION_KEY_NOT_FOUND,
                        "Translation key not found: " + keyId));
        if (request.description() != null) {
            entity.setDescription(trimToNull(request.description()));
        }
        if (request.values() != null) {
            applyValues(entity, request.values(), actorUserId);
        }
        keyRepository.save(entity);

        auditService.log("UPDATE", "TRANSLATION", String.valueOf(keyId), "key=" + entity.getKey());
        cache.clear();
        return toItem(entity, translationRepository.findByKeyId(keyId), Map.of());
    }

    @Override
    @Transactional
    public void delete(Long keyId) {
        TranslationKey entity = keyRepository.findById(keyId)
                .orElseThrow(() -> new BusinessException(BusinessErrorCode.TRANSLATION_KEY_NOT_FOUND,
                        "Translation key not found: " + keyId));
        String key = entity.getKey();
        translationRepository.deleteByKeyId(keyId);
        keyRepository.delete(entity);
        auditService.log("DELETE", "TRANSLATION", String.valueOf(keyId), "key=" + key);
        cache.clear();
    }

    @Override
    @Transactional(readOnly = true)
    public Map<String, String> messages(String lang) {
        if (!LANGUAGES.contains(lang)) {
            throw new BusinessException(BusinessErrorCode.INVALID_LANGUAGE, "Invalid language: " + lang);
        }
        CacheEntry entry = cache.get(lang);
        if (entry != null && entry.expiresAt().isAfter(Instant.now())) {
            return entry.messages();
        }
        Map<String, String> result = loadMessages(lang);
        cache.put(lang, new CacheEntry(result, Instant.now().plus(CACHE_TTL_MINUTES, java.time.temporal.ChronoUnit.MINUTES)));
        return result;
    }

    private Map<String, String> loadMessages(String lang) {
        Map<Long, String> byLang = new HashMap<>();
        for (Translation t : translationRepository.findByLanguage(lang)) {
            byLang.put(t.getKeyId(), t.getValue());
        }
        Map<Long, String> byEn = new HashMap<>();
        for (Translation t : translationRepository.findByLanguage("en")) {
            byEn.put(t.getKeyId(), t.getValue());
        }
        Map<String, String> result = new HashMap<>();
        for (TranslationKey k : keyRepository.findAll()) {
            String value = byLang.get(k.getId());
            if (value == null) value = byEn.get(k.getId());
            if (value != null) result.put(k.getKey(), value);
        }
        return result;
    }

    // ── helpers ─────────────────────────────────────────────────────────────

    private void saveValues(Long keyId, Map<String, String> values, Long actorUserId) {
        if (values == null) return;
        for (Map.Entry<String, String> e : values.entrySet()) {
            if (!LANGUAGES.contains(e.getKey())) continue;
            String value = trimToNull(e.getValue());
            if (value == null) continue;
            translationRepository.save(Translation.builder()
                    .keyId(keyId)
                    .language(e.getKey())
                    .value(value)
                    .updatedBy(actorUserId)
                    .build());
        }
    }

    /** Merge semantics: provided non-blank values upsert, blank values delete. */
    private void applyValues(TranslationKey entity, Map<String, String> values, Long actorUserId) {
        Long keyId = entity.getId();
        for (Map.Entry<String, String> e : values.entrySet()) {
            if (!LANGUAGES.contains(e.getKey())) continue;
            String value = trimToNull(e.getValue());
            Translation existing = translationRepository
                    .findById(new TranslationId(keyId, e.getKey())).orElse(null);
            if (value == null) {
                if (existing != null) translationRepository.delete(existing);
            } else if (existing != null) {
                existing.setValue(value);
                existing.setUpdatedBy(actorUserId);
                translationRepository.save(existing);
            } else {
                translationRepository.save(Translation.builder()
                        .keyId(keyId)
                        .language(e.getKey())
                        .value(value)
                        .updatedBy(actorUserId)
                        .build());
            }
        }
    }

    private TranslationKeyItem toItem(TranslationKey k, List<Translation> values, Map<Long, String> userNames) {
        Map<String, String> valueMap = new LinkedHashMap<>();
        Instant updatedAt = null;
        Long updatedBy = null;
        for (Translation t : values) {
            valueMap.put(t.getLanguage(), t.getValue());
            if (updatedAt == null || (t.getUpdatedAt() != null && t.getUpdatedAt().isAfter(updatedAt))) {
                updatedAt = t.getUpdatedAt();
                updatedBy = t.getUpdatedBy();
            }
        }
        return new TranslationKeyItem(k.getId(), k.getKey(), k.getDescription(),
                valueMap, updatedAt, updatedBy == null ? null : userNames.get(updatedBy));
    }

    /** Display names for the distinct updated_by ids present in the rows. */
    private Map<Long, String> userDisplayNames(Map<Long, List<Translation>> byKey) {
        Set<Long> ids = new HashSet<>();
        for (List<Translation> list : byKey.values()) {
            for (Translation t : list) {
                if (t.getUpdatedBy() != null) ids.add(t.getUpdatedBy());
            }
        }
        if (ids.isEmpty()) return Map.of();
        Map<Long, String> names = new HashMap<>();
        for (User u : userRepository.findAllById(ids)) {
            names.put(u.getId(), u.getName());
        }
        return names;
    }

    private static String trimToNull(String s) {
        if (s == null) return null;
        String t = s.trim();
        return t.isEmpty() ? null : t;
    }
}
