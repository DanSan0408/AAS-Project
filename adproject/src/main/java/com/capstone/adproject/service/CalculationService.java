package com.capstone.adproject.service;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import org.springframework.stereotype.Service;

import com.capstone.adproject.dto.AssessmentDataDto;
import com.capstone.adproject.dto.RubricCalculationDto;
import com.capstone.adproject.dto.StudentAssessmentDataDto;
import com.capstone.adproject.model.Assessment;
import com.capstone.adproject.model.Group;
import com.capstone.adproject.model.Mark;
import com.capstone.adproject.model.Rubric;
import com.capstone.adproject.model.Student;
import com.capstone.adproject.repositories.AssessmentCommentRepository;
import com.capstone.adproject.repositories.LecturerGroupAssignmentRepository;
import com.capstone.adproject.repositories.MarkRepository;

@Service
public class CalculationService {

    private final MarkRepository markRepository;
    private final LecturerGroupAssignmentRepository lecturerAssignmentRepository;
    private final AssessmentCommentRepository commentRepository;

    public CalculationService(
            MarkRepository markRepository,
            LecturerGroupAssignmentRepository lecturerAssignmentRepository,
            AssessmentCommentRepository commentRepository) {
        this.markRepository = markRepository;
        this.lecturerAssignmentRepository = lecturerAssignmentRepository;
        this.commentRepository = commentRepository;
    }

    /**
     * Calculate data for a specific assessment
     */
    public AssessmentDataDto calculateAssessmentData(Assessment assessment, List<Student> students) {
        System.out.println("\n╔════════════════════════════════════════════════════════════════╗");
        System.out.println("║ CALCULATE ASSESSMENT DATA CALLED");
        System.out.println("║ Assessment: " + assessment.getTitle());
        System.out.println("║ Students: " + students.size());
        System.out.println("╚════════════════════════════════════════════════════════════════╝\n");
        
        AssessmentDataDto dto = new AssessmentDataDto();
        dto.setAssessment(assessment);
        
        List<StudentAssessmentDataDto> studentDataList = new ArrayList<>();
        
        // Sort students alphabetically by name
        students.sort((s1, s2) -> s1.getUsername().compareToIgnoreCase(s2.getUsername()));
        
        for (Student student : students) {
            StudentAssessmentDataDto studentData = calculateStudentAssessmentData(student, assessment);
            studentDataList.add(studentData);
        }
        
        dto.setStudentDataList(studentDataList);
        return dto;
    }

