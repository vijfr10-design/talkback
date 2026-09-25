/*
 * Copyright (C) 2026 Vinicius
 *
 * Licensed under the Apache License, Version 2.0 (the "License"); you may not
 * use this file except in compliance with the License. You may obtain a copy of
 * the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS, WITHOUT
 * WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied. See the
 * License for the specific language governing permissions and limitations under
 * the License.
 */

package com.vinicius.leitor.velocidade;

import android.content.SharedPreferences;

/**
 * Configuração de velocidade do Bro Blind Screen Reader (seção Velocidade das configurações).
 *
 * <p>Cada atraso do caminho entre o gesto (ou evento) e o foco, o som e a fala passa por aqui.
 * Para cada um há três valores: o original do TalkBack, o novo padrão (reduzido só quando é
 * seguro) e o mínimo seguro. Cada grupo de atrasos tem um nível escolhido pelo usuário:
 * original, padrão, rápido (meio-termo entre padrão e mínimo) ou mínimo. O Modo turbo força o
 * mínimo em todos os grupos e liga as otimizações.
 *
 * <p>Atrasos que existem para evitar fala duplicada ou fora de ordem e que não são esperas (são
 * janelas de filtro) NÃO passam por aqui e continuam com o valor original: DELAY_AUTO_AFTER_STATE
 * e DELAY_SELECTED_AFTER_FOCUS (AccessibilityEventProcessor), TIMEOUT_TOLERANCE_MS
 * (AccessibilityFocusActionHistory) e os de TextEventFilter. Diminuí-los não deixa nada mais
 * rápido, só faz o leitor deixar de reconhecer eventos repetidos.
 *
 * <p>Os valores são lidos das preferências pelo serviço ({@link #carregar}) e ficam em campos
 * estáticos, para que a leitura em cada evento não custe nada.
 */
public final class ConfigVelocidade {

  // Chaves das preferências (as mesmas usadas no XML das configurações).
  public static final String CHAVE_TURBO = "bbsr_modo_turbo";
  public static final String CHAVE_CACHE_TRAVESSIA = "bbsr_cache_travessia";
  public static final String CHAVE_SOM_IMEDIATO = "bbsr_som_imediato";
  public static final String CHAVE_MULTIPLICADOR_FALA = "bbsr_multiplicador_fala";
  public static final String CHAVE_FALA_ENXUTA = "bbsr_fala_enxuta";
  public static final String CHAVE_OMITIR_TIPO = "bbsr_omitir_tipo";
  public static final String CHAVE_OMITIR_ESTADOS = "bbsr_omitir_estados";
  public static final String CHAVE_OMITIR_DICAS = "bbsr_omitir_dicas";
  public static final String CHAVE_ESPERA_EXPLORACAO = "bbsr_espera_exploracao";

  /** Espera usada pelo Modo turbo para começar a exploração por toque. */
  private static final int ESPERA_EXPLORACAO_TURBO_MS = 70;

  // Grupos de atrasos.
  public static final int GRUPO_TOQUE = 0;
  public static final int GRUPO_EVENTOS = 1;
  public static final int GRUPO_CONTEUDO = 2;
  public static final int GRUPO_DICAS = 3;
  public static final int GRUPO_SONS_GESTO = 4;
  public static final int GRUPO_JANELAS = 5;
  private static final int NUMERO_DE_GRUPOS = 6;

  /** Chave da preferência de nível de cada grupo, na ordem dos grupos. */
  public static final String[] CHAVES_NIVEL = {
    "bbsr_nivel_toque",
    "bbsr_nivel_eventos",
    "bbsr_nivel_conteudo",
    "bbsr_nivel_dicas",
    "bbsr_nivel_sons_gesto",
    "bbsr_nivel_janelas",
  };

  // Níveis (valores das listas nas configurações).
  public static final int NIVEL_ORIGINAL = 0;
  public static final int NIVEL_PADRAO = 1;
  public static final int NIVEL_RAPIDO = 2;
  public static final int NIVEL_MINIMO = 3;

  private static final int[] niveis = new int[NUMERO_DE_GRUPOS];

  static {
    for (int i = 0; i < NUMERO_DE_GRUPOS; i++) {
      niveis[i] = NIVEL_PADRAO;
    }
  }

