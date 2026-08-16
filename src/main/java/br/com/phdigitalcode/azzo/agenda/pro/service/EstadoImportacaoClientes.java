package br.com.phdigitalcode.azzo.agenda.pro.service;

import java.time.Instant;

import org.springframework.stereotype.Component;

import lombok.Getter;

/**
 * Espelha {@code modules/customers/application/EstadoImportacaoClientes.java}: memoria volatil da
 * ultima execucao do processamento da fila de importacao de clientes.
 *
 * <p>Estado de processo, nao persistido — reinicio zera tudo, como no original.
 */
@Component
@Getter
public class EstadoImportacaoClientes {

  private volatile Instant ultimaExecucaoProcessamento;
  private volatile int ultimoTotalProcessados;
  private volatile int ultimoTotalFilaPendente;
  private volatile String ultimoErroProcessamento;

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
}
