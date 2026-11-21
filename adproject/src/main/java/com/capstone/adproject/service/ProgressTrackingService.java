package com.capstone.adproject.service;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.capstone.adproject.model.Assessment;
import com.capstone.adproject.model.AssessmentComment;
import com.capstone.adproject.model.EvaluationDeletionLog;
import com.capstone.adproject.model.Group;
import com.capstone.adproject.model.Lecturer;
import com.capstone.adproject.model.LecturerGroupAssignment;
import com.capstone.adproject.model.LecturerRubricAssignment;
import com.capstone.adproject.model.LecturerStudentAssignment;
import com.capstone.adproject.model.Rubric;
import com.capstone.adproject.model.Student;
import com.capstone.adproject.model.StudentAssessmentAssignment;
import com.capstone.adproject.model.StudentCourseAssignment;
import com.capstone.adproject.repositories.AssessmentCommentRepository;
import com.capstone.adproject.repositories.EvaluationDeletionLogRepository;
import com.capstone.adproject.repositories.LecturerGroupAssignmentRepository;
import com.capstone.adproject.repositories.LecturerRubricAssignmentRepository;
import com.capstone.adproject.repositories.LecturerStudentAssignmentRepository;
import com.capstone.adproject.repositories.StudentAssessmentAssignmentRepository;
import com.capstone.adproject.repositories.StudentCourseAssignmentRepository;
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
    private final AssessmentCommentRepository assessmentCommentRepository;
    private final EvaluationDeletionLogRepository evaluationDeletionLogRepository;

    @Autowired(required = false)
    private StudentCourseAssignmentRepository studentCourseAssignmentRepository;

    public ProgressTrackingService(
            LecturerGroupAssignmentRepository lecturerGroupAssignmentRepository,
            LecturerRubricAssignmentRepository lecturerRubricAssignmentRepository,
            LecturerStudentAssignmentRepository lecturerStudentAssignmentRepository,
            StudentAssessmentAssignmentRepository studentAssessmentAssignmentRepository,
            LecturerAssessmentService lecturerAssessmentService,
            MarkService markService,
            MarkRepository markRepository,
            AssessmentCommentRepository assessmentCommentRepository,
            EvaluationDeletionLogRepository evaluationDeletionLogRepository) {
        this.lecturerGroupAssignmentRepository = lecturerGroupAssignmentRepository;
        this.lecturerRubricAssignmentRepository = lecturerRubricAssignmentRepository;
        this.lecturerStudentAssignmentRepository = lecturerStudentAssignmentRepository;
        this.studentAssessmentAssignmentRepository = studentAssessmentAssignmentRepository;
        this.lecturerAssessmentService = lecturerAssessmentService;
        this.markService = markService;
        this.markRepository = markRepository;
        this.assessmentCommentRepository = assessmentCommentRepository;
        this.evaluationDeletionLogRepository = evaluationDeletionLogRepository;
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
                
            List<Student> targetStudents = lecturerAssessmentService.getTargetStudentsForAssessment(assessment);
                
            for (Map.Entry<Lecturer, List<LecturerRubricAssignment>> entry : byLecturer.entrySet()) {
                Lecturer lecturer = entry.getKey();
                List<Rubric> assignedRubrics = entry.getValue().stream()
                    .map(LecturerRubricAssignment::getRubric)
                    .filter(r -> r != null)
                    .distinct()
                    .collect(Collectors.toList());
                
                int totalAssignedRubrics = assignedRubrics.size();
                int totalEvaluationsNeeded = totalAssignedRubrics * targetStudents.size();
                int totalEvaluationsCompleted = 0;
                
                List<Map<String, Object>> rubricDetailsList = new ArrayList<>();
                
                for (Rubric rubric : assignedRubrics) {
                    Map<String, Object> rubricDetail = new HashMap<>();
                    rubricDetail.put("rubricName", rubric.getName() != null ? rubric.getName() : "Untitled Rubric");
                    rubricDetail.put("assessmentType", rubric.getAssessmentTypes() != null ? rubric.getAssessmentTypes() : "Rubric");
                    
                    List<Map<String, Object>> studentStatusList = new ArrayList<>();
                    int completedCountForRubric = 0;
                    
                    for (Student student : targetStudents) {
                        Map<String, Object> studentProg = lecturerAssessmentService.getRubricProgressForStudent(assessment, lecturer, rubric, student);
                        boolean isComplete = Boolean.TRUE.equals(studentProg.get("isComplete"));
                        if (isComplete) {
                            completedCountForRubric++;
                        }
                        
                        Map<String, Object> studentInfo = new HashMap<>();
                        studentInfo.put("studentName", student.getUsername() != null && !student.getUsername().trim().isEmpty() ? student.getUsername().trim() : (student.getEmail() != null ? student.getEmail() : "Unknown Student"));
                        studentInfo.put("status", studentProg.get("status"));
                        studentInfo.put("percentage", studentProg.get("percentage"));
                        studentInfo.put("completed", studentProg.get("completed"));
                        studentInfo.put("required", studentProg.get("required"));
                        studentStatusList.add(studentInfo);
                    }
                    
                    rubricDetail.put("students", studentStatusList);
                    rubricDetail.put("completedCount", completedCountForRubric);
                    rubricDetail.put("totalCount", targetStudents.size());
                    rubricDetail.put("progress", targetStudents.isEmpty() ? 0 : (completedCountForRubric * 100 / targetStudents.size()));
                    
                    rubricDetailsList.add(rubricDetail);
                    totalEvaluationsCompleted += completedCountForRubric;
                }
                
                Map<String, Object> row = new HashMap<>();
                row.put("lecturerName", lecturer.getUsername() != null && !lecturer.getUsername().trim().isEmpty() ? lecturer.getUsername().trim() : lecturer.getEmail());
                row.put("assignedTarget", totalAssignedRubrics + " Rubrics");
                row.put("status", totalEvaluationsCompleted == 0 ? "Not Started" : (totalEvaluationsCompleted >= totalEvaluationsNeeded ? "Completed" : "In Progress"));
                row.put("progress", totalEvaluationsNeeded > 0 ? (totalEvaluationsCompleted * 100 / totalEvaluationsNeeded) : 0);
                row.put("assignedRubricDetails", rubricDetailsList);
                
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
            
            Long courseId = assessment != null && assessment.getCourse() != null ? assessment.getCourse().getId() : null;
            Group courseGroup = resolveGroupForStudentInCourse(student, courseId);
            if (courseGroup != null) {
                int membersCount = countMembersInGroupForCourse(courseGroup, courseId);
                if (isPeerAssigned) peerTotal = Math.max(0, membersCount - 1);
                if (isGroupAssigned) groupTotal = Math.max(0, membersCount - 1);
            }
            
            int totalAssigned = selfTotal + peerTotal + groupTotal;
            
            List<Mark> submittedMarks = markRepository.findByEvaluatorStudentAndAssessmentAndStatus(
                student, assessment, Mark.SubmissionStatus.FINAL);
                
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
        if (hasLecturer) return "Assessor";
        if (hasStudent) return "Student";
        return "None";
    }

    private Group resolveGroupForStudentInCourse(Student student, Long courseId) {
        if (student == null) {
            return null;
        }
        if (courseId != null && studentCourseAssignmentRepository != null && student.getId() != null) {
            java.util.Optional<StudentCourseAssignment> scaOpt = studentCourseAssignmentRepository
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

    private int countMembersInGroupForCourse(Group group, Long courseId) {
        if (group == null) {
            return 0;
        }
        java.util.Set<Long> memberIds = new java.util.HashSet<>();
        if (group.getStudents() != null) {
            for (Student s : group.getStudents()) {
                if (s != null && s.getId() != null) {
                    memberIds.add(s.getId());
                }
            }
        }
        if (courseId != null && group.getId() != null && studentCourseAssignmentRepository != null) {
            for (StudentCourseAssignment sca : studentCourseAssignmentRepository
                    .findByCourseIdAndGroupId(courseId, group.getId())) {
                if (sca.getStudent() != null && sca.getStudent().getId() != null) {
                    memberIds.add(sca.getStudent().getId());
                }
            }
        }
        return !memberIds.isEmpty() ? memberIds.size() : (group.getStudents() != null ? group.getStudents().size() : 0);
    }

    // ---------------------------------------------------------------------------------------------------------
    // Finished evaluations: admin can read them and delete them (never edit). Only FINAL marks count as "done".
    // An evaluation is one evaluator's marks for one rubric on one student; an assessor's group-rubric evaluation is
    // stored once per member and shown as a single row for the group.
    // ---------------------------------------------------------------------------------------------------------

    public static final String KIND_LECTURER = "LECTURER";
    public static final String KIND_STUDENT = "STUDENT";
    private static final String GROUP_TYPE = "Group Assessment";
    private static final int MAX_REASON_LENGTH = 300;
    private static final int MAX_TARGETS = 500;

    public record EvaluationLine(String subRubric, String rating, double marks, Double maxMarks) {}

    public record EvaluationCommentView(String label, String text) {}

    public record EvaluationRow(String evaluatorKind, Long evaluatorId, String evaluatorName, String targetName,
                                List<Long> targetIds, Long rubricId, String rubricName, String assessmentType,
                                LocalDateTime submittedAt, double totalMarks, Double maxMarks,
                                List<EvaluationLine> lines, List<EvaluationCommentView> comments) {

        public String evaluatorRole() {
            return KIND_LECTURER.equals(evaluatorKind) ? "Assessor" : "Student";
        }

        public String targetIdsCsv() {
            return targetIds.stream().map(String::valueOf).collect(Collectors.joining(","));
        }
    }

    /** Finished evaluations for an assessment, optionally narrowed to a group, an evaluated student or a rubric. */
    @Transactional(readOnly = true)
    public List<EvaluationRow> findEvaluations(Assessment assessment, Long groupId, Long studentId, Long rubricId) {
        Long courseId = courseIdOf(assessment);
        Map<String, List<Mark>> buckets = new LinkedHashMap<>();
        Map<String, Group> bucketGroups = new HashMap<>();
        for (Mark mark : markRepository.findByAssessmentAndStatus(assessment, Mark.SubmissionStatus.FINAL)) {
            if (mark.getEvaluatedStudent() == null || evaluatorIdOf(mark) == null) {
                continue;
            }
            if (rubricId != null && !rubricId.equals(rubricIdOf(mark))) {
                continue;
            }
            Group group = resolveGroupForStudentInCourse(mark.getEvaluatedStudent(), courseId);
            String key = bucketKey(mark, group);
            buckets.computeIfAbsent(key, k -> new ArrayList<>()).add(mark);
            bucketGroups.put(key, group);
        }

        List<AssessmentComment> comments = loadComments(assessment);
        List<EvaluationRow> rows = new ArrayList<>();
        for (Map.Entry<String, List<Mark>> entry : buckets.entrySet()) {
            List<Mark> bucket = entry.getValue();
            Group group = bucketGroups.get(entry.getKey());
            if (studentId != null && bucket.stream().noneMatch(m -> studentId.equals(m.getEvaluatedStudent().getId()))) {
                continue;
            }
            if (groupId != null && (group == null || !groupId.equals(group.getId()))) {
                continue;
            }
            rows.add(buildRow(bucket, group, comments));
        }
        rows.sort(Comparator.comparing((EvaluationRow r) -> r.targetName().toLowerCase())
                .thenComparing(r -> r.rubricName().toLowerCase())
                .thenComparing(r -> r.evaluatorName().toLowerCase()));
        return rows;
    }

    /** Students an evaluation can be about, for the picker. */
    public List<Student> getEvaluationStudents(Assessment assessment) {
        return lecturerAssessmentService.getTargetStudentsForAssessment(assessment).stream()
                .sorted(Comparator.comparing((Student s) -> displayName(s), String.CASE_INSENSITIVE_ORDER))
                .collect(Collectors.toList());
    }

    public List<EvaluationDeletionLog> recentDeletions(Long assessmentId) {
        return assessmentId == null ? List.of() : evaluationDeletionLogRepository.findTop20ByAssessmentIdOrderByDeletedAtDesc(assessmentId);
    }

    /**
     * Permanently removes one finished evaluation (its marks and its rubric comments) and records who did it and why.
     * Throws IllegalArgumentException with a message fit to show the admin.
     *
     * @return how many marks were deleted
     */
    @Transactional
    public int deleteEvaluation(Assessment assessment, String evaluatorKind, Long evaluatorId, Long rubricId,
                                String assessmentType, List<Long> targetIds, String reason, String actor) {
        if (!KIND_LECTURER.equals(evaluatorKind) && !KIND_STUDENT.equals(evaluatorKind)) {
            throw new IllegalArgumentException("Unknown evaluator type.");
        }
        String cleanReason = reason == null ? "" : reason.trim();
        if (cleanReason.isEmpty()) {
            throw new IllegalArgumentException("Give a reason for deleting this evaluation. It is kept in the deletion record.");
        }
        if (cleanReason.length() > MAX_REASON_LENGTH) {
            throw new IllegalArgumentException("The reason can be at most " + MAX_REASON_LENGTH + " characters.");
        }
        if (evaluatorId == null || targetIds == null || targetIds.isEmpty() || targetIds.size() > MAX_TARGETS) {
            throw new IllegalArgumentException("That evaluation could not be identified.");
        }

        Set<Long> targets = new HashSet<>(targetIds);
        List<Mark> finalMarks = markRepository.findByAssessmentAndStatus(assessment, Mark.SubmissionStatus.FINAL);
        List<Mark> matches = finalMarks.stream()
                .filter(m -> m.getEvaluatedStudent() != null && targets.contains(m.getEvaluatedStudent().getId()))
                .filter(m -> kindOf(m).equals(evaluatorKind) && evaluatorId.equals(evaluatorIdOf(m)))
                .filter(m -> Objects.equals(rubricId, rubricIdOf(m)))
                .filter(m -> sameType(assessmentType, m.getAssessmentType()))
                .collect(Collectors.toList());
        if (matches.isEmpty()) {
            throw new IllegalArgumentException("That evaluation no longer exists. It may already have been deleted.");
        }

        List<AssessmentComment> allComments = loadComments(assessment);
        Group group = resolveGroupForStudentInCourse(matches.get(0).getEvaluatedStudent(), courseIdOf(assessment));
        EvaluationRow row = buildRow(matches, group, allComments);

        Set<Mark> deleting = new HashSet<>(matches);
        boolean lecturer = KIND_LECTURER.equals(evaluatorKind);
        boolean marksRemainOfThisType = finalMarks.stream()
                .filter(m -> !deleting.contains(m))
                .filter(m -> m.getEvaluatedStudent() != null && targets.contains(m.getEvaluatedStudent().getId()))
                .anyMatch(m -> kindOf(m).equals(evaluatorKind) && evaluatorId.equals(evaluatorIdOf(m))
                        && sameType(assessmentType, m.getAssessmentType()));
        List<AssessmentComment> commentsToDelete = allComments.stream()
                .filter(c -> commentEvaluatorMatches(c, evaluatorKind, evaluatorId, targets))
                .filter(c -> c.getRubricId() != null
                        ? c.getRubricId().equals(rubricId)
                        : lecturer && !marksRemainOfThisType && sameType(assessmentType, c.getRubricAssessmentType()))
                .collect(Collectors.toList());

        EvaluationDeletionLog log = new EvaluationDeletionLog();
        log.setAssessmentId(assessment.getId());
        log.setAssessmentTitle(truncate(assessment.getTitle(), 255));
        log.setEvaluatorKind(evaluatorKind);
        log.setEvaluatorId(evaluatorId);
        log.setEvaluatorName(truncate(row.evaluatorName(), 255));
        log.setEvaluatedNames(truncate(matches.stream().map(m -> displayName(m.getEvaluatedStudent())).distinct()
                .sorted().collect(Collectors.joining(", ")), 1000));
        log.setRubricName(truncate(row.rubricName(), 255));
        log.setAssessmentType(truncate(assessmentType, 60));
        log.setMarkCount(matches.size());
        log.setTotalMarks(row.totalMarks());
        log.setSnapshot(snapshotOf(row, commentsToDelete));
        log.setReason(cleanReason);
        log.setDeletedBy(truncate(actor == null || actor.isBlank() ? "unknown" : actor, 100));
        evaluationDeletionLogRepository.save(log);

        if (!commentsToDelete.isEmpty()) {
            assessmentCommentRepository.deleteAll(commentsToDelete);
        }
        markRepository.deleteAll(matches);
        return matches.size();
    }

    private EvaluationRow buildRow(List<Mark> bucket, Group group, List<AssessmentComment> allComments) {
        Mark first = bucket.get(0);
        boolean lecturer = KIND_LECTURER.equals(kindOf(first));
        boolean groupRow = isGroupRow(first, group);

        List<Long> targetIds = bucket.stream().map(m -> m.getEvaluatedStudent().getId()).distinct().sorted().collect(Collectors.toList());
        Long representative = targetIds.get(0);
        List<Mark> shown = bucket.stream().filter(m -> representative.equals(m.getEvaluatedStudent().getId())).collect(Collectors.toList());

        List<EvaluationLine> lines = new ArrayList<>();
        double total = 0;
        for (Mark m : shown) {
            double value = m.getMarkValue() != null ? m.getMarkValue() : 0.0;
            total += value;
            String label = m.getSubRubric() != null && m.getSubRubric().getName() != null ? m.getSubRubric().getName()
                    : (m.getRubric() != null && m.getRubric().getName() != null ? m.getRubric().getName() : "Rubric");
            String rating = m.getRating() != null && m.getRating().getName() != null ? m.getRating().getName() : "-";
            Double max = m.getSubRubric() != null && m.getSubRubric().getMarks() != null ? m.getSubRubric().getMarks().doubleValue() : null;
            lines.add(new EvaluationLine(label, rating, value, max));
        }

        Rubric rubric = first.getRubric();
        Double maxMarks = rubric != null && rubric.getMarks() != null ? rubric.getMarks().doubleValue() : null;
        LocalDateTime submittedAt = bucket.stream().map(Mark::getSubmittedAt).filter(Objects::nonNull).max(Comparator.naturalOrder()).orElse(null);

        Set<Long> targets = new HashSet<>(targetIds);
        Long rubricId = rubricIdOf(first);
        Set<String> seen = new LinkedHashSet<>();
        List<EvaluationCommentView> comments = new ArrayList<>();
        for (AssessmentComment c : allComments) {
            if (!commentEvaluatorMatches(c, kindOf(first), evaluatorIdOf(first), targets)) {
                continue;
            }
            boolean belongs = c.getRubricId() != null
                    ? c.getRubricId().equals(rubricId)
                    : lecturer && sameType(first.getAssessmentType(), c.getRubricAssessmentType());
            if (!belongs || c.getCommentText() == null) {
                continue;
            }
            String label = c.getCommentLabel() != null ? c.getCommentLabel() : "Comment";
            if (seen.add(label + "\u0000" + c.getCommentText())) {
                comments.add(new EvaluationCommentView(label, c.getCommentText()));
            }
        }

        String evaluatorName = lecturer ? displayName(first.getLecturer()) : displayName(first.getEvaluatorStudent());
        String targetName = groupRow && group.getGroupName() != null ? group.getGroupName() : displayName(first.getEvaluatedStudent());
        return new EvaluationRow(kindOf(first), evaluatorIdOf(first), evaluatorName, targetName, targetIds, rubricId,
                rubric != null && rubric.getName() != null ? rubric.getName() : "General", first.getAssessmentType(),
                submittedAt, total, maxMarks, lines, comments);
    }

    private static String snapshotOf(EvaluationRow row, List<AssessmentComment> deletedComments) {
        StringBuilder sb = new StringBuilder();
        sb.append(row.evaluatorRole()).append(' ').append(row.evaluatorName()).append(" evaluated ").append(row.targetName())
                .append(" on ").append(row.rubricName()).append(" (").append(row.assessmentType()).append(")\n");
        for (EvaluationLine line : row.lines()) {
            sb.append("- ").append(line.subRubric()).append(": ").append(line.rating()).append(" (").append(line.marks());
            if (line.maxMarks() != null) {
                sb.append(" / ").append(line.maxMarks());
            }
            sb.append(")\n");
        }
        sb.append("Total: ").append(row.totalMarks());
        if (row.maxMarks() != null) {
            sb.append(" / ").append(row.maxMarks());
        }
        sb.append('\n');
        for (AssessmentComment c : deletedComments) {
            sb.append("Comment [").append(c.getCommentLabel() != null ? c.getCommentLabel() : "Comment").append("]: ")
                    .append(c.getCommentText()).append('\n');
        }
        return sb.toString();
    }

    private List<AssessmentComment> loadComments(Assessment assessment) {
        List<AssessmentComment> comments = assessmentCommentRepository.findByAssessment(assessment);
        return comments != null ? comments : List.of();
    }

    private static boolean commentEvaluatorMatches(AssessmentComment c, String kind, Long evaluatorId, Set<Long> targets) {
        AssessmentComment.EvaluatorType type = KIND_LECTURER.equals(kind)
                ? AssessmentComment.EvaluatorType.LECTURER : AssessmentComment.EvaluatorType.STUDENT;
        return c.getEvaluatorType() == type
                && evaluatorId != null && evaluatorId.equals(c.getEvaluatorId())
                && c.getEvaluatedStudent() != null && targets.contains(c.getEvaluatedStudent().getId());
    }

    private static String kindOf(Mark m) {
        return m.getLecturer() != null ? KIND_LECTURER : KIND_STUDENT;
    }

    private static Long evaluatorIdOf(Mark m) {
        if (m.getLecturer() != null) {
            return m.getLecturer().getId();
        }
        return m.getEvaluatorStudent() != null ? m.getEvaluatorStudent().getId() : null;
    }

    private static Long rubricIdOf(Mark m) {
        return m.getRubric() != null ? m.getRubric().getId() : null;
    }

    private static boolean sameType(String a, String b) {
        return a == null ? b == null : a.equalsIgnoreCase(b);
    }

    private static boolean isGroupRow(Mark m, Group group) {
        return group != null && KIND_LECTURER.equals(kindOf(m)) && GROUP_TYPE.equalsIgnoreCase(m.getAssessmentType());
    }

    private static String bucketKey(Mark m, Group group) {
        String target = isGroupRow(m, group) ? "g" + group.getId() : "s" + m.getEvaluatedStudent().getId();
        String type = m.getAssessmentType() == null ? "" : m.getAssessmentType().toLowerCase();
        return kindOf(m) + "|" + evaluatorIdOf(m) + "|" + rubricIdOf(m) + "|" + type + "|" + target;
    }

    private static Long courseIdOf(Assessment assessment) {
        return assessment != null && assessment.getCourse() != null ? assessment.getCourse().getId() : null;
    }

    private static String displayName(Student s) {
        if (s == null) {
            return "Unknown";
        }
        if (s.getUsername() != null && !s.getUsername().isBlank()) {
            return s.getUsername().trim();
        }
        return s.getEmail() != null ? s.getEmail() : "Student #" + s.getId();
    }

    private static String displayName(Lecturer l) {
        if (l == null) {
            return "Unknown";
        }
        if (l.getUsername() != null && !l.getUsername().isBlank()) {
            return l.getUsername().trim();
        }
        return l.getEmail() != null ? l.getEmail() : "Assessor #" + l.getId();
    }

    private static String truncate(String value, int max) {
        if (value == null) {
            return null;
        }
        return value.length() <= max ? value : value.substring(0, max);
    }
}
