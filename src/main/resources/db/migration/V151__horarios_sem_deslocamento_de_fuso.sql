-- Horarios (colunas time) gravados sem o deslocamento de fuso.
--
-- Com hibernate.jdbc.time_zone=UTC, o Hibernate 7 gravava LocalTime convertido: 09:00 no app virava
-- 12:00 no banco, e "fecha 23:00" virava 02:00 (atravessando a meia-noite). A partir desta versao as
-- entidades gravam LocalTime direto pelo driver (LocalTimeJdbcType), e os valores no banco passam a ser
-- a hora local de verdade.
--
-- So as linhas gravadas pelo app estao deslocadas (+3h, America/Sao_Paulo sem horario de verao). As
-- gravadas por SQL na primeira instalacao do Flyway (backfill e o tenant interno "Azzo System") ja
-- estao em hora local e ficam como estao. Criterio: criada ou alterada depois do fim da primeira
-- instalacao (as migracoes que rodaram juntas, ate 15 minutos depois da primeira).
--
-- A subtracao de intervalo em "time" da volta na meia-noite: 02:00 - 3h = 23:00.
-- Conferido em producao em 25/10/2026: tenant_business_hours 7 linhas do app e 7 do SQL;
-- professional_working_hours 14 do app; tenant_reactivation_config 1 do SQL; fechamentos parciais 0.
-- Sem gatilhos nem views nas tabelas; o CHECK chk_closure_time_order (end_time > start_time) continua
-- valendo porque as duas colunas andam juntas e um fechamento parcial nao atravessa a meia-noite.

CREATE TEMP TABLE fim_da_primeira_instalacao AS
SELECT max(installed_on) AS fim
FROM flyway_schema_history
WHERE installed_on <= (SELECT min(installed_on) FROM flyway_schema_history) + interval '15 minutes';

UPDATE tenant_business_hours
SET open_time   = open_time   - interval '3 hours',
    close_time  = close_time  - interval '3 hours',
    break_start = break_start - interval '3 hours',
    break_end   = break_end   - interval '3 hours'
WHERE coalesce(updated_at, created_at) > (SELECT fim FROM fim_da_primeira_instalacao);

UPDATE professional_working_hours
SET start_time = start_time - interval '3 hours',
    end_time   = end_time   - interval '3 hours'
WHERE created_at > (SELECT fim FROM fim_da_primeira_instalacao);

UPDATE tenant_reactivation_config
SET send_window_start = send_window_start - interval '3 hours',
    send_window_end   = send_window_end   - interval '3 hours'
WHERE (coalesce(updated_at, created_at) AT TIME ZONE 'America/Sao_Paulo')
      > (SELECT fim FROM fim_da_primeira_instalacao);

UPDATE tenant_special_closure_dates
SET start_time = start_time - interval '3 hours',
    end_time   = end_time   - interval '3 hours'
WHERE start_time IS NOT NULL
  AND coalesce(updated_at, created_at) > (SELECT fim FROM fim_da_primeira_instalacao);

DROP TABLE fim_da_primeira_instalacao;
