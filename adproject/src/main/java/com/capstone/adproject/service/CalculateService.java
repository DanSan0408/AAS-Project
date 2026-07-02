package com.capstone.adproject.service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;

import org.springframework.stereotype.Service;

import com.capstone.adproject.dto.AssessmentDataDto;
import com.capstone.adproject.dto.RubricCalculationDto;
import com.capstone.adproject.dto.StudentAssessmentDataDto;
import com.capstone.adproject.model.Assessment;
import com.capstone.adproject.model.AssessmentComment;
import com.capstone.adproject.model.Group;
import com.capstone.adproject.model.Mark;
import com.capstone.adproject.model.Rubric;
import com.capstone.adproject.model.Student;
import com.capstone.adproject.model.StudentResultOverride;
import com.capstone.adproject.repositories.AssessmentCommentRepository;
import com.capstone.adproject.repositories.LecturerGroupAssignmentRepository;
import com.capstone.adproject.repositories.MarkRepository;
import com.capstone.adproject.repositories.StudentResultOverrideRepository;
import com.capstone.adproject.repositories.FactorWeightageRepository;
import com.capstone.adproject.model.FactorWeightage;

@Service
public class CalculateService {

    private final MarkRepository markRepository;
    private final LecturerGroupAssignmentRepository lecturerAssignmentRepository;
    private final AssessmentCommentRepository commentRepository;
    private final StudentResultOverrideRepository overrideRepository;
    private final FactorWeightageRepository factorWeightageRepository;

    public CalculateService(
            MarkRepository markRepository,
            LecturerGroupAssignmentRepository lecturerAssignmentRepository,
            AssessmentCommentRepository commentRepository,
            StudentResultOverrideRepository overrideRepository,
            FactorWeightageRepository factorWeightageRepository) {
        this.markRepository = markRepository;
        this.lecturerAssignmentRepository = lecturerAssignmentRepository;
        this.commentRepository = commentRepository;
        this.overrideRepository = overrideRepository;
        this.factorWeightageRepository = factorWeightageRepository;
    }

    public AssessmentDataDto calculateAssessmentData(Assessment assessment, List<Student> students) {
        AssessmentDataDto dto = new AssessmentDataDto();
        dto.setAssessment(assessment);
        
        List<StudentAssessmentDataDto> studentDataList = new ArrayList<>();
        students.sort((s1, s2) -> s1.getUsername().compareToIgnoreCase(s2.getUsername()));
        
        for (Student student : students) {
            StudentAssessmentDataDto studentData = calculateStudentAssessmentData(student, assessment);
            studentDataList.add(studentData);
        }
        
        dto.setStudentDataList(studentDataList);
        return dto;
    }

    public Set<Integer> getCLOsWithGroupRubrics(Assessment assessment) {
        Set<Integer> clos = new HashSet<>();
        if (assessment.getRubrics() != null) {
            for (Rubric rubric : assessment.getRubrics()) {
                if ("Group Assessment".equalsIgnoreCase(rubric.getAssessmentTypes()) && rubric.getClo() != null) {
                    clos.add(rubric.getClo());
                }
            }
        }
        return clos;
    }

