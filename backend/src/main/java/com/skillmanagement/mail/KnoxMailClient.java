package com.skillmanagement.mail;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.List;
import java.util.Map;

/**
 * Client for the Knox C&C Suite Mail API v2.0 (Knox Developers Center).
 *
 * <p>Auth: every request carries the linkage-issued Account ID in the
 * {@code System-ID} header and a Bearer access token (both issued via the
 * Knox interface linkage application — not OAuth). Base URL is
 * environment-specific: stage {@code https://openapi.stage.samsung.net},
 * production (domestic) {@code https://openapi.samsung.net}.</p>
 *
 * <p>Constraints (Knox Dev Center): subject ≤ 300 bytes, body ≤ 1MB,
 * max 100 recipients, contentType TEXT/HTML/MIME, docSecuType
 * PERSONAL/OFFICIAL for normal mail.</p>
 */
@Component
public class KnoxMailClient {

    private final ObjectMapper objectMapper;
    private final HttpClient httpClient;
    private final String baseUrl;
    private final String systemId;
    private final String accessToken;
    private final int readTimeoutSeconds;

    public KnoxMailClient(
            ObjectMapper objectMapper,
            @Value("${knox.mail.base-url:https://openapi.stage.samsung.net}") String baseUrl,
            @Value("${knox.mail.system-id:}") String systemId,
            @Value("${knox.mail.access-token:}") String accessToken,
            @Value("${knox.mail.connect-timeout-seconds:10}") int connectTimeoutSeconds,
            @Value("${knox.mail.read-timeout-seconds:30}") int readTimeoutSeconds
    ) {
        this.objectMapper = objectMapper;
        this.httpClient = HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(connectTimeoutSeconds))
                .build();
        this.baseUrl = baseUrl.endsWith("/") ? baseUrl.substring(0, baseUrl.length() - 1) : baseUrl;
        this.systemId = systemId;
        this.accessToken = accessToken;
        this.readTimeoutSeconds = readTimeoutSeconds;
    }

    /**
     * Sends a normal mail via POST /mail/api/v2.0/mails/send?userId={senderKnoxId}.
     *
     * @param senderKnoxId Knox ID of the sender (query param {@code userId})
     * @param subject      mail subject (≤ 300 bytes)
     * @param body         plain-text body (≤ 1MB)
     * @param senderEmail  sender email (linkage-registered portal account)
     * @param recipients   recipient emails (≤ 100), sent as TO
     * @return the mailId issued by Knox
     * @throws KnoxMailException on any non-success response
     */
    public String sendMail(String senderKnoxId, String subject, String body,
                           String senderEmail, List<String> recipients) {
        return sendMail(senderKnoxId, subject, body, "TEXT", senderEmail, recipients);
    }

    /**
     * Sends a normal mail with an explicit content type (TEXT or HTML).
     *
     * @param senderKnoxId Knox ID of the sender (query param {@code userId})
     * @param subject      mail subject (≤ 300 bytes)
     * @param body         mail body (≤ 1MB)
     * @param contentType  "TEXT" or "HTML"
     * @param senderEmail  sender email (linkage-registered portal account)
     * @param recipients   recipient emails (≤ 100), sent as TO
     * @return the mailId issued by Knox
     * @throws KnoxMailException on any non-success response
     */
    public String sendMail(String senderKnoxId, String subject, String body, String contentType,
                           String senderEmail, List<String> recipients) {
        Map<String, Object> payload = Map.of(
                "subject", subject,
                "contents", body,
                "contentType", contentType,
                "docSecuType", "PERSONAL",
                "sender", Map.of("emailAddress", senderEmail),
                "recipients", recipients.stream()
                        .map(r -> Map.of("emailAddress", r, "recipientType", "TO"))
                        .toList()
        );

        HttpRequest request;
        try {
            request = HttpRequest.newBuilder()
                    .uri(URI.create(baseUrl + "/mail/api/v2.0/mails/send?userId=" + senderKnoxId))
                    .timeout(Duration.ofSeconds(readTimeoutSeconds))
                    .header("System-ID", systemId)
                    .header("Authorization", "Bearer " + accessToken)
                    .header("Content-Type", "application/json")
                    .POST(HttpRequest.BodyPublishers.ofString(objectMapper.writeValueAsString(payload)))
                    .build();
        } catch (Exception e) {
            throw new KnoxMailException("Failed to build Knox Mail API request", e);
        }

        HttpResponse<String> response;
        try {
            response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
        } catch (Exception e) {
            throw new KnoxMailException("Knox Mail API request failed: " + e.getMessage(), e);
        }

        if (response.statusCode() != 200) {
            throw new KnoxMailException("Knox Mail API HTTP " + response.statusCode() + ": "
                    + extractErrorMessage(response.body()));
        }

        try {
            JsonNode bodyNode = objectMapper.readTree(response.body());
            if (!"success".equalsIgnoreCase(bodyNode.path("result").asText())) {
                throw new KnoxMailException("Knox Mail API returned result="
                        + bodyNode.path("result").asText());
            }
            String mailId = bodyNode.path("mailId").asText(null);
            if (mailId == null || mailId.isBlank()) {
                throw new KnoxMailException("Knox Mail API response missing mailId: " + response.body());
            }
            return mailId;
        } catch (KnoxMailException e) {
            throw e;
        } catch (Exception e) {
            throw new KnoxMailException("Unexpected Knox Mail API response: " + response.body(), e);
        }
    }

    private String extractErrorMessage(String responseBody) {
        try {
            JsonNode body = objectMapper.readTree(responseBody);
            String code = body.path("errorCode").asText("");
            String message = body.path("message").asText(responseBody);
            return code.isBlank() ? message : code + ": " + message;
        } catch (Exception e) {
            return responseBody;
        }
    }
}
