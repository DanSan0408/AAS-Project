package com.capstone.adproject.service;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.capstone.adproject.model.Assessment;
import com.capstone.adproject.dto.ai.AiCommentDtos;
import com.capstone.adproject.model.AssessmentComment;
import com.capstone.adproject.model.Rubric;
import com.capstone.adproject.model.Student;
import com.capstone.adproject.model.StudentCourseAssignment;
import com.capstone.adproject.repositories.AssessmentCommentRepository;
import com.capstone.adproject.repositories.StudentCourseAssignmentRepository;
import com.capstone.adproject.repositories.StudentRepository;
import java.time.LocalDateTime;

import com.capstone.adproject.util.HtmlSanitizerUtil;

@Service
public class AssessmentCommentService {

    private static final Logger logger = LoggerFactory.getLogger(AssessmentCommentService.class);
    
    @Autowired
    private AssessmentCommentRepository commentRepository;
    
    @Autowired
    private StudentRepository studentRepository;

    @Autowired(required = false)
    private StudentCourseAssignmentRepository studentCourseAssignmentRepository;

    @Autowired(required = false)
    private AiServiceClient aiServiceClient;
    
    @Transactional
    public AssessmentComment saveComment(AssessmentComment comment) {
        if (comment.getCommentText() != null) {
            comment.setCommentText(HtmlSanitizerUtil.sanitizePlainText(comment.getCommentText()));
        }
        if (comment.getIsApproved() == null) {
            comment.setIsApproved(false);
        }
        if (comment.getApprovalStatus() == null) {
            comment.setApprovalStatus(AssessmentComment.ApprovalStatus.PENDING);
        }
        return commentRepository.save(comment);
    }
    
    public String getAnonymousIdentifier(Student evaluatedStudent, Long evaluatorId, Assessment assessment, String rubricAssessmentType) {

        Boolean isAnonymous = assessment.getCommentsAnonymousForType(rubricAssessmentType);
        if (isAnonymous != null && !isAnonymous) {

            return studentRepository.findById(evaluatorId)
                    .map(Student::getUsername)
                    .orElse("Student");
        }
        
        List<AssessmentComment> peerComments = commentRepository.findPeerCommentsForStudentInAssessment(
                evaluatedStudent, assessment).stream()
                .filter(c -> rubricAssessmentType.equalsIgnoreCase(c.getRubricAssessmentType()))
                .filter(c -> c.getRubricId() == null) 
                .collect(Collectors.toList());
        
        for (AssessmentComment comment : peerComments) {
            if (comment.getEvaluatorId().equals(evaluatorId)) {
                return comment.getAnonymousIdentifier();
            }
        }
        
        long uniqueEvaluators = peerComments.stream()
                .map(AssessmentComment::getEvaluatorId)
                .distinct()
                .count();
        
        int nextTeammateNumber = (int) uniqueEvaluators + 1;
        return "Teammate " + nextTeammateNumber;
    }

    public String getRubricAnonymousIdentifier(Student evaluatedStudent, Long evaluatorId, 
                                               Assessment assessment, Long rubricId, Integer commentIndex) {

        List<AssessmentComment> existingComments = commentRepository.findByEvaluatorAndStudentAndAssessment(
                evaluatorId,
                AssessmentComment.EvaluatorType.STUDENT,
                evaluatedStudent,
                assessment).stream()
                .filter(c -> c.getRubricId() != null && c.getRubricId().equals(rubricId))
                .filter(c -> c.getCommentIndex() != null && c.getCommentIndex().equals(commentIndex))
                .collect(Collectors.toList());

        if (!existingComments.isEmpty()) {
            return existingComments.get(0).getAnonymousIdentifier();
        }
        
        List<AssessmentComment> allRubricComments = commentRepository.findPeerCommentsForStudentInAssessment(
                evaluatedStudent, assessment).stream()
                .filter(c -> c.getRubricId() != null && c.getRubricId().equals(rubricId))
                .filter(c -> c.getCommentIndex() != null && c.getCommentIndex().equals(commentIndex))
                .collect(Collectors.toList());
        
        long uniqueEvaluators = allRubricComments.stream()
                .map(AssessmentComment::getEvaluatorId)
                .distinct()
                .count();
        
        int nextTeammateNumber = (int) uniqueEvaluators + 1;
        return "Teammate " + nextTeammateNumber;
    }

    @Transactional
    public void deleteComments(List<AssessmentComment> comments) {
        commentRepository.deleteAll(comments);
    }
    
