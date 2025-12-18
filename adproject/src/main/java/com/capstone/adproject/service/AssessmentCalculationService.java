package com.capstone.adproject.service;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.capstone.adproject.dto.RubricCalculationDto;
import com.capstone.adproject.dto.StudentAssessmentResultDto;
import com.capstone.adproject.model.Assessment;
import com.capstone.adproject.model.IndustrialSupervisor;
import com.capstone.adproject.model.Lecturer;
import com.capstone.adproject.model.LecturerGroupAssignment;
import com.capstone.adproject.model.Mark;
import com.capstone.adproject.model.Rating;
import com.capstone.adproject.model.Rubric;
import com.capstone.adproject.model.Student;
import com.capstone.adproject.model.SubRubric;
import com.capstone.adproject.repositories.AssessmentRepository;
import com.capstone.adproject.repositories.IndustrialSupervisorRepository;
import com.capstone.adproject.repositories.LecturerGroupAssignmentRepository;
import com.capstone.adproject.repositories.MarkRepository;
import com.capstone.adproject.repositories.RatingRepository;
import com.capstone.adproject.repositories.RubricRepository;
import com.capstone.adproject.repositories.StudentRepository;
import com.capstone.adproject.repositories.SubRubricRepository;

@Service
public class AssessmentCalculationService {
    
    @Autowired
    private StudentRepository studentRepository;
    
    @Autowired
    private AssessmentRepository assessmentRepository;
    
    @Autowired
    private RubricRepository rubricRepository;
    
    @Autowired
    private SubRubricRepository subRubricRepository;
    
    @Autowired
    private MarkRepository markRepository;
    
    @Autowired
    private RatingRepository ratingRepository;
    
    @Autowired
    private FactorCalculationService factorCalculationService;
    
    @Autowired
    private LecturerGroupAssignmentRepository lecturerGroupAssignmentRepository;
    
    @Autowired
    private IndustrialSupervisorRepository industrialSupervisorRepository;
    
