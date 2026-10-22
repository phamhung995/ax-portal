package com.skillmanagement.ainews.email;

import jakarta.mail.Address;
import jakarta.mail.FetchProfile;
import jakarta.mail.Folder;
import jakarta.mail.Message;
import jakarta.mail.Multipart;
import jakarta.mail.Part;
import jakarta.mail.Session;
import jakarta.mail.Store;
import org.springframework.stereotype.Component;

import java.io.InputStream;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Properties;

/**
 * IMAP implementation of the email-reading skill (AI_News_Batch.md §3.1).
 *
 * <p>READONLY connection, 60s timeout per mailbox, results sorted by date
 * ascending (oldest first — BR-NEWS-009). Any failure (timeout/auth/network)
 * is wrapped in {@link EmailReaderException} so the batch job can log the
 * source error and continue with the other sources (BR-NEWS-006).
 */
@Component
public class ImapEmailReader implements EmailReader {

    private static final int TIMEOUT_MILLIS = 60_000;
    private static final String DEFAULT_FOLDER = "INBOX";

    @Override
    public List<EmailRecord> read(String host, int port, String username, String password,
                                  String folder, Instant since, int limit) {
        if (limit <= 0) {
            return List.of();
        }
        Properties props = new Properties();
        props.put("mail.imap.host", host);
        props.put("mail.imap.port", String.valueOf(port));
        props.put("mail.imap.ssl.enable", "true");
        props.put("mail.imap.connectiontimeout", String.valueOf(TIMEOUT_MILLIS));
        props.put("mail.imap.timeout", String.valueOf(TIMEOUT_MILLIS));
        Session session = Session.getInstance(props);

        List<EmailRecord> result = new ArrayList<>();
        try (Store store = session.getStore("imaps")) {
            store.connect(host, port, username, password);
            Folder folderHandle = store.getFolder(folder == null || folder.isBlank() ? DEFAULT_FOLDER : folder);
            try {
                folderHandle.open(Folder.READ_ONLY);
                // IMAP SINCE is day-granular and inclusive — search a bit wider,
                // then apply the exact `date > since` filter in memory.
                Message[] messages = folderHandle.search(
                        new jakarta.mail.search.ReceivedDateTerm(
                                jakarta.mail.search.ComparisonTerm.GT,
                                java.util.Date.from(since.minusSeconds(86_400 * 2))));
                FetchProfile profile = new FetchProfile();
                profile.add(FetchProfile.Item.ENVELOPE);
                profile.add(FetchProfile.Item.FLAGS);
                folderHandle.fetch(messages, profile);

                List<Message> matching = new ArrayList<>();
                for (Message message : messages) {
                    if (message.getReceivedDate() != null
                            && message.getReceivedDate().toInstant().isAfter(since)) {
                        matching.add(message);
                    }
                }
                // Oldest first — required by BR-NEWS-009 (overflow must be newer than the marker).
                List<java.util.Date> dates = new ArrayList<>(matching.size());
                for (Message message : matching) {
                    dates.add(message.getReceivedDate());
                }
                List<Integer> order = new ArrayList<>(matching.size());
                for (int i = 0; i < matching.size(); i++) {
                    order.add(i);
                }
                order.sort((a, b) -> {
                    java.util.Date da = dates.get(a);
                    java.util.Date db = dates.get(b);
                    if (da == null && db == null) {
                        return 0;
                    }
                    if (da == null) {
                        return -1;
                    }
                    if (db == null) {
                        return 1;
                    }
                    return da.compareTo(db);
                });
                for (int index : order) {
                    if (result.size() >= limit) {
                        break;
                    }
                    result.add(toRecord(matching.get(index)));
                }
            } finally {
                if (folderHandle.isOpen()) {
                    folderHandle.close(false);
                }
            }
        } catch (EmailReaderException e) {
            throw e;
        } catch (Exception e) {
            // Never log the password (BR-NEWS-011 / NFR-NEWS-02).
            throw new EmailReaderException("IMAP read failed for " + username + "@" + host
                    + ": " + e.getMessage(), e);
        }
        return result;
    }

    private EmailRecord toRecord(Message message) throws Exception {
        String messageId = firstHeader(message, "Message-ID");
        String subject = message.getSubject();
        String from = formatAddress(message.getFrom());
        String to = formatAddress(message.getRecipients(jakarta.mail.Message.RecipientType.TO));
        Instant date = message.getReceivedDate() != null
                ? message.getReceivedDate().toInstant()
                : Instant.now();
        String autoSubmitted = firstHeader(message, "Auto-Submitted");

        String bodyText = null;
        String bodyHtml = null;
        if (message.isMimeType("multipart/*")) {
            Multipart multipart = (Multipart) message.getContent();
            for (int i = 0; i < multipart.getCount(); i++) {
                Part part = multipart.getBodyPart(i);
                if (bodyText == null && part.isMimeType("text/plain")) {
                    bodyText = (String) part.getContent();
                } else if (bodyHtml == null && part.isMimeType("text/html")) {
                    bodyHtml = (String) part.getContent();
                }
                if (bodyText != null && bodyHtml != null) {
                    break;
                }
            }
        } else if (message.isMimeType("text/plain")) {
            bodyText = (String) message.getContent();
        } else if (message.isMimeType("text/html")) {
            bodyHtml = (String) message.getContent();
        } else {
            try (InputStream in = message.getInputStream()) {
                bodyText = new String(in.readAllBytes());
            }
        }
        return new EmailRecord(messageId, subject, from, to, date, bodyText, bodyHtml, autoSubmitted);
    }

    private static String firstHeader(Message message, String name) {
        try {
            String[] values = message.getHeader(name);
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
