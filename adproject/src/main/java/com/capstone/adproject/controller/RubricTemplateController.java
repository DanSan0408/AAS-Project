package com.capstone.adproject.controller;

import java.util.Optional;

import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseBody;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import com.capstone.adproject.model.RubricTemplate;
import com.capstone.adproject.service.RubricTemplateService;
import com.capstone.adproject.service.CourseScopeService;

@Controller
@RequestMapping("/rubrics/templates")
public class RubricTemplateController {

    private final RubricTemplateService rubricTemplateService;
    private final CourseScopeService courseScopeService;

    public RubricTemplateController(RubricTemplateService rubricTemplateService, CourseScopeService courseScopeService) {
        this.rubricTemplateService = rubricTemplateService;
        this.courseScopeService = courseScopeService;
    }

    @GetMapping("/manage")
    public String manageTemplates(Model model) {
        model.addAttribute("templates", rubricTemplateService.getTemplatesForActiveCourse());
        model.addAttribute("pageTitle", "Manage Rubric Templates");
        return "rubric_template_manage";
    }

    @GetMapping("/add")
    public String addTemplateForm(Model model) {
        model.addAttribute("rubricTemplate", new RubricTemplate());
        model.addAttribute("pageTitle", "Create Rubric Template Blueprint");
        return "rubric_template_builder";
    }

    @GetMapping("/edit/{id}")
    public String editTemplateForm(@PathVariable Long id, Model model, RedirectAttributes redirectAttributes) {
        Optional<RubricTemplate> templateOpt = rubricTemplateService.getTemplateById(id);
        if (templateOpt.isEmpty()) {
            redirectAttributes.addFlashAttribute("errorMessage", "Blueprint not found.");
            return "redirect:/rubrics/templates/manage";
        }
        model.addAttribute("rubricTemplate", templateOpt.get());
        model.addAttribute("pageTitle", "Edit Rubric Template Blueprint");
        return "rubric_template_builder";
    }

    @PostMapping("/save")
    public String saveTemplate(@ModelAttribute("rubricTemplate") RubricTemplate rubricTemplate, RedirectAttributes redirectAttributes) {
        Long activeCourseId = courseScopeService.getActiveCourseIdForCurrentUser();
        if (activeCourseId == null) {
            redirectAttributes.addFlashAttribute("errorMessage", "No active course selected.");
            return "redirect:/rubrics/templates/manage";
        }

        if (rubricTemplate.getId() != null) {
            Optional<RubricTemplate> existing = rubricTemplateService.getTemplateById(rubricTemplate.getId());
            if (existing.isEmpty() || existing.get().getCourse() == null || !existing.get().getCourse().getId().equals(activeCourseId)) {
                redirectAttributes.addFlashAttribute("errorMessage", "Security validation failed: Unauthorized to edit this blueprint.");
                return "redirect:/rubrics/templates/manage";
            }
        }

        rubricTemplate.setCourse(courseScopeService.getActiveCourseForCurrentUser());
        rubricTemplateService.saveTemplate(rubricTemplate);
        redirectAttributes.addFlashAttribute("successMessage", "Rubric Template saved successfully.");
        return "redirect:/rubrics/templates/manage";
    }

    @PostMapping("/delete/{id}")
    public String deleteTemplate(@PathVariable Long id, RedirectAttributes redirectAttributes) {
        Long activeCourseId = courseScopeService.getActiveCourseIdForCurrentUser();
        Optional<RubricTemplate> existing = rubricTemplateService.getTemplateById(id);
        if (existing.isEmpty() || existing.get().getCourse() == null || !existing.get().getCourse().getId().equals(activeCourseId)) {
            redirectAttributes.addFlashAttribute("errorMessage", "Security validation failed: Unauthorized to delete this blueprint.");
            return "redirect:/rubrics/templates/manage";
        }
        
        rubricTemplateService.deleteTemplate(id);
        redirectAttributes.addFlashAttribute("successMessage", "Rubric Blueprint deleted successfully.");
        return "redirect:/rubrics/templates/manage";
    }

    @GetMapping("/api/{id}")
    @ResponseBody
    public ResponseEntity<String> getTemplateJson(@PathVariable Long id) {
        Optional<RubricTemplate> templateOpt = rubricTemplateService.getTemplateById(id);
        if (templateOpt.isPresent() && templateOpt.get().getStructureData() != null) {
            return ResponseEntity.ok().header("Content-Type", "application/json").body(templateOpt.get().getStructureData());
        }
        return ResponseEntity.notFound().build();
    }
}