    /**
     * ✅ FIXED: Calculate student assessment result using correct formulas
     */
    @Transactional(readOnly = true)
    public StudentAssessmentResultDto calculateStudentAssessmentResult(Long studentId, Long assessmentId) {
        Student student = studentRepository.findById(studentId)
            .orElseThrow(() -> new IllegalArgumentException("Student not found"));
        
        Assessment assessment = assessmentRepository.findById(assessmentId)
            .orElseThrow(() -> new IllegalArgumentException("Assessment not found"));
        
        StudentAssessmentResultDto result = new StudentAssessmentResultDto();
        result.setStudentId(studentId);
        result.setStudentName(student.getUsername());
        result.setAssessmentId(assessmentId);
        result.setAssessmentTitle(assessment.getTitle());
        
        // Get student factor from peer/self assessment
        Double studentFactor = getStudentFactor(studentId);
        result.setStudentFactor(studentFactor);
        
        System.out.println("\n=== CALCULATING FOR STUDENT ===");
        System.out.println("Student: " + student.getUsername() + ", Factor: " + studentFactor);
        
        List<RubricCalculationDto> rubricCalculations = new ArrayList<>();
        
        double totalGroupMarks = 0.0;
        double totalIndividualMarks = 0.0;
        
        Map<Integer, Double> cloTotalMarks = new HashMap<>();
        Map<Integer, Double> cloWeightedMarks = new HashMap<>();
        Map<Long, Double> weightedRubricMarks = new HashMap<>();
        
        // Process each rubric
        for (Rubric rubric : assessment.getRubrics()) {
            RubricCalculationDto rubricCalc = new RubricCalculationDto();
            rubricCalc.setRubricId(rubric.getId());
            rubricCalc.setRubricName(rubric.getName());
            rubricCalc.setAssessmentType(rubric.getAssessmentTypes());
            rubricCalc.setClo(rubric.getClo());
            
            Double configuredRubricMark = rubric.getMarks() != null ? rubric.getMarks().doubleValue() : 0.0;
            rubricCalc.setConfiguredRubricMark(configuredRubricMark);
            
            System.out.println("\n  Rubric: " + rubric.getName());
            System.out.println("  Type: " + rubric.getAssessmentTypes());
            System.out.println("  Configured Mark (Rm): " + configuredRubricMark);
            
            // Calculate Rf (Rubric Factor) and Mr (Evaluated Rubric Mark)
            Double rubricFactor;
            Double evaluatedRubricMark;
            
            if (rubric.hasSubRubrics()) {
                // ✅ Sub-rubric calculation
                var subRubricResult = calculateSubRubricFactor(student, rubric, assessment);
                rubricFactor = subRubricResult.rubricFactor;
                evaluatedRubricMark = subRubricResult.evaluatedMark;
                
                System.out.println("  [SUB-RUBRIC] Rf: " + rubricFactor + ", Mr: " + evaluatedRubricMark);
            } else {
                // ✅ Direct rating calculation
                var directRatingResult = calculateDirectRatingFactor(student, rubric, assessment);
                rubricFactor = directRatingResult.rubricFactor;
                evaluatedRubricMark = directRatingResult.evaluatedMark;
                
                System.out.println("  [DIRECT RATING] Rf: " + rubricFactor + ", Mr: " + evaluatedRubricMark);
            }
            
            rubricCalc.setRubricFactor(rubricFactor);
            rubricCalc.setEvaluatedRubricMark(evaluatedRubricMark);
            
            // Accumulate totals
            if ("Group Assessment".equalsIgnoreCase(rubric.getAssessmentTypes())) {
                totalGroupMarks += evaluatedRubricMark;
                
                // Calculate Wr (Weighted Rubric) = f × Mr
                Double weightedRubric = studentFactor * evaluatedRubricMark;
                weightedRubricMarks.put(rubric.getId(), weightedRubric);
                
                System.out.println("  Wr (Weighted): " + weightedRubric);
                
                // SClonWrm for group assessment (weighted)
                Integer clo = rubric.getClo();
                if (clo != null) {
                    cloWeightedMarks.put(clo, cloWeightedMarks.getOrDefault(clo, 0.0) + weightedRubric);
                }
            } else {
                totalIndividualMarks += evaluatedRubricMark;
                
                // SClonWrm for individual assessment (not weighted)
                Integer clo = rubric.getClo();
                if (clo != null) {
                    cloWeightedMarks.put(clo, cloWeightedMarks.getOrDefault(clo, 0.0) + evaluatedRubricMark);
                }
            }
            
            // SClonMrm (sum based on CLO)
            Integer clo = rubric.getClo();
            if (clo != null) {
                cloTotalMarks.put(clo, cloTotalMarks.getOrDefault(clo, 0.0) + evaluatedRubricMark);
            }
            
            rubricCalculations.add(rubricCalc);
        }
        
        result.setRubricCalculations(rubricCalculations);
        result.setTotalGroupMarks(totalGroupMarks);
        result.setTotalIndividualMarks(totalIndividualMarks);
        result.setCloTotalMarks(cloTotalMarks);
        result.setCloWeightedMarks(cloWeightedMarks);
        result.setWeightedRubricMarks(weightedRubricMarks);
        
        // T (Total marks) = SClo1Wr + ... + SClonWr
        double totalMarks = cloWeightedMarks.values().stream()
            .mapToDouble(Double::doubleValue)
            .sum();
        result.setTotalMarks(totalMarks);
        
        System.out.println("\n  Tg (Total Group): " + totalGroupMarks);
        System.out.println("  Ts (Total Individual): " + totalIndividualMarks);
        System.out.println("  T (Total): " + totalMarks);
        System.out.println("  SClonMrm: " + cloTotalMarks);
        System.out.println("  SClonWrm: " + cloWeightedMarks);
        System.out.println("===============================\n");
        
        return result;
    }
    
