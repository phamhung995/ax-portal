package com.furelise.skillmanagement.ainews.email;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Parses the REAL .mht sample exported from Knox
 * (20261013_[Did_You_Know]_AI_Helps_In_5_Five-Steps_of_SR_Handling_Journey.mht,
 * 3.2MB — multipart/related, UTF-8 encoded-word subject, 2 CID images
 * incl. one 2.3MB png).
 */
class MhtEmailParserTest {

    private final MhtEmailParser parser = new MhtEmailParser();

    private InputStream sample() {
        try {
            return Files.newInputStream(Path.of("src/test/resources/mht/sample.mht"));
        } catch (IOException e) {
            throw new IllegalStateException("sample.mht missing", e);
        }
    }

    @Test
    @DisplayName("parses headers of the real sample (encoded-word subject, RFC822 date)")
    void parsesRealSampleHeaders() {
        EmailRecord record = parser.parse(sample());
        assertThat(record.subject())
                .isEqualTo("[Did You Know] AI Helps In 5 Five-Steps of SR Handling Journey 👀");
        assertThat(record.from()).isEqualTo("Nguyen Thi Hoang Nhung <nth.nhung3@samsung.com>");
        assertThat(record.date()).isEqualTo(Instant.parse("2026-10-16T14:06:54Z"));
        assertThat(record.messageId()).contains("20261013024539epcms5p190fab6a7cb2148996817700ccc999e91@epcms5p1");
    }

    @Test
    @DisplayName("parses html body + both CID images (incl. the 2.3MB one)")
    void parsesRealSampleBodyAndImages() {
        EmailRecord record = parser.parse(sample());
        assertThat(record.bodyHtml()).isNotBlank();
        assertThat(record.bodyHtml()).contains("cid:cafe_image_0@s-core.co.kr");
        assertThat(record.cidImages()).hasSize(2);
        assertThat(record.cidImages()).containsKey("cafe_image_0@s-core.co.kr");
        assertThat(record.cidImages()).containsKey("20261013024539_0@epcms5p");
        // The large banner image must survive parsing (no size cap here).
        assertThat(record.cidImages().get("cafe_image_0@s-core.co.kr").length)
                .isGreaterThan(2 * 1024 * 1024);
    }

    @Test
    @DisplayName("rejects non-MIME content")
    void rejectsGarbage() {
        byte[] garbage = "this is not an mht file".getBytes(StandardCharsets.UTF_8);
        assertThatThrownBy(() -> parser.parse(new ByteArrayInputStream(garbage)))
                .isInstanceOf(MhtParseException.class);
    }
}
