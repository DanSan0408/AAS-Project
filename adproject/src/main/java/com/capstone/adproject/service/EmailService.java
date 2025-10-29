package com.capstone.adproject.service;

import jakarta.mail.internet.MimeMessage;
import org.springframework.core.io.ClassPathResource;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.mail.javamail.MimeMessageHelper;
import org.springframework.stereotype.Service;

@Service
public class EmailService {
    
    private final JavaMailSender mailSender;
    
    public EmailService(JavaMailSender mailSender) {
        this.mailSender = mailSender;
    }

    /**
     * Sends an HTML email with the password reset link and includes the UTM logo inline.
     */
    public void sendResetPasswordEmail(String userEmail, String link) {
        try {
            MimeMessage message = mailSender.createMimeMessage();
            // true flag indicates multipart message (for HTML and inline resources like images)
            MimeMessageHelper helper = new MimeMessageHelper(message, true, "UTF-8"); 

            // Replace with your 'from' email
            helper.setFrom("noreply@yourdomain.com"); 
            helper.setTo(userEmail);
            helper.setSubject("Password Reset Request");
            
            // --- HTML Content for Email ---
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
                + "<p style='margin-top: 20px; font-size: 12px; color: #95a5a6;'>&copy; Capstone Project Application</p>"
                + "</body></html>";
            
            helper.setText(htmlContent, true); // Set content as HTML

            // Add the image resource inline.
            // Assumes utmlogo.png is located in src/main/resources/static/images/
            ClassPathResource logoResource = new ClassPathResource("static/images/utmlogo.png");
            helper.addInline("logo", logoResource); // 'logo' matches 'cid:logo' in HTML img tag

            mailSender.send(message);
        } catch (Exception e) {
            // Log the exception for debugging mail issues
            e.printStackTrace();
            throw new RuntimeException("Failed to send HTML password reset email", e);
        }
    }
}
