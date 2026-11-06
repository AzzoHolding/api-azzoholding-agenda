package br.com.phdigitalcode.azzo.agenda.pro.service;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Locale;
import java.util.UUID;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import br.com.phdigitalcode.azzo.agenda.pro.dto.NfseAssistenteDtos;
import br.com.phdigitalcode.azzo.agenda.pro.entity.FiscalCertificateEntity;
import br.com.phdigitalcode.azzo.agenda.pro.entity.FiscalTaxConfigEntity;
import br.com.phdigitalcode.azzo.agenda.pro.entity.NfseConfigEntity;
import br.com.phdigitalcode.azzo.agenda.pro.entity.NfseInvoiceEntity;
import br.com.phdigitalcode.azzo.agenda.pro.entity.NfseInvoiceItemEntity;
import br.com.phdigitalcode.azzo.agenda.pro.entity.enums.NfseAmbiente;
import br.com.phdigitalcode.azzo.agenda.pro.entity.enums.NfseCustomerType;
import br.com.phdigitalcode.azzo.agenda.pro.exception.GlobalExceptionHandler;
import br.com.phdigitalcode.azzo.agenda.pro.integration.BrasilApiCnpjClient;
import br.com.phdigitalcode.azzo.agenda.pro.repository.FiscalCertificateRepository;
import br.com.phdigitalcode.azzo.agenda.pro.repository.FiscalTaxConfigRepository;
import br.com.phdigitalcode.azzo.agenda.pro.repository.NfseConfigRepository;
import br.com.phdigitalcode.azzo.agenda.pro.security.ContextoTenant;

/**
 * Assistente de configuracao da NFS-e (pedido do dono, 06/11/2026): preencher sem erro.
 *
 * <ul>
 *   <li>{@link #dadosDoEmissor}: consulta o CNPJ do proprio salao (BrasilAPI, a mesma do tomador) e
 *       devolve razao social, endereco, municipio (codigo IBGE) e situacao no Simples.
 *   <li>{@link #conferir}: monta uma nota de EXEMPLO com a configuracao salva e a confere contra o
 *       esquema oficial e as regras que o sistema conhece. Nada e enviado nem gravado. Diz, em
 *       portugues e por campo, o que falta. O que so a Receita confere (cadastro da inscricao
 *       municipal, habilitacao do CNPJ) aparece no primeiro envio, com o motivo na nota.
 * </ul>
 */
@Service
public class AssistenteDaNfse {

  static final String ERRO = "ERRO";
  static final String AVISO = "AVISO";
  private static final ZoneId ZONA = ZoneId.of("America/Sao_Paulo");
  private static final int DIAS_DE_AVISO_DO_CERTIFICADO = 30;

  private final ContextoTenant contextoTenant;
  private final BrasilApiCnpjClient brasilApiCnpjClient;
  private final NfseConfigRepository nfseConfigRepository;
  private final FiscalTaxConfigRepository fiscalTaxConfigRepository;
  private final FiscalCertificateRepository fiscalCertificateRepository;
  private final SefinNacionalXmlLayoutBuilder sefinNacionalXmlLayoutBuilder;

  public AssistenteDaNfse(
      ContextoTenant contextoTenant,
      BrasilApiCnpjClient brasilApiCnpjClient,
      NfseConfigRepository nfseConfigRepository,
      FiscalTaxConfigRepository fiscalTaxConfigRepository,
      FiscalCertificateRepository fiscalCertificateRepository,
      SefinNacionalXmlLayoutBuilder sefinNacionalXmlLayoutBuilder) {
    this.contextoTenant = contextoTenant;
    this.brasilApiCnpjClient = brasilApiCnpjClient;
    this.nfseConfigRepository = nfseConfigRepository;
    this.fiscalTaxConfigRepository = fiscalTaxConfigRepository;
    this.fiscalCertificateRepository = fiscalCertificateRepository;
    this.sefinNacionalXmlLayoutBuilder = sefinNacionalXmlLayoutBuilder;
  }

