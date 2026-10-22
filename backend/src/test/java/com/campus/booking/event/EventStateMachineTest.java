package com.campus.booking.event;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.campus.booking.event.EventStateMachine.Action;
import com.campus.booking.model.EventStatus;
import java.time.Duration;
import java.time.Instant;
import java.util.Optional;
import org.junit.jupiter.api.Test;

class EventStateMachineTest {

    private static Optional<EventStatus> next(EventStatus from, Action action) {
        return EventStateMachine.next(from, action);
    }

    @Test
    void happyPathGoesDraftSubmittedAdvisorApprovedApprovedCompleted() {
        assertEquals(Optional.of(EventStatus.SUBMITTED), next(EventStatus.DRAFT, Action.SUBMIT));
        assertEquals(Optional.of(EventStatus.ADVISOR_APPROVED), next(EventStatus.SUBMITTED, Action.ADVISOR_APPROVE));
        assertEquals(Optional.of(EventStatus.APPROVED), next(EventStatus.ADVISOR_APPROVED, Action.MANAGER_APPROVE));
        assertEquals(Optional.of(EventStatus.COMPLETED), next(EventStatus.APPROVED, Action.COMPLETE));
    }

    @Test
    void managerCannotSkipTheAdvisor() {
        assertTrue(next(EventStatus.SUBMITTED, Action.MANAGER_APPROVE).isEmpty());
        assertTrue(next(EventStatus.SUBMITTED, Action.MANAGER_REJECT).isEmpty());
        assertTrue(next(EventStatus.DRAFT, Action.MANAGER_APPROVE).isEmpty());
    }

    @Test
    void advisorActsOnlyOnSubmittedEvents() {
        assertTrue(next(EventStatus.DRAFT, Action.ADVISOR_APPROVE).isEmpty());
        assertTrue(next(EventStatus.ADVISOR_APPROVED, Action.ADVISOR_APPROVE).isEmpty());
        assertTrue(next(EventStatus.APPROVED, Action.ADVISOR_REJECT).isEmpty());
    }

    @Test
    void eitherReviewerCanReject() {
        assertEquals(Optional.of(EventStatus.REJECTED), next(EventStatus.SUBMITTED, Action.ADVISOR_REJECT));
        assertEquals(Optional.of(EventStatus.REJECTED), next(EventStatus.ADVISOR_APPROVED, Action.MANAGER_REJECT));
    }

    @Test
    void rejectedEventCanBeEditedOrResubmitted() {
        assertEquals(Optional.of(EventStatus.DRAFT), next(EventStatus.REJECTED, Action.EDIT));
        assertEquals(Optional.of(EventStatus.SUBMITTED), next(EventStatus.REJECTED, Action.SUBMIT));
    }

    @Test
    void editingAfterSubmissionNeedsApprovalAgain() {
        for (EventStatus s : new EventStatus[] {EventStatus.SUBMITTED, EventStatus.ADVISOR_APPROVED, EventStatus.APPROVED}) {
            assertEquals(Optional.of(EventStatus.DRAFT), next(s, Action.EDIT), "edit from " + s);
            assertTrue(EventStateMachine.holdsRoom(s), s + " should hold the room");
        }
        assertTrue(!EventStateMachine.holdsRoom(EventStatus.DRAFT));
        assertTrue(!EventStateMachine.holdsRoom(EventStatus.REJECTED));
    }

    @Test
    void finishedEventsAreFrozen() {
        for (Action a : Action.values()) {
            assertTrue(next(EventStatus.CANCELLED, a).isEmpty(), "cancelled + " + a);
            assertTrue(next(EventStatus.COMPLETED, a).isEmpty(), "completed + " + a);
        }
    }

    @Test
    void cannotSubmitTwiceOrCompleteBeforeApproval() {
        assertTrue(next(EventStatus.SUBMITTED, Action.SUBMIT).isEmpty());
        assertTrue(next(EventStatus.APPROVED, Action.SUBMIT).isEmpty());
        assertTrue(next(EventStatus.SUBMITTED, Action.COMPLETE).isEmpty());
    }

    @Test
    void onlyApprovedAndCompletedArePublic() {
        for (EventStatus s : EventStatus.values()) {
            boolean expected = s == EventStatus.APPROVED || s == EventStatus.COMPLETED;
            assertEquals(expected, EventStateMachine.isPublic(s), s.name());
        }
    }

    @Test
    void leadTimeIsInclusiveOfTheBoundary() {
        Instant now = Instant.parse("2030-01-01T10:00:00Z");
        assertTrue(EventStateMachine.meetsLeadTime(now.plus(Duration.ofDays(7)), now, 7));
        assertTrue(!EventStateMachine.meetsLeadTime(now.plus(Duration.ofDays(7)).minusSeconds(1), now, 7));
        assertTrue(EventStateMachine.meetsLeadTime(now.plusSeconds(1), now, 0));
    }
}
