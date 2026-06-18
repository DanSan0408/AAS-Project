package com.capstone.adproject.controller;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.hibernate.Hibernate;
import org.springframework.stereotype.Controller;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import com.capstone.adproject.model.Assessment;
import com.capstone.adproject.model.Rating;
import com.capstone.adproject.model.Rubric;
import com.capstone.adproject.model.SubRubric;
import com.capstone.adproject.repositories.LecturerGroupAssignmentRepository;
import com.capstone.adproject.repositories.LecturerRubricAssignmentRepository;
import com.capstone.adproject.repositories.LecturerStudentAssignmentRepository;
import com.capstone.adproject.repositories.StudentAssessmentAssignmentRepository;
import com.capstone.adproject.service.AssessmentService;
import com.capstone.adproject.service.CourseScopeService;
import com.capstone.adproject.service.RubricService;
import com.capstone.adproject.service.RubricTemplateService;
import com.capstone.adproject.service.SuperAdminService;
import com.capstone.adproject.util.HtmlSanitizerUtil;

@Controller @RequestMapping("/rubrics") public class RubricController {

    private final RubricService rubricService;
    private final AssessmentService assessmentService;
    private final CourseScopeService courseScopeService;
    private final SuperAdminService superAdminService;
    private final RubricTemplateService rubricTemplateService;
    private final LecturerGroupAssignmentRepository lecturerGroupAssignmentRepository;
    private final LecturerRubricAssignmentRepository lecturerRubricAssignmentRepository;
    private final LecturerStudentAssignmentRepository lecturerStudentAssignmentRepository;
    private final StudentAssessmentAssignmentRepository studentAssessmentAssignmentRepository;

    public RubricController(RubricService rubricService, AssessmentService assessmentService,
            CourseScopeService courseScopeService, SuperAdminService superAdminService,
            RubricTemplateService rubricTemplateService,
            LecturerGroupAssignmentRepository lecturerGroupAssignmentRepository,
            LecturerRubricAssignmentRepository lecturerRubricAssignmentRepository,
            LecturerStudentAssignmentRepository lecturerStudentAssignmentRepository,
            StudentAssessmentAssignmentRepository studentAssessmentAssignmentRepository) {
        this.rubricService = rubricService;
        this.assessmentService = assessmentService;
        this.courseScopeService = courseScopeService;
        this.superAdminService = superAdminService;
        this.rubricTemplateService = rubricTemplateService;
        this.lecturerGroupAssignmentRepository = lecturerGroupAssignmentRepository;
        this.lecturerRubricAssignmentRepository = lecturerRubricAssignmentRepository;
        this.lecturerStudentAssignmentRepository = lecturerStudentAssignmentRepository;
        this.studentAssessmentAssignmentRepository = studentAssessmentAssignmentRepository;
    }

    private boolean ownsAssessment(Assessment assessment) {
        return assessment != null
                && assessment.getCourse() != null
                && assessment.getCourse().getId() != null
                && courseScopeService.isActiveCourseId(assessment.getCourse().getId());
    }

    private boolean ownsRubric(Rubric rubric) {
        return rubric != null && ownsAssessment(rubric.getAssessment());
    }

    @GetMapping("/manage")
    public String manageAssessments(Model model) {
        Long activeCourseId = courseScopeService.getActiveCourseIdForCurrentUser();
        model.addAttribute("assessments", activeCourseId == null
                ? List.of()
            : assessmentService.findAllAssessmentsWithRubricsByCourseId(activeCourseId));

        if (!model.containsAttribute("newAssessment")) {
            model.addAttribute("newAssessment", new Assessment());
        }

        return "manage-assessments";
    }