  private static volatile boolean turbo = false;
  private static volatile boolean cacheTravessia = false;
  private static volatile boolean somImediato = true;
  private static volatile float multiplicadorFala = 1.0f;
  private static volatile boolean omitirTipo = false;
  private static volatile boolean omitirEstados = false;
  private static volatile boolean omitirDicas = false;
  private static volatile int esperaExploracao = 0;

  private ConfigVelocidade() {}

  /** Lê todas as preferências da seção Velocidade. */
  public static void carregar(SharedPreferences prefs) {
    for (int i = 0; i < NUMERO_DE_GRUPOS; i++) {
      niveis[i] = lerInteiro(prefs, CHAVES_NIVEL[i], NIVEL_PADRAO);
    }
    turbo = prefs.getBoolean(CHAVE_TURBO, false);
    // Otimização mais arriscada: desligada por padrão, ligada pelo Modo turbo.
    cacheTravessia = turbo || prefs.getBoolean(CHAVE_CACHE_TRAVESSIA, false);
    somImediato = turbo || prefs.getBoolean(CHAVE_SOM_IMEDIATO, true);
    float multiplicador = 1.0f;
    try {
      multiplicador = Float.parseFloat(prefs.getString(CHAVE_MULTIPLICADOR_FALA, "1.0"));
    } catch (NumberFormatException | ClassCastException e) {
      // Mantém 1.0.
    }
    multiplicadorFala = Math.max(0.5f, Math.min(multiplicador, 4.0f));
    esperaExploracao = lerInteiro(prefs, CHAVE_ESPERA_EXPLORACAO, 0);
    if (turbo && (esperaExploracao == 0 || esperaExploracao > ESPERA_EXPLORACAO_TURBO_MS)) {
      esperaExploracao = ESPERA_EXPLORACAO_TURBO_MS;
    }
    boolean enxuta = prefs.getBoolean(CHAVE_FALA_ENXUTA, false);
    omitirTipo = enxuta && prefs.getBoolean(CHAVE_OMITIR_TIPO, true);
    omitirEstados = enxuta && prefs.getBoolean(CHAVE_OMITIR_ESTADOS, true);
    omitirDicas = enxuta && prefs.getBoolean(CHAVE_OMITIR_DICAS, true);
  }

  /** Apaga todas as preferências da seção Velocidade, voltando aos valores padrão. */
  public static void restaurarPadrao(SharedPreferences prefs) {
    SharedPreferences.Editor editor = prefs.edit();
    for (String chave : CHAVES_NIVEL) {
      editor.remove(chave);
    }
    editor
        .remove(CHAVE_TURBO)
        .remove(CHAVE_CACHE_TRAVESSIA)
        .remove(CHAVE_SOM_IMEDIATO)
        .remove(CHAVE_MULTIPLICADOR_FALA)
        .remove(CHAVE_ESPERA_EXPLORACAO)
        .apply();
  }

  /** Verdadeiro quando qualquer uma das chaves pertence à seção Velocidade ou à Fala enxuta. */
  public static boolean ehChaveDaVelocidade(String chave) {
    if (chave == null) {
      return false;
    }
    return chave.startsWith("bbsr_nivel_")
        || chave.equals(CHAVE_TURBO)
        || chave.equals(CHAVE_CACHE_TRAVESSIA)
        || chave.equals(CHAVE_SOM_IMEDIATO)
        || chave.equals(CHAVE_MULTIPLICADOR_FALA)
        || chave.equals(CHAVE_ESPERA_EXPLORACAO)
        || chave.equals(CHAVE_FALA_ENXUTA)
        || chave.equals(CHAVE_OMITIR_TIPO)
        || chave.equals(CHAVE_OMITIR_ESTADOS)
        || chave.equals(CHAVE_OMITIR_DICAS);
  }

  private static int lerInteiro(SharedPreferences prefs, String chave, int padrao) {
    try {
      return Integer.parseInt(prefs.getString(chave, Integer.toString(padrao)));
    } catch (NumberFormatException | ClassCastException e) {
      return padrao;
    }
  }

  private static long atraso(int grupo, long original, long padrao, long minimo) {
    int nivel = turbo ? NIVEL_MINIMO : niveis[grupo];
    switch (nivel) {
      case NIVEL_ORIGINAL:
        return original;
      case NIVEL_RAPIDO:
        return (padrao + minimo) / 2;
      case NIVEL_MINIMO:
        return minimo;
      case NIVEL_PADRAO:
      default:
        return padrao;
    }
  }

