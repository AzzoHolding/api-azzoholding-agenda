package br.com.phdigitalcode.azzo.agenda.pro.repository;

import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import br.com.phdigitalcode.azzo.agenda.pro.entity.FiscalInvoiceEventEntity;

/**
 * Espelha {@code modules/fiscal/domain/repository/FiscalInvoiceEventRepository.java} — sem consulta
 * propria no original, so as operacoes de persistencia herdadas.
 */
@Repository
public interface FiscalInvoiceEventRepository
    extends JpaRepository<FiscalInvoiceEventEntity, UUID> {}
