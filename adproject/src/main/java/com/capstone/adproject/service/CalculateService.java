package com.capstone.adproject.service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import com.capstone.adproject.dto.AssessmentDataDto;
import com.capstone.adproject.dto.RubricCalculationDto;
import com.capstone.adproject.dto.StudentAssessmentDataDto;
import com.capstone.adproject.model.Assessment;
import com.capstone.adproject.model.AssessmentComment;
import com.capstone.adproject.model.Course;
import com.capstone.adproject.model.FactorWeightage;
import com.capstone.adproject.model.Group;
import com.capstone.adproject.model.Mark;
import com.capstone.adproject.model.Rubric;
import com.capstone.adproject.model.Student;
import com.capstone.adproject.model.StudentCourseAssignment;
import com.capstone.adproject.model.StudentResultOverride;
import com.capstone.adproject.repositories.AssessmentCommentRepository;
import com.capstone.adproject.repositories.CourseRepository;
import com.capstone.adproject.repositories.FactorWeightageRepository;
import com.capstone.adproject.repositories.LecturerGroupAssignmentRepository;
import com.capstone.adproject.repositories.MarkRepository;
import com.capstone.adproject.repositories.StudentCourseAssignmentRepository;
import com.capstone.adproject.repositories.StudentResultOverrideRepository;

@Service
public class CalculateService {

    private static final Logger logger = LoggerFactory.getLogger(CalculateService.class);

    private final MarkRepository markRepository;
    private final LecturerGroupAssignmentRepository lecturerAssignmentRepository;
    private final AssessmentCommentRepository commentRepository;
    private final StudentResultOverrideRepository overrideRepository;
    private final FactorWeightageRepository factorWeightageRepository;

    @Autowired(required = false)
    private StudentCourseAssignmentRepository studentCourseAssignmentRepository;

    @Autowired(required = false)
    private CourseRepository courseRepository;

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
        students.sort(Comparator.comparing(
            s -> {
                if (s == null) return "";
                if (s.getUsername() != null && !s.getUsername().isBlank()) {
                    return s.getUsername();
                }
                if (s.getEmail() != null && !s.getEmail().isBlank()) {
                    return s.getEmail();
                }
                return "";
            },
            String.CASE_INSENSITIVE_ORDER
        ));
        Map<Long, List<Mark>> marksByStudentId = groupMarksByEvaluatedStudent(finishedOnly(markRepository.findByAssessment(assessment)));
        
