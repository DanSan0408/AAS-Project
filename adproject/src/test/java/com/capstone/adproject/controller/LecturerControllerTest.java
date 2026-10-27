package com.capstone.adproject.controller;

import java.util.Collections;
import java.util.Date;
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

import com.capstone.adproject.model.Assessment;
import com.capstone.adproject.model.Course;
import com.capstone.adproject.model.Deadline;
import com.capstone.adproject.model.Group;
import com.capstone.adproject.model.Lecturer;
import com.capstone.adproject.model.LecturerGroupAssignment;
import com.capstone.adproject.repositories.LecturerGroupAssignmentRepository;
import com.capstone.adproject.repositories.LecturerRepository;
import com.capstone.adproject.repositories.LecturerRubricAssignmentRepository;
import com.capstone.adproject.repositories.LecturerStudentAssignmentRepository;
import com.capstone.adproject.service.AssessmentService;
import com.capstone.adproject.service.DeadlineService;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.model;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.view;

@ExtendWith(MockitoExtension.class)
class LecturerControllerTest {

    private MockMvc mockMvc;

    @Mock
    private AssessmentService assessmentService;

    @Mock
    private DeadlineService deadlineService;

    @Mock
    private LecturerRepository lecturerRepository;

    @Mock
    private LecturerGroupAssignmentRepository assignmentRepository;

    @Mock
    private LecturerRubricAssignmentRepository rubricAssignmentRepository;

    @Mock
    private LecturerStudentAssignmentRepository studentAssignmentRepository;

    @InjectMocks
    private LecturerController lecturerController;

    private Lecturer lecturer;
    private Course course;
    private Assessment assessment;

    @BeforeEach
    void setUp() {
        mockMvc = MockMvcBuilders.standaloneSetup(lecturerController).build();

        course = new Course();
        course.setId(10L);
        course.setCourseName("SWE 301");

        lecturer = new Lecturer();
        lecturer.setId(5L);
        lecturer.setEmail("lecturer@domain.com");
        lecturer.setUsername("dr_smith");
        lecturer.setCourse(course);

        assessment = new Assessment();
        assessment.setId(100L);
        assessment.setTitle("Milestone 1");
        assessment.setCourse(course);
    }

    @Test
    void testLecturerHome_LoadsAssessmentsAndDeadlines() throws Exception {
        Authentication auth = new UsernamePasswordAuthenticationToken(
                "lecturer@domain.com", "pass", List.of(new SimpleGrantedAuthority("ROLE_LECTURER")));

        when(lecturerRepository.findByEmail("lecturer@domain.com")).thenReturn(Optional.of(lecturer));
        when(assignmentRepository.findAssessmentsByLecturer(lecturer)).thenReturn(List.of(assessment));
        when(rubricAssignmentRepository.findAssessmentsByLecturer(lecturer)).thenReturn(Collections.emptyList());
        when(studentAssignmentRepository.findAssessmentsByLecturer(lecturer)).thenReturn(Collections.emptyList());

        Group testGroup = new Group();
        testGroup.setId(20L);
        testGroup.setGroupName("Alpha Team");

        LecturerGroupAssignment lga = new LecturerGroupAssignment();
        lga.setAssessment(assessment);
        lga.setLecturer(lecturer);
        lga.setGroup(testGroup);
        when(assignmentRepository.findByAssessment(assessment)).thenReturn(List.of(lga));

        Deadline deadline = new Deadline();
        deadline.setId(1L);
        deadline.setTitle("Milestone 1 Due");
        deadline.setAssessmentId(100L);
        deadline.setCourseId(10L);
        deadline.setDate(new Date(System.currentTimeMillis() + 86400000L));
        when(deadlineService.getDeadlinesByCourseId(10L)).thenReturn(List.of(deadline));

        mockMvc.perform(get("/lecturer/home").principal(auth))
                .andExpect(status().isOk())
                .andExpect(view().name("lecturer_home"))
                .andExpect(model().attributeExists("pendingTasks", "allAssessments", "assessmentTargets"));
    }
}
