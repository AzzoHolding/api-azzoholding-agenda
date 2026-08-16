package br.com.phdigitalcode.azzo.agenda.pro.service;

import java.time.Instant;

import org.springframework.stereotype.Component;

import lombok.Getter;

/**
 * Espelha {@code modules/inventory/application/EstadoImportacaoEstoque.java}: memoria volatil da
 * ultima execucao das duas rotinas de importacao, lida pelo health check de readiness.
 *
 * <p>Estado de processo, nao persistido — reinicio zera tudo, como no original.
 */
@Component
@Getter
public class EstadoImportacaoEstoque {

  private volatile Instant ultimaExecucaoProcessamento;
  private volatile Instant ultimaExecucaoLimpeza;
  private volatile int ultimoTotalProcessados;
  private volatile int ultimoTotalLimpos;
  private volatile int ultimoTotalFilaPendente;
  private volatile String ultimoErroProcessamento;
  private volatile String ultimoErroLimpeza;

  public void registrarProcessamento(Instant execucao, int totalProcessados, int filaPendente) {
    this.ultimaExecucaoProcessamento = execucao;
    this.ultimoTotalProcessados = Math.max(totalProcessados, 0);
    this.ultimoTotalFilaPendente = Math.max(filaPendente, 0);
    this.ultimoErroProcessamento = null;
  }

  public void registrarFalhaProcessamento(String erro) {
    this.ultimaExecucaoProcessamento = Instant.now();
    this.ultimoErroProcessamento = erro;
  }

  public void registrarLimpeza(Instant execucao, int totalLimpos) {
    this.ultimaExecucaoLimpeza = execucao;
    this.ultimoTotalLimpos = Math.max(totalLimpos, 0);
    this.ultimoErroLimpeza = null;
  }

  public void registrarFalhaLimpeza(String erro) {
    this.ultimaExecucaoLimpeza = Instant.now();
    this.ultimoErroLimpeza = erro;
  }
}
