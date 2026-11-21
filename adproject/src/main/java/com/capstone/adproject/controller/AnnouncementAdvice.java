package com.capstone.adproject.controller;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.security.authentication.AnonymousAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.ControllerAdvice;
import org.springframework.web.bind.annotation.ModelAttribute;

import com.capstone.adproject.service.AnnouncementService;
import com.capstone.adproject.service.CourseScopeService;
import com.capstone.adproject.service.AnnouncementService.AnnouncementView;

import jakarta.servlet.DispatcherType;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpSession;

/**
 * Gives every page a {@code pendingAnnouncements} list for the sign-in pop-up (rendered by the sidebar fragments).
 *
 * The list is worked out once per login session, kept in the session, and emptied when the user dismisses it, so a
 * user sees each login's announcements once and the database is asked once per login, not once per page. A new
 * announcement therefore reaches people at their next login, which is what "tell people when they sign in" means.
 */
@ControllerAdvice
public class AnnouncementAdvice {

    static final String SESSION_KEY = "iams.announcements.pending";
    /** The course the cached list was worked out for; the list is recomputed when the user switches course. */
    static final String COURSE_KEY = "iams.announcements.course";
    /** Ids the user has already dismissed in this session, so switching course does not show them twice. */
    static final String SEEN_KEY = "iams.announcements.seen";
    private static final Logger log = LoggerFactory.getLogger(AnnouncementAdvice.class);

    private final AnnouncementService announcementService;
    private final CourseScopeService courseScopeService;

    public AnnouncementAdvice(AnnouncementService announcementService, CourseScopeService courseScopeService) {
        this.announcementService = announcementService;
        this.courseScopeService = courseScopeService;
    }

    @ModelAttribute("pendingAnnouncements")
    @SuppressWarnings("unchecked")
    public List<AnnouncementView> pendingAnnouncements(HttpServletRequest request) {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication == null || !authentication.isAuthenticated()
                || authentication instanceof AnonymousAuthenticationToken) {
            return List.of();
        }
        // Only a normal page request shows the pop-up. An async dispatch (the progress stream finishing) or the /error page
        // runs after the response is committed, where creating a session throws and turns one failure into several alerts.
        if (request.getDispatcherType() != DispatcherType.REQUEST) {
            return List.of();
        }
        HttpSession session;
        try {
            session = request.getSession();
        } catch (IllegalStateException responseAlreadyCommitted) {
            return List.of();
        }
        Long courseId = activeCourseId();
        Object cached = session.getAttribute(SESSION_KEY);
        if (cached instanceof List<?> list && session.getAttribute(COURSE_KEY + ".set") != null
                && Objects.equals(session.getAttribute(COURSE_KEY), courseId)) {
            return (List<AnnouncementView>) list;
        }
        Set<Long> seen = seenIds(session);
        List<AnnouncementView> pending = new ArrayList<>();
        try {
            for (AnnouncementView view : announcementService.liveFor(authentication.getAuthorities(), LocalDateTime.now(), courseId)) {
                if (!seen.contains(view.id())) {
                    pending.add(view);
                }
            }
        } catch (RuntimeException e) {
            // A problem with announcements (for example a table that is not there yet) must never break the page.
            log.warn("Could not load announcements; skipping them for this session: {}", e.getMessage());
            pending = new ArrayList<>();
        }
        session.setAttribute(SESSION_KEY, pending);
        session.setAttribute(COURSE_KEY, courseId);
        session.setAttribute(COURSE_KEY + ".set", Boolean.TRUE);
        return pending;
    }

    private Long activeCourseId() {
        try {
            return courseScopeService == null ? null : courseScopeService.getActiveCourseIdForCurrentUser();
        } catch (RuntimeException e) {
            return null; // no course: only the announcements for every course
        }
    }

    @SuppressWarnings("unchecked")
    private static Set<Long> seenIds(HttpSession session) {
        Object seen = session.getAttribute(SEEN_KEY);
        if (seen instanceof Set<?> set) {
            return (Set<Long>) set;
        }
        Set<Long> created = new HashSet<>();
        session.setAttribute(SEEN_KEY, created);
        return created;
    }

    /** The user has read the announcements they were shown. */
    @SuppressWarnings("unchecked")
    static void markSeen(HttpSession session) {
        Object cached = session.getAttribute(SESSION_KEY);
        if (cached instanceof List<?> list) {
            Set<Long> seen = seenIds(session);
            for (Object item : list) {
                if (item instanceof AnnouncementView view && view.id() != null) {
                    seen.add(view.id());
                }
            }
        }
        session.setAttribute(SESSION_KEY, new ArrayList<AnnouncementView>());
    }
}
