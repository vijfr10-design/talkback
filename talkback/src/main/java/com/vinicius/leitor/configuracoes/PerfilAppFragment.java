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

import android.content.Context;
import android.os.Bundle;
import androidx.preference.Preference;
import androidx.preference.PreferenceCategory;
import androidx.preference.PreferenceScreen;
import com.google.android.accessibility.talkback.gesture.GestureShortcutMapping;
import com.google.android.accessibility.talkback.preference.base.GestureListPreference;
import com.google.android.accessibility.utils.PreferenceSettingsUtils;
import com.google.android.accessibility.utils.SharedPreferencesUtils;
import com.vinicius.leitor.gestos.PerfisGestos;

/** Edição do perfil de gestos de um aplicativo. */
public class PerfilAppFragment extends BaseGestosFragment {

  static final String ARG_PACOTE = "bbsr_pacote";

  private String pacote = "";
  private String nomeApp = "";

  public PerfilAppFragment() {
    super();
  }

  @Override
  public CharSequence getTitle() {
    return "Perfil de " + nomeApp;
  }

  @Override
  public void onCreatePreferences(Bundle savedInstanceState, String rootKey) {
    Bundle argumentos = getArguments();
    if (argumentos != null) {
      pacote = argumentos.getString(ARG_PACOTE, "");
    }
    Context context = requireContext();
    nomeApp = PerfisGestosFragment.nomeDoApp(context, pacote);

    PreferenceScreen tela = getPreferenceManager().createPreferenceScreen(context);
    PreferenceSettingsUtils.setPreferenceScreen(this, tela);

    PreferenceCategory geral = new PreferenceCategory(context);
    geral.setTitle(nomeApp);
    geral.setKey("bbsr_perfil_geral");
    tela.addPreference(geral);
    Preference info = new Preference(context);
    info.setPersistent(false);
    info.setSelectable(false);
    info.setTitle("Como funciona");
    info.setSummary(
        "Estes gestos valem quando "
            + nomeApp
            + " estiver em primeiro plano. Os gestos marcados com usar a ação do perfil global"
            + " continuam como no resto do celular.");
    geral.addPreference(info);
    Preference excluir = new Preference(context);
    excluir.setPersistent(false);
    excluir.setKey("bbsr_excluir_perfil");
    excluir.setTitle("Excluir este perfil");
    excluir.setOnPreferenceClickListener(
        preference -> {
          PerfisGestos.excluir(SharedPreferencesUtils.getSharedPreferences(context), pacote);
          avisar("Perfil de " + nomeApp + " excluído.");
          requireActivity().finish();
          return true;
        });
    geral.addPreference(excluir);

    PreferenceCategory gestos = new PreferenceCategory(context);
    gestos.setTitle("Gestos");
    gestos.setKey("bbsr_perfil_gestos");
    tela.addPreference(gestos);
    for (int idGesto : GestureShortcutMapping.gestosPersonalizaveis()) {
      String nomeGesto = GestureShortcutMapping.getGestureString(context, idGesto);
      GestureListPreference item = new GestureListPreference(context, null);
      item.setKey(PerfisGestos.chave(pacote, idGesto));
      item.setTitle(nomeGesto == null ? "Gesto " + idGesto : nomeGesto);
      item.adicionarOpcaoUsarGlobal("Usar a ação do perfil global");
      item.definirValorInicial(PerfisGestos.USAR_GLOBAL);
      gestos.addPreference(item);
    }
  }
}
