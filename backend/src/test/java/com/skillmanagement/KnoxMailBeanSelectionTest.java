package com.skillmanagement;

import com.skillmanagement.mail.EmailService;
import com.skillmanagement.mail.impl.KnoxMailServiceImpl;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.ApplicationContext;
import org.springframework.test.context.ActiveProfiles;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Verifies the EmailService bean selection: with knox.mail.system-id set,
 * exactly one EmailService bean exists and it is the Knox implementation
 * (dev-log implementation is disabled).
 */
@SpringBootTest(properties = {
        "knox.mail.system-id=TEST123",
        "knox.mail.access-token=test-token",
        "knox.mail.sender-email=tester@stage.samsung.com",
        "knox.mail.sender-knox-id=tester"
})
@ActiveProfiles("test")
class KnoxMailBeanSelectionTest {

    @Autowired
    private ApplicationContext context;

    @Test
    void exactlyOneEmailServiceBean_isKnox() {
        int count = context.getBeanNamesForType(EmailService.class).length;
        assertEquals(1, count, "Exactly one EmailService bean must be active");
        assertInstanceOf(KnoxMailServiceImpl.class, context.getBean(EmailService.class));
    }
}
