package com.capstone.adproject.service;

import java.util.Collections;
import java.util.List;
import java.util.Optional;

import org.springframework.stereotype.Service;

import com.capstone.adproject.model.Course;
import com.capstone.adproject.model.RubricTemplate;
import com.capstone.adproject.repositories.RubricTemplateRepository;

@Service
public class RubricTemplateService {

    private final RubricTemplateRepository rubricTemplateRepository;
    private final CourseScopeService courseScopeService;

    public RubricTemplateService(RubricTemplateRepository rubricTemplateRepository, CourseScopeService courseScopeService) {
        this.rubricTemplateRepository = rubricTemplateRepository;
        this.courseScopeService = courseScopeService;
    }

    public List<RubricTemplate> getTemplatesForActiveCourse() {
        Long courseId = courseScopeService.getActiveCourseIdForCurrentUser();
        if (courseId == null) {
            return Collections.emptyList();
        }
        return rubricTemplateRepository.findByCourseId(courseId);
    }

    public RubricTemplate saveTemplate(RubricTemplate template) {
        Course activeCourse = courseScopeService.getActiveCourseForCurrentUser();
        if (activeCourse != null) {
            template.setCourse(activeCourse);
        }
        return rubricTemplateRepository.save(template);
    }

    public Optional<RubricTemplate> getTemplateById(Long id) {
        return rubricTemplateRepository.findById(id);
    }

    public void deleteTemplate(Long id) {
        // The frontend will only show templates for the active course, safe to delete by ID here
        rubricTemplateRepository.deleteById(id);
    }
}