  // ---------------------------------------------------------------------------------------------
  // Grupo Toque (exploração por toque)

  /**
   * TouchExplorationInterpreter.TOUCH_END_DELAY_MS, original 70 ms: espera depois de levantar o
   * dedo antes de tratar o fim do toque (serve para juntar o fim do toque com o evento de hover
   * que chega logo depois). Padrão 50 ms: os eventos de hover chegam em poucos milissegundos, e
   * 50 ms ainda cobre com folga. Mínimo 20 ms: abaixo disso o fim do toque pode ser tratado
   * antes do último hover em aparelhos lentos.
   */
  public static long fimDoToqueMs() {
    return atraso(GRUPO_TOQUE, 70, 50, 20);
  }

  /**
   * TouchExplorationInterpreter.EMPTY_TOUCH_AREA_DELAY_MS, original 100 ms: espera antes de
   * avisar que o dedo está sobre uma área vazia (evita o aviso quando o dedo só atravessa um
   * espaço entre dois elementos). Padrão 70 ms, mínimo 30 ms: abaixo disso o som de área vazia
   * dispara a cada pequena passagem entre elementos.
   */
  public static long toqueAreaVaziaMs() {
    return atraso(GRUPO_TOQUE, 100, 70, 30);
  }

  // ---------------------------------------------------------------------------------------------
  // Grupo Eventos (resposta a cliques)

  /**
   * AccessibilityEventProcessor.EVENT_PROCESSING_DELAY, original 150 ms: espera antes de
   * processar o evento de clique, para o app terminar de atualizar o estado (ex.: "marcado").
   * Padrão 150 ms (o valor foi medido pelo Google como o mínimo que funciona em muitos
   * aparelhos). Mínimo 60 ms: bom na maioria dos apps modernos, mas alguns podem falar o estado
   * antigo.
   */
  public static long processamentoCliqueMs() {
    return atraso(GRUPO_EVENTOS, 150, 150, 60);
  }

  // ---------------------------------------------------------------------------------------------
  // Grupo Conteúdo (mudanças de conteúdo e rolagem)

  /**
   * SubtreeChangeEventInterpreter.SHORT_SUBTREE_CHANGED_DELAY_MS, original 150 ms: espera a tela
   * estabilizar depois de uma sequência de eventos de mudança de conteúdo. Precisa ser maior que
   * os 100 ms em que o Android agrupa esses eventos. Padrão 120 ms, mínimo 105 ms.
   */
  public static long subarvoreCurtaMs() {
    return atraso(GRUPO_CONTEUDO, 150, 120, 105);
  }

  /**
   * SubtreeChangeEventInterpreter.LONG_SUBTREE_CHANGED_DELAY_MS, original 350 ms: versão longa
   * usada durante rolagem automática (relógios). Padrão 350 ms (não afeta o celular), mínimo 200
   * ms.
   */
  public static long subarvoreLongaMs() {
    return atraso(GRUPO_CONTEUDO, 350, 350, 200);
  }

  /**
   * ScrollPositionInterpreter.DELAY_SCROLL_FEEDBACK, original 1000 ms: espera antes de falar a
   * posição depois de uma rolagem (ex.: "mostrando itens 5 a 10"). É só um aviso, não atrasa o
   * foco. Padrão 700 ms, mínimo 300 ms: menos que isso fala várias vezes durante uma rolagem.
   */
  public static long posicaoRolagemMs() {
    return atraso(GRUPO_CONTEUDO, 1000, 700, 300);
  }

  /**
   * ScrollPositionInterpreter.DELAY_PAGE_FEEDBACK, original 500 ms: o mesmo para páginas
   * (ViewPager). Padrão 350 ms, mínimo 150 ms.
   */
  public static long posicaoPaginaMs() {
    return atraso(GRUPO_CONTEUDO, 500, 350, 150);
  }

  // ---------------------------------------------------------------------------------------------
  // Grupo Dicas

