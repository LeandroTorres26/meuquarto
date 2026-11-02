package com.meuquarto.identity.infra;

import com.meuquarto.identity.application.PasswordResetMailer;
import jakarta.mail.internet.MimeMessage;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.DisposableBean;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.mail.javamail.MimeMessageHelper;
import org.springframework.stereotype.Component;

/**
 * Sends the reset e-mail over SMTP ({@code MAIL_*} variables; Mailpit locally, a transactional
 * provider in production). Delivery runs on its own thread so the HTTP answer takes the same time
 * whether or not an e-mail is sent, and a slow or failing mail server never changes the answer.
 * Failures are logged without the address, link or exception message (they could carry either).
 */
@Component
class SmtpPasswordResetMailer implements PasswordResetMailer, DisposableBean {

    private static final Logger log = LoggerFactory.getLogger(SmtpPasswordResetMailer.class);

    private final JavaMailSender mailSender;
    private final String from;
    private final ExecutorService executor = Executors.newVirtualThreadPerTaskExecutor();

    SmtpPasswordResetMailer(JavaMailSender mailSender, @Value("${meuquarto.mail.from}") String from) {
        this.mailSender = mailSender;
        this.from = from;
    }

    @Override
    public void sendResetLink(String toEmail, String link) {
        executor.execute(() -> deliver(toEmail, new PasswordResetEmail(link)));
    }

    private void deliver(String toEmail, PasswordResetEmail email) {
        try {
            MimeMessage message = mailSender.createMimeMessage();
            MimeMessageHelper helper = new MimeMessageHelper(message, true, StandardCharsets.UTF_8.name());
            helper.setFrom(from);
            helper.setTo(toEmail);
            helper.setSubject(PasswordResetEmail.SUBJECT);
            helper.setText(email.text(), email.html());
            mailSender.send(message);
        } catch (Exception e) {
            log.warn("Password reset e-mail was not delivered ({})", e.getClass().getSimpleName());
        }
    }

    @Override
    public void destroy() {
        executor.shutdown();
    }
}
