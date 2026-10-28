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
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.servlet.view.InternalResourceViewResolver;

import com.capstone.adproject.model.Admin;
import com.capstone.adproject.model.Lecturer;
import com.capstone.adproject.model.Student;
import com.capstone.adproject.model.SuperAdmin;
import com.capstone.adproject.repositories.AdminRepository;
import com.capstone.adproject.repositories.LecturerRepository;
import com.capstone.adproject.repositories.StudentRepository;
import com.capstone.adproject.repositories.SuperAdminRepository;

import static org.mockito.ArgumentMatchers.any;
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
class ProfileControllerTest {

    private MockMvc mockMvc;

    @Mock
    private StudentRepository studentRepository;

    @Mock
    private LecturerRepository lecturerRepository;

    @Mock
    private SuperAdminRepository superAdminRepository;

    @Mock
    private AdminRepository adminRepository;

    @InjectMocks
    private ProfileController profileController;

    @BeforeEach
    void setUp() {
        InternalResourceViewResolver viewResolver = new InternalResourceViewResolver();
        viewResolver.setPrefix("/templates/");
        viewResolver.setSuffix(".html");

        mockMvc = MockMvcBuilders.standaloneSetup(profileController)
                .setViewResolvers(viewResolver)
                .build();
    }

    @Test
    void testViewProfile_Student_LoadsProfileDetails() throws Exception {
        Authentication auth = new UsernamePasswordAuthenticationToken(
                "student1@domain.com", "pass", List.of(new SimpleGrantedAuthority("ROLE_STUDENT")));

        Student student = new Student();
        student.setUsername("student_one");
        student.setEmail("student1@domain.com");

        when(studentRepository.findByEmail("student1@domain.com")).thenReturn(Optional.of(student));

        mockMvc.perform(get("/profile").principal(auth))
                .andExpect(status().isOk())
                .andExpect(view().name("profile"))
                .andExpect(model().attribute("username", "student_one"))
                .andExpect(model().attribute("email", "student1@domain.com"))
                .andExpect(model().attribute("role", "STUDENT"));
    }

    @Test
    void testViewProfile_Lecturer_LoadsProfileDetails() throws Exception {
        Authentication auth = new UsernamePasswordAuthenticationToken(
                "lecturer1@domain.com", "pass", List.of(new SimpleGrantedAuthority("ROLE_LECTURER")));

        Lecturer lecturer = new Lecturer();
        lecturer.setUsername("dr_lecturer");
        lecturer.setEmail("lecturer1@domain.com");

        when(lecturerRepository.findByEmail("lecturer1@domain.com")).thenReturn(Optional.of(lecturer));

        mockMvc.perform(get("/profile").principal(auth))
                .andExpect(status().isOk())
                .andExpect(view().name("profile"))
                .andExpect(model().attribute("username", "dr_lecturer"))
                .andExpect(model().attribute("email", "lecturer1@domain.com"))
                .andExpect(model().attribute("role", "LECTURER"));
    }

    @Test
    void testUpdateProfile_Student_UpdatesUsernameSuccessfully() throws Exception {
        Authentication auth = new UsernamePasswordAuthenticationToken(
                "student1@domain.com", "pass", List.of(new SimpleGrantedAuthority("ROLE_STUDENT")));

        Student student = new Student();
        student.setUsername("old_username");
        student.setEmail("student1@domain.com");

        when(studentRepository.findByEmail("student1@domain.com")).thenReturn(Optional.of(student));

        mockMvc.perform(post("/profile/update")
                .principal(auth)
                .param("username", "new_student_name"))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/profile"))
                .andExpect(flash().attribute("successMessage", "Profile updated successfully!"));

        verify(studentRepository).save(student);
    }
}
