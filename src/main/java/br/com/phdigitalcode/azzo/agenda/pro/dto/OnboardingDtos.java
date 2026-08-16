package br.com.phdigitalcode.azzo.agenda.pro.dto;

import java.time.LocalDateTime;

import jakarta.validation.constraints.NotBlank;

/** Espelha {@code modules/onboarding/api/dto/OnboardingDtos.java}. */
public class OnboardingDtos {

  private OnboardingDtos() {}

  public record AcceptTermsRequest(
      @NotBlank(message = "termsVersion obrigatorio") String termsVersion,
      @NotBlank(message = "privacyVersion obrigatorio") String privacyVersion) {}

  public record OnboardingStatusResponse(
      boolean onboardingComplete,
      boolean onboardingSkipped,
      int currentStep,
      boolean hasProfessionals,
      boolean hasServices,
      boolean hasAssignments,
      boolean hasBusinessHours,
      boolean termsAccepted,
      String termsVersion,
      LocalDateTime completedAt) {}
}