    /**
     * Calculate all data for a single student in an assessment
     */
    public StudentAssessmentDataDto calculateStudentAssessmentData(Student student, Assessment assessment) {
        StudentAssessmentDataDto dto = new StudentAssessmentDataDto();
        dto.setStudent(student);
        dto.setAssessment(assessment);
        
        System.out.println("\n╔══════════════════════════════════════════════════════════╗");
        System.out.println("║ STARTING CALCULATION FOR: " + student.getUsername());
        System.out.println("╚══════════════════════════════════════════════════════════╝");
        
        // Get all marks for this student in this assessment
        List<Mark> marks = markRepository.findByEvaluatedStudentAndAssessment(student, assessment);
        
        // Calculate factor - check if this assessment should use peer evaluation factors
        boolean isPeerAssessment = false;
        
        // Method 1: Check assessment title
        String assessmentTitle = assessment.getTitle() != null ? assessment.getTitle().toLowerCase() : "";
        if (assessmentTitle.contains("peer") || assessmentTitle.contains("self")) {
            isPeerAssessment = true;
        }
        
        // Method 2: Check if assessment has ANY Group Assessment rubrics with peer evaluations
        if (!isPeerAssessment && assessment.getRubrics() != null) {
            boolean hasGroupRubrics = assessment.getRubrics().stream()
                .anyMatch(r -> "Group Assessment".equalsIgnoreCase(r.getAssessmentTypes()));
            
            if (hasGroupRubrics) {
                // Check if students are evaluating each other (peer evaluation)
                boolean hasPeerEvaluations = marks.stream()
                    .anyMatch(m -> m.getEvaluatorStudent() != null && 
                                  !m.getEvaluatorStudent().getId().equals(student.getId()));
                
                if (hasPeerEvaluations) {
                    isPeerAssessment = true;
                }
            }
        }
        
        if (isPeerAssessment) {
            dto.setFactor(calculateStudentFactor(student, assessment));
        } else {
            dto.setFactor(1.0); // Default factor for non-peer assessments
        }
        
        System.out.println("✓ Factor calculated: " + dto.getFactor());
        
        // Process each rubric
        Map<Long, RubricCalculationDto> rubricCalculations = new HashMap<>();
        
        System.out.println("✓ Processing " + assessment.getRubrics().size() + " rubrics...");
        
        for (Rubric rubric : assessment.getRubrics()) {
            RubricCalculationDto rubricCalc = calculateRubricData(student, assessment, rubric, marks);
            rubricCalculations.put(rubric.getId(), rubricCalc);
            System.out.println("  → " + rubric.getName() + ": Mr=" + rubricCalc.getEvaluatedRubricMark());
        }
        
        dto.setRubricCalculations(rubricCalculations);
        
        // ✅ NOW calculate weighted marks AFTER we have the factor
        System.out.println("\n┌─────────────────────────────────────────────────────────┐");
        System.out.println("│ CALCULATING WEIGHTED MARKS                              │");
        System.out.println("├─────────────────────────────────────────────────────────┤");
        System.out.println("│ Student: " + student.getUsername());
        System.out.println("│ Factor: " + dto.getFactor());
        System.out.println("└─────────────────────────────────────────────────────────┘");
        
        for (RubricCalculationDto rubricCalc : rubricCalculations.values()) {
            String assessmentType = rubricCalc.getAssessmentType();
            Double evaluatedMark = rubricCalc.getEvaluatedRubricMark();
            
            System.out.println("\n  Rubric: " + rubricCalc.getRubric().getName());
            System.out.println("    Assessment Type: " + assessmentType);
            System.out.println("    Evaluated Mark (Mr) BEFORE: " + evaluatedMark);
            
            if (assessmentType != null && "Group Assessment".equalsIgnoreCase(assessmentType.trim())) {
                // For Group Assessment: apply factor
                Double weighted = dto.getFactor() * evaluatedMark;
                rubricCalc.setWeightedRubricMark(weighted);
                
                System.out.println("    → GROUP ASSESSMENT");
                System.out.println("    → Weighted = " + dto.getFactor() + " × " + evaluatedMark + " = " + weighted);
                System.out.println("    → Mr (unweighted): " + rubricCalc.getEvaluatedRubricMark());
                System.out.println("    → Wr (weighted): " + rubricCalc.getWeightedRubricMark());
            } else {
                // For Individual Assessment: no factor
                rubricCalc.setWeightedRubricMark(evaluatedMark);
                
                System.out.println("    → INDIVIDUAL ASSESSMENT (no factor)");
                System.out.println("    → Weighted = " + evaluatedMark);
            }
        }
        
        System.out.println("\n✓ Weighted marks calculated!\n");
        
        // Calculate CLO-based sums
        dto.setCloSums(calculateCloSums(rubricCalculations));
        dto.setCloWeightedSums(calculateCloWeightedSums(rubricCalculations, dto.getFactor()));
        
        // Calculate total marks for assessment
        dto.setTotalMarks(calculateTotalMarks(dto.getCloWeightedSums()));
        
        // Get comments
        dto.setGroupComments(getCommentsForStudent(student, assessment, "Group Assessment"));
        dto.setIndividualComments(getCommentsForStudent(student, assessment, "Individual Assessment"));
        
        System.out.println("╔══════════════════════════════════════════════════════════╗");
        System.out.println("║ FINISHED CALCULATION FOR: " + student.getUsername());
        System.out.println("╚══════════════════════════════════════════════════════════╝\n");
        
        return dto;
    }

