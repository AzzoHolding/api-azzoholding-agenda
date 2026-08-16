package br.com.phdigitalcode.azzo.agenda.pro.dto.response;

import java.util.List;

/** Espelha {@code modules/finance/api/dto/PagedResponse.java} (contrato de paginacao compartilhado). */
public class PagedResponse<T> {
  public List<T> items;
  public long totalCount;
  public int currentPage;
  public int totalPages;
}
