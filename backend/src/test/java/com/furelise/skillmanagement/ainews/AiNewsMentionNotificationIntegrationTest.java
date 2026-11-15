package com.furelise.skillmanagement.ainews;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.furelise.skillmanagement.notification.model.Notification;
import com.furelise.skillmanagement.notification.repository.NotificationRepository;
import com.furelise.skillmanagement.user.model.User;
import com.furelise.skillmanagement.user.model.UserSettings;
import com.furelise.skillmanagement.user.repository.UserRepository;
import com.furelise.skillmanagement.user.repository.UserSettingsRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * End-to-end verification of the @mention notification path on the
 * {@code local-h2} profile: admin imports a news item, then posts a comment
 * that @mentions a second user. The mention notification is created in an
 * afterCommit callback running in its OWN transaction (TransactionTemplate) —
 * this test proves that path writes the notification row against a real
 * Spring context + real DB (H2). Nothing touches the real PostgreSQL.
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("local-h2")
class AiNewsMentionNotificationIntegrationTest {

    @Autowired
    private MockMvc mockMvc;
    @Autowired
    private ObjectMapper objectMapper;
    @Autowired
    private UserRepository userRepository;
    @Autowired
    private UserSettingsRepository userSettingsRepository;
    @Autowired
    private NotificationRepository notificationRepository;
    @Autowired
    private PasswordEncoder passwordEncoder;

    /** Keep the image store out of the repo's uploads/ folder. */
    @DynamicPropertySource
    static void imageDir(DynamicPropertyRegistry registry) throws Exception {
        Path tmp = Files.createTempDirectory("ainews-h2-mention-images");
        registry.add("app.upload.ai-news-image-dir", () -> tmp.toString());
    }

    private byte[] sampleBytes() throws Exception {
        return Files.readAllBytes(Path.of("src/test/resources/mht/sample.mht"));
    }

    private String adminToken() throws Exception {
        MvcResult res = mockMvc.perform(post("/api/v1/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\":\"admin@skillmgmt.local\",\"password\":\"Admin@12345\"}"))
                .andExpect(status().isOk())
                .andReturn();
        JsonNode data = objectMapper.readTree(res.getResponse().getContentAsString()).get("data");
        return data.get("accessToken").asText();
    }

    @Test
    @DisplayName("local-h2: @mention in comment → notification row for the mentioned user (afterCommit + own tx)")
    void mentionCreatesNotification() throws Exception {
        // Second user (the mention target) — admin is the actor.
        User target = userRepository.findByEmailIgnoreCase("mention-target@test.local")
                .orElseGet(() -> userRepository.save(User.builder()
                        .name("Mention Target")
                        .email("mention-target@test.local")
                        .password(passwordEncoder.encode("Target@12345"))
                        .role(userRepository.findByEmailIgnoreCase("admin@skillmgmt.local")
                                .orElseThrow().getRole())
                        .status("ACTIVE")
                        .createdAppId("TEST")
                        .build()));
        if (userSettingsRepository.findById(target.getId()).isEmpty()) {
            userSettingsRepository.save(UserSettings.builder()
                    .userId(target.getId())
                    .emailNotify(true).inappNotify(true)
                    .build());
        }

        String token = adminToken();

        // Import a real news item so a valid newsId exists.
        MvcResult importRes = mockMvc.perform(multipart("/api/v1/admin/ai-news/import")
                        .file(new MockMultipartFile("files", "sample.mht",
                                "message/rfc822", sampleBytes()))
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + token))
                .andExpect(status().isOk())
                .andReturn();
        JsonNode imported = objectMapper.readTree(importRes.getResponse().getContentAsString());
        assertThat(imported.get("data").isArray()).isTrue();
        JsonNode first = imported.get("data").get(0);
        assertThat(first.get("status").asText()).isEqualTo("INSERTED");
        long newsId = first.get("newsId").asLong();
        assertThat(newsId).isPositive();

        // Admin posts a comment @mentioning the target user.
        String content = "<p>Hi <span data-type=\"mention\" data-user-id=\""
                + target.getId() + "\" data-user-name=\"Mention Target\">@Mention Target</span> please review</p>";
        MvcResult commentRes = mockMvc.perform(post("/api/v1/home/ai-news/" + newsId + "/comments")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"content\":" + objectMapper.valueToTree(content) + ",\"parentId\":null}")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + token))
                .andExpect(status().isOk())
                .andReturn();
        JsonNode commentData = objectMapper.readTree(commentRes.getResponse().getContentAsString()).get("data");
        long commentId = commentData.get("commentId").asLong();
        assertThat(commentId).isPositive();

        // The afterCommit callback (own transaction) must have written the
        // notification for the mentioned user.
        List<Notification> notifications = notificationRepository
                .findByUserIdAndNotificationIdLessThanOrderByCreatedAtDescNotificationIdDesc(
                        target.getId(), Long.MAX_VALUE,
                        org.springframework.data.domain.Pageable.ofSize(20));
        assertThat(notifications)
                .anySatisfy(n -> {
                    assertThat(n.getUserId()).isEqualTo(target.getId());
                    assertThat(n.getNewsId()).isEqualTo(newsId);
                    assertThat(n.getCommentId()).isEqualTo(commentId);
                    assertThat(n.isRead()).isFalse();
                    assertThat(n.getTitle()).contains("mentioned you in");
                });
    }
}
