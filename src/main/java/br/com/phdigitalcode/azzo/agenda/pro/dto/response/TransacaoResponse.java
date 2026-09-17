package br.com.phdigitalcode.azzo.agenda.pro.dto.response;

import java.math.BigDecimal;

/** Porte verbatim de {@code modules/finance/api/dto/TransacaoResponse.java}. */
public class TransacaoResponse {
  public String id;
  public String tenantId;
  public String appointmentId;

  /**
   * A comanda que gerou este lancamento, quando veio de uma venda. Lancamento de comanda nao se
   * edita nem se exclui (2026-09-16): a tela precisa saber disso para nao oferecer o que o servidor
   * recusa.
   */
  public String comandaId;
  public String professionalId;
  public String productId;
  public String productCategory;
  public String type;
  public String category;
  public String description;
  public BigDecimal amount;
  public String paymentMethod;
  public String date;
  public String createdAt;
  public boolean reconciled;
  public String reconciledAt;
  public String source;
  public String recurringId;
}
