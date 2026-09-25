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

package com.vinicius.leitor.latencia;

import static com.google.android.accessibility.utils.Performance.EVENT_ID_UNTRACKED;
import static com.google.android.accessibility.utils.output.SpeechController.QUEUE_MODE_QUEUE;

import android.content.Context;
import android.content.SharedPreferences;
import com.google.android.accessibility.talkback.Feedback;
import com.google.android.accessibility.talkback.Pipeline;
import com.google.android.accessibility.talkback.R;
import com.google.android.accessibility.utils.SharedPreferencesUtils;
import com.google.android.accessibility.utils.output.FeedbackItem;
import com.google.android.accessibility.utils.output.SpeechController.SpeakOptions;

/** Liga o {@link MedidorLatencia} à preferência das configurações e à fala do serviço. */
public final class ControleMedidor {

  private final Context context;
  private final Pipeline.FeedbackReturner pipeline;
  private final SharedPreferences prefs;
  private final String chave;

  private final SharedPreferences.OnSharedPreferenceChangeListener ouvinte;

  public ControleMedidor(Context context, Pipeline.FeedbackReturner pipeline) {
    this.context = context;
    this.pipeline = pipeline;
    this.prefs = SharedPreferencesUtils.getSharedPreferences(context);
    this.chave = context.getString(R.string.pref_bbsr_medidor_latencia_key);
    this.ouvinte =
        (preferencias, chaveAlterada) -> {
          if (chave.equals(chaveAlterada)) {
            atualizar();
          }
        };
  }

  public void iniciar() {
    MedidorLatencia.setAnunciador(
        texto ->
            pipeline.returnFeedback(
                EVENT_ID_UNTRACKED,
                Feedback.speech(
                    texto,
                    SpeakOptions.create()
                        .setQueueMode(QUEUE_MODE_QUEUE)
                        .setFlags(FeedbackItem.FLAG_NO_HISTORY))));
    prefs.registerOnSharedPreferenceChangeListener(ouvinte);
    atualizar();
  }

  public void parar() {
    prefs.unregisterOnSharedPreferenceChangeListener(ouvinte);
    MedidorLatencia.setLigado(false);
    MedidorLatencia.setAnunciador(null);
  }

  private void atualizar() {
    MedidorLatencia.setLigado(estaLigado(context));
  }

  /** Lê a preferência "Medidor de latência". */
  public static boolean estaLigado(Context context) {
    return SharedPreferencesUtils.getSharedPreferences(context)
        .getBoolean(context.getString(R.string.pref_bbsr_medidor_latencia_key), false);
  }

  /** Liga ou desliga o medidor (usado também por ações de gesto). */
  public static void definir(Context context, boolean ligado) {
    SharedPreferencesUtils.getSharedPreferences(context)
        .edit()
        .putBoolean(context.getString(R.string.pref_bbsr_medidor_latencia_key), ligado)
        .apply();
  }
}
