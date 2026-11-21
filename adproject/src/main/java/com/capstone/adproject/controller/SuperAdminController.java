package com.capstone.adproject.controller;

import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;

import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import com.capstone.adproject.config.LegacyColumnCleanupMigrationRunner;
import com.capstone.adproject.model.Announcement;
import com.capstone.adproject.model.Course;
import com.capstone.adproject.model.Lecturer;
import com.capstone.adproject.service.AlertingService;
import com.capstone.adproject.service.AnnouncementService;
import com.capstone.adproject.service.AssessmentCommentService;
import com.capstone.adproject.service.CommentScanScheduler;
import com.capstone.adproject.service.MonitoringService;
import com.capstone.adproject.service.SuperAdminService;

import jakarta.servlet.http.HttpServletRequest;

@Controller
@RequestMapping("/superadmin")
public class SuperAdminController {

    private static final String RESET_CONFIRMATION_TEXT = "WIPE_EVERYTHING_EXCEPT_SUPER_ADMIN";

    private final SuperAdminService superAdminService;
    private final MonitoringService monitoringService;
    private final AlertingService alertingService;
    private final LegacyColumnCleanupMigrationRunner schemaGuard;
    private final AnnouncementService announcementService;
    private final AssessmentCommentService commentService;
    private final CommentScanScheduler commentScanScheduler;

    public SuperAdminController(SuperAdminService superAdminService, MonitoringService monitoringService,
                                AlertingService alertingService, LegacyColumnCleanupMigrationRunner schemaGuard,
                                AnnouncementService announcementService, AssessmentCommentService commentService,
                                CommentScanScheduler commentScanScheduler) {
        this.commentService = commentService;
        this.commentScanScheduler = commentScanScheduler;
        this.superAdminService = superAdminService;
        this.monitoringService = monitoringService;
        this.alertingService = alertingService;
        this.schemaGuard = schemaGuard;
        this.announcementService = announcementService;
    }

    /** System health, recent failures, database schema findings and alert channels. */
    @GetMapping("/monitoring")
    public String monitoring(Model model) {
        model.addAttribute("snapshot", monitoringService.snapshot());
        model.addAttribute("telegramConfigured", alertingService.isTelegramConfigured());
        model.addAttribute("emailAlertConfigured", alertingService.isEmailAlertConfigured());
        model.addAttribute("schemaCheckedAt", MonitoringService.formatUtc(schemaGuard.getLastRunAt()));
        model.addAttribute("superAdminUsername", getLoggedInUsername());
        return "superadmin_monitoring";
    }

    @PostMapping("/monitoring/test-alert")
    public String sendTestAlert(RedirectAttributes redirectAttributes) {
        String result = alertingService.notifyAll("[TEST] IAMS alert channel check",
                "This is a test alert requested by " + getLoggedInUsername() + " from the monitoring page.");
        redirectAttributes.addFlashAttribute("successMessage", "Test alert attempted: " + result + ".");
        return "redirect:/superadmin/monitoring";
    }

    @PostMapping("/monitoring/recheck-schema")
    public String recheckSchema(RedirectAttributes redirectAttributes) {
        schemaGuard.guardSchema();
        redirectAttributes.addFlashAttribute("successMessage", "Database schema re-checked.");
        return "redirect:/superadmin/monitoring";
    }

    // ---- AI comment check: is it any good? (developer tuning) ------------------------------------------------

    /** How the AI comment check compares with what moderators decided, and what the automatic scan has been doing. */
    @GetMapping("/ai-comments")
    public String aiComments(Model model) {
        model.addAttribute("report", commentService.aiAgreementReport());
        model.addAttribute("pendingRead", commentService.pendingReadByAi());
        model.addAttribute("pendingUnread", commentService.pendingNotReadByAi());
        model.addAttribute("scanEnabled", commentScanScheduler.isEnabled());
        model.addAttribute("lastRun", commentScanScheduler.lastRun());
        model.addAttribute("superAdminUsername", getLoggedInUsername());
        return "superadmin_ai_comments";
    }

