package com.skillmanagement.desktop.config;

import org.springframework.context.ApplicationContextInitializer;
import org.springframework.context.ConfigurableApplicationContext;
import java.sql.DriverManager;
import java.util.regex.Pattern;

/** Runs before bean creation, including upstream Flyway or data seeding. */
public final class DesktopDatabaseGuard implements ApplicationContextInitializer<ConfigurableApplicationContext> {
    private static final Pattern URL=Pattern.compile("^jdbc:postgresql://(127\\.0\\.0\\.1|localhost):([0-9]{1,5})/(sds_desktop_pilot_[a-z0-9_]+)$");
    record ConnectionInfo(String database,int port) {}
    static ConnectionInfo parse(String url) {
        var matcher=URL.matcher(url==null?"":url);
        if(!matcher.matches())throw refused();
        int port=Integer.parseInt(matcher.group(2));
        if(port<1 || port>65535)throw refused();
        return new ConnectionInfo(matcher.group(3),port);
    }
    static void check(ConnectionInfo expected,String database,String host,int port,String actualMarker,String marker) {
        if(marker==null || !marker.matches("SDS_DESKTOP_PILOT_ISOLATED_[A-Z0-9_]+") || !marker.equals(actualMarker) || !expected.database().equals(database) || expected.port()!=port || !java.util.List.of("127.0.0.1","::1").contains(host))throw refused();
    }
    @Override public void initialize(ConfigurableApplicationContext context) {
        var env=context.getEnvironment();
        if(!env.acceptsProfiles(org.springframework.core.env.Profiles.of("desktop-pilot")))return;
        String url=env.getProperty("spring.datasource.url");
        var expected=parse(url);
        String username=env.getProperty("spring.datasource.username"), password=env.getProperty("spring.datasource.password");
        if(username==null || username.isBlank() || password==null || password.isBlank())throw refused();
        try(var connection=DriverManager.getConnection(url,username,password);var statement=connection.createStatement();var row=statement.executeQuery("SELECT current_database(),host(inet_server_addr()),inet_server_port(),shobj_description(oid,'pg_database') FROM pg_database WHERE datname=current_database()")) {
            if(!row.next())throw refused();
            check(expected,row.getString(1),row.getString(2),row.getInt(3),row.getString(4),env.getProperty("desktop.pilot.marker"));
        } catch(java.sql.SQLException unavailable) { throw refused(); }
    }
    private static IllegalStateException refused() { return new IllegalStateException("Dedicated desktop pilot database identity/marker could not be verified. Startup stopped before migrations or seeding."); }
}
