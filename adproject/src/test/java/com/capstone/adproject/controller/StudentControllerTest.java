package com.capstone.adproject.controller;

import java.math.BigDecimal;
import java.security.Principal;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import com.capstone.adproject.model.Assessment;
import com.capstone.adproject.model.Course;
import com.capstone.adproject.model.Group;
import com.capstone.adproject.model.Mark;
import com.capstone.adproject.model.Rating;
import com.capstone.adproject.model.Rubric;
import com.capstone.adproject.model.Student;
import com.capstone.adproject.model.StudentAssessmentAssignment;
import com.capstone.adproject.repositories.AssessmentRepository;
import com.capstone.adproject.repositories.CourseRepository;
import com.capstone.adproject.repositories.MarkRepository;
import com.capstone.adproject.repositories.RatingRepository;
import com.capstone.adproject.repositories.RubricRepository;
import com.capstone.adproject.repositories.StudentAssessmentAssignmentRepository;
import com.capstone.adproject.repositories.StudentCourseAssignmentRepository;
import com.capstone.adproject.repositories.StudentRepository;
import com.capstone.adproject.service.AssessmentCommentService;
import com.capstone.adproject.service.AssessmentService;
import com.capstone.adproject.service.AssessmentSubmissionService;
import com.capstone.adproject.service.DeadlineService;
import com.capstone.adproject.service.MarkService;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
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
class StudentControllerTest {

    private MockMvc mockMvc;

    @Mock
    private AssessmentService assessmentService;

    @Mock
    private DeadlineService deadlineService;

    @Mock
    private StudentRepository studentRepository;

    @Mock
    private AssessmentRepository assessmentRepository;

    @Mock
    private RubricRepository rubricRepository;

    @Mock
    private RatingRepository ratingRepository;

    @Mock
    private MarkService markService;

    @Mock
    private AssessmentCommentService commentService;

    @Mock
    private MarkRepository markRepository;

    @Mock
    private StudentAssessmentAssignmentRepository studentAssessmentAssignmentRepository;

    @Mock
    private StudentCourseAssignmentRepository studentCourseAssignmentRepository;

    @Mock
    private AssessmentSubmissionService assessmentSubmissionService;

    @Mock
    private CourseRepository courseRepository;

    @InjectMocks
    private StudentController studentController;

    private Student student1;
    private Student student2;
    private Course course;
    private Group group;
    private Assessment assessment;
    private Rubric rubric;
    private Rating rating;
    private StudentAssessmentAssignment assignment;
    private Principal principal;

    @BeforeEach
    void setUp() {
        mockMvc = MockMvcBuilders.standaloneSetup(studentController).build();

        course = new Course();
        course.setId(1L);
        course.setCourseName("Software Engineering");

        group = new Group();
        group.setId(10L);
        group.setGroupName("Dev Team");
        group.setCourse(course);

        student1 = new Student();
        student1.setId(101L);
        student1.setUsername("student1");
        student1.setEmail("student1@example.com");
        student1.setCourse(course);
        student1.setGroup(group);

        student2 = new Student();
        student2.setId(102L);
        student2.setUsername("student2");
        student2.setEmail("student2@example.com");
        student2.setCourse(course);
        student2.setGroup(group);

        assessment = new Assessment();
        assessment.setId(50L);
        assessment.setTitle("Sprint 1 Evaluation");
        assessment.setCourse(course);
        assessment.setRubrics(new ArrayList<>());

        rubric = new Rubric();
        rubric.setId(501L);
        rubric.setAssessment(assessment);
        rubric.setAssessmentTypes("Peer Assessment");
        rubric.setMarks(BigDecimal.valueOf(10.0));
        rubric.setClo(1);
        rubric.setCloMarks(10.0);
        assessment.getRubrics().add(rubric);

        rating = new Rating();
        rating.setId(701L);
        rating.setMarks(BigDecimal.valueOf(8.5));
        rating.setName("Good");

        assignment = new StudentAssessmentAssignment();
        assignment.setAssessment(assessment);
        assignment.setStudent(student1);
        assignment.setPeerAssessment(true);
        assignment.setSelfAssessment(true);

        principal = () -> "student1@example.com";
    }