    /**
     * ✅ Calculate sub-rubric factor using formula:
     * μ_srmln = (r1 + ... + rm) / m
     * μ_ln = (μ_sr1l1 + ... + μ_srmln) / n
     * Rf = μ_ln / rmax
     * Mr = Rf × Rm
     */
    private RubricFactorResult calculateSubRubricFactor(Student student, Rubric rubric, Assessment assessment) {
        List<SubRubric> subRubrics = subRubricRepository.findByRubric(rubric);
        
        if (subRubrics.isEmpty()) {
            return new RubricFactorResult(0.0, 0.0);
        }
        
        System.out.println("    Sub-rubrics found: " + subRubrics.size());
        
        // Get all evaluators for this student
        List<EvaluatorInfo> evaluators = getEvaluatorsForStudent(student, assessment);
        
        System.out.println("    Evaluators: " + evaluators.size());
        
        if (evaluators.isEmpty()) {
            return new RubricFactorResult(0.0, 0.0);
        }
        
        // Calculate μ_srmln for each evaluator
        List<Double> evaluatorAverages = new ArrayList<>();
        
        for (EvaluatorInfo evaluator : evaluators) {
            System.out.println("      Evaluator: " + evaluator.name);
            
            // Get marks from this evaluator for this rubric's sub-rubrics
            List<Mark> marks = getMarksFromEvaluator(student, rubric, evaluator, assessment);
            
            if (marks.isEmpty()) {
                System.out.println("        No marks from this evaluator");
                continue;
            }
            
            // Calculate μ_srmln = (r1 + ... + rm) / m
            double sum = 0.0;
            int count = 0;
            
            for (Mark mark : marks) {
                if (mark.getRating() != null && mark.getRating().getMarks() != null) {
                    double ratingValue = mark.getRating().getMarks().doubleValue();
                    sum += ratingValue;
                    count++;
                    System.out.println("        Sub-rubric rating: " + ratingValue);
                }
            }
            
            if (count > 0) {
                double evaluatorAvg = sum / count;
                evaluatorAverages.add(evaluatorAvg);
                System.out.println("        μ_srmln (evaluator avg): " + evaluatorAvg);
            }
        }
        
        if (evaluatorAverages.isEmpty()) {
            return new RubricFactorResult(0.0, 0.0);
        }
        
        // Calculate μ_ln = (μ_sr1l1 + ... + μ_srmln) / n
        double mu_ln = evaluatorAverages.stream()
            .mapToDouble(Double::doubleValue)
            .average()
            .orElse(0.0);
        
        System.out.println("    μ_ln (overall avg): " + mu_ln);
        
        // Get rmax (maximum rating for this rubric)
        Double rmax = getRubricMaxRating(rubric);
        System.out.println("    rmax: " + rmax);
        
        // Calculate Rf = μ_ln / rmax
        Double rubricFactor = rmax > 0 ? mu_ln / rmax : 0.0;
        
        // Calculate Mr = Rf × Rm
        Double configuredMark = rubric.getMarks() != null ? rubric.getMarks().doubleValue() : 0.0;
        Double evaluatedMark = rubricFactor * configuredMark;
        
        return new RubricFactorResult(rubricFactor, evaluatedMark);
    }
    
