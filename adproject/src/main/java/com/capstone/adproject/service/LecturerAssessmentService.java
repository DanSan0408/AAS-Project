package com.capstone.adproject.service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.capstone.adproject.model.Assessment;
import com.capstone.adproject.model.AssessmentComment;
import com.capstone.adproject.model.Group;
import com.capstone.adproject.model.Lecturer;
import com.capstone.adproject.model.Mark;
import com.capstone.adproject.model.Rating;
import com.capstone.adproject.model.Rubric;
import com.capstone.adproject.model.Student;
import com.capstone.adproject.model.SubRubric;
import com.capstone.adproject.repositories.AssessmentCommentRepository;
import com.capstone.adproject.repositories.AssessmentRepository;
import com.capstone.adproject.repositories.GroupRepository;
import com.capstone.adproject.repositories.LecturerGroupAssignmentRepository;
import com.capstone.adproject.repositories.LecturerRepository;
import com.capstone.adproject.repositories.LecturerRubricAssignmentRepository;
import com.capstone.adproject.repositories.MarkRepository;
import com.capstone.adproject.repositories.RatingRepository;
import com.capstone.adproject.repositories.RubricRepository;
import com.capstone.adproject.repositories.StudentRepository;
import com.capstone.adproject.repositories.SubRubricRepository;
import com.capstone.adproject.util.HtmlSanitizerUtil;

@Service
public class LecturerAssessmentService {

    private final MarkRepository markRepository;
    private final AssessmentRepository assessmentRepository;
    private final GroupRepository groupRepository;
    private final StudentRepository studentRepository;
    private final SubRubricRepository subRubricRepository;
    private final RatingRepository ratingRepository;
    private final AssessmentCommentRepository assessmentCommentRepository;
    private final LecturerRepository lecturerRepository;
    private final RubricRepository rubricRepository;
    private final LecturerGroupAssignmentRepository groupAssignmentRepository;
    private final LecturerRubricAssignmentRepository rubricAssignmentRepository;

    public LecturerAssessmentService(
            MarkRepository markRepository,
            AssessmentRepository assessmentRepository,
            GroupRepository groupRepository,
            StudentRepository studentRepository,
            SubRubricRepository subRubricRepository,
            RatingRepository ratingRepository,
            AssessmentCommentRepository assessmentCommentRepository,
            LecturerRepository lecturerRepository,
            RubricRepository rubricRepository,
            LecturerGroupAssignmentRepository groupAssignmentRepository,
            LecturerRubricAssignmentRepository rubricAssignmentRepository) {
        this.markRepository = markRepository;
        this.assessmentRepository = assessmentRepository;
        this.groupRepository = groupRepository;
        this.studentRepository = studentRepository;
        this.subRubricRepository = subRubricRepository;
        this.ratingRepository = ratingRepository;
        this.assessmentCommentRepository = assessmentCommentRepository;
        this.lecturerRepository = lecturerRepository;
        this.rubricRepository = rubricRepository;
        this.groupAssignmentRepository = groupAssignmentRepository;
        this.rubricAssignmentRepository = rubricAssignmentRepository;
    }

    public List<Assessment> getAssessmentsForLecturerEvaluation() {
        List<Assessment> allAssessments = assessmentRepository.findAll();
        
        return allAssessments.stream()
            .filter(assessment -> {
                String title = assessment.getTitle().toLowerCase();
                return !title.contains("peer") && 
                        !title.contains("self") && 
                        !title.contains("industrial supervisor");
            })
            .collect(Collectors.toList());
    }

    @Transactional(readOnly = true)
    public List<Group> getAllGroups() {
        List<Group> groups = groupRepository.findAll();
        groups.forEach(g -> {
            if (g.getStudents() != null) {
                g.getStudents().size();
            }
        });
        return groups;
    }

    @Transactional(readOnly = true)
    public List<Student> getAllStudents() {
        return studentRepository.findAll();
    }

    @Transactional(readOnly = true)
    public List<Student> getStudentsByGroup(Long groupId) {
        return studentRepository.findByGroupId(groupId);
    }

