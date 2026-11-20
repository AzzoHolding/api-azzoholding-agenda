package br.com.phdigitalcode.azzo.agenda.pro.saude;

import static br.com.phdigitalcode.azzo.agenda.pro.saude.Saude.m;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * Modelos de anamnese e termos prontos por segmento (plano, 3.1 e etapa 4). O salao cria a partir deles e ajusta.
 * Os textos dos termos sao um ponto de partida: o salao deve conferir com o juridico dele.
 */
final class ModelosProntos {

  record Modelo(String nome, String segmento, Integer validadeMeses, List<Map<String, Object>> perguntas) {}

  record Termo(String tipo, String nome, String texto) {}

  private ModelosProntos() {}

  private static Map<String, Object> simNao(String id, String texto, boolean obrigatoria) {
    return m("id", id, "texto", texto, "tipo", "SIM_NAO", "obrigatoria", obrigatoria);
  }

  private static Map<String, Object> qual(String id, String texto, String depende, String tipoAlerta, String rotulo) {
    return m("id", id, "texto", texto, "tipo", "TEXTO", "obrigatoria", true,
        "condicao", m("pergunta", depende, "valor", "SIM"),
        "alerta", m("tipo", tipoAlerta, "quando", "PREENCHIDO", "rotulo", rotulo));
  }

  private static Map<String, Object> simAlerta(String id, String texto, String tipoAlerta, String rotulo) {
    return m("id", id, "texto", texto, "tipo", "SIM_NAO", "obrigatoria", true,
        "alerta", m("tipo", tipoAlerta, "quando", "SIM", "rotulo", rotulo));
  }

  private static Map<String, Object> livre(String id, String texto) {
    return m("id", id, "texto", texto, "tipo", "TEXTO_LONGO", "obrigatoria", false);
  }

  static List<Modelo> modelos() {
    List<Modelo> r = new ArrayList<>();
    r.add(new Modelo("Geral", "GERAL", 12, List.of(
        simNao("alergia", "Tem alergia a algum produto, cosmético ou medicamento?", true),
        qual("alergia_qual", "Qual alergia?", "alergia", "ALERGIA", "Alergia"),
        simNao("medicamento", "Usa algum medicamento contínuo?", true),
        m("id", "medicamento_qual", "texto", "Qual medicamento?", "tipo", "TEXTO", "obrigatoria", true,
            "condicao", m("pergunta", "medicamento", "valor", "SIM"),
            "alerta", m("tipo", "OBSERVACAO", "quando", "PREENCHIDO", "rotulo", "Medicamento")),
        simAlerta("gestante", "Está gestante ou amamentando?", "CONTRAINDICACAO", "Gestante ou amamentando"),
        simNao("doenca", "Tem alguma doença ou condição de saúde que devemos saber (diabetes, hipertensão, epilepsia...)?", true),
        qual("doenca_qual", "Qual condição?", "doenca", "RESTRICAO", "Condição de saúde"),
        livre("preferencias", "Preferências e observações (o que gosta, o que não gosta)"))));
    r.add(new Modelo("Cabelo e química", "CABELO", 12, List.of(
        simNao("quimica_recente", "Fez química no cabelo nos últimos 3 meses (coloração, alisamento, progressiva)?", true),
        m("id", "quimica_qual", "texto", "Qual química e quando?", "tipo", "TEXTO", "obrigatoria", true,
            "condicao", m("pergunta", "quimica_recente", "valor", "SIM"),
            "alerta", m("tipo", "RESTRICAO", "quando", "PREENCHIDO", "rotulo", "Química recente")),
        simAlerta("reacao_tintura", "Já teve reação a tintura ou alisante?", "ALERGIA", "Reação a tintura ou alisante"),
        simAlerta("couro_cabeludo", "Tem feridas, descamação ou sensibilidade no couro cabeludo?", "CONTRAINDICACAO", "Couro cabeludo sensível"),
        m("id", "tipo_fio", "texto", "Tipo de fio", "tipo", "ESCOLHA", "obrigatoria", false,
            "opcoes", List.of("Liso", "Ondulado", "Cacheado", "Crespo")),
        livre("observacoes", "Observações sobre o cabelo"))));
    r.add(new Modelo("Estética facial e corporal", "ESTETICA", 12, List.of(
        simAlerta("acido", "Usa ácidos ou retinoides na pele (ácido retinoico, glicólico)?", "CONTRAINDICACAO", "Uso de ácidos na pele"),
        simAlerta("roacutan", "Usou isotretinoína (Roacutan) nos últimos 6 meses?", "CONTRAINDICACAO", "Isotretinoína recente"),
        simAlerta("queloide", "Tem tendência a queloide ou manchas?", "RESTRICAO", "Tendência a queloide ou manchas"),
        simAlerta("marcapasso", "Usa marcapasso ou tem implante metálico?", "CONTRAINDICACAO", "Marcapasso ou implante metálico"),
        simNao("sol", "Tomou sol intenso na última semana?", true),
        m("id", "fototipo", "texto", "Fototipo (cor da pele)", "tipo", "ESCOLHA", "obrigatoria", false,
            "opcoes", List.of("I - muito clara", "II - clara", "III - morena clara", "IV - morena", "V - morena escura", "VI - negra")),
        livre("observacoes", "Observações sobre a pele"))));
    r.add(new Modelo("Depilação", "DEPILACAO", 12, List.of(
        simAlerta("varizes", "Tem varizes ou problemas de circulação?", "RESTRICAO", "Varizes ou má circulação"),
        simAlerta("diabetes", "Tem diabetes?", "RESTRICAO", "Diabetes"),
        simAlerta("pele_sensivel", "Tem pele sensível ou já teve foliculite?", "OBSERVACAO", "Pele sensível ou foliculite"),
        m("id", "metodo", "texto", "Método preferido", "tipo", "ESCOLHA", "obrigatoria", false,
            "opcoes", List.of("Cera quente", "Cera fria", "Linha", "Laser")),
        livre("observacoes", "Observações"))));
    r.add(new Modelo("Unhas", "UNHAS", 12, List.of(
        simAlerta("micose", "Tem micose ou alguma lesão nas unhas?", "CONTRAINDICACAO", "Micose ou lesão nas unhas"),
        simAlerta("alergia_esmalte", "Já teve alergia a esmalte, gel ou acrílico?", "ALERGIA", "Alergia a esmalte, gel ou acrílico"),
        simAlerta("diabetes", "Tem diabetes (cuidado com cutícula e pés)?", "RESTRICAO", "Diabetes"),
        livre("observacoes", "Observações"))));
    r.add(new Modelo("Barbearia", "BARBEARIA", 12, List.of(
        simAlerta("pele_barba", "Tem irritação, foliculite ou pelo encravado na barba?", "OBSERVACAO", "Pele sensível na barba"),
        simAlerta("alergia_lamina", "Tem alergia a produtos de barbear ou loção pós-barba?", "ALERGIA", "Alergia a produtos de barbear"),
        m("id", "navalha", "texto", "Prefere navalha ou máquina no acabamento?", "tipo", "ESCOLHA", "obrigatoria", false,
            "opcoes", List.of("Navalha", "Máquina", "Tanto faz")),
        livre("preferencias", "Preferências de corte e barba"))));
    return r;
  }

