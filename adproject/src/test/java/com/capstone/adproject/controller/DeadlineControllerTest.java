package com.capstone.adproject.controller;

import java.util.Date;
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
import com.capstone.adproject.model.Deadline;
import com.capstone.adproject.service.AssessmentService;
import com.capstone.adproject.service.CourseScopeService;
import com.capstone.adproject.service.DeadlineService;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
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
class DeadlineControllerTest {

    private MockMvc mockMvc;

    @Mock
    private DeadlineService deadlineService;

    @Mock
    private CourseScopeService courseScopeService;

    @Mock
    private AssessmentService assessmentService;

    @InjectMocks
    private DeadlineController deadlineController;

    private Course course;
    private Assessment assessment;
    private Deadline deadline;

    @BeforeEach
    void setUp() {
        mockMvc = MockMvcBuilders.standaloneSetup(deadlineController).build();

        course = new Course();
        course.setId(10L);
        course.setCourseName("Software Architecture");

        assessment = new Assessment();
        assessment.setId(100L);
        assessment.setTitle("Sprint 1");
        assessment.setCourse(course);

        deadline = new Deadline();
        deadline.setId(500L);
        deadline.setTitle("Sprint 1 Final Deliverable");
        deadline.setCourseId(10L);
        deadline.setAssessmentId(100L);
        deadline.setDate(new Date());
    }

    @Test
    void testSaveDeadline_NoActiveCourse_RedirectsWithErrorMessage() throws Exception {
        when(courseScopeService.getActiveCourseIdForCurrentUser()).thenReturn(null);

        mockMvc.perform(post("/deadlines/save")
                .param("title", "Project Deadline"))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/admin/home"))
                .andExpect(flash().attribute("errorMessage", "No active course selected."));

        verify(deadlineService, never()).save(any());
    }

    @Test
    void testSaveDeadline_EmptyTitle_RedirectsWithErrorMessage() throws Exception {
        when(courseScopeService.getActiveCourseIdForCurrentUser()).thenReturn(10L);

        mockMvc.perform(post("/deadlines/save")
                .param("title", "   "))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/admin/home"))
                .andExpect(flash().attribute("errorMessage", "Title cannot be empty!"));

        verify(deadlineService, never()).save(any());
    }

    @Test
    void testSaveDeadline_DuplicateTitle_RedirectsWithDuplicateWarning() throws Exception {
        when(courseScopeService.getActiveCourseIdForCurrentUser()).thenReturn(10L);
        when(deadlineService.isTitleDuplicateIgnoringWhitespace(eq("Sprint 1 Final Deliverable"), any(), eq(10L)))
                .thenReturn(true);
        when(deadlineService.getDeadlinesByCourseId(10L)).thenReturn(List.of(deadline));

        mockMvc.perform(post("/deadlines/save")
                .param("title", "Sprint 1 Final Deliverable")
                .param("confirmDuplicate", "false"))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/admin/home"))
                .andExpect(flash().attribute("isDuplicate", true))
                .andExpect(flash().attributeExists("deadlineToSave"));

        verify(deadlineService, never()).save(any());
    }

    @Test
    void testSaveDeadline_Valid_SavesSuccessfully() throws Exception {
        when(courseScopeService.getActiveCourseIdForCurrentUser()).thenReturn(10L);
        when(deadlineService.isTitleDuplicateIgnoringWhitespace(eq("New Deadline"), any(), eq(10L)))
                .thenReturn(false);

        mockMvc.perform(post("/deadlines/save")
                .param("title", "New Deadline")
                .param("confirmDuplicate", "false"))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/admin/home"))
                .andExpect(flash().attribute("successMessage", "Deadline saved successfully!"));

        verify(deadlineService).save(any(Deadline.class));
    }

    @Test
    void testEditDeadline_Authorized_LoadsDeadline() throws Exception {
        when(deadlineService.getDeadlineById(500L)).thenReturn(Optional.of(deadline));
        when(courseScopeService.getActiveCourseIdForCurrentUser()).thenReturn(10L);

        mockMvc.perform(get("/deadlines/edit/500"))
                .andExpect(status().isOk())
                .andExpect(view().name("edit-deadline"))
                .andExpect(model().attributeExists("deadline"));
    }

    @Test
    void testEditDeadline_NotFound_RedirectsToAdminHome() throws Exception {
        when(deadlineService.getDeadlineById(999L)).thenReturn(Optional.empty());

        mockMvc.perform(get("/deadlines/edit/999"))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/admin/home"));
    }

    @Test
    void testUpdateDeadline_Authorized_UpdatesSuccessfully() throws Exception {
        when(courseScopeService.getActiveCourseIdForCurrentUser()).thenReturn(10L);

        mockMvc.perform(post("/deadlines/update")
                .param("id", "500")
                .param("title", "Updated Milestone")
                .param("courseId", "10"))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/admin/home"))
                .andExpect(flash().attribute("successMessage", "Deadline updated successfully!"));

        verify(deadlineService).save(any(Deadline.class));
    }

    @Test
    void testDeleteDeadline_Authorized_DeletesSuccessfully() throws Exception {
        when(deadlineService.getDeadlineById(500L)).thenReturn(Optional.of(deadline));
        when(courseScopeService.getActiveCourseIdForCurrentUser()).thenReturn(10L);

        mockMvc.perform(post("/deadlines/delete/500"))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/admin/home"))
                .andExpect(flash().attribute("successMessage", "Deadline deleted successfully!"));

        verify(deadlineService).deleteDeadline(500L);
    }
}