    @Transactional
    public List<AssessmentComment> submitPeerComments(Student evaluator, Student evaluatedStudent, 
                                                       Assessment assessment, List<String> commentTexts) {
        
        List<AssessmentComment> savedComments = new ArrayList<>();
        
        String identifier = getAnonymousIdentifier(evaluatedStudent, evaluator.getId(), assessment, "Individual Assessment");
        String evaluatorName = (evaluator.getUsername() != null && !evaluator.getUsername().trim().isEmpty())
                ? evaluator.getUsername()
                : evaluator.getEmail();
        
        List<AssessmentComment> existingComments = commentRepository.findByEvaluatorAndStudentAndAssessment(
                evaluator.getId(),
                AssessmentComment.EvaluatorType.STUDENT,
                evaluatedStudent,
                assessment).stream()
                .filter(c -> c.getRubricAssessmentType() == null || 
                             !"Group Assessment".equalsIgnoreCase(c.getRubricAssessmentType()))
                .filter(c -> c.getRubricId() == null) 
                .collect(Collectors.toList());
        
        if (!existingComments.isEmpty()) {
            commentRepository.deleteAll(existingComments);
        }
        
        for (int i = 0; i < commentTexts.size(); i++) {
            String commentText = commentTexts.get(i);
            
            if (commentText == null || commentText.trim().isEmpty()) {
                continue;
            }
            
            AssessmentComment comment = new AssessmentComment();
            comment.setEvaluatorId(evaluator.getId());
            comment.setEvaluatorType(AssessmentComment.EvaluatorType.STUDENT);
            comment.setEvaluatorName(evaluatorName);
            comment.setEvaluatedStudent(evaluatedStudent);
            comment.setAssessment(assessment);
            comment.setCommentText(HtmlSanitizerUtil.sanitizePlainText(commentText.trim()));
            comment.setAssessmentType(AssessmentComment.CommentAssessmentType.PEER);
            comment.setAnonymousIdentifier(identifier);
            comment.setCommentIndex(i);
            comment.setCommentLabel(assessment.getIndividualCommentLabel(i));
            comment.setRubricAssessmentType("Individual Assessment");
            
            savedComments.add(commentRepository.save(comment));
        }
        
        return savedComments;
    }
    
    @Transactional
    public List<AssessmentComment> submitSelfComments(Student student, Assessment assessment, List<String> commentTexts) {
        
        List<AssessmentComment> savedComments = new ArrayList<>();
        
        List<AssessmentComment> existingComments = commentRepository.findSelfCommentsForStudentInAssessment(
                student, assessment, student.getId()).stream()
                .filter(c -> c.getRubricAssessmentType() == null || 
                             !"Group Assessment".equalsIgnoreCase(c.getRubricAssessmentType()))
                .filter(c -> c.getRubricId() == null) 
                .collect(Collectors.toList());
        
        if (!existingComments.isEmpty()) {
            commentRepository.deleteAll(existingComments);
        }
        
        for (int i = 0; i < commentTexts.size(); i++) {
            String commentText = commentTexts.get(i);
            
            if (commentText == null || commentText.trim().isEmpty()) {
                continue; 
            }
            
            AssessmentComment comment = new AssessmentComment();
            comment.setEvaluatorId(student.getId());
            comment.setEvaluatorType(AssessmentComment.EvaluatorType.STUDENT);
            String studentName = (student.getUsername() != null && !student.getUsername().trim().isEmpty())
                    ? student.getUsername()
                    : student.getEmail();
            comment.setEvaluatorName(studentName);
            comment.setEvaluatedStudent(student);
            comment.setAssessment(assessment);
            comment.setCommentText(HtmlSanitizerUtil.sanitizePlainText(commentText.trim()));
            comment.setAssessmentType(AssessmentComment.CommentAssessmentType.SELF);
            comment.setAnonymousIdentifier("You (Self)");
            comment.setCommentIndex(i);
            comment.setCommentLabel(assessment.getIndividualCommentLabel(i));
            comment.setRubricAssessmentType("Individual Assessment");
            
            savedComments.add(commentRepository.save(comment));
        }
        
        return savedComments;
    }
    
    @Transactional
    public List<AssessmentComment> submitTeamComments(Student student, Assessment assessment, List<String> commentTexts) {
        
        List<AssessmentComment> savedComments = new ArrayList<>();
        
        List<AssessmentComment> existingComments = commentRepository.findByEvaluatorAndStudentAndAssessment(
                student.getId(),
                AssessmentComment.EvaluatorType.STUDENT,
                student,
                assessment).stream()
                .filter(c -> "Group Assessment".equalsIgnoreCase(c.getRubricAssessmentType()))
                .filter(c -> c.getRubricId() == null) 
                .collect(Collectors.toList());
        
        if (!existingComments.isEmpty()) {
            commentRepository.deleteAll(existingComments);
        }
        
        for (int i = 0; i < commentTexts.size(); i++) {
            String commentText = commentTexts.get(i);
            
            if (commentText == null || commentText.trim().isEmpty()) {
                continue;
            }
            
            AssessmentComment comment = new AssessmentComment();
            comment.setEvaluatorId(student.getId());
            comment.setEvaluatorType(AssessmentComment.EvaluatorType.STUDENT);
            String studentName = (student.getUsername() != null && !student.getUsername().trim().isEmpty())
                    ? student.getUsername()
                    : student.getEmail();
            comment.setEvaluatorName(studentName);
            comment.setEvaluatedStudent(student);
            comment.setAssessment(assessment);
            comment.setCommentText(HtmlSanitizerUtil.sanitizePlainText(commentText.trim()));
            comment.setAssessmentType(AssessmentComment.CommentAssessmentType.TEAM);
            comment.setAnonymousIdentifier("You (Team Evaluation)");
            comment.setCommentIndex(i);
            comment.setCommentLabel(assessment.getGroupCommentLabel(i));
            comment.setRubricAssessmentType("Group Assessment");
            
            savedComments.add(commentRepository.save(comment));
        }
        
        return savedComments;
    }

