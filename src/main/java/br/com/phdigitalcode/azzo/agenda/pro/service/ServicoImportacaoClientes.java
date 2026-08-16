package br.com.phdigitalcode.azzo.agenda.pro.service;

import java.io.ByteArrayOutputStream;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;

import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.xssf.usermodel.XSSFSheet;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import br.com.phdigitalcode.azzo.agenda.pro.dto.ClientesImportacaoDtos.CriarImportacaoClienteRequest;
import br.com.phdigitalcode.azzo.agenda.pro.dto.ClientesImportacaoDtos.ImportacaoClienteErroLinhaResponse;
import br.com.phdigitalcode.azzo.agenda.pro.dto.ClientesImportacaoDtos.ImportacaoClienteJobResponse;
import br.com.phdigitalcode.azzo.agenda.pro.dto.ClientesImportacaoDtos.ModeloImportacaoClienteArquivoResponse;
import br.com.phdigitalcode.azzo.agenda.pro.entity.ImportacaoClienteErroLinha;
import br.com.phdigitalcode.azzo.agenda.pro.entity.ImportacaoClienteJob;
import br.com.phdigitalcode.azzo.agenda.pro.entity.enums.ModoImportacaoCliente;
import br.com.phdigitalcode.azzo.agenda.pro.entity.enums.StatusImportacaoCliente;
import br.com.phdigitalcode.azzo.agenda.pro.exception.ApiClientErrorException;
import br.com.phdigitalcode.azzo.agenda.pro.integration.AuditConstants;
import br.com.phdigitalcode.azzo.agenda.pro.integration.AuditEventCommand;
import br.com.phdigitalcode.azzo.agenda.pro.integration.AuditService;
import br.com.phdigitalcode.azzo.agenda.pro.repository.ImportacaoClienteErroLinhaRepository;
import br.com.phdigitalcode.azzo.agenda.pro.repository.ImportacaoClienteJobRepository;
import br.com.phdigitalcode.azzo.agenda.pro.security.ContextoTenant;
import br.com.phdigitalcode.azzo.agenda.pro.security.JwtPrincipal;

/**
 * Espelha {@code modules/customers/application/ServicoImportacaoClientes.java}: CRUD do job de
 * importacao de clientes (o upload em si e o hash ficam no controller — mesma divisao de
 * responsabilidade adotada em {@code ServicoEstoque}/{@code EstoqueController}).
 */
@Service
public class ServicoImportacaoClientes {

  private static final Logger LOG = LoggerFactory.getLogger(ServicoImportacaoClientes.class);

  private final ImportacaoClienteJobRepository importacaoClienteJobRepository;
  private final ImportacaoClienteErroLinhaRepository importacaoClienteErroLinhaRepository;
  private final ContextoTenant contextoTenant;
  private final AuditService auditService;

  public ServicoImportacaoClientes(
      ImportacaoClienteJobRepository importacaoClienteJobRepository,
      ImportacaoClienteErroLinhaRepository importacaoClienteErroLinhaRepository,
      ContextoTenant contextoTenant,
      AuditService auditService) {
    this.importacaoClienteJobRepository = importacaoClienteJobRepository;
    this.importacaoClienteErroLinhaRepository = importacaoClienteErroLinhaRepository;
    this.contextoTenant = contextoTenant;
    this.auditService = auditService;
  }

  @Transactional
  public ImportacaoClienteJobResponse criarImportacao(
      CriarImportacaoClienteRequest request, String nomeArquivo, String arquivoHashSha256, String arquivoStorageKey) {
    UUID tenantId = contextoTenant.obterTenantIdOuFalhar();
    ImportacaoClienteJob job = new ImportacaoClienteJob();
    job.setTenantId(tenantId);
    job.setNomeArquivo(nomeArquivo);
    job.setArquivoHashSha256(arquivoHashSha256);
    job.setArquivoStorageKey(arquivoStorageKey);
    job.setStatus(StatusImportacaoCliente.RECEBIDO);
    job.setModoImportacao(parseModoImportacao(request != null ? request.modoImportacao : null));
    job.setDryRun(request != null && Boolean.TRUE.equals(request.dryRun));
    job.setRequestedBy(obterUsuarioId());
    // saveAndFlush: o id vem do @PrePersist e entra tanto no payload de auditoria quanto na
    // resposta desta mesma chamada.
    importacaoClienteJobRepository.saveAndFlush(job);

    auditar(tenantId, "CLIENT_IMPORT_JOB_CREATED", null, resumoJob(job), job.getId());
    LOG.info(
        "customers.import.create.completed tenantId={} jobId={} fileName={} modoImportacao={} dryRun={} requestedBy={}",
        tenantId, job.getId(), nomeArquivo, job.getModoImportacao(), job.getDryRun(), job.getRequestedBy());
    return toJobResponse(job);
  }

