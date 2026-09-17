package br.com.phdigitalcode.azzo.agenda.pro.service;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import org.springframework.stereotype.Component;

import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;

/**
 * O que PRENDE um registro — e impede de exclui-lo sem apagar dinheiro ou historico junto.
 *
 * <p>Analise de 2026-09-16 (achados A2 e A6), confirmada nas chaves do banco de producao: excluir
 * um agendamento apagava em cascata o SINAL pago e as notas do atendimento; excluir um cliente
 * apagava as COMPRAS de pacote e as assinaturas; excluir um servico apagava o SALDO de sessoes que
 * os clientes ja tinham pago. E quando o vinculo era de outro tipo, a exclusao estourava como
 * "Ocorreu um erro inesperado", sem dizer o porque.
 *
 * <p>Cada metodo devolve a lista do que prende, em palavras — vazia quando pode excluir. Quem chama
 * decide a mensagem e o caminho alternativo (cancelar, anonimizar, desativar).
 */
@Component
public class VinculosDeExclusao {

  @PersistenceContext private EntityManager entityManager;

  /** O agendamento so sai se nunca envolveu dinheiro nem registro de atendimento. */
  public List<String> doAgendamento(UUID tenantId, UUID appointmentId) {
    List<String> vinculos = new ArrayList<>();
    adicionar(vinculos, contar(
        "SELECT COUNT(*) FROM appointment_deposits WHERE tenant_id = :t AND appointment_id = :id",
        tenantId, appointmentId), "sinal de reserva");
    adicionar(vinculos, contar(
        "SELECT COUNT(*) FROM comandas WHERE tenant_id = :t AND appointment_id = :id",
        tenantId, appointmentId), "comanda");
    adicionar(vinculos, contar(
        "SELECT COUNT(*) FROM transactions WHERE tenant_id = :t AND appointment_id = :id",
        tenantId, appointmentId), "lancamento financeiro");
    adicionar(vinculos, contar(
        "SELECT COUNT(*) FROM appointment_customer_notes WHERE tenant_id = :t AND appointment_id = :id",
        tenantId, appointmentId), "registro do atendimento");
    return vinculos;
  }

  /** O cliente so sai se nao tem historico nenhum no salao. */
  public List<String> doCliente(UUID tenantId, UUID clientId) {
    List<String> vinculos = new ArrayList<>();
    adicionar(vinculos, contar(
        "SELECT COUNT(*) FROM appointments WHERE tenant_id = :t AND client_id = :id",
        tenantId, clientId), "agendamentos");
    adicionar(vinculos, contar(
        "SELECT COUNT(*) FROM comandas WHERE tenant_id = :t AND client_id = :id",
        tenantId, clientId), "comandas");
    adicionar(vinculos, contar(
        "SELECT COUNT(*) FROM client_package_purchases WHERE tenant_id = :t AND client_id = :id",
        tenantId, clientId), "pacotes comprados");
    adicionar(vinculos, contar(
        "SELECT COUNT(*) FROM client_memberships WHERE tenant_id = :t AND client_id = :id",
        tenantId, clientId), "assinaturas");
    adicionar(vinculos, contar(
        "SELECT COUNT(*) FROM appointment_customer_notes WHERE tenant_id = :t AND client_id = :id",
        tenantId, clientId), "registros de atendimento");
    return vinculos;
  }

  /** O servico so sai se nunca foi vendido, agendado nem colocado em pacote ou plano. */
  public List<String> doServico(UUID tenantId, UUID serviceId) {
    List<String> vinculos = new ArrayList<>();
    adicionar(vinculos, contar(
        "SELECT COUNT(*) FROM appointment_items WHERE tenant_id = :t AND service_id = :id",
        tenantId, serviceId), "agendamentos");
    adicionar(vinculos, contar(
        "SELECT COUNT(*) FROM comanda_itens WHERE tenant_id = :t AND tipo = 'SERVICO' AND referencia_id = :id",
        tenantId, serviceId), "comandas");
    adicionar(vinculos, contar(
        "SELECT COUNT(*) FROM service_package_items WHERE tenant_id = :t AND service_id = :id",
        tenantId, serviceId), "pacotes");
    adicionar(vinculos, contar(
        "SELECT COUNT(*) FROM membership_plan_benefits WHERE tenant_id = :t AND service_id = :id",
        tenantId, serviceId), "planos de assinatura");
    adicionar(vinculos, contar(
        "SELECT COUNT(*) FROM client_package_balances WHERE tenant_id = :t AND service_id = :id",
        tenantId, serviceId), "saldo de pacote de clientes");
    adicionar(vinculos, contar(
        "SELECT COUNT(*) FROM client_membership_balances WHERE tenant_id = :t AND service_id = :id",
        tenantId, serviceId), "saldo de assinatura de clientes");
    return vinculos;
  }

  private long contar(String sql, UUID tenantId, UUID id) {
    Object resultado =
        entityManager.createNativeQuery(sql).setParameter("t", tenantId).setParameter("id", id)
            .getSingleResult();
    return resultado instanceof Number numero ? numero.longValue() : 0L;
  }

  private static void adicionar(List<String> vinculos, long quantidade, String oQue) {
    if (quantidade > 0) vinculos.add(oQue);
  }
}