    @Transactional
    public List<AssessmentComment> submitTeamComments(Student evaluator, Student evaluatedStudent,
                                                       Assessment assessment, List<String> commentTexts) {
        List<AssessmentComment> savedComments = new ArrayList<>();

        List<AssessmentComment> existingComments = commentRepository.findByEvaluatorAndStudentAndAssessment(
                evaluator.getId(),
                AssessmentComment.EvaluatorType.STUDENT,
                evaluatedStudent,
                assessment).stream()
                .filter(c -> "Group Assessment".equalsIgnoreCase(c.getRubricAssessmentType()))
                .filter(c -> c.getRubricId() == null)
                .collect(Collectors.toList());

        if (!existingComments.isEmpty()) {
            commentRepository.deleteAll(existingComments);
        }

        for (int i = 0; i < commentTexts.size(); i++) {
            String commentText = commentTexts.get(i);
            if (commentText == null || commentText.trim().isEmpty()) {
                continue;
            }

            AssessmentComment comment = new AssessmentComment();
            comment.setEvaluatorId(evaluator.getId());
            comment.setEvaluatorType(AssessmentComment.EvaluatorType.STUDENT);
            comment.setEvaluatorName(evaluator.getUsername() != null
                && !evaluator.getUsername().trim().isEmpty()
                    ? evaluator.getUsername()
                    : evaluator.getEmail());
            comment.setEvaluatedStudent(evaluatedStudent);
            comment.setAssessment(assessment);
            comment.setCommentText(HtmlSanitizerUtil.sanitizePlainText(commentText.trim()));
            comment.setAssessmentType(AssessmentComment.CommentAssessmentType.TEAM);
            comment.setAnonymousIdentifier("You (Team Evaluation)");
            comment.setCommentIndex(i);
            comment.setCommentLabel(assessment.getGroupCommentLabel(i));
            comment.setRubricAssessmentType("Group Assessment");
            savedComments.add(commentRepository.save(comment));
        }

        return savedComments;
    }
    
    @Transactional
    public AssessmentComment submitLecturerComment(Long lecturerId, String lecturerName,
                                                     Student evaluatedStudent, Assessment assessment,
                                                     String commentText, String rubricType) {
        
        List<AssessmentComment> existing = commentRepository.findByEvaluatorAndStudentAndAssessment(
                lecturerId,
                AssessmentComment.EvaluatorType.LECTURER,
                evaluatedStudent,
                assessment).stream()
                .filter(c -> rubricType.equalsIgnoreCase(c.getRubricAssessmentType()))
                .filter(c -> c.getRubricId() == null) 
                .collect(Collectors.toList());
        
        AssessmentComment comment;
        if (!existing.isEmpty()) {
            comment = existing.get(0);
            comment.setCommentText(HtmlSanitizerUtil.sanitizePlainText(commentText));
        } else {
            comment = new AssessmentComment();
            comment.setEvaluatorId(lecturerId);
            comment.setEvaluatorType(AssessmentComment.EvaluatorType.LECTURER);
            comment.setEvaluatorName(lecturerName);
            comment.setEvaluatedStudent(evaluatedStudent);
            comment.setAssessment(assessment);
            comment.setCommentText(HtmlSanitizerUtil.sanitizePlainText(commentText));
            comment.setAssessmentType(AssessmentComment.CommentAssessmentType.LECTURER_EVALUATION);
            comment.setCommentIndex(0);
            comment.setRubricAssessmentType(rubricType);
            comment.setAnonymousIdentifier(lecturerName); 
        }
        
        return commentRepository.save(comment);
    }
    
    // Supervisor comments are now handled as lecturer comments since industrial supervisors are removed
    // Use submitLecturerComment method instead for supervisor comments
    
    public Map<String, Object> getCompiledCommentsForStudent(Student student) {
        return getCompiledCommentsForStudent(student, null);
    }

