package com.meuquarto.identity.application;

/** Delivers the reset link to the account's e-mail. The SMTP implementation lives in infra. */
public interface PasswordResetMailer {

    /** Hands the message over for delivery. Must not block on, or report, the mail server's answer. */
    void sendResetLink(String toEmail, String link);
}
