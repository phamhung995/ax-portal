package com.skillmanagement.ainews;

/**
 * Thrown when an AI news item does not exist OR is archived.
 * The client gets a single 404 NEWS_NOT_FOUND in both cases — the reason is
 * intentionally not distinguished (avoids leaking archived-item info,
 * AI_News_Screen_SRS.md §5.4).
 */
public class NewsNotFoundException extends RuntimeException {

    public NewsNotFoundException(String message) {
        super(message);
    }
}