    public StudentAssessmentDataDto calculateStudentAssessmentData(Student student, Assessment assessment) {
        StudentAssessmentDataDto dto = new StudentAssessmentDataDto();
        dto.setStudent(student);
        dto.setAssessment(assessment);
        
        List<Mark> marks = markRepository.findByEvaluatedStudentAndAssessment(student, assessment);
        
        Double factor = 0.0;
        
        Optional<StudentResultOverride> overrideOpt = overrideRepository.findByStudent(student);
        if (overrideOpt.isPresent() && overrideOpt.get().getOverriddenFactor() != null) {
            factor = overrideOpt.get().getOverriddenFactor();
        } else {
            factor = calculatePeerAssessmentFactorForStudent(student);
        }
        
        dto.setFactor(factor);
        
        Map<Long, RubricCalculationDto> rubricCalculations = new HashMap<>();
        
        if (assessment.getRubrics() != null) {
            for (Rubric rubric : assessment.getRubrics()) {
                RubricCalculationDto rubricCalc = calculateRubricData(student, assessment, rubric, marks);
                
                String assessmentType = rubricCalc.getAssessmentType();
                Double evaluatedMark = rubricCalc.getEvaluatedRubricMark();
                Double maxRubricMark = rubric.getMarks() != null ? rubric.getMarks().doubleValue() : 0.0;

                if (assessmentType != null && "Group Assessment".equalsIgnoreCase(assessmentType.trim()) && factor > 0) {
                    double rawWeightedMark = factor * evaluatedMark;
                    rubricCalc.setWeightedRubricMark(round(Math.min(rawWeightedMark, maxRubricMark)));
                } else {
                    rubricCalc.setWeightedRubricMark(round(Math.min(evaluatedMark, maxRubricMark)));
                }
                
                rubricCalculations.put(rubric.getId(), rubricCalc);
            }
        }
        
        dto.setRubricCalculations(rubricCalculations);
        dto.setCloSums(calculateCloSums(rubricCalculations));
        dto.setCloWeightedSums(calculateCloWeightedSums(rubricCalculations));
        dto.setCloWeightedSumsGroupOnly(calculateCloWeightedSumsGroupOnly(rubricCalculations));
        dto.setCloSumsIndividualOnly(calculateCloSumsIndividualOnly(rubricCalculations));
        
        Double totalUnweighted = round(calculateTotalMarks(dto.getCloSums()));
        dto.setTotalUnweightedMarks(totalUnweighted);

        Double totalWeighted = round(calculateTotalMarks(dto.getCloWeightedSums()));
        dto.setTotalWeightedMarks(totalWeighted);
        dto.setTotalMarks(totalWeighted);
        
        dto.setGroupComments(getCommentsForStudent(student, assessment, "Group Assessment"));
        dto.setIndividualComments(getCommentsForStudent(student, assessment, "Individual Assessment"));
        
        return dto;
    }

    public Double calculateGrandTotal(Student student, List<Assessment> assessments) {
        Optional<StudentResultOverride> overrideOpt = overrideRepository.findByStudent(student);
        if (overrideOpt.isPresent() && overrideOpt.get().getOverriddenGrandTotal() != null) {
            return overrideOpt.get().getOverriddenGrandTotal();
        }

        double grandTotal = 0.0;
        if (assessments != null) {
            for (Assessment assessment : assessments) {
                StudentAssessmentDataDto studentData = calculateStudentAssessmentData(student, assessment);
                grandTotal += studentData.getTotalMarks(); 
            }
        }
        return round(grandTotal);
    }

    public com.capstone.adproject.dto.FactorDetailsDto getFactorDetailsForStudent(Student student) {
        com.capstone.adproject.dto.FactorDetailsDto dto = new com.capstone.adproject.dto.FactorDetailsDto();
        dto.setStudentId(student.getId());
        dto.setStudentName(student.getUsername());
        dto.setStudentEmail(student.getEmail());
        
        List<FactorWeightage> weightages = factorWeightageRepository.findByCourse(student.getCourse());
        List<com.capstone.adproject.dto.AssessmentFactorBreakdown> breakdowns = new ArrayList<>();
        
        double finalFactor = 0.0;
        
        if (!weightages.isEmpty()) {
            for (FactorWeightage fw : weightages) {
                com.capstone.adproject.dto.AssessmentFactorBreakdown breakdown = calculateBreakdownFromPeerAssessment(student, fw.getAssessment(), fw.getFactorContributionType());
                breakdown.setWeightage(fw.getWeightage());
                finalFactor += breakdown.getCalculatedFactor() * (fw.getWeightage() / 100.0);
                breakdowns.add(breakdown);
            }
            dto.setFinalCalculatedFactor(Math.round(finalFactor * 1000.0) / 1000.0);
        } else {
            List<Mark> allMarks = markRepository.findByEvaluatedStudent(student);
            List<Mark> peerMarks = allMarks.stream()
                .filter(m -> m.getEvaluatorStudent() != null)
                .filter(m -> m.getIsSupervisorMark() == null || !m.getIsSupervisorMark())
                .filter(m -> m.getLecturer() == null)
                .collect(Collectors.toList());
            
            if (!peerMarks.isEmpty()) {
                Assessment peerAssessment = peerMarks.get(0).getAssessment();
                com.capstone.adproject.dto.AssessmentFactorBreakdown breakdown = calculateBreakdownFromPeerAssessment(student, peerAssessment, "BOTH");
                breakdown.setWeightage(100.0);
                breakdowns.add(breakdown);
                dto.setFinalCalculatedFactor(breakdown.getCalculatedFactor());
            } else {
                dto.setFinalCalculatedFactor(0.0);
            }
        }
        
        dto.setBreakdowns(breakdowns);
        
        Optional<StudentResultOverride> overrideOpt = overrideRepository.findByStudent(student);
        if (overrideOpt.isPresent()) {
            dto.setIsOverridden(true);
            dto.setCurrentOverriddenFactor(overrideOpt.get().getOverriddenFactor());
        } else {
            dto.setIsOverridden(false);
            dto.setCurrentOverriddenFactor(dto.getFinalCalculatedFactor());
        }
        
        return dto;
    }
    
