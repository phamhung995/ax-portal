package com.furelise.skillmanagement.ainews.email;

/**
 * Thrown when an uploaded .mht file cannot be parsed as a MIME
 * {@code message/rfc822} message (corrupt file, wrong format, truncated).
 * The import service maps this to a per-file FAILED result.
 */
public class MhtParseException extends RuntimeException {

    public MhtParseException(String message) {
        super(message);
    }

    public MhtParseException(String message, Throwable cause) {
        super(message, cause);
    }
}
