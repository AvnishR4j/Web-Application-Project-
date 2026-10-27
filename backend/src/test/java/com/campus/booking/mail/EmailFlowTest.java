package com.campus.booking.mail;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.Mockito.atLeastOnce;
import static org.mockito.Mockito.clearInvocations;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.campus.booking.dto.AuthDtos.AuthResponse;
import com.campus.booking.exception.ApiExceptions.ForbiddenException;
import com.campus.booking.exception.ApiExceptions.UnauthorizedException;
import com.campus.booking.mail.Mailer.Message;
import com.campus.booking.model.Event;
import com.campus.booking.model.EventStatus;
import com.campus.booking.model.Registration;
import com.campus.booking.model.RegistrationStatus;
import com.campus.booking.model.Role;
import com.campus.booking.model.User;
import com.campus.booking.repository.EventRepository;
import com.campus.booking.repository.RegistrationRepository;
import com.campus.booking.repository.UserRepository;
import com.campus.booking.security.EmailAuth;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.mockito.ArgumentCaptor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.security.crypto.password.PasswordEncoder;

/** Sign-in codes, password reset and reminders against a throwaway database, with email captured instead of sent. */
@SpringBootTest(properties = "app.mail.reminders-enabled=false")
@EnabledIfEnvironmentVariable(named = "MONGODB_URI", matches = ".*/campus_(ci|test)\\b.*")
class EmailFlowTest {

    @MockBean Mailer mailer;
    @Autowired EmailAuth emailAuth;
    @Autowired EventReminders reminders;
    @Autowired UserRepository users;
    @Autowired EventRepository events;
    @Autowired RegistrationRepository registrations;
    @Autowired PasswordEncoder encoder;

    @BeforeEach
    void mailIsOn() {
        when(mailer.enabled()).thenReturn(true);
    }

    private static String fresh() {
        return "t" + UUID.randomUUID().toString().substring(0, 8) + "@thapar.edu";
    }

    private Message lastMessage() {
        ArgumentCaptor<Message> one = ArgumentCaptor.forClass(Message.class);
        verify(mailer, atLeastOnce()).send(one.capture());
        return one.getValue();
    }

    @Test
    void signInCodeWorksOnceAndOnlyForCollegeEmails() {
        assertThrows(ForbiddenException.class, () -> emailAuth.requestCode("someone@gmail.com"));
        assertThrows(ForbiddenException.class, () -> emailAuth.requestCode("x@evil-thapar.edu"));

        String email = fresh();
        emailAuth.requestCode(email.toUpperCase());
        Message sent = lastMessage();
        assertEquals(email, sent.to());
        Matcher m = Pattern.compile("(\\d{6})$").matcher(sent.subject());
        assertTrue(m.find(), sent.subject());
        String code = m.group(1);
        String wrong = code.equals("000000") ? "111111" : "000000";

        assertThrows(UnauthorizedException.class, () -> emailAuth.verifyCode(email, wrong));
        AuthResponse res = emailAuth.verifyCode(email, code);
        assertNotNull(res.token());
        assertEquals(Role.STUDENT, res.user().role());
        assertThrows(UnauthorizedException.class, () -> emailAuth.verifyCode(email, code)); // used up
    }

    @Test
    void askingAgainWithinAMinuteSendsNothingAndFiveWrongTriesLockTheCode() {
        String email = fresh();
        emailAuth.requestCode(email);
        clearInvocations(mailer);
        emailAuth.requestCode(email);
        verify(mailer, never()).send(any(Message.class));

        for (int i = 0; i < 5; i++) {
            assertThrows(UnauthorizedException.class, () -> emailAuth.verifyCode(email, "abcdef"));
        }
        UnauthorizedException locked = assertThrows(UnauthorizedException.class, () -> emailAuth.verifyCode(email, "123456"));
        assertTrue(locked.getMessage().contains("Too many"), locked.getMessage());
    }

    @Test
    void passwordResetLinkWorksOnceAndUnknownEmailsGetNothing() {
        clearInvocations(mailer);
        emailAuth.requestReset(fresh()); // no such account: silent
        verify(mailer, never()).send(any(Message.class));

        String email = fresh();
        users.save(new User("Asha", email, encoder.encode("old-password-1"), Role.STUDENT, "CSE"));
        emailAuth.requestReset(email);
        Matcher m = Pattern.compile("token=([A-Za-z0-9_-]+)").matcher(lastMessage().html());
        assertTrue(m.find());
        String token = m.group(1);

        assertThrows(RuntimeException.class, () -> emailAuth.resetPassword(token, "short"));
        emailAuth.resetPassword(token, "new-password-2");
        assertTrue(encoder.matches("new-password-2", users.findByEmail(email).orElseThrow().getPasswordHash()));
        assertThrows(UnauthorizedException.class, () -> emailAuth.resetPassword(token, "another-password-3"));
    }

    @Test
    void dayBeforeReminderGoesOutOnce() {
        String email = fresh();
        Event e = new Event();
        e.setClubId("club-" + UUID.randomUUID());
        e.setTitle("Reminder test " + UUID.randomUUID());
        e.setResourceId("nowhere");
        e.setStart(Instant.now().plus(Duration.ofHours(3)));
        e.setEnd(Instant.now().plus(Duration.ofHours(5)));
        e.setExpectedAttendees(10);
        e.setStatus(EventStatus.APPROVED);
        e = events.save(e);
        Registration r = new Registration();
        r.setEventId(e.getId());
        r.setUserEmail(email);
        r.setStatus(RegistrationStatus.CONFIRMED);
        r.setConfirmedAt(Instant.now());
        registrations.save(r);

        clearInvocations(mailer);
        reminders.sendDue();
        @SuppressWarnings("unchecked")
        ArgumentCaptor<List<Message>> batch = ArgumentCaptor.forClass(List.class);
        verify(mailer, atLeastOnce()).send(batch.capture());
        String title = e.getTitle();
        assertTrue(batch.getAllValues().stream().flatMap(List::stream)
                .anyMatch((m) -> m.to().equals(email) && m.subject().equals("Tomorrow: " + title)));
        assertNotNull(events.findById(e.getId()).orElseThrow().getReminderSentAt());

        clearInvocations(mailer);
        reminders.sendDue();
        verify(mailer, never()).send(anyList());
    }
}
