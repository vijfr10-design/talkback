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

import static com.google.android.accessibility.utils.preference.PreferencesActivity.FRAGMENT_ARGS;
import static com.google.android.accessibility.utils.preference.PreferencesActivity.FRAGMENT_NAME;

import android.content.Context;
import android.content.Intent;
import android.os.Bundle;
import android.widget.Toast;
import androidx.fragment.app.Fragment;
import androidx.preference.Preference;
import androidx.preference.PreferenceDialogFragmentCompat;
import androidx.recyclerview.widget.RecyclerView;
import com.android.talkback.TalkBackPreferencesActivity.TalkBackSubSettings;
import com.google.android.accessibility.talkback.preference.base.GestureListPreference;
import com.google.android.accessibility.talkback.preference.base.GesturePreferenceFragmentCompat;
import com.google.android.accessibility.talkback.preference.base.TalkbackBaseFragment;

/**
 * Base das telas de gestos do Bro Blind: abre a lista de ações de um {@link GestureListPreference}
 * do mesmo jeito que a tela de gestos do TalkBack, e atualiza os resumos ao voltar.
 */
abstract class BaseGestosFragment extends TalkbackBaseFragment {

  BaseGestosFragment(int xml) {
    super(xml);
  }

  BaseGestosFragment() {
    super();
  }

  @Override
  public void onDisplayPreferenceDialog(Preference preference) {
    if (preference instanceof GestureListPreference) {
      Fragment fragment = ((GestureListPreference) preference).createDialogFragment();
      if (fragment instanceof PreferenceDialogFragmentCompat) {
        PreferenceDialogFragmentCompat dialogo = (PreferenceDialogFragmentCompat) fragment;
        dialogo.setTargetFragment(this, 0);
        dialogo.show(getParentFragmentManager(), preference.getKey());
      } else {
        Bundle argumentos =
            GesturePreferenceFragmentCompat.createBundleForFragmentArguments(
                (GestureListPreference) preference);
        abrirTela(GesturePreferenceFragmentCompat.class, argumentos);
      }
    } else {
      super.onDisplayPreferenceDialog(preference);
    }
  }

  @Override
  public void onResume() {
    super.onResume();
    aoVoltar();
  }

  /** Atualiza a tela ao voltar de outra tela (ex.: depois de escolher uma ação). */
  void aoVoltar() {
    RecyclerView lista = getListView();
    if (lista != null && lista.getAdapter() != null) {
      lista.getAdapter().notifyDataSetChanged();
    }
  }

  /** Abre outra tela de configurações por cima desta. */
  void abrirTela(Class<?> fragmento, Bundle argumentos) {
    Intent intent = new Intent(requireContext(), TalkBackSubSettings.class);
    intent.putExtra(FRAGMENT_NAME, fragmento.getCanonicalName());
    intent.putExtra(FRAGMENT_ARGS, argumentos);
    startActivity(intent);
  }

  void avisar(String texto) {
    Context context = getContext();
    if (context != null) {
      Toast.makeText(context, texto, Toast.LENGTH_LONG).show();
    }
  }
}