    /**
     * Calculate rubric-specific data for a student
     */
    private RubricCalculationDto calculateRubricData(Student student, Assessment assessment, 
                                                     Rubric rubric, List<Mark> allMarks) {
        RubricCalculationDto dto = new RubricCalculationDto();
        dto.setRubric(rubric);
        
        // Filter marks for this rubric
        List<Mark> rubricMarks = allMarks.stream()
            .filter(m -> m.getRubric() != null && m.getRubric().getId().equals(rubric.getId()))
            .collect(Collectors.toList());
        
        if (rubric.hasSubRubrics()) {
            // Sub-rubric calculation
            dto.setRubricFactor(calculateSubRubricFactor(student, assessment, rubric, rubricMarks));
        } else {
            // Direct rating calculation
            dto.setRubricFactor(calculateDirectRatingFactor(student, assessment, rubric, rubricMarks));
        }
        
        // Calculate evaluated rubric mark (UNWEIGHTED): Mr = Rf × Rm
        Double configuredMark = rubric.getMarks() != null ? rubric.getMarks().doubleValue() : 0.0;
        dto.setEvaluatedRubricMark(dto.getRubricFactor() * configuredMark);
        
        // Store CLO
        dto.setClo(rubric.getClo() != null ? rubric.getClo() : 0);
        
        // Store assessment type
        dto.setAssessmentType(rubric.getAssessmentTypes());
        
        // Get evaluator details
        dto.setEvaluatorDetails(getEvaluatorDetails(student, assessment, rubric, rubricMarks));
        
        return dto;
    }

    /**
     * Calculate sub-rubric factor: Rf = μln / rmax
     * Where μln = average of all μsrmln (sub rubric marks per lecturer)
     */
    private Double calculateSubRubricFactor(Student student, Assessment assessment, 
                                           Rubric rubric, List<Mark> marks) {
        if (marks.isEmpty()) {
            return 0.0;
        }
        
        // Group marks by evaluator (lecturer)
        Map<Long, List<Mark>> marksByLecturer = marks.stream()
            .filter(m -> m.getSubRubric() != null)
            .collect(Collectors.groupingBy(m -> {
                // For lecturer marks, evaluatorStudent represents the lecturer
                return m.getEvaluatorStudent().getId();
            }));
        
        if (marksByLecturer.isEmpty()) {
            return 0.0;
        }
        
        // Calculate μsrmln for each lecturer
        List<Double> lecturerAverages = new ArrayList<>();
        
        for (Map.Entry<Long, List<Mark>> entry : marksByLecturer.entrySet()) {
            List<Mark> lecturerMarks = entry.getValue();
            
            // Calculate average rating for this lecturer: μsrmln = (r1 + ... + rm) / m
            double sum = lecturerMarks.stream()
                .mapToDouble(m -> m.getRating() != null && m.getRating().getMarks() != null 
                    ? m.getRating().getMarks().doubleValue() 
                    : 0.0)
                .sum();
            
            double average = lecturerMarks.size() > 0 ? sum / lecturerMarks.size() : 0.0;
            lecturerAverages.add(average);
        }
        
        // Calculate μln: average of all lecturer averages
        double muLn = lecturerAverages.stream()
            .mapToDouble(Double::doubleValue)
            .average()
            .orElse(0.0);
        
        // Get maximum rating value (rmax) from rubric's sub-rubrics
        double rmax = rubric.getSubRubrics().stream()
            .flatMap(sr -> sr.getRatings().stream())
            .mapToDouble(r -> r.getMarks() != null ? r.getMarks().doubleValue() : 0.0)
            .max()
            .orElse(1.0); // Avoid division by zero
        
        // Rf = μln / rmax
        return rmax > 0 ? muLn / rmax : 0.0;
    }

