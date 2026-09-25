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

import android.os.Build;
import android.os.Handler;
import android.os.HandlerThread;
import android.os.Process;
import android.view.accessibility.AccessibilityEvent;
import android.view.accessibility.AccessibilityNodeInfo;
import android.view.accessibility.AccessibilityWindowInfo;
import androidx.core.view.accessibility.AccessibilityNodeInfoCompat;
import com.google.android.accessibility.utils.traversal.OrderedTraversalStrategy;
import com.google.android.libraries.accessibility.utils.log.LogUtils;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Supplier;
import org.checkerframework.checker.nullness.qual.Nullable;

/**
 * Cache da ordem de travessia (próximo e anterior elemento).
 *
 * <p>Sem o cache, cada gesto de próximo/anterior monta de novo a árvore inteira da janela
 * ({@link OrderedTraversalStrategy}): percorre todos os nós, consulta filhos, limites na tela e
 * as relações traversalBefore/After, e reordena tudo. Com o cache, a árvore montada é guardada e
 * reaproveitada (junto com o cache de "nós que falam") enquanto a tela não muda.
 *
 * <p>Invalidação: qualquer evento de acessibilidade que possa mudar a estrutura da tela
 * (conteúdo, janela, rolagem, texto, seleção...) invalida o cache antes de ser processado. Só os
 * eventos que com certeza não mudam a estrutura (foco de acessibilidade, hover, toque, gesto,
 * anúncio, notificação, fala) mantêm o cache.
 *
 * <p>Pré-cálculo: depois que o foco muda ou a tela muda, a árvore da janela do foco é montada de
 * novo numa thread em segundo plano, para que o próximo gesto já a encontre pronta. No Android 13
 * ou mais novo, a montagem pede ao sistema o pré-carregamento (prefetch) dos descendentes da raiz.
 */
public final class CacheTravessia {

  private static final String TAG = "BBSR-Cache";

  /** Espera depois da última invalidação antes de pré-calcular (a tela costuma mudar em rajadas). */
  private static final long ESPERA_PRE_CALCULO_MS = 60;

  private static final int EVENTOS_QUE_NAO_MUDAM_ESTRUTURA =
      AccessibilityEvent.TYPE_VIEW_ACCESSIBILITY_FOCUSED
          | AccessibilityEvent.TYPE_VIEW_ACCESSIBILITY_FOCUS_CLEARED
          | AccessibilityEvent.TYPE_VIEW_HOVER_ENTER
          | AccessibilityEvent.TYPE_VIEW_HOVER_EXIT
          | AccessibilityEvent.TYPE_TOUCH_INTERACTION_START
          | AccessibilityEvent.TYPE_TOUCH_INTERACTION_END
          | AccessibilityEvent.TYPE_TOUCH_EXPLORATION_GESTURE_START
          | AccessibilityEvent.TYPE_TOUCH_EXPLORATION_GESTURE_END
          | AccessibilityEvent.TYPE_GESTURE_DETECTION_START
          | AccessibilityEvent.TYPE_GESTURE_DETECTION_END
          | AccessibilityEvent.TYPE_ANNOUNCEMENT
          | AccessibilityEvent.TYPE_NOTIFICATION_STATE_CHANGED
          | AccessibilityEvent.TYPE_SPEECH_STATE_CHANGE
          | AccessibilityEvent.TYPE_ASSIST_READING_CONTEXT;

  private static final class Entrada {
    final AccessibilityNodeInfoCompat raiz;
    final boolean incluirFilhosWeb;
    final boolean fabPrimeiro;
    final int geracao;
    final OrderedTraversalStrategy estrategia;

    Entrada(
        AccessibilityNodeInfoCompat raiz,
        boolean incluirFilhosWeb,
        boolean fabPrimeiro,
        int geracao,
        OrderedTraversalStrategy estrategia) {
      this.raiz = raiz;
      this.incluirFilhosWeb = incluirFilhosWeb;
      this.fabPrimeiro = fabPrimeiro;
      this.geracao = geracao;
      this.estrategia = estrategia;
    }

    boolean serve(AccessibilityNodeInfoCompat outraRaiz, boolean web, boolean fab, int atual) {
      return geracao == atual && incluirFilhosWeb == web && fabPrimeiro == fab
          && raiz.equals(outraRaiz);
    }
  }

  private static final AtomicInteger geracao = new AtomicInteger();
  private static volatile @Nullable Entrada entrada;
  private static @Nullable Handler handlerFundo;
  private static volatile @Nullable Supplier<AccessibilityNodeInfoCompat> fonteDaRaiz;