    public String getGroupEvaluationStatus(Assessment assessment, Lecturer lecturer, Long groupId) {
        List<Student> groupMembers = studentRepository.findByGroupId(groupId);
        if (groupMembers.isEmpty()) {
            return "not_started";
        }
        
        Student sampleStudent = groupMembers.get(0);
        
        List<Mark> existingMarks = markRepository.findByEvaluatorStudentAndEvaluatedStudentAndAssessment(
            sampleStudent, sampleStudent, assessment);
        
        List<Mark> lecturerMarks = existingMarks.stream()
            .filter(m -> m.getComments() != null && 
                        m.getComments().startsWith("LECTURER:" + lecturer.getId()))
            .collect(Collectors.toList());
        
        if (lecturerMarks.isEmpty()) {
            return "not_started";
        }
        
        boolean isComplete = isGroupEvaluationComplete(assessment, lecturer, groupId);
        
        return isComplete ? "completed" : "in_progress";
    }

    public String getStudentEvaluationStatus(Assessment assessment, Lecturer lecturer, Long studentId) {
        Student student = studentRepository.findById(studentId).orElse(null);
        if (student == null) {
            return "not_started";
        }

        List<Mark> existingMarks = markRepository.findByEvaluatorStudentAndEvaluatedStudentAndAssessment(
            student, student, assessment);

        List<Mark> lecturerMarks = existingMarks.stream()
            .filter(m -> m.getComments() != null &&
                        m.getComments().startsWith("LECTURER:" + lecturer.getId()))
            .collect(Collectors.toList());

        if (lecturerMarks.isEmpty()) {
            return "not_started";
        }

        boolean isComplete = isStudentEvaluationComplete(assessment, lecturer, studentId);
        return isComplete ? "completed" : "in_progress";
    }

    public boolean isGroupEvaluationComplete(Assessment assessment, Lecturer lecturer, Long groupId) {
        List<Student> groupMembers = studentRepository.findByGroupId(groupId);
        if (groupMembers.isEmpty()) {
            return false;
        }
        
        Group group = groupRepository.findById(groupId).orElse(null);
        boolean isAssignedToGroup = groupAssignmentRepository.existsByAssessmentAndGroupAndLecturer(assessment, group, lecturer);
        
        Set<Long> allowedRubricIds = rubricAssignmentRepository.findByLecturerAndAssessment(lecturer, assessment).stream()
            .map(a -> a.getRubric().getId())
            .collect(Collectors.toSet());
        
        List<Rubric> groupRubrics = assessment.getRubrics().stream()
            .filter(r -> r.getAssessmentTypes() != null && 
                        r.getAssessmentTypes().equalsIgnoreCase("Group Assessment"))
            .filter(r -> isAssignedToGroup || allowedRubricIds.contains(r.getId()))
            .collect(Collectors.toList());
        
        List<Rubric> individualRubrics = assessment.getRubrics().stream()
            .filter(r -> r.getAssessmentTypes() != null && 
                        r.getAssessmentTypes().equalsIgnoreCase("Individual Assessment"))
            .filter(r -> isAssignedToGroup || allowedRubricIds.contains(r.getId()))
            .collect(Collectors.toList());
        
        Student sampleStudent = groupMembers.get(0);
        
        if (!groupRubrics.isEmpty()) {
            boolean groupComplete = checkRubricsComplete(
                assessment, lecturer, sampleStudent, groupRubrics, "Group Assessment");
            
            if (!groupComplete) {
                return false;
            }
            
            List<String> groupCommentLabels = assessment.getGroupCommentLabels();
            boolean hasGroupComments = groupCommentLabels != null && !groupCommentLabels.isEmpty();
            
            if (hasGroupComments) {
                int requiredGroupComments = assessment.getGroupCommentCount() != null ? 
                    assessment.getGroupCommentCount() : 0;
                int groupCommentMinLength = assessment.getGroupCommentMinLength() != null ? 
                    assessment.getGroupCommentMinLength() : 0;
                
                if (requiredGroupComments > 0 && groupCommentMinLength > 0) {
                    boolean groupCommentsComplete = checkCommentsComplete(
                        assessment, lecturer, sampleStudent, "Group Assessment", 
                        requiredGroupComments, groupCommentMinLength);
                    
                    if (!groupCommentsComplete) {
                        return false;
                    }
                }
            }
        }
        
        if (!individualRubrics.isEmpty()) {
            for (Student student : groupMembers) {
                boolean individualComplete = checkRubricsComplete(
                    assessment, lecturer, student, individualRubrics, "Individual Assessment");
                
                if (!individualComplete) {
                    return false;
                }
                
                List<String> individualCommentLabels = assessment.getIndividualCommentLabels();
                boolean hasIndividualComments = individualCommentLabels != null && !individualCommentLabels.isEmpty();
                
                if (hasIndividualComments) {
                    int requiredIndividualComments = assessment.getIndividualCommentCount() != null ? 
                        assessment.getIndividualCommentCount() : 0;
                    int individualCommentMinLength = assessment.getIndividualCommentMinLength() != null ? 
                        assessment.getIndividualCommentMinLength() : 0;
                    
                    if (requiredIndividualComments > 0 && individualCommentMinLength > 0) {
                        boolean individualCommentsComplete = checkCommentsComplete(
                            assessment, lecturer, student, "Individual Assessment", 
                            requiredIndividualComments, individualCommentMinLength);
                        
                        if (!individualCommentsComplete) {
                            return false;
                        }
                    }
                }
            }
        }
        
        return true;
    }

