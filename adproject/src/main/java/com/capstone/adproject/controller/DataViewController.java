package com.capstone.adproject.controller;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;

import com.capstone.adproject.dto.AssessmentDataDto;
import com.capstone.adproject.dto.RubricCalculationDto;
import com.capstone.adproject.dto.StudentAssessmentDataDto;
import com.capstone.adproject.model.Assessment;
import com.capstone.adproject.model.Course;
import com.capstone.adproject.model.FactorOverrideHistory;
import com.capstone.adproject.model.FactorWeightage;
import com.capstone.adproject.model.Group;
import com.capstone.adproject.model.Rubric;
import com.capstone.adproject.model.Student;
import com.capstone.adproject.model.StudentResultOverride;
import com.capstone.adproject.repositories.GroupRepository;
import com.capstone.adproject.repositories.StudentResultOverrideRepository;
import com.capstone.adproject.repositories.FactorWeightageRepository;
import com.capstone.adproject.repositories.MarkRepository;
import com.capstone.adproject.service.AssessmentService;
import com.capstone.adproject.service.CalculateService;
import com.capstone.adproject.service.CourseScopeService;
import com.capstone.adproject.service.StudentService;
import com.capstone.adproject.service.AdminService;
import org.springframework.beans.factory.annotation.Autowired;

@Controller
@RequestMapping("/admin/data-views")
public class DataViewController {

    public static class OverrideForm {
        private List<StudentResultUpdate> updates = new ArrayList<>();
        public List<StudentResultUpdate> getUpdates() { return updates; }
        public void setUpdates(List<StudentResultUpdate> updates) { this.updates = updates; }
    }

    public static class StudentResultUpdate {
        private Long studentId;
        private Double factor;
        private Double grandTotal;

        public StudentResultUpdate() {}

        public StudentResultUpdate(Long studentId, Double factor, Double grandTotal) {
            this.studentId = studentId;
            this.factor = factor;
            this.grandTotal = grandTotal;
        }

        public Long getStudentId() { return studentId; }
        public void setStudentId(Long studentId) { this.studentId = studentId; }
        public Double getFactor() { return factor; }
        public void setFactor(Double factor) { this.factor = factor; }
        public Double getGrandTotal() { return grandTotal; }
        public void setGrandTotal(Double grandTotal) { this.grandTotal = grandTotal; }
    }

    private final AssessmentService assessmentService;
    private final StudentService studentService;
    private final CalculateService calculateService;
    private final MarkRepository markRepository;
    private final GroupRepository groupRepository;
    private final StudentResultOverrideRepository overrideRepository;
    private final CourseScopeService courseScopeService;
    private final AdminService adminService;
    private final FactorWeightageRepository factorWeightageRepository;
    private final com.capstone.adproject.repositories.FactorOverrideHistoryRepository factorOverrideHistoryRepository;

    @Autowired
    public DataViewController(
            AssessmentService assessmentService,
            MarkRepository markRepository,
            GroupRepository groupRepository,
            StudentService studentService,
            StudentResultOverrideRepository overrideRepository,
            CalculateService calculateService,
            CourseScopeService courseScopeService,
            AdminService adminService,
            FactorWeightageRepository factorWeightageRepository,
            com.capstone.adproject.repositories.FactorOverrideHistoryRepository factorOverrideHistoryRepository) {
        this.assessmentService = assessmentService;
        this.markRepository = markRepository;
        this.groupRepository = groupRepository;
        this.studentService = studentService;
        this.overrideRepository = overrideRepository;
        this.calculateService = calculateService;
        this.courseScopeService = courseScopeService;
        this.adminService = adminService;
        this.factorWeightageRepository = factorWeightageRepository;
        this.factorOverrideHistoryRepository = factorOverrideHistoryRepository;
    }

