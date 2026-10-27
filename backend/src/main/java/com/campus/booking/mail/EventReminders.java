package com.campus.booking.mail;

import com.campus.booking.model.Event;
import com.campus.booking.model.EventStatus;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.data.mongodb.core.query.Update;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * Every 15 minutes: approved events starting within the next 24 hours get one reminder to everyone confirmed.
 * Each event is claimed with a conditional update first, so a reminder is never sent twice, even with two servers.
 */
@Component
public class EventReminders {

    private static final Logger log = LoggerFactory.getLogger(EventReminders.class);

    private final MongoTemplate mongo;
    private final Notifications notifications;
    private final Clock clock;
    private final boolean enabled;

    public EventReminders(MongoTemplate mongo, Notifications notifications, Clock clock,
                          @Value("${app.mail.reminders-enabled:true}") boolean enabled) {
        this.mongo = mongo;
        this.notifications = notifications;
        this.clock = clock;
        this.enabled = enabled;
    }

    @Scheduled(initialDelayString = "PT2M", fixedDelayString = "PT15M")
    public void run() {
        if (!enabled) {
            return;
        }
        try {
            sendDue();
        } catch (RuntimeException e) {
            log.warn("Reminder run failed: {}", e.toString());
        }
    }

    /** Returns how many events were reminded about. */
    public int sendDue() {
        Instant now = Instant.now(clock);
        List<Event> due = mongo.find(Query.query(Criteria.where("status").is(EventStatus.APPROVED)
                .and("start").gt(now).lte(now.plus(Duration.ofHours(24)))
                .and("reminderSentAt").is(null)), Event.class);
        int sent = 0;
        for (Event ev : due) {
            var claimed = mongo.updateFirst(
                    Query.query(Criteria.where("id").is(ev.getId()).and("reminderSentAt").is(null)),
                    new Update().set("reminderSentAt", now), Event.class);
            if (claimed.getModifiedCount() == 1) {
                notifications.remind(ev);
                sent++;
            }
        }
        return sent;
    }
}