    public boolean isStudentEvaluationComplete(Assessment assessment, Lecturer lecturer, Long studentId) {
        Student student = studentRepository.findById(studentId).orElse(null);
        if (student == null) {
            return false;
        }

        List<Rubric> groupRubrics = assessment.getRubrics().stream()
            .filter(r -> r.getAssessmentTypes() != null &&
                        r.getAssessmentTypes().equalsIgnoreCase("Group Assessment"))
            .collect(Collectors.toList());

        List<Rubric> individualRubrics = assessment.getRubrics().stream()
            .filter(r -> r.getAssessmentTypes() != null &&
                        r.getAssessmentTypes().equalsIgnoreCase("Individual Assessment"))
            .collect(Collectors.toList());

        if (!groupRubrics.isEmpty()) {
            boolean groupComplete = checkRubricsComplete(
                assessment, lecturer, student, groupRubrics, "Group Assessment");

            if (!groupComplete) {
                return false;
            }

            List<String> groupCommentLabels = assessment.getGroupCommentLabels();
            boolean hasGroupComments = groupCommentLabels != null && !groupCommentLabels.isEmpty();

            if (hasGroupComments) {
                int requiredGroupComments = assessment.getGroupCommentCount() != null ?
                    assessment.getGroupCommentCount() : 0;
                int groupCommentMinLength = assessment.getGroupCommentMinLength() != null ?
                    assessment.getGroupCommentMinLength() : 0;

                if (requiredGroupComments > 0 && groupCommentMinLength > 0) {
                    boolean groupCommentsComplete = checkCommentsComplete(
                        assessment, lecturer, student, "Group Assessment",
                        requiredGroupComments, groupCommentMinLength);

                    if (!groupCommentsComplete) {
                        return false;
                    }
                }
            }
        }

        if (!individualRubrics.isEmpty()) {
            boolean individualComplete = checkRubricsComplete(
                assessment, lecturer, student, individualRubrics, "Individual Assessment");

            if (!individualComplete) {
                return false;
            }

            List<String> individualCommentLabels = assessment.getIndividualCommentLabels();
            boolean hasIndividualComments = individualCommentLabels != null && !individualCommentLabels.isEmpty();

            if (hasIndividualComments) {
                int requiredIndividualComments = assessment.getIndividualCommentCount() != null ?
                    assessment.getIndividualCommentCount() : 0;
                int individualCommentMinLength = assessment.getIndividualCommentMinLength() != null ?
                    assessment.getIndividualCommentMinLength() : 0;

                if (requiredIndividualComments > 0 && individualCommentMinLength > 0) {
                    boolean individualCommentsComplete = checkCommentsComplete(
                        assessment, lecturer, student, "Individual Assessment",
                        requiredIndividualComments, individualCommentMinLength);

                    if (!individualCommentsComplete) {
                        return false;
                    }
                }
            }
        }

        return !groupRubrics.isEmpty() || !individualRubrics.isEmpty();
    }