  @Transactional(readOnly = true)
  public List<ImportacaoClienteJobResponse> listarImportacoes() {
    UUID tenantId = contextoTenant.obterTenantIdOuFalhar();
    List<ImportacaoClienteJobResponse> response =
        importacaoClienteJobRepository.findByTenantIdOrderByCreatedAtDesc(tenantId).stream()
            .map(this::toJobResponse)
            .toList();
    LOG.info("customers.import.list.completed tenantId={} count={}", tenantId, response.size());
    return response;
  }

  @Transactional(readOnly = true)
  public ImportacaoClienteJobResponse buscarImportacao(UUID jobId) {
    ImportacaoClienteJob job = obterJobOuFalhar(jobId);
    LOG.info(
        "customers.import.get.completed tenantId={} jobId={} status={}",
        job.getTenantId(), job.getId(), job.getStatus());
    return toJobResponse(job);
  }

  @Transactional(readOnly = true)
  public List<ImportacaoClienteErroLinhaResponse> listarErrosImportacao(UUID jobId) {
    ImportacaoClienteJob job = obterJobOuFalhar(jobId);
    List<ImportacaoClienteErroLinhaResponse> response =
        importacaoClienteErroLinhaRepository
            .findByJobIdAndTenantIdOrderByLinhaAsc(job.getId(), job.getTenantId())
            .stream()
            .map(this::toErroResponse)
            .toList();
    LOG.info(
        "customers.import.errors.completed tenantId={} jobId={} count={}",
        job.getTenantId(), job.getId(), response.size());
    return response;
  }

  @Transactional
  public ImportacaoClienteJobResponse cancelarImportacao(UUID jobId) {
    ImportacaoClienteJob job = obterJobOuFalhar(jobId);
    if (!statusPermiteCancelamento(job.getStatus())) {
      LOG.warn(
          "customers.import.cancel.conflict tenantId={} jobId={} status={}",
          job.getTenantId(), job.getId(), job.getStatus());
      throw conflito("Status atual nao permite cancelamento.");
    }
    Map<String, Object> before = resumoJob(job);
    job.setStatus(StatusImportacaoCliente.CANCELADO);
    job.setFinishedAt(Instant.now());
    importacaoClienteJobRepository.save(job);
    auditar(job.getTenantId(), "CLIENT_IMPORT_JOB_CANCELLED", before, resumoJob(job), job.getId());
    LOG.info(
        "customers.import.cancel.completed tenantId={} jobId={} status={}",
        job.getTenantId(), job.getId(), job.getStatus());
    return toJobResponse(job);
  }

  public ModeloImportacaoClienteArquivoResponse gerarModeloImportacao(String formatoRaw) {
    String formato =
        formatoRaw == null || formatoRaw.isBlank() ? "xlsx" : formatoRaw.trim().toLowerCase(Locale.ROOT);
    if (!"xlsx".equals(formato) && !"csv".equals(formato)) {
      LOG.warn("customers.import.template.invalidFormat format={}", formatoRaw);
      throw new ApiClientErrorException("formato invalido. Use xlsx ou csv.", HttpStatus.BAD_REQUEST.value());
    }

    List<String> cabecalho =
        List.of(
            "nome",
            "telefone",
            "email",
            "data_nascimento",
            "observacoes",
            "cep",
            "logradouro",
            "numero",
            "complemento",
            "bairro",
            "cidade",
            "uf");
    List<String> exemplo =
        List.of(
            "Maria Silva",
            "11999998888",
            "maria@email.com",
            "1990-05-10",
            "Cliente VIP",
            "01001000",
            "Praca da Se",
            "100",
            "Sala 2",
            "Centro",
            "Sao Paulo",
            "SP");

    ModeloImportacaoClienteArquivoResponse response = new ModeloImportacaoClienteArquivoResponse();
    response.nomeArquivo = "modelo-importacao-clientes." + formato;
    if ("csv".equals(formato)) {
      response.contentType = "text/csv";
      response.conteudo = gerarCsv(cabecalho, exemplo);
      LOG.info("customers.import.template.completed format={} contentType={}", formato, response.contentType);
      return response;
    }

    response.contentType = "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet";
    response.conteudo = gerarXlsx(cabecalho, exemplo);
    LOG.info("customers.import.template.completed format={} contentType={}", formato, response.contentType);
    return response;
  }

