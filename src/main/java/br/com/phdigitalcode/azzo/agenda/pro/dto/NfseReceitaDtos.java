package br.com.phdigitalcode.azzo.agenda.pro.dto;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;

/** Notas da Receita: o que o ADN distribui para o CNPJ do salao. */
public final class NfseReceitaDtos {

  private NfseReceitaDtos() {}

  public static class BuscaRequest {
    /** HOMOLOGACAO ou PRODUCAO. */
    public String ambiente;
    public String unlockTokenId;
  }

  public static class BuscaResponse {
    public int novos;
    public int notas;
    public int eventos;
    public long ultimoNsu;
    /** A busca parou antes do fim (limite da Receita ou de tempo): buscar de novo continua dali. */
    public boolean parcial;
    public String mensagem;
  }

  public static class Nota {
    public String id;
    public String ambiente;
    public String chaveAcesso;
    public String numeroNfse;
    public String competencia;
    public String emitidaEm;
    /** PRESTADOR (emitida pelo salao), TOMADOR (recebida), INTERMEDIARIO ou OUTRO. */
    public String papel;
    /** AZZO (emitida pelo sistema) ou EXTERNA (portal, contador, outro sistema). */
    public String origem;
    public BigDecimal valorServicos;
    public BigDecimal valorIss;
    public boolean issRetido;
    public String prestadorDocumento;
    public String prestadorNome;
    public String tomadorDocumento;
    public String tomadorNome;
    public String descricao;
    public boolean cancelada;
    /** Do evento de cancelamento (quando a Receita o distribuiu). */
    public String canceladaEm;
    /** "Erro na emissão", "Serviço não prestado", "Outros"... */
    public String motivoCancelamento;
    /** O texto digitado por quem cancelou. */
    public String justificativaCancelamento;
    /** Detalhe do evento, para o dialogo da nota cancelada. */
    public String codigoMotivoCancelamento;
    public String tipoCancelamento;
    public String cancelamentoProcessadoEm;
    public String cancelamentoAutorDocumento;
    public String numeroDoEventoCancelamento;
    public String chaveSubstituta;
  }

  public static class ListaResponse {
    public List<Nota> itens = new ArrayList<>();
    public String ultimaBuscaEm;
    public long ultimoNsu;
  }
}
