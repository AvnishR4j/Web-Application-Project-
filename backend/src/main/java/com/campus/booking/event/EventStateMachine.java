package com.campus.booking.event;

import com.campus.booking.model.EventStatus;
import java.time.Duration;
import java.time.Instant;
import java.util.EnumSet;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

/**
 * Which actions are allowed in which state, and where they lead. Pure, so it can be tested on its own.
 *
 * <pre>
 * DRAFT --submit--> SUBMITTED --advisor approve--> ADVISOR_APPROVED --manager approve--> APPROVED --complete--> COMPLETED
 *                      |                                 |
 *                advisor reject                    manager reject
 *                      v                                 v
 *                  REJECTED  --edit--> DRAFT        REJECTED
 * Editing a SUBMITTED, ADVISOR_APPROVED or APPROVED event sends it back to DRAFT (the room is released and it
 * needs approval again). Cancel works from any state before COMPLETED.
 * </pre>
 */
public final class EventStateMachine {

    public enum Action { EDIT, SUBMIT, ADVISOR_APPROVE, ADVISOR_REJECT, MANAGER_APPROVE, MANAGER_REJECT, CANCEL, COMPLETE }

    private static final Set<EventStatus> IN_REVIEW_OR_APPROVED =
            EnumSet.of(EventStatus.SUBMITTED, EventStatus.ADVISOR_APPROVED, EventStatus.APPROVED);

    private static final Map<Action, Set<EventStatus>> ALLOWED_FROM = Map.of(
            Action.EDIT, EnumSet.of(EventStatus.DRAFT, EventStatus.REJECTED,
                    EventStatus.SUBMITTED, EventStatus.ADVISOR_APPROVED, EventStatus.APPROVED),
            Action.SUBMIT, EnumSet.of(EventStatus.DRAFT, EventStatus.REJECTED),
            Action.ADVISOR_APPROVE, EnumSet.of(EventStatus.SUBMITTED),
            Action.ADVISOR_REJECT, EnumSet.of(EventStatus.SUBMITTED),
            Action.MANAGER_APPROVE, EnumSet.of(EventStatus.ADVISOR_APPROVED),
            Action.MANAGER_REJECT, EnumSet.of(EventStatus.ADVISOR_APPROVED),
            Action.CANCEL, EnumSet.of(EventStatus.DRAFT, EventStatus.SUBMITTED,
                    EventStatus.ADVISOR_APPROVED, EventStatus.APPROVED, EventStatus.REJECTED),
            Action.COMPLETE, EnumSet.of(EventStatus.APPROVED));

    private EventStateMachine() {
    }

    /** The state after the action, or empty if the action is not allowed from this state. */
    public static Optional<EventStatus> next(EventStatus from, Action action) {
        if (!ALLOWED_FROM.get(action).contains(from)) {
            return Optional.empty();
        }
        EventStatus to = switch (action) {
            case EDIT -> EventStatus.DRAFT;
            case SUBMIT -> EventStatus.SUBMITTED;
            case ADVISOR_APPROVE -> EventStatus.ADVISOR_APPROVED;
            case ADVISOR_REJECT, MANAGER_REJECT -> EventStatus.REJECTED;
            case MANAGER_APPROVE -> EventStatus.APPROVED;
            case CANCEL -> EventStatus.CANCELLED;
            case COMPLETE -> EventStatus.COMPLETED;
        };
        return Optional.of(to);
    }

    /** True when the event holds a room booking that must be released if it leaves this state. */
    public static boolean holdsRoom(EventStatus status) {
        return IN_REVIEW_OR_APPROVED.contains(status);
    }

    /** Students only ever see approved (and later completed) events. */
    public static boolean isPublic(EventStatus status) {
        return status == EventStatus.APPROVED || status == EventStatus.COMPLETED;
    }

    /** The plan's minimum notice: the event must start at least {@code minLeadDays} after submission. */
    public static boolean meetsLeadTime(Instant start, Instant now, int minLeadDays) {
        return !start.isBefore(now.plus(Duration.ofDays(minLeadDays)));
    }
}
