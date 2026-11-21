package com.capstone.adproject.config;

import org.hibernate.Session;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.HandlerInterceptor;

import com.capstone.adproject.service.CourseScopeService;

import jakarta.persistence.EntityManager;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

@Component
public class CourseScopeInterceptor implements HandlerInterceptor {

    private final EntityManager entityManager;
    private final CourseScopeService courseScopeService;

    public CourseScopeInterceptor(EntityManager entityManager, CourseScopeService courseScopeService) {
        this.entityManager = entityManager;
        this.courseScopeService = courseScopeService;
    }

    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) {
        // Admin pages only ever work on a course the person administers, never one they merely assess or study in.
        Long activeCourseId = isAdminArea(request)
                ? courseScopeService.ensureAdminActiveCourse()
                : courseScopeService.getActiveCourseIdForCurrentUser();
        
        Session session = entityManager.unwrap(Session.class);
        if (activeCourseId != null) {
            session.enableFilter("courseScopeFilter").setParameter("activeCourseId", activeCourseId);
        } else {
            // Secure default: If no active course, force a non-existent ID (-1) 
            // so database queries inherently return empty lists instead of leaking all records.
            session.enableFilter("courseScopeFilter").setParameter("activeCourseId", -1L);
        }
        
        return true;
    }

    /** The paths only admins reach (see SecurityConfig). */
    public static boolean isAdminArea(HttpServletRequest request) {
        String path = request.getRequestURI();
        if (path == null) {
            return false;
        }
        String context = request.getContextPath();
        if (context != null && !context.isEmpty() && path.startsWith(context)) {
            path = path.substring(context.length());
        }
        return path.equals("/admin") || path.startsWith("/admin/")
                || path.equals("/rubrics") || path.startsWith("/rubrics/")
                || path.startsWith("/deadlines/")
                || path.equals("/manage-assessments") || path.equals("/assessments/manage");
    }
}
