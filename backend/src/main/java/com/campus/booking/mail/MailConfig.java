package com.campus.booking.mail;

import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableAsync;
import org.springframework.scheduling.annotation.EnableScheduling;

/** Emails go out on background threads; event reminders run on a schedule. */
@Configuration
@EnableAsync
@EnableScheduling
public class MailConfig {
}
