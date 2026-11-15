package com.furelise.skillmanagement.update;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.furelise.skillmanagement.update.controller.UpdateController;
import com.furelise.skillmanagement.update.dto.UpdateManifest;
import com.furelise.skillmanagement.update.model.AppRelease;
import com.furelise.skillmanagement.update.service.UpdateService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.Map;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@ExtendWith(MockitoExtension.class)
class UpdateControllerTest {

    private MockMvc mockMvc;
    private final ObjectMapper objectMapper = new ObjectMapper();

    @Mock private UpdateService updateService;
    @InjectMocks private UpdateController controller;

    @BeforeEach
    void setUp() {
        mockMvc = MockMvcBuilders.standaloneSetup(controller).build();
    }

    private AppRelease sampleRelease(String version) {
        AppRelease r = new AppRelease();
        r.setId(1L);
        r.setVersion(version);
        r.setNsisZipName("test_" + version + ".nsis.zip");
        r.setNsisZipPath("/tmp/test.zip");
        r.setNsisZipSize(1024);
        r.setSignature("sig==");
        r.setReleaseNotes("notes");
        r.setPublishedAt(OffsetDateTime.now());
        r.setActive(true);
        return r;
    }

    @Test
    void getLatest_returns404WhenNoRelease() throws Exception {
        when(updateService.getLatestManifest()).thenReturn(null);
        mockMvc.perform(get("/api/v1/update/latest"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.errorCode").value("NO_RELEASE"));
    }

    @Test
    void getLatest_returnsManifestJson() throws Exception {
        UpdateManifest manifest = new UpdateManifest(
                "1.20.20", "notes", "2026-11-09T08:00:00Z",
                Map.of("windows-x86_64",
                        new UpdateManifest.PlatformAsset("sig==",
                                "http://localhost:8080/api/v1/update/download/1.20.20"))
        );
        when(updateService.getLatestManifest()).thenReturn(manifest);
        mockMvc.perform(get("/api/v1/update/latest"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.version").value("1.20.20"))
                .andExpect(jsonPath("$.notes").value("notes"))
                .andExpect(jsonPath("$.pub_date").exists())
                .andExpect(jsonPath("$.platforms.windows-x86_64.signature").value("sig=="))
                .andExpect(jsonPath("$.platforms.windows-x86_64.url").exists());
    }

    @Test
    void download_returns404WhenVersionNotFound() throws Exception {
        when(updateService.getByVersion("9.9.9")).thenReturn(null);
        mockMvc.perform(get("/api/v1/update/download/9.9.9"))
                .andExpect(status().isNotFound());
    }

    @Test
    void download_returns404WhenFileMissing() throws Exception {
        AppRelease r = sampleRelease("1.20.20");
        when(updateService.getByVersion("1.20.20")).thenReturn(r);
        when(updateService.getReleaseResource(r)).thenReturn(null);
        mockMvc.perform(get("/api/v1/update/download/1.20.20"))
                .andExpect(status().isNotFound());
    }

    @Test
    void download_streamsBinaryWhenFound() throws Exception {
        AppRelease r = sampleRelease("1.20.20");
        when(updateService.getByVersion("1.20.20")).thenReturn(r);
        when(updateService.getReleaseResource(r))
                .thenReturn(new ByteArrayResource(new byte[]{0x50, 0x4B, 0x03, 0x04}));
        mockMvc.perform(get("/api/v1/update/download/1.20.20"))
                .andExpect(status().isOk())
                .andExpect(header().string("Content-Type", "application/octet-stream"))
                .andExpect(header().exists("Content-Disposition"))
                .andExpect(content().bytes(new byte[]{0x50, 0x4B, 0x03, 0x04}));
    }

    @Test
    void upload_returns400WhenFileEmpty() throws Exception {
        MockMultipartFile empty = new MockMultipartFile("nsisZip", "", "application/octet-stream", new byte[0]);
        mockMvc.perform(multipart("/api/v1/update/releases")
                        .file(empty)
                        .param("version", "1.20.20")
                        .param("signature", "sig=="))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errorCode").value("EMPTY_FILE"));
    }

    @Test
    void upload_returns400WhenVersionMissing() throws Exception {
        MockMultipartFile file = new MockMultipartFile("nsisZip", "test.zip", "application/octet-stream",
                new byte[]{0x50, 0x4B});
        mockMvc.perform(multipart("/api/v1/update/releases")
                        .file(file)
                        .param("signature", "sig=="))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errorCode").value("MISSING_VERSION"));
    }

    @Test
    void upload_returns400WhenSignatureMissing() throws Exception {
        MockMultipartFile file = new MockMultipartFile("nsisZip", "test.zip", "application/octet-stream",
                new byte[]{0x50, 0x4B});
        mockMvc.perform(multipart("/api/v1/update/releases")
                        .file(file)
                        .param("version", "1.20.20"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errorCode").value("MISSING_SIGNATURE"));
    }

    @Test
    void upload_succeedsWithValidInput() throws Exception {
        MockMultipartFile file = new MockMultipartFile("nsisZip", "test.zip", "application/octet-stream",
                new byte[]{0x50, 0x4B, 0x03, 0x04});
        AppRelease saved = sampleRelease("1.20.20");
        when(updateService.uploadRelease(eq("1.20.20"), eq("sig=="), eq("notes"), any()))
                .thenReturn(saved);
        mockMvc.perform(multipart("/api/v1/update/releases")
                        .file(file)
                        .param("version", "1.20.20")
                        .param("signature", "sig==")
                        .param("notes", "notes"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.version").value("1.20.20"));
    }

    @Test
    void delete_returns404WhenNotFound() throws Exception {
        when(updateService.deactivateRelease("9.9.9")).thenReturn(false);
        mockMvc.perform(delete("/api/v1/update/releases/9.9.9"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.errorCode").value("NOT_FOUND"));
    }

    @Test
    void delete_succeedsWhenFound() throws Exception {
        when(updateService.deactivateRelease("1.20.20")).thenReturn(true);
        mockMvc.perform(delete("/api/v1/update/releases/1.20.20"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data").value(true));
    }

    @Test
    void listReleases_returnsOk() throws Exception {
        when(updateService.listAll())
                .thenReturn(List.of(sampleRelease("1.20.20"), sampleRelease("1.20.19")));
        mockMvc.perform(get("/api/v1/update/releases"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.length()").value(2));
    }
}
