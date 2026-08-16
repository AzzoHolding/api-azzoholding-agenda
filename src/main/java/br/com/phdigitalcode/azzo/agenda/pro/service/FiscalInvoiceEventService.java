package br.com.phdigitalcode.azzo.agenda.pro.service;

import java.util.UUID;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import br.com.phdigitalcode.azzo.agenda.pro.entity.FiscalInvoiceEventEntity;
import br.com.phdigitalcode.azzo.agenda.pro.repository.FiscalInvoiceEventRepository;

/**
 * Espelha {@code modules/fiscal/application/FiscalInvoiceEventService.java}.
 *
 * <p>Grava a trilha append-only de eventos do documento fiscal (emissao, autorizacao, rejeicao,
 * cancelamento) com o retorno da SEFAZ.
 *
 * <p>⚠️ <b>Falha em silencio por desenho.</b> Argumento obrigatorio faltando faz o metodo
 * <b>retornar sem gravar e sem erro</b> — o evento e observabilidade, e o original preferiu perder o
 * registro a derrubar a emissao da nota. Nao trocar por excecao: um {@code eventType} vazio passaria
 * a abortar a autorizacao do documento.
 *
 * <p>{@code @Transactional} sem propagacao explicita, como no original: o evento entra na transacao
 * de quem chamou e some junto se ela reverter.
 */
@Service
public class FiscalInvoiceEventService {

  private final FiscalInvoiceEventRepository fiscalInvoiceEventRepository;

  public FiscalInvoiceEventService(FiscalInvoiceEventRepository fiscalInvoiceEventRepository) {
    this.fiscalInvoiceEventRepository = fiscalInvoiceEventRepository;
  }

  @Transactional
  public void registrarEvento(
      UUID tenantId,
      UUID invoiceId,
      String eventType,
      String eventStatus,
      String sefazStatusCode,
      String sefazStatusMessage) {
    if (tenantId == null || invoiceId == null) return;
    if (eventType == null || eventType.isBlank()) return;
    if (eventStatus == null || eventStatus.isBlank()) return;

    FiscalInvoiceEventEntity entity = new FiscalInvoiceEventEntity();
    entity.setTenantId(tenantId);
    entity.setInvoiceId(invoiceId);
    entity.setEventType(eventType);
    entity.setEventStatus(eventStatus);
    entity.setSefazStatusCode(sefazStatusCode);
    entity.setSefazStatusMessage(sefazStatusMessage);
    fiscalInvoiceEventRepository.save(entity);
  }
}
