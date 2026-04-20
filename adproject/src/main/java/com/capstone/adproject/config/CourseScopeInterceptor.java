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
        Long activeCourseId = courseScopeService.getActiveCourseIdForCurrentUser();
        
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
}