    public Map<String, Object> getCompiledCommentsForStudent(Student student, Long courseId) {
    List<Student> studentRecords = new ArrayList<>();
    java.util.Set<Long> seenStudentIds = new java.util.HashSet<>();
    if (student != null) {
        studentRecords.add(student);
        if (student.getId() != null) {
            seenStudentIds.add(student.getId());
        }
        if (student.getEmail() != null && !student.getEmail().isBlank() && studentRepository != null) {
            List<Student> matches = studentRepository.findAllByEmailIgnoreCase(student.getEmail());
            if (matches != null) {
                for (Student match : matches) {
                    if (match != null && (match.getId() == null || seenStudentIds.add(match.getId()))) {
                        studentRecords.add(match);
                    }
                }
            }
        }
    }

    List<AssessmentComment> rawComments = new ArrayList<>();
    java.util.Set<Long> seenCommentIds = new java.util.HashSet<>();
    for (Student record : studentRecords) {
        List<AssessmentComment> found = commentRepository.findByEvaluatedStudent(record);
        if (found == null) continue;
        for (AssessmentComment c : found) {
            if (c != null && (c.getId() == null || seenCommentIds.add(c.getId()))) {
                rawComments.add(c);
            }
        }
    }

    List<AssessmentComment> allComments = rawComments
        .stream()
        .filter(AssessmentComment::isApproved)
        .filter(c -> courseId == null || (c.getAssessment() != null
                && c.getAssessment().getCourse() != null
                && courseId.equals(c.getAssessment().getCourse().getId())))
        .collect(Collectors.toList());
    
    System.out.println("===== FETCHING COMMENTS FOR STUDENT =====");
    System.out.println("Student: " + student.getUsername());
    System.out.println("Total comments found: " + allComments.size());
    
    int rubricComments = 0;
    int generalComments = 0;
    
    int studentRubricComments = 0;
    int lecturerRubricComments = 0;
    
    for (AssessmentComment c : allComments) {
        if (c.getRubricId() != null) {
            rubricComments++;
            System.out.println("  - Rubric comment: rubricId=" + c.getRubricId() + 
                             ", label=" + c.getCommentLabel() +
                             ", evaluatorType=" + c.getEvaluatorType());
            
            if (c.getEvaluatorType() == AssessmentComment.EvaluatorType.STUDENT) {
                studentRubricComments++;
            } else if (c.getEvaluatorType() == AssessmentComment.EvaluatorType.LECTURER) {
                lecturerRubricComments++;
            }
        } else {
            generalComments++;
        }
    }
    
    System.out.println("General comments: " + generalComments);
    System.out.println("Rubric-specific comments: " + rubricComments);
    System.out.println("  - Student rubric comments: " + studentRubricComments);
    System.out.println("  - Lecturer rubric comments: " + lecturerRubricComments);
    System.out.println("=========================================");
    
    for (AssessmentComment comment : allComments) {
        String displayName = determineDisplayName(comment);
        comment.setDisplayName(displayName);
        
        System.out.println("Setting displayName: evaluatorType=" + comment.getEvaluatorType() + 
                         ", rubricId=" + comment.getRubricId() + 
                         ", evaluatorName=" + comment.getEvaluatorName() +
                         ", anonymousIdentifier=" + comment.getAnonymousIdentifier() +
                         ", displayName=" + displayName);
    }
    
    Map<String, Map<String, List<AssessmentComment>>> commentsByAssessment = new LinkedHashMap<>();
    
    for (AssessmentComment comment : allComments) {
        String assessmentTitle = comment.getAssessment().getTitle();
        
        commentsByAssessment.putIfAbsent(assessmentTitle, new LinkedHashMap<>());
        Map<String, List<AssessmentComment>> categoryMap = commentsByAssessment.get(assessmentTitle);
        
        String category;
        if (comment.getEvaluatorType() == AssessmentComment.EvaluatorType.STUDENT) {
            boolean writtenByViewer = comment.getEvaluatorId() != null && seenStudentIds.contains(comment.getEvaluatorId());
            if (comment.getAssessmentType() == AssessmentComment.CommentAssessmentType.SELF) {
                category = "self";
            } else if (comment.getAssessmentType() == AssessmentComment.CommentAssessmentType.TEAM && writtenByViewer) {
                category = "team";
            } else {
                category = "peer";
            }
        } else if (comment.getEvaluatorType() == AssessmentComment.EvaluatorType.LECTURER) {
            category = "lecturer";
        } else if (comment.getEvaluatorType() == AssessmentComment.EvaluatorType.SUPERVISOR) {
            category = "supervisor";
        } else {
            category = "other";
        }
        
        categoryMap.putIfAbsent(category, new ArrayList<>());
        categoryMap.get(category).add(comment);
    }
    
    for (Map<String, List<AssessmentComment>> categoryMap : commentsByAssessment.values()) {
        for (List<AssessmentComment> comments : categoryMap.values()) {
            comments.sort((c1, c2) -> {
                if (c1.getRubricId() == null && c2.getRubricId() != null) return -1;
                if (c1.getRubricId() != null && c2.getRubricId() == null) return 1;
                if (c1.getRubricId() != null && c2.getRubricId() != null) {
                    int rubricCompare = c1.getRubricId().compareTo(c2.getRubricId());
                    if (rubricCompare != 0) return rubricCompare;
                }
                return Integer.compare(c1.getCommentIndex(), c2.getCommentIndex());
            });
        }
    }
    
    Map<String, Object> result = new HashMap<>();
    result.put("commentsByAssessment", commentsByAssessment);
    result.put("totalComments", allComments.size());
    
    return result;
}

private String determineDisplayName(AssessmentComment comment) {
    if (comment.getEvaluatorType() == AssessmentComment.EvaluatorType.STUDENT) {
        // Student comments: check assessment-level anonymity
        Assessment assessment = comment.getAssessment();
        if (assessment != null) {
            Boolean isAnonymous = assessment.getCommentsAnonymousForType(comment.getRubricAssessmentType());
            if (isAnonymous != null && !isAnonymous) {
                return comment.getEvaluatorName() != null ? comment.getEvaluatorName() : "Student";
            }
        }
        String anon = comment.getAnonymousIdentifier();
        if (comment.getAssessmentType() == AssessmentComment.CommentAssessmentType.TEAM
                && comment.getEvaluatedStudent() != null
                && comment.getEvaluatorId() != null
                && !comment.getEvaluatorId().equals(comment.getEvaluatedStudent().getId())
                && "You (Team Evaluation)".equals(anon)) {
            return "Teammate";
        }
        return anon != null ? anon : "Teammate";
        
    } else if (comment.getEvaluatorType() == AssessmentComment.EvaluatorType.LECTURER) {
        
        String anonymousId = comment.getAnonymousIdentifier();
        String evaluatorName = comment.getEvaluatorName();
        
        System.out.println("  -> Checking anonymity: anonymousId='" + anonymousId + 
                         "', evaluatorName='" + evaluatorName + "'");
        
        if ("Lecturer".equals(anonymousId)) {
            System.out.println("  -> Anonymous comment detected, using: " + anonymousId);
            return anonymousId;
        }
        
        if (comment.getRubricId() != null) {
            Assessment assessment = comment.getAssessment();
            if (assessment != null && assessment.getRubrics() != null) {
                Rubric rubric = assessment.getRubrics().stream()
                    .filter(r -> r.getId().equals(comment.getRubricId()))
                    .findFirst()
                    .orElse(null);
                
                if (rubric != null) {
                    Integer commentIndex = comment.getCommentIndex();
                    Boolean isAnonymous = rubric.isRubricCommentAnonymous(commentIndex != null ? commentIndex : 0);
                    
                    System.out.println("  -> Rubric comment anonymity check: rubricId=" + comment.getRubricId() + 
                                     ", commentIndex=" + commentIndex + ", isAnonymous=" + isAnonymous);
                    
                    if (isAnonymous != null && isAnonymous) {
                        return "Lecturer";
                    }
                }
            }
        }
        
        System.out.println("  -> Non-anonymous comment, using: " + evaluatorName);
        return evaluatorName != null ? evaluatorName : "Lecturer";
    }
    
    return comment.getEvaluatorName() != null ? comment.getEvaluatorName() : "Evaluator";
}

public List<AssessmentComment> getExistingComments(Long evaluatorId, 
                                                    AssessmentComment.EvaluatorType evaluatorType,
                                                    Student evaluatedStudent, 
                                                    Assessment assessment) {
    return commentRepository.findByEvaluatorAndStudentAndAssessment(
            evaluatorId, evaluatorType, evaluatedStudent, assessment);
}
    