    private com.capstone.adproject.dto.AssessmentFactorBreakdown calculateBreakdownFromPeerAssessment(Student student, Assessment peerAssessment, String factorContributionType) {
        com.capstone.adproject.dto.AssessmentFactorBreakdown breakdown = new com.capstone.adproject.dto.AssessmentFactorBreakdown();
        breakdown.setAssessmentId(peerAssessment.getId());
        breakdown.setAssessmentTitle(peerAssessment.getTitle());
        breakdown.setRawRatings(new ArrayList<>());
        breakdown.setAverageRating(0.0);
        breakdown.setGroupAverage(0.0);
        breakdown.setCalculatedFactor(0.0);
        
        Group group = student.getGroup();
        if (group == null || group.getStudents() == null || group.getStudents().isEmpty()) return breakdown;
        
        List<Student> groupMembers = new ArrayList<>(group.getStudents());
        Map<Long, Double> individualAverages = new HashMap<>();
        
        for (Student member : groupMembers) {
            List<Mark> receivedMarks = markRepository.findByEvaluatedStudentAndAssessment(member, peerAssessment);
            receivedMarks = receivedMarks.stream()
                .filter(m -> m.getEvaluatorStudent() != null)
                .filter(m -> m.getIsSupervisorMark() == null || !m.getIsSupervisorMark())
                .filter(m -> m.getLecturer() == null)
                .filter(m -> {
                    String type = factorContributionType;
                    if ("INDIVIDUAL".equalsIgnoreCase(type)) {
                        return m.getAssessmentType() != null && m.getAssessmentType().toLowerCase().contains("individual");
                    }
                    if ("GROUP".equalsIgnoreCase(type)) {
                        return m.getAssessmentType() != null && m.getAssessmentType().toLowerCase().contains("group");
                    }
                    return true;
                })
                .collect(Collectors.toList());
            
            if (!receivedMarks.isEmpty()) {
                Map<Long, List<Mark>> marksByEvaluator = receivedMarks.stream()
                    .collect(Collectors.groupingBy(m -> m.getEvaluatorStudent().getId()));
                
                List<Double> allRatings = new ArrayList<>();
                List<List<Double>> ratingsPerTeammate = new ArrayList<>();

                for (Map.Entry<Long, List<Mark>> entry : marksByEvaluator.entrySet()) {
                    List<Double> tRatings = entry.getValue().stream()
                        .filter(m -> m.getRating() != null && m.getRating().getMarks() != null)
                        .map(m -> m.getRating().getMarks().doubleValue())
                        .collect(Collectors.toList());
                    if (!tRatings.isEmpty()) {
                        ratingsPerTeammate.add(tRatings);
                        allRatings.addAll(tRatings);
                    }
                }

                if (!allRatings.isEmpty()) {
                    double sum = allRatings.stream().mapToDouble(Double::doubleValue).sum();
                    individualAverages.put(member.getId(), sum / allRatings.size());
                    
                    if (member.getId().equals(student.getId())) {
                        breakdown.setRawRatings(ratingsPerTeammate);
                        breakdown.setAverageRating(sum / allRatings.size());
                    }
                }
            } else {
                individualAverages.put(member.getId(), 0.0);
            }
        }
        
        if (individualAverages.isEmpty()) return breakdown;
        
        double groupAverage = individualAverages.values().stream().mapToDouble(Double::doubleValue).average().orElse(0.0);
        if (groupAverage == 0) return breakdown;
        breakdown.setGroupAverage(groupAverage);
        
        Double individualAverage = individualAverages.get(student.getId());
        if (individualAverage == null) return breakdown;
        
        double factor = individualAverage / groupAverage;
        double cappedFactor = Math.min(factor, 1.05);
        breakdown.setCalculatedFactor(Math.round(cappedFactor * 1000.0) / 1000.0);
        
        return breakdown;
    }

