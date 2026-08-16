package br.com.phdigitalcode.azzo.agenda.pro.service;

import java.time.Instant;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.UUID;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.domain.PageRequest;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.support.TransactionTemplate;

import br.com.phdigitalcode.azzo.agenda.pro.entity.Cliente;
import br.com.phdigitalcode.azzo.agenda.pro.entity.ImportacaoClienteErroLinha;
import br.com.phdigitalcode.azzo.agenda.pro.entity.ImportacaoClienteJob;
import br.com.phdigitalcode.azzo.agenda.pro.entity.enums.ModoImportacaoCliente;
import br.com.phdigitalcode.azzo.agenda.pro.entity.enums.StatusImportacaoCliente;
import br.com.phdigitalcode.azzo.agenda.pro.integration.AuditConstants;
import br.com.phdigitalcode.azzo.agenda.pro.integration.AuditEventCommand;
import br.com.phdigitalcode.azzo.agenda.pro.integration.AuditService;
import br.com.phdigitalcode.azzo.agenda.pro.integration.MinioStorageService;
import br.com.phdigitalcode.azzo.agenda.pro.repository.ClienteRepository;
import br.com.phdigitalcode.azzo.agenda.pro.repository.ImportacaoClienteErroLinhaRepository;
import br.com.phdigitalcode.azzo.agenda.pro.repository.ImportacaoClienteJobRepository;
import br.com.phdigitalcode.azzo.agenda.pro.security.JwtPrincipal;
import br.com.phdigitalcode.azzo.agenda.pro.service.ClienteImportFileParser.ClienteImportRow;
import br.com.phdigitalcode.azzo.agenda.pro.util.DataUtil;

/**
 * Espelha {@code modules/customers/application/importacao/ProcessadorImportacaoClientesService.java}
 * e {@code modules/customers/domain/repository/ImportacaoClienteJobRepository#buscarPendentesParaProcessamento}
 * (chamado direto por HQL no Panache no original).
 *
 * <p><b>Isolamento por job.</b> Mesma armadilha ja documentada em
 * {@link ProcessadorImportacaoEstoqueService}: chamar um metodo {@code @Transactional} da propria
 * classe nao passa pelo proxy do Spring, entao a transacao nova por job e aberta explicitamente com
 * {@link TransactionTemplate} (propagacao {@code REQUIRES_NEW}) em {@link #processarJob(UUID)}.
 *
 * <p><b>Assimetria do original preservada (nao "consertada"):</b> ao contrario do processador de
 * estoque, este <b>nao remove o arquivo do storage nem zera {@code arquivoStorageKey}</b> ao
 * concluir ou falhar um job — o original de fato nao faz essa limpeza para clientes. Tambem nao ha
 * scheduler de limpeza de jobs expirados para clientes (existe so para estoque).
 */
@Service
public class ProcessadorImportacaoClientesService {

  private static final Logger LOG =
      LoggerFactory.getLogger(ProcessadorImportacaoClientesService.class);

  private static final List<StatusImportacaoCliente> STATUS_PENDENTES =
      List.of(
          StatusImportacaoCliente.RECEBIDO,
          StatusImportacaoCliente.EM_VALIDACAO,
          StatusImportacaoCliente.PROCESSANDO);

  private final ImportacaoClienteJobRepository importacaoClienteJobRepository;
  private final ImportacaoClienteErroLinhaRepository importacaoClienteErroLinhaRepository;
  private final ClienteRepository clienteRepository;
  private final ClienteImportFileParser clienteImportFileParser;
  private final MinioStorageService minioStorageService;
  private final AuditService auditService;
  private final TransactionTemplate requiresNewTransaction;

  public ProcessadorImportacaoClientesService(
      ImportacaoClienteJobRepository importacaoClienteJobRepository,
      ImportacaoClienteErroLinhaRepository importacaoClienteErroLinhaRepository,
      ClienteRepository clienteRepository,
      ClienteImportFileParser clienteImportFileParser,
      MinioStorageService minioStorageService,
      AuditService auditService,
      PlatformTransactionManager transactionManager) {
    this.importacaoClienteJobRepository = importacaoClienteJobRepository;
    this.importacaoClienteErroLinhaRepository = importacaoClienteErroLinhaRepository;
    this.clienteRepository = clienteRepository;
    this.clienteImportFileParser = clienteImportFileParser;
    this.minioStorageService = minioStorageService;
    this.auditService = auditService;
    this.requiresNewTransaction = new TransactionTemplate(transactionManager);
    this.requiresNewTransaction.setPropagationBehavior(TransactionDefinition.PROPAGATION_REQUIRES_NEW);
  }

  /**
   * Varredura global (todos os tenants), como no original. {@code limitePorRodada} vem configurado
   * no {@link br.com.phdigitalcode.azzo.agenda.pro.scheduler.ProcessadorImportacaoClientesScheduler},
   * mesma divisao de responsabilidade do Quarkus original.
   */
  public int processarFila(int limitePorRodada) {
    List<UUID> jobIds =
        importacaoClienteJobRepository.listarIdsPendentes(
            STATUS_PENDENTES, PageRequest.ofSize(Math.max(limitePorRodada, 1)));
    LOG.info("ProcessadorImportacaoClientes iniciado. jobs={}", jobIds.size());
    for (UUID jobId : jobIds) {
      processarJob(jobId);
    }
    LOG.info("ProcessadorImportacaoClientes finalizado. jobs={}", jobIds.size());
    return jobIds.size();
  }

