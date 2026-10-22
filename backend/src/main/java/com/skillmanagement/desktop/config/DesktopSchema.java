package com.skillmanagement.desktop.config;

import org.flywaydb.core.Flyway;
import org.springframework.beans.factory.InitializingBean;
import org.springframework.context.annotation.DependsOn;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;
import javax.sql.DataSource;

/** Separate Flyway history; never replaces the upstream Flyway bean. */
@Component
@Profile("desktop-pilot")
@DependsOn("flywayInitializer")
public class DesktopSchema implements InitializingBean {
    private final DataSource dataSource;
    public DesktopSchema(DataSource dataSource) { this.dataSource=dataSource; }
    @Override public void afterPropertiesSet() {
        Flyway.configure().dataSource(dataSource).locations("classpath:db/desktop-migration")
            .table("desktop_schema_history").baselineOnMigrate(true).baselineVersion("0").load().migrate();
    }
}
