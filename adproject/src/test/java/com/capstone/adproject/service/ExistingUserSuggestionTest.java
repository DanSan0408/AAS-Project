package com.capstone.adproject.service;

import java.util.List;
import java.util.Optional;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.util.ReflectionTestUtils;

import com.capstone.adproject.model.AdminCourseAssignment;
import com.capstone.adproject.model.Course;
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
import com.capstone.adproject.service.AdminService.UserSuggestion;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/** Typing an email or name while adding a user: suggest the existing account, and bring it into the course on request. */
@ExtendWith(MockitoExtension.class)
class ExistingUserSuggestionTest {

    @Mock private StudentRepository studentRepository;
    @Mock private LecturerRepository lecturerRepository;
    @Mock private GroupRepository groupRepository;
    @Mock private StudentCourseAssignmentRepository studentCourseAssignmentRepository;
    @Mock private PasswordEncoder passwordEncoder;
    @Mock private LecturerGroupAssignmentRepository assignmentRepository;
    @Mock private AdminRepository adminRepository;
    @Mock private CourseScopeService courseScopeService;
    @Mock private AdminCourseAssignmentRepository adminCourseAssignmentRepository;
    @Mock private CourseRepository courseRepository;
    @Mock private SuperAdminService superAdminService;
    @Mock private EmailService emailService;
    @Mock private UserService userService;

    @InjectMocks
    private AdminService adminService;

    private Course thisCourse;
    private Course otherCourse;

    @BeforeEach
    void setUp() {
        ReflectionTestUtils.setField(adminService, "emailService", emailService);
        thisCourse = new Course();
        thisCourse.setId(10L);
        otherCourse = new Course();
        otherCourse.setId(20L);
        lenient().when(courseScopeService.getActiveCourseForCurrentUser()).thenReturn(thisCourse);
    }

    private static Student student(Long id, String name, String email, Course course) {
        Student s = new Student();
        s.setId(id);
        s.setUsername(name);
        s.setEmail(email);
        s.setCourse(course);
        return s;
    }

    private static Lecturer lecturer(Long id, String name, String email, Course course) {
        Lecturer l = new Lecturer();
        l.setId(id);
        l.setUsername(name);
        l.setEmail(email);
        l.setCourse(course);
        return l;
    }

    // --- suggestions -----------------------------------------------------------------------------------------

    @Test
    void shortQueriesSuggestNothingAndDoNotTouchTheDatabase() {
        assertTrue(adminService.suggestStudents("ab").isEmpty());
        assertTrue(adminService.suggestStudents("  a ").isEmpty());
        assertTrue(adminService.suggestStudents(null).isEmpty());
        verify(studentRepository, never()).findTop5ByEmailContainingIgnoreCaseOrUsernameContainingIgnoreCaseOrderByUsernameAsc(anyString(), anyString());
    }

    @Test
    void aStudentFromAnotherCourseIsSuggestedWithAMaskedEmailAndMayBeAdded() {
        Student chen = student(7L, "Chen Wei", "chen.wei@uni.edu.my", otherCourse);
        when(studentRepository.findTop5ByEmailContainingIgnoreCaseOrUsernameContainingIgnoreCaseOrderByUsernameAsc("Chen", "Chen"))
                .thenReturn(List.of(chen));
        when(studentCourseAssignmentRepository.existsByStudentIdAndCourseId(7L, 10L)).thenReturn(false);

        List<UserSuggestion> found = adminService.suggestStudents(" Chen ");

        assertEquals(1, found.size());
        assertEquals(7L, found.get(0).id());
        assertEquals("Chen Wei", found.get(0).username());
        assertEquals("ch***@uni.edu.my", found.get(0).email());
        assertFalse(found.get(0).inThisCourse());
    }

    @Test
    void theFullEmailIsShownWhenTheAdminTypedExactlyThatEmail() {
        Student chen = student(7L, "Chen Wei", "chen.wei@uni.edu.my", otherCourse);
        when(studentRepository.findTop5ByEmailContainingIgnoreCaseOrUsernameContainingIgnoreCaseOrderByUsernameAsc("Chen.Wei@uni.edu.my", "Chen.Wei@uni.edu.my"))
                .thenReturn(List.of(chen));

        List<UserSuggestion> found = adminService.suggestStudents("Chen.Wei@uni.edu.my");

        assertEquals("chen.wei@uni.edu.my", found.get(0).email());
    }

    @Test
    void aStudentAlreadyInThisCourseIsFlaggedSoItCannotBeAddedTwice() {
        Student here = student(8L, "Aina", "aina@uni.edu.my", otherCourse);
        when(studentRepository.findTop5ByEmailContainingIgnoreCaseOrUsernameContainingIgnoreCaseOrderByUsernameAsc("aina", "aina"))
                .thenReturn(List.of(here));
        when(studentCourseAssignmentRepository.existsByStudentIdAndCourseId(8L, 10L)).thenReturn(true);

        assertTrue(adminService.suggestStudents("aina").get(0).inThisCourse());
    }

