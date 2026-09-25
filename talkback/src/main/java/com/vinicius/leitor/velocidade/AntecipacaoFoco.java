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

import android.os.SystemClock;
import androidx.core.view.accessibility.AccessibilityNodeInfoCompat;
import com.google.android.accessibility.talkback.Feedback;
import com.google.android.accessibility.talkback.Pipeline;
import com.google.android.accessibility.talkback.R;
import com.google.android.accessibility.talkback.TalkBackService;
import com.google.android.accessibility.talkback.focusmanagement.record.FocusActionInfo;
import com.google.android.accessibility.utils.AccessibilityNodeInfoUtils;
import com.google.android.accessibility.utils.Performance.EventId;
import org.checkerframework.checker.nullness.qual.Nullable;

/**
 * Som de foco e vibração no instante em que o foco muda.
 *
 * <p>No TalkBack original, o som e a vibração de foco saem junto com a fala, ou seja, só depois
 * que o evento de foco volta do Android e o texto inteiro do elemento é montado. Aqui, assim que
 * o leitor consegue pôr o foco (navegação por gesto ou exploração por toque), a fala anterior é
 * interrompida e o som e a vibração tocam na hora. Quando a fala do elemento é montada, o
 * compositor consulta {@link #consumir} e não toca o som de foco de novo.
 */
public final class AntecipacaoFoco {

  /** Tempo máximo entre o foco e a montagem da fala para considerar que o som já tocou. */
  private static final long VALIDADE_MS = 2000;

  private static @Nullable AccessibilityNodeInfoCompat ultimoNo;
  private static long ultimoInstante;

  private AntecipacaoFoco() {}

  /**
   * Chamado quando o foco de acessibilidade foi aplicado com sucesso.
   *
   * @return verdadeiro se o som e a vibração foram tocados agora
   */
  public static boolean aoAplicarFoco(
      Pipeline.FeedbackReturner pipeline,
      AccessibilityNodeInfoCompat no,
      FocusActionInfo info,
      @Nullable EventId eventId) {
    if (!ConfigVelocidade.somImediatoLigado() || info.forceMuteFeedback) {
      return false;
    }
    if (info.sourceAction != FocusActionInfo.LOGICAL_NAVIGATION
        && info.sourceAction != FocusActionInfo.TOUCH_EXPLORATION) {
      return false;
    }
    boolean acionavel = AccessibilityNodeInfoUtils.isActionableForAccessibility(no);
    synchronized (AntecipacaoFoco.class) {
      ultimoNo = no;
      ultimoInstante = SystemClock.uptimeMillis();
    }
    // Interrompe só a fala anterior, na hora (a fala nova virá com QUEUE_FLUSH do compositor).
    // Não interrompe sons, para não cortar o som de fim de gesto.
    // Na leitura contínua o foco também anda como navegação lógica, mas depois de cada fala
    // terminar; ali não há nada para interromper e interromper poderia parar a leitura.
    TalkBackService servico = TalkBackService.getInstance();
    if (servico != null && !servico.isLeituraContinuaAtiva()) {
      servico.getSpeechController().interrupt(/* stopTtsSpeechCompletely= */ false);
    }
    pipeline.returnFeedback(
        eventId,
        Feedback.sound(acionavel ? R.raw.focus_actionable : R.raw.focus)
            .vibration(
                acionavel ? R.array.view_actionable_pattern : R.array.view_hovered_pattern));
    return true;
  }

  /**
   * Chamado pelo compositor ao montar a fala do foco. Devolve verdadeiro (uma vez) se o som de
   * foco deste nó já foi tocado por {@link #aoAplicarFoco}.
   */
  public static synchronized boolean consumir(@Nullable AccessibilityNodeInfoCompat no) {
    if (no == null || ultimoNo == null) {
      return false;
    }
    boolean mesmo =
        ultimoNo.equals(no) && SystemClock.uptimeMillis() - ultimoInstante <= VALIDADE_MS;
    if (mesmo) {
      ultimoNo = null;
    }
    return mesmo;
  }
}