    @PostMapping("/assessment/save")
    public String saveAssessment(@ModelAttribute Assessment assessment,
            @RequestParam(value = "duplicateConfirmed", defaultValue = "false") boolean duplicateConfirmed,
            RedirectAttributes redirectAttributes) {

        Long assessmentId = assessment.getId();
        
        if (assessment.getTitle() != null) {
            assessment.setTitle(HtmlSanitizerUtil.sanitize(assessment.getTitle()));
        }

        if (!duplicateConfirmed) {
            Long activeCourseId = courseScopeService.getActiveCourseIdForCurrentUser();
            boolean isDuplicate = (activeCourseId == null ? List.<Assessment>of()
                    : assessmentService.findAllAssessmentsWithRubricsByCourseId(activeCourseId)).stream()
                    .filter(a -> assessmentId == null || !assessmentId.equals(a.getId()))
                    .anyMatch(a -> a.getTitle() != null
                            && a.getTitle().replaceAll("\\s+", "").equalsIgnoreCase(
                                    assessment.getTitle() == null ? "" : assessment.getTitle().replaceAll("\\s+", "")));

            if (isDuplicate) {
                redirectAttributes.addFlashAttribute("newAssessment", assessment);
                redirectAttributes.addFlashAttribute("duplicateFound", true);
                redirectAttributes.addFlashAttribute("errorMessage",
                        "An Assessment with the title '" + assessment.getTitle()
                                + "' already exists in your managed courses. Click 'Save' again to confirm the duplicate entry.");
                return "redirect:/rubrics/manage";
            }
        }

        if (assessment.getId() != null) {
            Assessment existingAssessment = rubricService.findAssessmentById(assessment.getId());
            if (!ownsAssessment(existingAssessment)) {
                redirectAttributes.addFlashAttribute("errorMessage",
                        "You are not authorized to update this assessment.");
                return "redirect:/rubrics/manage";
            }
            existingAssessment.setTitle(assessment.getTitle());
            rubricService.saveAssessment(existingAssessment);
            redirectAttributes.addFlashAttribute("successMessage", "Assessment updated successfully.");
        } else {
            Long activeCourseId = courseScopeService.getActiveCourseIdForCurrentUser();
            if (activeCourseId == null) {
                redirectAttributes.addFlashAttribute("errorMessage",
                        "No active course selected. Please select a course and try again.");
                return "redirect:/rubrics/manage";
            }

            if (!courseScopeService.isManagedCourseId(activeCourseId)) {
                redirectAttributes.addFlashAttribute("errorMessage",
                        "Assessment must belong to one of your managed courses.");
                return "redirect:/rubrics/manage";
            }

            // ✅ FIX: Validate course exists before setting
            var courseOpt = superAdminService.getCourseById(activeCourseId);
            if (courseOpt.isEmpty()) {
                redirectAttributes.addFlashAttribute("errorMessage",
                        "Selected course no longer exists. Please switch course and try again.");
                return "redirect:/rubrics/manage";
            }

            assessment.setCourse(courseOpt.get());

            if (assessment.getCourse() == null || assessment.getCourse().getId() == null) {
                redirectAttributes.addFlashAttribute("errorMessage",
                        "Selected course no longer exists. Please switch course and try again.");
                return "redirect:/rubrics/manage";
            }

            try {
                rubricService.saveAssessment(assessment);
                redirectAttributes.addFlashAttribute("successMessage", "New Assessment created successfully.");
            } catch (Exception e) {
                redirectAttributes.addFlashAttribute("errorMessage", "Error saving assessment: " + e.getMessage());
                return "redirect:/rubrics/manage";
            }
        }

        return "redirect:/rubrics/manage";
    }

    @PostMapping("/assessment/delete/{id}")
    public String deleteAssessment(@PathVariable("id") Long id, RedirectAttributes redirectAttributes) {
        try {
            Assessment assessment = rubricService.findAssessmentById(id);
            if (!ownsAssessment(assessment)) {
                redirectAttributes.addFlashAttribute("errorMessage",
                        "You are not authorized to delete this assessment.");
                return "redirect:/rubrics/manage";
            }
            rubricService.deleteAssessment(id);
            redirectAttributes.addFlashAttribute("successMessage", "Assessment deleted successfully.");
        } catch (Exception e) {
            redirectAttributes.addFlashAttribute("errorMessage", "Error deleting assessment.");
        }
        return "redirect:/rubrics/manage";
    }

