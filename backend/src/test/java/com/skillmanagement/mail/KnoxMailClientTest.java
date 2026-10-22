package com.skillmanagement.mail;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.skillmanagement.mail.KnoxMailClient;
import com.skillmanagement.mail.KnoxMailException;
import com.sun.net.httpserver.HttpServer;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Function;

import static org.junit.jupiter.api.Assertions.*;

class KnoxMailClientTest {

    private HttpServer server;
    private KnoxMailClient client;

    private final Map<String, String> lastHeaders = new ConcurrentHashMap<>();
    private volatile String lastBody = "";
    private volatile String lastPath = "";
    private volatile int nextStatus = 200;
    private volatile String nextResponse = "";

    @BeforeEach
    void setUp() throws IOException {
        server = HttpServer.create(new InetSocketAddress(0), 0);
        server.createContext("/", exchange -> {
            lastPath = exchange.getRequestURI().toString();
            lastHeaders.clear();
            exchange.getRequestHeaders().forEach((k, v) -> lastHeaders.put(k, String.join(",", v)));
            lastBody = new String(exchange.getRequestBody().readAllBytes(), StandardCharsets.UTF_8);
            byte[] response = nextResponse.getBytes(StandardCharsets.UTF_8);
            exchange.getResponseHeaders().add("Content-Type", "application/json");
            exchange.sendResponseHeaders(nextStatus, response.length);
            try (OutputStream os = exchange.getResponseBody()) {
                os.write(response);
            }
        });
        server.start();
        String baseUrl = "http://localhost:" + server.getAddress().getPort();
        client = new KnoxMailClient(new ObjectMapper(), baseUrl, "SYS123", "secret-token", 5, 5);
    }

    @AfterEach
    void tearDown() {
        server.stop(0);
    }

    private Function<String, String> header() {
        // HttpServer normalizes header names (System-ID -> System-id)
        return name -> lastHeaders.entrySet().stream()
                .filter(e -> e.getKey().equalsIgnoreCase(name))
                .map(Map.Entry::getValue).findFirst().orElse(null);
    }

    @Test
    @DisplayName("sendMail success: returns mailId, sends System-ID + Bearer headers, correct path and body")
    void testSendMailSuccess() {
        nextResponse = "{\"mailId\":\"20190531015540hdp_101d\",\"result\":\"success\"}";

        String mailId = client.sendMail("knoxportal", "Test subject", "Hello body",
                "knoxportal@samsung.com", List.of("user@samsung.com"));

        assertEquals("20190531015540hdp_101d", mailId);
        assertEquals("/mail/api/v2.0/mails/send?userId=knoxportal", lastPath);
        assertEquals("SYS123", header().apply("System-ID"));
        assertEquals("Bearer secret-token", header().apply("Authorization"));
        assertTrue(header().apply("Content-Type").contains("application/json"));
        // body shape
        assertTrue(lastBody.contains("\"subject\":\"Test subject\""));
        assertTrue(lastBody.contains("\"contentType\":\"TEXT\""));
        assertTrue(lastBody.contains("\"docSecuType\":\"PERSONAL\""));
        assertTrue(lastBody.contains("\"emailAddress\":\"knoxportal@samsung.com\""));
        assertTrue(lastBody.contains("\"recipientType\":\"TO\""));
    }

    @Test
    @DisplayName("sendMail with 400 errorCode: throws KnoxMailException with code and message")
    void testSendMailHttpError() {
        nextStatus = 400;
        nextResponse = "{\"errorCode\":\"ML1002\",\"message\":\"The user information does not exist.\"}";

        KnoxMailException ex = assertThrows(KnoxMailException.class,
                () -> client.sendMail("baduser", "s", "b", "sender@samsung.com", List.of("to@samsung.com")));

        assertTrue(ex.getMessage().contains("ML1002"));
        assertTrue(ex.getMessage().contains("The user information does not exist."));
    }

    @Test
    @DisplayName("sendMail with result != success: throws KnoxMailException")
    void testSendMailNonSuccessResult() {
        nextResponse = "{\"result\":\"error\"}";

        KnoxMailException ex = assertThrows(KnoxMailException.class,
                () -> client.sendMail("knoxportal", "s", "b", "sender@samsung.com", List.of("to@samsung.com")));

        assertTrue(ex.getMessage().contains("result=error"));
    }

    @Test
    @DisplayName("sendMail with missing mailId: throws KnoxMailException")
    void testSendMailMissingMailId() {
        nextResponse = "{\"result\":\"success\"}";

        assertThrows(KnoxMailException.class,
                () -> client.sendMail("knoxportal", "s", "b", "sender@samsung.com", List.of("to@samsung.com")));
    }

    @Test
    @DisplayName("sendMail with HTML contentType: sends contentType HTML")
    void testSendMailHtmlContentType() {
        nextResponse = "{\"mailId\":\"m2\",\"result\":\"success\"}";

        client.sendMail("knoxportal", "s", "<html><body>hi</body></html>", "HTML",
                "sender@samsung.com", List.of("to@samsung.com"));

        assertTrue(lastBody.contains("\"contentType\":\"HTML\""));
    }

    @Test
    @DisplayName("sendMail with multiple recipients: all included as TO")
    void testSendMailMultipleRecipients() {
        nextResponse = "{\"mailId\":\"m1\",\"result\":\"success\"}";

        client.sendMail("knoxportal", "s", "b", "sender@samsung.com",
                List.of("a@samsung.com", "b@samsung.com"));

        assertTrue(lastBody.contains("a@samsung.com"));
        assertTrue(lastBody.contains("b@samsung.com"));
    }
}
