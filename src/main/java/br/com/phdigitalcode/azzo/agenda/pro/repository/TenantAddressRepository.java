package br.com.phdigitalcode.azzo.agenda.pro.repository;

import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import br.com.phdigitalcode.azzo.agenda.pro.entity.TenantAddress;

/** Espelha {@code domain/repository/TenantAddressRepository.java} (Panache -> Spring Data JPA). */
@Repository
public interface TenantAddressRepository extends JpaRepository<TenantAddress, UUID> {}
