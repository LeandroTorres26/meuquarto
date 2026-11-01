package com.meuquarto.identity.domain;

/** A password offered for an account, with the e-mail it would belong to. The value the password rules check. */
public record PasswordCandidate(String password, EmailAddress email) {}