    public Double calculatePeerAssessmentFactorForStudent(Student student) {
        List<FactorWeightage> weightages = factorWeightageRepository.findByCourse(student.getCourse());
        
        if (!weightages.isEmpty()) {
            double finalFactor = 0.0;
            for (FactorWeightage fw : weightages) {
                double factor = calculateStudentFactorFromPeerAssessment(student, fw.getAssessment(), fw.getFactorContributionType());
                finalFactor += factor * (fw.getWeightage() / 100.0);
            }
            return Math.round(finalFactor * 1000.0) / 1000.0;
        }

        List<Mark> allMarks = markRepository.findByEvaluatedStudent(student);
        List<Mark> peerMarks = allMarks.stream()
            .filter(m -> m.getEvaluatorStudent() != null)
            .filter(m -> m.getIsSupervisorMark() == null || !m.getIsSupervisorMark())
            .filter(m -> m.getLecturer() == null)
            .collect(Collectors.toList());
        
        if (peerMarks.isEmpty()) return 0.0;
        
        Assessment peerAssessment = peerMarks.get(0).getAssessment();
        return calculateStudentFactorFromPeerAssessment(student, peerAssessment, "BOTH");
    }

    private Double calculateStudentFactorFromPeerAssessment(Student student, Assessment peerAssessment, String factorContributionType) {
        Group group = student.getGroup();
        if (group == null || group.getStudents() == null || group.getStudents().isEmpty()) return 0.0;
        
        List<Student> groupMembers = new ArrayList<>(group.getStudents());
        Map<Long, Double> individualAverages = new HashMap<>();
        
        for (Student member : groupMembers) {
            List<Mark> receivedMarks = markRepository.findByEvaluatedStudentAndAssessment(member, peerAssessment);
            receivedMarks = receivedMarks.stream()
                .filter(m -> m.getEvaluatorStudent() != null)
                .filter(m -> m.getIsSupervisorMark() == null || !m.getIsSupervisorMark())
                .filter(m -> m.getLecturer() == null)
                .filter(m -> {
                    if ("INDIVIDUAL".equalsIgnoreCase(factorContributionType)) {
                        return m.getAssessmentType() != null && m.getAssessmentType().toLowerCase().contains("individual");
                    }
                    if ("GROUP".equalsIgnoreCase(factorContributionType)) {
                        return m.getAssessmentType() != null && m.getAssessmentType().toLowerCase().contains("group");
                    }
                    return true;
                })
                .collect(Collectors.toList());
            
            if (!receivedMarks.isEmpty()) {
                double sum = receivedMarks.stream()
                    .filter(m -> m.getRating() != null && m.getRating().getMarks() != null)
                    .mapToDouble(m -> m.getRating().getMarks().doubleValue()).sum();
                individualAverages.put(member.getId(), sum / receivedMarks.size());
            }
        }
        
        if (individualAverages.isEmpty()) return 0.0;
        
        double groupAverage = individualAverages.values().stream().mapToDouble(Double::doubleValue).average().orElse(0.0);
        if (groupAverage == 0) return 0.0;
        
        Double individualAverage = individualAverages.get(student.getId());
        if (individualAverage == null) return 0.0;
        
        double factor = individualAverage / groupAverage;
        double cappedFactor = Math.min(factor, 1.05);
        return Math.round(cappedFactor * 1000.0) / 1000.0;
    }

    private RubricCalculationDto calculateRubricData(Student student, Assessment assessment, Rubric rubric, List<Mark> allMarks) {
        RubricCalculationDto dto = new RubricCalculationDto();
        dto.setRubric(rubric);
        
        List<Mark> rubricMarks = allMarks.stream()
            .filter(m -> m.getRubric() != null && m.getRubric().getId().equals(rubric.getId()))
            .collect(Collectors.toList());
            
        if (rubric.hasSubRubrics()) {
            dto.setRubricFactor(calculateSubRubricFactor(student, assessment, rubric, rubricMarks));
        } else {
            dto.setRubricFactor(calculateDirectRatingFactor(student, assessment, rubric, rubricMarks));
        }
        
        Double configuredMark = rubric.getMarks() != null ? rubric.getMarks().doubleValue() : 0.0;
        Double rawMark = dto.getRubricFactor() * configuredMark;
        dto.setEvaluatedRubricMark(round(rawMark));
        
        dto.setClo(rubric.getClo() != null ? rubric.getClo() : 0);
        dto.setAssessmentType(rubric.getAssessmentTypes());
        dto.setEvaluatorDetails(getEvaluatorDetails(student, assessment, rubric, rubricMarks));
        
        return dto;
    }