    /**
     * Calculate direct rating factor: Rf = dr / drmax
     * μRf = (Rf1 + ... + Rfn) / n
     */
    private Double calculateDirectRatingFactor(Student student, Assessment assessment, 
                                               Rubric rubric, List<Mark> marks) {
        if (marks.isEmpty()) {
            return 0.0;
        }
        
        // Get maximum rating value
        double drmax = rubric.getRatings().stream()
            .mapToDouble(r -> r.getMarks() != null ? r.getMarks().doubleValue() : 0.0)
            .max()
            .orElse(1.0);
        
        if (drmax == 0) {
            return 0.0;
        }
        
        // Calculate Rf for each evaluator
        List<Double> rubricFactors = marks.stream()
            .filter(m -> m.getRating() != null && m.getRating().getMarks() != null)
            .map(m -> {
                double dr = m.getRating().getMarks().doubleValue();
                return dr / drmax; // Rf = dr / drmax
            })
            .collect(Collectors.toList());
        
        // Calculate average rubric factor: μRf = (Rf1 + ... + Rfn) / n
        return rubricFactors.stream()
            .mapToDouble(Double::doubleValue)
            .average()
            .orElse(0.0);
    }

    /**
     * Calculate student factor for peer/self assessment
     * Ia = (r1 + ... + rn) / n
     * Ga = (Ia1 + ... + Ian) / n
     * f = Ia / Ga
     */
    private Double calculateStudentFactor(Student student, Assessment assessment) {
        Group group = student.getGroup();
        if (group == null || group.getStudents() == null || group.getStudents().isEmpty()) {
            return 1.0;
        }
        
        List<Student> groupMembers = new ArrayList<>(group.getStudents());
        
        // Calculate individual averages for all group members
        Map<Long, Double> individualAverages = new HashMap<>();
        
        for (Student member : groupMembers) {
            // Get all peer ratings this member received
            List<Mark> receivedMarks = markRepository.findByEvaluatedStudentAndAssessment(member, assessment);
            
            if (!receivedMarks.isEmpty()) {
                double sum = receivedMarks.stream()
                    .filter(m -> m.getRating() != null && m.getRating().getMarks() != null)
                    .mapToDouble(m -> m.getRating().getMarks().doubleValue())
                    .sum();
                
                double individualAverage = sum / receivedMarks.size();
                individualAverages.put(member.getId(), individualAverage);
            }
        }
        
        if (individualAverages.isEmpty()) {
            return 1.0;
        }
        
        // Calculate group average: Ga = (Ia1 + ... + Ian) / n
        double groupAverage = individualAverages.values().stream()
            .mapToDouble(Double::doubleValue)
            .average()
            .orElse(1.0);
        
        if (groupAverage == 0) {
            return 1.0;
        }
        
        // Get this student's individual average
        Double individualAverage = individualAverages.get(student.getId());
        if (individualAverage == null) {
            return 1.0;
        }
        
        // Factor: f = Ia / Ga
        return individualAverage / groupAverage;
    }

    /**
     * Calculate CLO-based sums: SClonMrm (unweighted)
     */
    private Map<Integer, Double> calculateCloSums(Map<Long, RubricCalculationDto> rubricCalculations) {
        Map<Integer, Double> cloSums = new HashMap<>();
        
        for (RubricCalculationDto rubricCalc : rubricCalculations.values()) {
            Integer clo = rubricCalc.getClo();
            Double evaluatedMark = rubricCalc.getEvaluatedRubricMark();
            
            cloSums.put(clo, cloSums.getOrDefault(clo, 0.0) + evaluatedMark);
        }
        
        return cloSums;
    }

    /**
     * Calculate weighted CLO sums: SClonWrm
     * Uses the weighted marks that were already calculated
     */
    private Map<Integer, Double> calculateCloWeightedSums(Map<Long, RubricCalculationDto> rubricCalculations, 
                                                           Double factor) {
        Map<Integer, Double> cloWeightedSums = new HashMap<>();
        
        for (RubricCalculationDto rubricCalc : rubricCalculations.values()) {
            Integer clo = rubricCalc.getClo();
            Double weightedMark = rubricCalc.getWeightedRubricMark();
            
            cloWeightedSums.put(clo, cloWeightedSums.getOrDefault(clo, 0.0) + weightedMark);
        }
        
        return cloWeightedSums;
    }

    /**
     * Calculate total marks: T = SClo1Wr + ... + SClonWr
     */
    private Double calculateTotalMarks(Map<Integer, Double> cloWeightedSums) {
        return cloWeightedSums.values().stream()
            .mapToDouble(Double::doubleValue)
            .sum();
    }

