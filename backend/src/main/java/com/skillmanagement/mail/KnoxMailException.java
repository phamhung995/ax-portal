package com.skillmanagement.mail;

/**
 * Raised when the Knox Mail API rejects a send request or returns an
 * unexpected payload.
 */
public class KnoxMailException extends RuntimeException {

    public KnoxMailException(String message) {
        super(message);
    }

    public KnoxMailException(String message, Throwable cause) {
        super(message, cause);
    }
}
