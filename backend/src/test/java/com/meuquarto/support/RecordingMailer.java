package com.meuquarto.support;

import com.meuquarto.identity.application.PasswordResetMailer;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;

/**
 * In-memory stand-in for the SMTP mailer: nothing leaves the JVM. Import it with
 * {@code @Import({TestcontainersConfiguration.class, RecordingMailer.Config.class})}.
 */
public class RecordingMailer implements PasswordResetMailer {

    public record Sent(String to, String link) {}

    private final List<Sent> sent = new CopyOnWriteArrayList<>();

    @Override
    public void sendResetLink(String toEmail, String link) {
        sent.add(new Sent(toEmail, link));
    }

    public List<Sent> sent() {
        return List.copyOf(sent);
    }

    public void clear() {
        sent.clear();
    }

    /** The token in the most recent link sent to {@code email}. */
    public String lastTokenFor(String email) {
        return sent.reversed().stream().filter(s -> s.to().equals(email)).findFirst()
                .map(s -> s.link().substring(s.link().indexOf("token=") + "token=".length()))
                .orElseThrow();
    }

    @TestConfiguration(proxyBeanMethods = false)
    public static class Config {

        @Bean
        @Primary
        RecordingMailer recordingMailer() {
            return new RecordingMailer();
        }
    }
}