  /** Um job por transacao propria — ver a nota de isolamento no javadoc da classe. */
  public void processarJob(UUID jobId) {
    requiresNewTransaction.executeWithoutResult(status -> processarJobNaTransacao(jobId));
  }

  private void processarJobNaTransacao(UUID jobId) {
    ImportacaoClienteJob job = importacaoClienteJobRepository.findById(jobId).orElse(null);
    if (job == null) return;
    if (job.getStatus() == StatusImportacaoCliente.CANCELADO) return;

    try {
      job.setStatus(StatusImportacaoCliente.PROCESSANDO);
      if (job.getStartedAt() == null) {
        job.setStartedAt(Instant.now());
      }
      importacaoClienteErroLinhaRepository.deleteByJobIdAndTenantId(job.getId(), job.getTenantId());

      byte[] arquivo = minioStorageService.baixarArquivo(job.getArquivoStorageKey(), job.getTenantId());
      List<ClienteImportRow> rows = clienteImportFileParser.parse(arquivo, job.getNomeArquivo());
      job.setLinhasRecebidas(rows.size());

      int linhasSucesso = 0;
      int linhasErro = 0;
      for (ClienteImportRow row : rows) {
        List<ImportacaoClienteErroLinha> erros = validarELocalizar(job, row);
        if (!erros.isEmpty()) {
          importacaoClienteErroLinhaRepository.saveAll(erros);
          linhasErro++;
          continue;
        }
        linhasSucesso++;
      }

      job.setLinhasProcessadas(rows.size());
      job.setLinhasSucesso(linhasSucesso);
      job.setLinhasErro(linhasErro);
      job.setMensagemResumo("Importacao concluida.");
      job.setStatus(
          linhasErro > 0
              ? StatusImportacaoCliente.CONCLUIDO_COM_ERROS
              : StatusImportacaoCliente.CONCLUIDO);
      job.setFinishedAt(Instant.now());
      job.setErrorMessage(null);
      importacaoClienteJobRepository.save(job);
      auditar(
          job,
          job.getStatus() == StatusImportacaoCliente.CONCLUIDO
              ? "CLIENT_IMPORT_JOB_COMPLETED"
              : "CLIENT_IMPORT_JOB_COMPLETED");
    } catch (Exception exception) {
      job.setStatus(StatusImportacaoCliente.FALHOU);
      job.setFinishedAt(Instant.now());
      job.setErrorMessage(detalharErro(exception));
      job.setMensagemResumo("Falha ao processar importacao de clientes.");
      importacaoClienteJobRepository.save(job);
      auditar(job, "CLIENT_IMPORT_JOB_FAILED");
      LOG.error("importacao_clientes_falhou jobId={}", jobId, exception);
    }
  }

  private List<ImportacaoClienteErroLinha> validarELocalizar(
      ImportacaoClienteJob job, ClienteImportRow row) {
    List<ImportacaoClienteErroLinha> erros = new ArrayList<>();
    String nome = normalizeText(row.nome());
    String phoneDigits = normalizeDigits(row.telefone());
    String email = normalizeEmail(row.email());

    if (nome == null || nome.isBlank()) {
      erros.add(erro(job, row.lineNumber(), "nome", "CLIENT_NAME_REQUIRED", "Nome obrigatorio.", row.nome()));
    }
    if ((phoneDigits == null || phoneDigits.isBlank()) && (email == null || email.isBlank())) {
      erros.add(
          erro(
              job,
              row.lineNumber(),
              "telefone",
              "CLIENT_CONTACT_REQUIRED",
              "Informe telefone ou email.",
              row.telefone()));
    }
    if (email != null && !email.matches("^[A-Za-z0-9+_.-]+@[A-Za-z0-9.-]+$")) {
      erros.add(erro(job, row.lineNumber(), "email", "CLIENT_EMAIL_INVALID", "Email invalido.", row.email()));
    }
    String uf = normalizeState(row.uf());
    if (row.uf() != null && !row.uf().isBlank() && uf == null) {
      erros.add(
          erro(job, row.lineNumber(), "uf", "CLIENT_STATE_INVALID", "UF invalida. Use 2 caracteres.", row.uf()));
    }
    if (!erros.isEmpty()) {
      return erros;
    }

    Optional<Cliente> byPhone = clienteRepository.findByTenantAndPhoneDigits(job.getTenantId(), phoneDigits);
    Optional<Cliente> byEmail =
        clienteRepository.findFirstByTenantIdAndEmailIgnoreCaseOrderByCreatedAtDesc(job.getTenantId(), email);
    if (byPhone.isPresent() && byEmail.isPresent() && !byPhone.get().getId().equals(byEmail.get().getId())) {
      erros.add(
          erro(
              job,
              row.lineNumber(),
              "telefone",
              "CLIENT_AMBIGUOUS_MATCH",
              "Linha ambigua: telefone e email apontam para clientes diferentes.",
              row.telefone()));
      return erros;
    }

    Cliente target = byPhone.orElseGet(() -> byEmail.orElse(null));
    if (target != null && job.getModoImportacao() == ModoImportacaoCliente.INSERT_ONLY) {
      erros.add(
          erro(
              job,
              row.lineNumber(),
              "telefone",
              "CLIENT_ALREADY_EXISTS",
              "Cliente ja existente para o modo INSERT_ONLY.",
              row.telefone()));
      return erros;
    }

    if (Boolean.TRUE.equals(job.getDryRun())) {
      return erros;
    }

    Cliente cliente = target != null ? target : new Cliente();
    cliente.setTenantId(job.getTenantId());
    cliente.setName(nome);
    cliente.setPhone(phoneDigits);
    cliente.setEmail(email);
    cliente.setBirthDate(parseBirthDate(row.dataNascimento()));
    cliente.setNotes(normalizeText(row.observacoes()));
    cliente.setZipCode(normalizeDigits(row.cep()));
    cliente.setStreet(normalizeText(row.logradouro()));
    cliente.setNumber(normalizeText(row.numero()));
    cliente.setComplement(normalizeText(row.complemento()));
    cliente.setNeighborhood(normalizeText(row.bairro()));
    cliente.setCity(normalizeText(row.cidade()));
    cliente.setState(uf);
    clienteRepository.save(cliente);
    return erros;
  }