  static List<Termo> termos() {
    return List.of(
        new Termo("TERMO_PROCEDIMENTO", "Termo de consentimento do procedimento", """
            Declaro que recebi as informações sobre o procedimento que será realizado, seus cuidados, possíveis \
            reações e resultados esperados, e que pude tirar minhas dúvidas.

            Declaro que as informações de saúde que prestei na anamnese são verdadeiras e completas, e que vou \
            avisar o estabelecimento se algo mudar.

            Autorizo a realização do procedimento e me comprometo a seguir os cuidados recomendados depois dele.

            Concordo em assinar este termo de forma eletrônica, que tem validade como assinatura eletrônica simples \
            (Lei 14.063/2020)."""),
        new Termo("AUTORIZACAO_FOTOS", "Autorização de registro de fotos", """
            Autorizo o estabelecimento a tirar e guardar fotos da área tratada, antes, durante e depois do \
            procedimento, só para acompanhar a evolução do meu tratamento.

            As fotos ficam guardadas com segurança, só a equipe autorizada pode ver, e não serão usadas em \
            divulgação sem uma autorização separada minha.

            Posso revogar esta autorização a qualquer momento; as fotos já tiradas seguem a política de retenção do \
            estabelecimento e a LGPD.

            Concordo em assinar esta autorização de forma eletrônica (Lei 14.063/2020)."""),
        new Termo("AUTORIZACAO_MARKETING", "Autorização de uso de imagem em divulgação", """
            Autorizo o estabelecimento a usar fotos do meu atendimento (antes e depois) em divulgação: redes \
            sociais, site e material de propaganda, sem pagamento.

            Esta autorização é separada da autorização de registro de fotos e é opcional: recusar não muda nada no \
            meu atendimento.

            Posso revogar esta autorização a qualquer momento; depois da revogação, as fotos não serão mais usadas \
            em novas publicações.

            Concordo em assinar esta autorização de forma eletrônica (Lei 14.063/2020)."""));
  }
}
