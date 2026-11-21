package com.capstone.adproject.service;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InOrder;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.util.ReflectionTestUtils;

import com.capstone.adproject.dto.GroupAssignmentDto;
import com.capstone.adproject.model.Course;
import com.capstone.adproject.model.Group;
import com.capstone.adproject.model.Lecturer;
import com.capstone.adproject.model.Student;
import com.capstone.adproject.model.StudentCourseAssignment;
import com.capstone.adproject.repositories.AdminCourseAssignmentRepository;
import com.capstone.adproject.repositories.AdminRepository;
import com.capstone.adproject.repositories.CourseRepository;
import com.capstone.adproject.repositories.GroupRepository;
import com.capstone.adproject.repositories.LecturerGroupAssignmentRepository;
import com.capstone.adproject.repositories.LecturerRepository;
import com.capstone.adproject.repositories.StudentCourseAssignmentRepository;
import com.capstone.adproject.repositories.StudentRepository;
import com.capstone.adproject.service.EmailService.WelcomeEmail;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.atLeastOnce;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class AdminServiceTest {

    @Mock
    private StudentRepository studentRepository;
    @Mock
    private LecturerRepository lecturerRepository;
    @Mock
    private GroupRepository groupRepository;
    @Mock
    private StudentCourseAssignmentRepository studentCourseAssignmentRepository;
    @Mock
    private PasswordEncoder passwordEncoder;
    @Mock
    private LecturerGroupAssignmentRepository assignmentRepository;
    @Mock
    private AdminRepository adminRepository;
    @Mock
    private CourseScopeService courseScopeService;
    @Mock
    private AdminCourseAssignmentRepository adminCourseAssignmentRepository;
    @Mock
    private CourseRepository courseRepository;
    @Mock
    private SuperAdminService superAdminService;
    @Mock
    private EmailService emailService;
    @Mock
    private UserService userService;

    @InjectMocks
    private AdminService adminService;

    private Course courseA;
    private Course courseB;
    private Group groupA;
    private Student multiCourseStudent;

    @BeforeEach
    void setUp() {
        courseA = new Course();
        courseA.setId(10L);
        courseA.setCourseCode("SECJ101");

        courseB = new Course();
        courseB.setId(20L);
        courseB.setCourseCode("SECJ202");

        groupA = new Group();
        groupA.setId(100L);
        groupA.setGroupName("Team Alpha");
        groupA.setCourse(courseA);

        multiCourseStudent = new Student();
        multiCourseStudent.setId(1L);
        multiCourseStudent.setEmail("student@example.com");
        multiCourseStudent.setUsername("student1");
        multiCourseStudent.setCourse(courseA);
        multiCourseStudent.setGroup(groupA);
    }

    @Test
    void testAssignStudentsToNewGroup_CourseB_DoesNotOverwriteCourseAGroupOnMultiCourseStudent() {
        GroupAssignmentDto dto = new GroupAssignmentDto();
        dto.setGroupName("Team Beta");
        dto.setSelectedStudentIds(List.of(1L));

        StudentCourseAssignment assignmentB = new StudentCourseAssignment(null, multiCourseStudent, courseB);

        when(courseScopeService.getActiveCourseForCurrentUser()).thenReturn(courseB);
        when(groupRepository.save(any(Group.class))).thenAnswer(inv -> {
            Group g = inv.getArgument(0);
            g.setId(200L);
            return g;
        });
        when(studentRepository.findAllById(List.of(1L))).thenReturn(List.of(multiCourseStudent));
        when(studentCourseAssignmentRepository.existsByStudentIdAndCourseId(1L, 20L)).thenReturn(true);
        when(studentCourseAssignmentRepository.findByStudentIdAndCourseId(1L, 20L)).thenReturn(Optional.of(assignmentB));

        adminService.assignStudentsToNewGroup(dto);

        assertEquals(groupA, multiCourseStudent.getGroup());
        assertEquals(200L, assignmentB.getGroup().getId());
        verify(studentCourseAssignmentRepository).save(assignmentB);
        verify(studentRepository, never()).save(multiCourseStudent);
    }

    @Test
    void testDeleteStudentById_MultiCourseStudent_OnlyRemovesActiveCourseEnrollment() {
        StudentCourseAssignment assignmentA = new StudentCourseAssignment(null, multiCourseStudent, courseA, groupA);
        StudentCourseAssignment assignmentB = new StudentCourseAssignment(null, multiCourseStudent, courseB);

        when(courseScopeService.getActiveCourseIdForCurrentUser()).thenReturn(10L);
        when(studentRepository.findById(1L)).thenReturn(Optional.of(multiCourseStudent));
        when(studentCourseAssignmentRepository.findByStudentId(1L)).thenReturn(List.of(assignmentA, assignmentB));

        adminService.deleteStudentById(1L);

        verify(studentRepository).deleteCommentsByStudentIdAndCourseId(1L, 10L);
        verify(studentRepository).deleteOverridesByStudentAndCourseId(1L, 10L);
        verify(studentCourseAssignmentRepository).deleteByStudentIdAndCourseId(1L, 10L);
        verify(studentRepository, never()).deleteById(1L);
        assertEquals(20L, multiCourseStudent.getCourse().getId());
        assertNull(multiCourseStudent.getGroup());
        verify(studentRepository, atLeastOnce()).save(multiCourseStudent);
    }

    @Test
    void testSearchLecturers_OnlyQueriesActiveCourse() {
        Lecturer lecturerB = new Lecturer();
        lecturerB.setId(9L);
        lecturerB.setEmail("lec@example.com");
        lecturerB.setCourse(courseB);

        when(courseScopeService.getActiveCourseIdForCurrentUser()).thenReturn(20L);
        when(lecturerRepository.findByCourseId(20L)).thenReturn(List.of(lecturerB));

        List<Lecturer> results = adminService.searchLecturers("lec");

        assertEquals(1, results.size());
        assertEquals(9L, results.get(0).getId());
        verify(lecturerRepository).findByCourseId(20L);
        verify(lecturerRepository, never()).findByEmailContainingIgnoreCase("lec");
    }

    @Test
    void testIsLecturerInActiveCourse_AdminAssignedViaAdminCourseAssignment() {
        Lecturer adminLecturer = new Lecturer();
        adminLecturer.setId(55L);
        adminLecturer.setEmail("admin@example.com");
        adminLecturer.setCourse(courseA); // Assigned directly to course A, but managing course B

        when(courseScopeService.getActiveCourseIdForCurrentUser()).thenReturn(20L);
        when(adminCourseAssignmentRepository.existsByLecturerIdAndCourseId(55L, 20L)).thenReturn(true);

        boolean inCourse = adminService.isLecturerInActiveCourse(adminLecturer);

        assertTrue(inCourse);
    }

    @Test
    void testDeleteLecturerById_CrossCourse_ThrowsUnauthorized() {
        Lecturer lecturerA = new Lecturer();
        lecturerA.setId(5L);
        lecturerA.setEmail("a@example.com");
        lecturerA.setCourse(courseA);

        when(courseScopeService.getActiveCourseIdForCurrentUser()).thenReturn(20L);
        when(lecturerRepository.findById(5L)).thenReturn(Optional.of(lecturerA));

        assertThrows(RuntimeException.class, () -> adminService.deleteLecturerById(5L));
        verify(lecturerRepository, never()).deleteById(5L);
    }

    // ---- Welcome email delivery tracking ----

    /** These collaborators are field-injected in AdminService, which @InjectMocks skips after constructor injection. */
    private MockHttpServletRequest wireWelcomeEmailCollaborators() {
        ReflectionTestUtils.setField(adminService, "emailService", emailService);
        ReflectionTestUtils.setField(adminService, "userService", userService);
        ReflectionTestUtils.setField(adminService, "publicBaseUrl", "https://cas.example/");
        return new MockHttpServletRequest();
    }

    private void stubNewStudentCreation() {
        when(courseScopeService.getActiveCourseForCurrentUser()).thenReturn(courseA);
        when(passwordEncoder.encode(anyString())).thenReturn("hashed");
        when(studentRepository.save(any(Student.class))).thenAnswer(inv -> {
            Student saved = inv.getArgument(0);
            if (saved.getId() == null) {
                saved.setId(77L);
            }
            return saved;
        });
    }

    @Test
    void saveStudent_NewStudent_SavesBeforeEmailingAndRecordsConfirmedDelivery() {
        MockHttpServletRequest request = wireWelcomeEmailCollaborators();
        stubNewStudentCreation();
        when(emailService.sendWelcomeEmailWithPassword(eq("new.student@example.com"), anyString(), eq("Student"), anyString()))
            .thenReturn(true);

        Student student = new Student();
        student.setEmail(" New.Student@Example.com ");

        boolean emailSent = adminService.saveStudent(student, request);

        assertTrue(emailSent);
        assertEquals("new.student", student.getUsername(), "blank username must be generated before saving");
        assertNotNull(student.getWelcomeEmailSentAt());

        ArgumentCaptor<String> link = ArgumentCaptor.forClass(String.class);
        InOrder order = inOrder(studentRepository, emailService);
        order.verify(studentRepository).save(student);
        order.verify(emailService).sendWelcomeEmailWithPassword(
            eq("new.student@example.com"), anyString(), eq("Student"), link.capture());
        assertEquals("https://cas.example/reset_password?token=" + student.getResetPasswordToken(), link.getValue());
    }

    @Test
    void saveStudent_EmailFails_KeepsStudentAndLeavesDeliveryUnconfirmed() {
        MockHttpServletRequest request = wireWelcomeEmailCollaborators();
        stubNewStudentCreation();
        when(emailService.sendWelcomeEmailWithPassword(anyString(), anyString(), anyString(), anyString()))
            .thenReturn(false);

        Student student = new Student();
        student.setEmail("unlucky@example.com");

        boolean emailSent = adminService.saveStudent(student, request);

        assertFalse(emailSent);
        assertNull(student.getWelcomeEmailSentAt());
        verify(studentRepository).save(student);
        assertTrue(AdminService.isAwaitingWelcomeEmail(student), "a failed welcome email must stay re-sendable");
    }

    @Test
    void saveLecturer_NewAssessorWithDuplicateUsername_IsRejectedBeforeAnythingIsSavedOrEmailed() {
        MockHttpServletRequest request = wireWelcomeEmailCollaborators();
        when(courseScopeService.getActiveCourseForCurrentUser()).thenReturn(courseA);
        when(passwordEncoder.encode(anyString())).thenReturn("hashed");
        when(lecturerRepository.findByCourseIdAndUsernameIgnoreCase(10L, "Dr Tan")).thenReturn(Optional.of(new Lecturer()));

        Lecturer lecturer = new Lecturer();
        lecturer.setEmail("tan@example.com");
        lecturer.setUsername("Dr Tan");

        assertThrows(RuntimeException.class, () -> adminService.saveLecturer(lecturer, request));
        verify(lecturerRepository, never()).save(any(Lecturer.class));
        verify(emailService, never()).sendWelcomeEmailWithPassword(any(), any(), any(), any());
    }

    @Test
    void bulkAddStudents_QueuesWelcomeEmailsForBackgroundDeliveryInsteadOfSendingInline() {
        MockHttpServletRequest request = wireWelcomeEmailCollaborators();
        stubNewStudentCreation();
        when(courseScopeService.getActiveCourseIdForCurrentUser()).thenReturn(10L);

        int added = adminService.bulkAddStudents(List.of("a@example.com", "b@example.com", "A@example.com"), null, request);

        assertEquals(2, added);
        verify(emailService, never()).sendWelcomeEmailWithPassword(any(), any(), any(), any());
        @SuppressWarnings("unchecked")
        ArgumentCaptor<List<WelcomeEmail>> batch = ArgumentCaptor.forClass(List.class);
        verify(emailService).sendWelcomeEmailsInBackground(batch.capture(), any());
        assertEquals(List.of("a@example.com", "b@example.com"),
            batch.getValue().stream().map(WelcomeEmail::email).toList());
        assertTrue(batch.getValue().stream().allMatch(mail -> mail.temporaryPassword() != null));
    }

    @Test
    void resendStudentWelcomeEmails_OnlyTargetsUnconfirmedAccountsAndNeverChangesPasswords() {
        MockHttpServletRequest request = wireWelcomeEmailCollaborators();

        Student neverEmailed = welcomeStudent(1L, "never@example.com", true, "raw-token", null);
        Student alreadyConfirmed = welcomeStudent(2L, "confirmed@example.com", true, "raw-token-2", LocalDateTime.now());
        Student setOwnPassword = welcomeStudent(3L, "active@example.com", true, null, null);
        Student sharedLogin = welcomeStudent(4L, "shared@example.com", false, "raw-token-4", null);

        when(courseScopeService.getActiveCourseIdForCurrentUser()).thenReturn(10L);
        when(studentRepository.findByCourseId(10L))
            .thenReturn(List.of(neverEmailed, alreadyConfirmed, setOwnPassword, sharedLogin));
        when(userService.generateResetToken(neverEmailed, Duration.ofDays(7))).thenReturn("fresh-token");

        int queued = adminService.resendStudentWelcomeEmails(null, request);

        assertEquals(1, queued);
        @SuppressWarnings("unchecked")
        ArgumentCaptor<List<WelcomeEmail>> batch = ArgumentCaptor.forClass(List.class);
        verify(emailService).sendWelcomeEmailsInBackground(batch.capture(), any());
        WelcomeEmail mail = batch.getValue().get(0);
        assertEquals(1, batch.getValue().size());
        assertEquals("never@example.com", mail.email());
        assertNull(mail.temporaryPassword(), "a re-sent welcome must not carry or reset a password");
        assertEquals("https://cas.example/reset_password?token=fresh-token", mail.resetLink());
        assertEquals("existing-hash", neverEmailed.getPassword());
        verify(userService, never()).generateResetToken(eq(alreadyConfirmed), any());
        verify(userService, never()).generateResetToken(eq(setOwnPassword), any());
        verify(userService, never()).generateResetToken(eq(sharedLogin), any());
    }

    @Test
    void resendStudentWelcomeEmails_ForOneAccount_SendsEvenWhenAnEarlierEmailWasAccepted_ButNeverToActivatedAccounts() {
        MockHttpServletRequest request = wireWelcomeEmailCollaborators();
        Student alreadyConfirmed = welcomeStudent(2L, "confirmed@example.com", true, "raw-token-2", LocalDateTime.now());
        Student activated = welcomeStudent(3L, "active@example.com", true, null, LocalDateTime.now());
        when(courseScopeService.getActiveCourseIdForCurrentUser()).thenReturn(10L);
        when(studentRepository.findByCourseId(10L)).thenReturn(List.of(alreadyConfirmed, activated));
        when(userService.generateResetToken(alreadyConfirmed, Duration.ofDays(7))).thenReturn("fresh");

        assertEquals(1, adminService.resendStudentWelcomeEmails(2L, request),
            "the per-account button must work even though a welcome email was already accepted before");
        assertEquals(0, adminService.resendStudentWelcomeEmails(3L, request),
            "an account that already set its password is never re-invited");
    }

    @Test
    void resendStudentWelcomeEmails_IncludeConfirmed_ReachesEveryoneWhoHasNotSetAPassword() {
        MockHttpServletRequest request = wireWelcomeEmailCollaborators();
        Student neverEmailed = welcomeStudent(1L, "never@example.com", true, "t1", null);
        Student alreadyConfirmed = welcomeStudent(2L, "confirmed@example.com", true, "t2", LocalDateTime.now());
        Student activated = welcomeStudent(3L, "active@example.com", true, null, LocalDateTime.now());
        when(courseScopeService.getActiveCourseIdForCurrentUser()).thenReturn(10L);
        when(studentRepository.findByCourseId(10L)).thenReturn(List.of(neverEmailed, alreadyConfirmed, activated));
        when(userService.generateResetToken(any(Student.class), eq(Duration.ofDays(7)))).thenReturn("fresh");

        assertEquals(2, adminService.resendStudentWelcomeEmails(null, true, request));
        verify(userService, never()).generateResetToken(eq(activated), any());
    }

    @Test
    void resendStudentWelcomeEmails_WithNothingPending_QueuesNothing() {
        MockHttpServletRequest request = wireWelcomeEmailCollaborators();
        when(courseScopeService.getActiveCourseIdForCurrentUser()).thenReturn(10L);
        when(studentRepository.findByCourseId(10L))
            .thenReturn(List.of(welcomeStudent(2L, "confirmed@example.com", true, "t", LocalDateTime.now())));

        assertEquals(0, adminService.resendStudentWelcomeEmails(null, request));
        verify(emailService, never()).sendWelcomeEmailsInBackground(any(), any());
    }

    @Test
    void saveStudent_Update_PreservesWelcomeEmailTimestamp() {
        LocalDateTime sentAt = LocalDateTime.of(2026, 11, 1, 9, 0);
        Student existing = welcomeStudent(5L, "kept@example.com", true, "tok", sentAt);
        existing.setUsername("kept");
        when(studentRepository.findById(5L)).thenReturn(Optional.of(existing));

        Student submitted = new Student();
        submitted.setId(5L);
        submitted.setEmail("kept@example.com");
        submitted.setUsername("renamed");

        adminService.saveStudent(submitted, new MockHttpServletRequest());

        assertEquals(sentAt, submitted.getWelcomeEmailSentAt());
        verify(studentRepository).save(submitted);
    }

    private static Student welcomeStudent(Long id, String email, boolean tempPassword, String token, LocalDateTime sentAt) {
        Student student = new Student();
        student.setId(id);
        student.setEmail(email);
        student.setPassword("existing-hash");
        student.setIsTempPassword(tempPassword);
        student.setResetPasswordToken(token);
        student.setWelcomeEmailSentAt(sentAt);
        return student;
    }

    // --- Deleting an assessor must not reach into their other courses ----------------------------------------------

    private Lecturer assessor(Long id, Course home) {
        Lecturer l = new Lecturer();
        l.setId(id);
        l.setEmail("lim@uni.edu.my");
        l.setCourse(home);
        return l;
    }

    private com.capstone.adproject.model.AdminCourseAssignment join(Lecturer l, Course c) {
        return new com.capstone.adproject.model.AdminCourseAssignment(1000L + c.getId(), l, c);
    }

    @Test
    void deletingAnAssessorWhoseHomeCourseIsThisOneKeepsTheirOtherCoursesAndAccount() {
        Lecturer lim = assessor(1L, courseA);
        var inB = join(lim, courseB);
        when(courseScopeService.getActiveCourseIdForCurrentUser()).thenReturn(10L);
        when(lecturerRepository.findById(1L)).thenReturn(Optional.of(lim));
        when(adminCourseAssignmentRepository.findByLecturerId(1L)).thenReturn(List.of(inB));

        adminService.deleteLecturerById(1L);

        verify(lecturerRepository).deleteMarksGivenInCourse(1L, 10L);
        verify(lecturerRepository).deleteGroupAssignmentsInCourse(1L, 10L);
        verify(lecturerRepository).deleteStudentAssignmentsInCourse(1L, 10L);
        verify(lecturerRepository).deleteRubricAssignmentsInCourse(1L, 10L);
        verify(lecturerRepository).deleteCommentsByLecturerInCourse(1L, 10L);
        verify(lecturerRepository).unlinkFromGroupsInCourse(1L, 10L);
        verify(lecturerRepository, never()).deleteMarksGiven(any());
        verify(lecturerRepository, never()).delete(any());
        // Their other course becomes their home course, and its join row is no longer needed.
        assertEquals(20L, lim.getCourse().getId());
        verify(adminCourseAssignmentRepository).delete(inB);
        verify(lecturerRepository).save(lim);
    }

    @Test
    void removingAnAssessorWhoJoinedThisCourseFromElsewhereOnlyLeavesThisCourse() {
        Lecturer lim = assessor(1L, courseB);
        var inA = join(lim, courseA);
        when(adminCourseAssignmentRepository.existsByLecturerIdAndCourseId(1L, 10L)).thenReturn(true);
        when(courseScopeService.getActiveCourseIdForCurrentUser()).thenReturn(10L);
        when(lecturerRepository.findById(1L)).thenReturn(Optional.of(lim));
        when(adminCourseAssignmentRepository.findByLecturerId(1L)).thenReturn(List.of(inA));

        adminService.deleteLecturerById(1L);

        verify(lecturerRepository).deleteMarksGivenInCourse(1L, 10L);
        verify(adminCourseAssignmentRepository).delete(inA);
        verify(lecturerRepository, never()).delete(any());
        verify(lecturerRepository, never()).deleteMarksGiven(any());
        assertEquals(20L, lim.getCourse().getId());
    }

    @Test
    void anAssessorWhoBelongsOnlyToThisCourseIsDeletedCompletely() {
        Lecturer lim = assessor(1L, courseA);
        when(courseScopeService.getActiveCourseIdForCurrentUser()).thenReturn(10L);
        when(lecturerRepository.findById(1L)).thenReturn(Optional.of(lim));
        when(adminCourseAssignmentRepository.findByLecturerId(1L)).thenReturn(List.of());

        adminService.deleteLecturerById(1L);

        verify(lecturerRepository).deleteMarksGiven(1L);
        verify(lecturerRepository).delete(lim);
        verify(lecturerRepository, never()).deleteMarksGivenInCourse(any(), any());
    }

    @Test
    void anAssessorOutsideThisCourseCannotBeDeleted() {
        Lecturer lim = assessor(1L, courseB);
        when(courseScopeService.getActiveCourseIdForCurrentUser()).thenReturn(10L);
        when(lecturerRepository.findById(1L)).thenReturn(Optional.of(lim));
        when(adminCourseAssignmentRepository.existsByLecturerIdAndCourseId(1L, 10L)).thenReturn(false);

        assertThrows(RuntimeException.class, () -> adminService.deleteLecturerById(1L));
        verify(lecturerRepository, never()).delete(any());
    }
}
