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
import android.content.Intent;
import android.content.pm.PackageManager;
import android.content.pm.ResolveInfo;
import android.os.Bundle;
import androidx.preference.Preference;
import androidx.preference.PreferenceScreen;
import com.google.android.accessibility.utils.PreferenceSettingsUtils;
import com.google.android.accessibility.utils.SharedPreferencesUtils;
import com.vinicius.leitor.gestos.PerfisGestos;
import java.text.Collator;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.TreeMap;

/** Lista os aplicativos instalados para criar um perfil de gestos. */
public class EscolherAppFragment extends BaseGestosFragment {

  public EscolherAppFragment() {
    super();
  }

  @Override
  public CharSequence getTitle() {
    return "Escolha o aplicativo";
  }

  @Override
  public void onCreatePreferences(Bundle savedInstanceState, String rootKey) {
    Context context = requireContext();
    PreferenceScreen tela = getPreferenceManager().createPreferenceScreen(context);
    PreferenceSettingsUtils.setPreferenceScreen(this, tela);

    Set<String> jaTemPerfil =
        PerfisGestos.apps(SharedPreferencesUtils.getSharedPreferences(context));
    PackageManager pm = context.getPackageManager();
    Intent principal = new Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER);
    List<ResolveInfo> atividades = pm.queryIntentActivities(principal, 0);
    TreeMap<String, String> porNome = new TreeMap<>(Collator.getInstance());
    for (ResolveInfo info : atividades) {
      String pacote = info.activityInfo.packageName;
      if (jaTemPerfil.contains(pacote) || porNome.containsValue(pacote)) {
        continue;
      }
      porNome.put(info.loadLabel(pm).toString() + " " + pacote, pacote);
    }
    List<String> chaves = new ArrayList<>(porNome.keySet());
    for (String chave : chaves) {
      String pacote = porNome.get(chave);
      Preference item = new Preference(context);
      item.setPersistent(false);
      item.setKey("bbsr_app_" + pacote);
      item.setTitle(PerfisGestosFragment.nomeDoApp(context, pacote));
      item.setOnPreferenceClickListener(
          preference -> {
            PerfisGestos.adicionar(SharedPreferencesUtils.getSharedPreferences(context), pacote);
            Bundle argumentos = new Bundle();
            argumentos.putString(PerfilAppFragment.ARG_PACOTE, pacote);
            abrirTela(PerfilAppFragment.class, argumentos);
            requireActivity().finish();
            return true;
          });
      tela.addPreference(item);
    }
  }
}
