package br.com.phdigitalcode.azzo.agenda.pro.dto;

/**
 * Espelha {@code modules/customers/api/dto/ClientesImportacaoDtos.java}.
 *
 * <p>Campos publicos porque o original tambem os expoe assim e o contrato JSON precisa bater
 * campo a campo com o que o frontend ja consome.
 */
public final class ClientesImportacaoDtos {

  private ClientesImportacaoDtos() {}

  public static class CriarImportacaoClienteRequest {
    public String modoImportacao;
    public Boolean dryRun;
  }

  public static class ImportacaoClienteJobResponse {
    public String jobId;
    public String nomeArquivo;
    public String status;
    public String modoImportacao;
    public Boolean dryRun;
    public int linhasRecebidas;
    public int linhasProcessadas;
    public int linhasSucesso;
    public int linhasErro;
    public String mensagemResumo;
    public String errorMessage;
    public String arquivoHashSha256;
    public String arquivoStorageKey;
    public String requestedBy;
    public String startedAt;
    public String finishedAt;
    public String createdAt;
    public String updatedAt;
  }

  public static class ImportacaoClienteErroLinhaResponse {
    public int linha;
    public String coluna;
    public String codigo;
    public String mensagem;
    public String valorOriginal;
  }

  public static class ModeloImportacaoClienteArquivoResponse {
    public String nomeArquivo;
    public String contentType;
    public byte[] conteudo;
  }
}
