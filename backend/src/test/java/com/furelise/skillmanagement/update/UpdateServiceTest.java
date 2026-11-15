package com.furelise.skillmanagement.update;

import com.furelise.skillmanagement.update.dto.UpdateManifest;
import com.furelise.skillmanagement.update.model.AppRelease;
import com.furelise.skillmanagement.update.repository.AppReleaseRepository;
import com.furelise.skillmanagement.update.service.UpdateService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.api.io.TempDir;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.util.ReflectionTestUtils;

import java.nio.file.Files;
import java.nio.file.Path;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class UpdateServiceTest {

    @Mock private AppReleaseRepository repository;
    @InjectMocks private UpdateService service;

    @TempDir Path tempDir;

    @BeforeEach
    void setUp() {
        ReflectionTestUtils.setField(service, "storageDir", tempDir.toString());
        ReflectionTestUtils.setField(service, "publicBaseUrl", "http://localhost:8080/api/v1/update");
    }

    private AppRelease sampleRelease(String version) {
        AppRelease r = new AppRelease();
        r.setId(1L);
        r.setVersion(version);
        r.setChannel("stable");
        r.setNsisZipName("AIPRO_STUDIO_" + version + "_x64-setup.nsis.zip");
        r.setNsisZipPath(tempDir.resolve("test.zip").toString());
        r.setNsisZipSize(1024);
        r.setSignature("dummy-signature-base64==");
        r.setReleaseNotes("test notes");
        r.setPublishedAt(OffsetDateTime.now());
        r.setActive(true);
        return r;
    }

    @Test
    void getLatestManifest_returnsNullWhenNoRelease() {
        when(repository.findFirstByIsActiveTrueOrderByPublishedAtDesc())
                .thenReturn(Optional.empty());
        assertNull(service.getLatestManifest());
    }

    @Test
    void getLatestManifest_buildsCorrectFormat() {
        AppRelease r = sampleRelease("1.20.20");
        when(repository.findFirstByIsActiveTrueOrderByPublishedAtDesc())
                .thenReturn(Optional.of(r));

        UpdateManifest m = service.getLatestManifest();

        assertNotNull(m);
        assertEquals("1.20.20", m.version());
        assertEquals("test notes", m.notes());
        assertNotNull(m.pubDate());
        assertNotNull(m.platforms().get("windows-x86_64"));
        UpdateManifest.PlatformAsset asset = m.platforms().get("windows-x86_64");
        assertEquals("dummy-signature-base64==", asset.signature());
        assertEquals("http://localhost:8080/api/v1/update/download/1.20.20", asset.url());
    }

    @Test
    void getByVersion_returnsNullWhenNotFound() {
        when(repository.findByVersionAndIsActiveTrue("9.9.9"))
                .thenReturn(Optional.empty());
        assertNull(service.getByVersion("9.9.9"));
    }

    @Test
    void getByVersion_returnsRelease() {
        AppRelease r = sampleRelease("1.20.20");
        when(repository.findByVersionAndIsActiveTrue("1.20.20"))
                .thenReturn(Optional.of(r));
        AppRelease result = service.getByVersion("1.20.20");
        assertNotNull(result);
        assertEquals("1.20.20", result.getVersion());
    }

    @Test
    void getReleaseResource_returnsNullWhenFileMissing() {
        AppRelease r = sampleRelease("1.20.20");
        r.setNsisZipPath("/nonexistent/path.zip");
        assertNull(service.getReleaseResource(r));
    }

    @Test
    void getReleaseResource_returnsResourceWhenFileExists() throws Exception {
        Path zip = tempDir.resolve("test.zip");
        Files.write(zip, new byte[]{0x50, 0x4B, 0x03, 0x04}); // PK zip magic
        AppRelease r = sampleRelease("1.20.20");
        r.setNsisZipPath(zip.toString());
        assertNotNull(service.getReleaseResource(r));
    }

    @Test
    void uploadRelease_savesFileAndMetadata() throws Exception {
        byte[] content = new byte[]{0x50, 0x4B, 0x03, 0x04, 0x01, 0x02};
        MockMultipartFile file = new MockMultipartFile("nsisZip", "test.nsis.zip",
                "application/octet-stream", content);
        when(repository.findByVersion("1.20.21"))
                .thenReturn(Optional.empty());
        when(repository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        AppRelease result = service.uploadRelease("1.20.21", "sig==", "notes", file);

        assertEquals("1.20.21", result.getVersion());
        assertEquals("sig==", result.getSignature());
        assertEquals(content.length, result.getNsisZipSize());
        assertTrue(Files.exists(tempDir.resolve("test.nsis.zip")));
        verify(repository).save(any());
    }

    @Test
    void uploadRelease_reactivatesSoftDeletedVersion() throws Exception {
        AppRelease existing = sampleRelease("1.20.20");
        existing.setActive(false); // soft-deleted
        byte[] content = new byte[]{0x50, 0x4B, 0x03, 0x04};
        MockMultipartFile file = new MockMultipartFile("nsisZip", "test.nsis.zip",
                "application/octet-stream", content);
        when(repository.findByVersion("1.20.20"))
                .thenReturn(Optional.of(existing));
        when(repository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        AppRelease result = service.uploadRelease("1.20.20", "new-sig==", "new notes", file);

        assertEquals("1.20.20", result.getVersion());
        assertEquals("new-sig==", result.getSignature());
        assertTrue(result.isActive(), "should be reactivated");
        verify(repository).save(existing);
    }

    @Test
    void deactivateRelease_returnsFalseWhenNotFound() {
        when(repository.findByVersionAndIsActiveTrue("9.9.9"))
                .thenReturn(Optional.empty());
        assertFalse(service.deactivateRelease("9.9.9"));
    }

    @Test
    void deactivateRelease_setsInactiveAndSaves() {
        AppRelease r = sampleRelease("1.20.20");
        when(repository.findByVersionAndIsActiveTrue("1.20.20"))
                .thenReturn(Optional.of(r));
        when(repository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        assertTrue(service.deactivateRelease("1.20.20"));
        assertFalse(r.isActive());
        verify(repository).save(r);
    }

    @Test
    void listAll_delegatesToRepository() {
        AppRelease r1 = sampleRelease("1.20.20");
        AppRelease r2 = sampleRelease("1.20.19");
        when(repository.findAllByIsActiveTrueOrderByPublishedAtDesc())
                .thenReturn(List.of(r1, r2));

        List<AppRelease> result = service.listAll();
        assertEquals(2, result.size());
        assertEquals("1.20.20", result.get(0).getVersion());
    }
}
