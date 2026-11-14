package br.com.phdigitalcode.azzo.agenda.pro.controller;

import java.time.Duration;
import java.util.UUID;

import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RestController;

import br.com.phdigitalcode.azzo.agenda.pro.dto.ConectorTefDtos;
import br.com.phdigitalcode.azzo.agenda.pro.entity.TerminalDePagamento;
import br.com.phdigitalcode.azzo.agenda.pro.service.ServicoDoConectorTef;

/**
 * Azzo Conector (TEF no balcao, Fase 6).
 *
 * <p>{@code /api/v1/public/conector-tef/**}: sem login de usuario (o Conector nao tem um) — quem
 * autentica e o token do pareamento, no cabecalho {@code Authorization: Bearer}. O caminho publico
 * ja passa pelo limite de requisicoes, o que protege o pareamento contra tentativa de codigo.
 *
 * <p>{@code /api/v1/pagamentos/contas/{id}/conectores}: o dono gera o codigo de pareamento.
 */
@RestController
public class ConectorTefController {

  private final ServicoDoConectorTef conector;

  public ConectorTefController(ServicoDoConectorTef conector) {
    this.conector = conector;
  }

  @PostMapping("/api/v1/pagamentos/contas/{contaId}/conectores")
  @PreAuthorize("hasRole('OWNER')")
  public ConectorTefDtos.CodigoDePareamento gerarPareamento(
      @PathVariable UUID contaId, @RequestBody(required = false) ConectorTefDtos.PedidoDeConector pedido) {
    return conector.gerarPareamento(contaId, pedido == null ? null : pedido.apelido);
  }

  @PostMapping("/api/v1/public/conector-tef/parear")
  public ConectorTefDtos.Pareado parear(@RequestBody ConectorTefDtos.PedidoDePareamento pedido) {
    return conector.parear(pedido);
  }

  @GetMapping("/api/v1/public/conector-tef/configuracao")
  public ConectorTefDtos.Configuracao configuracao(@RequestHeader(value = "Authorization", required = false) String authorization) {
    return conector.configuracao(conector.autenticar(authorization));
  }

  /** Espera ate ~20 s por uma venda; sem venda, 204 (o Conector pergunta de novo). */
  @GetMapping("/api/v1/public/conector-tef/pedidos/proximo")
  public ResponseEntity<ConectorTefDtos.Pedido> proximo(@RequestHeader(value = "Authorization", required = false) String authorization) {
    TerminalDePagamento t = conector.autenticar(authorization);
    ConectorTefDtos.Pedido pedido = conector.proximoPedido(t, Duration.ofSeconds(20));
    return pedido == null ? ResponseEntity.noContent().build() : ResponseEntity.ok(pedido);
  }

  @GetMapping("/api/v1/public/conector-tef/pedidos/{cobrancaId}")
  public ConectorTefDtos.Situacao situacao(
      @RequestHeader(value = "Authorization", required = false) String authorization, @PathVariable UUID cobrancaId) {
    return conector.situacao(conector.autenticar(authorization), cobrancaId);
  }

  @PostMapping("/api/v1/public/conector-tef/pedidos/{cobrancaId}/resultado")
  public ConectorTefDtos.RespostaAoResultado resultado(
      @RequestHeader(value = "Authorization", required = false) String authorization, @PathVariable UUID cobrancaId,
      @RequestBody ConectorTefDtos.Resultado resultado) {
    return conector.resultado(conector.autenticar(authorization), cobrancaId, resultado);
  }

  @PostMapping("/api/v1/public/conector-tef/cancelamentos")
  public ResponseEntity<Void> canceladaNoPinpad(
      @RequestHeader(value = "Authorization", required = false) String authorization,
      @RequestBody ConectorTefDtos.CancelamentoNoPinpad pedido) {
    conector.canceladaNoPinpad(conector.autenticar(authorization), pedido);
    return ResponseEntity.noContent().build();
  }
}
