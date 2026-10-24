package br.com.phdigitalcode.azzo.agenda.pro.security;

import org.springframework.stereotype.Component;

import br.com.phdigitalcode.azzo.agenda.pro.entity.Usuario;
import br.com.phdigitalcode.azzo.agenda.pro.entity.enums.PapelUsuario;
import br.com.phdigitalcode.azzo.agenda.pro.repository.PerfilAcessoRepository;
import br.com.phdigitalcode.azzo.agenda.pro.repository.ProfissionalRepository;

/**
 * O login de quem saiu da equipe.
 *
 * <p>Remover um profissional so o DESATIVA ({@code professionals.is_active = false}) — a linha fica
 * por causa do historico de agenda e comissao. Ate 2026-09-17 o login nao conferia isso (o codigo
 * dizia "verificacao de profissional inativo fica pendente"): quem era desligado continuava
 * entrando, vendo clientes e operando o PDV.
 *
 * <p>O DONO e o ADMIN nunca sao barrados por aqui: o dono pode ter o proprio cadastro de
 * profissional ("Sou eu") e desliga-lo da agenda nao pode tranca-lo fora do salao.
 */
@Component
public class AcessoDeProfissional {

  public static final String MENSAGEM =
      "Seu acesso foi desativado pelo salao. Fale com o responsavel.";

  private final ProfissionalRepository profissionalRepository;
  private final PerfilAcessoRepository perfilAcessoRepository;

  public AcessoDeProfissional(
      ProfissionalRepository profissionalRepository, PerfilAcessoRepository perfilAcessoRepository) {
    this.profissionalRepository = profissionalRepository;
    this.perfilAcessoRepository = perfilAcessoRepository;
  }

  /**
   * O usuario saiu da equipe? Duas formas de sair: ser DESLIGADO em /acessos ({@code
   * equipe_desligamento}) ou ter o cadastro de profissional DESATIVADO.
   *
   * <p>O desligamento nunca era consultado fora de {@code ServicoPerfisDeAcesso} (achado SEG-004,
   * auditoria de 2026-10-06): o desligado so tinha a senha trocada, e continuava renovando a sessao
   * por 30 dias e recuperando a conta por "esqueci minha senha".
   */
  public boolean desativado(Usuario usuario) {
    if (usuario == null || usuario.getId() == null || usuario.getTenantId() == null) return false;
    if (usuario.getRole() == PapelUsuario.OWNER || usuario.getRole() == PapelUsuario.ADMIN) {
      return false;
    }
    if (perfilAcessoRepository.estaDesligado(usuario.getId())) return true;
    return profissionalRepository
        .findByTenantIdAndUserId(usuario.getTenantId(), usuario.getId())
        .map(profissional -> !profissional.isActive())
        .orElse(false);
  }
}
