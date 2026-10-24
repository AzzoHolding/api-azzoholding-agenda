package br.com.phdigitalcode.azzo.agenda.pro.dto.request;

import java.math.BigDecimal;
import java.util.Map;

import jakarta.validation.constraints.NotNull;

/** Porte verbatim de {@code modules/finance/api/dto/FechamentoCaixaRequest.java}. */
public class FechamentoCaixaRequest {
  @NotNull public Map<String, BigDecimal> countedTotals;
  public String notes;

  /**
   * "Sim, sei que ha comanda aberta e quero fechar o caixa."
   *
   * <p>Mesmo desenho do {@code notes} obrigatorio quando ha diferenca e do
   * {@code confirmarDuplicado} do item repetido (V132): o servidor recusa por padrao e a pessoa
   * assume a escolha, em vez de a tela decidir por ela. Deixar uma comanda para amanha e
   * legitimo — o cliente que volta amanha; esquecer que ela existe, nao.
   */
  public boolean confirmarComandasAbertas;
}
