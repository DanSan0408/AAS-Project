package com.capstone.adproject.service;

import java.time.LocalDate;
import java.time.ZoneId;
import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import com.capstone.adproject.model.Assessment;
import com.capstone.adproject.model.Deadline;
import com.capstone.adproject.model.Student;
import com.capstone.adproject.repositories.LecturerGroupAssignmentRepository;
import com.capstone.adproject.repositories.LecturerRubricAssignmentRepository;
import com.capstone.adproject.repositories.StudentRepository;

@Service
public class DeadlineNotificationService {

    private static final Logger logger = LoggerFactory.getLogger(DeadlineNotificationService.class);

    private final DeadlineService deadlineService;
    private final AssessmentService assessmentService;
    private final StudentRepository studentRepository;
    private final LecturerGroupAssignmentRepository groupAssignmentRepository;
    private final LecturerRubricAssignmentRepository rubricAssignmentRepository;
    private final EmailService emailService;

    public DeadlineNotificationService(
            DeadlineService deadlineService,
            AssessmentService assessmentService,
            StudentRepository studentRepository,
            LecturerGroupAssignmentRepository groupAssignmentRepository,
            LecturerRubricAssignmentRepository rubricAssignmentRepository,
            EmailService emailService) {
        this.deadlineService = deadlineService;
        this.assessmentService = assessmentService;
        this.studentRepository = studentRepository;
        this.groupAssignmentRepository = groupAssignmentRepository;
        this.rubricAssignmentRepository = rubricAssignmentRepository;
        this.emailService = emailService;
    }

    // Runs every day at 8:00 AM server time
    @Scheduled(cron = "0 0 8 * * ?")
    public void checkDeadlinesAndSendNotifications() {
        logger.info("Running daily deadline notification check...");
        LocalDate today = LocalDate.now();
        LocalDate tomorrow = today.plusDays(1);

        List<Deadline> deadlines = deadlineService.getAllDeadlines();

        for (Deadline deadline : deadlines) {
            if (deadline.getAssessmentId() == null) continue;

            Optional<Assessment> assessmentOpt = assessmentService.getAssessmentById(deadline.getAssessmentId());
            if (assessmentOpt.isEmpty()) continue;
            Assessment assessment = assessmentOpt.get();

            LocalDate openDate = deadline.getOpenDate() != null ? 
                deadline.getOpenDate().toInstant().atZone(ZoneId.systemDefault()).toLocalDate() : null;
            LocalDate closeDate = deadline.getDate() != null ? 
                deadline.getDate().toInstant().atZone(ZoneId.systemDefault()).toLocalDate() : null;

            boolean isOpenToday = openDate != null && openDate.equals(today);
            boolean isClosingTomorrow = closeDate != null && closeDate.equals(tomorrow);

            if (isOpenToday) {
                sendNotifications(deadline, assessment, "OPEN");
            }
            if (isClosingTomorrow) {
                sendNotifications(deadline, assessment, "CLOSING_SOON");
            }
        }
    }

    private void sendNotifications(Deadline deadline, Assessment assessment, String notificationType) {
        Set<String> recipientEmails = new HashSet<>();
        String assessorType = deadline.getAssessorType(); 

        // 1. Collect students if the assessment targets students or everyone (null)
        if (assessorType == null || "STUDENT".equalsIgnoreCase(assessorType)) {
            if (assessment.getCourse() != null) {
                List<Student> students = studentRepository.findAll().stream()
                    .filter(s -> s.getCourse() != null && s.getCourse().getId().equals(assessment.getCourse().getId()))
                    .collect(Collectors.toList());
                for (Student s : students) {
                    if (s.getEmail() != null) recipientEmails.add(s.getEmail());
                }
            }
        }

        // 2. Collect lecturers/supervisors assigned to this assessment
        if (assessorType == null || "LECTURER".equalsIgnoreCase(assessorType) || "SUPERVISOR".equalsIgnoreCase(assessorType)) {
            groupAssignmentRepository.findByAssessment(assessment).forEach(assignment -> {
                if (assignment.getLecturer() != null && assignment.getLecturer().getEmail() != null) {
                    recipientEmails.add(assignment.getLecturer().getEmail());
                }
            });
            
            rubricAssignmentRepository.findByAssessment(assessment).forEach(assignment -> {
                if (assignment.getLecturer() != null && assignment.getLecturer().getEmail() != null) {
                    recipientEmails.add(assignment.getLecturer().getEmail());
                }
            });
        }

        if (recipientEmails.isEmpty()) return;

        // 3. Draft Email Content
        String subject;
        String message;
        String title = deadline.getTitle() != null && !deadline.getTitle().isEmpty() ? deadline.getTitle() : assessment.getTitle();

        if ("OPEN".equals(notificationType)) {
            subject = "Assessment Now Open: " + title;
            message = "Hello,\n\nThe assessment '" + title + "' is now open for evaluation.\n"
                    + "Please log in to the Assessment Administration System to complete your tasks.\n\n"
                    + "Deadline closes on: " + deadline.getDate() + "\n\nBest regards,\nUTM AAS Admin";
        } else {
            subject = "Action Required - Assessment Closing Tomorrow: " + title;
            message = "Hello,\n\nThis is a friendly reminder that the deadline for the assessment '" 
                    + title + "' is closing tomorrow (" + deadline.getDate() + ").\n"
                    + "Please ensure all your evaluations are submitted before the deadline.\n\nBest regards,\nUTM AAS Admin";
        }

        // 4. Send Emails
        for (String email : recipientEmails) {
            try {
                // NOTE: Change `sendEmail` if your EmailService method is named something else (e.g. sendSimpleMessage)
                emailService.sendDeadlineEmail(email, subject, message);
            } catch (Exception e) {
                logger.error("Failed to send deadline notification to {}", email, e);
            }
        }
    }
}