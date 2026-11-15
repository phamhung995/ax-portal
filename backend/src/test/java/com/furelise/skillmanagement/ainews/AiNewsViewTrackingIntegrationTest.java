package com.furelise.skillmanagement.ainews;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * End-to-end verification of the view-tracking path on the
 * {@code local-h2} profile: admin imports a news item, then POSTs
 * /{newsId}/view twice. The unique (news_id, user_id) constraint must make
 * the second call a no-op — the distinct-viewer count stays 1, and the
 * denormalized ai_news.view_count (read by the detail endpoint) must agree
 * with the view log.
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("local-h2")
class AiNewsViewTrackingIntegrationTest {

    @Autowired
    private MockMvc mockMvc;
    @Autowired
    private ObjectMapper objectMapper;

    /** Keep the image store out of the repo's uploads/ folder. */
    @DynamicPropertySource
    static void imageDir(DynamicPropertyRegistry registry) throws Exception {
        Path tmp = Files.createTempDirectory("ainews-h2-view-images");
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

    private long importNews(String token) throws Exception {
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
        return first.get("newsId").asLong();
    }

    private int postView(String token, long newsId) throws Exception {
        MvcResult res = mockMvc.perform(post("/api/v1/home/ai-news/" + newsId + "/view")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + token))
                .andExpect(status().isOk())
                .andReturn();
        return objectMapper.readTree(res.getResponse().getContentAsString())
                .get("data").get("viewCount").asInt();
    }

    @Test
    @DisplayName("local-h2: view is idempotent per user — 2nd POST keeps count at 1, detail agrees")
    void viewIsIdempotentPerUser() throws Exception {
        String token = adminToken();
        long newsId = importNews(token);

        // First view → count 1.
        assertThat(postView(token, newsId)).isEqualTo(1);
        // Second view (same user) → WHERE NOT EXISTS guard → no-op, still 1.
        assertThat(postView(token, newsId)).isEqualTo(1);

        // The denormalized counter on ai_news (detail endpoint) must agree
        // with the view log — recompute, not +1.
        MvcResult detailRes = mockMvc.perform(get("/api/v1/home/ai-news/" + newsId)
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + token))
                .andExpect(status().isOk())
                .andReturn();
        JsonNode detail = objectMapper.readTree(detailRes.getResponse().getContentAsString()).get("data");
        assertThat(detail.get("viewCount").asInt()).isEqualTo(1);
    }
}
