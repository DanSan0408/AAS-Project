package com.capstone.adproject.service;

import org.springframework.core.io.ClassPathResource;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.mail.javamail.MimeMessageHelper;
import org.springframework.stereotype.Service;

import jakarta.mail.internet.MimeMessage;

@Service
public class EmailService {
    
    private final JavaMailSender mailSender;
    
    public EmailService(JavaMailSender mailSender) {
        this.mailSender = mailSender;
    }

    /**
     * ✅ EXISTING: Sends password reset email with link
     */
    public void sendResetPasswordEmail(String userEmail, String link) {
        try {
            MimeMessage message = mailSender.createMimeMessage();
            MimeMessageHelper helper = new MimeMessageHelper(message, true, "UTF-8"); 

            helper.setFrom("noreply@yourdomain.com"); 
            helper.setTo(userEmail);
            helper.setSubject("Password Reset Request");
            
            // HTML content uses 'cid:logo' to reference the inline image attachment
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

            // ✅ CHANGED: Updated filename to logoUTM.png
            ClassPathResource logoResource = new ClassPathResource("static/images/logoUTM.png");
            helper.addInline("logo", logoResource);

            mailSender.send(message);
        } catch (Exception e) {
            e.printStackTrace();
            throw new RuntimeException("Failed to send HTML password reset email", e);
        }
    }

    /**
     * ✅ NEW: Sends welcome email with temporary password to new user
     */
    public void sendWelcomeEmailWithPassword(String userEmail, String temporaryPassword, String userType, String resetLink) {
        try {
            MimeMessage message = mailSender.createMimeMessage();
            MimeMessageHelper helper = new MimeMessageHelper(message, true, "UTF-8");

            helper.setFrom("noreply@yourdomain.com");
            helper.setTo(userEmail);
            helper.setSubject("Welcome to UTM CAS - Your Account Has Been Created");

            String htmlContent = "<html><body style='font-family: Arial, sans-serif; background-color: #f3f4f6; padding: 20px;'>"
                + "<div style='max-width: 600px; margin: 0 auto; background-color: #ffffff; padding: 30px; border-radius: 12px; box-shadow: 0 4px 15px rgba(0, 0, 0, 0.1);'>"
                + "<img src='cid:logo' alt='UTM Logo' style='max-width: 150px; height: auto; margin-bottom: 25px; display: block; margin-left: auto; margin-right: auto;'>"
                + "<h2 style='color: #2c3e50; margin-bottom: 20px; text-align: center;'>Welcome to UTM CAS!</h2>"
                + "<p style='color: #34495e; font-size: 16px; line-height: 1.6; text-align: center;'>Your account as a <strong>" + userType + "</strong> has been successfully created.</p>"
                + "<div style='background-color: #ecf0f1; padding: 20px; border-radius: 8px; margin: 25px 0;'>"
                + "<h3 style='color: #2c3e50; margin-top: 0;'>Your Login Credentials</h3>"
                + "<p style='margin: 10px 0;'><strong>Email:</strong> " + userEmail + "</p>"
                + "<p style='margin: 10px 0;'><strong>Temporary Password:</strong> <code style='background-color: #fff; padding: 5px 10px; border-radius: 4px; font-size: 16px; color: #e74c3c;'>" + temporaryPassword + "</code></p>"
                + "</div>"
                + "<p style='color: #e67e22; font-size: 14px; text-align: center; margin: 20px 0;'><strong>⚠️ IMPORTANT:</strong> This is a temporary password. For security reasons, please change it immediately.</p>"
                + "<div style='text-align: center; margin: 30px 0;'>"
                + "<a href='" + resetLink + "' style='background-color: #e74c3c; color: white; padding: 14px 28px; text-decoration: none; border-radius: 8px; font-weight: bold; font-size: 16px; display: inline-block;'>"
                + "Change Password Now"
                + "</a></div>"
                + "<p style='color: #7f8c8d; font-size: 14px; text-align: center;'>You can also login with your temporary password and change it later from your profile settings.</p>"
                + "<hr style='border: none; border-top: 1px solid #ecf0f1; margin: 30px 0;'>"
                + "<p style='color: #95a5a6; font-size: 12px; text-align: center;'>If you did not expect this email, please contact your system administrator.</p>"
                + "</div>"
                + "<p style='margin-top: 20px; font-size: 12px; color: #95a5a6; text-align: center;'>&copy; Danial Ihsan</p>"
                + "</body></html>";

            helper.setText(htmlContent, true);

            // ✅ CHANGED: Updated filename to logoUTM.png
            ClassPathResource logoResource = new ClassPathResource("static/images/logoUTM.png");
            helper.addInline("logo", logoResource);

            mailSender.send(message);
        } catch (Exception e) {
            e.printStackTrace();
            throw new RuntimeException("Failed to send welcome email with password", e);
        }
    }
}