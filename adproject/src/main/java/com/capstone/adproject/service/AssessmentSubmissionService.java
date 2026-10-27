package com.capstone.adproject.service;

import java.io.IOException;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.core.io.Resource;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import com.capstone.adproject.model.Assessment;
import com.capstone.adproject.model.AssessmentSubmission;
import com.capstone.adproject.model.Course;
import com.capstone.adproject.model.Group;
import com.capstone.adproject.model.Lecturer;
import com.capstone.adproject.model.LecturerGroupAssignment;
import com.capstone.adproject.model.LecturerRubricAssignment;
import com.capstone.adproject.model.LecturerStudentAssignment;
import com.capstone.adproject.model.Student;
import com.capstone.adproject.model.StudentAssessmentAssignment;
import com.capstone.adproject.model.SubmissionAnnotation;
import com.capstone.adproject.repositories.AssessmentSubmissionRepository;
import com.capstone.adproject.repositories.GroupRepository;
import com.capstone.adproject.repositories.LecturerGroupAssignmentRepository;
import com.capstone.adproject.repositories.LecturerRubricAssignmentRepository;
import com.capstone.adproject.repositories.LecturerStudentAssignmentRepository;
import com.capstone.adproject.repositories.StudentAssessmentAssignmentRepository;
import com.capstone.adproject.repositories.SubmissionAnnotationRepository;

@Service
public class AssessmentSubmissionService {

    private static final Logger logger = LoggerFactory.getLogger(AssessmentSubmissionService.class);

    @Autowired
    private AssessmentSubmissionRepository submissionRepository;

    @Autowired
    private SubmissionAnnotationRepository annotationRepository;

    @Autowired
    private FileStorageService fileStorageService;

    @Autowired
    private LecturerGroupAssignmentRepository lecturerGroupAssignmentRepository;

    @Autowired
    private LecturerStudentAssignmentRepository lecturerStudentAssignmentRepository;

    @Autowired
    private LecturerRubricAssignmentRepository lecturerRubricAssignmentRepository;

    @Autowired
    private GroupRepository groupRepository;

    @Autowired
    private StudentAssessmentAssignmentRepository studentAssessmentAssignmentRepository;

    @Transactional(readOnly = true)
    public Optional<AssessmentSubmission> getSubmissionById(Long id) {
        if (id == null)
            return Optional.empty();
        return submissionRepository.findById(id);
    }

    @Transactional(readOnly = true)
    public Optional<AssessmentSubmission> getSubmissionForTarget(Assessment assessment, Group group, Student student) {
        if (assessment == null)
            return Optional.empty();
        if (group != null) {
            Optional<AssessmentSubmission> groupSub = submissionRepository.findByAssessmentAndGroup(assessment, group);
            if (groupSub.isPresent())
                return groupSub;
        }
        if (student != null) {
            if (student.getGroup() != null) {
                Optional<AssessmentSubmission> groupSub = submissionRepository.findByAssessmentAndGroup(assessment,
                        student.getGroup());
                if (groupSub.isPresent())
                    return groupSub;
            }
            Optional<AssessmentSubmission> studentSub = submissionRepository.findByAssessmentAndStudent(assessment,
                    student);
            if (studentSub.isPresent())
                return studentSub;
        }
        return Optional.empty();
    }

    @Transactional(readOnly = true)
    public Optional<AssessmentSubmission> getSubmissionForStudent(Assessment assessment, Student student) {
        if (assessment == null || student == null)
            return Optional.empty();
        if ("GROUP".equalsIgnoreCase(assessment.getSubmissionScope()) && student.getGroup() != null) {
            Optional<AssessmentSubmission> groupSub = submissionRepository.findByAssessmentAndGroup(assessment,
                    student.getGroup());
            if (groupSub.isPresent())
                return groupSub;
        }
        Optional<AssessmentSubmission> studentSub = submissionRepository.findByAssessmentAndStudent(assessment,
                student);
        if (studentSub.isPresent()) {
            return studentSub;
        }
        if (student.getGroup() != null) {
            return submissionRepository.findByAssessmentAndGroup(assessment, student.getGroup());
        }
        return Optional.empty();
    }

