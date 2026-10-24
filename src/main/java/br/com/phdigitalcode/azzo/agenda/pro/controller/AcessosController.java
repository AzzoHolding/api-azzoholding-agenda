package br.com.phdigitalcode.azzo.agenda.pro.controller;

import java.util.List;
import java.util.UUID;

import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import br.com.phdigitalcode.azzo.agenda.pro.dto.AcessoDtos.AcessoEfetivoResponse;
import br.com.phdigitalcode.azzo.agenda.pro.dto.AcessoDtos.AtribuicaoRequest;
import br.com.phdigitalcode.azzo.agenda.pro.dto.AcessoDtos.EdicaoDeMembroRequest;
import br.com.phdigitalcode.azzo.agenda.pro.dto.AcessoDtos.FuncionalidadeResponse;
import br.com.phdigitalcode.azzo.agenda.pro.dto.AcessoDtos.HistoricoResponse;
import br.com.phdigitalcode.azzo.agenda.pro.dto.AcessoDtos.MembroResponse;
import br.com.phdigitalcode.azzo.agenda.pro.dto.AcessoDtos.MensagemResponse;
import br.com.phdigitalcode.azzo.agenda.pro.dto.AcessoDtos.NovoMembroRequest;
import br.com.phdigitalcode.azzo.agenda.pro.dto.AcessoDtos.PerfilRequest;
import br.com.phdigitalcode.azzo.agenda.pro.dto.AcessoDtos.PerfilResponse;
import br.com.phdigitalcode.azzo.agenda.pro.dto.AcessoDtos.PerfilResumoResponse;
import br.com.phdigitalcode.azzo.agenda.pro.service.ServicoPerfisDeAcesso;
import jakarta.validation.Valid;

/**
 * Perfis de acesso da equipe (docs/ESPEC_PERFIS_DE_ACESSO.md).
 *
 * <p><b>So o dono.</b> A gestao de acesso e exclusiva (R1/R4): um funcionario com perfil nunca
 * chega aqui, porque o papel dele no JWT nao e {@code OWNER}.
 */
@RestController
@RequestMapping("/api/v1/acessos")
@PreAuthorize("hasRole('OWNER')")
public class AcessosController {

  private final ServicoPerfisDeAcesso servico;

  public AcessosController(ServicoPerfisDeAcesso servico) {
    this.servico = servico;
  }

  @GetMapping("/funcionalidades")
  public List<FuncionalidadeResponse> funcionalidades() {
    return servico.funcionalidades();
  }

  // ─── Perfis ──────────────────────────────────────────────────────────────

  @GetMapping("/perfis")
  public List<PerfilResumoResponse> listarPerfis() {
    return servico.listarPerfis();
  }

  @PostMapping("/perfis")
  @ResponseStatus(HttpStatus.CREATED)
  public PerfilResponse criarPerfil(@Valid @RequestBody PerfilRequest request) {
    return servico.criarPerfil(request);
  }

  @GetMapping("/perfis/{id}")
  public PerfilResponse obterPerfil(@PathVariable UUID id) {
    return servico.obterPerfil(id);
  }

  @PutMapping("/perfis/{id}")
  public PerfilResponse atualizarPerfil(@PathVariable UUID id, @Valid @RequestBody PerfilRequest request) {
    return servico.atualizarPerfil(id, request);
  }

  @PostMapping("/perfis/{id}/duplicar")
  @ResponseStatus(HttpStatus.CREATED)
  public PerfilResponse duplicarPerfil(@PathVariable UUID id) {
    return servico.duplicarPerfil(id);
  }

  @DeleteMapping("/perfis/{id}")
  @ResponseStatus(HttpStatus.NO_CONTENT)
  public void excluirPerfil(@PathVariable UUID id) {
    servico.excluirPerfil(id);
  }

  // ─── Equipe ──────────────────────────────────────────────────────────────

  @GetMapping("/equipe")
  public List<MembroResponse> listarEquipe() {
    return servico.listarEquipe();
  }

  /** Membro SEM agenda (recepcao, financeiro): login e perfis, sem cadastro de profissional. */
  @PostMapping("/equipe")
  @ResponseStatus(HttpStatus.CREATED)
  public MembroResponse cadastrarMembro(@Valid @RequestBody NovoMembroRequest request) {
    return servico.cadastrarMembro(request);
  }

  @PutMapping("/equipe/{userId}")
  public MembroResponse editarMembro(@PathVariable UUID userId, @Valid @RequestBody EdicaoDeMembroRequest request) {
    return servico.editarMembro(userId, request);
  }

  @PutMapping("/equipe/{userId}/perfis")
  public MembroResponse atribuirPerfis(@PathVariable UUID userId, @Valid @RequestBody AtribuicaoRequest request) {
    return servico.atribuirPerfis(userId, request);
  }

  @PostMapping("/equipe/{userId}/redefinir-senha")
  public MensagemResponse redefinirSenha(@PathVariable UUID userId) {
    return servico.redefinirSenha(userId);
  }

  @PostMapping("/equipe/{userId}/desligar")
  public MembroResponse desligar(@PathVariable UUID userId) {
    return servico.desligar(userId);
  }

  @PostMapping("/equipe/{userId}/religar")
  public MembroResponse religar(@PathVariable UUID userId) {
    return servico.religar(userId);
  }

  @GetMapping("/equipe/{userId}/efetivo")
  public AcessoEfetivoResponse efetivo(@PathVariable UUID userId) {
    return servico.efetivo(userId);
  }

  @GetMapping("/historico")
  public List<HistoricoResponse> historico(@RequestParam(name = "limit", required = false) Integer limite) {
    return servico.historico(limite);
  }
}