    /**
     * Get evaluator details for display
     */
    private List<Map<String, Object>> getEvaluatorDetails(Student student, Assessment assessment, 
                                                          Rubric rubric, List<Mark> marks) {
        List<Map<String, Object>> details = new ArrayList<>();
        
        // Collect all unique evaluators for this rubric
        Map<String, Map<String, Object>> uniqueEvaluators = new java.util.LinkedHashMap<>();
        
        for (Mark mark : marks) {
            String evaluatorKey = null;
            Map<String, Object> detail = new HashMap<>();
            
            // Check if this is a supervisor evaluation
            if (mark.getSupervisorId() != null && mark.getIsSupervisorMark() != null && mark.getIsSupervisorMark()) {
                // This is an industrial supervisor evaluation
                detail.put("evaluatorType", "Supervisor");
                detail.put("supervisorId", mark.getSupervisorId());
                
                // Try to get supervisor name from the group's industrial supervisor
                if (student.getGroup() != null && student.getGroup().getIndustrialSupervisor() != null) {
                    if (student.getGroup().getIndustrialSupervisor().getId().equals(mark.getSupervisorId())) {
                        String supervisorName = student.getGroup().getIndustrialSupervisor().getUsername();
                        String supervisorEmail = student.getGroup().getIndustrialSupervisor().getEmail();
                        detail.put("evaluatorName", supervisorName);
                        detail.put("evaluatorEmail", supervisorEmail);
                        evaluatorKey = "supervisor_" + mark.getSupervisorId();
                    }
                }
                
                // If supervisor details not found, use generic label
                if (!detail.containsKey("evaluatorName")) {
                    detail.put("evaluatorName", "Industrial Supervisor");
                    detail.put("evaluatorEmail", "supervisor@utm.my");
                    evaluatorKey = "supervisor_generic";
                }
            } else {
                // This is a lecturer evaluation
                // Find ALL lecturers who evaluated this student's group for this assessment
                if (student.getGroup() != null) {
                    List<com.capstone.adproject.model.LecturerGroupAssignment> assignments = 
                        lecturerAssignmentRepository.findByAssessmentAndGroup(assessment, student.getGroup());
                    
                    if (!assignments.isEmpty()) {
                        // Process ALL lecturer assignments, not just the first one
                        for (com.capstone.adproject.model.LecturerGroupAssignment assignment : assignments) {
                            com.capstone.adproject.model.Lecturer lecturer = assignment.getLecturer();
                            String lecturerKey = "lecturer_" + lecturer.getId();
                            
                            // Only add if not already added
                            if (!uniqueEvaluators.containsKey(lecturerKey)) {
                                Map<String, Object> lecturerDetail = new HashMap<>();
                                lecturerDetail.put("evaluatorName", lecturer.getUsername());
                                lecturerDetail.put("evaluatorEmail", lecturer.getEmail());
                                lecturerDetail.put("evaluatorType", "Lecturer");
                                
                                // Add rating info if this mark is from this lecturer
                                if (mark.getRating() != null) {
                                    lecturerDetail.put("ratingValue", mark.getRating().getMarks());
                                    lecturerDetail.put("ratingName", mark.getRating().getName());
                                }
                                
                                lecturerDetail.put("markValue", mark.getMarkValue());
                                uniqueEvaluators.put(lecturerKey, lecturerDetail);
                            }
                        }
                        continue; // Skip adding detail to the list here, we'll add from uniqueEvaluators
                    } else {
                        // No assignment found, use generic lecturer label
                        detail.put("evaluatorName", "Lecturer");
                        detail.put("evaluatorEmail", "lecturer@utm.my");
                        detail.put("evaluatorType", "Lecturer");
                        evaluatorKey = "lecturer_generic";
                    }
                } else {
                    // Student has no group
                    detail.put("evaluatorName", "Lecturer");
                    detail.put("evaluatorEmail", "lecturer@utm.my");
                    detail.put("evaluatorType", "Lecturer");
                    evaluatorKey = "lecturer_no_group";
                }
            }
            
            // Rating value (for non-lecturer marks or generic lecturers)
            if (mark.getRating() != null && evaluatorKey != null) {
                detail.put("ratingValue", mark.getRating().getMarks());
                detail.put("ratingName", mark.getRating().getName());
            }
            
            if (evaluatorKey != null) {
                detail.put("markValue", mark.getMarkValue());
                
                // Add to unique evaluators if not already there
                if (!uniqueEvaluators.containsKey(evaluatorKey)) {
                    uniqueEvaluators.put(evaluatorKey, detail);
                }
            }
        }
        
        // Convert unique evaluators map to list
        details.addAll(uniqueEvaluators.values());
        
        return details;
    }