    @PostMapping("/ai-comments/scan-now")
    public String scanNow(RedirectAttributes redirectAttributes) {
        CommentScanScheduler.RunSummary run = commentScanScheduler.runOnce(true);
        boolean ok = "OK".equals(run.status());
        redirectAttributes.addFlashAttribute(ok ? "successMessage" : "errorMessage",
                "Scan " + run.status().toLowerCase() + ": read " + run.read() + ", flagged " + run.flagged()
                        + (run.notChecked() > 0 ? ", could not check " + run.notChecked() : "")
                        + (run.note().isEmpty() ? "." : ". " + run.note()));
        return "redirect:/superadmin/ai-comments";
    }

    // ---- Sign-in announcements ------------------------------------------------------------------------------

    private static final DateTimeFormatter DATETIME_INPUT = DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm");

    @GetMapping("/announcements")
    public String announcements(@RequestParam(name = "edit", required = false) Long editId, Model model) {
        Announcement form = editId == null ? new Announcement()
                : announcementService.findById(editId).orElseGet(Announcement::new);
        return announcementsPage(model, form);
    }

    @PostMapping("/announcements")
    public String saveAnnouncement(@RequestParam(name = "id", required = false) Long id,
                                   @RequestParam("title") String title,
                                   @RequestParam("message") String message,
                                   @RequestParam(name = "category", required = false) Announcement.Category category,
                                   @RequestParam(name = "audience", required = false) Announcement.Audience audience,
                                   @RequestParam(name = "courseId", required = false) Long courseId,
                                   @RequestParam(name = "active", defaultValue = "false") boolean active,
                                   @RequestParam(name = "startsAt", required = false)
                                   @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime startsAt,
                                   @RequestParam(name = "endsAt", required = false)
                                   @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime endsAt,
                                   Model model, RedirectAttributes redirectAttributes) {
        try {
            announcementService.save(id, title, message, category, audience, courseId, active, startsAt, endsAt, getLoggedInUsername());
        } catch (IllegalArgumentException e) {
            // Show the form again with what was typed, so nothing has to be retyped.
            Announcement typed = new Announcement();
            typed.setId(id);
            typed.setTitle(title);
            typed.setMessage(message);
            typed.setCategory(category == null ? Announcement.Category.NOTICE : category);
            typed.setAudience(audience == null ? Announcement.Audience.ALL : audience);
            typed.setCourseId(courseId);
            typed.setActive(active);
            typed.setStartsAt(startsAt);
            typed.setEndsAt(endsAt);
            model.addAttribute("errorMessage", e.getMessage());
            return announcementsPage(model, typed);
        }
        redirectAttributes.addFlashAttribute("successMessage", id == null
                ? "Announcement published. People will see it the next time they sign in."
                : "Announcement updated.");
        return "redirect:/superadmin/announcements";
    }

    @PostMapping("/announcements/{id}/toggle")
    public String toggleAnnouncement(@PathVariable("id") Long id, RedirectAttributes redirectAttributes) {
        try {
            boolean on = announcementService.toggle(id);
            redirectAttributes.addFlashAttribute("successMessage", on ? "Announcement switched on." : "Announcement switched off.");
        } catch (IllegalArgumentException e) {
            redirectAttributes.addFlashAttribute("errorMessage", e.getMessage());
        }
        return "redirect:/superadmin/announcements";
    }

    @PostMapping("/announcements/{id}/delete")
    public String deleteAnnouncement(@PathVariable("id") Long id, RedirectAttributes redirectAttributes) {
        announcementService.delete(id);
        redirectAttributes.addFlashAttribute("successMessage", "Announcement deleted.");
        return "redirect:/superadmin/announcements";
    }

