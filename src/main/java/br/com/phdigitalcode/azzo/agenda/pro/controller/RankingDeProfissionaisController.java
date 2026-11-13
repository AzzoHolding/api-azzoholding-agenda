package br.com.phdigitalcode.azzo.agenda.pro.controller;

import java.util.List;
import java.util.UUID;

import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
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

import br.com.phdigitalcode.azzo.agenda.pro.dto.RankingDtos;
import br.com.phdigitalcode.azzo.agenda.pro.security.RequiresPermission;
import br.com.phdigitalcode.azzo.agenda.pro.service.ServicoRankingDeProfissionais;

/**
 * Ranking dos profissionais (V166). Fora do {@code RelatoriosController} de proposito: la a rota
 * generica {@code /{nome}} engoliria estas.
 *
 * <ul>
 *   <li>ver rankings e resultados: {@code finance:view} (a mesma faixa dos relatorios);
 *   <li>criar, alterar, excluir: so o dono;
 *   <li>{@code /minha-posicao}: o proprio profissional, so a posicao dele.
 * </ul>
 */
@RestController
@RequestMapping("/api/v1/rankings-de-profissionais")
@PreAuthorize("hasAnyRole('OWNER', 'PROFESSIONAL', 'STAFF')")
public class RankingDeProfissionaisController {

  private final ServicoRankingDeProfissionais servico;

  public RankingDeProfissionaisController(ServicoRankingDeProfissionais servico) {
    this.servico = servico;
  }

  @GetMapping("/criterios")
  @RequiresPermission("finance:view")
  public List<RankingDtos.Criterio> criterios() {
    return servico.criterios();
  }

  @GetMapping
  @RequiresPermission("finance:view")
  public List<RankingDtos.Ranking> listar() {
    return servico.listar();
  }

  /** Antes de {@code /{id}}: o caminho literal tem de ganhar da variavel. */
  @GetMapping("/minha-posicao")
  @RequiresPermission("appointment:read")
  public RankingDtos.MinhasPosicoes minhaPosicao(
      @RequestParam(name = "de", required = false) String de,
      @RequestParam(name = "ate", required = false) String ate) {
    return servico.minhaPosicao(de, ate);
  }

  @PostMapping
  @PreAuthorize("hasRole('OWNER')")
  @ResponseStatus(HttpStatus.CREATED)
  public RankingDtos.Ranking criar(@RequestBody RankingDtos.RankingRequest pedido) {
    return servico.criar(pedido);
  }

  @PutMapping("/{id}")
  @PreAuthorize("hasRole('OWNER')")
  public RankingDtos.Ranking atualizar(@PathVariable UUID id, @RequestBody RankingDtos.RankingRequest pedido) {
    return servico.atualizar(id, pedido);
  }

  @DeleteMapping("/{id}")
  @PreAuthorize("hasRole('OWNER')")
  @ResponseStatus(HttpStatus.NO_CONTENT)
  public void excluir(@PathVariable UUID id) {
    servico.excluir(id);
  }

  @GetMapping("/{id}/resultado")
  @RequiresPermission("finance:view")
  public RankingDtos.Resultado resultado(
      @PathVariable UUID id,
      @RequestParam(name = "de", required = false) String de,
      @RequestParam(name = "ate", required = false) String ate) {
    return servico.resultado(id, de, ate);
  }

  @GetMapping("/{id}/resultado/export")
  @RequiresPermission("finance:view")
  public ResponseEntity<byte[]> exportar(
      @PathVariable UUID id,
      @RequestParam(name = "de", required = false) String de,
      @RequestParam(name = "ate", required = false) String ate) {
    byte[] csv = servico.exportarCsv(id, de, ate);
    return ResponseEntity.ok()
        .contentType(MediaType.parseMediaType("text/csv; charset=UTF-8"))
        .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"ranking-dos-profissionais.csv\"")
        .body(csv);
  }
}