    private boolean checkRubricsComplete(Assessment assessment, Lecturer lecturer, 
                                         Student student, List<Rubric> rubrics, String rubricType) {
        List<Mark> marks = markRepository.findByEvaluatorStudentAndEvaluatedStudentAndAssessment(
            student, student, assessment);
        
        List<Mark> lecturerMarks = marks.stream()
            .filter(m -> m.getComments() != null && 
                        m.getComments().startsWith("LECTURER:" + lecturer.getId()) &&
                        m.getAssessmentType() != null &&
                        m.getAssessmentType().equalsIgnoreCase(rubricType))
            .collect(Collectors.toList());
        
        int requiredEvaluations = 0;
        for (Rubric rubric : rubrics) {
            if (rubric.getSubRubrics() != null && !rubric.getSubRubrics().isEmpty()) {
                requiredEvaluations += rubric.getSubRubrics().size();
            } else if (rubric.getRatings() != null && !rubric.getRatings().isEmpty()) {
                requiredEvaluations += 1;
            }
        }
        
        return lecturerMarks.size() >= requiredEvaluations;
    }

    private boolean checkCommentsComplete(Assessment assessment, Lecturer lecturer, 
                                          Student student, String rubricType, 
                                          int requiredCount, int minLength) {
        List<AssessmentComment> comments = assessmentCommentRepository
            .findByEvaluatedStudentAndAssessmentAndEvaluatorIdAndEvaluatorType(
                student, assessment, lecturer.getId(), AssessmentComment.EvaluatorType.LECTURER)
            .stream()
            .filter(c -> rubricType.equalsIgnoreCase(c.getRubricAssessmentType()))
            .filter(c -> c.getRubricId() == null) 
            .collect(Collectors.toList());
        
        if (comments.size() < requiredCount) {
            return false;
        }
        
        for (AssessmentComment comment : comments) {
            if (comment.getCommentText() == null || 
                comment.getCommentText().length() < minLength) {
                return false;
            }
        }
        
        return true;
    }

    @Deprecated
    public boolean hasTargetBeenEvaluated(
            Assessment assessment, 
            Lecturer lecturer, 
            Long targetId, 
            boolean isGroupTarget,
            String rubricType) {
        
        if (isGroupTarget) {
            List<Student> groupMembers = studentRepository.findByGroupId(targetId);
            if (groupMembers.isEmpty()) {
                return false;
            }
            
            Student anyMember = groupMembers.get(0);
            List<Mark> marks = markRepository.findByEvaluatorStudentAndEvaluatedStudentAndAssessment(
                anyMember, anyMember, assessment);
            
            return marks.stream()
                .anyMatch(m -> m.getComments() != null && 
                              m.getComments().startsWith("LECTURER:" + lecturer.getId()) &&
                              m.getAssessmentType() != null &&
                              m.getAssessmentType().equalsIgnoreCase(rubricType));
        } else {
            Student student = studentRepository.findById(targetId).orElse(null);
            if (student == null) {
                return false;
            }
            
            List<Mark> marks = markRepository.findByEvaluatorStudentAndEvaluatedStudentAndAssessment(
                student, student, assessment);
            
            return marks.stream()
                .anyMatch(m -> m.getComments() != null && 
                              m.getComments().startsWith("LECTURER:" + lecturer.getId()) &&
                              m.getAssessmentType() != null &&
                              m.getAssessmentType().equalsIgnoreCase(rubricType));
        }
    }

    public boolean hasGroupBeenEvaluatedByLecturer(Assessment assessment, Lecturer lecturer, Long groupId) {
        List<Student> groupMembers = studentRepository.findByGroupId(groupId);
        if (groupMembers.isEmpty()) {
            return false;
        }
        
        Student anyMember = groupMembers.get(0);
        List<Mark> marks = markRepository.findByEvaluatorStudentAndEvaluatedStudentAndAssessment(
            anyMember, anyMember, assessment);
        
        return marks.stream()
            .anyMatch(m -> m.getComments() != null && 
                          m.getComments().startsWith("LECTURER:" + lecturer.getId()));
    }

