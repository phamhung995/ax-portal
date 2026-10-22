package com.skillmanagement.user.service;

import java.io.ByteArrayInputStream;
import java.io.FilterInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.SequenceInputStream;
import java.io.PushbackInputStream;
import java.nio.charset.StandardCharsets;

/**
 * Detects Samsung NASC document-protection encryption on uploaded files.
 * Encrypted files start with "<## NASCA DRM" instead of a ZIP "PK" header.
 * Such files are unreadable by Apache POI and must be rejected with a clear message.
 */
public final class NascFileDetector {

    private NascFileDetector() {
    }

    /**
     * Reads the first up-to-16 bytes without consuming or closing the stream.
     * Returns the head bytes so the caller can both check the marker and
     * replay them via {@link #withHead}.
     */
    public static byte[] peekHead(InputStream in) throws IOException {
        // Non-closing wrapper: PushbackInputStream.close() would otherwise close the upload stream.
        InputStream nonClosing = new FilterInputStream(in) {
            @Override public void close() { /* keep underlying stream open */ }
        };
        try (PushbackInputStream pb = new PushbackInputStream(nonClosing, 16)) {
            byte[] head = new byte[16];
            int read = pb.readNBytes(head, 0, 16);
            if (read < 0) return new byte[0];
            return read == 16 ? head : java.util.Arrays.copyOf(head, read);
        }
    }

    /** True when the head bytes start with the NASC DRM marker. */
    public static boolean isNascMarker(byte[] head) {
        if (head.length < 4) return false;
        String prefix = new String(head, 0, Math.min(head.length, 12), StandardCharsets.US_ASCII);
        return prefix.startsWith("<## NASCA");
    }

    /**
     * Returns a new stream that first replays the peeked head bytes, then
     * continues with the original stream. Use after {@link #peekHead} so the
     * parser sees the complete file.
     */
    public static InputStream withHead(byte[] head, InputStream in) {
        return new SequenceInputStream(new ByteArrayInputStream(head), in);
    }
}