    @GetMapping("/view/{assessmentId}")
    @Transactional
    public String viewAssessmentRubrics(@PathVariable("assessmentId") Long assessmentId, Model model,
            RedirectAttributes redirectAttributes) {
        Assessment assessment = rubricService.findAssessmentById(assessmentId);
        if (!ownsAssessment(assessment)) {
            redirectAttributes.addFlashAttribute("errorMessage", "You are not authorized to view this assessment.");
            return "redirect:/rubrics/manage";
        }

        rubricService.initializeRubricOrders(assessmentId);

        assessment = rubricService.findAssessmentById(assessmentId);

        Map<String, Map<String, List<Object>>> groupedRubrics = new LinkedHashMap<>();
        final String DUMMY_KEY = "ASSESSMENT_GROUPING";
        Map<String, List<Object>> innerGroup = new LinkedHashMap<>();

        if (assessment.getRubrics() != null) {
            Hibernate.initialize(assessment.getRubrics());
            for (Rubric rubric : assessment.getRubrics()) {
                if (rubric.getSubRubrics() != null) {
                    Hibernate.initialize(rubric.getSubRubrics());
                    rubric.getSubRubrics().forEach(subRubric -> {
                        if (subRubric.getRatings() != null) {
                            Hibernate.initialize(subRubric.getRatings());
                        }
                    });
                }
                if (rubric.getRatings() != null) {
                    Hibernate.initialize(rubric.getRatings());
                }

                String assessmentType = rubric.getAssessmentTypes();
                if (assessmentType == null || assessmentType.trim().isEmpty()) {
                    assessmentType = "Uncategorized";
                }
                innerGroup.computeIfAbsent(assessmentType, k -> new ArrayList<>()).add(rubric);
            }
        }

        for (List<Object> rubrics : innerGroup.values()) {
            rubrics.sort((o1, o2) -> {
                if (o1 instanceof Rubric && o2 instanceof Rubric) {
                    Integer order1 = ((Rubric) o1).getDisplayOrder();
                    Integer order2 = ((Rubric) o2).getDisplayOrder();
                    if (order1 == null)
                        order1 = 0;
                    if (order2 == null)
                        order2 = 0;
                    return Integer.compare(order1, order2);
                }
                return 0;
            });
        }

        groupedRubrics.put(DUMMY_KEY, innerGroup);

        boolean hasLecturerAssignments = !lecturerGroupAssignmentRepository.findByAssessment(assessment).isEmpty() ||
                                         !lecturerRubricAssignmentRepository.findByAssessment(assessment).isEmpty() ||
                                         !lecturerStudentAssignmentRepository.findByAssessment(assessment).isEmpty();
        boolean hasStudentAssignments = !studentAssessmentAssignmentRepository.findByAssessment(assessment).isEmpty();

        model.addAttribute("hasLecturerAssignments", hasLecturerAssignments);
        model.addAttribute("hasStudentAssignments", hasStudentAssignments);
        model.addAttribute("assessment", assessment);
        model.addAttribute("groupedRubrics", groupedRubrics);

        return "view-assessment-rubrics";
    }

    @PostMapping("/assessment/{assessmentId}/extra-notes")
    public String saveAssessmentExtraNotes(
            @PathVariable("assessmentId") Long assessmentId,
            @RequestParam("extraNotes") String extraNotes,
            RedirectAttributes redirectAttributes) {
        Assessment assessment = rubricService.findAssessmentById(assessmentId);
        if (!ownsAssessment(assessment)) {
            redirectAttributes.addFlashAttribute("errorMessage", "You are not authorized to update this assessment.");
            return "redirect:/rubrics/manage";
        }

        String sanitized = HtmlSanitizerUtil.sanitize(extraNotes == null ? "" : extraNotes.trim());
        assessment.setExtraNotes(sanitized.isBlank() ? null : sanitized);
        rubricService.saveAssessment(assessment);

        redirectAttributes.addFlashAttribute("successMessage", "Extra notes updated successfully.");
        return "redirect:/rubrics/view/" + assessmentId;
    }

