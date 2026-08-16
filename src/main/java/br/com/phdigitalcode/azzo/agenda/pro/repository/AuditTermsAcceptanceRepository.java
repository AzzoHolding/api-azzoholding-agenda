package br.com.phdigitalcode.azzo.agenda.pro.repository;

import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import br.com.phdigitalcode.azzo.agenda.pro.entity.AuditTermsAcceptance;

/** Espelha {@code modules/auth/domain/repository/TermsAcceptanceRepository.java}. */
@Repository
public interface AuditTermsAcceptanceRepository extends JpaRepository<AuditTermsAcceptance, UUID> {
}
