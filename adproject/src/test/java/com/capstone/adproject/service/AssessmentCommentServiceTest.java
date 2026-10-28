package com.capstone.adproject.service;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import static org.mockito.ArgumentMatchers.any;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import org.mockito.junit.jupiter.MockitoExtension;

import com.capstone.adproject.model.Assessment;
import com.capstone.adproject.model.AssessmentComment;
import com.capstone.adproject.model.Student;
import com.capstone.adproject.repositories.AssessmentCommentRepository;
import com.capstone.adproject.repositories.StudentRepository;

@ExtendWith(MockitoExtension.class)
public class AssessmentCommentServiceTest {

    @Mock
    private AssessmentCommentRepository commentRepository;

    @Mock
    private StudentRepository studentRepository;

    @InjectMocks
    private AssessmentCommentService commentService;

    private Student student;
    private Assessment assessment;

    @BeforeEach
    public void setUp() {
        student = new Student();
        student.setId(1L);
        student.setUsername("Alice");
        student.setEmail("alice@example.com");

        assessment = new Assessment();
        assessment.setId(10L);
        assessment.setTitle("Project Milestone 1");
    }

    @Test
    public void testStudentOnlyReceivesApprovedComments() {
        AssessmentComment approvedComment = new AssessmentComment();
        approvedComment.setId(101L);
        approvedComment.setEvaluatedStudent(student);
        approvedComment.setAssessment(assessment);
        approvedComment.setCommentText("Great job on module implementation!");
        approvedComment.setEvaluatorType(AssessmentComment.EvaluatorType.LECTURER);
        approvedComment.setAssessmentType(AssessmentComment.CommentAssessmentType.LECTURER_EVALUATION);
        approvedComment.setIsApproved(true);
        approvedComment.setApprovalStatus(AssessmentComment.ApprovalStatus.APPROVED);

        AssessmentComment pendingComment = new AssessmentComment();
        pendingComment.setId(102L);
        pendingComment.setEvaluatedStudent(student);
        pendingComment.setAssessment(assessment);
        pendingComment.setCommentText("Needs more work on presentation.");
        pendingComment.setEvaluatorType(AssessmentComment.EvaluatorType.LECTURER);
        pendingComment.setAssessmentType(AssessmentComment.CommentAssessmentType.LECTURER_EVALUATION);
        pendingComment.setIsApproved(false);
        pendingComment.setApprovalStatus(AssessmentComment.ApprovalStatus.PENDING);

        when(commentRepository.findByEvaluatedStudent(student))
                .thenReturn(List.of(approvedComment, pendingComment));

        Map<String, Object> compiled = commentService.getCompiledCommentsForStudent(student);
        Integer total = (Integer) compiled.get("totalComments");

        // The student should only see the 1 approved comment, not the pending one!
        assertEquals(1, total);
    }

    @Test
    public void testApproveCommentUpdatesStatus() {
        AssessmentComment comment = new AssessmentComment();
        comment.setId(201L);
        comment.setIsApproved(false);
        comment.setApprovalStatus(AssessmentComment.ApprovalStatus.PENDING);

        when(commentRepository.findById(201L)).thenReturn(Optional.of(comment));
        when(commentRepository.save(any(AssessmentComment.class))).thenAnswer(i -> i.getArgument(0));

        boolean result = commentService.approveComment(201L, "admin_user");

        assertTrue(result);
        assertTrue(comment.isApproved());
        assertEquals(AssessmentComment.ApprovalStatus.APPROVED, comment.getApprovalStatus());
        assertEquals("admin_user", comment.getApprovedBy());
        verify(commentRepository).save(comment);
    }

    @Test
    public void testRejectCommentHidesFromStudent() {
        AssessmentComment comment = new AssessmentComment();
        comment.setId(202L);
        comment.setIsApproved(true);
        comment.setApprovalStatus(AssessmentComment.ApprovalStatus.APPROVED);

        when(commentRepository.findById(202L)).thenReturn(Optional.of(comment));
        when(commentRepository.save(any(AssessmentComment.class))).thenAnswer(i -> i.getArgument(0));

        boolean result = commentService.rejectComment(202L, "admin_user");

        assertTrue(result);
        assertFalse(comment.isApproved());
        assertEquals(AssessmentComment.ApprovalStatus.REJECTED, comment.getApprovalStatus());
        assertEquals("admin_user", comment.getApprovedBy());
    }

    @Test
    public void testToggleFlagComment() {
        AssessmentComment comment = new AssessmentComment();
        comment.setId(203L);
        comment.setIsFlaggedNegative(false);

        when(commentRepository.findById(203L)).thenReturn(Optional.of(comment));
        when(commentRepository.save(any(AssessmentComment.class))).thenAnswer(i -> i.getArgument(0));

        boolean result = commentService.toggleFlag(203L);
        assertTrue(result);
        assertTrue(comment.getIsFlaggedNegative());

        // Toggle again to unflag
        boolean unflagResult = commentService.toggleFlag(203L);
        assertTrue(unflagResult);
        assertFalse(comment.getIsFlaggedNegative());
    }

