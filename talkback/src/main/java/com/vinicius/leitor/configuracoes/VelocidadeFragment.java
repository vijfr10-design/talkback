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

package com.vinicius.leitor.configuracoes;

import android.os.Bundle;
import android.widget.Toast;
import androidx.preference.Preference;
import com.google.android.accessibility.talkback.R;
import com.google.android.accessibility.talkback.preference.base.TalkbackBaseFragment;
import com.google.android.accessibility.utils.FeatureSupport;
import com.vinicius.leitor.velocidade.ControleVelocidade;

/** Tela Velocidade das configurações do Bro Blind Screen Reader. */
public class VelocidadeFragment extends TalkbackBaseFragment {

  public VelocidadeFragment() {
    super(R.xml.bbsr_velocidade_preferences);
  }

  @Override
  public CharSequence getTitle() {
    return getText(R.string.title_pref_category_bbsr_velocidade);
  }

  @Override
  public void onCreatePreferences(Bundle savedInstanceState, String rootKey) {
    super.onCreatePreferences(savedInstanceState, rootKey);
    Preference deteccao = findPreference(getString(R.string.pref_talkback_gesture_detection_key));
    if (deteccao != null) {
      if (!FeatureSupport.supportGestureDetection()) {
        deteccao.setEnabled(false);
        deteccao.setSummary("Disponível só no Android 13 ou mais novo.");
      } else {
        deteccao.setOnPreferenceChangeListener(
            (preference, novoValor) -> {
              Toast.makeText(
                      requireContext(),
                      "Desligue e ligue o leitor nas configurações de acessibilidade para aplicar.",
                      Toast.LENGTH_LONG)
                  .show();
              return true;
            });
      }
    }
    Preference restaurar = findPreference("bbsr_restaurar_padrao");
    if (restaurar != null) {
      restaurar.setOnPreferenceClickListener(
          preference -> {
            ControleVelocidade.restaurarPadrao(requireContext());
            // Recria a tela para mostrar os valores padrão.
            setPreferenceScreen(null);
            onCreatePreferences(savedInstanceState, rootKey);
            Toast.makeText(requireContext(), R.string.bbsr_padrao_restaurado, Toast.LENGTH_SHORT)
                .show();
            return true;
          });
    }
  }
}