    private Double calculateSubRubricFactor(Student student, Assessment assessment, Rubric rubric, List<Mark> marks) {
        if (marks.isEmpty()) return 0.0;
        
        Map<Long, List<Mark>> marksByLecturer = new HashMap<>();
        for (Mark mark : marks) {
            if (mark.getSubRubric() == null) continue;
            Long lecturerId = null;
            if (mark.getLecturer() != null) lecturerId = mark.getLecturer().getId();
            else if (mark.getEvaluatorStudent() != null) lecturerId = mark.getEvaluatorStudent().getId();
            
            if (lecturerId != null) {
                marksByLecturer.computeIfAbsent(lecturerId, k -> new ArrayList<>()).add(mark);
            }
        }
        
        if (marksByLecturer.isEmpty()) return 0.0;
        
        List<Double> lecturerAverages = new ArrayList<>();
        for (List<Mark> lecturerMarks : marksByLecturer.values()) {
            double sum = lecturerMarks.stream()
                .mapToDouble(m -> m.getRating() != null && m.getRating().getMarks() != null ? m.getRating().getMarks().doubleValue() : 0.0)
                .sum();
            lecturerAverages.add(sum / lecturerMarks.size());
        }
        
        double muLn = lecturerAverages.stream().mapToDouble(Double::doubleValue).average().orElse(0.0);
        
        double rmax = rubric.getSubRubrics().stream()
            .flatMap(sr -> sr.getRatings().stream())
            .mapToDouble(r -> r.getMarks() != null ? r.getMarks().doubleValue() : 0.0)
            .max().orElse(1.0);
            
        return rmax > 0 ? muLn / rmax : 0.0;
    }

    private Double calculateDirectRatingFactor(Student student, Assessment assessment, Rubric rubric, List<Mark> marks) {
        if (marks.isEmpty()) return 0.0;
        
        double drmax = rubric.getRatings().stream()
            .mapToDouble(r -> r.getMarks() != null ? r.getMarks().doubleValue() : 0.0)
            .max().orElse(1.0);
            
        if (drmax == 0) return 0.0;
        
        List<Double> rubricFactors = marks.stream()
            .filter(m -> m.getRating() != null && m.getRating().getMarks() != null)
            .map(m -> m.getRating().getMarks().doubleValue() / drmax)
            .collect(Collectors.toList());
            
        return rubricFactors.stream().mapToDouble(Double::doubleValue).average().orElse(0.0);
    }

    private Map<Integer, Double> calculateCloSums(Map<Long, RubricCalculationDto> rubricCalculations) {
        Map<Integer, Double> cloSums = new HashMap<>();
        for (RubricCalculationDto rubricCalc : rubricCalculations.values()) {
            Integer clo = rubricCalc.getClo();
            Double currentSum = cloSums.getOrDefault(clo, 0.0);
            cloSums.put(clo, currentSum + rubricCalc.getEvaluatedRubricMark());
        }
        for (Map.Entry<Integer, Double> entry : cloSums.entrySet()) {
            entry.setValue(round(entry.getValue()));
        }
        return cloSums;
    }

    private Map<Integer, Double> calculateCloWeightedSums(Map<Long, RubricCalculationDto> rubricCalculations) {
        Map<Integer, Double> cloWeightedSums = new HashMap<>();
        for (RubricCalculationDto rubricCalc : rubricCalculations.values()) {
            Integer clo = rubricCalc.getClo();
            Double currentSum = cloWeightedSums.getOrDefault(clo, 0.0);
            cloWeightedSums.put(clo, currentSum + rubricCalc.getWeightedRubricMark());
        }
        for (Map.Entry<Integer, Double> entry : cloWeightedSums.entrySet()) {
            entry.setValue(round(entry.getValue()));
        }
        return cloWeightedSums;
    }

    private Map<Integer, Double> calculateCloWeightedSumsGroupOnly(Map<Long, RubricCalculationDto> rubricCalculations) {
        Map<Integer, Double> cloWeightedSums = new HashMap<>();
        for (RubricCalculationDto rubricCalc : rubricCalculations.values()) {
            String assessmentType = rubricCalc.getAssessmentType();
            if (assessmentType != null && "Group Assessment".equalsIgnoreCase(assessmentType.trim())) {
                Integer clo = rubricCalc.getClo();
                Double currentSum = cloWeightedSums.getOrDefault(clo, 0.0);
                cloWeightedSums.put(clo, currentSum + rubricCalc.getWeightedRubricMark());
            }
        }
        for (Map.Entry<Integer, Double> entry : cloWeightedSums.entrySet()) {
            entry.setValue(round(entry.getValue()));
        }
        return cloWeightedSums;
    }

