/*package com.capstone.adproject.service;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.capstone.adproject.model.Assessment;
import com.capstone.adproject.model.AssessmentComment;
import com.capstone.adproject.model.Group;
import com.capstone.adproject.model.IndustrialSupervisor;
import com.capstone.adproject.model.Mark;
import com.capstone.adproject.model.Rating;
import com.capstone.adproject.model.Rubric;
import com.capstone.adproject.model.Student;
import com.capstone.adproject.model.SubRubric;
import com.capstone.adproject.repositories.AssessmentCommentRepository;
import com.capstone.adproject.repositories.GroupRepository;
import com.capstone.adproject.repositories.IndustrialSupervisorRepository;
import com.capstone.adproject.repositories.MarkRepository;
import com.capstone.adproject.repositories.RatingRepository;

@Service
@Transactional
public class IndustrialSupervisorService {
    
    private final IndustrialSupervisorRepository industrialSupervisorRepository;
    private final GroupRepository groupRepository;
    private final PasswordEncoder passwordEncoder;
    
    @Autowired
    private MarkRepository markRepository;
    
    @Autowired
    private AssessmentCommentRepository commentRepository;
    
    @Autowired
    private RatingRepository ratingRepository;
    
    public IndustrialSupervisorService(
            IndustrialSupervisorRepository industrialSupervisorRepository,
            GroupRepository groupRepository,
            PasswordEncoder passwordEncoder) {
        this.industrialSupervisorRepository = industrialSupervisorRepository;
        this.groupRepository = groupRepository;
        this.passwordEncoder = passwordEncoder;
    }
    
    public Optional<IndustrialSupervisor> findByUsername(String username) {
        return industrialSupervisorRepository.findByUsername(username);
    }
    
    public Optional<IndustrialSupervisor> findByEmail(String email) {
        return industrialSupervisorRepository.findByEmail(email);
    }
    
    public List<Group> getAssignedGroups(Long supervisorId) {
        return groupRepository.findByIndustrialSupervisorId(supervisorId);
    }
    
    public List<Group> getAssignedGroupsByUsername(String username) {
        Optional<IndustrialSupervisor> supervisor = findByUsername(username);
        if (supervisor.isPresent()) {
            return getAssignedGroups(supervisor.get().getId());
        }
        return List.of();
    }
    
    public IndustrialSupervisor save(IndustrialSupervisor supervisor) {
        return industrialSupervisorRepository.save(supervisor);
    }
    
    public IndustrialSupervisor createSupervisor(String username, String password, String email) {
        IndustrialSupervisor supervisor = new IndustrialSupervisor();
        supervisor.setUsername(username);
        supervisor.setPassword(passwordEncoder.encode(password));
        supervisor.setEmail(email);
        return save(supervisor);
    }
    
    public boolean existsByUsername(String username) {
        return industrialSupervisorRepository.existsByUsername(username);
    }
    
    public boolean existsByEmail(String email) {
        return industrialSupervisorRepository.existsByEmail(email);
    }
    
    public boolean hasEvaluationForGroup(Long supervisorId, Long groupId, Long assessmentId) {
        Long count = markRepository.countBySupervisorIdAndAssessmentId(supervisorId, assessmentId);
        return count != null && count > 0;
    }
    
    public Map<String, Object> getEvaluationProgress(
            Long supervisorId, Group group, Assessment assessment) {
        
        Map<String, Object> progress = new HashMap<>();
        
        List<Rubric> groupRubrics = assessment.getRubrics().stream()
            .filter(r -> "Group Assessment".equals(r.getAssessmentTypes()))
            .collect(Collectors.toList());
        
        List<Rubric> individualRubrics = assessment.getRubrics().stream()
            .filter(r -> "Individual Assessment".equals(r.getAssessmentTypes()))
            .collect(Collectors.toList());
        
        int totalGroupItems = countEvaluationItems(groupRubrics);
        int totalIndividualItems = countEvaluationItems(individualRubrics);
        
        List<Student> students = group.getStudents();
        if (students.isEmpty()) {
            progress.put("status", "NO_STUDENTS");
            progress.put("percentage", 0.0);
            return progress;
        }
        
        Student firstStudent = students.get(0);
        
        int completedGroupItems = countCompletedItems(
            supervisorId, firstStudent.getId(), assessment.getId(), groupRubrics);
        
        boolean groupComplete = (totalGroupItems > 0 && completedGroupItems >= totalGroupItems);
        
        List<String> groupCommentLabels = assessment.getGroupCommentLabels();
        boolean hasGroupComments = groupCommentLabels != null && !groupCommentLabels.isEmpty();
        
        boolean groupCommentsComplete = true; 
        
        if (hasGroupComments) {
            int groupCommentCount = assessment.getGroupCommentCount();
            int groupCommentMinLength = assessment.getGroupCommentMinLength() != null ? 
                assessment.getGroupCommentMinLength() : 0;
            
            if (groupCommentCount > 0 && groupCommentMinLength > 0) {
                long existingGroupComments = commentRepository
                    .findSupervisorCommentsForStudent(supervisorId, firstStudent.getId(), assessment.getId())
                    .stream()
                    .filter(c -> "Group Assessment".equals(c.getRubricAssessmentType()))
                    .filter(c -> c.getRubricId() == null) 
                    .filter(c -> c.getCommentText() != null && c.getCommentText().length() >= groupCommentMinLength)
                    .count();
                
                groupCommentsComplete = (existingGroupComments >= groupCommentCount);
            }
        }
        
        int studentsEvaluated = 0;
        for (Student student : students) {
            int completedItems = countCompletedItems(
                supervisorId, student.getId(), assessment.getId(), individualRubrics);
            
            List<String> individualCommentLabels = assessment.getIndividualCommentLabels();
            boolean hasIndividualComments = individualCommentLabels != null && !individualCommentLabels.isEmpty();
            
            boolean commentsComplete = true; 
            if (hasIndividualComments) {
                int individualCommentCount = assessment.getIndividualCommentCount();
                int individualCommentMinLength = assessment.getIndividualCommentMinLength() != null ? 
                    assessment.getIndividualCommentMinLength() : 0;
                
                if (individualCommentCount > 0 && individualCommentMinLength > 0) {
                    long existingComments = commentRepository
                        .findSupervisorCommentsForStudent(supervisorId, student.getId(), assessment.getId())
                        .stream()
                        .filter(c -> "Individual Assessment".equals(c.getRubricAssessmentType()))
                        .filter(c -> c.getRubricId() == null) 
                        .filter(c -> c.getCommentText() != null && c.getCommentText().length() >= individualCommentMinLength)
                        .count();
                    
                    commentsComplete = (existingComments >= individualCommentCount);
                }
            }
            
            if (totalIndividualItems > 0 && completedItems >= totalIndividualItems && commentsComplete) {
                studentsEvaluated++;
            }
        }
        
        boolean groupEvaluationComplete = (totalGroupItems == 0 || (groupComplete && groupCommentsComplete));
        boolean allStudentsComplete = (students.isEmpty() || studentsEvaluated >= students.size());
        
        int totalParts = (totalGroupItems > 0 ? 1 : 0) + students.size();
        int completedParts = (groupEvaluationComplete ? 1 : 0) + studentsEvaluated;
        double percentage = totalParts > 0 ? (completedParts * 100.0 / totalParts) : 0.0;
        
        String status;
        if (percentage >= 100.0) {
            status = "COMPLETED";
        } else if (percentage > 0) {
            status = "IN_PROGRESS";
        } else {
            status = "NOT_STARTED";
        }
        
        progress.put("status", status);
        progress.put("percentage", percentage);
        progress.put("groupComplete", groupEvaluationComplete);
        progress.put("studentsEvaluated", studentsEvaluated);
        progress.put("totalStudents", students.size());
        progress.put("completedParts", completedParts);
        progress.put("totalParts", totalParts);
        
        return progress;
    }

    private int countEvaluationItems(List<Rubric> rubrics) {
        int count = 0;
        for (Rubric rubric : rubrics) {
            if (rubric.hasSubRubrics()) {
                count += rubric.getSubRubrics().size();
            } else if (rubric.hasDirectRatings()) {
                count += 1;
            }
        }
        return count;
    }
    
    private int countCompletedItems(
            Long supervisorId, Long studentId, Long assessmentId, List<Rubric> rubrics) {
        
        List<Mark> marks = markRepository.findByEvaluatorStudentIdAndEvaluatedStudentIdAndAssessmentId(
            studentId, studentId, assessmentId);
        
        marks = marks.stream()
            .filter(m -> m.getSupervisorId() != null && m.getSupervisorId().equals(supervisorId))
            .collect(Collectors.toList());
        
        Set<Long> completedRubrics = new HashSet<>();
        Set<Long> completedSubRubrics = new HashSet<>();
        
        for (Mark mark : marks) {
            if (mark.getSubRubric() != null) {
                completedSubRubrics.add(mark.getSubRubric().getId());
            } else if (mark.getRubric() != null) {
                completedRubrics.add(mark.getRubric().getId());
            }
        }
        
        int completed = 0;
        for (Rubric rubric : rubrics) {
            if (rubric.hasSubRubrics()) {
                for (SubRubric sr : rubric.getSubRubrics()) {
                    if (completedSubRubrics.contains(sr.getId())) {
                        completed++;
                    }
                }
            } else if (rubric.hasDirectRatings()) {
                if (completedRubrics.contains(rubric.getId())) {
                    completed++;
                }
            }
        }
        
        return completed;
    }
    
    @Transactional
    public void saveContinuousEvaluation(
            IndustrialSupervisor supervisor,
            Group group,
            Assessment assessment,
            Map<String, String> formData) {
        
        List<Student> students = group.getStudents();
        if (students.isEmpty()) {
            throw new RuntimeException("No students in group");
        }
        
        List<Rubric> groupRubrics = assessment.getRubrics().stream()
            .filter(r -> "Group Assessment".equals(r.getAssessmentTypes()))
            .collect(Collectors.toList());
        
        List<Rubric> individualRubrics = assessment.getRubrics().stream()
            .filter(r -> "Individual Assessment".equals(r.getAssessmentTypes()))
            .collect(Collectors.toList());
        
        Map<Long, Long> groupRubricRatings = extractRatings(formData, "group_rubric");
        Map<Long, Long> groupSubRubricRatings = extractRatings(formData, "group_subrubric");
        List<String> groupComments = extractComments(formData, "group_comment", 
            assessment.getGroupCommentCount());
        
        Map<Long, Map<Integer, String>> groupRubricComments = extractRubricComments(
            formData, "group", groupRubrics);
        
        if (!groupRubricRatings.isEmpty() || !groupSubRubricRatings.isEmpty()) {
            for (Student student : students) {
                saveMarksForStudent(supervisor, student, student, assessment, 
                    groupRubricRatings, groupSubRubricRatings, "Group Assessment", true);
            }
        }
        
        boolean hasGroupComments = groupComments.stream().anyMatch(c -> c != null && !c.trim().isEmpty());
        if (hasGroupComments) {
            for (Student student : students) {
                saveCommentsForStudent(supervisor, student, assessment, 
                    groupComments, "Group Assessment");
            }
        }
        
        if (!groupRubricComments.isEmpty()) {
            for (Student student : students) {
                saveRubricCommentsForStudent(supervisor, student, assessment, 
                    groupRubricComments, "Group Assessment");
            }
        }
        
        for (Student student : students) {
            String prefix = "student_" + student.getId();
            
            Map<Long, Long> studentRubricRatings = extractRatings(formData, prefix + "_rubric");
            Map<Long, Long> studentSubRubricRatings = extractRatings(formData, prefix + "_subrubric");
            List<String> studentComments = extractComments(formData, prefix + "_comment", 
                assessment.getIndividualCommentCount());
            
            Map<Long, Map<Integer, String>> studentRubricComments = extractRubricComments(
                formData, prefix, individualRubrics);
            
            if (!studentRubricRatings.isEmpty() || !studentSubRubricRatings.isEmpty()) {
                saveMarksForStudent(supervisor, student, student, assessment, 
                    studentRubricRatings, studentSubRubricRatings, "Individual Assessment", true);
            }
            
            boolean hasIndividualComments = studentComments.stream().anyMatch(c -> c != null && !c.trim().isEmpty());
            if (hasIndividualComments) {
                saveCommentsForStudent(supervisor, student, assessment, 
                    studentComments, "Individual Assessment");
            }
            
            if (!studentRubricComments.isEmpty()) {
                saveRubricCommentsForStudent(supervisor, student, assessment, 
                    studentRubricComments, "Individual Assessment");
            }
        }
    }
    
    private Map<Long, Long> extractRatings(Map<String, String> formData, String prefix) {
        Map<Long, Long> ratings = new HashMap<>();
        
        for (Map.Entry<String, String> entry : formData.entrySet()) {
            String key = entry.getKey();
            if (key.startsWith(prefix + "[") && key.endsWith("]")) {
                try {
                    Long id = Long.parseLong(key.substring(
                        key.indexOf("[") + 1, key.indexOf("]")));
                    Long ratingId = Long.parseLong(entry.getValue());
                    ratings.put(id, ratingId);
                } catch (NumberFormatException e) {
                
                }
            }
        }
        
        return ratings;
    }
    
    private List<String> extractComments(Map<String, String> formData, String prefix, int count) {
        List<String> comments = new ArrayList<>();
        for (int i = 0; i < count; i++) {
            String key = prefix + "[" + i + "]";
            comments.add(formData.getOrDefault(key, ""));
        }
        return comments;
    }
    
    private Map<Long, Map<Integer, String>> extractRubricComments(
            Map<String, String> formData, String prefix, List<Rubric> rubrics) {
        
        Map<Long, Map<Integer, String>> rubricCommentsMap = new HashMap<>();
        
        for (Rubric rubric : rubrics) {
            Integer commentCount = rubric.getRubricCommentCount();
            if (commentCount == null || commentCount == 0) {
                continue;
            }
            
            Map<Integer, String> commentsForRubric = new HashMap<>();
            
            for (int i = 0; i < commentCount; i++) {
                String key = prefix + "_rubric_" + rubric.getId() + "_comment_" + i;
                String commentText = formData.getOrDefault(key, "");
                
                if (commentText != null && !commentText.trim().isEmpty()) {
                    commentsForRubric.put(i, commentText);
                }
            }
            
            if (!commentsForRubric.isEmpty()) {
                rubricCommentsMap.put(rubric.getId(), commentsForRubric);
            }
        }
        
        return rubricCommentsMap;
    }
    
    @Transactional
    private void saveMarksForStudent(
            IndustrialSupervisor supervisor,
            Student evaluatorStudent,
            Student evaluatedStudent,
            Assessment assessment,
            Map<Long, Long> rubricRatings,
            Map<Long, Long> subRubricRatings,
            String assessmentType,
            boolean isSupervisorMark) {
        
        List<Mark> existingMarks = markRepository.findByEvaluatorStudentAndEvaluatedStudentAndAssessment(
            evaluatorStudent, evaluatedStudent, assessment);
        
        existingMarks = existingMarks.stream()
            .filter(m -> m.getSupervisorId() != null && m.getSupervisorId().equals(supervisor.getId()))
            .filter(m -> assessmentType.equals(m.getAssessmentType()))
            .collect(Collectors.toList());
        
        markRepository.deleteAll(existingMarks);
        
        List<Mark> newMarks = new ArrayList<>();
        
        for (Map.Entry<Long, Long> entry : rubricRatings.entrySet()) {
            Long rubricId = entry.getKey();
            Long ratingId = entry.getValue();
            
            Rating rating = ratingRepository.findById(ratingId).orElse(null);
            if (rating == null) continue;
            
            Mark mark = new Mark();
            mark.setEvaluatorStudent(evaluatorStudent);
            mark.setEvaluatedStudent(evaluatedStudent);
            mark.setAssessment(assessment);
            mark.setRubric(rating.getRubric());
            mark.setRating(rating);
            mark.setAssessmentType(assessmentType);
            mark.setStatus(Mark.SubmissionStatus.SUBMITTED);
            mark.setSupervisorId(supervisor.getId());
            mark.setIsSupervisorMark(isSupervisorMark);
            
            newMarks.add(mark);
        }
        
        for (Map.Entry<Long, Long> entry : subRubricRatings.entrySet()) {
            Long subRubricId = entry.getKey();
            Long ratingId = entry.getValue();
            
            Rating rating = ratingRepository.findById(ratingId).orElse(null);
            if (rating == null) continue;
            
            Mark mark = new Mark();
            mark.setEvaluatorStudent(evaluatorStudent);
            mark.setEvaluatedStudent(evaluatedStudent);
            mark.setAssessment(assessment);
            mark.setSubRubric(rating.getSubRubric());
            mark.setRubric(rating.getSubRubric().getRubric());
            mark.setRating(rating);
            mark.setAssessmentType(assessmentType);
            mark.setStatus(Mark.SubmissionStatus.SUBMITTED);
            mark.setSupervisorId(supervisor.getId());
            mark.setIsSupervisorMark(isSupervisorMark);
            
            newMarks.add(mark);
        }
        
        markRepository.saveAll(newMarks);
    }
    
    @Transactional
    private void saveCommentsForStudent(
            IndustrialSupervisor supervisor,
            Student student,
            Assessment assessment,
            List<String> comments,
            String rubricAssessmentType) {
        
        List<String> commentLabels = "Group Assessment".equals(rubricAssessmentType) ?
            assessment.getGroupCommentLabels() :
            assessment.getIndividualCommentLabels();
        
        if (commentLabels == null || commentLabels.isEmpty()) {
            return;
        }
        
        List<AssessmentComment> newComments = new ArrayList<>();
        
        for (int i = 0; i < comments.size(); i++) {
            String commentText = comments.get(i);
            if (commentText == null || commentText.trim().isEmpty()) continue;
            
            AssessmentComment comment = new AssessmentComment();
            comment.setEvaluatedStudent(student);
            comment.setEvaluatorId(supervisor.getId());
            comment.setEvaluatorType(AssessmentComment.EvaluatorType.SUPERVISOR);
            comment.setEvaluatorName(supervisor.getUsername());
            comment.setAssessment(assessment);
            comment.setCommentText(commentText);
            comment.setAssessmentType(AssessmentComment.CommentAssessmentType.SUPERVISOR_EVALUATION);
            comment.setCommentIndex(i);
            comment.setRubricAssessmentType(rubricAssessmentType);
            comment.setRubricId(null);
            
            if ("Group Assessment".equals(rubricAssessmentType)) {
                comment.setCommentLabel(assessment.getGroupCommentLabel(i));
                
                Boolean isAnonymous = assessment.isGroupCommentAnonymous(i);
                if (isAnonymous != null && isAnonymous) {
                    comment.setAnonymousIdentifier("Supervisor");
                } else {
                    comment.setAnonymousIdentifier(supervisor.getUsername());
                }
            } else {
                comment.setCommentLabel(assessment.getIndividualCommentLabel(i));
                
                Boolean isAnonymous = assessment.isIndividualCommentAnonymous(i);
                if (isAnonymous != null && isAnonymous) {
                    comment.setAnonymousIdentifier("Supervisor");
                } else {
                    comment.setAnonymousIdentifier(supervisor.getUsername());
                }
            }
            
            newComments.add(comment);
        }
        
        if (!newComments.isEmpty()) {
            List<AssessmentComment> existingComments = commentRepository
                .findSupervisorCommentsForStudent(supervisor.getId(), student.getId(), assessment.getId())
                .stream()
                .filter(c -> rubricAssessmentType.equals(c.getRubricAssessmentType()))
                .filter(c -> c.getRubricId() == null)
                .collect(Collectors.toList());
            
            if (!existingComments.isEmpty()) {
                commentRepository.deleteAll(existingComments);
            }
            
            commentRepository.saveAll(newComments);
        }
    }
    
    @Transactional
    private void saveRubricCommentsForStudent(
            IndustrialSupervisor supervisor,
            Student student,
            Assessment assessment,
            Map<Long, Map<Integer, String>> rubricComments,
            String rubricAssessmentType) {
        
        for (Map.Entry<Long, Map<Integer, String>> entry : rubricComments.entrySet()) {
            Long rubricId = entry.getKey();
            Map<Integer, String> commentsForRubric = entry.getValue();
            
            List<AssessmentComment> existingComments = commentRepository
                .findSupervisorCommentsForStudent(supervisor.getId(), student.getId(), assessment.getId())
                .stream()
                .filter(c -> rubricId.equals(c.getRubricId()))
                .collect(Collectors.toList());
            
            if (!existingComments.isEmpty()) {
                commentRepository.deleteAll(existingComments);
            }
            
            Rubric rubric = assessment.getRubrics().stream()
                .filter(r -> r.getId().equals(rubricId))
                .findFirst()
                .orElse(null);
            
            if (rubric == null) {
                continue;
            }
            
            List<AssessmentComment> newComments = new ArrayList<>();
            
            for (Map.Entry<Integer, String> commentEntry : commentsForRubric.entrySet()) {
                Integer commentIndex = commentEntry.getKey();
                String commentText = commentEntry.getValue();
                
                if (commentText == null || commentText.trim().isEmpty()) {
                    continue;
                }
                
                AssessmentComment comment = new AssessmentComment();
                comment.setEvaluatedStudent(student);
                comment.setEvaluatorId(supervisor.getId());
                comment.setEvaluatorType(AssessmentComment.EvaluatorType.SUPERVISOR);
                comment.setEvaluatorName(supervisor.getUsername());
                comment.setAssessment(assessment);
                comment.setCommentText(commentText.trim());
                comment.setAssessmentType(AssessmentComment.CommentAssessmentType.SUPERVISOR_EVALUATION);
                comment.setCommentIndex(commentIndex);
                comment.setRubricAssessmentType(rubricAssessmentType);
                comment.setRubricId(rubricId);
                comment.setCommentLabel(rubric.getRubricCommentLabel(commentIndex));
                
                Boolean isAnonymous = rubric.isRubricCommentAnonymous(commentIndex);
                if (isAnonymous != null && isAnonymous) {
                    comment.setAnonymousIdentifier("Supervisor");
                } else {
                    comment.setAnonymousIdentifier(supervisor.getUsername());
                }
                
                newComments.add(comment);
            }
            
            if (!newComments.isEmpty()) {
                commentRepository.saveAll(newComments);
            }
        }
    }
    
    public Map<String, Object> loadExistingEvaluation(
            IndustrialSupervisor supervisor, Group group, Assessment assessment) {
        
        Map<String, Object> data = new HashMap<>();
        
        List<Student> students = group.getStudents();
        if (students.isEmpty()) {
            return data;
        }
        
        Student firstStudent = students.get(0);
        
        List<Mark> groupMarks = markRepository
            .findByEvaluatorStudentAndEvaluatedStudentAndAssessment(firstStudent, firstStudent, assessment)
            .stream()
            .filter(m -> m.getSupervisorId() != null && m.getSupervisorId().equals(supervisor.getId()))
            .filter(m -> "Group Assessment".equals(m.getAssessmentType()))
            .collect(Collectors.toList());
        
        Map<Long, Long> groupRubricRatings = new HashMap<>();
        Map<Long, Long> groupSubRubricRatings = new HashMap<>();
        
        for (Mark mark : groupMarks) {
            if (mark.getSubRubric() != null) {
                groupSubRubricRatings.put(mark.getSubRubric().getId(), mark.getRating().getId());
            } else if (mark.getRubric() != null) {
                groupRubricRatings.put(mark.getRubric().getId(), mark.getRating().getId());
            }
        }
        
        data.put("groupRubricRatings", groupRubricRatings);
        data.put("groupSubRubricRatings", groupSubRubricRatings);
        
        List<AssessmentComment> groupComments = commentRepository
            .findSupervisorCommentsForStudent(supervisor.getId(), firstStudent.getId(), assessment.getId())
            .stream()
            .filter(c -> "Group Assessment".equals(c.getRubricAssessmentType()))
            .filter(c -> c.getRubricId() == null) // Only general comments
            .sorted((a, b) -> Integer.compare(a.getCommentIndex(), b.getCommentIndex()))
            .collect(Collectors.toList());
        
        List<String> groupCommentTexts = groupComments.stream()
            .map(AssessmentComment::getCommentText)
            .collect(Collectors.toList());
        
        data.put("groupComments", groupCommentTexts);
        
        Map<Long, Map<Integer, String>> groupRubricComments = new HashMap<>();
        List<AssessmentComment> allGroupComments = commentRepository
            .findSupervisorCommentsForStudent(supervisor.getId(), firstStudent.getId(), assessment.getId())
            .stream()
            .filter(c -> "Group Assessment".equals(c.getRubricAssessmentType()))
            .filter(c -> c.getRubricId() != null) 
            .collect(Collectors.toList());
        
        for (AssessmentComment comment : allGroupComments) {
            Long rubricId = comment.getRubricId();
            groupRubricComments.putIfAbsent(rubricId, new HashMap<>());
            groupRubricComments.get(rubricId).put(comment.getCommentIndex(), comment.getCommentText());
        }
        
        data.put("groupRubricComments", groupRubricComments);
        
        Map<Long, Map<String, Object>> studentData = new HashMap<>();
        
        for (Student student : students) {
            Map<String, Object> sData = new HashMap<>();
            
            List<Mark> studentMarks = markRepository
                .findByEvaluatedStudentAndAssessment(student, assessment)
                .stream()
                .filter(m -> m.getSupervisorId() != null && m.getSupervisorId().equals(supervisor.getId()))
                .filter(m -> "Individual Assessment".equals(m.getAssessmentType()))
                .collect(Collectors.toList());
            
            Map<Long, Long> studentRubricRatings = new HashMap<>();
            Map<Long, Long> studentSubRubricRatings = new HashMap<>();
            
            for (Mark mark : studentMarks) {
                if (mark.getSubRubric() != null) {
                    studentSubRubricRatings.put(mark.getSubRubric().getId(), mark.getRating().getId());
                } else if (mark.getRubric() != null) {
                    studentRubricRatings.put(mark.getRubric().getId(), mark.getRating().getId());
                }
            }
            
            sData.put("rubricRatings", studentRubricRatings);
            sData.put("subRubricRatings", studentSubRubricRatings);
            
            List<AssessmentComment> studentComments = commentRepository
                .findSupervisorCommentsForStudent(supervisor.getId(), student.getId(), assessment.getId())
                .stream()
                .filter(c -> "Individual Assessment".equals(c.getRubricAssessmentType()))
                .filter(c -> c.getRubricId() == null) 
                .sorted((a, b) -> Integer.compare(a.getCommentIndex(), b.getCommentIndex()))
                .collect(Collectors.toList());
            
            List<String> studentCommentTexts = studentComments.stream()
                .map(AssessmentComment::getCommentText)
                .collect(Collectors.toList());
            
            sData.put("comments", studentCommentTexts);
            
            Map<Long, Map<Integer, String>> studentRubricComments = new HashMap<>();
            List<AssessmentComment> allStudentComments = commentRepository
                .findSupervisorCommentsForStudent(supervisor.getId(), student.getId(), assessment.getId())
                .stream()
                .filter(c -> "Individual Assessment".equals(c.getRubricAssessmentType()))
                .filter(c -> c.getRubricId() != null) 
                .collect(Collectors.toList());
            
            for (AssessmentComment comment : allStudentComments) {
                Long rubricId = comment.getRubricId();
                studentRubricComments.putIfAbsent(rubricId, new HashMap<>());
                studentRubricComments.get(rubricId).put(comment.getCommentIndex(), comment.getCommentText());
            }
            
            sData.put("rubricComments", studentRubricComments);
            
            studentData.put(student.getId(), sData);
        }
        
        data.put("studentData", studentData);
        
        return data;
    }
}

*/