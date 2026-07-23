package com.capstone.adproject.service;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import org.springframework.stereotype.Service;

import com.capstone.adproject.model.Assessment;
import com.capstone.adproject.model.Lecturer;
import com.capstone.adproject.model.LecturerGroupAssignment;
import com.capstone.adproject.model.LecturerRubricAssignment;
import com.capstone.adproject.model.LecturerStudentAssignment;
import com.capstone.adproject.model.Student;
import com.capstone.adproject.model.StudentAssessmentAssignment;
import com.capstone.adproject.repositories.LecturerGroupAssignmentRepository;
import com.capstone.adproject.repositories.LecturerRubricAssignmentRepository;
import com.capstone.adproject.repositories.LecturerStudentAssignmentRepository;
import com.capstone.adproject.repositories.StudentAssessmentAssignmentRepository;
import com.capstone.adproject.repositories.MarkRepository;
import com.capstone.adproject.model.Mark;

@Service
public class ProgressTrackingService {

    private final LecturerGroupAssignmentRepository lecturerGroupAssignmentRepository;
    private final LecturerRubricAssignmentRepository lecturerRubricAssignmentRepository;
    private final LecturerStudentAssignmentRepository lecturerStudentAssignmentRepository;
    private final StudentAssessmentAssignmentRepository studentAssessmentAssignmentRepository;
    private final LecturerAssessmentService lecturerAssessmentService;
    private final MarkService markService;
    private final MarkRepository markRepository;

    public ProgressTrackingService(
            LecturerGroupAssignmentRepository lecturerGroupAssignmentRepository,
            LecturerRubricAssignmentRepository lecturerRubricAssignmentRepository,
            LecturerStudentAssignmentRepository lecturerStudentAssignmentRepository,
            StudentAssessmentAssignmentRepository studentAssessmentAssignmentRepository,
            LecturerAssessmentService lecturerAssessmentService,
            MarkService markService,
            MarkRepository markRepository) {
        this.lecturerGroupAssignmentRepository = lecturerGroupAssignmentRepository;
        this.lecturerRubricAssignmentRepository = lecturerRubricAssignmentRepository;
        this.lecturerStudentAssignmentRepository = lecturerStudentAssignmentRepository;
        this.studentAssessmentAssignmentRepository = studentAssessmentAssignmentRepository;
        this.lecturerAssessmentService = lecturerAssessmentService;
        this.markService = markService;
        this.markRepository = markRepository;
    }

