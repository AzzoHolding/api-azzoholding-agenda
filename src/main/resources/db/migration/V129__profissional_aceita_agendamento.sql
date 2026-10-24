-- Profissional que NAO atende: continua na equipe (login, comissao, perfis de acesso), mas sai das
-- listas de MARCAR horario — agenda interna, agendamento publico e assistente do WhatsApp. E o caso
-- da recepcionista cadastrada como profissional e do dono que so administra.
--
-- Nasce TRUE para todo mundo de proposito: com o Watchtower, esta migration roda em producao no
-- boot da api, e ninguem pode sumir da agenda por causa dela. Os atendimentos ja marcados de quem
-- for desligado depois continuam — a flag so impede marcacao NOVA.
ALTER TABLE professionals ADD COLUMN IF NOT EXISTS accepts_appointments BOOLEAN NOT NULL DEFAULT TRUE;