    @Test
    void testStudentHome_LoadsHomeDashboard() throws Exception {
        when(studentRepository.findByEmail("student1@example.com")).thenReturn(Optional.of(student1));
        when(assessmentService.findAllAssessmentsWithRubrics()).thenReturn(List.of(assessment));
        when(studentAssessmentAssignmentRepository.existsByAssessmentAndStudent(assessment, student1)).thenReturn(true);
        when(deadlineService.getDeadlinesByCourseId(1L)).thenReturn(Collections.emptyList());
        when(studentRepository.findByGroup(group)).thenReturn(List.of(student1, student2));
        when(markService.getAssessmentProgress(eq(student1), eq(assessment), anyList())).thenReturn(Map.of());
        when(deadlineService.getDeadlinesByAssessmentId(50L)).thenReturn(Collections.emptyList());
        when(studentAssessmentAssignmentRepository.findByAssessment(assessment)).thenReturn(List.of(assignment));
        when(assessmentRepository.findAll()).thenReturn(List.of(assessment));

        mockMvc.perform(get("/student/home").principal(principal))
                .andExpect(status().isOk())
                .andExpect(view().name("student_home"))
                .andExpect(model().attributeExists("allAssessments", "currentStudent", "hasGroup"));
    }

    @Test
    void testListAssessments_LoadsAssessments() throws Exception {
        when(studentRepository.findByEmail("student1@example.com")).thenReturn(Optional.of(student1));
        when(assessmentRepository.findAll()).thenReturn(List.of(assessment));
        when(studentAssessmentAssignmentRepository.existsByAssessmentAndStudent(assessment, student1)).thenReturn(true);
        when(deadlineService.getDeadlinesByAssessmentId(50L)).thenReturn(Collections.emptyList());
        when(studentAssessmentAssignmentRepository.findByAssessment(assessment)).thenReturn(List.of(assignment));
        when(studentRepository.findByGroup(group)).thenReturn(List.of(student1, student2));

        mockMvc.perform(get("/student/assessments").principal(principal))
                .andExpect(status().isOk())
                .andExpect(view().name("student_assessments"))
                .andExpect(model().attributeExists("currentStudent"));
    }

    @Test
    void testShowPeerAssessmentForm_Authorized_RendersPeerForm() throws Exception {
        when(studentRepository.findByEmail("student1@example.com")).thenReturn(Optional.of(student1));
        when(assessmentRepository.findById(50L)).thenReturn(Optional.of(assessment));
        when(deadlineService.getDeadlinesByAssessmentId(50L)).thenReturn(Collections.emptyList());
        when(studentAssessmentAssignmentRepository.findByAssessment(assessment)).thenReturn(List.of(assignment));
        when(studentRepository.findById(102L)).thenReturn(Optional.of(student2));
        when(markService.getMarksForStudentEvaluation(student1, student2, assessment)).thenReturn(Collections.emptyList());
        when(commentService.getExistingComments(eq(101L), any(), eq(student2), eq(assessment))).thenReturn(Collections.emptyList());

        mockMvc.perform(get("/student/assessment/50/evaluate/102").principal(principal))
                .andExpect(status().isOk())
                .andExpect(view().name("peer_assessment_form"))
                .andExpect(model().attribute("isSelfAssessment", false))
                .andExpect(model().attributeExists("assessment", "rubrics", "evaluator", "evaluatedStudent"));
    }

