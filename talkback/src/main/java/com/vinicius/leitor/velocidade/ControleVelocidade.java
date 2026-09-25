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

import android.accessibilityservice.AccessibilityService;
import android.content.Context;
import android.content.SharedPreferences;
import android.view.accessibility.AccessibilityNodeInfo;
import com.google.android.accessibility.utils.AccessibilityNodeInfoUtils;
import com.google.android.accessibility.utils.SharedPreferencesUtils;

/**
 * Liga a seção Velocidade das configurações ao serviço: carrega {@link ConfigVelocidade}, observa
 * mudanças nas preferências e inicia o {@link CacheTravessia}.
 */
public final class ControleVelocidade {

  private final AccessibilityService servico;
  private final SharedPreferences prefs;

  private final SharedPreferences.OnSharedPreferenceChangeListener ouvinte =
      (preferencias, chave) -> {
        if (chave == null || ConfigVelocidade.ehChaveDaVelocidade(chave)) {
          ConfigVelocidade.carregar(preferencias);
          if (!ConfigVelocidade.cacheTravessiaLigado()) {
            CacheTravessia.invalidar();
          }
        }
      };

  public ControleVelocidade(AccessibilityService servico) {
    this.servico = servico;
    this.prefs = SharedPreferencesUtils.getSharedPreferences(servico);
  }

  public void iniciar() {
    ConfigVelocidade.carregar(prefs);
    prefs.registerOnSharedPreferenceChangeListener(ouvinte);
    CacheTravessia.iniciar(
        () -> {
          AccessibilityNodeInfo foco = servico.findFocus(AccessibilityNodeInfo.FOCUS_ACCESSIBILITY);
          if (foco == null) {
            return null;
          }
          return AccessibilityNodeInfoUtils.getRoot(AccessibilityNodeInfoUtils.toCompat(foco));
        });
  }

  public void parar() {
    prefs.unregisterOnSharedPreferenceChangeListener(ouvinte);
    CacheTravessia.parar();
  }

  /** Volta todas as opções da seção Velocidade ao padrão. */
  public static void restaurarPadrao(Context context) {
    ConfigVelocidade.restaurarPadrao(SharedPreferencesUtils.getSharedPreferences(context));
  }
}
