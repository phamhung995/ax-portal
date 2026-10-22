package com.skillmanagement.mail;

import com.skillmanagement.mail.KnoxMailClient;
import com.skillmanagement.mail.KnoxMailException;
import com.skillmanagement.model.EmailManagement;
import com.skillmanagement.repository.EmailRepository;
import com.skillmanagement.mail.impl.KnoxMailServiceImpl;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class KnoxMailServiceTest {

    @Mock
    private KnoxMailClient mailClient;

    @Mock
    private EmailRepository emailRepository;

    private KnoxMailServiceImpl service;

    @BeforeEach
    void setUp() {
        service = new KnoxMailServiceImpl(mailClient, emailRepository,
                "tq.dat1@stage.samsung.com", "tq.dat1");
    }

    @Test
    @DisplayName("sendOtpEmail success: records SENT, calls client with sender knox id and recipient")
    void testSendOtpEmailSuccess() {
        when(emailRepository.save(any(EmailManagement.class))).thenAnswer(i -> i.getArgument(0));
        when(mailClient.sendMail(anyString(), anyString(), anyString(), anyString(), anyString(), anyList()))
                .thenReturn("mail-1");

        service.sendOtpEmail("user@samsung.com", "EMAIL_VERIFICATION", "123456", "Verify your email");

        verify(mailClient).sendMail(eq("tq.dat1"), eq("Verify your email"),
                argThat(body -> body.contains("123456") && body.contains("<!DOCTYPE html>")),
                eq("HTML"),
                eq("tq.dat1@stage.samsung.com"), eq(java.util.List.of("user@samsung.com")));

        ArgumentCaptor<EmailManagement> captor = ArgumentCaptor.forClass(EmailManagement.class);
        verify(emailRepository, atLeast(2)).save(captor.capture());
        EmailManagement last = captor.getAllValues().get(captor.getAllValues().size() - 1);
        assertEquals("SENT", last.getStatus());
        assertNotNull(last.getSentAt());
        assertNull(last.getErrorCode());
    }

    @Test
    @DisplayName("sendOtpEmail failure: records FAILED with KNOX_MAIL_SEND_FAILED and error message")
    void testSendOtpEmailFailure() {
        when(emailRepository.save(any(EmailManagement.class))).thenAnswer(i -> i.getArgument(0));
        when(mailClient.sendMail(anyString(), anyString(), anyString(), anyString(), anyString(), anyList()))
                .thenThrow(new KnoxMailException("ML1002: The user information does not exist."));

        service.sendOtpEmail("user@samsung.com", "PASSWORD_RESET", "654321", "Reset your password");

        ArgumentCaptor<EmailManagement> captor = ArgumentCaptor.forClass(EmailManagement.class);
        verify(emailRepository, atLeast(2)).save(captor.capture());
        EmailManagement last = captor.getAllValues().get(captor.getAllValues().size() - 1);
        assertEquals("FAILED", last.getStatus());
        assertEquals("KNOX_MAIL_SEND_FAILED", last.getErrorCode());
        assertTrue(last.getErrorMessage().contains("ML1002"));
    }

    @Test
    @DisplayName("sendNotificationEmail success: records SENT with SKILL_NOTIFICATION type")
    void testSendNotificationEmailSuccess() {
        when(emailRepository.save(any(EmailManagement.class))).thenAnswer(i -> i.getArgument(0));
        when(mailClient.sendMail(anyString(), anyString(), anyString(), anyString(), anyString(), anyList()))
                .thenReturn("mail-2");

        service.sendNotificationEmail("user@samsung.com", "Skill approved", "Your skill was approved");

        verify(mailClient).sendMail(eq("tq.dat1"), eq("Skill approved"),
                eq("Your skill was approved"),
                eq("TEXT"),
                eq("tq.dat1@stage.samsung.com"), eq(java.util.List.of("user@samsung.com")));

        ArgumentCaptor<EmailManagement> captor = ArgumentCaptor.forClass(EmailManagement.class);
        verify(emailRepository, atLeast(2)).save(captor.capture());
        EmailManagement last = captor.getAllValues().get(captor.getAllValues().size() - 1);
        assertEquals("SKILL_NOTIFICATION", last.getEmailType());
        assertEquals("SENT", last.getStatus());
    }

    @Test
    @DisplayName("sendNotificationEmail failure: error message truncated to 500 chars")
    void testSendNotificationEmailFailureTruncatesMessage() {
        when(emailRepository.save(any(EmailManagement.class))).thenAnswer(i -> i.getArgument(0));
        when(mailClient.sendMail(anyString(), anyString(), anyString(), anyString(), anyString(), anyList()))
                .thenThrow(new KnoxMailException("x".repeat(800)));

        service.sendNotificationEmail("user@samsung.com", "s", "b");

        ArgumentCaptor<EmailManagement> captor = ArgumentCaptor.forClass(EmailManagement.class);
        verify(emailRepository, atLeast(2)).save(captor.capture());
        EmailManagement last = captor.getAllValues().get(captor.getAllValues().size() - 1);
        assertEquals("FAILED", last.getStatus());
        assertEquals(500, last.getErrorMessage().length());
    }
}