    @PostMapping("/assessment/{assessmentId}/extra-notes-for-student")
    public String saveAssessmentExtraNotesForStudent(
            @PathVariable("assessmentId") Long assessmentId,
            @RequestParam("extraNotesForStudent") String extraNotesForStudent,
            RedirectAttributes redirectAttributes) {
        Assessment assessment = rubricService.findAssessmentById(assessmentId);
        if (!ownsAssessment(assessment)) {
            redirectAttributes.addFlashAttribute("errorMessage", "You are not authorized to update this assessment.");
            return "redirect:/rubrics/manage";
        }

        String sanitized = HtmlSanitizerUtil.sanitize(extraNotesForStudent == null ? "" : extraNotesForStudent.trim());
        assessment.setExtraNotesForStudent(sanitized.isBlank() ? null : sanitized);
        rubricService.saveAssessment(assessment);

        redirectAttributes.addFlashAttribute("successMessage", "Extra notes for students updated successfully.");
        return "redirect:/rubrics/view/" + assessmentId;
    }

    @GetMapping("/assessment/{id}/fill")
    public String showBulkFillForm(@PathVariable("id") Long id, Model model, RedirectAttributes redirectAttributes) {
        Assessment assessment = rubricService.findAssessmentById(id);
        if (!ownsAssessment(assessment)) {
            redirectAttributes.addFlashAttribute("errorMessage", "You are not authorized to edit this assessment.");
            return "redirect:/rubrics/manage";
        }
        model.addAttribute("assessment", assessment);
        return "bulk-rubric-edit";
    }

    @PostMapping("/assessment/{id}/fill/save")
    public String saveBulkFill(@PathVariable("id") Long id, @ModelAttribute Assessment assessment,
            RedirectAttributes redirectAttributes) {
        try {
            // Phase 4: Mutation Guarding - Ensure payload ID matches path ID
            if (assessment.getId() != null && !assessment.getId().equals(id)) {
                redirectAttributes.addFlashAttribute("errorMessage", "Security validation failed: Payload ID mismatch.");
                return "redirect:/rubrics/manage";
            }
            assessment.setId(id);

            Assessment existingAssessment = rubricService.findAssessmentById(id);
            if (!ownsAssessment(existingAssessment)) {
                redirectAttributes.addFlashAttribute("errorMessage",
                        "You are not authorized to update this assessment.");
                return "redirect:/rubrics/manage";
            }
            
            // Phase 4: Prevent course hijacking via modified payload
            assessment.setCourse(existingAssessment.getCourse());

            rubricService.saveBulkAssessment(assessment);
            redirectAttributes.addFlashAttribute("successMessage", "Assessment content saved successfully!");
            return "redirect:/rubrics/view/" + id;
        } catch (Exception e) {
            redirectAttributes.addFlashAttribute("errorMessage", "Error saving content: " + e.getMessage());
            return "redirect:/rubrics/assessment/" + id + "/fill";
        }
    }

    @GetMapping("/add/{assessmentId}")
    public String showAddRubricForm(@PathVariable("assessmentId") Long assessmentId, Model model,
            RedirectAttributes redirectAttributes) {
        Assessment assessment = rubricService.findAssessmentById(assessmentId);
        if (!ownsAssessment(assessment)) {
            redirectAttributes.addFlashAttribute("errorMessage",
                    "You are not authorized to add rubrics to this assessment.");
            return "redirect:/rubrics/manage";
        }
        Rubric rubric = new Rubric();
        rubric.setAssessment(assessment);

        rubric.setSubRubrics(new ArrayList<>());
        rubric.setRatings(new ArrayList<>());

        model.addAttribute("rubric", rubric);
        model.addAttribute("pageTitle", "Add New Rubric");
        model.addAttribute("formAction", "/rubrics/save");

        model.addAttribute("assessmentTypesOptions", new String[] { "Individual Assessment", "Group Assessment" });
        model.addAttribute("availableTemplates", rubricTemplateService.getTemplatesForActiveCourse());

        return "rubric-form";
    }

