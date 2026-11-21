package com.capstone.adproject.config;

import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class CourseScopeInterceptorTest {

    private static MockHttpServletRequest get(String path) {
        MockHttpServletRequest request = new MockHttpServletRequest("GET", path);
        request.setRequestURI(path);
        return request;
    }

    @Test
    void theAdminOnlyPathsAreRecognised() {
        for (String path : new String[] {"/admin/home", "/admin", "/rubrics/manage", "/deadlines/save", "/manage-assessments", "/assessments/manage"}) {
            assertTrue(CourseScopeInterceptor.isAdminArea(get(path)), path);
        }
    }

    @Test
    void assessorStudentAndPublicPathsAreNot() {
        for (String path : new String[] {"/lecturer/home", "/student/home", "/login", "/superadmin/home", "/administrator", "/api/progress"}) {
            assertFalse(CourseScopeInterceptor.isAdminArea(get(path)), path);
        }
    }

    @Test
    void aContextPathIsIgnored() {
        MockHttpServletRequest request = get("/iams/admin/home");
        request.setContextPath("/iams");
        assertTrue(CourseScopeInterceptor.isAdminArea(request));
    }
}
