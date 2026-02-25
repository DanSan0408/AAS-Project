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
import com.capstone.adproject.model.Admin;
import com.capstone.adproject.model.Assessment;
import com.capstone.adproject.model.Group;
import com.capstone.adproject.model.Rubric;
import com.capstone.adproject.model.Student;
import com.capstone.adproject.model.StudentResultOverride;
import com.capstone.adproject.repositories.GroupRepository;
import com.capstone.adproject.repositories.StudentResultOverrideRepository;
import com.capstone.adproject.service.AssessmentService;
import com.capstone.adproject.service.CalculateService;
import com.capstone.adproject.service.StudentService;

class OverrideForm {
    private List<StudentResultUpdate> updates = new ArrayList<>();
    public List<StudentResultUpdate> getUpdates() { return updates; }
    public void setUpdates(List<StudentResultUpdate> updates) { this.updates = updates; }
}

class StudentResultUpdate {
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

@Controller
@RequestMapping("/admin/data-views")
public class DataViewController {

    private final AssessmentService assessmentService;
    private final StudentService studentService;
    private final CalculateService calculateService;
    private final GroupRepository groupRepository;
    private final StudentResultOverrideRepository overrideRepository;

    public DataViewController(
            AssessmentService assessmentService,
            StudentService studentService,
            CalculateService calculateService,
            GroupRepository groupRepository,
            StudentResultOverrideRepository overrideRepository) {
        this.assessmentService = assessmentService;
        this.studentService = studentService;
        this.calculateService = calculateService;
        this.groupRepository = groupRepository;
        this.overrideRepository = overrideRepository;
    }

    private String getLoggedInUsername() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication != null && authentication.getPrincipal() instanceof Admin) {
            return ((Admin) authentication.getPrincipal()).getEmail();
        }
        return "Admin";
    }

    @GetMapping
    public String showDataViewsHome(Model model) {
        model.addAttribute("assessments", assessmentService.findAllAssessmentsWithRubrics());
        model.addAttribute("adminUsername", getLoggedInUsername());
        return "data_views_home";
    }

    @GetMapping("/overall")
    public String showOverallData(Model model) {
        List<Assessment> assessments = assessmentService.findAllAssessmentsWithRubrics();
        List<Student> students = studentService.getAllStudents();
        
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
        
        List<Group> allGroups = groupRepository.findAll();
        model.addAttribute("allGroups", allGroups);
        model.addAttribute("assessment", assessment);
        
        Group selectedGroup = null;
        if (groupId != null) {
            selectedGroup = groupRepository.findById(groupId).orElse(null);
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
            List<Student> students = new ArrayList<>(selectedGroup.getStudents());
            
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
        
        List<Group> groups = groupRepository.findAll();
        model.addAttribute("groups", groups);
        model.addAttribute("selectedGroupId", groupId);
        model.addAttribute("adminUsername", getLoggedInUsername());
        
        OverrideForm form = new OverrideForm();
        List<Student> students = new ArrayList<>();
        
        if (groupId != null) {
            Group group = groupRepository.findById(groupId).orElse(null);
            if (group != null) {
                students = new ArrayList<>(group.getStudents());
                
                students.sort((s1, s2) -> {
                    String email1 = s1.getEmail() != null ? s1.getEmail() : "";
                    String email2 = s2.getEmail() != null ? s2.getEmail() : "";
                    return email1.compareToIgnoreCase(email2);
                });
                
                List<Assessment> assessments = assessmentService.findAllAssessmentsWithRubrics();
                
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
                if (student != null) {
                    StudentResultOverride override = overrideRepository.findByStudent(student)
                        .orElse(new StudentResultOverride());
                    
                    override.setStudent(student);
                    override.setOverriddenFactor(update.getFactor());
                    override.setOverriddenGrandTotal(update.getGrandTotal());
                    
                    overrideRepository.save(override);
                }
            }
        }
        return "redirect:/admin/data-views/edit-overrides?groupId=" + (groupId != null ? groupId : "");
    }
}