package br.com.phdigitalcode.azzo.agenda.pro.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import br.com.phdigitalcode.azzo.agenda.pro.entity.NfseReceitaCursorEntity;

@Repository
public interface NfseReceitaCursorRepository
    extends JpaRepository<NfseReceitaCursorEntity, NfseReceitaCursorEntity.Chave> {}
