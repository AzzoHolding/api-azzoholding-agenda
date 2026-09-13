package br.com.phdigitalcode.azzo.agenda.pro.dto;

import java.util.ArrayList;
import java.util.List;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

/** Contrato de {@code /api/v1/acessos} — perfis de acesso da equipe. */
public final class AcessoDtos {

  private AcessoDtos() {}

  /** Uma linha da arvore distribuivel (o teto do dono, sem as exclusivas). */
  public static class FuncionalidadeResponse {
    public String id;
    public String route;
    public String label;
    public String parentId;
    public int displayOrder;
    public String iconKey;
    /** {@code false}: a tela existe para o dono, mas o backend dela ainda nao tem permissao (fase 2). */
    public boolean distribuivel;
  }

  public static class PerfilResumoResponse {
    public String id;
    public String nome;
    public String descricao;
    public boolean acessoTotal;
    public int membros;
    public int funcionalidades;
    public String updatedAt;
  }

  public static class PerfilResponse {
    public String id;
    public String nome;
    public String descricao;
    public boolean acessoTotal;
    public int membros;
    /** Ids de {@code item_menu} gravados no perfil. */
    public List<String> itens = new ArrayList<>();
    /** Dos {@link #itens}, os que hoje estao fora do teto do dono (plano, fiscal, catalogo). */
    public List<String> indisponiveis = new ArrayList<>();
    public String updatedAt;
  }

  public static class PerfilRequest {
    @NotBlank @Size(max = 80) public String nome;
    @Size(max = 255) public String descricao;
    @NotNull public List<String> itens = new ArrayList<>();
  }

  public static class PerfilRef {
    public String id;
    public String nome;
    public boolean acessoTotal;
  }

  public static class MembroResponse {
    public String userId;
    public String nome;
    public String email;
    public String telefone;
    /** {@code PROFESSIONAL} (tem agenda), {@code STAFF} (sem agenda) ou {@code FINANCE}. */
    public String papel;
    public String profissionalId;
    public String profissionalNome;
    public List<PerfilRef> perfis = new ArrayList<>();
    /** Saiu da equipe: o acesso esta bloqueado, o historico continua. */
    public boolean desligado;
  }

  public static class AtribuicaoRequest {
    /** Lista vazia = "seguir sem escolher": recebe o Acesso completo (decisao D5). */
    @NotNull public List<String> perfis = new ArrayList<>();
  }

  public static class NovoMembroRequest {
    @NotBlank @Size(max = 160) public String nome;
    @NotBlank @Email public String email;
    public String telefone;
    public List<String> perfis = new ArrayList<>();
  }

  public static class EdicaoDeMembroRequest {
    @NotBlank @Size(max = 160) public String nome;
    public String telefone;
  }

  public static class AcessoEfetivoResponse {
    /** {@code false}: a pessoa ainda esta no padrao do papel fixo, sem perfil. */
    public boolean usaPerfis;
    public List<String> rotas = new ArrayList<>();
    public List<String> permissoes = new ArrayList<>();
  }

  public static class HistoricoResponse {
    public String id;
    public String acao;
    public String autorId;
    public String autorNome;
    /** JSON do estado anterior, como foi gravado. */
    public String antes;
    public String depois;
    public String criadoEm;
  }

  public static class MensagemResponse {
    public String mensagem;

    public MensagemResponse(String mensagem) {
      this.mensagem = mensagem;
    }
  }
}
