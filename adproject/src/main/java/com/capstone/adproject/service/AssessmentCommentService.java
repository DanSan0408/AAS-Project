package com.capstone.adproject.service;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.capstone.adproject.model.Assessment;
import com.capstone.adproject.model.AssessmentComment;
import com.capstone.adproject.model.Rubric;
import com.capstone.adproject.model.Student;
import com.capstone.adproject.repositories.AssessmentCommentRepository;
import com.capstone.adproject.repositories.StudentRepository;
import java.time.LocalDateTime;

import com.capstone.adproject.util.HtmlSanitizerUtil;

@Service
public class AssessmentCommentService {
    
    @Autowired
    private AssessmentCommentRepository commentRepository;
    
    @Autowired
    private StudentRepository studentRepository;

    @Autowired
    private CommentSentimentAnalysisService sentimentAnalysisService;
    
    @Transactional
    public AssessmentComment saveComment(AssessmentComment comment) {
        if (comment.getCommentText() != null) {
            comment.setCommentText(HtmlSanitizerUtil.sanitizePlainText(comment.getCommentText()));
            populateSentiment(comment);
        }
        if (comment.getIsApproved() == null) {
            comment.setIsApproved(false);
        }
        if (comment.getApprovalStatus() == null) {
            comment.setApprovalStatus(AssessmentComment.ApprovalStatus.PENDING);
        }
        return commentRepository.save(comment);
    }

    public void populateSentiment(AssessmentComment comment) {
        if (sentimentAnalysisService != null && comment.getCommentText() != null 
                && (comment.getFlagReason() == null || comment.getSentimentScore() == null)) {
            CommentSentimentAnalysisService.SentimentAnalysisResult result = sentimentAnalysisService.analyze(comment.getCommentText());
            if (result != null) {
                comment.setIsFlaggedNegative(result.isFlaggedNegative());
                comment.setFlagReason(result.getFlagReason());
                comment.setSentimentScore(result.getSentimentScore());
            }
        }
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
        String evaluatorName = evaluator.getUsername();
        
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
            comment.setEvaluatorName(student.getUsername());
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
            comment.setEvaluatorName(student.getUsername());
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
    List<AssessmentComment> allComments = commentRepository
        .findByEvaluatedStudent(student)
        .stream()
        .filter(AssessmentComment::isApproved)
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
            if (comment.getAssessmentType() == AssessmentComment.CommentAssessmentType.SELF) {
                category = "self";
            } else if (comment.getAssessmentType() == AssessmentComment.CommentAssessmentType.TEAM) {
                category = "team";
            } else {
                category = "peer";
            }
        } else if (comment.getEvaluatorType() == AssessmentComment.EvaluatorType.LECTURER) {
            category = "lecturer";
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
        return comment.getAnonymousIdentifier() != null ? comment.getAnonymousIdentifier() : "Teammate";
        
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

    @Transactional
    public int bulkApproveAllComments(Long assessmentId, String adminUsername) {
        List<AssessmentComment> comments = commentRepository.findByAssessmentId(assessmentId);
        int count = 0;
        for (AssessmentComment comment : comments) {
            if (!comment.isApproved()) {
                comment.setIsApproved(true);
                comment.setApprovalStatus(AssessmentComment.ApprovalStatus.APPROVED);
                comment.setApprovedAt(LocalDateTime.now());
                comment.setApprovedBy(adminUsername);
                commentRepository.save(comment);
                count++;
            }
        }
        return count;
    }

    @Transactional
    public int bulkApproveSafeComments(Long assessmentId, String adminUsername) {
        List<AssessmentComment> comments = commentRepository.findByAssessmentId(assessmentId);
        int count = 0;
        for (AssessmentComment comment : comments) {
            populateSentiment(comment);
            if (!Boolean.TRUE.equals(comment.getIsFlaggedNegative()) && !comment.isApproved()) {
                comment.setIsApproved(true);
                comment.setApprovalStatus(AssessmentComment.ApprovalStatus.APPROVED);
                comment.setApprovedAt(LocalDateTime.now());
                comment.setApprovedBy(adminUsername);
                commentRepository.save(comment);
                count++;
            }
        }
        return count;
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

    public Map<String, Long> getModerationStats(Long assessmentId) {
        Map<String, Long> stats = new HashMap<>();
        List<AssessmentComment> comments = commentRepository.findByAssessmentId(assessmentId);
        
        long total = comments.size();
        long pending = 0;
        long approved = 0;
        long rejected = 0;
        long flagged = 0;

        for (AssessmentComment c : comments) {
            populateSentiment(c);
            if (c.getApprovalStatus() == AssessmentComment.ApprovalStatus.APPROVED || Boolean.TRUE.equals(c.getIsApproved())) {
                approved++;
            } else if (c.getApprovalStatus() == AssessmentComment.ApprovalStatus.REJECTED) {
                rejected++;
            } else {
                pending++;
            }
            if (Boolean.TRUE.equals(c.getIsFlaggedNegative())) {
                flagged++;
            }
        }

        stats.put("total", total);
        stats.put("pending", pending);
        stats.put("approved", approved);
        stats.put("rejected", rejected);
        stats.put("flagged", flagged);
        return stats;
    }

    public List<AssessmentComment> getCommentsForModeration(Long assessmentId, String filter, String roleFilter, Long groupId, String search) {
        List<AssessmentComment> comments = commentRepository.findByAssessmentIdOrderBySubmittedAtDesc(assessmentId);
        
        // Ensure sentiment is populated for all comments
        for (AssessmentComment comment : comments) {
            if (comment.getFlagReason() == null || comment.getSentimentScore() == null) {
                populateSentiment(comment);
            }
        }

        return comments.stream()
            .filter(c -> {
                if (filter == null || "all".equalsIgnoreCase(filter)) return true;
                if ("flagged".equalsIgnoreCase(filter)) return Boolean.TRUE.equals(c.getIsFlaggedNegative());
                if ("pending".equalsIgnoreCase(filter)) return c.getApprovalStatus() == AssessmentComment.ApprovalStatus.PENDING && !Boolean.TRUE.equals(c.getIsApproved());
                if ("approved".equalsIgnoreCase(filter)) return c.isApproved();
                if ("rejected".equalsIgnoreCase(filter)) return c.getApprovalStatus() == AssessmentComment.ApprovalStatus.REJECTED;
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
                return s != null && s.getGroup() != null && groupId.equals(s.getGroup().getId());
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
    }
}