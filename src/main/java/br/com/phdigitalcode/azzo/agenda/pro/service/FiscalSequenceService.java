package br.com.phdigitalcode.azzo.agenda.pro.service;

import java.util.Locale;
import java.util.UUID;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import br.com.phdigitalcode.azzo.agenda.pro.entity.FiscalSequenceControlEntity;
import br.com.phdigitalcode.azzo.agenda.pro.repository.FiscalSequenceControlRepository;

/**
 * Espelha {@code modules/fiscal/application/FiscalSequenceService.java}.
 *
 * <p>Reserva o proximo numero sequencial do documento fiscal por
 * tenant/modelo/serie/ambiente. A numeracao da NF-e nao pode ter buraco nem repeticao, entao a
 * exclusao vem em duas camadas — <b>ambas do original</b>:
 *
 * <ol>
 *   <li>{@code synchronized}, que serializa as chamadas dentro desta JVM;
 *   <li>{@code SELECT ... FOR UPDATE} em
 *       {@link FiscalSequenceControlRepository#findForUpdate}, que serializa entre instancias.
 * </ol>
 *
 * <p>⚠️ <b>A trava do {@code synchronized} e liberada antes do commit</b>, porque o proxy
 * transacional envolve o metodo por fora: quando o {@code synchronized} solta, a transacao ainda
 * nao fechou. Quem realmente protege da colisao entre transacoes concorrentes e o lock pessimista da
 * linha, nao o monitor Java. Mesmo desenho do Quarkus original, mantido de proposito.
 *
 * <p><b>Adaptacao a armadilha 3 (Panache grava na hora, Spring Data adia):</b> o original chama
 * {@code persist()}, que emite o INSERT imediatamente e faz uma corrida perder na unique
 * {@code uq_fiscal_sequence_control} ali mesmo. {@code save()} sozinho adiaria o INSERT ate o
 * commit, deixando duas transacoes seguirem em frente com o mesmo numero — por isso aqui e
 * {@code saveAndFlush}.
 *
 * <p>Substituicao de {@code isPersistent()} (que nao existe no Spring Data): a flag
 * {@code sequenciaNova} carrega a mesma informacao — foi encontrada no banco, ou acabou de ser
 * criada em memoria?
 */
@Service
public class FiscalSequenceService {

  private final FiscalSequenceControlRepository fiscalSequenceControlRepository;

  public FiscalSequenceService(FiscalSequenceControlRepository fiscalSequenceControlRepository) {
    this.fiscalSequenceControlRepository = fiscalSequenceControlRepository;
  }

  @Transactional
  public synchronized int nextNumber(UUID tenantId, String modelo, int serie, String ambiente) {
    if (tenantId == null) throw new IllegalArgumentException("tenantId obrigatorio");
    if (modelo == null || modelo.isBlank()) throw new IllegalArgumentException("modelo obrigatorio");
    if (ambiente == null || ambiente.isBlank()) {
      throw new IllegalArgumentException("ambiente obrigatorio");
    }
    if (serie <= 0) throw new IllegalArgumentException("serie deve ser maior que zero");

    String modeloNormalizado = modelo.trim().toUpperCase(Locale.ROOT);
    String ambienteNormalizado = ambiente.trim().toUpperCase(Locale.ROOT);

    FiscalSequenceControlEntity sequence =
        fiscalSequenceControlRepository
            .findForUpdate(tenantId, modeloNormalizado, serie, ambienteNormalizado)
            .orElse(null);

    if (sequence == null) {
      sequence =
          fiscalSequenceControlRepository.saveAndFlush(
              criarNovaSequencia(tenantId, modeloNormalizado, serie, ambienteNormalizado));
    }

    sequence.setUltimoNumero(sequence.getUltimoNumero() + 1);
    return sequence.getUltimoNumero();
  }

  private FiscalSequenceControlEntity criarNovaSequencia(
      UUID tenantId, String modelo, int serie, String ambiente) {
    FiscalSequenceControlEntity entity = new FiscalSequenceControlEntity();
    entity.setTenantId(tenantId);
    entity.setModelo(modelo);
    entity.setSerie(serie);
    entity.setAmbiente(ambiente);
    entity.setUltimoNumero(0);
    return entity;
  }
}