    @GetMapping("/edit/{rubricId}")
    @Transactional
    public String showEditRubricForm(@PathVariable("rubricId") Long rubricId, Model model,
            RedirectAttributes redirectAttributes) {
        Rubric existingRubric = rubricService.findRubricById(rubricId);
        if (!ownsRubric(existingRubric)) {
            redirectAttributes.addFlashAttribute("errorMessage", "You are not authorized to edit this rubric.");
            return "redirect:/rubrics/manage";
        }

        if (!model.containsAttribute("rubric")) {
            Rubric rubric = existingRubric;

            if (rubric.getSubRubrics() == null) {
                rubric.setSubRubrics(new ArrayList<>());
            } else {
                for (SubRubric subRubric : rubric.getSubRubrics()) {
                    if (subRubric.getRatings() == null) {
                        subRubric.setRatings(new ArrayList<>());
                    }
                }
            }

            if (rubric.getRatings() == null) {
                rubric.setRatings(new ArrayList<>());
            }

            model.addAttribute("rubric", rubric);
        }

        model.addAttribute("pageTitle", "Edit Rubric");
        model.addAttribute("formAction", "/rubrics/save");

        model.addAttribute("assessmentTypesOptions", new String[] { "Individual Assessment", "Group Assessment" });
        model.addAttribute("availableTemplates", rubricTemplateService.getTemplatesForActiveCourse());

        return "rubric-form";
    }

    @PostMapping("/save")
    @Transactional
    public String saveOrUpdateRubric(@ModelAttribute Rubric rubric,
            @RequestParam(value = "duplicateConfirmed", defaultValue = "false") boolean duplicateConfirmed,
            @RequestParam Map<String, String> allParams,
            RedirectAttributes redirectAttributes,
            Model model) {

        Long assessmentId = rubric.getAssessment().getId();
        Long rubricId = rubric.getId();
        
        if (rubric.getName() != null) {
            rubric.setName(HtmlSanitizerUtil.sanitize(rubric.getName()));
        }

        Assessment assessment = rubricService.findAssessmentById(assessmentId);
        if (!ownsAssessment(assessment)) {
            redirectAttributes.addFlashAttribute("errorMessage", "You are not authorized to modify this assessment.");
            return "redirect:/rubrics/manage";
        }

        if (rubricId != null) {
            Rubric existingRubric = rubricService.findRubricById(rubricId);
            if (!ownsRubric(existingRubric)) {
                redirectAttributes.addFlashAttribute("errorMessage", "You are not authorized to modify this rubric.");
                return "redirect:/rubrics/manage";
            }
        }

        List<String> commentLabels = rubric.getRubricCommentLabels();
        if (commentLabels != null && !commentLabels.isEmpty()) {
            List<Boolean> anonymousFlags = new ArrayList<>();

            for (int i = 0; i < commentLabels.size(); i++) {
                String paramKey = "rubricCommentAnonymousFlags[" + i + "]";
                boolean isChecked = allParams.containsKey(paramKey) && "true".equals(allParams.get(paramKey));
                anonymousFlags.add(isChecked);
            }

            rubric.setRubricCommentAnonymousFlags(anonymousFlags);
        }

        if (!duplicateConfirmed) {
            boolean isDuplicate = rubricService.isRubricNameDuplicate(
                    rubric.getName(),
                    assessmentId,
                    rubricId);

            if (isDuplicate) {
                redirectAttributes.addFlashAttribute("rubric", rubric);
                redirectAttributes.addFlashAttribute("duplicateFound", true);
                redirectAttributes.addFlashAttribute("errorMessage",
                        "A Rubric with the name '" + rubric.getName()
                                + "' already exists in this assessment. Click 'Save' again to confirm the duplicate entry.");

                String redirectPath = (rubricId == null) ? "/rubrics/add/" + assessmentId : "/rubrics/edit/" + rubricId;
                return "redirect:" + redirectPath;
            }
        }

        if (rubric.getMarks() != null) {
            rubric.setCloMarks(rubric.getMarks().doubleValue());
        } else {
            rubric.setCloMarks(null);
        }

        if (rubric.getSubRubrics() != null) {
            for (SubRubric subRubric : rubric.getSubRubrics()) {
                subRubric.setRubric(rubric);

                if (subRubric.getRatings() != null) {
                    for (Rating rating : subRubric.getRatings()) {
                        rating.setSubRubric(subRubric);
                        rating.setRubric(null);
                    }
                }
            }
        }

        if (rubric.getRatings() != null) {
            for (Rating rating : rubric.getRatings()) {
                rating.setRubric(rubric);
                rating.setSubRubric(null);
            }
        }

        Rubric savedRubric = rubricService.saveRubric(rubric);

        redirectAttributes.addFlashAttribute("successMessage", "Rubric saved successfully.");

        return "redirect:/rubrics/view/" + savedRubric.getAssessment().getId();
    }