    private String getLoggedInUsername() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication != null && authentication.getName() != null) {
            return authentication.getName();
        }
        return "";
    }

    private Set<Long> getManagedCourseIdsForCurrentUser() {
        return courseScopeService.getActiveCourseIdsForCurrentUser();
    }
    
    @GetMapping("/factors")
    public String showFactorDisplayPage(@RequestParam(required = false) Long groupId, Model model) {
        Long activeCourseId = courseScopeService.getActiveCourseIdForCurrentUser();
        List<Group> groups = activeCourseId == null
            ? List.of()
            : groupRepository.findByCourseId(activeCourseId);
        
        model.addAttribute("groups", groups);
        model.addAttribute("selectedGroupId", groupId);
        model.addAttribute("adminUsername", getLoggedInUsername());
        
        List<com.capstone.adproject.dto.FactorDetailsDto> factorDetailsList = new ArrayList<>();
        List<FactorOverrideHistory> allHistory = new ArrayList<>();
        
        if (groupId != null) {
            Group group = groupRepository.findById(groupId).orElse(null);
            if (group != null && ownsGroup(group)) {
                List<Student> students = new ArrayList<>(group.getStudents());
                students.sort((s1, s2) -> {
                    String n1 = s1.getUsername() != null ? s1.getUsername() : "";
                    String n2 = s2.getUsername() != null ? s2.getUsername() : "";
                    return n1.compareToIgnoreCase(n2);
                });
                
                for (Student student : students) {
                    factorDetailsList.add(calculateService.getFactorDetailsForStudent(student));
                }
                
                allHistory = factorOverrideHistoryRepository.findByStudentInOrderByChangedAtDesc(students);
            }
        }
        
        model.addAttribute("factorDetailsList", factorDetailsList);
        model.addAttribute("allHistory", allHistory);
        
        return "factor_display";
    }

    private boolean ownsAssessment(Assessment assessment) {
        return assessment != null
            && assessment.getCourse() != null
            && assessment.getCourse().getId() != null
            && getManagedCourseIdsForCurrentUser().contains(assessment.getCourse().getId());
    }

    private boolean ownsGroup(Group group) {
        return group != null
            && group.getCourse() != null
            && group.getCourse().getId() != null
            && getManagedCourseIdsForCurrentUser().contains(group.getCourse().getId());
    }

    private boolean ownsStudent(Student student) {
        return student != null
            && student.getCourse() != null
            && student.getCourse().getId() != null
            && getManagedCourseIdsForCurrentUser().contains(student.getCourse().getId());
    }

    @GetMapping
    public String showDataViewsHome(Model model) {
        Long activeCourseId = courseScopeService.getActiveCourseIdForCurrentUser();
        model.addAttribute("assessments", activeCourseId == null
            ? List.of()
            : assessmentService.findAllAssessmentsWithRubricsByCourseId(activeCourseId));
        model.addAttribute("adminUsername", getLoggedInUsername());
        return "data_views_home";
    }

    @GetMapping("/overall")
    public String showOverallData(Model model) {
        Long activeCourseId = courseScopeService.getActiveCourseIdForCurrentUser();
        List<Assessment> assessments = activeCourseId == null
            ? List.of()
            : assessmentService.findAllAssessmentsWithRubricsByCourseId(activeCourseId);
        List<Student> students = activeCourseId == null
            ? new ArrayList<>()
            : new ArrayList<>(adminService.getAllStudents());
        
        students.sort((s1, s2) -> {
            String email1 = s1.getEmail() != null ? s1.getEmail() : "";
            String email2 = s2.getEmail() != null ? s2.getEmail() : "";
            return email1.compareToIgnoreCase(email2);
        });
        
        Map<Long, Map<String, Object>> studentOverallData = new HashMap<>();
        
        Map<Long, Set<Integer>> assessmentCLOsWithGroupRubrics = new HashMap<>();
        for (Assessment assessment : assessments) {
            assessmentCLOsWithGroupRubrics.put(assessment.getId(), calculateService.getCLOsWithGroupRubrics(assessment));
        }
        
        for (Student student : students) {
            Map<String, Object> data = new HashMap<>();
            data.put("teamName", student.getGroup() != null ? student.getGroup().getGroupName() : "No Team");
            
            Double grandTotal = calculateService.calculateGrandTotal(student, assessments);
            Double factor = 0.0;
            
            if (!assessments.isEmpty()) {
                factor = calculateService.calculateStudentAssessmentData(student, assessments.get(0)).getFactor();
            }
            
            Optional<StudentResultOverride> override = overrideRepository.findByStudent(student);
            boolean isOverridden = override.isPresent() && override.get().getOverriddenGrandTotal() != null;
            
            data.put("factor", factor);
            data.put("grandTotal", grandTotal);
            data.put("isOverridden", isOverridden);
            data.put("grade", calculateService.calculateGrade(grandTotal));
            
            List<StudentAssessmentDataDto> details = new ArrayList<>();
            for(Assessment a : assessments) {
                details.add(calculateService.calculateStudentAssessmentData(student, a));
            }
            data.put("assessmentDataList", details);
            
            studentOverallData.put(student.getId(), data);
        }
        
        model.addAttribute("students", students);
        model.addAttribute("assessments", assessments);
        model.addAttribute("studentOverallData", studentOverallData);
        model.addAttribute("assessmentCLOsWithGroupRubrics", assessmentCLOsWithGroupRubrics);
        model.addAttribute("adminUsername", getLoggedInUsername());
        
        return "overall_data_view";
    }

    @GetMapping("/assessment/{assessmentId}")
    public String showAssessmentData(
            @PathVariable Long assessmentId,
            @RequestParam(required = false) Long groupId,
            Model model) {
        
        Assessment assessment = assessmentService.getAssessmentById(assessmentId)
            .orElseThrow(() -> new RuntimeException("Assessment not found"));
        if (!ownsAssessment(assessment)) {
            throw new RuntimeException("Unauthorized assessment access");
        }
        
        Long activeCourseId = courseScopeService.getActiveCourseIdForCurrentUser();
        List<Group> allGroups = activeCourseId == null
            ? List.of()
            : groupRepository.findByCourseId(activeCourseId);
        model.addAttribute("allGroups", allGroups);
        model.addAttribute("assessment", assessment);
        
        Group selectedGroup = null;
        if (groupId != null) {
            selectedGroup = groupRepository.findById(groupId).orElse(null);
            if (selectedGroup != null && !ownsGroup(selectedGroup)) {
                selectedGroup = null;
            }
        } else if (!allGroups.isEmpty()) {
            selectedGroup = allGroups.get(0);
        }
        
        model.addAttribute("selectedGroup", selectedGroup);
        
        AssessmentDataDto assessmentData = new AssessmentDataDto();
        assessmentData.setAssessment(assessment);
        
        Map<String, List<Rubric>> rubricsByType = new HashMap<>();
        Map<Long, Map<Long, Map<String, Object>>> studentRubricDetails = new HashMap<>();
        Map<String, String> uniqueEvaluators = new java.util.LinkedHashMap<>();

        Map<Long, Map<String, List<String>>> studentGroupComments = new HashMap<>();
        Map<Long, Map<String, List<String>>> studentIndividualComments = new HashMap<>();
        
        if (selectedGroup != null && selectedGroup.getStudents() != null && !selectedGroup.getStudents().isEmpty()) {
            List<Student> students = selectedGroup.getStudents();
            
            students.sort((s1, s2) -> {
                String email1 = s1.getEmail() != null ? s1.getEmail() : "";
                String email2 = s2.getEmail() != null ? s2.getEmail() : "";
                return email1.compareToIgnoreCase(email2);
            });
            
            assessmentData = calculateService.calculateAssessmentData(assessment, students);
            
            if (assessment.getRubrics() != null) {
                for (Rubric rubric : assessment.getRubrics()) {
                    String type = rubric.getAssessmentTypes() != null ? rubric.getAssessmentTypes() : "Other";
                    rubricsByType.computeIfAbsent(type, k -> new ArrayList<>()).add(rubric);
                }
            }
            
            for (Student student : students) {
                Map<Long, Map<String, Object>> rubricDetails = new HashMap<>();
                for (Rubric rubric : assessment.getRubrics()) {
                    Map<String, Object> details = new HashMap<>();
                    details.put("subRubricRatingsByEvaluator", calculateService.getSubRubricRatingsByEvaluator(student, assessment, rubric));
                    details.put("muSrmlnByEvaluator", calculateService.calculateMuSrmln(student, assessment, rubric));
                    details.put("muLn", calculateService.calculateMuLn(student, assessment, rubric));
                    details.put("evaluatorComments", calculateService.getEvaluatorCommentsForRubric(student, assessment, rubric));
                    details.put("directRatingsByEvaluator", calculateService.getDirectRatingsByEvaluator(student, assessment, rubric));
                    details.put("rfByEvaluator", calculateService.calculateRfByEvaluator(student, assessment, rubric));
                    details.put("muRf", calculateService.calculateMuRf(student, assessment, rubric));
                    details.put("drmax", calculateService.getDrmax(rubric));
                    rubricDetails.put(rubric.getId(), details);
                }
                studentRubricDetails.put(student.getId(), rubricDetails);

                studentGroupComments.put(student.getId(), calculateService.getGroupAssessmentComments(student, assessment));
                studentIndividualComments.put(student.getId(), calculateService.getIndividualAssessmentComments(student, assessment));
            }
            
            for (Student student : students) {
                for (Rubric rubric : assessment.getRubrics()) {
                    StudentAssessmentDataDto studentData = calculateService.calculateStudentAssessmentData(student, assessment);
                    RubricCalculationDto rubricCalc = studentData.getRubricCalculations().get(rubric.getId());
                    if (rubricCalc != null && rubricCalc.getEvaluatorDetails() != null) {
                        for (Map<String, Object> evalDetail : rubricCalc.getEvaluatorDetails()) {
                            String evalType = (String) evalDetail.get("evaluatorType");
                            String evalName = (String) evalDetail.get("evaluatorName");
                            if (evalName != null) {
                                uniqueEvaluators.put(evalName, evalType);
                            }
                        }
                    }
                }
            }
        }
        
        model.addAttribute("assessmentData", assessmentData);
        model.addAttribute("rubricsByType", rubricsByType);
        model.addAttribute("studentRubricDetails", studentRubricDetails);
        model.addAttribute("uniqueEvaluators", uniqueEvaluators);
        model.addAttribute("studentGroupComments", studentGroupComments);
        model.addAttribute("studentIndividualComments", studentIndividualComments);
        model.addAttribute("adminUsername", getLoggedInUsername());
        
        return "assessment_data_view";
    }

    @GetMapping("/edit-overrides")
    public String showEditOverridesPage(
            @RequestParam(required = false) Long groupId, 
            Model model) {
        
        Long activeCourseId = courseScopeService.getActiveCourseIdForCurrentUser();
        List<Group> groups = activeCourseId == null
            ? List.of()
            : groupRepository.findByCourseId(activeCourseId);
        model.addAttribute("groups", groups);
        model.addAttribute("selectedGroupId", groupId);
        model.addAttribute("adminUsername", getLoggedInUsername());
        
        OverrideForm form = new OverrideForm();
        List<Student> students = new ArrayList<>();
        
        if (groupId != null) {
            Group group = groupRepository.findById(groupId).orElse(null);
            if (group != null && ownsGroup(group)) {
                students = group.getStudents();
                
                students.sort((s1, s2) -> {
                    String email1 = s1.getEmail() != null ? s1.getEmail() : "";
                    String email2 = s2.getEmail() != null ? s2.getEmail() : "";
                    return email1.compareToIgnoreCase(email2);
                });
                
                List<Assessment> assessments = activeCourseId == null
                    ? List.of()
                    : assessmentService.findAllAssessmentsWithRubricsByCourseId(activeCourseId);
                
                for (Student student : students) {
                    Double currentFactor = 0.0;
                    if(!assessments.isEmpty()) {
                         currentFactor = calculateService.calculateStudentAssessmentData(student, assessments.get(0)).getFactor();
                    }
                    Double currentTotal = calculateService.calculateGrandTotal(student, assessments);
                    
                    form.getUpdates().add(new StudentResultUpdate(student.getId(), currentFactor, currentTotal));
                }
            }
        }
        
        model.addAttribute("students", students);
        model.addAttribute("form", form);
        
        return "edit_overrides";
    }

    @PostMapping("/edit-overrides/save")
    public String saveOverrides(@ModelAttribute OverrideForm form, @RequestParam(required = false) Long groupId) {
        for (StudentResultUpdate update : form.getUpdates()) {
            if (update.getStudentId() != null) {
                Student student = studentService.findStudentById(update.getStudentId()).orElse(null);
                if (student != null && ownsStudent(student)) {
                    StudentResultOverride override = overrideRepository.findByStudent(student)
                        .orElse(new StudentResultOverride());
                    
                    Double oldFactor = override.getOverriddenFactor();
                    Double oldTotal = override.getOverriddenGrandTotal();
                    
                    // Only record history if something actually changed (or if it's new)
                    boolean isNew = (override.getId() == null);
                    boolean isChanged = isNew || 
                                        !java.util.Objects.equals(oldFactor, update.getFactor()) || 
                                        !java.util.Objects.equals(oldTotal, update.getGrandTotal());

                    override.setStudent(student);
                    override.setOverriddenFactor(update.getFactor());
                    override.setOverriddenGrandTotal(update.getGrandTotal());
                    
                    overrideRepository.save(override);
                    
                    if (isChanged) {
                        FactorOverrideHistory history = new FactorOverrideHistory(
                                student,
                                oldFactor,
                                update.getFactor(),
                                oldTotal,
                                update.getGrandTotal(),
                                getLoggedInUsername(),
                                java.time.LocalDateTime.now()
                        );
                        factorOverrideHistoryRepository.save(history);
                    }
                }
            }
        }
        return "redirect:/admin/data-views/edit-overrides?groupId=" + (groupId != null ? groupId : "");
    }

    @GetMapping("/calculate-factor")
    public String showCalculateFactorPage(Model model) {
        Long activeCourseId = courseScopeService.getActiveCourseIdForCurrentUser();
        List<Assessment> assessments = activeCourseId == null
            ? List.of()
            : assessmentService.findAllAssessmentsWithRubricsByCourseId(activeCourseId);
        
        Map<Long, Double> currentWeightages = new HashMap<>();
        Map<Long, String> currentTypes = new HashMap<>();
        if (activeCourseId != null) {
            Course course = new Course();
            course.setId(activeCourseId);
            List<FactorWeightage> weightages = factorWeightageRepository.findByCourse(course);
            for (FactorWeightage fw : weightages) {
                currentWeightages.put(fw.getAssessment().getId(), fw.getWeightage());
                currentTypes.put(fw.getAssessment().getId(), fw.getFactorContributionType() != null ? fw.getFactorContributionType() : "BOTH");
            }
        }
        
        model.addAttribute("assessments", assessments);
        model.addAttribute("currentWeightages", currentWeightages);
        model.addAttribute("currentTypes", currentTypes);
        model.addAttribute("adminUsername", getLoggedInUsername());
        return "calculate_factor";
    }

    @PostMapping("/calculate-factor")
    public String calculateFactor(@RequestParam Map<String, String> params, org.springframework.web.servlet.mvc.support.RedirectAttributes redirectAttributes) {
        Long activeCourseId = courseScopeService.getActiveCourseIdForCurrentUser();
        if (activeCourseId == null) return "redirect:/admin/data-views";
        
        Course dummyCourse = new Course();
        dummyCourse.setId(activeCourseId);
        
        double totalWeight = 0.0;
        for (String key : params.keySet()) {
            if (key.startsWith("weight_")) {
                String val = params.get(key);
                if (val != null && !val.trim().isEmpty()) {
                    try {
                        double weight = Double.parseDouble(val);
                        if (weight > 0) totalWeight += weight;
                    } catch (NumberFormatException e) {
                        // ignore
                    }
                }
            }
        }
        
        if (totalWeight > 0 && Math.abs(totalWeight - 100.0) >= 0.01) {
            redirectAttributes.addFlashAttribute("errorMessage", "Total weightage must be exactly 100%.");
            return "redirect:/admin/data-views/calculate-factor";
        }
        
        List<FactorWeightage> existing = factorWeightageRepository.findByCourse(dummyCourse);
        factorWeightageRepository.deleteAll(existing);
        
        for (String key : params.keySet()) {
            if (key.startsWith("weight_")) {
                Long assessmentId = Long.parseLong(key.substring(7));
                String val = params.get(key);
                String typeVal = params.get("type_" + assessmentId);
                if (typeVal == null || typeVal.trim().isEmpty()) typeVal = "BOTH";
                if (val != null && !val.trim().isEmpty()) {
                    try {
                        Double weight = Double.parseDouble(val);
                        if (weight > 0) {
                            Assessment assessment = assessmentService.getAssessmentById(assessmentId).orElse(null);
                            if (assessment != null && ownsAssessment(assessment)) {
                                FactorWeightage fw = new FactorWeightage(assessment.getCourse(), assessment, weight);
                                fw.setFactorContributionType(typeVal);
                                factorWeightageRepository.save(fw);
                            }
                        }
                    } catch (NumberFormatException e) {
                        // ignore invalid input
                    }
                }
            }
        }
        
        return "redirect:/admin/data-views/overall";
    }
}