    public Map<String, Object> getLecturerProgress(Assessment assessment) {
        Map<String, Object> result = new HashMap<>();
        List<Map<String, Object>> progressList = new ArrayList<>();
        
        List<LecturerGroupAssignment> groupAssignments = lecturerGroupAssignmentRepository.findByAssessment(assessment);
        List<LecturerRubricAssignment> rubricAssignments = lecturerRubricAssignmentRepository.findByAssessment(assessment);
        List<LecturerStudentAssignment> studentAssignments = lecturerStudentAssignmentRepository.findByAssessment(assessment);

        String assignmentMode = "GROUP";
        if (!studentAssignments.isEmpty()) {
            assignmentMode = "STUDENT";
        } else if (!rubricAssignments.isEmpty()) {
            assignmentMode = "RUBRIC";
        }
        
        result.put("assignmentMode", assignmentMode);
        
        boolean hasGroupRubrics = assessment.getRubrics().stream().anyMatch(r -> "Group Assessment".equalsIgnoreCase(r.getAssessmentTypes()));
        boolean hasIndividualRubrics = assessment.getRubrics().stream().anyMatch(r -> "Individual Assessment".equalsIgnoreCase(r.getAssessmentTypes()));
        result.put("hasGroupRubrics", hasGroupRubrics);
        result.put("hasIndividualRubrics", hasIndividualRubrics);
        
        if ("STUDENT".equals(assignmentMode)) {
            Map<Lecturer, List<LecturerStudentAssignment>> byLecturer = studentAssignments.stream()
                .filter(a -> a.getLecturer() != null && a.getStudent() != null)
                .collect(Collectors.groupingBy(LecturerStudentAssignment::getLecturer));
                
            for (Map.Entry<Lecturer, List<LecturerStudentAssignment>> entry : byLecturer.entrySet()) {
                Lecturer lecturer = entry.getKey();
                int totalAssigned = entry.getValue().size();
                int completed = 0;
                int groupCompleted = 0;
                int individualCompleted = 0;
                
                for (LecturerStudentAssignment assignment : entry.getValue()) {
                    Long studentId = assignment.getStudent().getId();
                    if (lecturerAssessmentService.isGroupRubricsCompleteForStudent(assessment, lecturer, studentId)) groupCompleted++;
                    if (lecturerAssessmentService.isIndividualRubricsCompleteForStudent(assessment, lecturer, studentId)) individualCompleted++;
                    if (lecturerAssessmentService.isStudentEvaluationComplete(assessment, lecturer, studentId)) {
                        completed++;
                    }
                }
                
                Map<String, Object> row = new HashMap<>();
                row.put("lecturerName", lecturer.getUsername() != null ? lecturer.getUsername() : lecturer.getEmail());
                row.put("assignedTarget", totalAssigned + " Students");
                row.put("status", completed == 0 ? "Not Started" : (completed >= totalAssigned ? "Completed" : "In Progress"));
                row.put("progress", (totalAssigned > 0 ? (completed * 100 / totalAssigned) : 0));
                row.put("groupProgress", (totalAssigned > 0 ? (groupCompleted * 100 / totalAssigned) : 0));
                row.put("individualProgress", (totalAssigned > 0 ? (individualCompleted * 100 / totalAssigned) : 0));
                progressList.add(row);
            }
        } else if ("RUBRIC".equals(assignmentMode)) {
            Map<Lecturer, List<LecturerRubricAssignment>> byLecturer = rubricAssignments.stream()
                .filter(a -> a.getLecturer() != null && a.getRubric() != null)
                .collect(Collectors.groupingBy(LecturerRubricAssignment::getLecturer));
                
            for (Map.Entry<Lecturer, List<LecturerRubricAssignment>> entry : byLecturer.entrySet()) {
                Lecturer lecturer = entry.getKey();
                int totalAssigned = entry.getValue().size();
                int completed = 0;
                
                Map<String, Object> row = new HashMap<>();
                row.put("lecturerName", lecturer.getUsername() != null ? lecturer.getUsername() : lecturer.getEmail());
                row.put("assignedTarget", totalAssigned + " Rubrics");
                row.put("status", "Pending / In Progress");
                row.put("progress", (totalAssigned > 0 ? (completed * 100 / totalAssigned) : 0));
                progressList.add(row);
            }
        } else {
            Map<Lecturer, List<LecturerGroupAssignment>> byLecturer = groupAssignments.stream()
                .filter(a -> a.getLecturer() != null && a.getGroup() != null)
                .collect(Collectors.groupingBy(LecturerGroupAssignment::getLecturer));
                
            for (Map.Entry<Lecturer, List<LecturerGroupAssignment>> entry : byLecturer.entrySet()) {
                Lecturer lecturer = entry.getKey();
                int totalAssigned = entry.getValue().size();
                int completed = 0;
                int groupCompleted = 0;
                int individualCompleted = 0;
                
                for (LecturerGroupAssignment assignment : entry.getValue()) {
                    Long groupId = assignment.getGroup().getId();
                    if (lecturerAssessmentService.isGroupRubricsCompleteForGroup(assessment, lecturer, groupId)) groupCompleted++;
                    if (lecturerAssessmentService.isIndividualRubricsCompleteForGroup(assessment, lecturer, groupId)) individualCompleted++;
                    if (lecturerAssessmentService.isGroupEvaluationComplete(assessment, lecturer, groupId)) {
                        completed++;
                    }
                }
                
                Map<String, Object> row = new HashMap<>();
                row.put("lecturerName", lecturer.getUsername() != null ? lecturer.getUsername() : lecturer.getEmail());
                row.put("assignedTarget", totalAssigned + " Groups");
                row.put("status", completed == 0 ? "Not Started" : (completed >= totalAssigned ? "Completed" : "In Progress"));
                row.put("progress", (totalAssigned > 0 ? (completed * 100 / totalAssigned) : 0));
                row.put("groupProgress", (totalAssigned > 0 ? (groupCompleted * 100 / totalAssigned) : 0));
                row.put("individualProgress", (totalAssigned > 0 ? (individualCompleted * 100 / totalAssigned) : 0));
                progressList.add(row);
            }
        }

        result.put("progressList", progressList);
        return result;
    }