    /**
     * ✅ Calculate direct rating factor using formula:
     * Rf = dr / drmax
     * μ_Rf = (Rf1 + ... + Rfn) / n
     * Mr = μ_Rf × Rm
     */
    private RubricFactorResult calculateDirectRatingFactor(Student student, Rubric rubric, Assessment assessment) {
        // Get all evaluators for this student
        List<EvaluatorInfo> evaluators = getEvaluatorsForStudent(student, assessment);
        
        System.out.println("    Evaluators: " + evaluators.size());
        
        if (evaluators.isEmpty()) {
            return new RubricFactorResult(0.0, 0.0);
        }
        
        // Get drmax (maximum rating for this rubric)
        Double drmax = getRubricMaxRating(rubric);
        System.out.println("    drmax: " + drmax);
        
        if (drmax == 0) {
            return new RubricFactorResult(0.0, 0.0);
        }
        
        // Calculate Rf for each evaluator
        List<Double> rubricFactors = new ArrayList<>();
        
        for (EvaluatorInfo evaluator : evaluators) {
            System.out.println("      Evaluator: " + evaluator.name);
            
            // Get mark from this evaluator for this rubric
            List<Mark> marks = getMarksFromEvaluator(student, rubric, evaluator, assessment);
            
            if (marks.isEmpty()) {
                System.out.println("        No marks from this evaluator");
                continue;
            }
            
            // Should only be one mark for direct rating
            Mark mark = marks.get(0);
            
            if (mark.getRating() != null && mark.getRating().getMarks() != null) {
                double dr = mark.getRating().getMarks().doubleValue();
                
                // Rf = dr / drmax
                double Rf = dr / drmax;
                rubricFactors.add(Rf);
                
                System.out.println("        dr: " + dr + ", Rf: " + Rf);
            }
        }
        
        if (rubricFactors.isEmpty()) {
            return new RubricFactorResult(0.0, 0.0);
        }
        
        // Calculate μ_Rf = (Rf1 + ... + Rfn) / n
        double mu_Rf = rubricFactors.stream()
            .mapToDouble(Double::doubleValue)
            .average()
            .orElse(0.0);
        
        System.out.println("    μ_Rf (average Rf): " + mu_Rf);
        
        // Calculate Mr = μ_Rf × Rm
        Double configuredMark = rubric.getMarks() != null ? rubric.getMarks().doubleValue() : 0.0;
        Double evaluatedMark = mu_Rf * configuredMark;
        
        return new RubricFactorResult(mu_Rf, evaluatedMark);
    }
    
    /**
     * Get all evaluators for a student in an assessment
     */
    private List<EvaluatorInfo> getEvaluatorsForStudent(Student student, Assessment assessment) {
        List<EvaluatorInfo> evaluators = new ArrayList<>();
        
        // Get all marks for this student
        List<Mark> allMarks = markRepository.findByEvaluatedStudentAndAssessment(student, assessment);
        
        // Collect unique evaluators
        Map<String, EvaluatorInfo> seenEvaluators = new HashMap<>();
        
        for (Mark mark : allMarks) {
            String evaluatorKey;
            
            // Supervisor
            if (mark.getIsSupervisorMark() != null && mark.getIsSupervisorMark()) {
                Long supervisorId = mark.getSupervisorId();
                if (supervisorId != null) {
                    evaluatorKey = "SUPERVISOR_" + supervisorId;
                    
                    if (!seenEvaluators.containsKey(evaluatorKey)) {
                        IndustrialSupervisor supervisor = industrialSupervisorRepository.findById(supervisorId)
                            .orElse(null);
                        if (supervisor != null) {
                            EvaluatorInfo info = new EvaluatorInfo();
                            info.id = supervisorId;
                            info.name = supervisor.getUsername();
                            info.type = "SUPERVISOR";
                            seenEvaluators.put(evaluatorKey, info);
                        }
                    }
                }
            }
            // Peer/Self
            else if (mark.getEvaluatorStudent() != null) {
                Student evaluator = mark.getEvaluatorStudent();
                evaluatorKey = "PEER_" + evaluator.getId();
                
                if (!seenEvaluators.containsKey(evaluatorKey)) {
                    EvaluatorInfo info = new EvaluatorInfo();
                    info.id = evaluator.getId();
                    info.name = evaluator.getUsername();
                    info.type = "PEER_SELF";
                    seenEvaluators.put(evaluatorKey, info);
                }
            }
            // Lecturer
            else {
                if (student.getGroup() != null) {
                    List<LecturerGroupAssignment> assignments = lecturerGroupAssignmentRepository
                        .findByAssessmentAndGroup(assessment, student.getGroup());
                    
                    for (LecturerGroupAssignment assignment : assignments) {
                        Lecturer lecturer = assignment.getLecturer();
                        evaluatorKey = "LECTURER_" + lecturer.getId();
                        
                        if (!seenEvaluators.containsKey(evaluatorKey)) {
                            EvaluatorInfo info = new EvaluatorInfo();
                            info.id = lecturer.getId();
                            info.name = lecturer.getUsername();
                            info.type = "LECTURER";
                            seenEvaluators.put(evaluatorKey, info);
                        }
                    }
                }
            }
        }
        
        evaluators.addAll(seenEvaluators.values());
        return evaluators;
    }
    
