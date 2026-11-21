package com.capstone.adproject.service;

import java.util.List;
import java.util.function.Consumer;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.ClassPathResource;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.mail.javamail.MimeMessageHelper;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import org.springframework.web.util.HtmlUtils;

import jakarta.mail.internet.MimeMessage;

@Service
public class EmailService {
    
    private static final Logger logger = LoggerFactory.getLogger(EmailService.class);
    
    private final JavaMailSender mailSender;

    @Value("${spring.mail.from:${spring.mail.username:admin@example.com}}")
    private String mailFrom;

    @Value("${spring.mail.username:}")
    private String mailUsername;

    @Value("${spring.mail.password:}")
    private String mailPassword;
    
    public EmailService(JavaMailSender mailSender) {
        this.mailSender = mailSender;
    }

    private boolean mailIsConfigured() {
        if (mailUsername == null || mailUsername.isBlank()
                || mailPassword == null || mailPassword.isBlank()) {
            logger.error("Email delivery is disabled: SPRING_MAIL_USERNAME and SPRING_MAIL_PASSWORD must be configured");
            return false;
        }
        if (mailFrom == null || mailFrom.isBlank()
                || "admin@example.com".equalsIgnoreCase(mailFrom.trim())) {
            mailFrom = mailUsername.trim();
        }
        return true;
    }

    /** The From address that will actually be used (mirrors the fallback in {@link #mailIsConfigured()}). */
    public String effectiveFromAddress() {
        boolean fallsBack = mailFrom == null || mailFrom.isBlank() || "admin@example.com".equalsIgnoreCase(mailFrom.trim());
        return fallsBack ? (mailUsername == null ? "" : mailUsername.trim()) : mailFrom.trim();
    }

    /**
     * Describes why outgoing mail cannot work, or returns null when the setup looks usable. Shown on the Super Admin
     * monitoring page and logged at startup, so a bad SPRING_MAIL_* value is visible straight after a deploy instead
     * of only in the mail provider's own log.
     */
    public String mailConfigProblem() {
        if (mailUsername == null || mailUsername.isBlank() || mailPassword == null || mailPassword.isBlank()) {
            return "SPRING_MAIL_USERNAME / SPRING_MAIL_PASSWORD are not set, so no email can be sent.";
        }
        String from = effectiveFromAddress();
        if (from.equalsIgnoreCase(mailUsername.trim()) && mailUsername.trim().toLowerCase().endsWith("@smtp-brevo.com")) {
            return "The From address is the Brevo SMTP login (" + from + "), which Brevo rejects as an invalid sender. "
                    + "Set SPRING_MAIL_FROM to a verified sender on your authenticated domain and redeploy.";
        }
        return null;
    }

    @org.springframework.context.event.EventListener(org.springframework.boot.context.event.ApplicationReadyEvent.class)
    public void logMailConfiguration() {
        String problem = mailConfigProblem();
        if (problem == null) {
            logger.info("Mail configured: sending as {}", effectiveFromAddress());
        } else {
            logger.warn("MAIL CONFIGURATION PROBLEM: {}", problem);
        }
    }

    @Async("emailTaskExecutor")
    public void sendResetPasswordEmail(String userEmail, String link) {
        if (!mailIsConfigured()) {
            return;
        }
        try {
            MimeMessage message = mailSender.createMimeMessage();
            MimeMessageHelper helper = new MimeMessageHelper(message, true, "UTF-8"); 

            helper.setFrom(mailFrom); 
            helper.setTo(userEmail);
            helper.setSubject("Password Reset Request");
            
            String htmlContent = "<html><body style='font-family: Arial, sans-serif; background-color: #f3f4f6; padding: 20px; text-align: center;'>"
                + "<div style='max-width: 600px; margin: 0 auto; background-color: #ffffff; padding: 25px; border-radius: 12px; box-shadow: 0 4px 15px rgba(0, 0, 0, 0.1);'>"
                + "<img src='cid:logo' alt='UTM Logo' style='max-width: 150px; height: auto; margin-bottom: 25px; display: block; margin-left: auto; margin-right: auto;'>"
                + "<h2 style='color: #2c3e50; margin-bottom: 20px;'>Password Reset Request</h2>"
                + "<p style='color: #34495e; font-size: 16px; line-height: 1.5;'>We received a request to reset the password for your account. Please click the button below to continue.</p>"
                + "<div style='margin: 30px 0;'>"
                + "<a href='" + link + "' style='background-color: #3498db; color: white; padding: 14px 28px; text-decoration: none; border-radius: 8px; font-weight: bold; font-size: 16px; display: inline-block; transition: background-color 0.3s ease;'>"
                + "Reset My Password"
                + "</a></div>"
                + "<p style='color: #34495e; font-size: 14px;'>The link will expire shortly for security reasons.</p>"
                + "<p style='color: #e74c3c; font-size: 14px; margin-top: 30px;'>If you did not request a password reset, please ignore this email.</p>"
                + "</div>"
                + "<p style='margin-top: 20px; font-size: 12px; color: #95a5a6;'>&copy; Danial Ihsan</p>"
                + "</body></html>";
            
            helper.setText(htmlContent, true);

            ClassPathResource logoResource = new ClassPathResource("static/images/logoUTM.png");
            helper.addInline("logo", logoResource);

            mailSender.send(message);
            logger.info("Sent password reset email to {}", userEmail);
        } catch (Exception e) {
            logger.error("Failed to send HTML password reset email to: {}", userEmail, e);
        }
    }

