package br.com.phdigitalcode.azzo.agenda.pro.service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import br.com.phdigitalcode.azzo.agenda.pro.dto.response.CnpjConsultaResponse;
import br.com.phdigitalcode.azzo.agenda.pro.exception.CnpjApiIndisponivelException;
import br.com.phdigitalcode.azzo.agenda.pro.util.CnpjValidator;
import br.com.phdigitalcode.azzo.agenda.pro.util.DocumentoValidator;

/**
 * O que o cadastro confere do CPF/CNPJ antes de abrir o periodo gratuito (que e UM por documento).
 *
 * <ul>
 *   <li>CPF e CNPJ: digito verificador (a tela ja conferia; a API nao).
 *   <li>CNPJ: existe na Receita e esta ATIVO (CNPJ.ws, com BrasilAPI de reserva). CPF nao tem
 *       consulta publica gratuita, entao fica so com o digito.
 * </ul>
 *
 * <p>Se os dois provedores estiverem FORA do ar, o cadastro segue (so o digito vale): nao se barra
 * cliente novo por falha de terceiro. Roda ANTES da transacao do cadastro, porque faz chamada HTTP.
 */
@Service
public class VerificacaoDoDocumentoNoCadastro {

  private static final Logger LOG = LoggerFactory.getLogger(VerificacaoDoDocumentoNoCadastro.class);

  private final CnpjConsultaService cnpjConsultaService;

  public VerificacaoDoDocumentoNoCadastro(CnpjConsultaService cnpjConsultaService) {
    this.cnpjConsultaService = cnpjConsultaService;
  }

  /** @throws IllegalArgumentException documento invalido, CNPJ inexistente ou nao ativo. */
  public void verificarOuFalhar(String cpfCnpj) {
    String digitos = DocumentoValidator.digitos(cpfCnpj);
    if (digitos == null || digitos.isBlank()) {
      throw new IllegalArgumentException("CPF/CNPJ obrigatorio para ativar plano gratuito");
    }
    if (!DocumentoValidator.cpfOuCnpjValido(digitos)) {
      throw new IllegalArgumentException("CPF/CNPJ invalido");
    }
    if (digitos.length() != 14) return;

    CnpjConsultaResponse consulta;
    try {
      consulta = cnpjConsultaService.consultarSemContato(digitos);
    } catch (CnpjApiIndisponivelException e) {
      LOG.warn("Cadastro seguiu sem conferir o CNPJ {} na Receita: provedores indisponiveis", CnpjValidator.mask(digitos));
      return;
    }
    String situacao = consulta == null ? null : consulta.situacaoCadastral;
    if (situacao != null && !"Ativa".equalsIgnoreCase(situacao)) {
      throw new IllegalArgumentException(
          "CNPJ com situacao " + situacao + " na Receita Federal. Informe um CNPJ ativo.");
    }
  }
}