    public Map<String, Object> getStudentProgress(Assessment assessment) {
        Map<String, Object> result = new HashMap<>();
        List<Map<String, Object>> progressList = new ArrayList<>();
        
        List<StudentAssessmentAssignment> assignments = studentAssessmentAssignmentRepository.findByAssessment(assessment);
        
        for (StudentAssessmentAssignment assignment : assignments) {
            if (assignment.getStudent() == null) continue;
            Student student = assignment.getStudent();
            
            boolean isSelfAssigned = Boolean.TRUE.equals(assignment.getSelfAssessment());
            boolean isPeerAssigned = Boolean.TRUE.equals(assignment.getPeerAssessment());
            boolean isGroupAssigned = Boolean.TRUE.equals(assignment.getGroupAssessment());
            
            int selfTotal = isSelfAssigned ? 1 : 0;
            int peerTotal = 0;
            int groupTotal = 0;
            
            if (student.getGroup() != null && student.getGroup().getStudents() != null) {
                int membersCount = student.getGroup().getStudents().size();
                if (isPeerAssigned) peerTotal = Math.max(0, membersCount - 1);
                if (isGroupAssigned) groupTotal = Math.max(0, membersCount - 1);
            }
            
            int totalAssigned = selfTotal + peerTotal + groupTotal;
            
            List<Mark> submittedMarks = markRepository.findByEvaluatorStudentAndAssessmentAndStatus(
                student, assessment, Mark.SubmissionStatus.SUBMITTED);
                
            long selfCompleted = submittedMarks.stream()
                .filter(m -> "Self Assessment".equalsIgnoreCase(m.getAssessmentType()))
                .map(m -> m.getEvaluatedStudent().getId())
                .distinct()
                .count();
                
            long peerCompleted = submittedMarks.stream()
                .filter(m -> "Peer Assessment".equalsIgnoreCase(m.getAssessmentType()))
                .map(m -> m.getEvaluatedStudent().getId())
                .distinct()
                .count();
                
            long groupCompleted = submittedMarks.stream()
                .filter(m -> "Group Assessment".equalsIgnoreCase(m.getAssessmentType()))
                .map(m -> m.getEvaluatedStudent().getId())
                .distinct()
                .count();
                
            int completed = (int) (selfCompleted + peerCompleted + groupCompleted);
            
            Map<String, Object> row = new HashMap<>();
            row.put("studentName", student.getUsername() != null ? student.getUsername() : student.getEmail());
            row.put("assignedTarget", totalAssigned > 0 ? totalAssigned + " Evaluations" : "None");
            row.put("status", completed == 0 ? "Not Started" : (completed >= totalAssigned ? "Completed" : "In Progress"));
            row.put("progress", (totalAssigned > 0 ? (completed * 100 / totalAssigned) : 0));
            
            row.put("isSelfAssigned", isSelfAssigned);
            row.put("selfStatus", selfCompleted > 0 ? "Completed" : "Not Started");
            row.put("selfProgress", isSelfAssigned ? (selfCompleted > 0 ? 100 : 0) : 0);
            
            row.put("isPeerAssigned", isPeerAssigned);
            row.put("peerStatus", peerCompleted == 0 ? "Not Started" : (peerCompleted >= peerTotal ? "Completed" : "In Progress"));
            row.put("peerProgress", peerTotal > 0 ? (peerCompleted * 100 / peerTotal) : 0);
            
            row.put("isGroupAssigned", isGroupAssigned);
            row.put("groupStatus", groupCompleted == 0 ? "Not Started" : (groupCompleted >= groupTotal ? "Completed" : "In Progress"));
            row.put("groupProgress", groupTotal > 0 ? (groupCompleted * 100 / groupTotal) : 0);
            
            progressList.add(row);
        }
        
        result.put("progressList", progressList);
        return result;
    }

    public String getAssessorTypes(Assessment assessment) {
        boolean hasLecturer = !lecturerGroupAssignmentRepository.findByAssessment(assessment).isEmpty() ||
                              !lecturerRubricAssignmentRepository.findByAssessment(assessment).isEmpty() ||
                              !lecturerStudentAssignmentRepository.findByAssessment(assessment).isEmpty();
        boolean hasStudent = !studentAssessmentAssignmentRepository.findByAssessment(assessment).isEmpty();

        if (hasLecturer && hasStudent) return "Both";
        if (hasLecturer) return "Lecturer";
        if (hasStudent) return "Student";
        return "None";
    }
}