    /** One welcome email to deliver; see {@link #sendWelcomeEmailWithPassword} for a null {@code temporaryPassword}. */
    public record WelcomeEmail(String email, String temporaryPassword, String userType, String resetLink) {}

    /**
     * Sends welcome emails one after another on the email thread, calling {@code onSent} with the address each
     * time the mail server accepts a message so the caller can record the delivery.
     */
    @Async("emailTaskExecutor")
    public void sendWelcomeEmailsInBackground(List<WelcomeEmail> mails, Consumer<String> onSent) {
        int sent = 0;
        for (WelcomeEmail mail : mails) {
            try {
                if (sendWelcomeEmailWithPassword(mail.email(), mail.temporaryPassword(), mail.userType(), mail.resetLink())) {
                    sent++;
                    onSent.accept(mail.email());
                }
            } catch (RuntimeException e) {
                logger.error("Welcome email was sent to {} but recording it failed", mail.email(), e);
            }
        }
        if (sent < mails.size()) {
            logger.error("Welcome email batch finished: {} of {} sent", sent, mails.size());
        } else {
            logger.info("Welcome email batch finished: {} of {} sent", sent, mails.size());
        }
    }

    /**
     * Sends the welcome email. When {@code temporaryPassword} is null (a re-sent welcome for an existing account,
     * whose stored password cannot be recovered and is left unchanged) the message carries only the
     * set-password link.
     *
     * @return true when the mail server accepted the message
     */
    public boolean sendWelcomeEmailWithPassword(String userEmail, String temporaryPassword, String userType, String resetLink) {
        if (!mailIsConfigured()) {
            return false;
        }
        try {
            MimeMessage message = mailSender.createMimeMessage();
            MimeMessageHelper helper = new MimeMessageHelper(message, true, "UTF-8");

            helper.setFrom(mailFrom);
            helper.setTo(userEmail);
            helper.setSubject("Welcome to UTM CAS - Your Account Has Been Created");

            boolean hasPassword = temporaryPassword != null;

            String htmlContent = "<html><body style='font-family: Arial, sans-serif; background-color: #f3f4f6; padding: 20px;'>"
                + "<div style='max-width: 600px; margin: 0 auto; background-color: #ffffff; padding: 30px; border-radius: 12px; box-shadow: 0 4px 15px rgba(0, 0, 0, 0.1);'>"
                + "<img src='cid:logo' alt='UTM Logo' style='max-width: 150px; height: auto; margin-bottom: 25px; display: block; margin-left: auto; margin-right: auto;'>"
                + "<h2 style='color: #2c3e50; margin-bottom: 20px; text-align: center;'>Welcome to UTM CAS!</h2>"
                + "<p style='color: #34495e; font-size: 16px; line-height: 1.6; text-align: center;'>Your account as a <strong>" + userType + "</strong> has been successfully created.</p>"
                + "<div style='background-color: #ecf0f1; padding: 20px; border-radius: 8px; margin: 25px 0;'>"
                + "<h3 style='color: #2c3e50; margin-top: 0;'>Your Login Credentials</h3>"
                + "<p style='margin: 10px 0;'><strong>Email:</strong> " + userEmail + "</p>"
                + (hasPassword
                    ? "<p style='margin: 10px 0;'><strong>Temporary Password:</strong> <code style='background-color: #fff; padding: 5px 10px; border-radius: 4px; font-size: 16px; color: #e74c3c;'>" + HtmlUtils.htmlEscape(temporaryPassword) + "</code></p>"
                    : "<p style='margin: 10px 0;'>Choose your own password using the button below.</p>")
                + "</div>"
                + (hasPassword
                    ? "<p style='color: #e67e22; font-size: 14px; text-align: center; margin: 20px 0;'><strong>⚠️ IMPORTANT:</strong> This is a temporary password. For security reasons, please change it immediately.</p>"
                    : "")
                + "<div style='text-align: center; margin: 30px 0;'>"
                + "<a href='" + resetLink + "' style='background-color: #e74c3c; color: white; padding: 14px 28px; text-decoration: none; border-radius: 8px; font-weight: bold; font-size: 16px; display: inline-block;'>"
                + (hasPassword ? "Change Password Now" : "Set My Password")
                + "</a></div>"
                + (hasPassword
                    ? "<p style='color: #7f8c8d; font-size: 14px; text-align: center;'>You can also login with your temporary password and change it later from your profile settings.</p>"
                    : "<p style='color: #7f8c8d; font-size: 14px; text-align: center;'>If the link has expired, use &quot;Forgot password&quot; on the login page. Already signed in? You can ignore this email &mdash; your account has not changed.</p>")
                + "<hr style='border: none; border-top: 1px solid #ecf0f1; margin: 30px 0;'>"
                + "<p style='color: #95a5a6; font-size: 12px; text-align: center;'>If you did not expect this email, please contact your system administrator.</p>"
                + "</div>"
                + "<p style='margin-top: 20px; font-size: 12px; color: #95a5a6; text-align: center;'>&copy; Danial Ihsan</p>"
                + "</body></html>";

            helper.setText(htmlContent, true);

            ClassPathResource logoResource = new ClassPathResource("static/images/logoUTM.png");
            helper.addInline("logo", logoResource);

            mailSender.send(message);
            logger.info("Sent welcome email to {}", userEmail);
            return true;
        } catch (Exception e) {
            logger.error("Failed to send welcome email with password to: {}", userEmail, e);
            return false;
        }
    }

