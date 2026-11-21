package com.capstone.adproject.model;

import java.time.LocalDateTime;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * A message the Super Admin wants every matching user to see in a pop-up when they sign in (nightly maintenance,
 * a new release, an improvement). Plain text only: it is always rendered escaped.
 */
@Entity
@Table(name = "announcements")
@Data
@NoArgsConstructor
public class Announcement {

    public enum Category {
        NOTICE("Notice"),
        MAINTENANCE("Maintenance"),
        RELEASE("New release"),
        IMPROVEMENT("Improvement");

        private final String label;

        Category(String label) {
            this.label = label;
        }

        public String getLabel() {
            return label;
        }
    }

    public enum Audience {
        ALL("Everyone"),
        STUDENTS("Students"),
        ASSESSORS("Assessors"),
        ADMINS("Admins");

        private final String label;

        Audience(String label) {
            this.label = label;
        }

        public String getLabel() {
            return label;
        }
    }

    /** What the Super Admin sees in the list; never stored. */
    public enum Status {
        LIVE("Live"),
        SCHEDULED("Scheduled"),
        EXPIRED("Expired"),
        OFF("Off");

        private final String label;

        Status(String label) {
            this.label = label;
        }

        public String getLabel() {
            return label;
        }
    }

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, length = 120)
    private String title;

    @Column(nullable = false, length = 2000)
    private String message;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private Category category = Category.NOTICE;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private Audience audience = Audience.ALL;

    /** The course this is for; null means every course. */
    @Column(name = "course_id")
    private Long courseId;

    @Column(nullable = false)
    private boolean active = true;

    /** Optional: not shown before this time (server time zone). */
    private LocalDateTime startsAt;

    /** Optional: not shown from this time on (server time zone). */
    private LocalDateTime endsAt;

    @Column(nullable = false, length = 150)
    private String createdBy;

    @Column(nullable = false)
    private LocalDateTime createdAt;

    private LocalDateTime updatedAt;

    public Status statusAt(LocalDateTime now) {
        if (!active) {
            return Status.OFF;
        }
        if (startsAt != null && startsAt.isAfter(now)) {
            return Status.SCHEDULED;
        }
        if (endsAt != null && !endsAt.isAfter(now)) {
            return Status.EXPIRED;
        }
        return Status.LIVE;
    }

    public Long getCourseId() { return courseId; }
    public void setCourseId(Long courseId) { this.courseId = courseId; }
}
