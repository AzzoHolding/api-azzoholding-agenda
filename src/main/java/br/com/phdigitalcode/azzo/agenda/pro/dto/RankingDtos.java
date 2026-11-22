package br.com.phdigitalcode.azzo.agenda.pro.dto;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;

/** Ranking dos profissionais com os criterios do dono (V166). */
public final class RankingDtos {

  private RankingDtos() {}

  /** Uma regra do dono. PONTOS: {@code pontos} a cada {@code aCada}. PESO: {@code peso} em %. */
  public static class Regra {
    public String chave;
    public BigDecimal pontos;
    public BigDecimal aCada;
    public BigDecimal peso;
  }

  public static class RankingRequest {
    public String nome;
    public String modo;
    public List<Regra> criterios = new ArrayList<>();
    public boolean visivelParaProfissional;
  }

  public static class Ranking {
    public String id;
    public String nome;
    public String modo;
    public List<Regra> criterios = new ArrayList<>();
    public boolean visivelParaProfissional;
    public String createdAt;
    public String updatedAt;
  }

  /** O que cada criterio mede: a tela monta o editor a partir daqui. */
  public static class Criterio {
    public String chave;
    public String rotulo;
    public String unidade;
    public boolean maiorEMelhor;
  }

  public static class ItemDoResultado {
    public String chave;
    public String rotulo;
    public String unidade;
    public BigDecimal valor;
    public BigDecimal pontos;
  }

  public static class LinhaDoResultado {
    public int posicao;
    public String profissionalId;
    public String nome;
    public String avatarUrl;
    public BigDecimal total;
    public List<ItemDoResultado> criterios = new ArrayList<>();
  }

  public static class Resultado {
    public Ranking ranking;
    public String de;
    public String ate;
    public List<LinhaDoResultado> linhas = new ArrayList<>();
  }

  /** A posicao de UM profissional — sem os colegas. */
  public static class MinhaPosicao {
    public String rankingId;
    public String nome;
    public String modo;
    public int posicao;
    public int participantes;
    public BigDecimal total;
    public List<ItemDoResultado> criterios = new ArrayList<>();
  }

  public static class MinhasPosicoes {
    public String de;
    public String ate;
    public List<MinhaPosicao> rankings = new ArrayList<>();
  }
}
