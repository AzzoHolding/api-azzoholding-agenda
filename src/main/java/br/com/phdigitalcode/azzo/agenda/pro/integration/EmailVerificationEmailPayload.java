package br.com.phdigitalcode.azzo.agenda.pro.integration;

import java.util.UUID;

public record EmailVerificationEmailPayload(String confirmUrl, UUID emailVerificationTokenId) {}
