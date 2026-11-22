package br.com.phdigitalcode.azzo.agenda.pro.saude;

import static br.com.phdigitalcode.azzo.agenda.pro.saude.Saude.m;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import br.com.phdigitalcode.azzo.agenda.pro.dto.PublicBookingDtos;
import br.com.phdigitalcode.azzo.agenda.pro.entity.Cliente;
import br.com.phdigitalcode.azzo.agenda.pro.exception.ApiClientErrorException;
import br.com.phdigitalcode.azzo.agenda.pro.repository.ClienteRepository;
import br.com.phdigitalcode.azzo.agenda.pro.service.ServicoPublicBooking;

/**
 * Salões da Azzo na area da cliente (pedido de 20/11/2026): buscar, ver servicos e precos, agendar.
 *
 * <p>Decisoes: aparecem os salões com plano ativo e pelo menos um servico ativo, se o dono nao desligou "Aparecer na busca"
 * ({@code tenant_operational_settings.aparecer_na_area_da_cliente}, V188). A busca so mostra o que ja e publico no link
 * de agendamento. Agendar num salao novo cria (ou acha pelo telefone) o cadastro dela la e liga a conta: foi ela quem
 * pediu, e o telefone da conta so entra por convite com os 4 digitos.
 */
@Service
public class AgendaDaCliente {

  /** O salao do proprio sistema nunca aparece. */
  static final UUID SALAO_DO_SISTEMA = UUID.fromString("11111111-1111-1111-1111-111111111111");
  static final int POR_PAGINA = 20;

  private final NamedParameterJdbcTemplate jdbc;
  private final ServicoPublicBooking agendamentoOnline;
  private final ClienteRepository clientes;
  private final ContasDaCliente contas;
  private final AcessoDeSaude acesso;

  public AgendaDaCliente(NamedParameterJdbcTemplate jdbc, ServicoPublicBooking agendamentoOnline, ClienteRepository clientes,
      ContasDaCliente contas, AcessoDeSaude acesso) {
    this.jdbc = jdbc;
    this.agendamentoOnline = agendamentoOnline;
    this.clientes = clientes;
    this.contas = contas;
    this.acesso = acesso;
  }

  // ─── Regras puras ──────────────────────────────────────────────────────────

  /** "%termo%" para o ILIKE, sem os curingas que a pessoa digitou. */
  static String padrao(String termo) {
    if (termo == null || termo.isBlank()) return null;
    String t = termo.trim().replaceAll("[%_\\\\]", " ").replaceAll("\\s+", " ");
    return t.isBlank() ? null : "%" + (t.length() > 60 ? t.substring(0, 60) : t) + "%";
  }

  /** O telefone da conta (55 + DDD + numero) no formato do cadastro: sem o 55 quando e brasileiro. */
  static String telefoneParaCadastro(String canonico) {
    if (canonico == null) return null;
    if (canonico.startsWith("55") && (canonico.length() == 12 || canonico.length() == 13)) return canonico.substring(2);
    return canonico;
  }

  // ─── Busca ─────────────────────────────────────────────────────────────────

  private static final String VISIVEL = """
      t.id <> :sistema and t.slug is not null
      and exists (select 1 from plan_status ps where ps.id = t.plan_status_id and ps.code = 'ACTIVE')
      and exists (select 1 from services s where s.tenant_id = t.id and s.is_active)
      and coalesce((select o.aparecer_na_area_da_cliente from tenant_operational_settings o where o.tenant_id = t.id), true)""";

  @Transactional(readOnly = true)
  public Map<String, Object> buscar(UUID conta, String busca, String cidade, int pagina) {
    Map<String, Object> p = m("sistema", SALAO_DO_SISTEMA, "conta", conta, "busca", padrao(busca), "cidade", padrao(cidade),
        "lim", POR_PAGINA, "off", Math.max(0, pagina) * POR_PAGINA);
    String w = " where " + VISIVEL
        + " and (cast(:busca as text) is null or t.name ilike :busca or t.description ilike :busca"
        + "      or exists (select 1 from services s where s.tenant_id = t.id and s.is_active and s.name ilike :busca))"
        + " and (cast(:cidade as text) is null or ad.city ilike :cidade or ad.neighborhood ilike :cidade)";
    String de = " from tenants t left join tenant_addresses ad on ad.tenant_id = t.id";
    Long total = jdbc.queryForObject("select count(*)" + de + w, p, Long.class);
    List<Map<String, Object>> itens = new ArrayList<>();
    for (Map<String, Object> x : jdbc.queryForList("""
        select t.id, t.slug, t.name, t.description, t.logo, ad.city, ad.neighborhood, ad.state,
               (select count(*) from services s where s.tenant_id = t.id and s.is_active) as servicos,
               (select min(s.price) from services s where s.tenant_id = t.id and s.is_active and s.price > 0) as menor_preco,
               exists (select 1 from vinculos_da_conta v where v.tenant_id = t.id and v.conta_id = :conta and v.desfeito_em is null) as meu
        """ + de + w + " order by meu desc, t.name limit :lim offset :off", p)) {
      itens.add(resumo(x));
    }
    return m("itens", itens, "total", total, "pagina", Math.max(0, pagina), "porPagina", POR_PAGINA);
  }

