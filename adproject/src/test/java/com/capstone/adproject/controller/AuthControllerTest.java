package com.capstone.adproject.controller;

import java.util.Collections;
import java.util.List;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContext;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.servlet.view.InternalResourceViewResolver;

import com.capstone.adproject.model.Admin;
import com.capstone.adproject.service.EmailService;
import com.capstone.adproject.service.UserService;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.model;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.redirectedUrl;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.view;

@ExtendWith(MockitoExtension.class)
class AuthControllerTest {

    private MockMvc mockMvc;

    @Mock
    private UserService userService;

    @Mock
    private EmailService emailService;

    @InjectMocks
    private AuthController authController;

    @BeforeEach
    void setUp() {
        InternalResourceViewResolver viewResolver = new InternalResourceViewResolver();
        viewResolver.setPrefix("/templates/");
        viewResolver.setSuffix(".html");

        mockMvc = MockMvcBuilders.standaloneSetup(authController)
                .setViewResolvers(viewResolver)
                .build();
        SecurityContextHolder.clearContext();
    }

    @Test
    void testRoot_Unauthenticated_RedirectsToLogin() throws Exception {
        mockMvc.perform(get("/"))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/login"));
    }

    @Test
    void testRoot_AuthenticatedAsAdmin_RedirectsToAdminHome() throws Exception {
        Authentication auth = new UsernamePasswordAuthenticationToken(
                "adminUser", "pass", List.of(new SimpleGrantedAuthority("ROLE_ADMIN")));
        SecurityContextHolder.getContext().setAuthentication(auth);

        mockMvc.perform(get("/"))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/admin/home"));
    }

    @Test
    void testRoot_AuthenticatedAsLecturer_RedirectsToLecturerHome() throws Exception {
        Authentication auth = new UsernamePasswordAuthenticationToken(
                "lecturerUser", "pass", List.of(new SimpleGrantedAuthority("ROLE_LECTURER")));
        SecurityContextHolder.getContext().setAuthentication(auth);

        mockMvc.perform(get("/"))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/lecturer/home"));
    }

    @Test
    void testRoot_AuthenticatedAsStudent_RedirectsToStudentHome() throws Exception {
        Authentication auth = new UsernamePasswordAuthenticationToken(
                "studentUser", "pass", List.of(new SimpleGrantedAuthority("ROLE_STUDENT")));
        SecurityContextHolder.getContext().setAuthentication(auth);

        mockMvc.perform(get("/"))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/student/home"));
    }

    @Test
    void testRoot_AuthenticatedAsSuperAdmin_RedirectsToSuperAdminHome() throws Exception {
        Authentication auth = new UsernamePasswordAuthenticationToken(
                "superAdminUser", "pass", List.of(new SimpleGrantedAuthority("ROLE_SUPER_ADMIN")));
        SecurityContextHolder.getContext().setAuthentication(auth);

        mockMvc.perform(get("/"))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/superadmin/home"));
    }

    @Test
    void testLogin_Unauthenticated_RendersLoginPage() throws Exception {
        mockMvc.perform(get("/login"))
                .andExpect(status().isOk())
                .andExpect(view().name("login"));
    }

    @Test
    void testLogin_WithErrorParam_SetsLoginErrorAttribute() throws Exception {
        mockMvc.perform(get("/login").param("error", "true"))
                .andExpect(status().isOk())
                .andExpect(view().name("login"))
                .andExpect(model().attribute("loginError", "true"));
    }

    @Test
    void testForgotPassword_Get_RendersForm() throws Exception {
        mockMvc.perform(get("/forgot_password"))
                .andExpect(status().isOk())
                .andExpect(view().name("forgot_password_form"));
    }

    @Test
    void testForgotPassword_Post_ValidEmail_GeneratesTokenAndSendsEmail() throws Exception {
        Admin mockAdmin = new Admin();
        mockAdmin.setEmail("test@example.com");

        when(userService.findUserByEmail("test@example.com")).thenReturn(mockAdmin);
        when(userService.generateResetToken(mockAdmin)).thenReturn("secure-reset-token-123");

        mockMvc.perform(post("/forgot_password")
                .param("email", "test@example.com"))
                .andExpect(status().isOk())
                .andExpect(view().name("forgot_password_form"))
                .andExpect(model().attributeExists("message"));

        verify(emailService).sendResetPasswordEmail(eq("test@example.com"), anyString());
    }

    @Test
    void testForgotPassword_Post_NonExistentEmail_DoesNotSendEmail() throws Exception {
        when(userService.findUserByEmail("unknown@example.com")).thenReturn(null);

        mockMvc.perform(post("/forgot_password")
                .param("email", "unknown@example.com"))
                .andExpect(status().isOk())
                .andExpect(view().name("forgot_password_form"))
                .andExpect(model().attributeExists("message"));

        verify(emailService, never()).sendResetPasswordEmail(anyString(), anyString());
    }

    @Test
    void testResetPassword_Get_ValidToken_RendersResetForm() throws Exception {
        Admin mockAdmin = new Admin();
        when(userService.findUserByResetToken("valid-token")).thenReturn(mockAdmin);

        mockMvc.perform(get("/reset_password").param("token", "valid-token"))
                .andExpect(status().isOk())
                .andExpect(view().name("reset_password_form"))
                .andExpect(model().attribute("token", "valid-token"));
    }

    @Test
    void testResetPassword_Get_InvalidToken_ReturnsLoginWithError() throws Exception {
        when(userService.findUserByResetToken("expired-token")).thenReturn(null);

        mockMvc.perform(get("/reset_password").param("token", "expired-token"))
                .andExpect(status().isOk())
                .andExpect(view().name("login"))
                .andExpect(model().attributeExists("error"));
    }

    @Test
    void testResetPassword_Post_MismatchPasswords_ReturnsFormWithError() throws Exception {
        mockMvc.perform(post("/reset_password")
                .param("token", "tok123")
                .param("password", "newPass1")
                .param("confirm_password", "newPass2"))
                .andExpect(status().isOk())
                .andExpect(view().name("reset_password_form"))
                .andExpect(model().attribute("error", "Passwords do not match."));
    }

    @Test
    void testResetPassword_Post_Success_UpdatesPassword() throws Exception {
        Admin mockAdmin = new Admin();
        when(userService.findUserByResetToken("tok123")).thenReturn(mockAdmin);
        doNothing().when(userService).updatePassword(mockAdmin, "correctPassword");

        mockMvc.perform(post("/reset_password")
                .param("token", "tok123")
                .param("password", "correctPassword")
                .param("confirm_password", "correctPassword"))
                .andExpect(status().isOk())
                .andExpect(view().name("login"))
                .andExpect(model().attributeExists("message"));

        verify(userService).updatePassword(mockAdmin, "correctPassword");
    }
}
