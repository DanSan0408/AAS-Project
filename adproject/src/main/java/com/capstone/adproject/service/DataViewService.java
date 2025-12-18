package com.capstone.adproject.service;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.TreeSet;
import java.util.stream.Collectors;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.capstone.adproject.dto.AssessmentColumnDto;
import com.capstone.adproject.dto.AssessmentDataViewDto;
import com.capstone.adproject.dto.AssessmentResultDetails;
import com.capstone.adproject.dto.OverallDataViewDto;
import com.capstone.adproject.dto.OverallStudentRowDto;
import com.capstone.adproject.dto.RubricHeaderDto;
import com.capstone.adproject.dto.StudentAssessmentResultDto;
import com.capstone.adproject.dto.StudentFactorDto;
import com.capstone.adproject.dto.StudentRowDto;
import com.capstone.adproject.model.Assessment;
import com.capstone.adproject.model.AssessmentComment;
import com.capstone.adproject.model.IndustrialSupervisor;
import com.capstone.adproject.model.Lecturer;
import com.capstone.adproject.model.LecturerGroupAssignment;
import com.capstone.adproject.model.Mark;
import com.capstone.adproject.model.Rubric;
import com.capstone.adproject.model.Student;
import com.capstone.adproject.repositories.AssessmentCommentRepository;
import com.capstone.adproject.repositories.AssessmentRepository;
import com.capstone.adproject.repositories.IndustrialSupervisorRepository;
import com.capstone.adproject.repositories.LecturerGroupAssignmentRepository;
import com.capstone.adproject.repositories.MarkRepository;
import com.capstone.adproject.repositories.StudentRepository;

@Service
public class DataViewService {
    
    @Autowired
    private CalculatedResultPersistenceService calculatedResultPersistenceService;
    
    @Autowired
    private AssessmentRepository assessmentRepository;
    
    @Autowired
    private StudentRepository studentRepository;
    
    @Autowired
    private IndustrialSupervisorRepository industrialSupervisorRepository;
    
    @Autowired
    private LecturerGroupAssignmentRepository lecturerGroupAssignmentRepository;
    
    @Autowired
    private FactorCalculationService factorCalculationService;
    
    @Autowired
    private MarkRepository markRepository;
    
    @Autowired
    private AssessmentCommentRepository assessmentCommentRepository;
    