    private Map<Integer, Double> calculateCloSumsIndividualOnly(Map<Long, RubricCalculationDto> rubricCalculations) {
        Map<Integer, Double> cloSums = new HashMap<>();
        for (RubricCalculationDto rubricCalc : rubricCalculations.values()) {
            String assessmentType = rubricCalc.getAssessmentType();
            if (assessmentType != null && "Individual Assessment".equalsIgnoreCase(assessmentType.trim())) {
                Integer clo = rubricCalc.getClo();
                Double currentSum = cloSums.getOrDefault(clo, 0.0);
                cloSums.put(clo, currentSum + rubricCalc.getEvaluatedRubricMark());
            }
        }
        for (Map.Entry<Integer, Double> entry : cloSums.entrySet()) {
            entry.setValue(round(entry.getValue()));
        }
        return cloSums;
    }

    private Double calculateTotalMarks(Map<Integer, Double> cloSums) {
        double total = cloSums.values().stream().mapToDouble(Double::doubleValue).sum();
        return round(total);
    }
    
    private Double round(Double value) {
        if (value == null) return 0.0;
        BigDecimal bd = BigDecimal.valueOf(value);
        bd = bd.setScale(2, RoundingMode.HALF_UP); 
        return bd.doubleValue();
    }

    private Double round3(Double value) {
        if (value == null) return 0.0;
        BigDecimal bd = BigDecimal.valueOf(value);
        bd = bd.setScale(3, RoundingMode.HALF_UP);
        return bd.doubleValue();
    }

    private List<Map<String, Object>> getEvaluatorDetails(Student student, Assessment assessment, Rubric rubric, List<Mark> marks) {
        List<Map<String, Object>> details = new ArrayList<>();
        Map<String, Map<String, Object>> uniqueEvaluators = new java.util.LinkedHashMap<>();
        
        for (Mark mark : marks) {
            String evaluatorKey = null;
            Map<String, Object> detail = new HashMap<>();
            
            if (mark.getSupervisorId() != null && Boolean.TRUE.equals(mark.getIsSupervisorMark())) {
                // Since we're removing industrial supervisors, treat supervisor marks as lecturer marks
                // The supervisorId should now reference a lecturer ID
                detail.put("evaluatorName", "Lecturer (Supervisor Role)");
                detail.put("evaluatorType", "Lecturer");
                evaluatorKey = "lecturer_supervisor_" + mark.getSupervisorId();
                
            } else if (mark.getLecturer() != null) {
                evaluatorKey = "lecturer_" + mark.getLecturer().getId();
                detail.put("evaluatorName", mark.getLecturer().getUsername());
                detail.put("evaluatorType", "Lecturer");
                
            } else if (mark.getEvaluatorStudent() != null) {
                evaluatorKey = "student_" + mark.getEvaluatorStudent().getId();
                detail.put("evaluatorName", mark.getEvaluatorStudent().getUsername());
                detail.put("evaluatorType", "Student");
                
            } else {
                evaluatorKey = "unknown_" + mark.getId();
                detail.put("evaluatorName", "Unknown Evaluator");
                detail.put("evaluatorType", "Unknown");
            }
            
            if (evaluatorKey != null && !uniqueEvaluators.containsKey(evaluatorKey)) {
                uniqueEvaluators.put(evaluatorKey, detail);
            }
        }
        
        details.addAll(uniqueEvaluators.values());
        return details;
    }

    private List<String> getCommentsForStudent(Student student, Assessment assessment, String assessmentType) {
        return commentRepository.findByEvaluatedStudentAndAssessmentAndRubricAssessmentType(student, assessment, assessmentType)
            .stream()
            .map(c -> c.getDisplayName() + ": " + c.getCommentText())
            .collect(Collectors.toList());
    }
    
    public String calculateGrade(Double totalMarks) {
        if (totalMarks == null) return "N/A";
        if (totalMarks >= 90) return "A+";
        else if (totalMarks >= 80) return "A";
        else if (totalMarks >= 75) return "A-";
        else if (totalMarks >= 70) return "B+";
        else if (totalMarks >= 65) return "B";
        else if (totalMarks >= 60) return "B-";
        else if (totalMarks >= 55) return "C+";
        else if (totalMarks >= 50) return "C";
        else if (totalMarks >= 45) return "C-";
        else if (totalMarks >= 40) return "D+";
        else if (totalMarks >= 35) return "D";
        else if (totalMarks >= 30) return "D-";
        else return "E";
    }

