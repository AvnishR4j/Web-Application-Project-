package com.campus.booking.model;

/**
 * The four roles from the workflow plan. Self-registration always creates a STUDENT;
 * the other roles are assigned by a SUPER_ADMIN.
 */
public enum Role {
    STUDENT,
    CLUB_ORGANIZER,
    RESOURCE_ADMIN,
    SUPER_ADMIN
}
