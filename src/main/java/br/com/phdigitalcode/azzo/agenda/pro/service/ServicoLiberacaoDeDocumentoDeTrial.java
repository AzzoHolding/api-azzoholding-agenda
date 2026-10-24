package br.com.phdigitalcode.azzo.agenda.pro.service;

import java.time.Duration;
import java.time.Instant;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import br.com.phdigitalcode.azzo.agenda.pro.repository.TenantRepository;

/**
 * Solta o documento do periodo gratuito de cadastros que nunca confirmaram o e-mail (achado
 * SEG-009, auditoria de 2026-10-06).
 *
 * <p>O periodo gratuito e um por documento, e o documento fica reservado ja no cadastro. Como a
 * posse do CPF/CNPJ nao e provada (so os digitos e a Receita), quem se cadastrava com o documento
 * de outra pessoa e um e-mail descartavel e nunca confirmava, negava o periodo gratuito ao titular
 * de verdade, para sempre. Passado o prazo, o documento volta a ficar disponivel.
 *
 * <p>Nada e apagado: so o hash do documento e anulado. Se a pessoa confirmar o e-mail depois, o
 * documento e reivindicado de novo, se ninguem o tiver pego
 * ({@link ServicoConfirmacaoDeEmail#reivindicarDocumentoDeTrial}). O e-mail ocupado nao precisa de
 * liberacao: o dono do endereco recupera a conta por "esqueci minha senha", que comprova a posse.
 */
@Service
public class ServicoLiberacaoDeDocumentoDeTrial {

  private static final Logger LOG = LoggerFactory.getLogger(ServicoLiberacaoDeDocumentoDeTrial.class);

  private final TenantRepository tenantRepository;
  private final int diasDeEspera;

  /** {@code 0} (ou menos) desliga a liberacao. */
  public ServicoLiberacaoDeDocumentoDeTrial(
      TenantRepository tenantRepository,
      @Value("${app.registration.unconfirmed-document-hold-days:7}") int diasDeEspera) {
    this.tenantRepository = tenantRepository;
    this.diasDeEspera = diasDeEspera;
  }

  @Transactional
  public int liberar() {
    if (diasDeEspera < 1) return 0;
    Instant limite = Instant.now().minus(Duration.ofDays(diasDeEspera));
    int liberados = tenantRepository.liberarDocumentoDeTrialDeCadastrosNaoConfirmados(limite);
    if (liberados > 0) {
      // So a contagem: o documento e dado pessoal e nao vai para o log.
      LOG.info(
          "cadastros_nao_confirmados.documento_liberado quantidade={} diasDeEspera={}",
          liberados, diasDeEspera);
    }
    return liberados;
  }
}
