package com.furelise.skillmanagement.ainews.email;

import jakarta.mail.Address;
import jakarta.mail.Multipart;
import jakarta.mail.Part;
import jakarta.mail.Session;
import jakarta.mail.internet.MimeMessage;
import org.springframework.stereotype.Component;

import java.io.ByteArrayInputStream;
import java.io.InputStream;
import java.time.Instant;
import java.util.HashMap;
import java.util.Map;
import java.util.Properties;

/**
 * Parses an uploaded .mht file into an {@link EmailRecord}
 * (AI_News_Screen_SRS.md v1.14 FR-21 — manual import, admin).
 *
 * <p>.mht files exported from Outlook/Knox are MIME {@code message/rfc822}
 * (multipart/related with base64 parts), so they parse directly with
 * {@link MimeMessage} — no OLE/MSG handling needed. The parsing mirrors the
 * {@code EmailRecord} contract (first text/plain + first text/html, inline
 * CID images keyed by Content-ID) so the import service can feed the result
 * through the exact same sanitize → store → image pipeline as the batch job.</p>
 *
 * <p>Image parts are NOT size-capped here: the cap (2MB) belongs to
 * {@code AiNewsImageStore} (file storage); oversized CID images are
 * inlined as data-URIs by the import service instead.</p>
 */
@Component
public class MhtEmailParser {

    /** CID image size cap for admin-uploaded .mht files. Generous (10MB)
     *  because the file itself is already capped at 20MB by the import
     *  service — the cap is a safety net, not the primary bound. */
    static final int MAX_CID_IMAGE_BYTES = 10 * 1024 * 1024;

    /**
     * Parses a .mht file.
     *
     * @param in the file content (MIME message/rfc822)
     * @return the parsed email record
     * @throws MhtParseException when the content is not a parseable MIME message
     */
    public EmailRecord parse(InputStream in) {
        byte[] bytes;
        try {
            bytes = in.readAllBytes();
        } catch (Exception e) {
            throw new MhtParseException("Cannot read .mht file: " + e.getMessage(), e);
        }
        try {
            MimeMessage message = new MimeMessage(
                    Session.getInstance(new Properties()), new ByteArrayInputStream(bytes));
            EmailRecord record = toRecord(message);
            // MimeMessage is lenient — it "parses" almost any byte stream as a
            // message. A file with no subject, no sender and no body is not a
            // real email (garbage / wrong format) → reject it.
            if ((record.subject() == null || record.subject().isBlank())
                    && (record.from() == null || record.from().isBlank())
                    && (record.bodyHtml() == null || record.bodyHtml().isBlank())
                    && (record.bodyText() == null || record.bodyText().isBlank())) {
                throw new MhtParseException(
                        "File does not contain a recognizable email (no subject, sender or body)");
            }
            return record;
        } catch (MhtParseException e) {
            throw e;
        } catch (Exception e) {
            throw new MhtParseException("Not a valid .mht file: " + e.getMessage(), e);
        }
    }

    private EmailRecord toRecord(MimeMessage message) throws Exception {
        String messageId = firstHeader(message, "Message-ID");
        String subject = message.getSubject();
        String from = formatAddress(message.getFrom());
        String to = formatAddress(message.getRecipients(jakarta.mail.Message.RecipientType.TO));
        // getSentDate() parses RFC 822 dates incl. trailing zone comments
        // (e.g. "Fri, 16 Oct 2026 14:06:54 +0000 (GMT)").
        Instant date = message.getSentDate() != null
                ? message.getSentDate().toInstant()
                : Instant.now();
        String autoSubmitted = firstHeader(message, "Auto-Submitted");

        String bodyText = null;
        String bodyHtml = null;
        Map<String, byte[]> cidImages = new HashMap<>();
        if (message.isMimeType("multipart/*")) {
            Multipart multipart = (Multipart) message.getContent();
            String[] bodyTextHolder = new String[1];
            String[] bodyHtmlHolder = new String[1];
            collectParts(multipart, part -> {
                try {
                    if (bodyTextHolder[0] == null && part.isMimeType("text/plain")) {
                        bodyTextHolder[0] = (String) part.getContent();
                    } else if (bodyHtmlHolder[0] == null && part.isMimeType("text/html")) {
                        bodyHtmlHolder[0] = (String) part.getContent();
                    }
                } catch (Exception e) {
                    // A failing part is skipped — the other parts still count.
                }
            }, cidImages, 0);
            bodyText = bodyTextHolder[0];
            bodyHtml = bodyHtmlHolder[0];
        } else if (message.isMimeType("text/plain")) {
            bodyText = (String) message.getContent();
        } else if (message.isMimeType("text/html")) {
            bodyHtml = (String) message.getContent();
        } else {
            try (InputStream in = message.getInputStream()) {
                bodyText = new String(in.readAllBytes());
            }
        }
        return new EmailRecord(messageId, subject, from, to, date, bodyText, bodyHtml,
                autoSubmitted, cidImages);
    }

    /**
     * Recursively walks a multipart, collecting inline images
     * ({@code Content-Disposition: inline} + {@code Content-ID}) from
     * {@code multipart/related} parts. Nested multiparts are traversed too;
     * text parts are passed to {@code bodyConsumer} (first text/plain +
     * first text/html). Mirrors the batch reader's part collection.
     *
     * @param depth nesting level — capped to prevent StackOverflowError
     *              on crafted deeply-nested MIME (admin-only, but still)
     */
    private void collectParts(Multipart multipart,
                              java.util.function.Consumer<Part> bodyConsumer,
                              Map<String, byte[]> cidImages, int depth) throws Exception {
        if (depth > 20) {
            // Defensive: a legitimately nested MIME never exceeds ~10 levels.
            return;
        }
        for (int i = 0; i < multipart.getCount(); i++) {
            Part part = multipart.getBodyPart(i);
            if (part.isMimeType("multipart/*")) {
                collectParts((Multipart) part.getContent(), bodyConsumer, cidImages, depth + 1);
                continue;
            }
            String contentId = firstHeader(part, "Content-ID");
            String disposition = firstHeader(part, "Content-Disposition");
            if (contentId != null && contentId.startsWith("<") && contentId.endsWith(">")
                    && part.isMimeType("image/*")
                    && (disposition == null || disposition.toLowerCase().contains("inline"))) {
                String cid = contentId.substring(1, contentId.length() - 1);
                try (InputStream in = part.getInputStream()) {
                    // Cap CID image size at read time —
                    // the store's 2MB cap only protects disk, not heap.
                    byte[] bytes = in.readNBytes(MAX_CID_IMAGE_BYTES + 1);
                    if (bytes.length <= MAX_CID_IMAGE_BYTES) {
                        cidImages.put(cid, bytes);
                    }
                }
                continue;
            }
            if (part.isMimeType("text/plain") || part.isMimeType("text/html")) {
                bodyConsumer.accept(part);
            }
        }
    }

    private static String firstHeader(Part part, String name) {
        try {
            String[] values = part.getHeader(name);
            return (values != null && values.length > 0) ? values[0] : null;
        } catch (jakarta.mail.MessagingException e) {
            return null;
        }
    }

    private static String formatAddress(Address[] addresses) {
        if (addresses == null || addresses.length == 0) {
            return null;
        }
        StringBuilder sb = new StringBuilder();
        for (Address address : addresses) {
            if (sb.length() > 0) {
                sb.append(", ");
            }
            if (address instanceof jakarta.mail.internet.InternetAddress ia) {
                sb.append(ia.toUnicodeString());
            } else {
                sb.append(address.toString());
            }
        }
        return sb.toString();
    }
}
