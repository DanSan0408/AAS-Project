package com.capstone.adproject.controller;

import java.util.List;

import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.ControllerAdvice;
import org.springframework.web.bind.annotation.ModelAttribute;

import com.capstone.adproject.model.Course;
import com.capstone.adproject.service.CourseScopeService;

@ControllerAdvice
public class AdminCourseContextAdvice {

    private final CourseScopeService courseScopeService;

    public AdminCourseContextAdvice(CourseScopeService courseScopeService) {
        this.courseScopeService = courseScopeService;
    }

    @ModelAttribute("managedCourses")
    public List<Course> managedCourses() {
        if (!isAdminOrSuperAdmin()) {
            return List.of();
        }
        return courseScopeService.getManagedCoursesForCurrentUser();
    }

    @ModelAttribute("activeCourseId")
    public Long activeCourseId() {
        if (!isAdminOrSuperAdmin()) {
            return null;
        }
        return courseScopeService.getActiveCourseIdForCurrentUser();
    }

    @ModelAttribute("activeCourse")
    public Course activeCourse() {
        if (!isAdminOrSuperAdmin()) {
            return null;
        }
        return courseScopeService.getActiveCourseForCurrentUser();
    }

    private boolean isAdminOrSuperAdmin() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication == null || authentication.getAuthorities() == null) {
            return false;
        }

        return authentication.getAuthorities().stream().anyMatch(a -> {
            String role = a.getAuthority();
            return "ROLE_ADMIN".equals(role) || "ROLE_SUPER_ADMIN".equals(role);
        });
    }
}