    @Transactional
    public AssessmentSubmission saveSubmission(Assessment assessment, Course course, Group group, Student student,
            MultipartFile file) throws IOException {
        if (assessment == null || student == null || file == null || file.isEmpty()) {
            throw new IllegalArgumentException("Invalid submission parameters.");
        }

        // Validate file size limit (capped at max 50 MB)
        int maxMb = assessment.getMaxFileSizeMb() != null ? Math.min(assessment.getMaxFileSizeMb(), 50) : 50;
        long maxBytes = (long) maxMb * 1024 * 1024;
        if (file.getSize() > maxBytes || file.getSize() > 50L * 1024 * 1024) {
            throw new IllegalArgumentException(
                    "File size exceeds the allowed limit of " + maxMb + " MB (storage cap: 50 MB).");
        }

        boolean isGroup = "GROUP".equalsIgnoreCase(assessment.getSubmissionScope());
        String targetFolder = isGroup && group != null ? "group_" + group.getId() : "student_" + student.getId();

        // Check if an existing submission exists
        Optional<AssessmentSubmission> existingOpt = isGroup && group != null
                ? submissionRepository.findByAssessmentAndGroup(assessment, group)
                : submissionRepository.findByAssessmentAndStudent(assessment, student);

        String relativeFilePath = fileStorageService.storePdfFile(file, course.getId(), assessment.getId(),
                targetFolder);

        AssessmentSubmission submission;
        if (existingOpt.isPresent()) {
            submission = existingOpt.get();
            // Delete old physical file to save disk space
            fileStorageService.deleteFile(submission.getStoredFileName());

            submission.setOriginalFileName(file.getOriginalFilename());
            submission.setStoredFileName(relativeFilePath);
            submission.setFileSizeBytes(file.getSize());
            submission.setContentType(file.getContentType() != null ? file.getContentType() : "application/pdf");
            submission.setSubmittedAt(LocalDateTime.now());
            submission.setStudent(student); // tracks last student who replaced the submission
            submission.setVersion(submission.getVersion() != null ? submission.getVersion() + 1 : 2);
            submission.setStatus("RESUBMITTED");
        } else {
            submission = new AssessmentSubmission();
            submission.setAssessment(assessment);
            submission.setCourse(course);
            submission.setGroup(isGroup ? group : null);
            submission.setStudent(student);
            submission.setOriginalFileName(file.getOriginalFilename());
            submission.setStoredFileName(relativeFilePath);
            submission.setFileSizeBytes(file.getSize());
            submission.setContentType(file.getContentType() != null ? file.getContentType() : "application/pdf");
            submission.setSubmittedAt(LocalDateTime.now());
            submission.setVersion(1);
            submission.setStatus("SUBMITTED");
        }

        return submissionRepository.save(submission);
    }

    public Resource getSubmissionFileResource(AssessmentSubmission submission) {
        if (submission == null || submission.getStoredFileName() == null) {
            throw new IllegalArgumentException("Submission has no stored file.");
        }
        return fileStorageService.loadFileAsResource(submission.getStoredFileName());
    }

    @Transactional(readOnly = true)
    public List<SubmissionAnnotation> getAnnotationsForSubmission(AssessmentSubmission submission) {
        if (submission == null)
            return List.of();
        return annotationRepository.findBySubmissionOrderByPageNumberAsc(submission);
    }

    @Transactional(readOnly = true)
    public List<SubmissionAnnotation> getAnnotationsForSubmissionAndLecturer(AssessmentSubmission submission,
            Lecturer lecturer) {
        if (submission == null || lecturer == null)
            return List.of();
        return annotationRepository.findBySubmissionAndLecturer(submission, lecturer);
    }

    @Transactional
    public SubmissionAnnotation saveOrUpdateAnnotation(AssessmentSubmission submission, Lecturer lecturer,
            Integer pageNumber, String strokesJson) {
        if (submission == null || lecturer == null || pageNumber == null) {
            throw new IllegalArgumentException("Submission, lecturer, and page number are required.");
        }

        Optional<SubmissionAnnotation> existing = annotationRepository
                .findBySubmissionAndLecturerAndPageNumber(submission, lecturer, pageNumber);
        SubmissionAnnotation annotation;
        if (existing.isPresent()) {
            annotation = existing.get();
            annotation.setStrokesJson(strokesJson);
            annotation.setUpdatedAt(LocalDateTime.now());
        } else {
            annotation = new SubmissionAnnotation();
            annotation.setSubmission(submission);
            annotation.setLecturer(lecturer);
            annotation.setPageNumber(pageNumber);
            annotation.setStrokesJson(strokesJson);
            annotation.setUpdatedAt(LocalDateTime.now());
        }
        return annotationRepository.save(annotation);
    }