    public boolean hasSubmittedAllComments(Student evaluator, Assessment assessment, List<Student> teamMembers) {
        int requiredIndividualCommentCount = assessment.getIndividualCommentCount();
        int requiredGroupCommentCount = assessment.getGroupCommentCount();
        
        for (Student member : teamMembers) {
            List<AssessmentComment> comments = commentRepository.findByEvaluatorAndStudentAndAssessment(
                    evaluator.getId(),
                    AssessmentComment.EvaluatorType.STUDENT,
                    member,
                    assessment).stream()
                    .filter(c -> c.getRubricAssessmentType() == null || 
                                 !"Group Assessment".equalsIgnoreCase(c.getRubricAssessmentType()))
                    .filter(c -> c.getRubricId() == null) 
                    .collect(Collectors.toList());
            
            if (comments.size() < requiredIndividualCommentCount) {
                return false;
            }
        }
        
        if (requiredGroupCommentCount > 0) {
            List<AssessmentComment> teamComments = commentRepository.findByEvaluatorAndStudentAndAssessment(
                    evaluator.getId(),
                    AssessmentComment.EvaluatorType.STUDENT,
                    evaluator,
                    assessment).stream()
                    .filter(c -> "Group Assessment".equalsIgnoreCase(c.getRubricAssessmentType()))
                    .filter(c -> c.getRubricId() == null) 
                    .collect(Collectors.toList());
            
            if (teamComments.size() < requiredGroupCommentCount) {
                return false;
            }
        }
        
        return true;
    }

    @Transactional
    public boolean approveComment(Long commentId, String adminUsername) {
        return commentRepository.findById(commentId).map(comment -> {
            comment.setBulkDecision(false);
            comment.setIsApproved(true);
            comment.setApprovalStatus(AssessmentComment.ApprovalStatus.APPROVED);
            comment.setApprovedAt(LocalDateTime.now());
            comment.setApprovedBy(adminUsername);
            commentRepository.save(comment);
            return true;
        }).orElse(false);
    }

    @Transactional
    public boolean rejectComment(Long commentId, String adminUsername) {
        return commentRepository.findById(commentId).map(comment -> {
            comment.setBulkDecision(false);
            comment.setIsApproved(false);
            comment.setApprovalStatus(AssessmentComment.ApprovalStatus.REJECTED);
            comment.setApprovedAt(LocalDateTime.now());
            comment.setApprovedBy(adminUsername);
            commentRepository.save(comment);
            return true;
        }).orElse(false);
    }

    @Transactional(readOnly = true)
    public Optional<AssessmentComment> getCommentById(Long commentId) {
        if (commentId == null) {
            return Optional.empty();
        }
        return commentRepository.findById(commentId);
    }

    @Transactional
    public boolean toggleFlag(Long commentId) {
        return commentRepository.findById(commentId).map(comment -> {
            boolean current = Boolean.TRUE.equals(comment.getIsFlaggedNegative());
            comment.setIsFlaggedNegative(!current);
            if (!current && (comment.getFlagReason() == null || comment.getFlagReason().isEmpty())) {
                comment.setFlagReason("Manually flagged by administrator");
            }
            commentRepository.save(comment);
            return true;
        }).orElse(false);
    }

    /** Approves every comment still waiting for a decision. A comment a moderator rejected stays rejected. */
    @Transactional
    public int bulkApproveAllComments(Long assessmentId, String adminUsername) {
        int count = 0;
        for (AssessmentComment comment : commentRepository.findByAssessmentId(assessmentId)) {
            if (comment.getApprovalStatus() == AssessmentComment.ApprovalStatus.PENDING && !comment.isApproved()) {
                markApproved(comment, adminUsername);
                count++;
            }
        }
        return count;
    }

    /**
     * Approves only the pending comments the AI check read and found no concern with, at high confidence, and that have not been
     * edited since. Anything flagged, unclear or not yet checked is left for a person.
     */
    @Transactional
    public int bulkApproveSafeComments(Long assessmentId, String adminUsername) {
        int count = 0;
        for (AssessmentComment comment : commentRepository.findByAssessmentId(assessmentId)) {
            if (isScannedSafe(comment)) {
                markApproved(comment, adminUsername);
                count++;
            }
        }
        return count;
    }