    /**
     * Get comments for student in assessment
     */
    private List<String> getCommentsForStudent(Student student, Assessment assessment, String assessmentType) {
        return commentRepository
            .findByEvaluatedStudentAndAssessmentAndRubricAssessmentType(student, assessment, assessmentType)
            .stream()
            .map(comment -> comment.getDisplayName() + ": " + comment.getCommentText())
            .collect(Collectors.toList());
    }

    /**
     * Calculate grand total across all assessments: Gt = T1 + ... + Tn
     */
    public Double calculateGrandTotal(Student student, List<Assessment> assessments) {
        double grandTotal = 0.0;
        
        for (Assessment assessment : assessments) {
            StudentAssessmentDataDto studentData = calculateStudentAssessmentData(student, assessment);
            grandTotal += studentData.getTotalMarks();
        }
        
        return grandTotal;
    }

    /**
     * Get detailed evaluator information including sub-rubric ratings
     */
    public Map<String, Object> getDetailedEvaluatorInfo(Student student, Assessment assessment, Rubric rubric) {
        Map<String, Object> result = new HashMap<>();
        
        // Get all marks for this rubric
        List<Mark> marks = markRepository.findByEvaluatedStudentAndAssessment(student, assessment)
            .stream()
            .filter(m -> m.getRubric() != null && m.getRubric().getId().equals(rubric.getId()))
            .collect(Collectors.toList());
        
        // Group by evaluator
        Map<String, List<Mark>> marksByEvaluator = new HashMap<>();
        
        for (Mark mark : marks) {
            String evaluatorKey = getEvaluatorKey(mark, student);
            marksByEvaluator.computeIfAbsent(evaluatorKey, k -> new ArrayList<>()).add(mark);
        }
        
        result.put("marksByEvaluator", marksByEvaluator);
        result.put("allMarks", marks);
        
        return result;
    }

    /**
     * Get evaluator identifier key
     */
    private String getEvaluatorKey(Mark mark, Student student) {
        if (mark.getSupervisorId() != null && mark.getIsSupervisorMark() != null && mark.getIsSupervisorMark()) {
            if (student.getGroup() != null && student.getGroup().getIndustrialSupervisor() != null) {
                if (student.getGroup().getIndustrialSupervisor().getId().equals(mark.getSupervisorId())) {
                    return student.getGroup().getIndustrialSupervisor().getUsername();
                }
            }
            return "Industrial Supervisor";
        } else if (mark.getEvaluatorStudent() != null) {
            // Try to find lecturer from group assignment
            if (student.getGroup() != null) {
                List<com.capstone.adproject.model.LecturerGroupAssignment> assignments = 
                    lecturerAssignmentRepository.findByGroup(student.getGroup());
                
                if (!assignments.isEmpty()) {
                    // For now, use the first lecturer found
                    // In future, you might want to match by evaluatorStudent ID if that represents lecturer
                    return assignments.get(0).getLecturer().getUsername();
                }
            }
            return "Lecturer";
        }
        return "Unknown Evaluator";
    }

