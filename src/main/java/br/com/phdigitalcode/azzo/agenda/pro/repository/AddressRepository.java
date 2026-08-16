package br.com.phdigitalcode.azzo.agenda.pro.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import br.com.phdigitalcode.azzo.agenda.pro.entity.Address;

/** Espelha {@code domain/repository/AddressRepository.java}. */
@Repository
public interface AddressRepository extends JpaRepository<Address, String> {
}