    @Async("emailTaskExecutor")
    public void sendDeadlineEmail(String userEmail, String subject, String textMessage) {
        if (!mailIsConfigured()) {
            return;
        }
        try {
            MimeMessage message = mailSender.createMimeMessage();
            MimeMessageHelper helper = new MimeMessageHelper(message, true, "UTF-8");

            helper.setFrom(mailFrom);
            helper.setTo(userEmail);
            helper.setSubject(subject);

            // Convert plain text newlines to HTML breaks
            String htmlMessage = textMessage.replace("\n", "<br>");

            String htmlContent = "<html><body style='font-family: Arial, sans-serif; background-color: #f3f4f6; padding: 20px;'>"
                + "<div style='max-width: 600px; margin: 0 auto; background-color: #ffffff; padding: 30px; border-radius: 12px; box-shadow: 0 4px 15px rgba(0, 0, 0, 0.1);'>"
                + "<img src='cid:logo' alt='UTM Logo' style='max-width: 150px; height: auto; margin-bottom: 25px; display: block; margin-left: auto; margin-right: auto;'>"
                + "<h2 style='color: #2c3e50; margin-bottom: 20px; text-align: center; border-bottom: 2px solid #ecf0f1; padding-bottom: 10px;'>" + subject + "</h2>"
                + "<div style='color: #34495e; font-size: 16px; line-height: 1.6;'>"
                + htmlMessage
                + "</div>"
                + "<hr style='border: none; border-top: 1px solid #ecf0f1; margin: 30px 0;'>"
                + "<p style='color: #95a5a6; font-size: 12px; text-align: center;'>This is an automated notification from the UTM Assessment Administration System.</p>"
                + "</div>"
                + "<p style='margin-top: 20px; font-size: 12px; color: #95a5a6; text-align: center;'>&copy; Danial Ihsan</p>"
                + "</body></html>";

            helper.setText(htmlContent, true);

            ClassPathResource logoResource = new ClassPathResource("static/images/logoUTM.png");
            helper.addInline("logo", logoResource);

            mailSender.send(message);
            logger.info("Sent deadline email to {}", userEmail);
        } catch (Exception e) {
            logger.error("Failed to send deadline email to: {}", userEmail, e);
        }
    }

    /**
     * Sends a generic alert email with a standardized template.
     *
     * @param userEmail The recipient's email address.
     * @param subject The subject line of the email.
     * @param textMessage The main body content of the alert (plain text, will be converted to HTML).
     */
    @Async("emailTaskExecutor")
    public void sendAlertEmail(String userEmail, String subject, String textMessage) {
        if (!mailIsConfigured()) {
            return;
        }
        try {
            MimeMessage message = mailSender.createMimeMessage();
            MimeMessageHelper helper = new MimeMessageHelper(message, true, "UTF-8");

            helper.setFrom(mailFrom);
            helper.setTo(userEmail);
            helper.setSubject(subject);

            String htmlMessage = textMessage.replace("\n", "<br>");

            String htmlContent = "<html><body style='font-family: Arial, sans-serif; background-color: #f3f4f6; padding: 20px;'>"
                + "<div style='max-width: 600px; margin: 0 auto; background-color: #ffffff; padding: 30px; border-radius: 12px; box-shadow: 0 4px 15px rgba(0, 0, 0, 0.1);'>"
                + "<h2 style='color: #e74c3c; margin-bottom: 20px; text-align: center; border-bottom: 2px solid #e74c3c; padding-bottom: 10px;'>🚨 " + subject + "</h2>"
                + "<div style='color: #34495e; font-size: 16px; line-height: 1.6;'>"
                + htmlMessage
                + "</div>"
                + "<hr style='border: none; border-top: 1px solid #ecf0f1; margin: 30px 0;'>"
                + "<p style='color: #95a5a6; font-size: 12px; text-align: center;'>This is an automated alert from your Application Monitoring System.</p>"
                + "</div></body></html>";
            helper.setText(htmlContent, true);
            mailSender.send(message);
            logger.info("Sent alert email to {}", userEmail);
        } catch (Exception e) {
            logger.error("Failed to send alert email to {}: {}", userEmail, e.getMessage(), e);
        }
    }
}