    @Test
    public void testGetCommentById() {
        AssessmentComment comment = new AssessmentComment();
        comment.setId(501L);
        comment.setCommentText("Great presentation");

        when(commentRepository.findById(501L)).thenReturn(Optional.of(comment));
        when(commentRepository.findById(999L)).thenReturn(Optional.empty());

        Optional<AssessmentComment> found = commentService.getCommentById(501L);
        assertTrue(found.isPresent());
        assertEquals("Great presentation", found.get().getCommentText());

        Optional<AssessmentComment> notFound = commentService.getCommentById(999L);
        assertFalse(notFound.isPresent());

        Optional<AssessmentComment> nullId = commentService.getCommentById(null);
        assertFalse(nullId.isPresent());
    }

    @Test
    public void testBulkApproveAllComments() {
        AssessmentComment comment1 = new AssessmentComment();
        comment1.setId(301L);
        comment1.setIsApproved(false);
        comment1.setApprovalStatus(AssessmentComment.ApprovalStatus.PENDING);

        AssessmentComment comment2 = new AssessmentComment();
        comment2.setId(302L);
        comment2.setIsApproved(false);
        comment2.setApprovalStatus(AssessmentComment.ApprovalStatus.PENDING);

        when(commentRepository.findByAssessmentId(10L))
                .thenReturn(new ArrayList<>(List.of(comment1, comment2)));
        when(commentRepository.save(any(AssessmentComment.class))).thenAnswer(i -> i.getArgument(0));

        int approvedCount = commentService.bulkApproveAllComments(10L, "admin_user");

        assertEquals(2, approvedCount);
        assertTrue(comment1.isApproved());
        assertTrue(comment2.isApproved());
        assertEquals(AssessmentComment.ApprovalStatus.APPROVED, comment1.getApprovalStatus());
    }

    @Test
    public void testModerationStatsCalculation() {
        AssessmentComment pending = new AssessmentComment();
        pending.setIsApproved(false);
        pending.setApprovalStatus(AssessmentComment.ApprovalStatus.PENDING);

        AssessmentComment approved = new AssessmentComment();
        approved.setIsApproved(true);
        approved.setApprovalStatus(AssessmentComment.ApprovalStatus.APPROVED);

        AssessmentComment rejected = new AssessmentComment();
        rejected.setIsApproved(false);
        rejected.setApprovalStatus(AssessmentComment.ApprovalStatus.REJECTED);

        when(commentRepository.findByAssessmentId(10L))
                .thenReturn(List.of(pending, approved, rejected));

        Map<String, Long> stats = commentService.getModerationStats(10L);

        assertEquals(3L, stats.get("total"));
        assertEquals(1L, stats.get("pending"));
        assertEquals(1L, stats.get("approved"));
        assertEquals(1L, stats.get("rejected"));
    }

    @Test
    public void testGetCommentsForModeration_FiltersStudentCommentsCorrectly() {
        AssessmentComment studentComment = new AssessmentComment();
        studentComment.setId(401L);
        studentComment.setEvaluatorType(AssessmentComment.EvaluatorType.STUDENT);
        studentComment.setAssessmentType(AssessmentComment.CommentAssessmentType.PEER);
        studentComment.setEvaluatorName("StudentBob");
        studentComment.setEvaluatedStudent(student);
        studentComment.setCommentText("Bob's peer feedback");
        studentComment.setApprovalStatus(AssessmentComment.ApprovalStatus.PENDING);
        studentComment.setIsApproved(false);

        AssessmentComment lecturerComment = new AssessmentComment();
        lecturerComment.setId(402L);
        lecturerComment.setEvaluatorType(AssessmentComment.EvaluatorType.LECTURER);
        lecturerComment.setAssessmentType(AssessmentComment.CommentAssessmentType.LECTURER_EVALUATION);
        lecturerComment.setEvaluatorName("Dr. Smith");
        lecturerComment.setEvaluatedStudent(student);
        lecturerComment.setCommentText("Lecturer grading feedback");
        lecturerComment.setApprovalStatus(AssessmentComment.ApprovalStatus.APPROVED);
        lecturerComment.setIsApproved(true);

        when(commentRepository.findByAssessmentIdOrderBySubmittedAtDesc(10L))
                .thenReturn(List.of(studentComment, lecturerComment));

        // When filtering by 'all', both appear
        List<AssessmentComment> allComments = commentService.getCommentsForModeration(10L, "all", "all", null, null);
        assertEquals(2, allComments.size());

        // When filtering by 'student' role, only the student comment appears
        List<AssessmentComment> studentOnly = commentService.getCommentsForModeration(10L, "all", "student", null, null);
        assertEquals(1, studentOnly.size());
        assertEquals("StudentBob", studentOnly.get(0).getEvaluatorName());

        // When filtering by 'pending' status, only the student comment appears
        List<AssessmentComment> pendingOnly = commentService.getCommentsForModeration(10L, "pending", "all", null, null);
        assertEquals(1, pendingOnly.size());
        assertEquals(401L, pendingOnly.get(0).getId());

        // When filtering by 'assessor' role, only the lecturer comment appears
        List<AssessmentComment> assessorOnly = commentService.getCommentsForModeration(10L, "all", "assessor", null, null);
        assertEquals(1, assessorOnly.size());
        assertEquals("Dr. Smith", assessorOnly.get(0).getEvaluatorName());
    }
}