    static boolean isScannedSafe(AssessmentComment c) {
        return c.getApprovalStatus() == AssessmentComment.ApprovalStatus.PENDING && !c.isApproved()
                && "ok".equals(c.getAiLabel()) && c.getAiConfidence() != null && c.getAiConfidence() >= SAFE_CONFIDENCE
                && !Boolean.TRUE.equals(c.getIsFlaggedNegative()) && !needsScan(c);
    }

    private void markApproved(AssessmentComment comment, String adminUsername) {
        comment.setBulkDecision(true);
        comment.setIsApproved(true);
        comment.setApprovalStatus(AssessmentComment.ApprovalStatus.APPROVED);
        comment.setApprovedAt(LocalDateTime.now());
        comment.setApprovedBy(adminUsername);
        commentRepository.save(comment);
    }

    /** How often the AI check and the moderators agreed, over every comment a person has decided on after it was read. */
    @Transactional(readOnly = true)
    public AiAgreement.Report aiAgreementReport() {
        return AiAgreement.compare(commentRepository.findByAiCheckedAtIsNotNullAndApprovalStatusIn(
                List.of(AssessmentComment.ApprovalStatus.APPROVED, AssessmentComment.ApprovalStatus.REJECTED)));
    }

    public long pendingReadByAi() {
        return commentRepository.countByApprovalStatusAndAiCheckedAtIsNotNull(AssessmentComment.ApprovalStatus.PENDING);
    }

    public long pendingNotReadByAi() {
        return commentRepository.countByApprovalStatusAndAiCheckedAtIsNull(AssessmentComment.ApprovalStatus.PENDING);
    }

    // --- AI comment check (advice only) -----------------------------------------------------------------------------

    static final double SAFE_CONFIDENCE = 0.8;
    private static final int SCAN_BATCH = 20;
    private static final int SCAN_LIMIT = 100;

    /** What a scan did: how many comments it read, how many it marked for a look, and what was left. */
    public record ScanResult(int checked, int flagged, int notChecked, int remaining, String aiStatus, List<String> notes) {}

    static String textHash(String text) {
        try {
            byte[] digest = java.security.MessageDigest.getInstance("SHA-256")
                .digest((text == null ? "" : text).getBytes(java.nio.charset.StandardCharsets.UTF_8));
            StringBuilder hex = new StringBuilder();
            for (byte b : digest) hex.append(String.format("%02x", b));
            return hex.toString();
        } catch (java.security.NoSuchAlgorithmException e) {
            throw new IllegalStateException(e);
        }
    }

    /** A comment needs reading by the AI check when it never was, or when its text changed since. */
    static boolean needsScan(AssessmentComment c) {
        return c.getAiCheckedAt() == null || c.getAiTextHash() == null || !c.getAiTextHash().equals(textHash(c.getCommentText()));
    }

