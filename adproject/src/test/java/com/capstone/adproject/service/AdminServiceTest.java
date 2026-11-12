package com.capstone.adproject.service;

import java.util.List;
import java.util.Optional;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.crypto.password.PasswordEncoder;

import com.capstone.adproject.dto.GroupAssignmentDto;
import com.capstone.adproject.model.Course;
import com.capstone.adproject.model.Group;
import com.capstone.adproject.model.Lecturer;
import com.capstone.adproject.model.Student;
import com.capstone.adproject.model.StudentCourseAssignment;
import com.capstone.adproject.repositories.AdminRepository;
import com.capstone.adproject.repositories.GroupRepository;
import com.capstone.adproject.repositories.LecturerGroupAssignmentRepository;
import com.capstone.adproject.repositories.LecturerRepository;
import com.capstone.adproject.repositories.StudentCourseAssignmentRepository;
import com.capstone.adproject.repositories.StudentRepository;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.atLeastOnce;
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
        when(lecturerRepository.findByCourseIdAndEmailContainingIgnoreCase(20L, "lec")).thenReturn(List.of(lecturerB));

        List<Lecturer> results = adminService.searchLecturers("lec");

        assertEquals(1, results.size());
        assertEquals(9L, results.get(0).getId());
        verify(lecturerRepository).findByCourseIdAndEmailContainingIgnoreCase(20L, "lec");
        verify(lecturerRepository, never()).findByEmailContainingIgnoreCase("lec");
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
}