    public Map<String, Map<Long, Double>> getSubRubricRatingsByEvaluator(Student student, Assessment assessment, Rubric rubric) {
        Map<String, Map<Long, Double>> result = new java.util.LinkedHashMap<>();
        
        if (!rubric.hasSubRubrics()) {
            return result;
        }
        
        List<Mark> marks = markRepository.findByEvaluatedStudentAndAssessment(student, assessment);
        List<Mark> rubricMarks = marks.stream()
            .filter(m -> m.getRubric() != null && m.getRubric().getId().equals(rubric.getId()))
            .collect(Collectors.toList());
        
        Map<String, List<Mark>> marksByEvaluator = new HashMap<>();
        
        for (Mark mark : rubricMarks) {
            if (mark.getSubRubric() == null) continue;
            
            String evaluatorKey = getEvaluatorKey(mark);
            marksByEvaluator.computeIfAbsent(evaluatorKey, k -> new ArrayList<>()).add(mark);
        }
        
        for (Map.Entry<String, List<Mark>> entry : marksByEvaluator.entrySet()) {
            Map<Long, Double> subRubricRatings = new HashMap<>();
            
            for (Mark mark : entry.getValue()) {
                if (mark.getSubRubric() != null && mark.getRating() != null && mark.getRating().getMarks() != null) {
                    subRubricRatings.put(
                        mark.getSubRubric().getId(),
                        mark.getRating().getMarks().doubleValue()
                    );
                }
            }
            
            result.put(entry.getKey(), subRubricRatings);
        }
        
        return result;
    }

    public Map<String, Double> calculateMuSrmln(Student student, Assessment assessment, Rubric rubric) {
        Map<String, Double> result = new java.util.LinkedHashMap<>();
        
        if (!rubric.hasSubRubrics()) {
            return result;
        }
        
        List<Mark> marks = markRepository.findByEvaluatedStudentAndAssessment(student, assessment);
        List<Mark> rubricMarks = marks.stream()
            .filter(m -> m.getRubric() != null && m.getRubric().getId().equals(rubric.getId()))
            .filter(m -> m.getSubRubric() != null)
            .collect(Collectors.toList());
        
        Map<String, List<Mark>> marksByEvaluator = new HashMap<>();
        
        for (Mark mark : rubricMarks) {
            String evaluatorKey = getEvaluatorKey(mark);
            marksByEvaluator.computeIfAbsent(evaluatorKey, k -> new ArrayList<>()).add(mark);
        }
        
        for (Map.Entry<String, List<Mark>> entry : marksByEvaluator.entrySet()) {
            double sum = entry.getValue().stream()
                .filter(m -> m.getRating() != null && m.getRating().getMarks() != null)
                .mapToDouble(m -> m.getRating().getMarks().doubleValue())
                .sum();
            
            double average = !entry.getValue().isEmpty() ? sum / entry.getValue().size() : 0.0;
            result.put(entry.getKey(), round3(average));
        }
        
        return result;
    }

    public Double calculateMuLn(Student student, Assessment assessment, Rubric rubric) {
        if (!rubric.hasSubRubrics()) {
            return 0.0;
        }
        
        Map<String, Double> muSrmlnByEvaluator = calculateMuSrmln(student, assessment, rubric);
        
        if (muSrmlnByEvaluator.isEmpty()) {
            return 0.0;
        }
        
        double sum = muSrmlnByEvaluator.values().stream()
            .mapToDouble(Double::doubleValue)
            .sum();
        
        return round3(sum / muSrmlnByEvaluator.size());
    }

    public Map<String, List<String>> getEvaluatorCommentsForRubric(Student student, Assessment assessment, Rubric rubric) {
        Map<String, List<String>> result = new java.util.LinkedHashMap<>();
        
        List<AssessmentComment> rubricComments = 
            commentRepository.findByEvaluatedStudentAndAssessmentAndRubricId(student, assessment, rubric.getId());
        
        if (rubricComments.isEmpty()) {
            rubricComments = commentRepository.findByRubricId(rubric.getId());
            final Assessment finalAssessment = assessment;
            final Student finalStudent = student;
            rubricComments = rubricComments.stream()
                .filter(c -> c.getEvaluatedStudent() != null && 
                            c.getEvaluatedStudent().getId().equals(finalStudent.getId()))
                .filter(c -> c.getAssessment() != null &&
                            c.getAssessment().getId().equals(finalAssessment.getId()))
                .collect(Collectors.toList());
        }
        
        for (AssessmentComment comment : rubricComments) {
            String evaluatorName = comment.getDisplayName();
            String commentText = comment.getCommentText();
            
            if (evaluatorName != null && commentText != null && !commentText.trim().isEmpty()) {
                result.computeIfAbsent(evaluatorName, k -> new ArrayList<>()).add(commentText);
            }
        }
        
        return result;
    }

