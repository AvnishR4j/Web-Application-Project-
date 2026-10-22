package com.campus.booking.model;

/**
 * Roles from the workflow plan. Self-registration always creates a STUDENT;
 * the other roles are assigned by a SUPER_ADMIN. FACULTY_ADVISOR approves events for the clubs they advise.
 */
public enum Role {
    STUDENT,
    CLUB_ORGANIZER,
    FACULTY_ADVISOR,
    RESOURCE_ADMIN,
    SUPER_ADMIN
}