    private String announcementsPage(Model model, Announcement form) {
        model.addAttribute("announcements", announcementService.findAll());
        model.addAttribute("form", form);
        model.addAttribute("startsAtInput", form.getStartsAt() == null ? "" : form.getStartsAt().format(DATETIME_INPUT));
        model.addAttribute("endsAtInput", form.getEndsAt() == null ? "" : form.getEndsAt().format(DATETIME_INPUT));
        model.addAttribute("categories", Announcement.Category.values());
        model.addAttribute("audiences", Announcement.Audience.values());
        java.util.List<com.capstone.adproject.model.Course> courses = superAdminService.getAllCourses();
        model.addAttribute("courses", courses);
        java.util.Map<Long, String> courseNames = new java.util.HashMap<>();
        for (com.capstone.adproject.model.Course c : courses) {
            courseNames.put(c.getId(), (c.getCourseCode() != null ? c.getCourseCode() + " - " : "") + (c.getCourseName() != null ? c.getCourseName() : ""));
        }
        model.addAttribute("courseNames", courseNames);
        model.addAttribute("now", LocalDateTime.now());
        model.addAttribute("serverZone", ZoneId.systemDefault().getId());
        model.addAttribute("superAdminUsername", getLoggedInUsername());
        return "superadmin_announcements";
    }