  private ImportacaoClienteErroLinha erro(
      ImportacaoClienteJob job, int linha, String coluna, String codigo, String mensagem, String valorOriginal) {
    ImportacaoClienteErroLinha erro = new ImportacaoClienteErroLinha();
    erro.setJobId(job.getId());
    erro.setTenantId(job.getTenantId());
    erro.setLinha(linha);
    erro.setColuna(coluna);
    erro.setCodigo(codigo);
    erro.setMensagem(mensagem);
    erro.setValorOriginal(valorOriginal);
    return erro;
  }

  private LocalDate parseBirthDate(String value) {
    if (value == null || value.isBlank()) return null;
    try {
      return DataUtil.parseDataISO(value);
    } catch (Exception exception) {
      return null;
    }
  }

  private String normalizeText(String value) {
    if (value == null) return null;
    String normalized = value.trim();
    return normalized.isBlank() ? null : normalized;
  }

  private String normalizeEmail(String value) {
    if (value == null) return null;
    String normalized = value.trim().toLowerCase(Locale.ROOT);
    return normalized.isBlank() ? null : normalized;
  }

  private String normalizeDigits(String value) {
    if (value == null) return null;
    String normalized = value.replaceAll("\\D", "").trim();
    return normalized.isBlank() ? null : normalized;
  }

  private String normalizeState(String value) {
    if (value == null) return null;
    String normalized = value.trim().toUpperCase(Locale.ROOT);
    return normalized.length() == 2 ? normalized : null;
  }

  private void auditar(ImportacaoClienteJob job, String action) {
    try {
      AuditEventCommand command = new AuditEventCommand();
      command.tenantId = job.getTenantId();
      command.actorUserId = obterUsuarioId();
      command.actorRole = obterActorRole();
      command.module = AuditConstants.Module.CUSTOMER;
      command.action = action;
      command.entityType = "CLIENT_IMPORT_JOB";
      command.entityId = job.getId() != null ? job.getId().toString() : null;
      command.sourceChannel = AuditConstants.SourceChannel.SYSTEM;
      LinkedHashMap<String, Object> after = new LinkedHashMap<>();
      after.put("status", job.getStatus() != null ? job.getStatus().name() : null);
      after.put("linhasRecebidas", job.getLinhasRecebidas());
      after.put("linhasProcessadas", job.getLinhasProcessadas());
      after.put("linhasSucesso", job.getLinhasSucesso());
      after.put("linhasErro", job.getLinhasErro());
      if (job.getMensagemResumo() != null) {
        after.put("mensagemResumo", job.getMensagemResumo());
      }
      if (job.getErrorMessage() != null) {
        after.put("errorMessage", job.getErrorMessage());
      }
      command.after = after;
      auditService.recordSuccess(command);
    } catch (Exception ignored) {
      // auditoria nao deve bloquear processamento
    }
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
    if (authentication == null || authentication.getAuthorities() == null) return "SYSTEM";
    return authentication.getAuthorities().stream()
        .map(GrantedAuthority::getAuthority)
        .map(role -> role.startsWith("ROLE_") ? role.substring(5) : role)
        .findFirst()
        .orElse("SYSTEM");
  }

  private String detalharErro(Exception exception) {
    StringBuilder builder = new StringBuilder();
    Throwable current = exception;
    while (current != null) {
      if (builder.length() > 0) builder.append(" | caused by: ");
      builder.append(current.getClass().getName());
      if (current.getMessage() != null && !current.getMessage().isBlank()) {
        builder.append(": ").append(current.getMessage());
      }
      current = current.getCause();
    }
    return builder.toString();
  }
}