  public NfseAssistenteDtos.Emissor dadosDoEmissor(String cnpjInformado) {
    String cnpj = cnpjInformado == null ? "" : cnpjInformado.replaceAll("\\D", "");
    if (cnpj.length() != 14) throw new IllegalArgumentException("NFSE_TOMADOR_CNPJ_INVALID");
    BrasilApiCnpjClient.BrasilApiCnpjResponse dados;
    try {
      dados = brasilApiCnpjClient.lookup(cnpj);
    } catch (RuntimeException ex) {
      throw new IllegalArgumentException("NFSE_TOMADOR_LOOKUP_UNAVAILABLE");
    }
    if (dados == null) throw new IllegalArgumentException("NFSE_TOMADOR_NOT_FOUND");

    NfseAssistenteDtos.Emissor emissor = new NfseAssistenteDtos.Emissor();
    emissor.cnpj = cnpj;
    emissor.razaoSocial = dados.razao_social;
    emissor.nomeFantasia = dados.nome_fantasia;
    emissor.situacaoCadastral = dados.descricao_situacao_cadastral;
    emissor.logradouro = juntar(dados.descricao_tipo_de_logradouro, dados.logradouro);
    emissor.numero = dados.numero;
    emissor.complemento = dados.complemento;
    emissor.bairro = dados.bairro;
    emissor.cep = dados.cep == null ? null : dados.cep.replaceAll("\\D", "");
    emissor.municipio = dados.municipio;
    emissor.uf = dados.uf;
    emissor.municipioCodigoIbge =
        dados.codigo_municipio_ibge == null ? null : String.format("%07d", dados.codigo_municipio_ibge);
    emissor.situacaoNoSimples = situacaoNoSimples(dados.opcao_pelo_mei, dados.opcao_pelo_simples);
    emissor.telefone = dados.ddd_telefone_1 == null ? null : dados.ddd_telefone_1.replaceAll("\\D", "");
    emissor.email = dados.email == null ? null : dados.email.trim().toLowerCase(Locale.ROOT);
    return emissor;
  }

  /** MEI tambem e optante do Simples: o MEI vem primeiro. Sem as duas respostas, nao afirma nada. */
  static String situacaoNoSimples(Boolean mei, Boolean simples) {
    if (Boolean.TRUE.equals(mei)) return "2";
    if (Boolean.TRUE.equals(simples)) return "3";
    if (Boolean.FALSE.equals(mei) && Boolean.FALSE.equals(simples)) return "1";
    return null;
  }

  @Transactional(readOnly = true)
  public NfseAssistenteDtos.Conferencia conferir(String ambienteInformado) {
    UUID tenantId = contextoTenant.obterTenantIdOuFalhar();
    NfseAmbiente ambiente = parseAmbiente(ambienteInformado);
    NfseAssistenteDtos.Conferencia conferencia = new NfseAssistenteDtos.Conferencia();
    List<NfseAssistenteDtos.Problema> problemas = conferencia.problemas;

    NfseConfigEntity config = nfseConfigRepository.findByTenantAndAmbiente(tenantId, ambiente).orElse(null);
    if (config == null) {
      problemas.add(new NfseAssistenteDtos.Problema(ERRO, "configuracao",
          "A configuração da NFS-e deste ambiente ainda não foi salva."));
      return fechar(conferencia);
    }
    if (!SefinNacionalProviderAdapter.CODE.equalsIgnoreCase(config.getProvedor())) {
      problemas.add(new NfseAssistenteDtos.Problema(ERRO, "provedor",
          "Escolha o provedor Emissor Nacional. A conferência automática vale para ele."));
      return fechar(conferencia);
    }

    FiscalTaxConfigEntity taxConfig = fiscalTaxConfigRepository.findByTenantId(tenantId).orElse(null);
    if (taxConfig == null) {
      problemas.add(new NfseAssistenteDtos.Problema(ERRO, "emissor",
          "Salve os dados do salão (CNPJ e endereço) no passo 1."));
    } else if (isBlank(taxConfig.getIssuerIm())) {
      problemas.add(new NfseAssistenteDtos.Problema(AVISO, "inscricaoMunicipal",
          "Sem inscrição municipal. Se o salão tem cadastro na prefeitura, a Receita exige o número na nota."));
    }

    conferirCertificado(tenantId, problemas);

    if (taxConfig != null) {
      try {
        sefinNacionalXmlLayoutBuilder.montarDpsDeExemplo(
            notaDeExemplo(tenantId, ambiente, config), config, taxConfig, List.of(itemDeExemplo(config)));
      } catch (IllegalArgumentException ex) {
        problemas.add(new NfseAssistenteDtos.Problema(ERRO, campoDoCodigo(ex.getMessage()), traduzir(ex.getMessage())));
      }
    }
    return fechar(conferencia);
  }

  private void conferirCertificado(UUID tenantId, List<NfseAssistenteDtos.Problema> problemas) {
    FiscalCertificateEntity certificado = fiscalCertificateRepository.findActiveByTenant(tenantId).orElse(null);
    if (certificado == null) {
      problemas.add(new NfseAssistenteDtos.Problema(ERRO, "certificado",
          "Envie o certificado digital A1 do salão (passo 4)."));
      return;
    }
    Instant validade = certificado.getValidTo();
    if (validade == null) return;
    Instant agora = Instant.now();
    if (!validade.isAfter(agora)) {
      problemas.add(new NfseAssistenteDtos.Problema(ERRO, "certificado",
          "O certificado digital está vencido. Envie um certificado válido."));
    } else if (validade.isBefore(agora.plus(DIAS_DE_AVISO_DO_CERTIFICADO, ChronoUnit.DAYS))) {
      problemas.add(new NfseAssistenteDtos.Problema(AVISO, "certificado",
          "O certificado digital vence em menos de 30 dias. Providencie a renovação."));
    }
  }