  private static final Runnable preCalcular =
      () -> {
        Supplier<AccessibilityNodeInfoCompat> fonte = fonteDaRaiz;
        if (!ConfigVelocidade.cacheTravessiaLigado() || fonte == null) {
          return;
        }
        try {
          AccessibilityNodeInfoCompat raiz = fonte.get();
          if (raiz == null) {
            return;
          }
          int geracaoInicial = geracao.get();
          Entrada atual = entrada;
          if (atual != null && atual.serve(raiz, false, false, geracaoInicial)) {
            return;
          }
          OrderedTraversalStrategy estrategia = montar(raiz, false, false);
          if (geracao.get() == geracaoInicial) {
            entrada = new Entrada(raiz, false, false, geracaoInicial, estrategia);
          }
        } catch (RuntimeException e) {
          // Nós podem ficar inválidos a qualquer momento (app fechou, tela mudou). O próximo
          // gesto monta a árvore normalmente.
          LogUtils.w(TAG, "Pré-cálculo falhou: %s", e);
        }
      };

  private CacheTravessia() {}

  /**
   * Chamado pelo serviço ao ligar.
   *
   * @param fonte devolve a raiz da janela que tem o foco de acessibilidade (pode rodar em segundo
   *     plano)
   */
  public static synchronized void iniciar(Supplier<AccessibilityNodeInfoCompat> fonte) {
    fonteDaRaiz = fonte;
    if (handlerFundo == null) {
      HandlerThread thread =
          new HandlerThread("BBSR-Travessia", Process.THREAD_PRIORITY_FOREGROUND);
      thread.start();
      handlerFundo = new Handler(thread.getLooper());
    }
  }

  /** Chamado pelo serviço ao desligar. */
  public static synchronized void parar() {
    fonteDaRaiz = null;
    invalidar();
    if (handlerFundo != null) {
      handlerFundo.getLooper().quitSafely();
      handlerFundo = null;
    }
  }

  /** Invalida o cache se o evento puder ter mudado a estrutura da tela. */
  public static void aoReceberEvento(AccessibilityEvent event) {
    int tipo = event.getEventType();
    if ((tipo & EVENTOS_QUE_NAO_MUDAM_ESTRUTURA) != 0) {
      if (tipo == AccessibilityEvent.TYPE_VIEW_ACCESSIBILITY_FOCUSED) {
        agendarPreCalculo();
      }
      return;
    }
    invalidar();
    agendarPreCalculo();
  }

  /** Descarta a árvore guardada. */
  public static void invalidar() {
    geracao.incrementAndGet();
    entrada = null;
  }

  private static void agendarPreCalculo() {
    Handler handler = handlerFundo;
    if (handler == null || !ConfigVelocidade.cacheTravessiaLigado()) {
      return;
    }
    handler.removeCallbacks(preCalcular);
    handler.postDelayed(preCalcular, ESPERA_PRE_CALCULO_MS);
  }

  /** Devolve a estratégia de travessia ordenada, do cache quando possível. */
  public static OrderedTraversalStrategy obter(
      @Nullable AccessibilityNodeInfoCompat raiz, boolean incluirFilhosWeb, boolean fabPrimeiro) {
    if (!ConfigVelocidade.cacheTravessiaLigado() || raiz == null) {
      return new OrderedTraversalStrategy(raiz, incluirFilhosWeb, fabPrimeiro);
    }
    int geracaoAtual = geracao.get();
    Entrada atual = entrada;
    if (atual != null && atual.serve(raiz, incluirFilhosWeb, fabPrimeiro, geracaoAtual)) {
      return atual.estrategia;
    }
    OrderedTraversalStrategy estrategia = montar(raiz, incluirFilhosWeb, fabPrimeiro);
    if (geracao.get() == geracaoAtual) {
      entrada = new Entrada(raiz, incluirFilhosWeb, fabPrimeiro, geracaoAtual, estrategia);
    }
    return estrategia;
  }

  private static OrderedTraversalStrategy montar(
      AccessibilityNodeInfoCompat raiz, boolean incluirFilhosWeb, boolean fabPrimeiro) {
    preCarregar(raiz);
    return new OrderedTraversalStrategy(raiz, incluirFilhosWeb, fabPrimeiro);
  }

  /** No Android 13+, pede ao sistema os descendentes da raiz de uma vez (menos idas ao app). */
  private static void preCarregar(AccessibilityNodeInfoCompat raiz) {
    if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) {
      return;
    }
    try {
      AccessibilityNodeInfo info = raiz.unwrap();
      AccessibilityWindowInfo janela = info.getWindow();
      if (janela != null) {
        janela.getRoot(AccessibilityNodeInfo.FLAG_PREFETCH_DESCENDANTS_BREADTH_FIRST);
      }
    } catch (RuntimeException e) {
      // Pré-carregamento é só uma otimização.
    }
  }
}