    @Transactional
    public void saveEvaluationScores(Long assessmentId, Long lecturerId, String lecturerName,
                                   Long targetId, boolean isGroupTarget, String rubricType,
                                   Map<String, String> scores, Map<Integer, String> comments,
                                   Map<Long, Map<Integer, String>> rubricComments) {

        Assessment assessment = assessmentRepository.findById(assessmentId)
            .orElseThrow(() -> new RuntimeException("Assessment not found"));
        
        Lecturer lecturer = lecturerRepository.findById(lecturerId)
            .orElseThrow(() -> new RuntimeException("Lecturer not found"));
        
        List<Student> targetStudents;
        if (isGroupTarget) {
            Group group = groupRepository.findById(targetId)
                .orElseThrow(() -> new RuntimeException("Group not found"));
            targetStudents = studentRepository.findByGroup(group);
        } else {
            Student student = studentRepository.findById(targetId)
                .orElseThrow(() -> new RuntimeException("Student not found"));
            targetStudents = List.of(student);
        }
        
        for (Student student : targetStudents) {
            List<Mark> existingMarks = markRepository.findByEvaluatorStudentAndEvaluatedStudentAndAssessment(
                    student, student, assessment).stream()
                .filter(m -> m.getComments() != null && m.getComments().equals("LECTURER:" + lecturerId))
                .filter(m -> rubricType.equalsIgnoreCase(m.getAssessmentType()))
                .collect(Collectors.toList());
            
            if (!existingMarks.isEmpty()) {
                markRepository.deleteAll(existingMarks);
            }
            
            List<AssessmentComment> existingComments = assessmentCommentRepository.findByEvaluatorAndStudentAndAssessment(
                    lecturerId,
                    AssessmentComment.EvaluatorType.LECTURER,
                    student,
                    assessment).stream()
                .filter(c -> rubricType.equalsIgnoreCase(c.getRubricAssessmentType()))
                .filter(c -> c.getRubricId() == null)
                .collect(Collectors.toList());
            
            if (!existingComments.isEmpty()) {
                assessmentCommentRepository.deleteAll(existingComments);
            }
            
            List<Mark> newMarks = new ArrayList<>();
            for (Map.Entry<String, String> entry : scores.entrySet()) {
                String key = entry.getKey();
                Long ratingId = Long.parseLong(entry.getValue());
                
                Rating rating = ratingRepository.findById(ratingId)
                    .orElseThrow(() -> new RuntimeException("Rating not found"));
                
                Mark mark = new Mark();
                mark.setEvaluatorStudent(student);
                mark.setEvaluatedStudent(student);
                mark.setAssessment(assessment);
                mark.setRating(rating);
                mark.setComments("LECTURER:" + lecturerId);
                mark.setLecturer(lecturer);
                mark.setAssessmentType(rubricType);
                mark.setStatus(Mark.SubmissionStatus.FINAL);
                
                if (key.startsWith("subRubric_")) {
                    Long subRubricId = Long.parseLong(key.substring(10));
                    SubRubric subRubric = subRubricRepository.findById(subRubricId)
                        .orElseThrow(() -> new RuntimeException("SubRubric not found"));
                    
                    mark.setSubRubric(subRubric);
                    mark.setRubric(subRubric.getRubric());
                    mark.setMarkValue(rating.getMarks() != null ? rating.getMarks().doubleValue() : 0.0);
                    mark.setClo(subRubric.getRubric().getClo());
                    
                    if (subRubric.getRubric().getMarks() != null && 
                        subRubric.getRubric().getMarks().compareTo(BigDecimal.ZERO) > 0 &&
                        subRubric.getMarks() != null && 
                        subRubric.getMarks().compareTo(BigDecimal.ZERO) > 0 &&
                        subRubric.getRubric().getCloMarks() != null) {
                        
                        double subRubricProportion = subRubric.getMarks()
                            .divide(subRubric.getRubric().getMarks(), 4, RoundingMode.HALF_UP)
                            .doubleValue();
                        double cloMarksForSubRubric = subRubric.getRubric().getCloMarks() * subRubricProportion;
                        double achievementProportion = rating.getMarks()
                            .divide(subRubric.getMarks(), 4, RoundingMode.HALF_UP)
                            .doubleValue();
                        mark.setCloMarks(cloMarksForSubRubric * achievementProportion);
                    } else {
                        mark.setCloMarks(rating.getMarks() != null ? rating.getMarks().doubleValue() : 0.0);
                    }
                } else if (key.startsWith("rubric_")) {
                    Long rubricId = Long.parseLong(key.substring(7));
                    Rubric rubric = rubricRepository.findById(rubricId)
                        .orElseThrow(() -> new RuntimeException("Rubric not found"));
                    
                    mark.setRubric(rubric);
                    mark.setMarkValue(rating.getMarks() != null ? rating.getMarks().doubleValue() : 0.0);
                    mark.setClo(rubric.getClo());
                    mark.setCloMarks(rating.getMarks() != null ? rating.getMarks().doubleValue() : 0.0);
                }
                
                newMarks.add(mark);
            }
            
            if (!newMarks.isEmpty()) {
                markRepository.saveAll(newMarks);
            }
            
            if (comments != null && !comments.isEmpty()) {
                List<String> commentLabels = rubricType.equalsIgnoreCase("Group Assessment") ?
                    assessment.getGroupCommentLabels() :
                    assessment.getIndividualCommentLabels();
                
                if (commentLabels != null && !commentLabels.isEmpty()) {
                    for (Map.Entry<Integer, String> commentEntry : comments.entrySet()) {
                        if (commentEntry.getValue() == null || commentEntry.getValue().trim().isEmpty()) {
                            continue;
                        }
                        
                        AssessmentComment comment = new AssessmentComment();
                        comment.setEvaluatorId(lecturerId);
                        comment.setEvaluatorType(AssessmentComment.EvaluatorType.LECTURER);
                        comment.setEvaluatorName(lecturerName);
                        comment.setEvaluatedStudent(student);
                        comment.setAssessment(assessment);
                        comment.setCommentText(HtmlSanitizerUtil.sanitize(commentEntry.getValue().trim()));
                        comment.setAssessmentType(AssessmentComment.CommentAssessmentType.LECTURER_EVALUATION);
                        comment.setCommentIndex(commentEntry.getKey());
                        comment.setRubricAssessmentType(rubricType);
                        comment.setRubricId(null);
                        
                        String label = rubricType.equalsIgnoreCase("Group Assessment") ?
                            assessment.getGroupCommentLabel(commentEntry.getKey()) :
                            assessment.getIndividualCommentLabel(commentEntry.getKey());
                        comment.setCommentLabel(label);
                        
                        Integer commentIndex = commentEntry.getKey();
                        Boolean isAnonymous = rubricType.equalsIgnoreCase("Group Assessment") ?
                            assessment.isGroupCommentAnonymous(commentIndex) :
                            assessment.isIndividualCommentAnonymous(commentIndex);
                        
                        if (isAnonymous != null && isAnonymous) {
                            comment.setAnonymousIdentifier("Lecturer");
                        } else {
                            comment.setAnonymousIdentifier(lecturerName);
                        }
                        
                        assessmentCommentRepository.save(comment);
                    }
                }
            }
            
            if (rubricComments != null && !rubricComments.isEmpty()) {
                for (Map.Entry<Long, Map<Integer, String>> rubricEntry : rubricComments.entrySet()) {
                    Long rubricId = rubricEntry.getKey();
                    Map<Integer, String> rubricCommentMap = rubricEntry.getValue();
                    
                    Rubric rubric = rubricRepository.findById(rubricId)
                        .orElseThrow(() -> new RuntimeException("Rubric not found"));
                    
                    List<AssessmentComment> existingRubricComments = assessmentCommentRepository.findByEvaluatorAndStudentAndAssessment(
                            lecturerId,
                            AssessmentComment.EvaluatorType.LECTURER,
                            student,
                            assessment).stream()
                        .filter(c -> c.getRubricId() != null && c.getRubricId().equals(rubricId))
                        .collect(Collectors.toList());
                    
                    if (!existingRubricComments.isEmpty()) {
                        assessmentCommentRepository.deleteAll(existingRubricComments);
                    }
                    
                    for (Map.Entry<Integer, String> commentEntry : rubricCommentMap.entrySet()) {
                        Integer commentIndex = commentEntry.getKey();
                        String commentText = commentEntry.getValue();
                        
                        if (commentText == null || commentText.trim().isEmpty()) {
                            continue;
                        }
                        
                        AssessmentComment comment = new AssessmentComment();
                        comment.setEvaluatorId(lecturerId);
                        comment.setEvaluatorType(AssessmentComment.EvaluatorType.LECTURER);
                        comment.setEvaluatorName(lecturerName);
                        comment.setEvaluatedStudent(student);
                        comment.setAssessment(assessment);
                        comment.setCommentText(HtmlSanitizerUtil.sanitize(commentText.trim()));
                        comment.setAssessmentType(AssessmentComment.CommentAssessmentType.LECTURER_EVALUATION);
                        comment.setCommentIndex(commentIndex);
                        comment.setRubricAssessmentType(rubricType);
                        comment.setRubricId(rubricId);
                        comment.setCommentLabel(rubric.getRubricCommentLabel(commentIndex));
                        
                        Boolean isAnonymous = rubric.isRubricCommentAnonymous(commentIndex);
                        if (isAnonymous != null && isAnonymous) {
                            comment.setAnonymousIdentifier("Lecturer");
                        } else {
                            comment.setAnonymousIdentifier(lecturerName);
                        }
                        
                        assessmentCommentRepository.save(comment);
                    }
                }
            }
        }
    }
    
