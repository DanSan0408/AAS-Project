package com.capstone.adproject.controller;

import java.util.List;
import java.util.Optional;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import com.capstone.adproject.model.Course;
import com.capstone.adproject.model.Lecturer;
import com.capstone.adproject.service.SuperAdminService;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.flash;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.model;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.redirectedUrl;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.view;

@ExtendWith(MockitoExtension.class)
class SuperAdminControllerTest {

    private MockMvc mockMvc;

    @Mock
    private SuperAdminService superAdminService;

    @InjectMocks
    private SuperAdminController superAdminController;

    private Course course;

    @BeforeEach
    void setUp() {
        mockMvc = MockMvcBuilders.standaloneSetup(superAdminController).build();

        Authentication auth = new UsernamePasswordAuthenticationToken(
                "superadmin@domain.com", "pass", List.of(new SimpleGrantedAuthority("ROLE_SUPER_ADMIN")));
        SecurityContextHolder.getContext().setAuthentication(auth);

        course = new Course();
        course.setId(10L);
        course.setCourseName("Advanced Software Engineering");
    }

    @Test
    void testSuperAdminHome_LoadsDashboard() throws Exception {
        when(superAdminService.getManagedCoursesForAdminEmail("superadmin@domain.com")).thenReturn(List.of(course));
        when(superAdminService.getUserCourse("superadmin@domain.com")).thenReturn(Optional.of(course));
        when(superAdminService.getAllCourses()).thenReturn(List.of(course));

        mockMvc.perform(get("/superadmin/home"))
                .andExpect(status().isOk())
                .andExpect(view().name("superadmin_home"))
                .andExpect(model().attributeExists("managedCourses", "superAdminUsername", "courseStatistics"));
    }

    @Test
    void testManageCourses_LoadsCoursesList() throws Exception {
        when(superAdminService.getAllCourses()).thenReturn(List.of(course));

        mockMvc.perform(get("/superadmin/manage-courses"))
                .andExpect(status().isOk())
                .andExpect(view().name("manage_courses"))
                .andExpect(model().attributeExists("courses", "newCourse"));
    }

    @Test
    void testAddCourse_ValidCourse_SavesAndRedirects() throws Exception {
        Lecturer superAdminLecturer = new Lecturer();
        superAdminLecturer.setId(1L);
        superAdminLecturer.setEmail("superadmin@domain.com");

        when(superAdminService.resolveLecturerByIdentity("superadmin@domain.com"))
                .thenReturn(Optional.of(superAdminLecturer));
        when(superAdminService.saveCourse(any(Course.class))).thenReturn(course);

        mockMvc.perform(post("/superadmin/courses/add")
                .param("courseName", "New Course"))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/superadmin/manage-courses"))
                .andExpect(flash().attribute("successMessage", "Course added successfully!"));

        verify(superAdminService).assignAdminToCourse(1L, 10L);
    }

    @Test
    void testDeleteCourse_ValidCourse_DeletesAndRedirects() throws Exception {
        mockMvc.perform(post("/superadmin/courses/delete/10"))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/superadmin/manage-courses"))
                .andExpect(flash().attribute("successMessage", "Course deleted successfully!"));

        verify(superAdminService).deleteCourse(10L);
    }

    @Test
    void testResetData_InvalidConfirmationText_Aborts() throws Exception {
        mockMvc.perform(post("/superadmin/reset-data")
                .param("confirmationText", "wrong_text"))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/superadmin/manage-courses"))
                .andExpect(flash().attributeExists("errorMessage"));
    }

    @Test
    void testResetData_ValidConfirmationText_ExecutesReset() throws Exception {
        mockMvc.perform(post("/superadmin/reset-data")
                .param("confirmationText", "WIPE_EVERYTHING_EXCEPT_SUPER_ADMIN"))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/superadmin/manage-courses"))
                .andExpect(flash().attribute("successMessage",
                        "System reset complete. All data has been wiped except super admin account(s)."));

        verify(superAdminService).resetToSuperAdminOnly();
    }
}

