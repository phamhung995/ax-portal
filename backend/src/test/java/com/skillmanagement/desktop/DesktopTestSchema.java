package com.skillmanagement.desktop;

import org.springframework.core.io.ByteArrayResource;
import org.springframework.core.io.ClassPathResource;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.init.ResourceDatabasePopulator;
import java.nio.charset.StandardCharsets;

final class DesktopTestSchema {
    static void create(JdbcTemplate jdbc) throws java.io.IOException {
        for(String script:new String[]{"V1__desktop_sources.sql","V2__desktop_releases.sql"}) {
            var bytes=new ClassPathResource("db/desktop-migration/"+script).getInputStream().readAllBytes();
            String sql=new String(bytes,StandardCharsets.UTF_8).replace("CREATE TABLE ","CREATE TABLE IF NOT EXISTS ").replace("CREATE INDEX ","CREATE INDEX IF NOT EXISTS ");
            new ResourceDatabasePopulator(new ByteArrayResource(sql.getBytes(StandardCharsets.UTF_8))).execute(jdbc.getDataSource());
        }
    }
}