    public Map<String, Double> getDirectRatingsByEvaluator(Student student, Assessment assessment, Rubric rubric) {
        Map<String, Double> result = new java.util.LinkedHashMap<>();
        
        if (rubric.hasSubRubrics()) {
            return result;
        }
        
        List<Mark> marks = markRepository.findByEvaluatedStudentAndAssessment(student, assessment);
        List<Mark> rubricMarks = marks.stream()
            .filter(m -> m.getRubric() != null && m.getRubric().getId().equals(rubric.getId()))
            .filter(m -> m.getRating() != null && m.getRating().getMarks() != null)
            .collect(Collectors.toList());
        
        for (Mark mark : rubricMarks) {
            String evaluatorKey = getEvaluatorKey(mark);
            result.put(evaluatorKey, mark.getRating().getMarks().doubleValue());
        }
        
        return result;
    }

    public Map<String, Double> calculateRfByEvaluator(Student student, Assessment assessment, Rubric rubric) {
        Map<String, Double> result = new java.util.LinkedHashMap<>();
        
        if (rubric.hasSubRubrics()) {
            return result;
        }
        
        double drmax = getDrmax(rubric);
        if (drmax == 0) {
            return result;
        }
        
        Map<String, Double> directRatings = getDirectRatingsByEvaluator(student, assessment, rubric);
        
        for (Map.Entry<String, Double> entry : directRatings.entrySet()) {
            double rf = entry.getValue() / drmax;
            result.put(entry.getKey(), round3(rf));
        }
        
        return result;
    }

    public Double calculateMuRf(Student student, Assessment assessment, Rubric rubric) {
        if (rubric.hasSubRubrics()) {
            return 0.0;
        }
        
        Map<String, Double> rfByEvaluator = calculateRfByEvaluator(student, assessment, rubric);
        
        if (rfByEvaluator.isEmpty()) {
            return 0.0;
        }
        
        double sum = rfByEvaluator.values().stream()
            .mapToDouble(Double::doubleValue)
            .sum();
        
        return round3(sum / rfByEvaluator.size());
    }

    public Double getDrmax(Rubric rubric) {
        if (rubric == null || rubric.getRatings() == null || rubric.getRatings().isEmpty()) {
            return 1.0;
        }
        
        return rubric.getRatings().stream()
            .filter(r -> r.getMarks() != null)
            .mapToDouble(r -> r.getMarks().doubleValue())
            .max()
            .orElse(1.0);
    }

    public Map<String, List<String>> getGroupAssessmentComments(Student student, Assessment assessment) {
        Map<String, List<String>> result = new java.util.LinkedHashMap<>();
        
        List<AssessmentComment> groupComments = commentRepository
            .findByEvaluatedStudentAndAssessmentAndRubricAssessmentType(student, assessment, "Group Assessment");
        
        groupComments = groupComments.stream()
            .filter(c -> c.getRubricId() == null)
            .collect(Collectors.toList());
        
        for (AssessmentComment comment : groupComments) {
            String evaluatorName = comment.getDisplayName();
            String commentText = comment.getCommentText();
            
            if (evaluatorName != null && commentText != null && !commentText.trim().isEmpty()) {
                result.computeIfAbsent(evaluatorName, k -> new ArrayList<>()).add(commentText);
            }
        }
        
        return result;
    }

    public Map<String, List<String>> getIndividualAssessmentComments(Student student, Assessment assessment) {
        Map<String, List<String>> result = new java.util.LinkedHashMap<>();
        
        List<AssessmentComment> individualComments = commentRepository
            .findByEvaluatedStudentAndAssessmentAndRubricAssessmentType(student, assessment, "Individual Assessment");
        
        individualComments = individualComments.stream()
            .filter(c -> c.getRubricId() == null)
            .collect(Collectors.toList());
        
        for (AssessmentComment comment : individualComments) {
            String evaluatorName = comment.getDisplayName();
            String commentText = comment.getCommentText();
            
            if (evaluatorName != null && commentText != null && !commentText.trim().isEmpty()) {
                result.computeIfAbsent(evaluatorName, k -> new ArrayList<>()).add(commentText);
            }
        }
        
        return result;
    }

    private String getEvaluatorKey(Mark mark) {
        if (mark.getSupervisorId() != null && Boolean.TRUE.equals(mark.getIsSupervisorMark())) {
            // Since we're removing industrial supervisors, treat supervisor marks as lecturer marks
            // The supervisorId should now reference a lecturer ID
            return "Lecturer (Supervisor Role)";
        } else if (mark.getLecturer() != null) {
            return mark.getLecturer().getUsername();
        } else if (mark.getEvaluatorStudent() != null) {
            return mark.getEvaluatorStudent().getUsername();
        } else {
            return "Unknown Evaluator";
        }
    }
}