    /**
     * Get detailed sub-rubric calculations per evaluator
     */
    public Map<String, Map<Long, Double>> getSubRubricRatingsByEvaluator(
            Student student, Assessment assessment, Rubric rubric) {
        
        Map<String, Map<Long, Double>> evaluatorSubRubricRatings = new java.util.LinkedHashMap<>();
        
        // Get all marks for this rubric
        List<Mark> marks = markRepository.findByEvaluatedStudentAndAssessment(student, assessment)
            .stream()
            .filter(m -> m.getRubric() != null && m.getRubric().getId().equals(rubric.getId()))
            .filter(m -> m.getSubRubric() != null) // Only sub-rubric marks
            .collect(Collectors.toList());
        
        System.out.println("\n=== DETAILED MARKS DEBUG ===");
        System.out.println("Student: " + student.getUsername());
        System.out.println("Rubric: " + rubric.getName());
        System.out.println("Total marks found: " + marks.size());
        
        // CRITICAL DEBUG: Check lecturer assignments for this group
        if (student.getGroup() != null) {
            List<com.capstone.adproject.model.LecturerGroupAssignment> assignments = 
                lecturerAssignmentRepository.findByAssessmentAndGroup(assessment, student.getGroup());
            
            System.out.println("\n** LECTURER ASSIGNMENTS FOR THIS GROUP **");
            System.out.println("Group: " + student.getGroup().getGroupName() + " (Size: " + student.getGroup().getStudents().size() + ")");
            System.out.println("Number of lecturer assignments: " + assignments.size());
            for (com.capstone.adproject.model.LecturerGroupAssignment assignment : assignments) {
                com.capstone.adproject.model.Lecturer lecturer = assignment.getLecturer();
                System.out.println("  - Lecturer ID: " + lecturer.getId() + ", Name: " + lecturer.getUsername());
            }
            System.out.println("******************************************\n");
        }
        
        // CRITICAL DEBUG: Show lecturer_id for each mark
        System.out.println("** MARK DETAILS WITH LECTURER_ID **");
        for (Mark mark : marks) {
            System.out.print("Mark ID " + mark.getId() + ": ");
            System.out.print("SubRubric: " + mark.getSubRubric().getName() + ", ");
            System.out.print("Rating: " + (mark.getRating() != null ? String.format("%.2f", mark.getRating().getMarks()) : "null") + ", ");
            
            if (mark.getLecturer() != null) {
                System.out.print("lecturer_id=" + mark.getLecturer().getId() + " (");
                System.out.print(mark.getLecturer().getUsername() + ")");
            } else {
                System.out.print("lecturer_id=NULL");
            }
            
            if (mark.getIsSupervisorMark() != null && mark.getIsSupervisorMark()) {
                System.out.print(", is_supervisor_mark=TRUE");
            }
            
            System.out.println();
        }
        System.out.println("************************************\n");
        
        // Group by actual evaluator
        for (Mark mark : marks) {
            String evaluatorKey;
            
            // Check if it's a supervisor mark
            if (mark.getIsSupervisorMark() != null && mark.getIsSupervisorMark()) {
                if (student.getGroup() != null && student.getGroup().getIndustrialSupervisor() != null) {
                    evaluatorKey = student.getGroup().getIndustrialSupervisor().getUsername();
                } else {
                    evaluatorKey = "Supervisor " + (mark.getSupervisorId() != null ? mark.getSupervisorId() : "Unknown");
                }
            } else if (mark.getLecturer() != null) {
                // Use the lecturer field
                evaluatorKey = mark.getLecturer().getUsername();
            } else {
                // Fallback for old marks without lecturer_id
                evaluatorKey = "Lecturer (ID unknown)";
            }
            
            Long subRubricId = mark.getSubRubric().getId();
            Double ratingValue = mark.getRating() != null && mark.getRating().getMarks() != null 
                ? mark.getRating().getMarks().doubleValue() 
                : 0.0;
            
            System.out.println("  ASSIGNING: Mark ID " + mark.getId() + " -> Evaluator: " + evaluatorKey + ", Rating: " + ratingValue);
            
            evaluatorSubRubricRatings
                .computeIfAbsent(evaluatorKey, k -> new java.util.LinkedHashMap<>())
                .put(subRubricId, ratingValue);
        }
        
        System.out.println("\nFinal grouping by evaluators: " + evaluatorSubRubricRatings.keySet());
        for (Map.Entry<String, Map<Long, Double>> entry : evaluatorSubRubricRatings.entrySet()) {
            System.out.println("  " + entry.getKey() + ": " + entry.getValue().size() + " sub-rubrics rated");
        }
        System.out.println("===========================\n");
        
        return evaluatorSubRubricRatings;
    }

