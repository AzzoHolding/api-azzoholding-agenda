alter table nfse_invoices
  add column if not exists chave_acesso_nfse varchar(50);