    /**
     * ✅ COMPLETELY FIXED: Build assessment-specific data view with Rating + Mark columns
     * Supports multiple lecturers evaluating the same student/group
     */
    @Transactional(readOnly = true)
    public AssessmentDataViewDto buildAssessmentDataView(Long assessmentId) {
        Assessment assessment = assessmentRepository.findById(assessmentId)
            .orElseThrow(() -> new IllegalArgumentException("Assessment not found"));
        
        AssessmentDataViewDto dataView = new AssessmentDataViewDto();
        dataView.setAssessmentId(assessmentId);
        dataView.setAssessmentTitle(assessment.getTitle());
        
        // Get rubric headers
        List<RubricHeaderDto> groupRubrics = new ArrayList<>();
        List<RubricHeaderDto> individualRubrics = new ArrayList<>();
        Set<Integer> clos = new TreeSet<>();
        
        for (Rubric rubric : assessment.getRubrics()) {
            RubricHeaderDto header = new RubricHeaderDto();
            header.setRubricId(rubric.getId());
            header.setRubricName(rubric.getName());
            header.setClo(rubric.getClo());
            header.setAssessmentType(rubric.getAssessmentTypes());
            
            if ("Group Assessment".equalsIgnoreCase(rubric.getAssessmentTypes())) {
                groupRubrics.add(header);
            } else {
                individualRubrics.add(header);
            }
            
            if (rubric.getClo() != null) {
                clos.add(rubric.getClo());
            }
        }
        
        dataView.setGroupRubrics(groupRubrics);
        dataView.setIndividualRubrics(individualRubrics);
        dataView.setClos(clos);
        
        System.out.println("\n=== BUILDING ASSESSMENT DATA VIEW ===");
        System.out.println("Assessment: " + assessment.getTitle());
        
        // Get all marks for this assessment
        List<Mark> allMarks = markRepository.findByAssessment(assessment);
        System.out.println("Total marks: " + allMarks.size());
        
        // Get lecturer assignments
        List<LecturerGroupAssignment> lecturerAssignments = lecturerGroupAssignmentRepository
            .findByAssessment(assessment);
        
        System.out.println("Lecturer assignments: " + lecturerAssignments.size());
        
        // Create evaluation pairs: (evaluator, evaluatedStudent) → list of marks
        Map<EvaluationPair, List<Mark>> marksByPair = new HashMap<>();
        
        for (Mark mark : allMarks) {
            // For lecturer marks, create a pair for EACH lecturer assigned to the group
            List<EvaluationPair> pairs = createEvaluationPairs(mark, lecturerAssignments);
            
            for (EvaluationPair pair : pairs) {
                marksByPair.computeIfAbsent(pair, k -> new ArrayList<>()).add(mark);
            }
        }
        
        System.out.println("Total evaluation pairs: " + marksByPair.size());
        
        // Build student rows
        List<StudentRowDto> studentRows = new ArrayList<>();
        
        for (Map.Entry<EvaluationPair, List<Mark>> entry : marksByPair.entrySet()) {
            EvaluationPair pair = entry.getKey();
            List<Mark> marks = entry.getValue();
            
            StudentRowDto row = new StudentRowDto();
            
            // Evaluator info
            row.setEvaluatorId(pair.evaluatorId);
            row.setEvaluatorEmail(pair.evaluatorEmail);
            row.setEvaluatorName(pair.evaluatorName);
            
            // Evaluated student info
            row.setStudentId(pair.evaluatedStudentId);
            row.setStudentEmail(pair.evaluatedStudentEmail);
            row.setStudentName(pair.evaluatedStudentName);
            row.setGroupName(pair.groupName);
            
            System.out.println("\nProcessing pair: " + pair.evaluatorName + " → " + pair.evaluatedStudentName);
            System.out.println("  Marks for this pair: " + marks.size());
            
            // Get student factor
            Double studentFactor = 1.0;
            StudentAssessmentResultDto result = calculatedResultPersistenceService.getPersistedResults(
                pair.evaluatedStudentId, assessmentId);
            
            if (result != null) {
                if (result.getStudentFactor() != null) {
                    studentFactor = result.getStudentFactor();
                }
                row.setCloTotals(result.getCloWeightedMarks());
                row.setTotalMarks(result.getTotalMarks());
            }
            
            System.out.println("  Student factor: " + studentFactor);
            
            // Process marks - store BOTH rating and mark value
            for (Mark mark : marks) {
                if (mark.getRubric() == null) continue;
                
                Long rubricId = mark.getRubric().getId();
                String assessmentType = mark.getRubric().getAssessmentTypes();
                String ratingName = mark.getRating() != null ? mark.getRating().getName() : "-";
                Double markValue = mark.getMarkValue();
                
                System.out.println("  Processing mark: Rubric=" + mark.getRubric().getName() + 
                                 ", Type=" + assessmentType + ", Rating=" + ratingName + ", Mark=" + markValue);
                
                if ("Group Assessment".equalsIgnoreCase(assessmentType)) {
                    // For group assessments: show rating and weighted mark
                    Double weightedMark = markValue != null ? markValue * studentFactor : null;
                    row.getGroupRubricRatings().put(rubricId, ratingName);
                    row.getGroupRubricMarks().put(rubricId, weightedMark);
                    System.out.println("    → GROUP: rating=" + ratingName + ", weighted=" + weightedMark);
                } else if ("Individual Assessment".equalsIgnoreCase(assessmentType)) {
                    // For individual assessments: show rating and mark as-is
                    row.getIndividualRubricRatings().put(rubricId, ratingName);
                    row.getIndividualRubricMarks().put(rubricId, markValue);
                    System.out.println("    → INDIVIDUAL: rating=" + ratingName + ", mark=" + markValue);
                }
            }
            
            // Get comments
            populateCommentsForPair(row, pair, assessment);
            
            studentRows.add(row);
        }
        
        // Sort by evaluated student name, then evaluator name
        studentRows.sort(Comparator
            .comparing(StudentRowDto::getStudentName)
            .thenComparing(StudentRowDto::getEvaluatorName));
        
        dataView.setStudentRows(studentRows);
        
        System.out.println("Total rows created: " + studentRows.size());
        System.out.println("======================================\n");
        
        return dataView;
    }
    
