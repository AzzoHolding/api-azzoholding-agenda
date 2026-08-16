package br.com.phdigitalcode.azzo.agenda.pro.repository;

import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import br.com.phdigitalcode.azzo.agenda.pro.entity.IntegrationLog;

/** Espelha {@code domain/repository/IntegrationLogRepository.java} (apenas CRUD). */
@Repository
public interface IntegrationLogRepository extends JpaRepository<IntegrationLog, UUID> {}