    @PostMapping("/delete/{rubricId}")
    public String deleteRubric(@PathVariable("rubricId") Long rubricId, RedirectAttributes redirectAttributes) {
        Rubric rubric = rubricService.findRubricById(rubricId);
        if (!ownsRubric(rubric)) {
            redirectAttributes.addFlashAttribute("errorMessage", "You are not authorized to delete this rubric.");
            return "redirect:/rubrics/manage";
        }
        Long assessmentId = rubric.getAssessment().getId();
        try {
            rubricService.deleteRubric(rubricId);
            redirectAttributes.addFlashAttribute("successMessage", "Rubric deleted successfully.");
        } catch (Exception e) {
            redirectAttributes.addFlashAttribute("errorMessage", "Error deleting rubric.");
        }
        return "redirect:/rubrics/view/" + assessmentId;
    }

    @GetMapping("/assessment/edit/{id}")
    public String showEditAssessmentForm(@PathVariable("id") Long id, Model model,
            RedirectAttributes redirectAttributes) {
        Assessment assessment = rubricService.findAssessmentById(id);
        if (!ownsAssessment(assessment)) {
            redirectAttributes.addFlashAttribute("errorMessage", "You are not authorized to edit this assessment.");
            return "redirect:/rubrics/manage";
        }
        model.addAttribute("newAssessment", assessment);
        Long activeCourseId = courseScopeService.getActiveCourseIdForCurrentUser();
        model.addAttribute("assessments", activeCourseId == null
            ? List.of()
            : assessmentService.findAllAssessmentsWithRubricsByCourseId(activeCourseId));
        return "manage-assessments";
    }

    @PostMapping("/assessment/{assessmentId}/move-block")
    public String moveAssessmentBlock(@PathVariable("assessmentId") Long assessmentId,
            @RequestParam("blockType") String blockType,
            @RequestParam("direction") String direction,
            RedirectAttributes redirectAttributes) {
        Assessment assessment = rubricService.findAssessmentById(assessmentId);
        if (!ownsAssessment(assessment)) {
            redirectAttributes.addFlashAttribute("errorMessage", "You are not authorized to reorder this assessment.");
            return "redirect:/rubrics/manage";
        }
        try {
            rubricService.moveAssessmentBlock(assessmentId, blockType, direction);
        } catch (Exception e) {
        }

        String anchor = blockType.equalsIgnoreCase("Group") ? "group-block" : "individual-block";
        return "redirect:/rubrics/view/" + assessmentId + "#" + anchor;
    }

    @PostMapping("/rubric/{rubricId}/move")
    public String moveRubric(@PathVariable("rubricId") Long rubricId,
            @RequestParam("direction") String direction,
            RedirectAttributes redirectAttributes) {
        try {
            Rubric rubric = rubricService.findRubricById(rubricId);
            if (!ownsRubric(rubric)) {
                redirectAttributes.addFlashAttribute("errorMessage", "You are not authorized to reorder this rubric.");
                return "redirect:/rubrics/manage";
            }
            Long assessmentId = rubric.getAssessment().getId();

            rubricService.moveRubric(rubricId, direction);

            return "redirect:/rubrics/view/" + assessmentId + "#rubric-" + rubricId;

        } catch (Exception e) {
            return "redirect:/rubrics/manage";
        }
    }
}