  /** Uma nota de R$ 100,00 para pessoa fisica, com os padroes da configuracao. Nao e gravada. */
  private NfseInvoiceEntity notaDeExemplo(UUID tenantId, NfseAmbiente ambiente, NfseConfigEntity config) {
    NfseInvoiceEntity nota = new NfseInvoiceEntity();
    nota.setId(UUID.randomUUID());
    nota.setTenantId(tenantId);
    nota.setAmbiente(ambiente);
    nota.setProvedor(config.getProvedor());
    nota.setMunicipioCodigoIbge(config.getMunicipioCodigoIbge());
    nota.setLocalPrestacaoCodigoIbge(config.getMunicipioCodigoIbge());
    nota.setSerieRps(config.getSerieRps());
    nota.setNumeroRps(1L);
    nota.setDataCompetencia(LocalDate.now(ZONA));
    nota.setCustomerType(NfseCustomerType.CPF);
    nota.setCustomerDocument("00000000191");
    nota.setCustomerName("Cliente de exemplo");
    nota.setNaturezaOperacao("Prestacao de servico");
    nota.setItemListaServico(config.getItemListaServicoPadrao());
    nota.setNationalTaxCode(config.getNationalTaxCodeDefault());
    nota.setNbsCode(config.getNbsCodeDefault());
    nota.setValorServicos(new BigDecimal("100.00"));
    nota.setValorDeducoes(BigDecimal.ZERO);
    nota.setAliquotaIss(config.getAliquotaIssPadrao());
    nota.setIssRetido(false);
    return nota;
  }

  private NfseInvoiceItemEntity itemDeExemplo(NfseConfigEntity config) {
    NfseInvoiceItemEntity item = new NfseInvoiceItemEntity();
    item.setLineNumber(1);
    item.setDescricaoServico("Servico de exemplo");
    item.setQuantidade(BigDecimal.ONE);
    item.setValorUnitario(new BigDecimal("100.00"));
    item.setValorTotal(new BigDecimal("100.00"));
    item.setNationalTaxCode(config.getNationalTaxCodeDefault());
    item.setNbsCode(config.getNbsCodeDefault());
    return item;
  }

  /** A mensagem em portugues; o leiaute traz o detalhe do esquema depois de ":". */
  static String traduzir(String codigo) {
    if (codigo == null) return "Não foi possível montar a nota de exemplo.";
    if (codigo.startsWith("NFSE_DPS_FORA_DO_LEIAUTE")) {
      int separador = codigo.indexOf(": ");
      return "A nota de exemplo não passou no esquema oficial da Receita"
          + (separador >= 0 ? ": " + codigo.substring(separador + 2) : ".");
    }
    String mensagem = GlobalExceptionHandler.mensagemDoCodigo(codigo);
    return mensagem != null ? mensagem : codigo;
  }

  /** Leva a tela ate o passo certo. */
  static String campoDoCodigo(String codigo) {
    if (codigo == null) return "dps";
    if (codigo.contains("CTRIBNAC") || codigo.contains("NBS")) return "servico";
    if (codigo.contains("PRESTADOR")) return "emissor";
    if (codigo.contains("SERIE") || codigo.contains("APP_VERSION") || codigo.contains("MUNICIPIO")) return "emissao";
    if (codigo.contains("OP_SIMP_NAC") || codigo.contains("REG_") || codigo.contains("PERCENTUAL")
        || codigo.contains("IBS_CBS")) {
      return "impostos";
    }
    return "dps";
  }

  private NfseAssistenteDtos.Conferencia fechar(NfseAssistenteDtos.Conferencia conferencia) {
    conferencia.pronto = conferencia.problemas.stream().noneMatch(problema -> ERRO.equals(problema.tipo));
    return conferencia;
  }

  private NfseAmbiente parseAmbiente(String valor) {
    if (isBlank(valor)) return NfseAmbiente.HOMOLOGACAO;
    try {
      return NfseAmbiente.valueOf(valor.trim().toUpperCase(Locale.ROOT));
    } catch (IllegalArgumentException ex) {
      throw new IllegalArgumentException("Ambiente NFS-e inválido.");
    }
  }

  private static String juntar(String tipo, String logradouro) {
    if (isBlank(logradouro)) return null;
    if (isBlank(tipo) || logradouro.toUpperCase(Locale.ROOT).startsWith(tipo.toUpperCase(Locale.ROOT))) {
      return logradouro.trim();
    }
    return tipo.trim() + " " + logradouro.trim();
  }

  private static boolean isBlank(String valor) {
    return valor == null || valor.isBlank();
  }
}
