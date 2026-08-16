package br.com.phdigitalcode.azzo.agenda.pro.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.util.Optional;
import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import br.com.phdigitalcode.azzo.agenda.pro.dto.request.SpecialtyCreateRequest;
import br.com.phdigitalcode.azzo.agenda.pro.dto.request.SpecialtyUpdateRequest;
import br.com.phdigitalcode.azzo.agenda.pro.dto.response.SpecialtyResponse;
import br.com.phdigitalcode.azzo.agenda.pro.entity.Specialty;
import br.com.phdigitalcode.azzo.agenda.pro.repository.SpecialtyRepository;
import br.com.phdigitalcode.azzo.agenda.pro.security.ContextoTenant;

/** Cobre as regras do {@code ServicoSpecialties} original: upsert por nome, unicidade e limites. */
class SpecialtyServiceTest {

  private SpecialtyRepository specialtyRepository;
  private SpecialtyService service;
  private final UUID tenantId = UUID.randomUUID();

  @BeforeEach
  void setUp() {
    specialtyRepository = mock(SpecialtyRepository.class);
    ContextoTenant contextoTenant = mock(ContextoTenant.class);
    when(contextoTenant.obterTenantIdOuFalhar()).thenReturn(tenantId);
    when(specialtyRepository.save(any(Specialty.class))).thenAnswer(inv -> {
      Specialty s = inv.getArgument(0);
      if (s.getId() == null) s.setId(UUID.randomUUID());
      return s;
    });
    service = new SpecialtyService(specialtyRepository, contextoTenant);
  }

  @Test
  void criarComNomeNovoPersisteEspecialidade() {
    when(specialtyRepository.findByTenantAndName(eq(tenantId), eq("Coloracao"))).thenReturn(Optional.empty());

    SpecialtyCreateRequest req = new SpecialtyCreateRequest();
    req.name = "  Coloracao  ";
    req.description = "Tintura em geral";

    SpecialtyResponse response = service.criar(req);

    assertThat(response.name).isEqualTo("Coloracao");
    assertThat(response.description).isEqualTo("Tintura em geral");
  }

  @Test
  void criarComNomeExistenteReaproveitaERegistraNovaDescricao() {
    Specialty existente = new Specialty();
    existente.setId(UUID.randomUUID());
    existente.setTenantId(tenantId);
    existente.setName("Coloracao");
    existente.setDescription("antiga");
    when(specialtyRepository.findByTenantAndName(eq(tenantId), eq("Coloracao"))).thenReturn(Optional.of(existente));

    SpecialtyCreateRequest req = new SpecialtyCreateRequest();
    req.name = "Coloracao";
    req.description = "nova descricao";

    SpecialtyResponse response = service.criar(req);

    assertThat(response.id).isEqualTo(existente.getId().toString());
    assertThat(response.description).isEqualTo("nova descricao");
  }

  @Test
  void nomeEmBrancoEhRejeitado() {
    SpecialtyCreateRequest req = new SpecialtyCreateRequest();
    req.name = "   ";

    assertThatThrownBy(() -> service.criar(req))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("Nome da especialidade e obrigatorio");
  }

  @Test
  void descricaoAcimaDe500CaracteresEhRejeitada() {
    SpecialtyCreateRequest req = new SpecialtyCreateRequest();
    req.name = "Corte";
    req.description = "x".repeat(501);

    assertThatThrownBy(() -> service.criar(req))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("no maximo 500 caracteres");
  }

  @Test
  void descricaoEmBrancoVirarNulo() {
    when(specialtyRepository.findByTenantAndName(eq(tenantId), eq("Corte"))).thenReturn(Optional.empty());

    SpecialtyCreateRequest req = new SpecialtyCreateRequest();
    req.name = "Corte";
    req.description = "   ";

    assertThat(service.criar(req).description).isNull();
  }

  @Test
  void atualizarParaNomeJaUsadoPorOutraEspecialidadeEhRejeitado() {
    UUID id = UUID.randomUUID();
    Specialty alvo = new Specialty();
    alvo.setId(id);
    alvo.setTenantId(tenantId);
    alvo.setName("Corte");

    Specialty outra = new Specialty();
    outra.setId(UUID.randomUUID());
    outra.setTenantId(tenantId);
    outra.setName("Coloracao");

    when(specialtyRepository.findById(id)).thenReturn(Optional.of(alvo));
    when(specialtyRepository.findByTenantAndName(eq(tenantId), eq("Coloracao"))).thenReturn(Optional.of(outra));

    SpecialtyUpdateRequest req = new SpecialtyUpdateRequest();
    req.name = "Coloracao";

    assertThatThrownBy(() -> service.atualizar(id, req))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("Ja existe especialidade com este nome");
  }

  @Test
  void atualizarEspecialidadeDeOutroTenantFalhaComNaoEncontrada() {
    UUID id = UUID.randomUUID();
    Specialty deOutroTenant = new Specialty();
    deOutroTenant.setId(id);
    deOutroTenant.setTenantId(UUID.randomUUID()); // tenant diferente
    deOutroTenant.setName("Corte");
    when(specialtyRepository.findById(id)).thenReturn(Optional.of(deOutroTenant));

    SpecialtyUpdateRequest req = new SpecialtyUpdateRequest();
    req.name = "Corte";

    assertThatThrownBy(() -> service.atualizar(id, req))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("Especialidade nao encontrada");
  }

  @Test
  void deletarSelecionadasComListaNulaEhRejeitado() {
    assertThatThrownBy(() -> service.deletarSelecionadas(null))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("Nenhuma especialidade selecionada");
  }
}
