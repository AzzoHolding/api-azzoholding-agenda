package br.com.phdigitalcode.azzo.agenda.pro.integration;

import java.util.UUID;

/** Espelha {@code modules/email/application/PasswordResetEmailPayload.java}. */
public record PasswordResetEmailPayload(String resetUrl, UUID passwordResetTokenId) {}
