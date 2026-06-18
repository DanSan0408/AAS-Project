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

@Service
public class ProgressTrackingService {

    private final LecturerGroupAssignmentRepository lecturerGroupAssignmentRepository;
    private final LecturerRubricAssignmentRepository lecturerRubricAssignmentRepository;
    private final LecturerStudentAssignmentRepository lecturerStudentAssignmentRepository;
    private final StudentAssessmentAssignmentRepository studentAssessmentAssignmentRepository;
    private final LecturerAssessmentService lecturerAssessmentService;
    private final MarkService markService;

    public ProgressTrackingService(
            LecturerGroupAssignmentRepository lecturerGroupAssignmentRepository,
            LecturerRubricAssignmentRepository lecturerRubricAssignmentRepository,
            LecturerStudentAssignmentRepository lecturerStudentAssignmentRepository,
            StudentAssessmentAssignmentRepository studentAssessmentAssignmentRepository,
            LecturerAssessmentService lecturerAssessmentService,
            MarkService markService) {
        this.lecturerGroupAssignmentRepository = lecturerGroupAssignmentRepository;
        this.lecturerRubricAssignmentRepository = lecturerRubricAssignmentRepository;
        this.lecturerStudentAssignmentRepository = lecturerStudentAssignmentRepository;
        this.studentAssessmentAssignmentRepository = studentAssessmentAssignmentRepository;
        this.lecturerAssessmentService = lecturerAssessmentService;
        this.markService = markService;
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
        
        if ("STUDENT".equals(assignmentMode)) {
            Map<Lecturer, List<LecturerStudentAssignment>> byLecturer = studentAssignments.stream()
                .filter(a -> a.getLecturer() != null && a.getStudent() != null)
                .collect(Collectors.groupingBy(LecturerStudentAssignment::getLecturer));
                
            for (Map.Entry<Lecturer, List<LecturerStudentAssignment>> entry : byLecturer.entrySet()) {
                Lecturer lecturer = entry.getKey();
                int totalAssigned = entry.getValue().size();
                int completed = 0;
                
                for (LecturerStudentAssignment assignment : entry.getValue()) {
                    boolean isComplete = lecturerAssessmentService.isStudentEvaluationComplete(assessment, lecturer, assignment.getStudent().getId());
                    if (isComplete) {
                        completed++;
                    }
                }
                
                Map<String, Object> row = new HashMap<>();
                row.put("lecturerName", lecturer.getUsername() != null ? lecturer.getUsername() : lecturer.getEmail());
                row.put("assignedTarget", totalAssigned + " Students");
                row.put("status", completed == 0 ? "Not Started" : (completed >= totalAssigned ? "Completed" : "In Progress"));
                row.put("progress", (totalAssigned > 0 ? (completed * 100 / totalAssigned) : 0));
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
                
                for (LecturerGroupAssignment assignment : entry.getValue()) {
                    boolean isComplete = lecturerAssessmentService.isGroupEvaluationComplete(assessment, lecturer, assignment.getGroup().getId());
                    if (isComplete) {
                        completed++;
                    }
                }
                
                Map<String, Object> row = new HashMap<>();
                row.put("lecturerName", lecturer.getUsername() != null ? lecturer.getUsername() : lecturer.getEmail());
                row.put("assignedTarget", totalAssigned + " Groups");
                row.put("status", completed == 0 ? "Not Started" : (completed >= totalAssigned ? "Completed" : "In Progress"));
                row.put("progress", (totalAssigned > 0 ? (completed * 100 / totalAssigned) : 0));
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
            
            int totalAssigned = 0;
            int completed = 0;
            
            if (student.getGroup() != null && student.getGroup().getStudents() != null) {
                List<Student> teamMembers = student.getGroup().getStudents();
                totalAssigned = teamMembers.size();
                Map<String, Object> studentProgressMap = markService.getAssessmentProgress(student, assessment, teamMembers);
                completed = ((Number) studentProgressMap.getOrDefault("completedAssessments", 0)).intValue();
            }
            
            Map<String, Object> row = new HashMap<>();
            row.put("studentName", student.getUsername() != null ? student.getUsername() : student.getEmail());
            row.put("assignedTarget", totalAssigned > 0 ? totalAssigned + " Peers" : "No Group");
            row.put("status", completed == 0 ? "Not Started" : (completed >= totalAssigned ? "Completed" : "In Progress"));
            row.put("progress", (totalAssigned > 0 ? (completed * 100 / totalAssigned) : 0));
            progressList.add(row);
        }
        
        result.put("progressList", progressList);
        return result;
    }
}