  private static Map<String, Object> resumo(Map<String, Object> x) {
    return m("salaoId", x.get("id"), "slug", x.get("slug"), "salao", x.get("name"), "descricao", x.get("description"),
        "logo", x.get("logo"), "cidade", x.get("city"), "bairro", x.get("neighborhood"), "uf", x.get("state"),
        "servicos", x.get("servicos"), "menorPreco", x.get("menor_preco"), "meu", x.get("meu"));
  }

  /** O salao pelo slug: visivel na busca, ou um dos salões dela (mesmo que o dono tenha desligado a busca). */
  Map<String, Object> salao(UUID conta, String slug) {
    if (slug == null || slug.isBlank() || slug.length() > 120) throw new ApiClientErrorException("Salão não encontrado.", 404);
    List<Map<String, Object>> r = jdbc.queryForList("""
        select t.id, t.slug, t.name, t.description, t.logo, t.phone, t.whatsapp, t.instagram, ad.city, ad.neighborhood, ad.state,
               ad.street, ad.number,
               (select count(*) from services s where s.tenant_id = t.id and s.is_active) as servicos,
               (select min(s.price) from services s where s.tenant_id = t.id and s.is_active and s.price > 0) as menor_preco,
               exists (select 1 from vinculos_da_conta v where v.tenant_id = t.id and v.conta_id = :conta and v.desfeito_em is null) as meu
          from tenants t left join tenant_addresses ad on ad.tenant_id = t.id
         where t.slug = :slug and t.id <> :sistema and (""" + VISIVEL + """
         or exists (select 1 from vinculos_da_conta v where v.tenant_id = t.id and v.conta_id = :conta and v.desfeito_em is null))""",
        m("slug", slug, "conta", conta, "sistema", SALAO_DO_SISTEMA));
    if (r.isEmpty()) throw new ApiClientErrorException("Salão não encontrado.", 404);
    return r.get(0);
  }

  @Transactional(readOnly = true)
  public Map<String, Object> perfil(UUID conta, String slug) {
    Map<String, Object> x = salao(conta, slug);
    Map<String, Object> r = resumo(x);
    r.put("endereco", x.get("street") == null ? null : x.get("street") + (x.get("number") == null ? "" : ", " + x.get("number")));
    r.put("instagram", x.get("instagram"));
    r.put("servicosDoSalao", agendamentoOnline.listarServicosAtivos(slug));
    return r;
  }

  // ─── Agendar ───────────────────────────────────────────────────────────────

  public record Pedido(String professionalId, List<PublicBookingDtos.ItemRequest> items, String date, String startTime, String cupom,
      String cpf) {}

  /**
   * Agenda com os dados da conta. Ja ligada ao salao: usa o cadastro dela la. Salao novo: acha o cadastro pelo telefone
   * (com e sem o 55) ou deixa o agendamento online criar; depois liga a conta.
   */
  @Transactional
  public Map<String, Object> agendar(UUID conta, String slug, Pedido pedido) {
    Map<String, Object> s = salao(conta, slug);
    UUID t = (UUID) s.get("id");
    if (pedido == null || pedido.items() == null || pedido.items().isEmpty()) throw new IllegalArgumentException("Escolha pelo menos um serviço.");
    Map<String, Object> c = jdbc.queryForMap("select nome, telefone, email, email_confirmado_em from contas_de_cliente where id = :id",
        Map.of("id", conta));
    UUID ligado = jdbc.query("select client_id from vinculos_da_conta where conta_id = :c and tenant_id = :t and desfeito_em is null",
        Map.of("c", conta, "t", t), rs -> rs.next() ? (UUID) rs.getObject(1) : null);
    Cliente cadastro = ligado != null ? clientes.findById(ligado).orElse(null) : null;
    if (cadastro == null) {
      String telefone = (String) c.get("telefone");
      if (telefone == null) {
        throw new ApiClientErrorException("Sua conta está sem telefone. Para agendar num salão novo, peça um convite ao salão.", 400);
      }
      cadastro = clientes.findByTenantAndPhoneDigits(t, telefone).orElse(null);
    }
    PublicBookingDtos.PublicAppointmentRequest req = new PublicBookingDtos.PublicAppointmentRequest();
    req.customerName = cadastro != null && cadastro.getName() != null ? cadastro.getName() : String.valueOf(c.get("nome"));
    req.customerPhone = cadastro != null && cadastro.getPhone() != null ? cadastro.getPhone() : telefoneParaCadastro((String) c.get("telefone"));
    if (req.customerPhone == null) throw new ApiClientErrorException("O seu cadastro neste salão está sem telefone. Fale com o salão.", 400);
    req.customerEmail = c.get("email_confirmado_em") != null ? (String) c.get("email") : null;
    req.customerCpfCnpj = pedido.cpf() == null || pedido.cpf().isBlank() ? null : pedido.cpf();
    req.professionalId = pedido.professionalId();
    req.items = new ArrayList<>(pedido.items());
    req.date = pedido.date();
    req.startTime = pedido.startTime();
    req.cupom = pedido.cupom();
    PublicBookingDtos.PublicAppointmentResponse resp = agendamentoOnline.criarAgendamentoPublico(slug, req);

    UUID clientId = jdbc.queryForObject("select client_id from appointments where id = :a", Map.of("a", UUID.fromString(resp.appointmentId)),
        UUID.class);
    boolean ligou = false;
    if (ligado == null) {
      UUID outra = contas.contaDoCadastro(t, clientId);
      if (outra == null) {
        jdbc.update("insert into vinculos_da_conta (conta_id, tenant_id, client_id) values (:c, :t, :cl)",
            Map.of("c", conta, "t", t, "cl", clientId));
        acesso.auditar(t, "CLIENT_AREA_LINK", clientId, m("conta", conta, "por", "AGENDAMENTO"), false);
        contas.evento(conta, "SALAO_LIGADO", String.valueOf(s.get("name")));
        ligou = true;
      }
    }
    acesso.auditar(t, "CLIENT_AREA_APPOINTMENT_CREATE", clientId, m("agendamento", resp.appointmentId, "conta", conta), false);
    contas.evento(conta, "AGENDOU_HORARIO", String.valueOf(s.get("name")));
    return m("agendamento", resp, "salaoId", t, "ligouAoSalao", ligou);
  }

