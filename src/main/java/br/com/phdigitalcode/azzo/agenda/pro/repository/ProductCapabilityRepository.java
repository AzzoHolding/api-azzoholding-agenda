package br.com.phdigitalcode.azzo.agenda.pro.repository;

import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import br.com.phdigitalcode.azzo.agenda.pro.entity.ProductCapability;

/**
 * Espelha {@code domain/repository/ProductCapabilityRepository.java}. A chave e o
 * {@code productId} — a PK da tabela {@code product_capabilities} e {@code product_id}.
 */
@Repository
public interface ProductCapabilityRepository extends JpaRepository<ProductCapability, UUID> {}
