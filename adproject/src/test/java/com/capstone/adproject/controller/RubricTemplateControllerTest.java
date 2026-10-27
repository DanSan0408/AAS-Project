package com.capstone.adproject.controller;

import java.util.List;
import java.util.Optional;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import com.capstone.adproject.model.Course;
import com.capstone.adproject.model.RubricTemplate;
import com.capstone.adproject.service.CourseScopeService;
import com.capstone.adproject.service.RubricTemplateService;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.flash;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.model;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.redirectedUrl;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.view;

@ExtendWith(MockitoExtension.class)
class RubricTemplateControllerTest {

    private MockMvc mockMvc;

    @Mock
    private RubricTemplateService rubricTemplateService;

    @Mock
    private CourseScopeService courseScopeService;

    @InjectMocks
    private RubricTemplateController rubricTemplateController;

    private Course course;
    private RubricTemplate template;

    @BeforeEach
    void setUp() {
        mockMvc = MockMvcBuilders.standaloneSetup(rubricTemplateController).build();

        course = new Course();
        course.setId(1L);
        course.setCourseName("SWE 101");

        template = new RubricTemplate();
        template.setId(10L);
        template.setName("Standard Rubric");
        template.setCourse(course);
        template.setStructureData("{\"criteria\": []}");
    }

    @Test
    void testManageTemplates_LoadsTemplatesForActiveCourse() throws Exception {
        when(rubricTemplateService.getTemplatesForActiveCourse()).thenReturn(List.of(template));

        mockMvc.perform(get("/rubrics/templates/manage"))
                .andExpect(status().isOk())
                .andExpect(view().name("rubric_template_manage"))
                .andExpect(model().attributeExists("templates", "pageTitle"));
    }

    @Test
    void testAddTemplateForm_LoadsTemplateBuilder() throws Exception {
        mockMvc.perform(get("/rubrics/templates/add"))
                .andExpect(status().isOk())
                .andExpect(view().name("rubric_template_builder"))
                .andExpect(model().attributeExists("rubricTemplate", "pageTitle"));
    }

    @Test
    void testEditTemplateForm_Found_LoadsBuilder() throws Exception {
        when(rubricTemplateService.getTemplateById(10L)).thenReturn(Optional.of(template));

        mockMvc.perform(get("/rubrics/templates/edit/10"))
                .andExpect(status().isOk())
                .andExpect(view().name("rubric_template_builder"))
                .andExpect(model().attribute("rubricTemplate", template));
    }

    @Test
    void testEditTemplateForm_NotFound_RedirectsWithErrorMessage() throws Exception {
        when(rubricTemplateService.getTemplateById(99L)).thenReturn(Optional.empty());

        mockMvc.perform(get("/rubrics/templates/edit/99"))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/rubrics/templates/manage"))
                .andExpect(flash().attribute("errorMessage", "Blueprint not found."));
    }

    @Test
    void testSaveTemplate_ActiveCourse_PersistsTemplate() throws Exception {
        when(courseScopeService.getActiveCourseIdForCurrentUser()).thenReturn(1L);
        when(courseScopeService.getActiveCourseForCurrentUser()).thenReturn(course);

        mockMvc.perform(post("/rubrics/templates/save")
                .param("templateName", "New Template")
                .param("structureData", "{\"items\":[]}"))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/rubrics/templates/manage"))
                .andExpect(flash().attribute("successMessage", "Rubric Template saved successfully."));

        verify(rubricTemplateService).saveTemplate(any(RubricTemplate.class));
    }

    @Test
    void testDeleteTemplate_Authorized_DeletesSuccessfully() throws Exception {
        when(courseScopeService.getActiveCourseIdForCurrentUser()).thenReturn(1L);
        when(rubricTemplateService.getTemplateById(10L)).thenReturn(Optional.of(template));

        mockMvc.perform(post("/rubrics/templates/delete/10"))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/rubrics/templates/manage"))
                .andExpect(flash().attribute("successMessage", "Rubric Blueprint deleted successfully."));

        verify(rubricTemplateService).deleteTemplate(10L);
    }

    @Test
    void testGetTemplateJson_Found_ReturnsJson() throws Exception {
        when(rubricTemplateService.getTemplateById(10L)).thenReturn(Optional.of(template));

        mockMvc.perform(get("/rubrics/templates/api/10"))
                .andExpect(status().isOk())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON))
                .andExpect(content().string("{\"criteria\": []}"));
    }
}