  /**
   * "Falar com o salao" num salao em que ela ainda nao tem cadastro ligado: acha o cadastro pelo telefone ou cria um, e
   * liga a conta (mesma regra do agendamento). Devolve o id do salao para abrir a conversa.
   */
  @Transactional
  public Map<String, Object> ligarParaConversar(UUID conta, String slug) {
    Map<String, Object> s = salao(conta, slug);
    UUID t = (UUID) s.get("id");
    UUID ligado = jdbc.query("select client_id from vinculos_da_conta where conta_id = :c and tenant_id = :t and desfeito_em is null",
        Map.of("c", conta, "t", t), rs -> rs.next() ? (UUID) rs.getObject(1) : null);
    if (ligado != null) return m("salaoId", t, "ligouAoSalao", false);
    Map<String, Object> c = jdbc.queryForMap("select nome, telefone, email, email_confirmado_em from contas_de_cliente where id = :id",
        Map.of("id", conta));
    String telefone = (String) c.get("telefone");
    if (telefone == null) {
      throw new ApiClientErrorException("Sua conta está sem telefone. Para falar com um salão novo, peça um convite ao salão.", 400);
    }
    Cliente cadastro = clientes.findByTenantAndPhoneDigits(t, telefone).orElse(null);
    if (cadastro == null) {
      cadastro = new Cliente();
      cadastro.setTenantId(t);
      cadastro.setName(String.valueOf(c.get("nome")));
      cadastro.setPhone(telefoneParaCadastro(telefone));
      if (c.get("email_confirmado_em") != null) cadastro.setEmail((String) c.get("email"));
      cadastro = clientes.save(cadastro);
    }
    if (contas.contaDoCadastro(t, cadastro.getId()) != null) {
      throw new ApiClientErrorException("Este cadastro do salão já está ligado a outra conta. Fale com o salão.", 409);
    }
    jdbc.update("insert into vinculos_da_conta (conta_id, tenant_id, client_id) values (:c, :t, :cl)",
        Map.of("c", conta, "t", t, "cl", cadastro.getId()));
    acesso.auditar(t, "CLIENT_AREA_LINK", cadastro.getId(), m("conta", conta, "por", "CONVERSA"), false);
    contas.evento(conta, "SALAO_LIGADO", String.valueOf(s.get("name")));
    return m("salaoId", t, "ligouAoSalao", true);
  }

  // ─── Dono: aparecer na busca ───────────────────────────────────────────────

  @Transactional(readOnly = true)
  public Map<String, Object> visibilidade() {
    UUID t = acesso.tenant();
    Boolean v = jdbc.query("select aparecer_na_area_da_cliente from tenant_operational_settings where tenant_id = :t", Map.of("t", t),
        rs -> rs.next() ? rs.getBoolean(1) : Boolean.TRUE);
    return m("aparecerNaBusca", v);
  }

  @Transactional
  public Map<String, Object> mudarVisibilidade(boolean aparecer) {
    acesso.exigirGerenciarDocumentos();
    UUID t = acesso.tenant();
    int n = jdbc.update("update tenant_operational_settings set aparecer_na_area_da_cliente = :v where tenant_id = :t",
        Map.of("v", aparecer, "t", t));
    if (n == 0) {
      jdbc.update("insert into tenant_operational_settings (tenant_id, aparecer_na_area_da_cliente) values (:t, :v)",
          Map.of("v", aparecer, "t", t));
    }
    acesso.auditar(t, "CLIENT_AREA_LISTING_UPDATE", null, m("aparecer", aparecer), false);
    return visibilidade();
  }
}