        for (Student student : students) {
            Double resolvedFactor = resolveStudentFactor(student);
            StudentAssessmentDataDto studentData = calculateStudentAssessmentData(
                student,
                assessment,
                resolvedFactor,
                marksByStudentId.get(student.getId())
            );
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

    public Map<Long, List<Mark>> getMarksByEvaluatedStudentForAssessment(Assessment assessment) {
        return groupMarksByEvaluatedStudent(finishedOnly(markRepository.findByAssessment(assessment)));
    }

    public StudentAssessmentDataDto calculateStudentAssessmentData(Student student, Assessment assessment) {
        return calculateStudentAssessmentData(student, assessment, null);
    }

    public StudentAssessmentDataDto calculateStudentAssessmentData(Student student, Assessment assessment, Double precomputedFactor) {
        return calculateStudentAssessmentData(student, assessment, precomputedFactor, null);
    }

    public StudentAssessmentDataDto calculateStudentAssessmentData(
            Student student,
            Assessment assessment,
            Double precomputedFactor,
            List<Mark> preloadedMarks) {
        StudentAssessmentDataDto dto = new StudentAssessmentDataDto();
        dto.setStudent(student);
        dto.setAssessment(assessment);
        
        List<Mark> marks = finishedOnly(preloadedMarks != null ? preloadedMarks : markRepository.findByEvaluatedStudentAndAssessment(student, assessment));
        
        Double factor = precomputedFactor != null ? precomputedFactor : resolveStudentFactor(student);
        
        dto.setFactor(factor);
        dto.setEvaluated(!marks.isEmpty());
        
        Map<Long, RubricCalculationDto> rubricCalculations = new HashMap<>();
        
        if (assessment.getRubrics() != null) {
            for (Rubric rubric : assessment.getRubrics()) {
                RubricCalculationDto rubricCalc = calculateRubricData(student, assessment, rubric, marks);
                
                String assessmentType = rubricCalc.getAssessmentType();
                Double evaluatedMark = rubricCalc.getEvaluatedRubricMark();
                Double maxRubricMark = rubric.getMarks() != null ? rubric.getMarks().doubleValue() : 0.0;

                if (assessmentType != null && "Group Assessment".equalsIgnoreCase(assessmentType.trim()) && factor > 0) {
                    double cappedFactor = Math.min(factor, 1.05);
                    double rawWeightedMark = cappedFactor * evaluatedMark;
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
        dto.setCloWeightedSumsIndividualOnly(calculateCloWeightedSumsIndividualOnly(rubricCalculations));
        dto.setCloSumsIndividualOnly(calculateCloSumsIndividualOnly(rubricCalculations));
        dto.setCloSumsGroupOnly(calculateCloSumsGroupOnly(rubricCalculations));
        
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
        Long inferredCourseId = inferCourseId(student, assessments);
        return calculateGrandTotal(student, assessments, inferredCourseId);
    }

    public Double calculateGrandTotal(Student student, List<Assessment> assessments, Long courseId) {
        Optional<StudentResultOverride> overrideOpt = resolveCourseOverride(student, courseId);
        if (overrideOpt.isPresent() && overrideOpt.get().getOverriddenGrandTotal() != null) {
            return overrideOpt.get().getOverriddenGrandTotal();
        }

        Double factor = resolveStudentFactor(student, courseId);
        double grandTotal = 0.0;
        if (assessments != null) {
            for (Assessment assessment : assessments) {
                if (courseId != null && assessment.getCourse() != null && assessment.getCourse().getId() != null
                        && !courseId.equals(assessment.getCourse().getId())) {
                    continue;
                }
                StudentAssessmentDataDto studentData = calculateStudentAssessmentData(student, assessment, factor);
                grandTotal += studentData.getTotalMarks(); 
            }
        }
        return round(grandTotal);
    }

    public com.capstone.adproject.dto.FactorDetailsDto getFactorDetailsForStudent(Student student) {
        Long defaultCourseId = student != null && student.getCourse() != null ? student.getCourse().getId() : null;
        return getFactorDetailsForStudent(student, defaultCourseId);
    }

    public com.capstone.adproject.dto.FactorDetailsDto getFactorDetailsForStudent(Student student, Long courseId) {
        com.capstone.adproject.dto.FactorDetailsDto dto = new com.capstone.adproject.dto.FactorDetailsDto();
        dto.setStudentId(student.getId());
        dto.setStudentName(student.getUsername());
        dto.setStudentEmail(student.getEmail());
        
        List<FactorWeightage> weightages = resolveCourseWeightages(student, courseId);
        List<com.capstone.adproject.dto.AssessmentFactorBreakdown> breakdowns = new ArrayList<>();
        
        double finalFactor = 0.0;
        
        if (!weightages.isEmpty()) {
            for (FactorWeightage fw : weightages) {
                com.capstone.adproject.dto.AssessmentFactorBreakdown breakdown = calculateBreakdownFromPeerAssessment(student, fw.getAssessment(), fw.getFactorContributionType());
                breakdown.setWeightage(fw.getWeightage());
                finalFactor += breakdown.getCalculatedFactor() * (fw.getWeightage() / 100.0);
                breakdowns.add(breakdown);
            }
            double cappedTotal = Math.min(finalFactor, 1.05);
            dto.setFinalCalculatedFactor(Math.round(cappedTotal * 1000.0) / 1000.0);
        } else {
            List<Mark> allMarks = finishedOnly(markRepository.findByEvaluatedStudent(student));
            List<Mark> peerMarks = allMarks.stream()
                .filter(m -> belongsToCourseId(m, courseId))
                .filter(m -> m.getEvaluatorStudent() != null)
                .filter(m -> m.getIsSupervisorMark() == null || !m.getIsSupervisorMark())
                .filter(m -> m.getLecturer() == null)
                .collect(Collectors.toList());
            
            if (!peerMarks.isEmpty()) {
                Assessment peerAssessment = peerMarks.get(0).getAssessment();
                com.capstone.adproject.dto.AssessmentFactorBreakdown breakdown = calculateBreakdownFromPeerAssessment(student, peerAssessment, "BOTH");
                breakdown.setWeightage(100.0);
                breakdowns.add(breakdown);
                double cappedTotal = Math.min(breakdown.getCalculatedFactor(), 1.05);
                dto.setFinalCalculatedFactor(Math.round(cappedTotal * 1000.0) / 1000.0);
            } else {
                dto.setFinalCalculatedFactor(0.0);
            }
        }
        
        dto.setBreakdowns(breakdowns);
        
        Optional<StudentResultOverride> overrideOpt = resolveCourseOverride(student, courseId);
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
        
        Group group = resolveGroupForAssessment(student, peerAssessment);
        List<Student> groupMembers = resolveGroupMembersForCourse(group, peerAssessment != null && peerAssessment.getCourse() != null ? peerAssessment.getCourse().getId() : null);
        if (group == null || groupMembers.isEmpty()) return breakdown;
        Map<Long, Double> individualAverages = new HashMap<>();
        
        for (Student member : groupMembers) {
            List<Mark> receivedMarks = finishedOnly(markRepository.findByEvaluatedStudentAndAssessment(member, peerAssessment));
            receivedMarks = ratingsFromOthers(receivedMarks, member).stream()
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
            }
        }
        
        if (individualAverages.isEmpty()) return breakdown;
        
        double groupAverage = individualAverages.values().stream().mapToDouble(Double::doubleValue).average().orElse(0.0);
        if (groupAverage == 0) return breakdown;
        breakdown.setGroupAverage(groupAverage);
        
        Double individualAverage = individualAverages.get(student.getId());
        if (individualAverage == null) return breakdown;
        
        double factor = individualAverage / groupAverage;
        breakdown.setCalculatedFactor(Math.round(factor * 1000.0) / 1000.0);
        
        return breakdown;
    }

    public Double calculatePeerAssessmentFactorForStudent(Student student) {
        Long defaultCourseId = student != null && student.getCourse() != null ? student.getCourse().getId() : null;
        return calculatePeerAssessmentFactorForStudent(student, defaultCourseId);
    }

    public Double calculatePeerAssessmentFactorForStudent(Student student, Long courseId) {
        List<FactorWeightage> weightages = resolveCourseWeightages(student, courseId);
        
        if (!weightages.isEmpty()) {
            List<Double> factors = new ArrayList<>();
            List<Double> weights = new ArrayList<>();
            for (FactorWeightage fw : weightages) {
                factors.add(calculateStudentFactorFromPeerAssessment(student, fw.getAssessment(), fw.getFactorContributionType()));
                weights.add(fw.getWeightage() / 100.0);
            }
            return Math.round(combineWeightedFactors(factors, weights) * 1000.0) / 1000.0;
        }

        List<Mark> allMarks = finishedOnly(markRepository.findByEvaluatedStudent(student));
        List<Mark> peerMarks = allMarks.stream()
            .filter(m -> belongsToCourseId(m, courseId))
            .filter(m -> m.getEvaluatorStudent() != null)
            .filter(m -> m.getIsSupervisorMark() == null || !m.getIsSupervisorMark())
            .filter(m -> m.getLecturer() == null)
            .collect(Collectors.toList());
        
        if (peerMarks.isEmpty()) return 0.0;
        
        Assessment peerAssessment = peerMarks.get(0).getAssessment();
        return calculateStudentFactorFromPeerAssessment(student, peerAssessment, "BOTH");
    }

    private Double calculateStudentFactorFromPeerAssessment(Student student, Assessment peerAssessment, String factorContributionType) {
        Group group = resolveGroupForAssessment(student, peerAssessment);
        List<Student> groupMembers = resolveGroupMembersForCourse(group, peerAssessment != null && peerAssessment.getCourse() != null ? peerAssessment.getCourse().getId() : null);
        if (group == null || groupMembers.isEmpty()) return 0.0;
        Map<Long, Double> individualAverages = new HashMap<>();
        
        for (Student member : groupMembers) {
            List<Mark> receivedMarks = finishedOnly(markRepository.findByEvaluatedStudentAndAssessment(member, peerAssessment));
            receivedMarks = ratingsFromOthers(receivedMarks, member).stream()
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
                long validCount = receivedMarks.stream()
                    .filter(m -> m.getRating() != null && m.getRating().getMarks() != null).count();
                if (validCount > 0) {
                    individualAverages.put(member.getId(), sum / validCount);
                }
            }
        }
        
        if (individualAverages.isEmpty()) return 0.0;
        
        double groupAverage = individualAverages.values().stream().mapToDouble(Double::doubleValue).average().orElse(0.0);
        if (groupAverage == 0) return 0.0;
        
        Double individualAverage = individualAverages.get(student.getId());
        if (individualAverage == null) return 0.0;
        
        double factor = individualAverage / groupAverage;
        return Math.round(factor * 1000.0) / 1000.0;
    }

    /** A mark counts once it is finished. Drafts are work in progress; rows from before status existed (null) still count. */
    public static boolean isFinished(Mark mark) {
        return mark != null && (mark.getStatus() == null
                || mark.getStatus() == Mark.SubmissionStatus.FINAL
                || mark.getStatus() == Mark.SubmissionStatus.SUBMITTED);
    }

    static List<Mark> finishedOnly(List<Mark> marks) {
        return marks == null ? new ArrayList<>() : marks.stream().filter(CalculateService::isFinished).collect(Collectors.toList());
    }

    /** What teammates gave a student: their own self-rating is not part of it. */
    static List<Mark> ratingsFromOthers(List<Mark> received, Student member) {
        return received.stream()
            .filter(m -> m.getEvaluatorStudent() == null || member == null || member.getId() == null
                    || !member.getId().equals(m.getEvaluatorStudent().getId()))
            .collect(Collectors.toList());
    }

    /**
     * Weighted sum of the teamwork factors that have data. A component with no data (factor 0) is left out and its weight is
     * shared by the others, so a student with one assessment marked is not treated as having scored zero on the rest.
     * With data for every component this is the plain weighted sum.
     */
    static double combineWeightedFactors(List<Double> factors, List<Double> weights) {
        double weightedSum = 0.0;
        double validWeight = 0.0;
        double allWeight = 0.0;
        for (int i = 0; i < factors.size(); i++) {
            double weight = weights.get(i) != null ? weights.get(i) : 0.0;
            allWeight += weight;
            double factor = factors.get(i) != null ? factors.get(i) : 0.0;
            if (factor > 0) {
                weightedSum += factor * weight;
                validWeight += weight;
            }
        }
        return validWeight > 0 ? weightedSum * (allWeight / validWeight) : 0.0;
    }

    private RubricCalculationDto calculateRubricData(Student student, Assessment assessment, Rubric rubric, List<Mark> allMarks) {
        RubricCalculationDto dto = new RubricCalculationDto();
        dto.setRubric(rubric);
        
        List<Mark> rubricMarks = allMarks.stream()
            .filter(m -> m.getRubric() != null && m.getRubric().getId().equals(rubric.getId()))
            .collect(Collectors.toList());
        // Where assessors marked this rubric, their marks decide it; student self/peer ratings are not averaged in with them.
        List<Mark> assessorMarks = rubricMarks.stream().filter(m -> m.getLecturer() != null).collect(Collectors.toList());
        if (!assessorMarks.isEmpty()) {
            rubricMarks = assessorMarks;
        }
            
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
        
        Map<String, List<Mark>> marksByLecturer = new HashMap<>();
        for (Mark mark : marks) {
            if (mark.getSubRubric() == null) continue;
            String evaluatorKey = null;
            if (mark.getLecturer() != null) evaluatorKey = "L" + mark.getLecturer().getId();
            else if (mark.getEvaluatorStudent() != null) evaluatorKey = "S" + mark.getEvaluatorStudent().getId();
            
            if (evaluatorKey != null) {
                marksByLecturer.computeIfAbsent(evaluatorKey, k -> new ArrayList<>()).add(mark);
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

    private Map<Integer, Double> calculateCloWeightedSumsIndividualOnly(Map<Long, RubricCalculationDto> rubricCalculations) {
        Map<Integer, Double> cloWeightedSums = new HashMap<>();
        for (RubricCalculationDto rubricCalc : rubricCalculations.values()) {
            String assessmentType = rubricCalc.getAssessmentType();
            if (assessmentType != null && "Individual Assessment".equalsIgnoreCase(assessmentType.trim())) {
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

    private Map<Integer, Double> calculateCloSumsGroupOnly(Map<Long, RubricCalculationDto> rubricCalculations) {
        Map<Integer, Double> cloSums = new HashMap<>();
        for (RubricCalculationDto rubricCalc : rubricCalculations.values()) {
            String assessmentType = rubricCalc.getAssessmentType();
            if (assessmentType != null && "Group Assessment".equalsIgnoreCase(assessmentType.trim())) {
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
                detail.put("evaluatorName", "Assessor (Supervisor Role)");
                detail.put("evaluatorType", "Assessor");
                evaluatorKey = "lecturer_supervisor_" + mark.getSupervisorId();
                
            } else if (mark.getLecturer() != null) {
                evaluatorKey = "lecturer_" + mark.getLecturer().getId();
                detail.put("evaluatorName", mark.getLecturer().getUsername());
                detail.put("evaluatorType", "Assessor");
                
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

    private Double resolveStudentFactor(Student student) {
        Long defaultCourseId = student != null && student.getCourse() != null ? student.getCourse().getId() : null;
        return resolveStudentFactor(student, defaultCourseId);
    }

    private Double resolveStudentFactor(Student student, Long courseId) {
        Optional<StudentResultOverride> overrideOpt = resolveCourseOverride(student, courseId);
        if (overrideOpt.isPresent() && overrideOpt.get().getOverriddenFactor() != null) {
            return overrideOpt.get().getOverriddenFactor();
        }
        return calculatePeerAssessmentFactorForStudent(student, courseId);
    }

    private Long inferCourseId(Student student, List<Assessment> assessments) {
        if (assessments != null) {
            for (Assessment a : assessments) {
                if (a != null && a.getCourse() != null && a.getCourse().getId() != null) {
                    return a.getCourse().getId();
                }
            }
        }
        return student != null && student.getCourse() != null ? student.getCourse().getId() : null;
    }

    private boolean belongsToCourseId(Mark mark, Long courseId) {
        if (courseId == null || mark == null) {
            return true;
        }
        if (mark.getAssessment() == null || mark.getAssessment().getCourse() == null
                || mark.getAssessment().getCourse().getId() == null) {
            return false;
        }
        return courseId.equals(mark.getAssessment().getCourse().getId());
    }

    private Optional<StudentResultOverride> resolveCourseOverride(Student student, Long courseId) {
        if (student == null) {
            return Optional.empty();
        }
        if (courseId != null) {
            Optional<StudentResultOverride> scoped = overrideRepository.findByStudentAndCourseId(student, courseId);
            if (scoped.isPresent()) {
                return scoped;
            }
        }
        Optional<StudentResultOverride> legacy = overrideRepository.findByStudent(student);
        if (legacy.isPresent() && courseId != null) {
            Course overrideCourse = legacy.get().getCourse();
            if (overrideCourse != null && overrideCourse.getId() != null && !courseId.equals(overrideCourse.getId())) {
                return Optional.empty();
            }
        }
        return legacy;
    }

    private List<FactorWeightage> resolveCourseWeightages(Student student, Long courseId) {
        if (courseId != null) {
            List<FactorWeightage> byCourseId = factorWeightageRepository.findByCourseId(courseId);
            if (byCourseId != null && !byCourseId.isEmpty()) {
                return byCourseId;
            }
            if (courseRepository != null) {
                Optional<Course> courseOpt = courseRepository.findById(courseId);
                if (courseOpt.isPresent()) {
                    List<FactorWeightage> byCourse = factorWeightageRepository.findByCourse(courseOpt.get());
                    if (byCourse != null && !byCourse.isEmpty()) {
                        return byCourse;
                    }
                }
            }
            if (student != null && student.getCourse() != null && courseId.equals(student.getCourse().getId())) {
                List<FactorWeightage> byStudentCourse = factorWeightageRepository.findByCourse(student.getCourse());
                return byStudentCourse != null ? byStudentCourse : List.of();
            }
            return List.of();
        }
        if (student != null && student.getCourse() != null) {
            List<FactorWeightage> byStudentCourse = factorWeightageRepository.findByCourse(student.getCourse());
            return byStudentCourse != null ? byStudentCourse : List.of();
        }
        return List.of();
    }

    private Group resolveGroupForAssessment(Student student, Assessment assessment) {
        if (student == null) {
            return null;
        }
        Long courseId = assessment != null && assessment.getCourse() != null ? assessment.getCourse().getId() : null;
        if (courseId != null && studentCourseAssignmentRepository != null && student.getId() != null) {
            Optional<StudentCourseAssignment> scaOpt = studentCourseAssignmentRepository
                .findByStudentIdAndCourseId(student.getId(), courseId);
            if (scaOpt.isPresent() && scaOpt.get().getGroup() != null) {
                Group assigned = scaOpt.get().getGroup();
                if (assigned.getCourse() == null || courseId.equals(assigned.getCourse().getId())) {
                    return assigned;
                }
            }
        }
        Group fallback = student.getGroup();
        if (fallback == null) {
            return null;
        }
        if (courseId != null && fallback.getCourse() != null && fallback.getCourse().getId() != null
                && !courseId.equals(fallback.getCourse().getId())) {
            return null;
        }
        return fallback;
    }

    private List<Student> resolveGroupMembersForCourse(Group group, Long courseId) {
        if (group == null) {
            return List.of();
        }
        java.util.Map<Long, Student> uniqueMembers = new java.util.LinkedHashMap<>();
        if (group.getStudents() != null) {
            for (Student member : group.getStudents()) {
                if (member != null && member.getId() != null) {
                    uniqueMembers.putIfAbsent(member.getId(), member);
                }
            }
        }
        if (courseId != null && group.getId() != null && studentCourseAssignmentRepository != null) {
            List<StudentCourseAssignment> courseAssignments = studentCourseAssignmentRepository
                .findByCourseIdAndGroupId(courseId, group.getId());
            if (courseAssignments != null) {
                for (StudentCourseAssignment sca : courseAssignments) {
                    if (sca.getStudent() != null && sca.getStudent().getId() != null) {
                        uniqueMembers.putIfAbsent(sca.getStudent().getId(), sca.getStudent());
                    }
                }
            }
        }
        return new ArrayList<>(uniqueMembers.values());
    }

    private Map<Long, List<Mark>> groupMarksByEvaluatedStudent(List<Mark> marks) {
        Map<Long, List<Mark>> grouped = new HashMap<>();
        if (marks == null || marks.isEmpty()) {
            return grouped;
        }

        for (Mark mark : marks) {
            if (mark.getEvaluatedStudent() == null || mark.getEvaluatedStudent().getId() == null) {
                continue;
            }
            grouped.computeIfAbsent(mark.getEvaluatedStudent().getId(), key -> new ArrayList<>()).add(mark);
        }
        return grouped;
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

    public Map<String, Map<String, List<String>>> getEvaluatorCommentsForRubric(Student student, Assessment assessment, Rubric rubric) {
        Map<String, Map<String, List<String>>> result = new java.util.LinkedHashMap<>();
        
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
            String questionLabel = comment.getDisplayLabel(assessment, rubric);
            String evaluatorName = comment.getDisplayName();
            String commentText = comment.getCommentText();
            
            if (evaluatorName != null && commentText != null && !commentText.trim().isEmpty()) {
                result.computeIfAbsent(questionLabel, k -> new java.util.LinkedHashMap<>())
                      .computeIfAbsent(evaluatorName, k -> new ArrayList<>())
                      .add(commentText);
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

    public Map<String, Map<String, List<String>>> getGroupAssessmentComments(Student student, Assessment assessment) {
        Map<String, Map<String, List<String>>> result = new java.util.LinkedHashMap<>();
        
        List<AssessmentComment> groupComments = commentRepository
            .findByEvaluatedStudentAndAssessmentAndRubricAssessmentType(student, assessment, "Group Assessment");
        
        for (AssessmentComment comment : groupComments) {
            String questionLabel = comment.getDisplayLabel(assessment);
            String evaluatorName = comment.getDisplayName();
            String commentText = comment.getCommentText();
            
            if (evaluatorName != null && commentText != null && !commentText.trim().isEmpty()) {
                result.computeIfAbsent(questionLabel, k -> new java.util.LinkedHashMap<>())
                      .computeIfAbsent(evaluatorName, k -> new ArrayList<>())
                      .add(commentText);
            }
        }
        
        return result;
    }

    public Map<String, Map<String, List<String>>> getIndividualAssessmentComments(Student student, Assessment assessment) {
        Map<String, Map<String, List<String>>> result = new java.util.LinkedHashMap<>();
        
        List<AssessmentComment> individualComments = commentRepository
            .findByEvaluatedStudentAndAssessmentAndRubricAssessmentType(student, assessment, "Individual Assessment");
        
        for (AssessmentComment comment : individualComments) {
            String questionLabel = comment.getDisplayLabel(assessment);
            String evaluatorName = comment.getDisplayName();
            String commentText = comment.getCommentText();
            
            if (evaluatorName != null && commentText != null && !commentText.trim().isEmpty()) {
                result.computeIfAbsent(questionLabel, k -> new java.util.LinkedHashMap<>())
                      .computeIfAbsent(evaluatorName, k -> new ArrayList<>())
                      .add(commentText);
            }
        }
        
        return result;
    }

    public Map<String, Map<String, List<String>>> getAllAssessmentComments(Student student, Assessment assessment) {
        Map<String, Map<String, List<String>>> result = new java.util.LinkedHashMap<>();
        
        List<AssessmentComment> allComments = commentRepository.findByEvaluatedStudentAndAssessment(student, assessment);
        
        for (AssessmentComment comment : allComments) {
            String baseLabel = comment.getDisplayLabel(assessment);
            String type = comment.getRubricAssessmentType();
            if (type == null || type.isEmpty()) {
                type = "Rubric";
            }
            
            String questionLabel = baseLabel + " (" + type + " Comments)";
            String evaluatorName = comment.getDisplayName();
            String commentText = comment.getCommentText();
            
            if (evaluatorName != null && commentText != null && !commentText.trim().isEmpty()) {
                result.computeIfAbsent(questionLabel, k -> new java.util.LinkedHashMap<>())
                      .computeIfAbsent(evaluatorName, k -> new ArrayList<>())
                      .add(commentText);
            }
        }
        
        long displayed = result.values().stream()
            .flatMap(evaluators -> evaluators.values().stream())
            .mapToLong(List::size).sum();
        logger.info("Comment retrieval: assessmentId={}, studentId={}, loaded={}, displayed={}",
            assessment.getId(), student.getId(), allComments.size(), displayed);

        return result;
    }

    /**
     * The comments the AI audit may read about a student: written by a teammate (not the student, not an assessor) and already
     * approved by a moderator. Pending and rejected comments never leave the system.
     */
    public List<AssessmentComment> getApprovedPeerComments(Student student, Assessment assessment) {
        return commentRepository.findByEvaluatedStudentAndAssessment(student, assessment).stream()
            .filter(c -> c.getEvaluatorType() == AssessmentComment.EvaluatorType.STUDENT)
            .filter(c -> c.getApprovalStatus() == AssessmentComment.ApprovalStatus.APPROVED)
            .filter(c -> c.getEvaluatorId() != null && !c.getEvaluatorId().equals(student.getId()))
            .filter(c -> c.getCommentText() != null && !c.getCommentText().trim().isEmpty())
            .collect(Collectors.toList());
    }

    private String getEvaluatorKey(Mark mark) {
        if (mark.getSupervisorId() != null && Boolean.TRUE.equals(mark.getIsSupervisorMark())) {
            // Since we're removing industrial supervisors, treat supervisor marks as lecturer marks
            // The supervisorId should now reference a lecturer ID
            return "Assessor (Supervisor Role)";
        } else if (mark.getLecturer() != null) {
            return mark.getLecturer().getUsername();
        } else if (mark.getEvaluatorStudent() != null) {
            return mark.getEvaluatorStudent().getUsername();
        } else {
            return "Unknown Evaluator";
        }
    }
}