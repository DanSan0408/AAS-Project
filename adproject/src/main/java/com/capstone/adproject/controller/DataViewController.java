package com.capstone.adproject.controller;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;

import com.capstone.adproject.dto.AssessmentDataDto;
import com.capstone.adproject.dto.StudentAssessmentDataDto;
import com.capstone.adproject.model.Admin;
import com.capstone.adproject.model.Assessment;
import com.capstone.adproject.model.Group;
import com.capstone.adproject.model.Student;
import com.capstone.adproject.repositories.GroupRepository;
import com.capstone.adproject.service.AssessmentService;
import com.capstone.adproject.service.CalculationService;
import com.capstone.adproject.service.StudentService;

@Controller
@RequestMapping("/admin/data-views")
public class DataViewController {

    private final AssessmentService assessmentService;
    private final StudentService studentService;
    private final CalculationService calculationService;
    private final GroupRepository groupRepository;

    public DataViewController(
            AssessmentService assessmentService,
            StudentService studentService,
            CalculationService calculationService,
            GroupRepository groupRepository) {
        this.assessmentService = assessmentService;
        this.studentService = studentService;
        this.calculationService = calculationService;
        this.groupRepository = groupRepository;
    }

    private String getLoggedInUsername() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication != null && authentication.getPrincipal() instanceof Admin) {
            return ((Admin) authentication.getPrincipal()).getUsername();
        }
        return "Admin";
    }

    /**
     * Show assessment selection page
     */
    @GetMapping
    public String showDataViewsHome(Model model) {
        List<Assessment> assessments = assessmentService.findAllAssessmentsWithRubrics();
        
        model.addAttribute("assessments", assessments);
        model.addAttribute("adminUsername", getLoggedInUsername());
        
        return "data_views_home";
    }

    /**
     * Show data for a specific assessment with group filtering
     */
    @GetMapping("/assessment/{assessmentId}")
    public String showAssessmentData(
            @PathVariable Long assessmentId,
            @RequestParam(required = false) Long groupId,
            Model model) {
        
        Assessment assessment = assessmentService.getAssessmentById(assessmentId)
            .orElseThrow(() -> new RuntimeException("Assessment not found"));
        
        // Get all groups for the dropdown
        List<Group> allGroups = groupRepository.findAllWithStudents();
        allGroups.sort((g1, g2) -> g1.getGroupName().compareToIgnoreCase(g2.getGroupName()));
        
        // Determine which students to show
        List<Student> studentsToShow = new ArrayList<>();
        Group selectedGroup = null;
        
        if (groupId != null) {
            selectedGroup = groupRepository.findById(groupId)
                .orElseThrow(() -> new RuntimeException("Group not found"));
            studentsToShow = new ArrayList<>(selectedGroup.getStudents());
        } else if (!allGroups.isEmpty()) {
            selectedGroup = allGroups.get(0);
            studentsToShow = new ArrayList<>(selectedGroup.getStudents());
        }
        
        studentsToShow.sort((s1, s2) -> s1.getUsername().compareToIgnoreCase(s2.getUsername()));
        
        // ✅ THIS IS THE ONLY CALCULATION CALL YOU NEED!
        // It calculates EVERYTHING including weighted marks
        AssessmentDataDto assessmentData = calculationService.calculateAssessmentData(assessment, studentsToShow);
        
        // Calculate detailed sub-rubric data for display
        Map<Long, Map<Long, Map<String, Object>>> studentRubricDetails = new HashMap<>();
        
        for (StudentAssessmentDataDto studentData : assessmentData.getStudentDataList()) {
            Map<Long, Map<String, Object>> rubricDetails = new HashMap<>();
            
            for (com.capstone.adproject.model.Rubric rubric : assessment.getRubrics()) {
                Map<String, Object> details = new HashMap<>();
                
                if (rubric.hasSubRubrics()) {
                    details.put("subRubricRatingsByEvaluator", 
                        calculationService.getSubRubricRatingsByEvaluator(studentData.getStudent(), assessment, rubric));
                    details.put("muSrmlnByEvaluator", 
                        calculationService.calculateMuSrmln(studentData.getStudent(), assessment, rubric));
                    details.put("muLn", 
                        calculationService.calculateMuLn(studentData.getStudent(), assessment, rubric));
                }
                
                details.put("evaluatorComments", 
                    calculationService.getEvaluatorCommentsForRubric(studentData.getStudent(), assessment, rubric));
                
                rubricDetails.put(rubric.getId(), details);
            }
            
            studentRubricDetails.put(studentData.getStudent().getId(), rubricDetails);
        }
        
        // Collect all unique evaluators
        Map<String, String> uniqueEvaluators = new java.util.LinkedHashMap<>();
        for (StudentAssessmentDataDto studentData : assessmentData.getStudentDataList()) {
            for (com.capstone.adproject.dto.RubricCalculationDto rubricCalc : studentData.getRubricCalculations().values()) {
                for (Map<String, Object> detail : rubricCalc.getEvaluatorDetails()) {
                    String evaluatorName = (String) detail.get("evaluatorName");
                    String evaluatorEmail = (String) detail.get("evaluatorEmail");
                    if (evaluatorName != null && evaluatorEmail != null) {
                        uniqueEvaluators.put(evaluatorEmail, evaluatorName);
                    }
                }
            }
        }
        
        // Separate rubrics by assessment type
        Map<String, List<com.capstone.adproject.model.Rubric>> rubricsByType = assessment.getRubrics().stream()
            .collect(Collectors.groupingBy(
                r -> r.getAssessmentTypes() != null ? r.getAssessmentTypes() : "Uncategorized"
            ));
        
        model.addAttribute("assessment", assessment);
        model.addAttribute("assessmentData", assessmentData);
        model.addAttribute("rubricsByType", rubricsByType);
        model.addAttribute("uniqueEvaluators", uniqueEvaluators);
        model.addAttribute("adminUsername", getLoggedInUsername());
        model.addAttribute("allGroups", allGroups);
        model.addAttribute("selectedGroup", selectedGroup);
        model.addAttribute("studentRubricDetails", studentRubricDetails);
        
        return "assessment_data_view";
    }

    /**
     * Show overall summary data across all assessments
     */
    @GetMapping("/overall")
    public String showOverallData(Model model) {
        List<Assessment> assessments = assessmentService.findAllAssessmentsWithRubrics();
        
        // Get all students sorted alphabetically
        List<Student> students = studentService.getAllStudents();
        students.sort((s1, s2) -> s1.getUsername().compareToIgnoreCase(s2.getUsername()));
        
        // Calculate data for each student across all assessments
        Map<Long, Map<String, Object>> studentOverallData = new HashMap<>();
        
        for (Student student : students) {
            Map<String, Object> studentData = new HashMap<>();
            
            // Basic info
            studentData.put("student", student);
            studentData.put("teamName", student.getGroup() != null ? student.getGroup().getGroupName() : "No Team");
            
            // Calculate factor (using first peer/self assessment found)
            Double factor = 1.0;
            for (Assessment assessment : assessments) {
                if (assessment.getTitle().toLowerCase().contains("peer") || 
                    assessment.getTitle().toLowerCase().contains("self")) {
                    factor = calculationService.calculateStudentAssessmentData(student, assessment).getFactor();
                    break;
                }
            }
            studentData.put("factor", factor);
            
            // Calculate data for each assessment
            List<StudentAssessmentDataDto> assessmentDataList = new ArrayList<>();
            for (Assessment assessment : assessments) {
                StudentAssessmentDataDto assessmentData = 
                    calculationService.calculateStudentAssessmentData(student, assessment);
                assessmentDataList.add(assessmentData);
            }
            studentData.put("assessmentDataList", assessmentDataList);
            
            // Calculate grand total
            Double grandTotal = calculationService.calculateGrandTotal(student, assessments);
            studentData.put("grandTotal", grandTotal);
            
            studentOverallData.put(student.getId(), studentData);
        }
        
        model.addAttribute("students", students);
        model.addAttribute("assessments", assessments);
        model.addAttribute("studentOverallData", studentOverallData);
        model.addAttribute("adminUsername", getLoggedInUsername());
        
        return "overall_data_view";
    }
}