    /**
     * Get marks from a specific evaluator for a rubric
     */
    private List<Mark> getMarksFromEvaluator(Student student, Rubric rubric, EvaluatorInfo evaluator, Assessment assessment) {
        List<Mark> allMarks = markRepository.findByEvaluatedStudentAndAssessmentAndRubric(student, assessment, rubric);
        
        return allMarks.stream()
            .filter(mark -> {
                if ("SUPERVISOR".equals(evaluator.type)) {
                    return mark.getIsSupervisorMark() != null && mark.getIsSupervisorMark() &&
                           mark.getSupervisorId() != null && mark.getSupervisorId().equals(evaluator.id);
                } else if ("PEER_SELF".equals(evaluator.type)) {
                    return mark.getEvaluatorStudent() != null &&
                           mark.getEvaluatorStudent().getId().equals(evaluator.id);
                } else if ("LECTURER".equals(evaluator.type)) {
                    // Lecturer marks have no evaluatorStudent and no supervisor flag
                    return (mark.getEvaluatorStudent() == null || mark.getEvaluatorStudent().getId().equals(student.getId())) &&
                           (mark.getIsSupervisorMark() == null || !mark.getIsSupervisorMark());
                }
                return false;
            })
            .collect(Collectors.toList());
    }
    
    /**
     * Get maximum rating for a rubric
     */
    private Double getRubricMaxRating(Rubric rubric) {
        if (rubric.hasSubRubrics()) {
            // For sub-rubrics, get max from first sub-rubric's ratings
            List<SubRubric> subRubrics = subRubricRepository.findByRubric(rubric);
            if (!subRubrics.isEmpty()) {
                List<Rating> ratings = ratingRepository.findBySubRubric(subRubrics.get(0));
                return ratings.stream()
                    .map(r -> r.getMarks() != null ? r.getMarks().doubleValue() : 0.0)
                    .max(Double::compare)
                    .orElse(0.0);
            }
        } else {
            // For direct rating, get max from rubric's ratings
            List<Rating> ratings = ratingRepository.findByRubric(rubric);
            return ratings.stream()
                .map(r -> r.getMarks() != null ? r.getMarks().doubleValue() : 0.0)
                .max(Double::compare)
                .orElse(0.0);
        }
        return 0.0;
    }
    
    /**
     * Get student factor from peer/self assessment
     */
    private Double getStudentFactor(Long studentId) {
        var groupFactors = factorCalculationService.calculateFactorsForAllGroups();
        
        for (var groupFactor : groupFactors) {
            for (var studentFactor : groupFactor.getStudentFactors()) {
                if (studentFactor.getStudentId().equals(studentId)) {
                    return studentFactor.getCappedFactor();
                }
            }
        }
        
        return 1.0; // Default factor
    }
    
    /**
     * Helper classes
     */
    private static class EvaluatorInfo {
        Long id;
        String name;
        String type;
    }
    
    private static class RubricFactorResult {
        Double rubricFactor;
        Double evaluatedMark;
        
        RubricFactorResult(Double rubricFactor, Double evaluatedMark) {
            this.rubricFactor = rubricFactor;
            this.evaluatedMark = evaluatedMark;
        }
    }
}