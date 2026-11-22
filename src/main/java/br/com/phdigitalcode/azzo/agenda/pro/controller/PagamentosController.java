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
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import br.com.phdigitalcode.azzo.agenda.pro.dto.PagamentosDtos;
import br.com.phdigitalcode.azzo.agenda.pro.security.ContextoTenant;
import br.com.phdigitalcode.azzo.agenda.pro.security.RequiresPermission;
import br.com.phdigitalcode.azzo.agenda.pro.service.LiberacaoDePagamentos;
import br.com.phdigitalcode.azzo.agenda.pro.service.ServicoDeContasDePagamento;
import br.com.phdigitalcode.azzo.agenda.pro.service.ServicoDePagamentos;

/**
 * Pagamentos integrados (Fase 0): as contas do salao nas operadoras e as cobrancas.
 *
 * <p>Conectar, alterar e desconectar conta: so o dono (mexe em credencial). Ver e acompanhar
 * cobranca: quem opera o PDV ({@code pos:view}/{@code pos:manage}). Confirmar Pix na chave: o dono ou
 * quem tem a opcao no perfil ({@code pos:pix-confirm}). Estornar: so o dono.
 */
@RestController
@RequestMapping("/api/v1/pagamentos")
@PreAuthorize("hasAnyRole('OWNER', 'PROFESSIONAL', 'STAFF')")
public class PagamentosController {

  private final ServicoDeContasDePagamento contas;
  private final ServicoDePagamentos pagamentos;
  private final LiberacaoDePagamentos liberacao;
  private final ContextoTenant contextoTenant;

  public PagamentosController(ServicoDeContasDePagamento contas, ServicoDePagamentos pagamentos,
      LiberacaoDePagamentos liberacao, ContextoTenant contextoTenant) {
    this.contas = contas;
    this.pagamentos = pagamentos;
    this.liberacao = liberacao;
    this.contextoTenant = contextoTenant;
  }

  /** O salao esta no piloto dos pagamentos integrados? As telas mostram ou escondem por aqui. */
  @GetMapping("/liberacao")
  public java.util.Map<String, Boolean> liberacao() {
    return java.util.Map.of("liberado", liberacao.liberado(contextoTenant.obterTenantIdOuFalhar()));
  }

  @GetMapping("/operadoras")
  @PreAuthorize("hasRole('OWNER')")
  public List<PagamentosDtos.Operadora> operadoras() {
    return contas.operadoras();
  }

  @GetMapping("/contas")
  @PreAuthorize("hasRole('OWNER')")
  public List<PagamentosDtos.Conta> listarContas() {
    return contas.listar();
  }

  @PostMapping("/contas")
  @PreAuthorize("hasRole('OWNER')")
  @ResponseStatus(HttpStatus.CREATED)
  public PagamentosDtos.Conta conectar(@RequestBody PagamentosDtos.ContaRequest pedido) {
    return contas.conectar(pedido);
  }

  @PutMapping("/contas/{id}")
  @PreAuthorize("hasRole('OWNER')")
  public PagamentosDtos.Conta atualizar(@PathVariable UUID id, @RequestBody PagamentosDtos.ContaRequest pedido) {
    return contas.atualizar(id, pedido);
  }

  @DeleteMapping("/contas/{id}")
  @PreAuthorize("hasRole('OWNER')")
  @ResponseStatus(HttpStatus.NO_CONTENT)
  public void desconectar(@PathVariable UUID id) {
    contas.desconectar(id);
  }

  @PostMapping("/contas/{id}/testar")
  @PreAuthorize("hasRole('OWNER')")
  public PagamentosDtos.Conta testar(@PathVariable UUID id) {
    return contas.testar(id);
  }

  @GetMapping("/contas/{id}/terminais-na-operadora")
  @PreAuthorize("hasRole('OWNER')")
  public List<PagamentosDtos.Terminal> terminaisNaOperadora(@PathVariable UUID id) {
    return contas.terminaisNaOperadora(id);
  }

  @PostMapping("/contas/{id}/terminais")
  @PreAuthorize("hasRole('OWNER')")
  public PagamentosDtos.Conta adicionarTerminal(@PathVariable UUID id, @RequestBody PagamentosDtos.TerminalRequest pedido) {
    return contas.adicionarTerminal(id, pedido);
  }

  @DeleteMapping("/contas/{id}/terminais/{terminalId}")
  @PreAuthorize("hasRole('OWNER')")
  public PagamentosDtos.Conta removerTerminal(@PathVariable UUID id, @PathVariable UUID terminalId) {
    return contas.removerTerminal(id, terminalId);
  }

  /** R$ 1,00 por Pix ou link, para ver a conta funcionando de ponta a ponta. */
  @PostMapping("/cobrancas/teste")
  @PreAuthorize("hasRole('OWNER')")
  @ResponseStatus(HttpStatus.CREATED)
  public PagamentosDtos.Cobranca cobrancaDeTeste(@RequestBody PagamentosDtos.CobrancaDeTesteRequest pedido) {
    return pagamentos.criarTeste(pedido);
  }

  /**
   * Para o PDV: Pix, link e maquininhas disponiveis (sem dado de conta). Permissao do PDV, e nao do
   * financeiro: a atendente com perfil so de PDV tambem cobra por Pix e maquininha (achado de 14/11).
   */
  @GetMapping("/disponiveis")
  @RequiresPermission("pos:view")
  public PagamentosDtos.Disponiveis disponiveis() {
    return contas.disponiveis();
  }

  /** A tela acompanha a cobranca por aqui (o PDV pergunta enquanto espera a maquininha). */
  @GetMapping("/cobrancas/{id}")
  @RequiresPermission("pos:view")
  public PagamentosDtos.Cobranca cobranca(@PathVariable UUID id) {
    return pagamentos.obter(id);
  }

  @PostMapping("/cobrancas/{id}/cancelar")
  @RequiresPermission("pos:manage")
  public PagamentosDtos.Cobranca cancelar(@PathVariable UUID id) {
    return pagamentos.cancelar(id);
  }

  /**
   * Pix na chave do salao: o caixa confirma que o dinheiro chegou (nao ha aviso de operadora). Alem
   * do PDV, exige o dono ou a opcao "Confirmar Pix recebido" no perfil de acesso (conferido no servico).
   */
  @PostMapping("/cobrancas/{id}/confirmar-recebimento")
  @RequiresPermission("pos:manage")
  public PagamentosDtos.Cobranca confirmarRecebimento(@PathVariable UUID id) {
    return pagamentos.confirmarRecebimento(id);
  }

  @PostMapping("/cobrancas/{id}/estornar")
  @PreAuthorize("hasRole('OWNER')")
  public PagamentosDtos.Cobranca estornar(@PathVariable UUID id, @RequestBody(required = false) PagamentosDtos.EstornoRequest pedido) {
    return pagamentos.estornar(id, pedido == null ? null : pedido.valor);
  }
}
