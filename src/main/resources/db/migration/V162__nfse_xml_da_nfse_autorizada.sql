-- XML da NFS-e autorizada, como a Receita Nacional devolve (nfseXmlGZipB64), cifrado pelo
-- EncryptionService. E a fonte do DANFSe v2.0 (NT SE/CGNFS-e 008/2026): o documento auxiliar so
-- pode mostrar o que consta do arquivo da NFS-e. Notas antigas ficam com NULL e usam o PDF simples.
--
-- Conferido em producao antes de escrever (06/11/2026): nfse_invoices sem gatilho; CHECKs so em
-- ambiente, customer_document_rule, customer_type, fiscal_status e operational_status; 3 linhas.

ALTER TABLE nfse_invoices
  ADD COLUMN IF NOT EXISTS xml_nfse_enc BYTEA;
