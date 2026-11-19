package com.capstone.adproject.service;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.net.InetAddress;
import java.net.ServerSocket;
import java.net.Socket;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Properties;
import java.util.concurrent.CopyOnWriteArrayList;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.mail.javamail.JavaMailSenderImpl;
import org.springframework.test.util.ReflectionTestUtils;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Drives {@link EmailService} through a real SMTP conversation against an in-process mail server, using the same
 * mail properties as application.properties. This proves the message is built and handed over correctly; it does
 * not prove the production mail account or network path works.
 */
class EmailServiceSmtpTest {

    private FakeSmtpServer smtp;
    private EmailService emailService;

    @BeforeEach
    void setUp() throws IOException {
        smtp = new FakeSmtpServer();
        emailService = emailServiceFor(smtp.port(), "sender@example.com", "app-password");
    }

    @AfterEach
    void tearDown() throws IOException {
        smtp.close();
    }

    private static EmailService emailServiceFor(int port, String username, String password) {
        JavaMailSenderImpl sender = new JavaMailSenderImpl();
        sender.setHost("127.0.0.1");
        sender.setPort(port);
        sender.setUsername(username);
        sender.setPassword(password);
        Properties props = sender.getJavaMailProperties();
        props.put("mail.smtp.auth", "true");
        props.put("mail.smtp.starttls.enable", "true");
        props.put("mail.smtp.connectiontimeout", "5000");
        props.put("mail.smtp.timeout", "5000");
        props.put("mail.smtp.writetimeout", "5000");
        props.put("mail.smtp.quitwait", "false");

        EmailService service = new EmailService(sender);
        ReflectionTestUtils.setField(service, "mailFrom", "");
        ReflectionTestUtils.setField(service, "mailUsername", username);
        ReflectionTestUtils.setField(service, "mailPassword", password);
        return service;
    }

    @Test
    void welcomeEmailIsDeliveredWithCredentialsAndLink() {
        boolean sent = emailService.sendWelcomeEmailWithPassword(
                "new.student@example.com", "Ab&lt9#xY", "Student", "https://cas.example/reset_password?token=abc-123");

        assertTrue(sent);
        assertEquals(1, smtp.messages.size());
        FakeSmtpServer.Message message = smtp.messages.get(0);
        assertEquals("<sender@example.com>", message.from);
        assertEquals(List.of("<new.student@example.com>"), message.recipients);
        assertTrue(smtp.authenticated, "client should authenticate before sending");

        String body = decodeQuotedPrintable(message.data);
        assertTrue(body.contains("Subject: Welcome to UTM CAS - Your Account Has Been Created"));
        assertTrue(body.contains("https://cas.example/reset_password?token=abc-123"));
        // '&' in a generated password must be escaped, or mail clients render "&lt" as "<".
        assertTrue(body.contains("Ab&amp;lt9#xY"));
        assertTrue(body.contains("Content-ID: <logo>"), "inline logo should be attached");
    }

    @Test
    void resentWelcomeEmailCarriesOnlyTheSetPasswordLink() {
        boolean sent = emailService.sendWelcomeEmailWithPassword(
                "old.student@example.com", null, "Student", "https://cas.example/reset_password?token=fresh");

        assertTrue(sent);
        String body = decodeQuotedPrintable(smtp.messages.get(0).data);
        assertTrue(body.contains("https://cas.example/reset_password?token=fresh"));
        assertTrue(body.contains("Set My Password"));
        assertFalse(body.contains("Temporary Password"));
    }

    @Test
    void reportsFailureWhenMailCredentialsAreMissing() {
        EmailService unconfigured = emailServiceFor(smtp.port(), "", "");

        assertFalse(unconfigured.sendWelcomeEmailWithPassword("a@example.com", "pw", "Student", "https://x/y"));
        assertTrue(smtp.messages.isEmpty());
    }

    @Test
    void reportsFailureWhenServerRejectsTheMessage() {
        smtp.rejectRecipients = true;

        assertFalse(emailService.sendWelcomeEmailWithPassword("a@example.com", "pw", "Student", "https://x/y"));
        assertTrue(smtp.messages.isEmpty());
    }

