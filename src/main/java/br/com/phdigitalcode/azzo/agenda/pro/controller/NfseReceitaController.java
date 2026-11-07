package br.com.phdigitalcode.azzo.agenda.pro.controller;

import java.nio.charset.StandardCharsets;

import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import br.com.phdigitalcode.azzo.agenda.pro.dto.NfseReceitaDtos;
import br.com.phdigitalcode.azzo.agenda.pro.entity.NfseDocumentoDaReceitaEntity;
import br.com.phdigitalcode.azzo.agenda.pro.security.ContextoTenant;
import br.com.phdigitalcode.azzo.agenda.pro.service.FiscalAccessService;
import br.com.phdigitalcode.azzo.agenda.pro.service.ServicoDeNotasDaReceita;

/**
 * Notas na Receita: buscar no ADN (precisa da senha do certificado, mesma permissao de emitir),
 * listar, e baixar XML e DANFSe (leitura fiscal).
 */
@RestController
@RequestMapping("/api/v1/fiscal/nfse/receita")
@PreAuthorize(FiscalController.LEITURA)
public class NfseReceitaController {

  private final ServicoDeNotasDaReceita servico;
  private final FiscalAccessService fiscalAccessService;
  private final ContextoTenant contextoTenant;

  public NfseReceitaController(
      ServicoDeNotasDaReceita servico, FiscalAccessService fiscalAccessService, ContextoTenant contextoTenant) {
    this.servico = servico;
    this.fiscalAccessService = fiscalAccessService;
    this.contextoTenant = contextoTenant;
  }

  @PostMapping("/buscar")
  @PreAuthorize(FiscalController.OPERACAO)
  public NfseReceitaDtos.BuscaResponse buscar(@RequestBody(required = false) NfseReceitaDtos.BuscaRequest pedido) {
    validarAcesso();
    return servico.buscar(pedido);
  }

  @GetMapping("/notas")
  public NfseReceitaDtos.ListaResponse listar(
      @RequestParam(required = false) String ambiente,
      @RequestParam(required = false) String papel,
      @RequestParam(required = false) String de,
      @RequestParam(required = false) String ate) {
    validarAcesso();
    return servico.listar(ambiente, papel, de, ate);
  }

  @GetMapping(value = "/notas/{id}/xml", produces = MediaType.APPLICATION_XML_VALUE)
  public ResponseEntity<byte[]> xml(@PathVariable String id) {
    validarAcesso();
    NfseDocumentoDaReceitaEntity doc = servico.obter(id);
    return ResponseEntity.ok()
        .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"" + nomeDoArquivo(doc) + ".xml\"")
        .body(servico.xml(id).getBytes(StandardCharsets.UTF_8));
  }

  @GetMapping(value = "/notas/{id}/danfse", produces = MediaType.APPLICATION_PDF_VALUE)
  public ResponseEntity<byte[]> danfse(@PathVariable String id) {
    validarAcesso();
    NfseDocumentoDaReceitaEntity doc = servico.obter(id);
    return ResponseEntity.ok()
        .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"" + nomeDoArquivo(doc) + ".pdf\"")
        .body(servico.danfse(id));
  }

  private static String nomeDoArquivo(NfseDocumentoDaReceitaEntity doc) {
    return doc.getChaveAcesso() != null ? "NFSe-" + doc.getChaveAcesso() : "nfse-" + doc.getId();
  }

  private void validarAcesso() {
    fiscalAccessService.validarAcessoFiscal(contextoTenant.obterTenantIdOuFalhar());
  }
}
