package com.capstone.adproject.service;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Optional;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import com.capstone.adproject.dto.AssessmentDataDto;
import com.capstone.adproject.model.Assessment;
import com.capstone.adproject.model.Student;
import com.capstone.adproject.repositories.AssessmentCommentRepository;
import com.capstone.adproject.repositories.FactorWeightageRepository;
import com.capstone.adproject.repositories.LecturerGroupAssignmentRepository;
import com.capstone.adproject.repositories.MarkRepository;
import com.capstone.adproject.repositories.StudentResultOverrideRepository;

@ExtendWith(MockitoExtension.class)
class CalculateServiceTest {

    @Mock
    private MarkRepository markRepository;

    @Mock
    private LecturerGroupAssignmentRepository lecturerAssignmentRepository;

    @Mock
    private AssessmentCommentRepository commentRepository;

    @Mock
    private StudentResultOverrideRepository overrideRepository;

    @Mock
    private FactorWeightageRepository factorWeightageRepository;

    @InjectMocks
    private CalculateService calculateService;

    private Assessment assessment;

    @BeforeEach
    void setUp() {
        assessment = new Assessment();
        assessment.setId(1L);
        assessment.setTitle("Milestone 1");
        assessment.setRubrics(new ArrayList<>());
    }

    @Test
    void testCalculateAssessmentData_NullSafeSorting_NoNullPointerException() {
        Student s1 = new Student();
        s1.setId(1L);
        s1.setUsername("charlie");
        s1.setEmail("charlie@utm.my");

        Student s2 = new Student();
        s2.setId(2L);
        s2.setUsername(null); // Null username
        s2.setEmail("alice@utm.my");

        Student s3 = new Student();
        s3.setId(3L);
        s3.setUsername("Bob"); // Mixed case username
        s3.setEmail("bob@utm.my");

        Student s4 = new Student();
        s4.setId(4L);
        s4.setUsername("   "); // Blank whitespace username
        s4.setEmail("david@utm.my");

        Student s5 = new Student();
        s5.setId(5L);
        s5.setUsername(null);
        s5.setEmail(null); // Both null

        List<Student> students = new ArrayList<>(List.of(s1, s2, s3, s4, s5));

        when(markRepository.findByAssessment(assessment)).thenReturn(Collections.emptyList());
        when(overrideRepository.findByStudent(any())).thenReturn(Optional.empty());

        AssessmentDataDto result = assertDoesNotThrow(() ->
            calculateService.calculateAssessmentData(assessment, students)
        );

        assertNotNull(result);
        assertEquals(5, result.getStudentDataList().size());

        // Verify the sorted order:
        // 1. s5: "" (null username, null email)
        // 2. s2: "alice@utm.my" (null username, email fallback)
        // 3. s3: "Bob" (case-insensitive)
        // 4. s1: "charlie"
        // 5. s4: "david@utm.my" (blank username, email fallback)
        assertEquals(5L, result.getStudentDataList().get(0).getStudent().getId());
        assertEquals(2L, result.getStudentDataList().get(1).getStudent().getId());
        assertEquals(3L, result.getStudentDataList().get(2).getStudent().getId());
        assertEquals(1L, result.getStudentDataList().get(3).getStudent().getId());
        assertEquals(4L, result.getStudentDataList().get(4).getStudent().getId());
    }
}