    @Test
    void assessorsAreMatchedTheSameWay() {
        Lecturer direct = lecturer(1L, "Dr Lim", "lim@uni.edu.my", thisCourse);
        Lecturer assigned = lecturer(2L, "Dr Lee", "lee@uni.edu.my", otherCourse);
        Lecturer elsewhere = lecturer(3L, "Dr Tan", "tan@uni.edu.my", otherCourse);
        when(lecturerRepository.findTop5ByEmailContainingIgnoreCaseOrUsernameContainingIgnoreCaseOrderByUsernameAsc("uni", "uni"))
                .thenReturn(List.of(direct, assigned, elsewhere));
        when(adminCourseAssignmentRepository.existsByLecturerIdAndCourseId(2L, 10L)).thenReturn(true);
        when(adminCourseAssignmentRepository.existsByLecturerIdAndCourseId(3L, 10L)).thenReturn(false);

        List<UserSuggestion> found = adminService.suggestLecturers("uni");

        assertTrue(found.get(0).inThisCourse());
        assertTrue(found.get(1).inThisCourse());
        assertFalse(found.get(2).inThisCourse());
    }

    // --- bringing an existing user over ----------------------------------------------------------------------

    @Test
    void addingAnExistingStudentEnrollsThemInThisCourseWithoutCreatingOrEmailing() {
        Student chen = student(7L, "Chen Wei", "chen@uni.edu.my", otherCourse);
        when(studentRepository.findById(7L)).thenReturn(Optional.of(chen));
        when(studentCourseAssignmentRepository.existsByStudentIdAndCourseId(7L, 10L)).thenReturn(false);

        Student result = adminService.enrollExistingStudent(7L);

        assertEquals(7L, result.getId());
        ArgumentCaptor<StudentCourseAssignment> saved = ArgumentCaptor.forClass(StudentCourseAssignment.class);
        verify(studentCourseAssignmentRepository).save(saved.capture());
        assertEquals(7L, saved.getValue().getStudent().getId());
        assertEquals(10L, saved.getValue().getCourse().getId());
        verify(studentRepository, never()).save(any());
        verify(emailService, never()).sendWelcomeEmailWithPassword(any(), any(), any(), any());
    }

    @Test
    void aStudentAlreadyInThisCourseCannotBeAddedAgain() {
        Student chen = student(7L, "Chen Wei", "chen@uni.edu.my", otherCourse);
        when(studentRepository.findById(7L)).thenReturn(Optional.of(chen));
        when(studentCourseAssignmentRepository.existsByStudentIdAndCourseId(7L, 10L)).thenReturn(true);

        assertThrows(IllegalArgumentException.class, () -> adminService.enrollExistingStudent(7L));
        verify(studentCourseAssignmentRepository, never()).save(any());
    }

    @Test
    void addingAStudentThatDoesNotExistIsRefused() {
        when(studentRepository.findById(99L)).thenReturn(Optional.empty());

        assertThrows(IllegalArgumentException.class, () -> adminService.enrollExistingStudent(99L));
    }

    @Test
    void addingAnAssessorFromAnotherCourseRecordsAnAssignmentAndLeavesTheirHomeCourseAlone() {
        Lecturer lim = lecturer(1L, "Dr Lim", "lim@uni.edu.my", otherCourse);
        when(lecturerRepository.findById(1L)).thenReturn(Optional.of(lim));
        when(adminCourseAssignmentRepository.existsByLecturerIdAndCourseId(1L, 10L)).thenReturn(false);

        Lecturer result = adminService.enrollExistingLecturer(1L);

        assertEquals(1L, result.getId());
        ArgumentCaptor<AdminCourseAssignment> saved = ArgumentCaptor.forClass(AdminCourseAssignment.class);
        verify(adminCourseAssignmentRepository).save(saved.capture());
        assertEquals(10L, saved.getValue().getCourse().getId());
        assertEquals(20L, lim.getCourse().getId());
        verify(lecturerRepository, never()).save(any());
    }

    @Test
    void anAssessorWithNoHomeCourseGetsThisOneAsTheirCourse() {
        Lecturer lim = lecturer(1L, "Dr Lim", "lim@uni.edu.my", null);
        when(lecturerRepository.findById(1L)).thenReturn(Optional.of(lim));

        adminService.enrollExistingLecturer(1L);

        verify(lecturerRepository).save(lim);
        assertEquals(10L, lim.getCourse().getId());
        verify(adminCourseAssignmentRepository, never()).save(any());
    }

    @Test
    void anAssessorAlreadyInThisCourseCannotBeAddedAgain() {
        Lecturer lim = lecturer(1L, "Dr Lim", "lim@uni.edu.my", thisCourse);
        when(lecturerRepository.findById(1L)).thenReturn(Optional.of(lim));

        assertThrows(RuntimeException.class, () -> adminService.enrollExistingLecturer(1L));
        verify(adminCourseAssignmentRepository, never()).save(any());
    }

    @Test
    void existingAccountsAreFoundByEmailForTheAddMessage() {
        Student chen = student(7L, "Chen Wei", "chen@uni.edu.my", otherCourse);
        when(studentRepository.findFirstByEmailIgnoreCaseOrderByIdAsc("chen@uni.edu.my")).thenReturn(Optional.of(chen));
        when(studentRepository.findFirstByEmailIgnoreCaseOrderByIdAsc("new@uni.edu.my")).thenReturn(Optional.empty());

        assertTrue(adminService.findExistingStudent(" Chen@Uni.edu.my ", null).isPresent());
        assertFalse(adminService.findExistingStudent("new@uni.edu.my", "new").isPresent());
    }
}
