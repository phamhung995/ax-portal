package com.skillmanagement.desktop.config;

import com.skillmanagement.desktop.config.DesktopDatabaseGuard;
import org.junit.jupiter.api.Test;
import static org.assertj.core.api.Assertions.*;

class DesktopDatabaseGuardTest {
    @Test void guard_is_registered_with_spring_before_context_beans_are_created() {
        assertThat(org.springframework.core.io.support.SpringFactoriesLoader.loadFactoryNames(org.springframework.context.ApplicationContextInitializer.class,getClass().getClassLoader())).contains(DesktopDatabaseGuard.class.getName());
    }
    @Test void refuses_wrong_database_remote_urls_and_marker_before_startup() {
        for(String url:new String[]{"jdbc:postgresql://localhost:5432/skill_management_db","jdbc:postgresql://remote:5432/sds_desktop_pilot_test","jdbc:postgresql://127.0.0.1:5432/sds_desktop_pilot_test?currentSchema=public"})
            assertThatThrownBy(()->DesktopDatabaseGuard.parse(url)).isInstanceOf(IllegalStateException.class);
        var expected=DesktopDatabaseGuard.parse("jdbc:postgresql://127.0.0.1:15432/sds_desktop_pilot_test");
        assertThatCode(()->DesktopDatabaseGuard.check(expected,"sds_desktop_pilot_test","127.0.0.1",15432,"SDS_DESKTOP_PILOT_ISOLATED_TEST","SDS_DESKTOP_PILOT_ISOLATED_TEST")).doesNotThrowAnyException();
        assertThatThrownBy(()->DesktopDatabaseGuard.check(expected,"sds_desktop_pilot_test","127.0.0.1",15432,null,"SDS_DESKTOP_PILOT_ISOLATED_TEST")).isInstanceOf(IllegalStateException.class);
        assertThatThrownBy(()->DesktopDatabaseGuard.check(expected,"sds_desktop_pilot_test","127.0.0.1",5432,"SDS_DESKTOP_PILOT_ISOLATED_TEST","SDS_DESKTOP_PILOT_ISOLATED_TEST")).isInstanceOf(IllegalStateException.class);
    }
}