  static boolean statusPermiteCancelamento(StatusImportacaoCliente status) {
    if (status == null) return false;
    return status != StatusImportacaoCliente.CONCLUIDO
        && status != StatusImportacaoCliente.CONCLUIDO_COM_ERROS
        && status != StatusImportacaoCliente.FALHOU
        && status != StatusImportacaoCliente.CANCELADO;
  }

  private ImportacaoClienteJob obterJobOuFalhar(UUID jobId) {
    UUID tenantId = contextoTenant.obterTenantIdOuFalhar();
    return importacaoClienteJobRepository
        .findByIdAndTenantId(jobId, tenantId)
        .orElseThrow(
            () -> {
              LOG.warn("customers.import.job.notFound tenantId={} jobId={}", tenantId, jobId);
              return naoEncontrado("Job de importacao de clientes nao encontrado.");
            });
  }

  private ModoImportacaoCliente parseModoImportacao(String raw) {
    if (raw == null || raw.isBlank()) return ModoImportacaoCliente.INSERT_ONLY;
    try {
      return ModoImportacaoCliente.valueOf(raw.trim().toUpperCase(Locale.ROOT));
    } catch (Exception exception) {
      LOG.warn("customers.import.invalidMode modoImportacao={}", raw);
      throw new ApiClientErrorException(
          "modoImportacao invalido. Use INSERT_ONLY ou UPSERT.", HttpStatus.BAD_REQUEST.value());
    }
  }

  private ImportacaoClienteJobResponse toJobResponse(ImportacaoClienteJob entity) {
    ImportacaoClienteJobResponse response = new ImportacaoClienteJobResponse();
    response.jobId = entity.getId() != null ? entity.getId().toString() : null;
    response.nomeArquivo = entity.getNomeArquivo();
    response.status = entity.getStatus() != null ? entity.getStatus().name() : null;
    response.modoImportacao = entity.getModoImportacao() != null ? entity.getModoImportacao().name() : null;
    response.dryRun = entity.getDryRun();
    response.linhasRecebidas = zero(entity.getLinhasRecebidas());
    response.linhasProcessadas = zero(entity.getLinhasProcessadas());
    response.linhasSucesso = zero(entity.getLinhasSucesso());
    response.linhasErro = zero(entity.getLinhasErro());
    response.mensagemResumo = entity.getMensagemResumo();
    response.errorMessage = entity.getErrorMessage();
    response.arquivoHashSha256 = entity.getArquivoHashSha256();
    response.arquivoStorageKey = entity.getArquivoStorageKey();
    response.requestedBy = entity.getRequestedBy() != null ? entity.getRequestedBy().toString() : null;
    response.startedAt = entity.getStartedAt() != null ? entity.getStartedAt().toString() : null;
    response.finishedAt = entity.getFinishedAt() != null ? entity.getFinishedAt().toString() : null;
    response.createdAt = entity.getCreatedAt() != null ? entity.getCreatedAt().toString() : null;
    response.updatedAt = entity.getUpdatedAt() != null ? entity.getUpdatedAt().toString() : null;
    return response;
  }

  private ImportacaoClienteErroLinhaResponse toErroResponse(ImportacaoClienteErroLinha entity) {
    ImportacaoClienteErroLinhaResponse response = new ImportacaoClienteErroLinhaResponse();
    response.linha = zero(entity.getLinha());
    response.coluna = entity.getColuna();
    response.codigo = entity.getCodigo();
    response.mensagem = entity.getMensagem();
    response.valorOriginal = entity.getValorOriginal();
    return response;
  }

  private int zero(Integer value) {
    return value == null ? 0 : value;
  }

