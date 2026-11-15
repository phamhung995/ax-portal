package com.furelise.skillmanagement.translation.dto;

import java.util.Map;

/**
 * Public i18n payload: all messages for one language.
 */
public record MessagesResponse(String lang, Map<String, String> messages) {
}
