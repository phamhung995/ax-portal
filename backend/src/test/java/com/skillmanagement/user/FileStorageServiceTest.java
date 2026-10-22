package com.skillmanagement.user;

import com.skillmanagement.user.service.FileStorageService;
import com.skillmanagement.user.service.impl.FileStorageServiceImpl;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.mock.web.MockMultipartFile;

import java.io.IOException;
import java.nio.file.Path;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class FileStorageServiceTest {

    private FileStorageService fileStorageService;

    @BeforeEach
    void setUp(@TempDir Path tempDir) {
        fileStorageService = new FileStorageServiceImpl(tempDir.toString(), "/uploads/avatars");
    }

    @Test
    void shouldStoreValidAvatar() throws IOException {
        MockMultipartFile file = new MockMultipartFile(
                "avatar", "test.png", "image/png", "fake image content".getBytes()
        );

        String url = fileStorageService.storeAvatar(1L, file, null);
        assertThat(url).startsWith("/uploads/avatars/avatar_1_");
        assertThat(url).endsWith(".png");
    }

    @Test
    void shouldRejectInvalidExtension() {
        MockMultipartFile file = new MockMultipartFile(
                "avatar", "test.exe", "application/octet-stream", "bad content".getBytes()
        );

        assertThatThrownBy(() -> fileStorageService.storeAvatar(1L, file, null))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Định dạng file không hợp lệ");
    }

    @Test
    void shouldRejectEmptyFile() {
        MockMultipartFile file = new MockMultipartFile(
                "avatar", "test.png", "image/png", new byte[0]
        );

        assertThatThrownBy(() -> fileStorageService.storeAvatar(1L, file, null))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("không được để trống");
    }
}
