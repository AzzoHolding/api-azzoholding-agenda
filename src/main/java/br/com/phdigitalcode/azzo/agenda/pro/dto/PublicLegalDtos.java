package br.com.phdigitalcode.azzo.agenda.pro.dto;

/** Espelha {@code application/dto/contract/PublicLegalDtos.java}. */
public final class PublicLegalDtos {

  private PublicLegalDtos() {}

  public static class LegalDocumentResponse {
    public String documentType;
    public String version;
    public String title;
    public String content;
    public String contentHash;
    public String createdAt;
  }

  public static class PublicLegalResponse {
    public LegalDocumentResponse termsOfUse;
    public LegalDocumentResponse privacyPolicy;
    public LgpdContactResponse lgpdContact;
  }

  public static class LgpdContactResponse {
    public String email;
    public String channel;
    public String responseSla;
  }
}