  private byte[] gerarCsv(List<String> cabecalho, List<String> exemplo) {
    StringBuilder csv = new StringBuilder();
    csv.append(String.join(",", cabecalho)).append('\n');
    csv.append(String.join(",", exemplo)).append('\n');
    return csv.toString().getBytes(StandardCharsets.UTF_8);
  }

  private byte[] gerarXlsx(List<String> cabecalho, List<String> exemplo) {
    try (XSSFWorkbook workbook = new XSSFWorkbook();
        ByteArrayOutputStream outputStream = new ByteArrayOutputStream()) {
      XSSFSheet sheet = workbook.createSheet("modelo");
      Row cabecalhoRow = sheet.createRow(0);
      for (int i = 0; i < cabecalho.size(); i++) {
        cabecalhoRow.createCell(i).setCellValue(cabecalho.get(i));
      }
      Row exemploRow = sheet.createRow(1);
      for (int i = 0; i < exemplo.size(); i++) {
        exemploRow.createCell(i).setCellValue(exemplo.get(i));
      }
      for (int i = 0; i < cabecalho.size(); i++) {
        int maxLen = Math.max(cabecalho.get(i).length(), i < exemplo.size() ? exemplo.get(i).length() : 0);
        sheet.setColumnWidth(i, Math.max(maxLen * 400 + 2000, 4000));
      }
      workbook.write(outputStream);
      return outputStream.toByteArray();
    } catch (Exception exception) {
      throw new IllegalStateException("Falha ao gerar modelo de importacao de clientes.", exception);
    }
  }

  private void auditar(UUID tenantId, String action, Object before, Object after, UUID entityId) {
    try {
      AuditEventCommand command = new AuditEventCommand();
      command.tenantId = tenantId;
      command.actorUserId = obterUsuarioId();
      command.actorRole = obterActorRole();
      command.module = AuditConstants.Module.CUSTOMER;
      command.action = action;
      command.entityType = "CLIENT_IMPORT_JOB";
      command.entityId = entityId != null ? entityId.toString() : null;
      command.sourceChannel = AuditConstants.SourceChannel.API;
      command.before = before;
      command.after = after;
      auditService.recordSuccess(command);
    } catch (Exception ignored) {
      // auditoria nao deve bloquear o fluxo principal
    }
  }

  private Map<String, Object> resumoJob(ImportacaoClienteJob job) {
    if (job == null) return null;
    Map<String, Object> snapshot = new HashMap<>();
    snapshot.put("id", job.getId() != null ? job.getId().toString() : null);
    snapshot.put("nomeArquivo", job.getNomeArquivo());
    snapshot.put("status", job.getStatus() != null ? job.getStatus().name() : null);
    snapshot.put("modoImportacao", job.getModoImportacao() != null ? job.getModoImportacao().name() : null);
    snapshot.put("dryRun", job.getDryRun());
    snapshot.put("linhasRecebidas", job.getLinhasRecebidas());
    snapshot.put("linhasProcessadas", job.getLinhasProcessadas());
    snapshot.put("linhasSucesso", job.getLinhasSucesso());
    snapshot.put("linhasErro", job.getLinhasErro());
    snapshot.put("mensagemResumo", job.getMensagemResumo());
    snapshot.put("errorMessage", job.getErrorMessage());
    snapshot.put("arquivoHashSha256", job.getArquivoHashSha256());
    snapshot.put("arquivoStorageKey", job.getArquivoStorageKey());
    snapshot.put("requestedBy", job.getRequestedBy() != null ? job.getRequestedBy().toString() : null);
    return snapshot;
  }

  private UUID obterUsuarioId() {
    Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
    if (authentication != null && authentication.getPrincipal() instanceof JwtPrincipal jwtPrincipal) {
      return jwtPrincipal.userId();
    }
    return null;
  }

  private String obterActorRole() {
    Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
    if (authentication == null || authentication.getAuthorities() == null) return null;
    return authentication.getAuthorities().stream()
        .map(GrantedAuthority::getAuthority)
        .map(role -> role.startsWith("ROLE_") ? role.substring(5) : role)
        .findFirst()
        .orElse(null);
  }

  private ApiClientErrorException naoEncontrado(String mensagem) {
    return new ApiClientErrorException(mensagem, HttpStatus.NOT_FOUND.value());
  }

  private ApiClientErrorException conflito(String mensagem) {
    return new ApiClientErrorException(mensagem, HttpStatus.CONFLICT.value());
  }
}