    /**
     * ✅ FIXED: Create evaluation pairs - returns LIST to handle multiple lecturers
     */
    private List<EvaluationPair> createEvaluationPairs(Mark mark, List<LecturerGroupAssignment> lecturerAssignments) {
        List<EvaluationPair> pairs = new ArrayList<>();
        
        Student evaluatedStudent = mark.getEvaluatedStudent();
        if (evaluatedStudent == null) return pairs;
        
        String groupName = evaluatedStudent.getGroup() != null ? 
            evaluatedStudent.getGroup().getGroupName() : "No Group";
        
        // 1. Supervisor Mark - single pair
        if (mark.getIsSupervisorMark() != null && mark.getIsSupervisorMark()) {
            Long supervisorId = mark.getSupervisorId();
            if (supervisorId != null) {
                IndustrialSupervisor supervisor = industrialSupervisorRepository.findById(supervisorId)
                    .orElse(null);
                if (supervisor != null) {
                    EvaluationPair pair = new EvaluationPair();
                    pair.evaluatorId = supervisorId;
                    pair.evaluatorEmail = supervisor.getEmail();
                    pair.evaluatorName = supervisor.getUsername();
                    pair.evaluatorType = "SUPERVISOR";
                    pair.evaluatedStudentId = evaluatedStudent.getId();
                    pair.evaluatedStudentEmail = evaluatedStudent.getEmail();
                    pair.evaluatedStudentName = evaluatedStudent.getUsername();
                    pair.groupName = groupName;
                    pairs.add(pair);
                }
            }
            return pairs;
        }
        
        // 2. Peer/Self Mark - single pair
        if (mark.getEvaluatorStudent() != null) {
            Student evaluator = mark.getEvaluatorStudent();
            EvaluationPair pair = new EvaluationPair();
            pair.evaluatorId = evaluator.getId();
            pair.evaluatorEmail = evaluator.getEmail();
            pair.evaluatorName = evaluator.getUsername();
            pair.evaluatorType = "PEER_SELF";
            pair.evaluatedStudentId = evaluatedStudent.getId();
            pair.evaluatedStudentEmail = evaluatedStudent.getEmail();
            pair.evaluatedStudentName = evaluatedStudent.getUsername();
            pair.groupName = groupName;
            pairs.add(pair);
            return pairs;
        }
        
        // 3. ✅ CRITICAL FIX: Lecturer Mark - create pair for EACH assigned lecturer
        if (evaluatedStudent.getGroup() != null) {
            List<LecturerGroupAssignment> assignedLecturers = lecturerAssignments.stream()
                .filter(assignment -> assignment.getGroup().getId().equals(evaluatedStudent.getGroup().getId()))
                .collect(Collectors.toList());
            
            if (!assignedLecturers.isEmpty()) {
                // Create a separate pair for EACH lecturer assigned to this group
                for (LecturerGroupAssignment assignment : assignedLecturers) {
                    Lecturer lecturer = assignment.getLecturer();
                    
                    EvaluationPair pair = new EvaluationPair();
                    pair.evaluatorId = lecturer.getId();
                    pair.evaluatorEmail = lecturer.getEmail();
                    pair.evaluatorName = lecturer.getUsername();
                    pair.evaluatorType = "LECTURER";
                    pair.evaluatedStudentId = evaluatedStudent.getId();
                    pair.evaluatedStudentEmail = evaluatedStudent.getEmail();
                    pair.evaluatedStudentName = evaluatedStudent.getUsername();
                    pair.groupName = groupName;
                    
                    pairs.add(pair);
                }
            }
        }
        
        return pairs;
    }
    
    /**
     * Populate comments for an evaluation pair
     */
    private void populateCommentsForPair(StudentRowDto row, EvaluationPair pair, Assessment assessment) {
        List<AssessmentComment> comments = assessmentCommentRepository
            .findByEvaluatedStudentAndAssessment(
                studentRepository.findById(pair.evaluatedStudentId).orElse(null), 
                assessment
            );
        
        // Filter by evaluator
        List<AssessmentComment> pairComments = comments.stream()
            .filter(comment -> {
                if ("SUPERVISOR".equals(pair.evaluatorType)) {
                    return comment.getEvaluatorType() == AssessmentComment.EvaluatorType.SUPERVISOR &&
                           comment.getEvaluatorId().equals(pair.evaluatorId);
                } else if ("LECTURER".equals(pair.evaluatorType)) {
                    return comment.getEvaluatorType() == AssessmentComment.EvaluatorType.LECTURER &&
                           comment.getEvaluatorId().equals(pair.evaluatorId);
                } else if ("PEER_SELF".equals(pair.evaluatorType)) {
                    return comment.getEvaluatorType() == AssessmentComment.EvaluatorType.STUDENT &&
                           comment.getEvaluatorId().equals(pair.evaluatorId);
                }
                return false;
            })
            .filter(c -> c.getRubricId() == null)
            .collect(Collectors.toList());
        
        // Separate by rubric type
        StringBuilder groupComments = new StringBuilder();
        StringBuilder individualComments = new StringBuilder();
        
        for (AssessmentComment comment : pairComments) {
            String commentText = comment.getCommentText();
            if (commentText == null || commentText.trim().isEmpty()) continue;
            
            StringBuilder commentBuilder = new StringBuilder();
            if (comment.getCommentLabel() != null && !comment.getCommentLabel().isEmpty()) {
                commentBuilder.append(comment.getCommentLabel()).append(": ");
            }
            commentBuilder.append(commentText);
            
            if ("Group Assessment".equalsIgnoreCase(comment.getRubricAssessmentType())) {
                if (groupComments.length() > 0) groupComments.append("; ");
                groupComments.append(commentBuilder);
            } else if ("Individual Assessment".equalsIgnoreCase(comment.getRubricAssessmentType())) {
                if (individualComments.length() > 0) individualComments.append("; ");
                individualComments.append(commentBuilder);
            }
        }
        
        row.setGroupComments(groupComments.length() > 0 ? groupComments.toString() : null);
        row.setIndividualComments(individualComments.length() > 0 ? individualComments.toString() : null);
    }
    