    private String getLoggedInUsername() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication != null) {
            return authentication.getName();
        }
        return "Super Admin";
    }

    @GetMapping("/home")
    public String superAdminHome(Model model) {
        String username = getLoggedInUsername();
        model.addAttribute("superAdminUsername", username);

        var managedCourses = superAdminService.getManagedCoursesForAdminEmail(username);
        model.addAttribute("managedCourses", managedCourses);
        model.addAttribute("managedCourseCount", managedCourses.size());
        
        // Get the assigned course for this user
        var assignedCourse = superAdminService.getUserCourse(username);
        if (assignedCourse.isPresent()) {
            model.addAttribute("assignedCourse", assignedCourse.get());
        } else {
            model.addAttribute("assignedCourse", new Course());
        }
        
        // Get all courses with their statistics
        var allCourses = superAdminService.getAllCourses();
        var courseStatistics = allCourses.stream()
            .map(course -> superAdminService.getCourseSummary(course.getId()))
            .toList();
        model.addAttribute("courseStatistics", courseStatistics);
        
        return "superadmin_home";
    }

    @GetMapping("/manage-courses")
    public String manageCourses(Model model) {
        model.addAttribute("courses", superAdminService.getAllCourses());
        model.addAttribute("newCourse", new Course());
        model.addAttribute("superAdminUsername", getLoggedInUsername());
        return "manage_courses";
    }

    @PostMapping("/courses/add")
    public String addCourse(@ModelAttribute Course course, RedirectAttributes redirectAttributes) {
        try {
            String username = getLoggedInUsername();
            // The ensureAdminAssignable method was removed. We now find the lecturer record directly.
            Lecturer lecturer = superAdminService.resolveLecturerByIdentity(username)
                .orElseThrow(() -> new IllegalStateException("Could not find an assessor profile for the current super admin."));
            course.setCreatedBy(lecturer);
            Course savedCourse = superAdminService.saveCourse(course);
            
            // Auto-assign super admin to the course they created
            superAdminService.assignAdminToCourse(lecturer.getId(), savedCourse.getId());
            
            redirectAttributes.addFlashAttribute("successMessage", "Course added successfully!");
        } catch (Exception e) {
            redirectAttributes.addFlashAttribute("errorMessage", "Error adding course: " + e.getMessage());
        }
        return "redirect:/superadmin/manage-courses";
    }

    @GetMapping("/courses/edit/{id}")
    public String editCourse(@PathVariable("id") Long id, Model model, RedirectAttributes redirectAttributes) {
        return superAdminService.getCourseById(id).map(course -> {
            model.addAttribute("course", course);
            model.addAttribute("superAdminUsername", getLoggedInUsername());
            return "edit_course"; // A new template for editing a course
        }).orElseGet(() -> {
            redirectAttributes.addFlashAttribute("errorMessage", "Course not found.");
            return "redirect:/superadmin/manage-courses";
        });
    }

    @PostMapping("/courses/update")
    public String updateCourse(@ModelAttribute Course course, RedirectAttributes redirectAttributes) {
        try {
            superAdminService.saveCourse(course);
            redirectAttributes.addFlashAttribute("successMessage", "Course updated successfully!");
        } catch (Exception e) {
            redirectAttributes.addFlashAttribute("errorMessage", "Error updating course: " + e.getMessage());
        }
        return "redirect:/superadmin/manage-courses";
    }

    @PostMapping("/courses/delete/{id}")
    public String deleteCourse(@PathVariable("id") Long id, RedirectAttributes redirectAttributes) {
        try {
            superAdminService.deleteCourse(id);
            redirectAttributes.addFlashAttribute("successMessage", "Course deleted successfully!");
        } catch (Exception e) {
            redirectAttributes.addFlashAttribute("errorMessage", "Error deleting course: " + e.getMessage());
        }
        return "redirect:/superadmin/manage-courses";
    }

    @GetMapping("/invite-admin")
    public String inviteAdminForm(Model model) {
        var admins = superAdminService.getAllAdminLecturers();
        model.addAttribute("courses", superAdminService.getAllCourses());
        model.addAttribute("admins", admins);
        model.addAttribute("adminManagedCoursesMap", superAdminService.getAdminManagedCoursesMap(admins));
        model.addAttribute("superAdminUsername", getLoggedInUsername());
        return "invite_admin"; // A new template for inviting admins
    }

    @PostMapping("/invite-admin")
    public String inviteAdmin(@RequestParam String email,
                              @RequestParam String name,
                              @RequestParam(required = false) Long courseId,
                              HttpServletRequest request,
                              RedirectAttributes redirectAttributes) {
        try {
            boolean emailSent = superAdminService.inviteAdmin(email, name, courseId, request);
            if (emailSent) {
                redirectAttributes.addFlashAttribute("successMessage", "Admin invitation sent to " + email);
            } else {
                redirectAttributes.addFlashAttribute("errorMessage",
                        "Admin account created, but the welcome email could not be sent. Check SMTP configuration.");
            }
        } catch (Exception e) {
            redirectAttributes.addFlashAttribute("errorMessage", "Error inviting admin: " + e.getMessage());
        }
        return "redirect:/superadmin/invite-admin";
    }

    @PostMapping("/admins/delete/{id}")
    public String deleteAdmin(@PathVariable("id") Long id, RedirectAttributes redirectAttributes) {
        try {
            superAdminService.deleteAdminById(id, getLoggedInUsername());
            redirectAttributes.addFlashAttribute("successMessage", "Admin deleted successfully.");
        } catch (Exception e) {
            redirectAttributes.addFlashAttribute("errorMessage", "Error deleting admin: " + e.getMessage());
        }
        return "redirect:/superadmin/invite-admin";
    }

    @PostMapping("/reset-data")
    public String resetData(
            @RequestParam("confirmationText") String confirmationText,
            RedirectAttributes redirectAttributes) {
        if (confirmationText == null || !RESET_CONFIRMATION_TEXT.equals(confirmationText.trim())) {
            redirectAttributes.addFlashAttribute("errorMessage",
                    "Reset aborted. Please type the exact confirmation text: " + RESET_CONFIRMATION_TEXT);
            return "redirect:/superadmin/manage-courses";
        }

        try {
            superAdminService.resetToSuperAdminOnly();
            redirectAttributes.addFlashAttribute("successMessage",
                    "System reset complete. All data has been wiped except super admin account(s).");
        } catch (Exception e) {
            redirectAttributes.addFlashAttribute("errorMessage", "Reset failed: " + e.getMessage());
        }

        return "redirect:/superadmin/manage-courses";
    }
}