    /**
     * Calculate μsrmln (sub rubric mark per lecturer) for each evaluator
     * μsrmln = (r1 + ... + rm) / m
     * Where m is the number of sub-rubrics
     */
    public Map<String, Double> calculateMuSrmln(
            Student student, Assessment assessment, Rubric rubric) {
        
        Map<String, Double> muSrmlnByEvaluator = new java.util.LinkedHashMap<>();
        
        // Get sub-rubric ratings by evaluator
        Map<String, Map<Long, Double>> evaluatorRatings = 
            getSubRubricRatingsByEvaluator(student, assessment, rubric);
        
        System.out.println("\n=== μsrmln CALCULATION ===");
        System.out.println("Student: " + student.getUsername());
        System.out.println("Rubric: " + rubric.getName());
        
        // Calculate average for each evaluator
        for (Map.Entry<String, Map<Long, Double>> entry : evaluatorRatings.entrySet()) {
            String evaluatorKey = entry.getKey();
            Map<Long, Double> subRubricRatings = entry.getValue();
            
            if (!subRubricRatings.isEmpty()) {
                double sum = subRubricRatings.values().stream()
                    .mapToDouble(Double::doubleValue)
                    .sum();
                int m = subRubricRatings.size();
                double average = sum / m;
                
                System.out.println("  " + evaluatorKey + ": (" + 
                                 subRubricRatings.values().stream()
                                     .map(v -> String.format("%.2f", v))
                                     .collect(Collectors.joining(" + ")) +
                                 ") / " + m + " = " + String.format("%.3f", average));
                
                muSrmlnByEvaluator.put(evaluatorKey, average);
            }
        }
        
        System.out.println("========================\n");
        
        return muSrmlnByEvaluator;
    }

    /**
     * Calculate μln (sub rubric factor) - average of all μsrmln values
     * μln = (μsr1l1 + ... + μsrmln) / n
     * Where n is the number of evaluators (lecturers)
     */
    public Double calculateMuLn(Student student, Assessment assessment, Rubric rubric) {
        Map<String, Double> muSrmlnValues = calculateMuSrmln(student, assessment, rubric);
        
        if (muSrmlnValues.isEmpty()) {
            return 0.0;
        }
        
        double sum = muSrmlnValues.values().stream()
            .mapToDouble(Double::doubleValue)
            .sum();
        
        double muLn = sum / muSrmlnValues.size();
        
        System.out.println("Student: " + student.getUsername() + ", Rubric: " + rubric.getName());
        System.out.println("  muSrmlnByEvaluator: " + muSrmlnValues);
        System.out.println("  muLn: " + muLn);
        
        return muLn;
    }

    /**
     * Get evaluator-specific comments for a rubric
     */
    public Map<String, List<String>> getEvaluatorCommentsForRubric(
            Student student, Assessment assessment, Rubric rubric) {
        
        Map<String, List<String>> evaluatorComments = new java.util.LinkedHashMap<>();
        
        // Get all comments for this assessment and student
        List<com.capstone.adproject.model.AssessmentComment> comments = 
            commentRepository.findByEvaluatedStudentAndAssessment(student, assessment);
        
        // Filter for this rubric's assessment type
        String assessmentType = rubric.getAssessmentTypes();
        comments = comments.stream()
            .filter(c -> assessmentType != null && assessmentType.equals(c.getRubricAssessmentType()))
            .collect(Collectors.toList());
        
        // Group by evaluator
        for (com.capstone.adproject.model.AssessmentComment comment : comments) {
            String evaluatorName = comment.getEvaluatorName();
            if (evaluatorName == null || evaluatorName.isEmpty()) {
                evaluatorName = "Unknown Evaluator";
            }
            
            String commentText = comment.getCommentText();
            if (comment.getCommentLabel() != null) {
                commentText = comment.getCommentLabel() + ": " + commentText;
            }
            
            evaluatorComments
                .computeIfAbsent(evaluatorName, k -> new ArrayList<>())
                .add(commentText);
        }
        
        return evaluatorComments;
    }
}