    @Test
    void backgroundBatchReportsOnlyAcceptedAddresses() {
        List<String> recorded = new CopyOnWriteArrayList<>();
        smtp.rejectRecipientContaining = "bounce";

        emailService.sendWelcomeEmailsInBackground(List.of(
                new EmailService.WelcomeEmail("ok1@example.com", "pw1", "Student", "https://x/1"),
                new EmailService.WelcomeEmail("bounce@example.com", "pw2", "Student", "https://x/2"),
                new EmailService.WelcomeEmail("ok2@example.com", null, "Assessor", "https://x/3")),
                recorded::add);

        assertEquals(List.of("ok1@example.com", "ok2@example.com"), recorded);
        assertEquals(2, smtp.messages.size());
    }

    private static String decodeQuotedPrintable(String data) {
        return data.replace("=\r\n", "").replace("=3D", "=");
    }

    /** Minimal single-threaded SMTP server: accepts AUTH LOGIN/PLAIN and records each message. */
    private static final class FakeSmtpServer implements AutoCloseable {

        static final class Message {
            String from;
            final List<String> recipients = new CopyOnWriteArrayList<>();
            String data;
        }

        final List<Message> messages = new CopyOnWriteArrayList<>();
        volatile boolean authenticated;
        volatile boolean rejectRecipients;
        volatile String rejectRecipientContaining;

        private final ServerSocket serverSocket;
        private final Thread thread;

        FakeSmtpServer() throws IOException {
            serverSocket = new ServerSocket(0, 50, InetAddress.getByName("127.0.0.1"));
            thread = new Thread(this::acceptLoop, "fake-smtp");
            thread.setDaemon(true);
            thread.start();
        }

        int port() {
            return serverSocket.getLocalPort();
        }

        private void acceptLoop() {
            while (!serverSocket.isClosed()) {
                try (Socket socket = serverSocket.accept()) {
                    serve(socket);
                } catch (IOException e) {
                    // socket closed or client went away; keep accepting until the server is closed
                }
            }
        }

        private void serve(Socket socket) throws IOException {
            BufferedReader in = new BufferedReader(new InputStreamReader(socket.getInputStream(), StandardCharsets.ISO_8859_1));
            OutputStream out = socket.getOutputStream();
            reply(out, "220 localhost ESMTP fake");
            Message current = new Message();
            String line;
            while ((line = in.readLine()) != null) {
                String upper = line.toUpperCase();
                if (upper.startsWith("EHLO") || upper.startsWith("HELO")) {
                    reply(out, "250-localhost");
                    reply(out, "250 AUTH LOGIN PLAIN");
                } else if (upper.startsWith("AUTH LOGIN")) {
                    reply(out, "334 VXNlcm5hbWU6");
                    in.readLine();
                    reply(out, "334 UGFzc3dvcmQ6");
                    in.readLine();
                    authenticated = true;
                    reply(out, "235 2.7.0 Authentication successful");
                } else if (upper.startsWith("AUTH PLAIN")) {
                    if (line.trim().equalsIgnoreCase("AUTH PLAIN")) {
                        reply(out, "334 ");
                        in.readLine();
                    }
                    authenticated = true;
                    reply(out, "235 2.7.0 Authentication successful");
                } else if (upper.startsWith("MAIL FROM:")) {
                    current = new Message();
                    current.from = line.substring(10).trim().split(" ")[0];
                    reply(out, "250 OK");
                } else if (upper.startsWith("RCPT TO:")) {
                    String recipient = line.substring(8).trim();
                    String needle = rejectRecipientContaining;
                    if (rejectRecipients || (needle != null && recipient.contains(needle))) {
                        reply(out, "550 5.1.1 Recipient rejected");
                    } else {
                        current.recipients.add(recipient);
                        reply(out, "250 OK");
                    }
                } else if (upper.equals("DATA")) {
                    reply(out, "354 End data with <CR><LF>.<CR><LF>");
                    StringBuilder data = new StringBuilder();
                    String dataLine;
                    while ((dataLine = in.readLine()) != null && !dataLine.equals(".")) {
                        data.append(dataLine).append("\r\n");
                    }
                    current.data = data.toString();
                    messages.add(current);
                    reply(out, "250 OK queued");
                } else if (upper.startsWith("QUIT")) {
                    reply(out, "221 Bye");
                    return;
                } else {
                    reply(out, "250 OK");
                }
            }
        }

        private static void reply(OutputStream out, String text) throws IOException {
            out.write((text + "\r\n").getBytes(StandardCharsets.ISO_8859_1));
            out.flush();
        }

        @Override
        public void close() throws IOException {
            serverSocket.close();
        }
    }
}