  /**
   * ProcessorAccessibilityHints.DELAY_HINT, original 400 ms: espera antes da dica de uso ("toque
   * duas vezes para ativar"). A dica entra na fila depois da fala do elemento, então o atraso só
   * decide quanto tempo de silêncio há antes dela. Padrão 250 ms, mínimo 50 ms.
   */
  public static long dicaMs() {
    return atraso(GRUPO_DICAS, 400, 250, 50);
  }

  // ---------------------------------------------------------------------------------------------
  // Grupo Sons do gesto

  /**
   * ProcessorGestureVibrator.FEEDBACK_DELAY, original 70 ms: espera depois do início de um gesto
   * antes de tocar o som e a vibração de "gesto em andamento" (evita o som em toques rápidos).
   * Padrão 70 ms, mínimo 30 ms: menos que isso o som toca em qualquer toque.
   */
  public static long somInicioGestoMs() {
    return atraso(GRUPO_SONS_GESTO, 70, 70, 30);
  }

  // ---------------------------------------------------------------------------------------------
  // Grupo Janelas (troca de tela)

  /**
   * WindowEventInterpreter.WINDOW_CHANGE_DELAY_MS, original 550 ms: espera a animação de troca de
   * tela terminar antes de anunciar a janela nova e pôr o foco nela. Padrão 550 ms (animações do
   * Android duram até ~500 ms). Mínimo 300 ms: com animações reduzidas nas opções do
   * desenvolvedor funciona bem; com animações normais pode anunciar a tela antes de ela terminar
   * de abrir.
   */
  public static long trocaDeJanelaMs() {
    return atraso(GRUPO_JANELAS, 550, 550, 300);
  }

  /**
   * WindowEventInterpreter.WINDOW_CHANGE_DELAY_NO_ANIMATION_MS, original 200 ms: o mesmo quando
   * as animações estão desligadas. Padrão 200 ms, mínimo 100 ms.
   */
  public static long trocaDeJanelaSemAnimacaoMs() {
    return atraso(GRUPO_JANELAS, 200, 200, 100);
  }

  /**
   * WindowEventInterpreter.NONE_APPLICATION_WINDOW_DELAY_MS, original 150 ms: espera para
   * janelas que não são de aplicativo (teclado, diálogos do sistema). Padrão 150 ms, mínimo 80
   * ms.
   */
  public static long janelaNaoAplicativoMs() {
    return atraso(GRUPO_JANELAS, 150, 150, 80);
  }

  /**
   * Espera entre o dedo encostar na tela e a exploração por toque começar, quando o próprio
   * leitor reconhece os gestos (TouchInteractionMonitor, Android 13+). No TalkBack essa espera é
   * de 250 ms (tempo do toque duplo do Android menos 50 ms) e existe para decidir se o toque é o
   * começo de um gesto. É a principal demora entre encostar o dedo e o cursor responder. Com
   * valores baixos o leitor responde quase na hora, como o Jieshuo, mas o primeiro toque de um
   * toque duplo pode mover o foco para o elemento sob o dedo, e um deslizar que começa devagar
   * pode anunciar o elemento onde o dedo encostou.
   *
   * @return a espera em milissegundos, ou 0 para usar a do TalkBack
   */
  public static int esperaExploracaoMs() {
    return esperaExploracao;
  }

  // ---------------------------------------------------------------------------------------------
  // Otimizações e fala

  /** Cache da ordem de travessia (ver {@link CacheTravessia}). */
  public static boolean cacheTravessiaLigado() {
    return cacheTravessia;
  }

  /** Som de foco e vibração tocados no instante em que o foco muda, antes de montar o texto. */
  public static boolean somImediatoLigado() {
    return somImediato;
  }

  /** Multiplicador aplicado sobre a velocidade de fala do sistema (acima do máximo normal). */
  public static float multiplicadorFala() {
    return multiplicadorFala;
  }

  /** Fala enxuta: omitir o tipo do elemento ("botão", "caixa de seleção"). */
  public static boolean omitirTipo() {
    return omitirTipo;
  }

  /** Fala enxuta: omitir estados óbvios ("selecionado", "não selecionado", "somente leitura"). */
  public static boolean omitirEstadosObvios() {
    return omitirEstados;
  }

  /** Fala enxuta: omitir as dicas de uso ("toque duas vezes para ativar"). */
  public static boolean omitirDicas() {
    return omitirDicas;
  }
}