    /**
     * Reads the pending comments of an assessment that have not been checked (up to 100 at a time, 20 per request) and records
     * the finding on each. Identical comment texts share one AI verdict rather than paying to scan duplicates repeatedly.
     * The finding is advice: a comment stays pending until a person approves or rejects it.
     */
    @Transactional
    public ScanResult scanPendingComments(Long assessmentId) {
        if (aiServiceClient == null) {
            throw new IllegalStateException("The AI service is not configured.");
        }
        List<AssessmentComment> allComments = commentRepository.findByAssessmentId(assessmentId);
        // Reuse verdicts already recorded on identical text in the same assessment when no manual flag blocks them.
        Map<String, AssessmentComment> alreadyCheckedByHash = new HashMap<>();
        for (AssessmentComment c : allComments) {
            if (!needsScan(c) && c.getAiLabel() != null && !"not_checked".equals(c.getAiLabel())) {
                alreadyCheckedByHash.putIfAbsent(c.getAiTextHash(), c);
            }
        }

        List<AssessmentComment> rawTodo = allComments.stream()
            .filter(c -> c.getApprovalStatus() == AssessmentComment.ApprovalStatus.PENDING && !c.isApproved())
            .filter(c -> c.getCommentText() != null && !c.getCommentText().isBlank())
            .filter(AssessmentCommentService::needsScan)
            .sorted(java.util.Comparator.comparing(AssessmentComment::getId))
            .collect(Collectors.toList());
        int remaining = Math.max(0, rawTodo.size() - SCAN_LIMIT);
        if (rawTodo.size() > SCAN_LIMIT) rawTodo = rawTodo.subList(0, SCAN_LIMIT);

        int checked = 0, flagged = 0, notChecked = 0;
        Map<String, List<AssessmentComment>> todoByHash = new java.util.LinkedHashMap<>();
        for (AssessmentComment c : rawTodo) {
            String hash = textHash(c.getCommentText());
            AssessmentComment cached = alreadyCheckedByHash.get(hash);
            if (cached != null) {
                boolean aiFlagged = cached.getFlagReason() != null
                        ? cached.getFlagReason().startsWith("AI check:")
                        : Boolean.TRUE.equals(cached.getIsFlaggedNegative());
                AiCommentDtos.Verdict reused = new AiCommentDtos.Verdict(
                        String.valueOf(c.getId()),
                        cached.getAiLabel(),
                        cached.getAiConfidence() != null ? cached.getAiConfidence() : 0.0,
                        null,
                        cached.getAiReason(),
                        aiFlagged,
                        aiFlagged,
                        "CACHE");
                applyVerdict(c, reused);
                commentRepository.save(c);
                checked++;
                if (Boolean.TRUE.equals(c.getIsFlaggedNegative())) flagged++;
                continue;
            }
            todoByHash.computeIfAbsent(hash, k -> new java.util.ArrayList<>()).add(c);
        }

        List<AssessmentComment> representatives = todoByHash.values().stream()
                .map(group -> group.get(0))
                .collect(Collectors.toList());

        boolean anyBatchDone = false;
        String worst = "NOT_USED";
        List<String> notes = new java.util.ArrayList<>();
        for (int from = 0; from < representatives.size(); from += SCAN_BATCH) {
            List<AssessmentComment> batch = representatives.subList(from, Math.min(from + SCAN_BATCH, representatives.size()));
            AiCommentDtos.CheckResponse response;
            try {
                response = aiServiceClient.checkComments(new AiCommentDtos.CheckRequest(batch.stream()
                    .map(c -> new AiCommentDtos.Item(String.valueOf(c.getId()), c.getCommentText())).collect(Collectors.toList())));
            } catch (RuntimeException e) {
                if (!anyBatchDone) throw e;     // nothing was read: tell the caller
                for (int j = from; j < representatives.size(); j++) {
                    remaining += todoByHash.get(textHash(representatives.get(j).getCommentText())).size();
                }
                notes.add("The scan stopped early; run it again to continue.");
                break;
            }
            anyBatchDone = true;
            worst = worse(worst, response.aiStatus());
            if (response.aiNotes() != null) notes.addAll(response.aiNotes());
            Map<String, AiCommentDtos.Verdict> byId = new HashMap<>();
            if (response.results() != null) response.results().forEach(v -> byId.put(v.id(), v));
            for (AssessmentComment rep : batch) {
                List<AssessmentComment> group = todoByHash.get(textHash(rep.getCommentText()));
                AiCommentDtos.Verdict verdict = byId.get(String.valueOf(rep.getId()));
                if (verdict == null || "not_checked".equals(verdict.label())) {
                    notChecked += group.size();
                    continue;            // left unchecked, so a later scan tries again
                }
                for (AssessmentComment comment : group) {
                    applyVerdict(comment, verdict);
                    commentRepository.save(comment);
                    checked++;
                    if (Boolean.TRUE.equals(verdict.flagged())) flagged++;
                }
            }
        }
        return new ScanResult(checked, flagged, notChecked, remaining, worst, notes.stream().distinct().collect(Collectors.toList()));
    }

    private static String worse(String a, String b) {
        java.util.List<String> order = List.of("NOT_USED", "OK", "PARTIAL", "UNAVAILABLE");
        return order.indexOf(b == null ? "OK" : b) > order.indexOf(a) ? (b == null ? "OK" : b) : a;
    }

    private void applyVerdict(AssessmentComment comment, AiCommentDtos.Verdict verdict) {
        comment.setAiLabel(verdict.label());
        comment.setAiConfidence(verdict.confidence());
        comment.setAiReason(verdict.reason() == null ? null : (verdict.reason().length() > 300 ? verdict.reason().substring(0, 300) : verdict.reason()));
        comment.setAiCheckedAt(LocalDateTime.now());
        comment.setAiTextHash(textHash(comment.getCommentText()));
        boolean manuallyFlagged = comment.getFlagReason() != null && comment.getFlagReason().startsWith("Manually flagged");
        if (!manuallyFlagged) {           // an admin's own flag is never overwritten by the AI
            boolean flag = Boolean.TRUE.equals(verdict.flagged());
            comment.setIsFlaggedNegative(flag);
            comment.setFlagReason(flag ? "AI check: " + (verdict.reason() == null ? "needs a look" : verdict.reason()) : null);
        }
    }

    @Transactional
    public int bulkApproveComments(List<Long> commentIds, String adminUsername) {
        if (commentIds == null || commentIds.isEmpty()) return 0;
        int count = 0;
        for (Long id : commentIds) {
            if (approveComment(id, adminUsername)) {
                count++;
            }
        }
        return count;
    }

    @Transactional
    public int bulkRejectComments(List<Long> commentIds, String adminUsername) {
        if (commentIds == null || commentIds.isEmpty()) return 0;
        int count = 0;
        for (Long id : commentIds) {
            if (rejectComment(id, adminUsername)) {
                count++;
            }
        }
        return count;
    }

    @Transactional(readOnly = true)
    public Map<String, Long> getModerationStats(Long assessmentId) {
        Map<String, Long> stats = new HashMap<>();
        List<AssessmentComment> comments = commentRepository.findByAssessmentId(assessmentId);
        
        long total = comments.size();
        long pending = 0;
        long approved = 0;
        long rejected = 0;
        long flagged = 0;
        long notScanned = 0;

        for (AssessmentComment c : comments) {
            if (c.getApprovalStatus() == AssessmentComment.ApprovalStatus.APPROVED || Boolean.TRUE.equals(c.getIsApproved())) {
                approved++;
            } else if (c.getApprovalStatus() == AssessmentComment.ApprovalStatus.REJECTED) {
                rejected++;
            } else {
                pending++;
                if (Boolean.TRUE.equals(c.getIsFlaggedNegative())) flagged++;
                if (needsScan(c)) notScanned++;
            }
        }

        stats.put("total", total);
        stats.put("pending", pending);
        stats.put("approved", approved);
        stats.put("rejected", rejected);
        stats.put("flagged", flagged);               // pending comments the AI check (or an admin) marked for a closer look
        stats.put("notScanned", notScanned);         // pending comments the AI check has not read yet (or that were edited since)
        logger.info("Comment moderation totals: assessmentId={}, total={}, pending={}, approved={}, rejected={}",
            assessmentId, total, pending, approved, rejected);
        return stats;
    }

