package com.capstone.adproject.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.when;

import java.util.Optional;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UsernameNotFoundException;

import com.capstone.adproject.model.Admin;
import com.capstone.adproject.model.Lecturer;
import com.capstone.adproject.model.Student;
import com.capstone.adproject.model.SuperAdmin;
import com.capstone.adproject.repositories.AdminRepository;
import com.capstone.adproject.repositories.LecturerRepository;
import com.capstone.adproject.repositories.StudentRepository;
import com.capstone.adproject.repositories.SuperAdminRepository;

class CustomUserDetailsServiceTest {

    @Mock
    private StudentRepository studentRepository;

    @Mock
    private LecturerRepository lecturerRepository;

    @Mock
    private AdminRepository adminRepository;

    @Mock
    private SuperAdminRepository superAdminRepository;

    @InjectMocks
    private CustomUserDetailsService customUserDetailsService;

    @BeforeEach
    void setUp() {
        MockitoAnnotations.openMocks(this);
    }

    @Test
    void testSuperAdminTakesPrecedenceOverStudentAndAdmin() {
        String email = "super@example.com";

        SuperAdmin sa = new SuperAdmin();
        sa.setEmail(email);
        sa.setUsername("superadmin");
        sa.setPassword("superadmin-pass");
        sa.setRoles("ROLE_SUPER_ADMIN,ROLE_ADMIN");

        Student stu = new Student();
        stu.setEmail(email);
        stu.setPassword("student-pass");

        when(superAdminRepository.findByEmail(email)).thenReturn(Optional.of(sa));
        when(studentRepository.findByEmail(email)).thenReturn(Optional.of(stu));

        UserDetails details = customUserDetailsService.loadUserByUsername(email);

        assertNotNull(details);
        assertEquals(email, details.getUsername());
        assertEquals("superadmin-pass", details.getPassword());
        assertTrue(details.getAuthorities().stream().map(GrantedAuthority::getAuthority).anyMatch(r -> r.equals("ROLE_SUPER_ADMIN")));
        assertTrue(details.getAuthorities().stream().noneMatch(r -> r.equals("ROLE_STUDENT")));
    }

    @Test
    void testAdminTakesPrecedenceOverLecturerPasswordAndStudentRole() {
        String email = "coordinator@example.com";

        Admin admin = new Admin();
        admin.setEmail(email);
        admin.setUsername("coordinator");
        admin.setPassword("admin-strong-pass");

        Lecturer lecturer = new Lecturer();
        lecturer.setEmail(email);
        lecturer.setUsername("coordinator");
        lecturer.setPassword("lecturer-other-pass");
        lecturer.setRoles("ROLE_LECTURER");

        Student student = new Student();
        student.setEmail(email);
        student.setPassword("student-weak-pass");

        when(superAdminRepository.findByEmail(email)).thenReturn(Optional.empty());
        when(superAdminRepository.findByUsername(email)).thenReturn(Optional.empty());
        when(adminRepository.findByEmail(email)).thenReturn(Optional.of(admin));
        when(lecturerRepository.findByEmail(email)).thenReturn(Optional.of(lecturer));
        when(studentRepository.findByEmail(email)).thenReturn(Optional.of(student));

        UserDetails details = customUserDetailsService.loadUserByUsername(email);

        assertNotNull(details);
        assertEquals("admin-strong-pass", details.getPassword());
        assertEquals("ROLE_ADMIN", details.getAuthorities().iterator().next().getAuthority());
        assertTrue(details.getAuthorities().stream().map(GrantedAuthority::getAuthority).anyMatch(r -> r.equals("ROLE_LECTURER")));
        assertTrue(details.getAuthorities().stream().noneMatch(r -> r.equals("ROLE_STUDENT")));
    }

    @Test
    void testLecturerFoundSuccessfully() {
        String email = "lecturer@example.com";

        Lecturer lecturer = new Lecturer();
        lecturer.setEmail(email);
        lecturer.setUsername("dr_lecturer");
        lecturer.setPassword("lecturer-pass");
        lecturer.setRoles("ROLE_LECTURER");

        when(superAdminRepository.findByEmail(email)).thenReturn(Optional.empty());
        when(superAdminRepository.findByUsername(email)).thenReturn(Optional.empty());
        when(adminRepository.findByEmail(email)).thenReturn(Optional.empty());
        when(adminRepository.findByUsername(email)).thenReturn(Optional.empty());
        when(lecturerRepository.findByEmail(email)).thenReturn(Optional.of(lecturer));

        UserDetails details = customUserDetailsService.loadUserByUsername(email);

        assertNotNull(details);
        assertEquals("lecturer-pass", details.getPassword());
        assertTrue(details.getAuthorities().stream().map(GrantedAuthority::getAuthority).anyMatch(r -> r.equals("ROLE_LECTURER")));
    }

    @Test
    void testStudentFoundSuccessfully() {
        String email = "student@example.com";

        Student student = new Student();
        student.setEmail(email);
        student.setUsername("stud123");
        student.setPassword("student-pass");

        when(superAdminRepository.findByEmail(email)).thenReturn(Optional.empty());
        when(superAdminRepository.findByUsername(email)).thenReturn(Optional.empty());
        when(adminRepository.findByEmail(email)).thenReturn(Optional.empty());
        when(adminRepository.findByUsername(email)).thenReturn(Optional.empty());
        when(lecturerRepository.findByEmail(email)).thenReturn(Optional.empty());
        when(lecturerRepository.findByUsername(email)).thenReturn(Optional.empty());
        when(studentRepository.findByEmail(email)).thenReturn(Optional.of(student));

        UserDetails details = customUserDetailsService.loadUserByUsername(email);

        assertNotNull(details);
        assertEquals("student-pass", details.getPassword());
        assertEquals(1, details.getAuthorities().size());
        assertEquals("ROLE_STUDENT", details.getAuthorities().iterator().next().getAuthority());
    }

    @Test
    void testUserNotFoundThrowsUsernameNotFoundException() {
        String unknown = "unknown@example.com";

        when(superAdminRepository.findByEmail(unknown)).thenReturn(Optional.empty());
        when(superAdminRepository.findByUsername(unknown)).thenReturn(Optional.empty());
        when(adminRepository.findByEmail(unknown)).thenReturn(Optional.empty());
        when(adminRepository.findByUsername(unknown)).thenReturn(Optional.empty());
        when(lecturerRepository.findByEmail(unknown)).thenReturn(Optional.empty());
        when(lecturerRepository.findByUsername(unknown)).thenReturn(Optional.empty());
        when(studentRepository.findByEmail(unknown)).thenReturn(Optional.empty());
        when(studentRepository.findByUsername(unknown)).thenReturn(Optional.empty());

        assertThrows(UsernameNotFoundException.class, () -> {
            customUserDetailsService.loadUserByUsername(unknown);
        });
    }

    @Test
    void testBlankUsernameThrowsUsernameNotFoundException() {
        assertThrows(UsernameNotFoundException.class, () -> {
            customUserDetailsService.loadUserByUsername("   ");
        });
        assertThrows(UsernameNotFoundException.class, () -> {
            customUserDetailsService.loadUserByUsername(null);
        });
    }
}