    @Transactional
    public void saveAllAnnotationsFromJson(AssessmentSubmission submission, Lecturer lecturer,
            String allAnnotationsJson) {
        if (submission == null || lecturer == null || allAnnotationsJson == null || allAnnotationsJson.isBlank()) {
            return;
        }
        try {
            ObjectMapper mapper = new ObjectMapper();
            JsonNode root = mapper.readTree(allAnnotationsJson);
            if (root.isObject()) {
                root.properties().forEach(entry -> {
                    try {
                        int pageNum = Integer.parseInt(entry.getKey());
                        String strokesJson = entry.getValue().toString();
                        saveOrUpdateAnnotation(submission, lecturer, pageNum, strokesJson);
                    } catch (Exception e) {
                        logger.warn("Failed saving annotation for page: " + entry.getKey(), e);
                    }
                });
            }
        } catch (Exception e) {
            logger.error("Failed parsing allAnnotationsJson", e);
        }
    }

    @Transactional(readOnly = true)
    public Map<String, Object> getSubmissionMappingOverview(Assessment assessment) {
        Map<String, Object> result = new LinkedHashMap<>();
        if (assessment == null)
            return result;

        List<LecturerGroupAssignment> groupAssignments = lecturerGroupAssignmentRepository.findByAssessment(assessment);
        List<LecturerRubricAssignment> rubricAssignments = lecturerRubricAssignmentRepository
                .findByAssessment(assessment);
        List<LecturerStudentAssignment> studentAssignments = lecturerStudentAssignmentRepository
                .findByAssessment(assessment);

        String assignmentMode = "GROUP";
        if (!studentAssignments.isEmpty()) {
            assignmentMode = "STUDENT";
        } else if (!rubricAssignments.isEmpty()) {
            assignmentMode = "RUBRIC";
        }
        result.put("assignmentMode", assignmentMode);

        String submissionScope = assessment.getSubmissionScope() != null ? assessment.getSubmissionScope() : "GROUP";
        result.put("submissionScope", submissionScope);

        List<AssessmentSubmission> submissions = submissionRepository.findByAssessment(assessment);
        Map<Long, AssessmentSubmission> groupSubmissionMap = new HashMap<>();
        Map<Long, AssessmentSubmission> studentSubmissionMap = new HashMap<>();
        Map<Long, Integer> doodleCountMap = new HashMap<>();

        for (AssessmentSubmission sub : submissions) {
            if (sub.getGroup() != null) {
                groupSubmissionMap.put(sub.getGroup().getId(), sub);
            }
            if (sub.getStudent() != null) {
                studentSubmissionMap.put(sub.getStudent().getId(), sub);
            }
            int doodleCount = annotationRepository.findBySubmission(sub).size();
            doodleCountMap.put(sub.getId(), doodleCount);
        }

        List<Map<String, Object>> mappingRows = new ArrayList<>();
        Long courseId = assessment.getCourse() != null ? assessment.getCourse().getId() : null;

        if ("GROUP".equalsIgnoreCase(submissionScope)) {
            List<Group> courseGroups = courseId != null ? groupRepository.findAllWithStudentsByCourseId(courseId)
                    : List.of();
            Map<Long, List<Lecturer>> lecturersByGroup = groupAssignments.stream()
                    .filter(a -> a.getGroup() != null && a.getLecturer() != null)
                    .collect(Collectors.groupingBy(a -> a.getGroup().getId(),
                            Collectors.mapping(LecturerGroupAssignment::getLecturer, Collectors.toList())));

            for (Group group : courseGroups) {
                Map<String, Object> row = new LinkedHashMap<>();
                row.put("targetType", "GROUP");
                row.put("targetId", group.getId());
                row.put("targetName", group.getGroupName());
                row.put("students", group.getStudents() != null ? group.getStudents() : List.of());

                List<Lecturer> assignedLecturers = new ArrayList<>();
                String rubricsScopeDescription = "All Assessment Rubrics";

                if ("GROUP".equals(assignmentMode)) {
                    assignedLecturers = lecturersByGroup.getOrDefault(group.getId(), List.of());
                } else if ("RUBRIC".equals(assignmentMode)) {
                    assignedLecturers = rubricAssignments.stream()
                            .map(LecturerRubricAssignment::getLecturer)
                            .filter(Objects::nonNull)
                            .distinct()
                            .collect(Collectors.toList());
                    rubricsScopeDescription = "Assigned by Specific Rubrics";
                } else if ("STUDENT".equals(assignmentMode)) {
                    Set<Long> studentIds = group.getStudents() != null
                            ? group.getStudents().stream().map(Student::getId).collect(Collectors.toSet())
                            : Set.of();
                    assignedLecturers = studentAssignments.stream()
                            .filter(a -> a.getStudent() != null && studentIds.contains(a.getStudent().getId()))
                            .map(LecturerStudentAssignment::getLecturer)
                            .filter(Objects::nonNull)
                            .distinct()
                            .collect(Collectors.toList());
                }

                row.put("assignedLecturers", assignedLecturers);
                row.put("rubricsScope", rubricsScopeDescription);

                AssessmentSubmission sub = groupSubmissionMap.get(group.getId());
                row.put("submission", sub);
                row.put("doodleCount", sub != null ? doodleCountMap.getOrDefault(sub.getId(), 0) : 0);

                mappingRows.add(row);
            }
        } else {
            List<StudentAssessmentAssignment> studentAssignmentsList = studentAssessmentAssignmentRepository
                    .findByAssessment(assessment);
            List<Student> assignedStudents = studentAssignmentsList.stream()
                    .map(StudentAssessmentAssignment::getStudent)
                    .filter(Objects::nonNull)
                    .distinct()
                    .collect(Collectors.toList());

            Map<Long, List<Lecturer>> lecturersByStudent = studentAssignments.stream()
                    .filter(a -> a.getStudent() != null && a.getLecturer() != null)
                    .collect(Collectors.groupingBy(a -> a.getStudent().getId(),
                            Collectors.mapping(LecturerStudentAssignment::getLecturer, Collectors.toList())));

            Map<Long, List<Lecturer>> lecturersByGroup = groupAssignments.stream()
                    .filter(a -> a.getGroup() != null && a.getLecturer() != null)
                    .collect(Collectors.groupingBy(a -> a.getGroup().getId(),
                            Collectors.mapping(LecturerGroupAssignment::getLecturer, Collectors.toList())));

            for (Student student : assignedStudents) {
                Map<String, Object> row = new LinkedHashMap<>();
                row.put("targetType", "STUDENT");
                row.put("targetId", student.getId());
                row.put("targetName",
                        student.getUsername() != null && !student.getUsername().isEmpty() ? student.getUsername()
                                : student.getEmail());
                row.put("email", student.getEmail());
                row.put("matric", student.getEmail() != null ? student.getEmail() : "-");
                row.put("groupName", student.getGroup() != null ? student.getGroup().getGroupName() : "No Group");

                List<Lecturer> assignedLecturers = new ArrayList<>();
                String rubricsScopeDescription = "All Assessment Rubrics";

                if ("STUDENT".equals(assignmentMode)) {
                    assignedLecturers = lecturersByStudent.getOrDefault(student.getId(), List.of());
                } else if ("GROUP".equals(assignmentMode)) {
                    if (student.getGroup() != null) {
                        assignedLecturers = lecturersByGroup.getOrDefault(student.getGroup().getId(), List.of());
                    }
                } else if ("RUBRIC".equals(assignmentMode)) {
                    assignedLecturers = rubricAssignments.stream()
                            .map(LecturerRubricAssignment::getLecturer)
                            .filter(Objects::nonNull)
                            .distinct()
                            .collect(Collectors.toList());
                    rubricsScopeDescription = "Assigned by Specific Rubrics";
                }

                row.put("assignedLecturers", assignedLecturers);
                row.put("rubricsScope", rubricsScopeDescription);

                AssessmentSubmission sub = studentSubmissionMap.get(student.getId());
                row.put("submission", sub);
                row.put("doodleCount", sub != null ? doodleCountMap.getOrDefault(sub.getId(), 0) : 0);

                mappingRows.add(row);
            }
        }

        int totalExpected = mappingRows.size();
        long totalSubmitted = mappingRows.stream().filter(r -> r.get("submission") != null).count();
        long totalPending = totalExpected - totalSubmitted;
        long totalDoodled = mappingRows.stream().filter(r -> (int) r.get("doodleCount") > 0).count();

        Map<String, Object> stats = new LinkedHashMap<>();
        stats.put("totalExpected", totalExpected);
        stats.put("totalSubmitted", totalSubmitted);
        stats.put("totalPending", totalPending);
        stats.put("totalDoodled", totalDoodled);

        result.put("mappings", mappingRows);
        result.put("stats", stats);

        return result;
    }
}