    @Transactional(readOnly = true)
    public List<AssessmentComment> getCommentsForModeration(Long assessmentId, String filter, String roleFilter, Long groupId, String search) {
        List<AssessmentComment> comments = commentRepository.findByAssessmentIdOrderBySubmittedAtDesc(assessmentId);

        List<AssessmentComment> filteredComments = comments.stream()
            .filter(c -> {
                if (filter == null || "all".equalsIgnoreCase(filter)) return true;
                if ("pending".equalsIgnoreCase(filter)) return c.getApprovalStatus() == AssessmentComment.ApprovalStatus.PENDING && !Boolean.TRUE.equals(c.getIsApproved());
                if ("approved".equalsIgnoreCase(filter)) return c.isApproved();
                if ("rejected".equalsIgnoreCase(filter)) return c.getApprovalStatus() == AssessmentComment.ApprovalStatus.REJECTED;
                if ("flagged".equalsIgnoreCase(filter)) return Boolean.TRUE.equals(c.getIsFlaggedNegative()) && !c.isApproved()
                        && c.getApprovalStatus() != AssessmentComment.ApprovalStatus.REJECTED;
                return true;
            })
            .filter(c -> {
                if (roleFilter == null || roleFilter.isEmpty() || "all".equalsIgnoreCase(roleFilter)) return true;
                if ("lecturer".equalsIgnoreCase(roleFilter) || "assessor".equalsIgnoreCase(roleFilter)) return c.getEvaluatorType() == AssessmentComment.EvaluatorType.LECTURER;
                if ("student".equalsIgnoreCase(roleFilter)) return c.getEvaluatorType() == AssessmentComment.EvaluatorType.STUDENT;
                if ("peer".equalsIgnoreCase(roleFilter)) return c.getAssessmentType() == AssessmentComment.CommentAssessmentType.PEER;
                if ("self".equalsIgnoreCase(roleFilter)) return c.getAssessmentType() == AssessmentComment.CommentAssessmentType.SELF;
                if ("team".equalsIgnoreCase(roleFilter)) return c.getAssessmentType() == AssessmentComment.CommentAssessmentType.TEAM;
                return true;
            })
            .filter(c -> {
                if (groupId == null) return true;
                Student s = c.getEvaluatedStudent();
                if (s == null) return false;
                if (studentCourseAssignmentRepository != null && s.getId() != null
                        && c.getAssessment() != null && c.getAssessment().getCourse() != null) {
                    Optional<StudentCourseAssignment> sca = studentCourseAssignmentRepository
                            .findByStudentIdAndCourseId(s.getId(), c.getAssessment().getCourse().getId());
                    if (sca.isPresent() && sca.get().getGroup() != null) {
                        return groupId.equals(sca.get().getGroup().getId());
                    }
                }
                return s.getGroup() != null && groupId.equals(s.getGroup().getId());
            })
            .filter(c -> {
                if (search == null || search.trim().isEmpty()) return true;
                String q = search.trim().toLowerCase();
                String text = c.getCommentText() != null ? c.getCommentText().toLowerCase() : "";
                String studentName = c.getEvaluatedStudent() != null ? (c.getEvaluatedStudent().getUsername() != null ? c.getEvaluatedStudent().getUsername().toLowerCase() : "") : "";
                String studentEmail = c.getEvaluatedStudent() != null ? (c.getEvaluatedStudent().getEmail() != null ? c.getEvaluatedStudent().getEmail().toLowerCase() : "") : "";
                String evalName = c.getEvaluatorName() != null ? c.getEvaluatorName().toLowerCase() : "";
                String anon = c.getAnonymousIdentifier() != null ? c.getAnonymousIdentifier().toLowerCase() : "";
                String label = c.getCommentLabel() != null ? c.getCommentLabel().toLowerCase() : "";
                return text.contains(q) || studentName.contains(q) || studentEmail.contains(q) || evalName.contains(q) || anon.contains(q) || label.contains(q);
            })
            .collect(Collectors.toList());
        logger.info("Comment moderation retrieval: assessmentId={}, loaded={}, displayed={}, statusFilter={}, roleFilter={}, groupId={}, searchApplied={}",
            assessmentId, comments.size(), filteredComments.size(), filter, roleFilter, groupId,
            search != null && !search.isBlank());
        return filteredComments;
    }

    /** Assessors' own comments on an assessment, for the moderation briefing. Comments a moderator rejected are left out. */
    public List<AssessmentComment> getAssessorCommentsForBriefing(Assessment assessment) {
        return commentRepository.findByAssessmentAndEvaluatorType(assessment, AssessmentComment.EvaluatorType.LECTURER).stream()
            .filter(c -> c.getApprovalStatus() != AssessmentComment.ApprovalStatus.REJECTED)
            .filter(c -> c.getCommentText() != null && !c.getCommentText().trim().isEmpty())
            .filter(c -> c.getEvaluatedStudent() != null)
            .limit(60)
            .collect(java.util.stream.Collectors.toList());
    }
}
