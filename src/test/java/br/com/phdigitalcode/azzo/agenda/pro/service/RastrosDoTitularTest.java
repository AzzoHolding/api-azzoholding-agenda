package br.com.phdigitalcode.azzo.agenda.pro.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import br.com.phdigitalcode.azzo.agenda.pro.entity.Cliente;
import br.com.phdigitalcode.azzo.agenda.pro.integration.MinioStorageService;
import br.com.phdigitalcode.azzo.agenda.pro.repository.ChatConversationRepository;
import br.com.phdigitalcode.azzo.agenda.pro.repository.ChatMessageRepository;
import br.com.phdigitalcode.azzo.agenda.pro.repository.NotificationRepository;
import br.com.phdigitalcode.azzo.agenda.pro.repository.WhatsAppBookingReactivationCycleRepository;
import br.com.phdigitalcode.azzo.agenda.pro.repository.WhatsAppMessageLogRepository;

class RastrosDoTitularTest {

  private ChatConversationRepository conversas;
  private ChatMessageRepository mensagens;
  private NotificationRepository notificacoes;
  private WhatsAppMessageLogRepository whatsapp;
  private WhatsAppBookingReactivationCycleRepository reativacoes;
  private MinioStorageService storage;
  private RastrosDoTitular rastros;
  private Cliente cliente;

  @BeforeEach
  void setUp() {
    conversas = mock(ChatConversationRepository.class);
    mensagens = mock(ChatMessageRepository.class);
    notificacoes = mock(NotificationRepository.class);
    whatsapp = mock(WhatsAppMessageLogRepository.class);
    reativacoes = mock(WhatsAppBookingReactivationCycleRepository.class);
    storage = mock(MinioStorageService.class);
    rastros = new RastrosDoTitular(conversas, mensagens, notificacoes, whatsapp, reativacoes, storage);

    cliente = new Cliente();
    cliente.setId(UUID.randomUUID());
    cliente.setTenantId(UUID.randomUUID());
    cliente.setPhone("+55 (21) 98759-9613");
    cliente.setAvatar("tenants/x/clientes/y/avatar.png");
  }

  @Test
  void telefoneLocalTiraDdiEMascara() {
    assertThat(RastrosDoTitular.telefoneLocal("+55 (21) 98759-9613")).isEqualTo("21987599613");
    assertThat(RastrosDoTitular.telefoneLocal("21987599613")).isEqualTo("21987599613");
    assertThat(RastrosDoTitular.telefoneLocal("552133334444")).isEqualTo("2133334444");
    // Curto demais para identificar alguem: nao vira criterio de busca.
    assertThat(RastrosDoTitular.telefoneLocal("98759")).isNull();
    assertThat(RastrosDoTitular.telefoneLocal(null)).isNull();
  }

  @Test
  void limpaPeloClienteEPeloTelefoneESomaOsTotais() {
    when(notificacoes.anonimizarPorClienteRaw(cliente.getTenantId(), cliente.getId())).thenReturn(2);
    when(notificacoes.anonimizarPorTelefoneRaw(cliente.getTenantId(), "21987599613", 11)).thenReturn(1);
    when(whatsapp.anonimizarPorTelefoneRaw(cliente.getTenantId(), "21987599613", 11)).thenReturn(4);

    RastrosDoTitular.Resultado resultado = rastros.limpar(cliente);

    assertThat(resultado.notificacoes()).isEqualTo(3);
    assertThat(resultado.mensagensWhatsapp()).isEqualTo(4);
    verify(conversas).anonimizarPorClienteRaw(cliente.getTenantId(), cliente.getId());
    verify(mensagens).anonimizarPorClienteRaw(cliente.getTenantId(), cliente.getId());
    verify(reativacoes).anonimizarPorClienteRaw(cliente.getTenantId(), cliente.getId());
    verify(storage).removerArquivoClienteAvatar("tenants/x/clientes/y/avatar.png", cliente.getTenantId());
  }

  @Test
  void semTelefoneNaoBuscaPorTelefone() {
    cliente.setPhone(null);
    rastros.limpar(cliente);
    verify(notificacoes, never()).anonimizarPorTelefoneRaw(any(), anyString(), anyInt());
    verify(whatsapp, never()).anonimizarPorTelefoneRaw(any(), anyString(), anyInt());
  }

  @Test
  void fotoExternaNaoEDoStorageEFalhaNoStorageNaoSegura() {
    cliente.setAvatar("https://exemplo.com/foto.png");
    rastros.limpar(cliente);
    verify(storage, never()).removerArquivoClienteAvatar(any(), any());

    cliente.setAvatar("tenants/x/avatar.png");
    doThrow(new IllegalArgumentException("escopo")).when(storage).removerArquivoClienteAvatar(any(), any());
    rastros.limpar(cliente);
  }
}
