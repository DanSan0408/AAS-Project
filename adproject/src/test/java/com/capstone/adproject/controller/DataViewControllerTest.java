package com.capstone.adproject.controller;

import java.util.Collections;
import java.util.List;
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
import com.capstone.adproject.repositories.FactorOverrideHistoryRepository;
import com.capstone.adproject.repositories.FactorWeightageRepository;
import com.capstone.adproject.repositories.GroupRepository;
import com.capstone.adproject.repositories.MarkRepository;
import com.capstone.adproject.repositories.StudentResultOverrideRepository;
import com.capstone.adproject.service.AdminService;
import com.capstone.adproject.service.AssessmentService;
import com.capstone.adproject.service.CalculateService;
import com.capstone.adproject.service.CourseScopeService;
import com.capstone.adproject.service.StudentService;

import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.model;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.view;

@ExtendWith(MockitoExtension.class)
class DataViewControllerTest {

    private MockMvc mockMvc;

    @Mock
    private AssessmentService assessmentService;

    @Mock
    private MarkRepository markRepository;

    @Mock
    private GroupRepository groupRepository;

    @Mock
    private StudentService studentService;

    @Mock
    private StudentResultOverrideRepository overrideRepository;

    @Mock
    private CalculateService calculateService;

    @Mock
    private CourseScopeService courseScopeService;

    @Mock
    private AdminService adminService;

    @Mock
    private FactorWeightageRepository factorWeightageRepository;

    @Mock
    private FactorOverrideHistoryRepository factorOverrideHistoryRepository;

    @InjectMocks
    private DataViewController dataViewController;

    private Course course;
    private Assessment assessment;

    @BeforeEach
    void setUp() {
        mockMvc = MockMvcBuilders.standaloneSetup(dataViewController).build();

        course = new Course();
        course.setId(10L);
        course.setCourseName("Data Analysis");

        assessment = new Assessment();
        assessment.setId(100L);
        assessment.setTitle("Midterm Evaluation");
        assessment.setCourse(course);
    }

    @Test
    void testShowDataViewsHome_LoadsAssessmentsForCourse() throws Exception {
        when(courseScopeService.getActiveCourseIdForCurrentUser()).thenReturn(10L);
        when(assessmentService.findAllAssessmentsWithRubricsByCourseId(10L)).thenReturn(List.of(assessment));

        mockMvc.perform(get("/admin/data-views"))
                .andExpect(status().isOk())
                .andExpect(view().name("data_views_home"))
                .andExpect(model().attributeExists("assessments", "adminUsername"));
    }

    @Test
    void testShowFactorsDisplayPage_LoadsGroups() throws Exception {
        when(courseScopeService.getActiveCourseIdForCurrentUser()).thenReturn(10L);
        when(groupRepository.findByCourseId(10L)).thenReturn(Collections.emptyList());

        mockMvc.perform(get("/admin/data-views/factors"))
                .andExpect(status().isOk())
                .andExpect(view().name("factor_display"))
                .andExpect(model().attributeExists("groups"));
    }
}
