package br.com.phdigitalcode.azzo.agenda.pro.integration;

/** E-mail da conta da cliente (area da cliente): titulo, texto, botao e endereco. */
public record AvisoDaClienteEmailPayload(String titulo, String texto, String botao, String url) {}