    /**
     * Helper class for evaluation pairs
     */
    private static class EvaluationPair {
        Long evaluatorId;
        String evaluatorEmail;
        String evaluatorName;
        String evaluatorType;
        
        Long evaluatedStudentId;
        String evaluatedStudentEmail;
        String evaluatedStudentName;
        String groupName;
        
        @Override
        public boolean equals(Object o) {
            if (this == o) return true;
            if (!(o instanceof EvaluationPair)) return false;
            EvaluationPair that = (EvaluationPair) o;
            return Objects.equals(evaluatorId, that.evaluatorId) &&
                   Objects.equals(evaluatorType, that.evaluatorType) &&
                   Objects.equals(evaluatedStudentId, that.evaluatedStudentId);
        }
        
        @Override
        public int hashCode() {
            return Objects.hash(evaluatorId, evaluatorType, evaluatedStudentId);
        }
    }
    
    /**
     * Build overall data view across all assessments
     */
    @Transactional(readOnly = true)
    public OverallDataViewDto buildOverallDataView() {
        OverallDataViewDto dataView = new OverallDataViewDto();
        
        List<Assessment> assessments = assessmentRepository.findAll();
        assessments.sort(Comparator.comparing(Assessment::getTitle));
        
        List<AssessmentColumnDto> assessmentColumns = new ArrayList<>();
        for (Assessment assessment : assessments) {
            AssessmentColumnDto column = new AssessmentColumnDto();
            column.setAssessmentId(assessment.getId());
            column.setAssessmentTitle(assessment.getTitle());
            
            Set<Integer> clos = assessment.getRubrics().stream()
                .map(Rubric::getClo)
                .filter(Objects::nonNull)
                .collect(Collectors.toSet());
            column.setClos(clos);
            
            assessmentColumns.add(column);
        }
        dataView.setAssessmentColumns(assessmentColumns);
        
        List<Student> students = studentRepository.findAll();
        students.sort(Comparator.comparing(Student::getUsername));
        
        var groupFactors = factorCalculationService.calculateFactorsForAllGroups();
        Map<Long, Double> studentFactorMap = new HashMap<>();
        for (var groupFactor : groupFactors) {
            for (StudentFactorDto studentFactor : groupFactor.getStudentFactors()) {
                studentFactorMap.put(studentFactor.getStudentId(), studentFactor.getCappedFactor());
            }
        }
        
        List<OverallStudentRowDto> studentRows = new ArrayList<>();
        
        for (Student student : students) {
            OverallStudentRowDto row = new OverallStudentRowDto();
            row.setStudentId(student.getId());
            row.setStudentName(student.getUsername());
            row.setGroupName(student.getGroup() != null ? student.getGroup().getGroupName() : "No Group");
            row.setFactor(studentFactorMap.getOrDefault(student.getId(), 1.0));
            
            double grandTotal = 0.0;
            
            for (Assessment assessment : assessments) {
                StudentAssessmentResultDto result = calculatedResultPersistenceService.getPersistedResults(
                    student.getId(), assessment.getId());
                
                AssessmentResultDetails details = new AssessmentResultDetails();
                
                if (result != null) {
                    details.setCloMrm(result.getCloTotalMarks());
                    details.setCloWrm(result.getCloWeightedMarks());
                    details.setTotalT(result.getTotalMarks());
                    
                    grandTotal += result.getTotalMarks();
                } else {
                    details.setTotalT(0.0);
                }
                
                row.getAssessmentResults().put(assessment.getId(), details);
            }
            
            row.setGrandTotal(grandTotal);
            studentRows.add(row);
        }
        
        dataView.setStudentRows(studentRows);
        
        return dataView;
    }
}