    @Test
    void testShowPeerAssessmentForm_DifferentGroup_RedirectsWithError() throws Exception {
        Student stranger = new Student();
        stranger.setId(999L);
        stranger.setUsername("stranger");
        stranger.setCourse(course);
        Group otherGroup = new Group();
        otherGroup.setId(99L);
        stranger.setGroup(otherGroup);

        when(studentRepository.findByEmail("student1@example.com")).thenReturn(Optional.of(student1));
        when(assessmentRepository.findById(50L)).thenReturn(Optional.of(assessment));
        when(deadlineService.getDeadlinesByAssessmentId(50L)).thenReturn(Collections.emptyList());
        when(studentAssessmentAssignmentRepository.findByAssessment(assessment)).thenReturn(List.of(assignment));
        when(studentRepository.findById(999L)).thenReturn(Optional.of(stranger));

        mockMvc.perform(get("/student/assessment/50/evaluate/999").principal(principal))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/student/assessments"))
                .andExpect(flash().attribute("error", "You can only evaluate members of your own group."));
    }

    @Test
    void testSubmitPeerAssessment_ValidRating_SavesMarksAndRedirects() throws Exception {
        when(studentRepository.findByEmail("student1@example.com")).thenReturn(Optional.of(student1));
        when(assessmentRepository.findById(50L)).thenReturn(Optional.of(assessment));
        when(deadlineService.getDeadlinesByAssessmentId(50L)).thenReturn(Collections.emptyList());
        when(studentAssessmentAssignmentRepository.findByAssessment(assessment)).thenReturn(List.of(assignment));
        when(studentRepository.findById(102L)).thenReturn(Optional.of(student2));
        when(markService.getMarksForStudentEvaluation(student1, student2, assessment)).thenReturn(Collections.emptyList());
        when(commentService.getExistingComments(eq(101L), any(), eq(student2), eq(assessment))).thenReturn(Collections.emptyList());
        when(rubricRepository.findById(501L)).thenReturn(Optional.of(rubric));
        when(ratingRepository.findById(701L)).thenReturn(Optional.of(rating));

        mockMvc.perform(post("/student/assessment/50/evaluate/102/submit")
                .principal(principal)
                .param("rubric_501", "701"))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/student/assessments"))
                .andExpect(flash().attribute("success", "Assessment and comments submitted successfully!"));

        verify(markService).saveAllMarks(anyList());
    }

    @Test
    void testViewMyComments_LoadsCompiledComments() throws Exception {
        when(studentRepository.findByEmail("student1@example.com")).thenReturn(Optional.of(student1));
        when(commentService.getCompiledCommentsForStudent(student1)).thenReturn(Map.of(
                "commentsByAssessment", Map.of(),
                "totalComments", 0
        ));

        mockMvc.perform(get("/student/comments").principal(principal))
                .andExpect(status().isOk())
                .andExpect(view().name("student_comments"))
                .andExpect(model().attributeExists("totalComments", "currentStudent"));
    }

    @Test
    void testViewAssessmentResults_LoadsMarksAndAverages() throws Exception {
        when(studentRepository.findByEmail("student1@example.com")).thenReturn(Optional.of(student1));
        when(assessmentRepository.findById(50L)).thenReturn(Optional.of(assessment));

        Mark mark = new Mark();
        mark.setEvaluatedStudent(student1);
        mark.setAssessment(assessment);
        mark.setRubric(rubric);
        mark.setMarkValue(9.0);

        when(markRepository.findByEvaluatedStudentAndAssessment(student1, assessment)).thenReturn(List.of(mark));
        when(markService.calculateTotalMarks(student1, assessment)).thenReturn(90.0);
        when(markService.calculateCloMarks(student1, assessment)).thenReturn(Map.of(1, 9.0));

        mockMvc.perform(get("/student/assessment/50/results").principal(principal))
                .andExpect(status().isOk())
                .andExpect(view().name("assessment_results"))
                .andExpect(model().attribute("totalMarks", 90.0))
                .andExpect(model().attributeExists("receivedMarks", "averageScores", "cloMarks"));
    }
}