    public Map<String, Long> getExistingMarks(Assessment assessment, Lecturer lecturer, 
                                               Student student, String rubricType) {
        Map<String, Long> existingMarks = new HashMap<>();
        
        List<Mark> marks = markRepository.findByEvaluatorStudentAndEvaluatedStudentAndAssessment(
            student, student, assessment);
        
        for (Mark mark : marks) {
            if (mark.getComments() != null && 
                mark.getComments().startsWith("LECTURER:" + lecturer.getId()) &&
                mark.getAssessmentType() != null &&
                mark.getAssessmentType().equalsIgnoreCase(rubricType)) {
                
                String key;
                if (mark.getSubRubric() != null) {
                    key = "subRubric_" + mark.getSubRubric().getId();
                } else if (mark.getRubric() != null) {
                    key = "rubric_" + mark.getRubric().getId();
                } else {
                    continue;
                }
                
                if (mark.getRating() != null) {
                    existingMarks.put(key, mark.getRating().getId());
                }
            }
        }
        
        return existingMarks;
    }
    
    public Map<Integer, String> getExistingComments(Assessment assessment, Lecturer lecturer, 
                                                     Student student, String rubricType) {
        Map<Integer, String> existingComments = new HashMap<>();
        
        List<AssessmentComment> comments = assessmentCommentRepository
            .findByEvaluatedStudentAndAssessmentAndEvaluatorIdAndEvaluatorType(
                student, assessment, lecturer.getId(), AssessmentComment.EvaluatorType.LECTURER).stream()
            .filter(c -> rubricType.equalsIgnoreCase(c.getRubricAssessmentType()))
            .filter(c -> c.getRubricId() == null)
            .collect(Collectors.toList());
        
        for (AssessmentComment comment : comments) {
            existingComments.put(comment.getCommentIndex(), comment.getCommentText());
        }
        
        return existingComments;
    }

    public Map<Integer, String> getExistingRubricComments(Assessment assessment, Lecturer lecturer, 
                                                           Student student, Long rubricId) {
        List<AssessmentComment> comments = assessmentCommentRepository.findByEvaluatorAndStudentAndAssessment(
            lecturer.getId(),
            AssessmentComment.EvaluatorType.LECTURER,
            student,
            assessment
        ).stream()
        .filter(c -> c.getRubricId() != null && c.getRubricId().equals(rubricId))
        .sorted((c1, c2) -> Integer.compare(c1.getCommentIndex(), c2.getCommentIndex()))
        .collect(Collectors.toList());
        
        Map<Integer, String> commentMap = new HashMap<>();
        for (AssessmentComment comment : comments) {
            commentMap.put(comment.getCommentIndex(), comment.getCommentText());
        }
        
        return commentMap;
    }
}