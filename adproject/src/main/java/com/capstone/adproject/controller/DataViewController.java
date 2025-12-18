package com.capstone.adproject.controller;

import java.util.List;

import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Controller;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;

import com.capstone.adproject.dto.AssessmentDataViewDto;
import com.capstone.adproject.dto.GroupFactorDto;
import com.capstone.adproject.dto.OverallDataViewDto;
import com.capstone.adproject.dto.StudentAssessmentResultDto;
import com.capstone.adproject.model.Admin;
import com.capstone.adproject.model.Assessment;
import com.capstone.adproject.service.AssessmentService;
import com.capstone.adproject.service.CalculatedResultPersistenceService;
import com.capstone.adproject.service.DataViewService;
import com.capstone.adproject.service.FactorCalculationService;
import com.capstone.adproject.service.RubricService;

@Controller
@RequestMapping("/admin/data-views")
public class DataViewController {

    private final FactorCalculationService factorCalculationService;
    private final CalculatedResultPersistenceService calculatedResultPersistenceService;
    private final AssessmentService assessmentService;
    private final DataViewService dataViewService;
    private final RubricService rubricService;

    public DataViewController(
            FactorCalculationService factorCalculationService,
            CalculatedResultPersistenceService calculatedResultPersistenceService,
            AssessmentService assessmentService,
            DataViewService dataViewService,
            RubricService rubricService) {
        this.factorCalculationService = factorCalculationService;
        this.calculatedResultPersistenceService = calculatedResultPersistenceService;
        this.assessmentService = assessmentService;
        this.dataViewService = dataViewService;
        this.rubricService = rubricService;
    }

    private String getLoggedInUsername() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication != null && authentication.getPrincipal() instanceof Admin) {
            return ((Admin) authentication.getPrincipal()).getUsername();
        }
        return "Admin";
    }

    /**
     * Main data views index - shows list of assessments and overall option
     */
    @GetMapping("")
    public String showDataViewsIndex(Model model) {
        List<Assessment> assessments = rubricService.findAllAssessments();
        
        // Calculate factors
        List<GroupFactorDto> groupFactors = factorCalculationService.calculateFactorsForAllGroups();
        
        double averageFactor = 0.0;
        int totalStudents = 0;
        
        if (!groupFactors.isEmpty()) {
            double sumFactors = 0.0;
            for (GroupFactorDto group : groupFactors) {
                for (var student : group.getStudentFactors()) {
                    sumFactors += student.getCappedFactor();
                    totalStudents++;
                }
            }
            if (totalStudents > 0) {
                averageFactor = sumFactors / totalStudents;
            }
        }
        
        model.addAttribute("assessments", assessments);
        model.addAttribute("groupFactors", groupFactors);
        model.addAttribute("averageFactor", averageFactor);
        model.addAttribute("totalStudents", totalStudents);
        model.addAttribute("adminUsername", getLoggedInUsername());
        
        return "admin_data_views_index";
    }
    
    /**
     * ✅ FIXED: View assessment-specific data table with auto-calculation
     */
    @GetMapping("/assessment/{assessmentId}")
    @Transactional
    public String viewAssessmentData(
            @PathVariable Long assessmentId,
            Model model) {
        
        try {
            // ✅ Check if results exist, if not, calculate automatically
            Assessment assessment = assessmentService.getAssessmentById(assessmentId)
                .orElseThrow(() -> new IllegalArgumentException("Assessment not found"));
            
            // Check if we have any calculated results
            List<StudentAssessmentResultDto> existingResults = 
                calculatedResultPersistenceService.getAllResultsForAssessment(assessmentId);
            
            if (existingResults.isEmpty()) {
                // No results exist, calculate them
                try {
                    calculatedResultPersistenceService.calculateAndPersistAllStudents(assessmentId);
                    model.addAttribute("infoMessage", "Results calculated automatically.");
                } catch (Exception e) {
                    model.addAttribute("warningMessage", 
                        "Could not auto-calculate results: " + e.getMessage());
                }
            }
            
            // Build and display the data view
            AssessmentDataViewDto dataView = dataViewService.buildAssessmentDataView(assessmentId);
            
            model.addAttribute("dataView", dataView);
            model.addAttribute("adminUsername", getLoggedInUsername());
            
            return "admin_assessment_data_view";
            
        } catch (Exception e) {
            model.addAttribute("errorMessage", "Error loading data: " + e.getMessage());
            e.printStackTrace();
            return "redirect:/admin/data-views";
        }
    }
    
    /**
     * View overall data table across all assessments
     */
    @GetMapping("/overall")
    @Transactional(readOnly = true)
    public String viewOverallData(Model model) {
        
        try {
            OverallDataViewDto dataView = dataViewService.buildOverallDataView();
            
            model.addAttribute("dataView", dataView);
            model.addAttribute("adminUsername", getLoggedInUsername());
            
            return "admin_overall_data_view";
            
        } catch (Exception e) {
            model.addAttribute("errorMessage", "Error loading overall data: " + e.getMessage());
            e.printStackTrace();
            return "redirect:/admin/data-views";
        }
    }
}