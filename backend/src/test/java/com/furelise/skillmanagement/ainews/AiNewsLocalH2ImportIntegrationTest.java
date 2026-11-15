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
 * End-to-end verification of the {@code local-h2} DB-safe test profile:
 * boots the FULL app on in-memory H2 (Flyway off, schema from JPA), logs in
 * as the seeded admin, imports the REAL Knox sample .mht through the HTTP
 * layer, and confirms the news persisted with the 2.3MB CID image inlined
 * as a data-URI. Nothing is written to the real PostgreSQL.
 *
 * <p>This is the safe way to test the AI News import pipeline (the exact
 * {@code MhtEmailParser} path the Knox portal batch now shares) without a
 * live portal or a real database.</p>
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("local-h2")
class AiNewsLocalH2ImportIntegrationTest {

    @Autowired
    private MockMvc mockMvc;
    @Autowired
    private ObjectMapper objectMapper;

    /** Keep the image store out of the repo's uploads/ folder. */
    @DynamicPropertySource
    static void imageDir(DynamicPropertyRegistry registry) throws Exception {
        Path tmp = Files.createTempDirectory("ainews-h2-images");
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
    @DisplayName("local-h2: admin login + .mht import persists news with inlined CID image (no real DB)")
    void importsRealSampleOnH2() throws Exception {
        String token = adminToken();

        // Import the real 3.2MB Knox sample (2 CID images incl. one 2.3MB).
        MvcResult importRes = mockMvc.perform(multipart("/api/v1/admin/ai-news/import")
                        .file(new MockMultipartFile("files", "sample.mht",
                                "message/rfc822", sampleBytes()))
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + token))
                .andExpect(status().isOk())
                .andReturn();

        JsonNode results = objectMapper.readTree(importRes.getResponse().getContentAsString())
                .get("data");
        assertThat(results.isArray()).isTrue();
        JsonNode first = results.get(0);
        assertThat(first.get("status").asText()).isEqualTo("INSERTED");
        long newsId = first.get("newsId").asLong();
        assertThat(newsId).isPositive();

        // Fetch the detail and confirm the 2.3MB CID image was inlined as a
        // data-URI (the store's 2MB disk cap pushes it to inlineOversizedCidImages).
        MvcResult detailRes = mockMvc.perform(get("/api/v1/home/ai-news/{id}", newsId)
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + token))
                .andExpect(status().isOk())
                .andReturn();
        JsonNode detail = objectMapper.readTree(detailRes.getResponse().getContentAsString()).get("data");
        assertThat(detail.get("title").asText())
                .isEqualTo("AI Helps In 5 Five-Steps of SR Handling Journey 👀");
        assertThat(detail.get("body").asText()).contains("data:image/png;base64,");
    }
}
